package dev.halloween;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** A short incident between residents already present in the settlement. */
final class StreetCrime {
    private static final String BANDIT = "halloween_bandit";
    private static final String WANTED = "halloween_wanted";
    private static final String ASSAULT = "halloween_crime_street_assault";
    private static final Map<ServerWorld, Map<Long, District>> DISTRICTS = new WeakHashMap<>();

    private static final class District {
        Incident incident;
        long nextCheck;
    }

    static final class Incident {
        final UUID victim;
        final List<UUID> gang;
        final BlockPos origin;
        final long started;
        int nextAttacker;
        final java.util.Set<UUID> leftNext = new java.util.HashSet<>();
        boolean reported, startedFight;
        List<UUID> crowd=List.of();
        UUID pending;
        long hitAt;
        List<VillagerEntity> witnesses=List.of();

        Incident(LivingEntity victim, List<VillagerEntity> gang, long now) {
            this.victim = victim.getUuid();
            this.gang = gang.stream().map(VillagerEntity::getUuid).toList();
            this.origin = victim.getBlockPos();
            this.started = now;
        }
    }

    static void tick(ServerWorld world, VillageMemory.Team team, List<VillagerEntity> residents) {
        var districts = DISTRICTS.computeIfAbsent(world, ignored -> new HashMap<>());
        var district = districts.computeIfAbsent(team.village, ignored -> new District());
        long now = world.getTime();
        if (district.incident != null) {
            if (!advance(world, district.incident, residents)) {
                district.incident = null;
                district.nextCheck = now + world.random.nextBetween(1800, 3200);
            }
            return;
        }
        if (now < district.nextCheck) return;
        district.nextCheck = now + world.random.nextBetween(220, 420);
        if (world.random.nextInt(VillageBuilders.workHours(world) ? 4 : 2) != 0) return;
        district.incident = find(world, team, residents);
    }

    static void tickPunches(ServerWorld world){
        var districts=DISTRICTS.get(world);
        if(districts==null)return;
        for(var district:districts.values()){
            var incident=district.incident;
            if(incident!=null)resolveStrike(world,incident,world.getTime());
        }
    }

    static void resolveStrike(ServerWorld world,Incident incident,long now){
        if(incident.pending==null||now<incident.hitAt)return;
        var id=incident.pending;
        incident.pending=null;
        if(world.getEntity(incident.victim) instanceof VillagerEntity victim&&victim.isAlive()
                &&world.getEntity(id) instanceof VillagerEntity attacker&&available(attacker,true)
                &&attacker.squaredDistanceTo(victim)<4.8&&attacker.canSee(victim)
                &&!guardWatching(incident.witnesses,victim,9))strike(world,incident,incident.witnesses,attacker,victim);
    }

    static boolean active(ServerWorld world, VillagerEntity villager) {
        var districts = DISTRICTS.get(world);
        if (districts == null) return false;
        for (var district : districts.values()) {
            var incident = district.incident;
            if (incident != null && world.getTime() - incident.started < 460
                    && incident.gang.contains(villager.getUuid())) return true;
        }
        return false;
    }

    static Incident find(ServerWorld world, VillageMemory.Team team, List<VillagerEntity> residents) {
        String owner = "halloween_population_team_" + Long.toUnsignedString(team.village);
        var bandits = residents.stream().filter(v -> available(v, false) && !active(world, v) && !VillageConvoys.fighting(world, v))
                .filter(v -> v.getCommandTags().stream().noneMatch(t -> t.startsWith("halloween_population_team_") && !t.equals(owner)))
                .limit(15).toList();
        if (bandits.size() < 2) return null;
        var candidates = new ArrayList<LivingEntity>();
        for (var player : world.getPlayers()) if (targetable(player)) candidates.add(player);
        for (var resident : residents) if (targetable(resident)) candidates.add(resident);

        Incident best = null, bestPlayer = null;
        double bestScore = 0, bestPlayerScore = 0;
        double radius = Math.min(220, VillageEras.radius(world, team.anchor));
        for (var victim : candidates) {
            if (victim instanceof ServerPlayerEntity p && !VillageFights.ready(world,p)) continue;
            if (victim.getBlockPos().getSquaredDistance(team.anchor) > radius * radius) continue;
            if (world.getPlayers().stream().noneMatch(p -> p.squaredDistanceTo(victim) < 70 * 70)) continue;
            if (guardWatching(residents, victim, 9)) continue;
            var gang = bandits.stream().filter(v -> Math.abs(v.getY() - victim.getY()) <= 4)
                    .filter(v -> v.squaredDistanceTo(victim) <= 22 * 22)
                    .sorted(Comparator.comparingDouble(victim::squaredDistanceTo)).limit(3).toList();
            if (gang.size() < (victim instanceof ServerPlayerEntity ? 3 : 2) || gang.stream().noneMatch(v -> v.canSee(victim))) continue;
            int cover = cover(world, victim.getBlockPos());
            double score = 28 - Math.sqrt(gang.getFirst().squaredDistanceTo(victim)) + cover * 6;
            if (victim instanceof ServerPlayerEntity && score > bestPlayerScore) {
                bestPlayerScore = score;
                bestPlayer = playerIncident(world,victim,gang,bandits);
            }
            if (score > bestScore) {
                bestScore = score;
                best = victim instanceof ServerPlayerEntity ? playerIncident(world,victim,gang,bandits) : new Incident(victim,gang,world.getTime());
            }
        }
        return bestPlayer != null && world.random.nextInt(4) == 0 ? bestPlayer : best;
    }

    private static Incident playerIncident(ServerWorld world,LivingEntity victim,List<VillagerEntity> attackers,List<VillagerEntity> group){
        var incident=new Incident(victim,attackers,world.getTime());
        incident.crowd=group.stream().filter(v->v.squaredDistanceTo(victim)<28*28).map(VillagerEntity::getUuid).toList();
        return incident;
    }

    private static int cover(ServerWorld world, BlockPos pos) {
        int sides = 0;
        for (Direction side : Direction.Type.HORIZONTAL) {
            for (int distance = 1; distance <= 3; distance++) {
                var wall = pos.offset(side, distance).up();
                if (world.getBlockState(wall).isSolidBlock(world, wall)) {
                    sides++;
                    break;
                }
            }
        }
        return sides;
    }

    static boolean targetable(LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target.hasVehicle() || target.isSleeping()) return false;
        var tags = target.getCommandTags();
        if (target instanceof ServerPlayerEntity player) {
            return !player.isCreative() && !player.getAbilities().invulnerable
                    && player.networkHandler.canInteractWithGame()
                    && !tags.contains("halloween_player_custody") && !tags.contains("halloween_player_jailed");
        }
        return target instanceof VillagerEntity v && !v.isBaby() && !RoyalProfession.isKing(v)
                && !VillageDefense.isGuard(v) && !VillageConvoys.owns(v) && !tags.contains(BANDIT) && !tags.contains("halloween_prisoner");
    }

    private static boolean available(VillagerEntity v, boolean continuing) {
        var tags = v.getCommandTags();
        return v.isAlive() && !v.isSleeping() && !v.hasVehicle() && !v.isLeashed() && !v.isAiDisabled()
                && tags.contains(BANDIT) && !tags.contains("halloween_prisoner") && !tags.contains("halloween_surrender")
                && !tags.contains("halloween_royal_assassin") && !tags.contains("halloween_rebel")
                && !tags.contains("halloween_attacked") && !VillageDefense.engaged(v) && !VillageTrials.busy(v)
                && (continuing || !tags.contains(WANTED) && !tags.contains("halloween_escaped"));
    }

    static boolean advance(ServerWorld world, Incident incident, List<VillagerEntity> residents) {
        if (!(world.getEntity(incident.victim) instanceof LivingEntity victim) || !targetable(victim)
                || world.getTime() - incident.started >= 460
                || !victim.getBlockPos().isWithinDistance(incident.origin, 36)) {
            stop(world, incident);
            return false;
        }
        var attackers = new ArrayList<VillagerEntity>();
        for (var id : incident.gang) {
            if (world.getEntity(id) instanceof VillagerEntity v && available(v, true)
                    && v.squaredDistanceTo(victim) <= 32 * 32) attackers.add(v);
        }
        if (attackers.size() < (victim instanceof ServerPlayerEntity ? 3 : 2)) {
            stop(world, incident);
            return false;
        }

        if (guardWatching(residents, victim, 9)) {
            if (incident.reported) report(attackers);
            if(victim instanceof ServerPlayerEntity player)VillageFights.interruptGang(world,player);
            stop(world, incident);
            return false;
        }

        if(victim instanceof ServerPlayerEntity player){
            if(incident.startedFight)return VillageFights.running(world,player);
            incident.startedFight=VillageFights.start(world,player,attackers,incident.crowd,true);
            return incident.startedFight;
        }

        for (var attacker : attackers) {
            attacker.setAttacking(true);
            FightPose.of(attacker).halloween$fightPose(FightPose.GUARD);
            var to=victim.getEntityPos().subtract(attacker.getEntityPos());
            float yaw=(float)Math.toDegrees(Math.atan2(-to.x,to.z));
            attacker.setBodyYaw(yaw);
            attacker.setHeadYaw(yaw);
            attacker.getLookControl().lookAt(victim,25,30);
            VillageTasks.hold(attacker);
            if(attacker.squaredDistanceTo(victim)>4||!attacker.canSee(victim)) pursue(world,attacker,victim,.92);
            else attacker.getNavigation().stop();
        }
        for (int i = 0; i < attackers.size(); i++) {
            int turn = (incident.nextAttacker + i) % attackers.size();
            var attacker = attackers.get(turn);
            if (attacker.squaredDistanceTo(victim) > 4 || !attacker.canSee(victim)) continue;
            incident.nextAttacker = (turn + 1) % attackers.size();
            boolean left=incident.leftNext.remove(attacker.getUuid());
            if(!left)incident.leftNext.add(attacker.getUuid());
            FightPose.of(attacker).halloween$fightPose(left?FightPose.LEFT:FightPose.RIGHT);
            incident.pending=attacker.getUuid();
            incident.hitAt=world.getTime()+5;
            incident.witnesses=List.copyOf(residents);
            break; // One real blow per update leaves time to react and avoids invulnerability-frame spam.
        }
        return true;
    }

    static boolean pursue(ServerWorld world,VillagerEntity attacker,LivingEntity victim,double speed){
        var nav=attacker.getNavigation();
        if(!VillagePathBudget.take(world))return false;
        nav.setCanOpenDoors(true);
        var position=victim.getBlockPos();
        var goals=new java.util.HashSet<BlockPos>();
        for(var offset:BlockPos.iterateOutwards(position,2,1,2)){
            if(!world.isChunkLoaded(offset)||offset.equals(position)||!world.getFluidState(offset).isEmpty())continue;
            var box=attacker.getBoundingBox().offset(Vec3d.ofBottomCenter(offset).subtract(attacker.getEntityPos()));
            if(!world.isSpaceEmpty(attacker,box)||world.getBlockState(offset.down()).getCollisionShape(world,offset.down()).isEmpty())continue;
            if(Vec3d.ofBottomCenter(offset).squaredDistanceTo(victim.getEntityPos())>4.1)continue;
            var eyes=Vec3d.ofBottomCenter(offset).add(0,attacker.getEyeHeight(attacker.getPose()),0);
            var ray=world.raycast(new net.minecraft.world.RaycastContext(eyes,victim.getEyePos(),
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,attacker));
            if(ray.getType()==net.minecraft.util.hit.HitResult.Type.MISS)goals.add(offset.toImmutable());
        }
        var path=goals.isEmpty()?null:nav.findPathTo(goals,0);
        if(path==null||!path.reachesTarget()){
            nav.stop();attacker.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);return false;
        }
        return nav.startMovingAlong(path,speed);
    }

    private static void strike(ServerWorld world, Incident incident, List<VillagerEntity> residents,
                               VillagerEntity attacker, LivingEntity victim) {
        if (!victim.damage(world, world.getDamageSources().mobAttack(attacker), 5)) return;
        Vec3d spray = victim.getEntityPos().subtract(attacker.getEntityPos()).multiply(1, 0, 1).normalize().multiply(.22);
        Vec3d impact = victim.getEntityPos().add(spray).add(0, victim.getHeight() * .65, 0);
        world.spawnParticles(new net.minecraft.particle.DustParticleEffect(0x8e1322, .63f),
                impact.x, impact.y, impact.z, 9, .23, .16, .23, .06);
        world.spawnParticles(new net.minecraft.particle.DustParticleEffect(0x481017, .47f),
                impact.x, impact.y - .1, impact.z, 5, .27, .22, .27, .055);
        attacker.addCommandTag(ASSAULT);
        if (victim instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.literal("§cНа тебя напала банда!"), true);
        }
        boolean witnessed = residents.stream().anyMatch(v -> v != victim && v.isAlive() && !v.isSleeping()
                && !v.getCommandTags().contains(BANDIT) && v.squaredDistanceTo(victim) < 16 * 16 && v.canSee(attacker));
        if (witnessed) {
            incident.reported = true;
            for (var id : incident.gang) {
                if (world.getEntity(id) instanceof VillagerEntity member && member.getCommandTags().contains(ASSAULT)) {
                    member.addCommandTag(WANTED);
                }
            }
        }
        if (victim instanceof VillagerEntity resident && resident.isAlive() && VillagePathBudget.take(world)) {
            Vec3d away = resident.getEntityPos().subtract(attacker.getEntityPos()).multiply(1, 0, 1).normalize();
            resident.getNavigation().startMovingTo(resident.getX() + away.x * 8, resident.getY(),
                    resident.getZ() + away.z * 8, 1);
            VillageTasks.hold(resident);
        }
        world.playSound(null, victim.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                SoundCategory.HOSTILE, .85f, .88f);
        if (victim.isDead() && incident.reported) attacker.addCommandTag("halloween_crime_murder");
    }

    private static boolean guardWatching(List<VillagerEntity> residents, LivingEntity victim, int range) {
        return residents.stream().anyMatch(v -> v.isAlive() && VillageDefense.isGuard(v) && !VillageDefense.escorting(v)
                && v.squaredDistanceTo(victim) <= range * range && v.canSee(victim));
    }

    private static void report(List<VillagerEntity> attackers) {
        for (var attacker : attackers) {
            if (attacker.getCommandTags().contains(ASSAULT)) attacker.addCommandTag(WANTED);
        }
    }

    private static void stop(ServerWorld world, Incident incident) {
        for (var id : incident.gang) {
            if (world.getEntity(id) instanceof VillagerEntity v) {
                if(VillageFights.owns(world,v))continue;
                FightPose.of(v).halloween$fightPose(FightPose.NONE);
                v.setAttacking(false);
                if (available(v, true)) v.getNavigation().stop();
            }
        }
    }

    private StreetCrime() {}
}
