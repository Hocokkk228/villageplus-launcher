package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.*;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.*;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

/** Shared combat controller for patrols, mounted guards and castle sentries. */
public final class VillageDefense {
    private static final Map<UUID,Long> COMBAT=new HashMap<>();
    static void combat(VillagerEntity guard){COMBAT.put(guard.getUuid(),guard.getEntityWorld().getTime()+30);guard.setAttacking(true);}
    static boolean engaged(VillagerEntity guard){return COMBAT.getOrDefault(guard.getUuid(),0L)>guard.getEntityWorld().getTime();}
    static boolean escorting(VillagerEntity g){return g.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_cell_exit_"))||g.getCommandTags().contains("halloween_player_guarding")||g.getCommandTags().contains("halloween_guard_escorting")||g.getCommandTags().contains("halloween_executor");}
    private static final Map<UUID,Long> ATTACKS=new HashMap<>();
    private record Alert(UUID target,long scanned){}
    private static final Map<ServerWorld,Map<Long,Alert>> ALERTS=new WeakHashMap<>();
    private record HorseGoal(BlockPos target,long expires){}
    private static final Map<UUID,HorseGoal> HORSE_GOALS=new HashMap<>();
    static void register(){
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->{
            if(entity instanceof VillagerEntity rider&&isGuard(rider)&&rider.getVehicle() instanceof HorseEntity
                &&source.isOf(net.minecraft.entity.damage.DamageTypes.IN_WALL))return false;
            return true;
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.MOB_CONVERSION.register((old,converted,context)->{
            if(old instanceof net.minecraft.entity.mob.ZombieVillagerEntity zombie&&formerGuard(zombie)&&converted instanceof VillagerEntity guard){
                guard.addCommandTag("halloween_guard");guard.setExperience(1);guard.setPersistent();
                for(String tag:old.getCommandTags())if(tag.startsWith("halloween_"))guard.addCommandTag(tag);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(w->{
            if(w.getPlayers().isEmpty())return;
            for(var player:w.getPlayers())for(var horse:w.getEntitiesByClass(HorseEntity.class,new Box(player.getBlockPos()).expand(96),h->h.isAlive()&&h.getFirstPassenger() instanceof VillagerEntity rider&&(isGuard(rider)||rider.getVillagerData().profession().matchesKey(Oprichniki.KEY))))driveHorse(horse);
            if(w.getTime()%5!=0)return;
            Set<UUID> seen=new HashSet<>();
            for(var player:w.getPlayers())for(var zombie:w.getEntitiesByClass(net.minecraft.entity.mob.ZombieVillagerEntity.class,new Box(player.getBlockPos()).expand(128),VillageDefense::formerGuard))
                if(zombie.isAlive()&&seen.add(zombie.getUuid())&&(VillageEras.settlement(w,zombie.getBlockPos())==null||VillageEras.settlement(w,zombie.getBlockPos()).era<3))((dev.halloween.mixin.village.GuardZombieCure)zombie).halloween$cureGuard(w);
            seen.clear();
            for(var p:w.getPlayers())for(var guard:w.getEntitiesByClass(VillagerEntity.class,new Box(p.getBlockPos()).expand(128),VillageDefense::isGuard))
                if(seen.add(guard.getUuid())){guard.setAttacking(engaged(guard));if(!engaged(guard))fight(w,guard,guard.getBlockPos(),16);}
        });
    }
    private static void driveHorse(HorseEntity horse){
        var path=horse.getNavigation().getCurrentPath();long now=horse.getEntityWorld().getTime();
        if(path!=null&&!path.isFinished())HORSE_GOALS.put(horse.getUuid(),new HorseGoal(path.getTarget(),now+80));
        var saved=HORSE_GOALS.get(horse.getUuid());
        if(!horse.isOnGround()||saved==null||saved.expires()<now){if(saved!=null&&saved.expires()<now)HORSE_GOALS.remove(horse.getUuid());return;}
        Vec3d target=path!=null&&!path.isFinished()?path.getNodePosition(horse):Vec3d.ofCenter(saved.target());
        double dx=target.x-horse.getX(),dz=target.z-horse.getZ();double length=Math.sqrt(dx*dx+dz*dz);
        while(path!=null&&!path.isFinished()&&length<.7){
            path.next();target=path.isFinished()?Vec3d.ofCenter(saved.target()):path.getNodePosition(horse);
            dx=target.x-horse.getX();dz=target.z-horse.getZ();length=Math.sqrt(dx*dx+dz*dz);
        }
        if(length<.3){if(path==null||path.isFinished())HORSE_GOALS.remove(horse.getUuid());return;}
        double vx=dx/length*.28,vz=dz/length*.28;
        if(horse.getFirstPassenger() instanceof VillagerEntity rider){
            var world=horse.getEntityWorld();
            var horseAhead=horse.getBoundingBox().offset(vx*2,0,vz*2);
            var riderAhead=rider.getBoundingBox().offset(vx*2,0,vz*2);
            if(world.getBlockCollisions(horse,horseAhead).iterator().hasNext()||world.getBlockCollisions(rider,riderAhead).iterator().hasNext()){
                horse.getNavigation().stop();var velocity=horse.getVelocity();horse.setVelocity(0,velocity.y,0);horse.velocityDirty=true;
                rider.stopRiding();rider.addCommandTag("halloween_mount_blocked_"+(now+400));return;
            }
        }
        if(path==null||path.isFinished()){
            BlockPos ahead=BlockPos.ofFloored(horse.getX()+vx*2,horse.getY(),horse.getZ()+vz*2);
            if(!horse.getEntityWorld().getBlockState(ahead.down()).isSolidBlock(horse.getEntityWorld(),ahead.down())
                ||!horse.getEntityWorld().isSpaceEmpty(horse,horse.getBoundingBox().offset(vx,0,vz)))return;
        }
        var velocity=horse.getVelocity();horse.setVelocity(vx,velocity.y,vz);horse.velocityDirty=true;
    }
    static void shoot(ServerWorld w,VillagerEntity guard,LivingEntity target){
        if(w.getTime()-ATTACKS.getOrDefault(guard.getUuid(),-100L)<40||!guard.canSee(target))return;
        if(guard.getMainHandStack().getItem() instanceof MusketItem){
            if(w.getTime()-ATTACKS.getOrDefault(guard.getUuid(),-100L)>=70&&MusketItem.fireGuard(w,guard,target,20))ATTACKS.put(guard.getUuid(),w.getTime());return;
        }
        ATTACKS.put(guard.getUuid(),w.getTime());
        var arrow=new net.minecraft.entity.projectile.ArrowEntity(w,guard,new net.minecraft.item.ItemStack(Items.ARROW),guard.getMainHandStack());
        double dx=target.getX()-guard.getX(),dz=target.getZ()-guard.getZ(),dy=target.getBodyY(.5)-arrow.getY();
        arrow.setVelocity(dx,dy+Math.sqrt(dx*dx+dz*dz)*.04,dz,1.6f,3);w.spawnEntity(arrow);guard.swingHand(Hand.MAIN_HAND);
        w.playSound(null,guard.getBlockPos(),net.minecraft.sound.SoundEvents.ITEM_CROSSBOW_SHOOT,net.minecraft.sound.SoundCategory.NEUTRAL,1,1);
    }
    private static boolean formerGuard(net.minecraft.entity.mob.ZombieVillagerEntity v){return v.getCommandTags().contains("halloween_guard")
        ||v.getVillagerData().profession().matchesKey(GuardProfession.KEY)||v.getVillagerData().profession().matchesKey(GuardProfession.ROYAL_KEY);}
    public static boolean isGuard(VillagerEntity v){
        return v.isAlive()&&(v.getCommandTags().contains("halloween_guard")
            ||v.getVillagerData().profession().matchesKey(GuardProfession.KEY)
            ||v.getVillagerData().profession().matchesKey(GuardProfession.ROYAL_KEY));
    }
    static boolean fight(ServerWorld w,VillagerEntity guard,BlockPos centre,int radius){
        if(escorting(guard)||guard.getMainHandStack().isOf(Items.LEAD))return false;
        if(guard.getCommandTags().contains("halloween_bodyguard")||guard.getCommandTags().contains("halloween_castle_guard"))return false;
        LivingEntity target=w.getEntitiesByClass(HostileEntity.class,new Box(guard.getBlockPos()).expand(radius,8,radius),m->m.isAlive()&&!m.isSpectator()&&surface(w,m)&&Math.abs(m.getY()-guard.getY())<=3&&guard.canSee(m))
            .stream().filter(m->assigned(w,guard,m)).min(Comparator.comparingDouble(guard::squaredDistanceTo)).orElse(null);
        if(target==null)return false;
        var path=guard.getNavigation().findPathTo(target,1);if(path==null||!path.reachesTarget())return false;
        combat(guard);
        if(guard.isSleeping())guard.wakeUp();
        guard.getBrain().forget(MemoryModuleType.INTERACTION_TARGET);
        guard.getBrain().remember(MemoryModuleType.WALK_TARGET,new WalkTarget(target.getBlockPos(),.85f,1),40);
        if(target instanceof HostileEntity hostile)hostile.setTarget(guard);
        double distance=guard.squaredDistanceTo(target);
        if(distance<9||distance>16){
            boolean melee=distance<9;
            for(int i=0;i<guard.getInventory().size();i++){
                var candidate=guard.getInventory().getStack(i);
                if(melee&&(candidate.isOf(VillageItems.RAPIER)||candidate.isOf(VillageItems.SABRE))||!melee&&candidate.getItem() instanceof MusketItem){
                    var old=guard.getMainHandStack();guard.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,candidate);guard.getInventory().setStack(i,old);break;
                }
            }
        }
        boolean ranged=guard.getMainHandStack().isOf(Items.CROSSBOW)||guard.getMainHandStack().getItem() instanceof MusketItem;
        if(ranged&&target instanceof HostileEntity&&distance<24*24&&distance>9&&guard.canSee(target)){
            guard.getNavigation().stop();guard.getLookControl().lookAt(target,30,30);
            shoot(w,guard,target);
        }else if(distance>(guard.getMainHandStack().isOf(Items.IRON_SPEAR)?25:6.76)){
            if(guard.getVehicle() instanceof HorseEntity horse){if(horse.getNavigation().isIdle()||w.getTime()%20==0)horse.getNavigation().startMovingTo(target,.95);}
            else if(guard.getNavigation().isIdle()||w.getTime()%20==0)guard.getNavigation().startMovingTo(target,.85);
        }else if(target instanceof VillagerEntity){
            // The arrest controller must get a chance to bind a criminal rather than killing them.
            return false;
        }else if(w.getTime()-ATTACKS.getOrDefault(guard.getUuid(),-100L)>=20){
            ATTACKS.put(guard.getUuid(),w.getTime());guard.swingHand(Hand.MAIN_HAND);
            target.damage(w,w.getDamageSources().mobAttack(guard),VillageEconomy.attackDamage(guard));VillageEras.work(w,guard,"guard",1);
        }
        return true;
    }
    static boolean surface(ServerWorld w,LivingEntity target){
        BlockPos p=target.getBlockPos();if(!w.isChunkLoaded(new ChunkPos(p).toLong()))return false;
        return p.getY()>=w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ())-2;
    }
    private record Responders(long at,List<UUID> ids){}
    private static final Map<ServerWorld,Map<UUID,Responders>> RESPONDERS=new WeakHashMap<>();
    private static boolean isGuardTarget(LivingEntity target){return target instanceof VillagerEntity v&&isGuard(v);}
    static boolean assigned(ServerWorld w,VillagerEntity guard,LivingEntity target){
        if(target instanceof net.minecraft.server.network.ServerPlayerEntity p&&(p.isCreative()||p.isSpectator()))return false;
        boolean wanted=target.getCommandTags().contains("halloween_wanted")||target.getCommandTags().contains("halloween_player_wanted");
        if(wanted){
            if(escorting(guard)||isGuardTarget(target)||target.getCommandTags().contains("halloween_surrender")||target.getCommandTags().contains("halloween_prisoner")||target.getCommandTags().contains("halloween_player_custody")||target.getCommandTags().contains("halloween_player_jailed"))return false;
            int level=target instanceof net.minecraft.server.network.ServerPlayerEntity player?PlayerJustice.wantedLevel(player):1;int range=level==3?160:target.getCommandTags().contains("halloween_player_escaped")?6:25;
            if(guard.squaredDistanceTo(target)>range*range||(level<3&&!guard.canSee(target)))return false;
            var responders=RESPONDERS.computeIfAbsent(w,k->new HashMap<>());var cached=responders.get(target.getUuid());
            if(cached==null||w.getTime()-cached.at()>=20){
                var nearby=w.getEntitiesByClass(VillagerEntity.class,new Box(target.getBlockPos()).expand(range,4,range),g->isGuard(g)&&!escorting(g)&&!g.getCommandTags().contains("halloween_bodyguard")&&!g.getCommandTags().contains("halloween_castle_guard")&&(level==3||g.canSee(target)));
                nearby.sort(Comparator.comparingDouble(target::squaredDistanceTo));cached=new Responders(w.getTime(),nearby.stream().limit(level==3?128:3).map(VillagerEntity::getUuid).toList());responders.put(target.getUuid(),cached);
                if(responders.size()>256)responders.entrySet().removeIf(e->w.getTime()-e.getValue().at()>40);
            }
            return cached.ids().contains(guard.getUuid());
        }
        if(escorting(guard)||guard.squaredDistanceTo(target)>24*24||!guard.canSee(target))return false;
        var responders=RESPONDERS.computeIfAbsent(w,k->new HashMap<>());var cached=responders.get(target.getUuid());
        if(cached==null||w.getTime()-cached.at()>=20){
            var nearby=w.getEntitiesByClass(VillagerEntity.class,new Box(target.getBlockPos()).expand(24,4,24),g->isGuard(g)&&!escorting(g)&&!g.getCommandTags().contains("halloween_bodyguard")&&!g.getCommandTags().contains("halloween_castle_guard")&&g.canSee(target));
            nearby.sort(Comparator.comparingDouble(target::squaredDistanceTo));cached=new Responders(w.getTime(),nearby.stream().limit(3).map(VillagerEntity::getUuid).toList());responders.put(target.getUuid(),cached);
        }
        return cached.ids().contains(guard.getUuid());
    }

    private VillageDefense(){}
}
