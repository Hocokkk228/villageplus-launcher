package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.*;

final class VillageFights {
    static final float KNOCKDOWN_HEALTH=6;
    private static final int PUNCH_WINDUP=5, KICK_WINDUP=8;
    private static final Map<ServerWorld,Map<UUID,Bout>> BOUTS=new WeakHashMap<>();
    private static final Map<VillagerEntity,Long> REACTION=new WeakHashMap<>();
    private static final Map<ServerPlayerEntity,Long> RECENT_TARGET=new WeakHashMap<>();

    enum Stage { FIGHT, GROUND, FLEE, RISE }
    static final class Fighter {
        final UUID id;
        boolean left;
        long poseUntil,hitAt;
        Fighter(UUID id){this.id=id;}
    }
    static final class Bout {
        final UUID target;
        final List<Fighter> fighters;
        final List<UUID> crowd;
        final long started;
        final Vec3d origin;
        final boolean gang,lethal;
        Stage stage=Stage.FIGHT;
        long phaseAt,nextStrike,clearSince=-1;
        int turn,side,kicks;
        float bodyYaw;
        Vec3d escapeDirection;
        Bout(ServerPlayerEntity target,List<VillagerEntity> fighters,List<UUID> crowd,
             long now,boolean gang,boolean lethal,int side){
            this.target=target.getUuid();
            this.fighters=fighters.stream().map(v->new Fighter(v.getUuid())).toList();
            this.crowd=List.copyOf(crowd);
            this.started=now;
            this.origin=target.getEntityPos();
            this.gang=gang;
            this.lethal=lethal;
            this.side=side;
        }
    }

    static void register(){
        ServerTickEvents.END_WORLD_TICK.register(VillageFights::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity,source,base,taken,blocked)->{
            if(taken>0&&entity instanceof VillagerEntity villager&&source.getAttacker() instanceof ServerPlayerEntity player
                    &&villager.getEntityWorld() instanceof ServerWorld world) react(world,villager,player);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->{
            if(entity instanceof VillagerEntity villager&&source.getSource()==source.getAttacker()
                    &&source.getAttacker() instanceof LivingEntity attacker
                    &&villager.getEntityWorld() instanceof ServerWorld world
                    &&frontGuard(villager,attacker)&&world.random.nextFloat()<.38f){
                var fighter=findFighter(world,villager.getUuid());
                if(fighter!=null){
                    fighter.poseUntil=world.getTime()+9;
                    FightPose.of(villager).halloween$fightPose(FightPose.BLOCK);
                    world.playSound(null,villager.getBlockPos(),SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE,SoundCategory.PLAYERS,.9f,.7f);
                    return false;
                }
            }
            return true;
        });
        AttackEntityCallback.EVENT.register((p,w,h,e,hit)->locked(p)?ActionResult.FAIL:ActionResult.PASS);
        AttackBlockCallback.EVENT.register((p,w,h,pos,dir)->locked(p)?ActionResult.FAIL:ActionResult.PASS);
        UseEntityCallback.EVENT.register((p,w,h,e,hit)->locked(p)?ActionResult.FAIL:ActionResult.PASS);
        UseBlockCallback.EVENT.register((p,w,h,pos)->locked(p)?ActionResult.FAIL:ActionResult.PASS);
        UseItemCallback.EVENT.register((p,w,h)->locked(p)?ActionResult.FAIL:ActionResult.PASS);
    }

    private static boolean locked(net.minecraft.entity.player.PlayerEntity player){
        return FightPose.down(FightPose.of(player).halloween$fightPose());
    }

    static boolean owns(ServerWorld world,VillagerEntity villager){
        var bouts=BOUTS.get(world);
        if(bouts==null)return false;
        var id=villager.getUuid();
        return bouts.values().stream().anyMatch(b->b.fighters.stream().anyMatch(f->f.id.equals(id))
                ||b.stage==Stage.FLEE&&b.crowd.contains(id));
    }

    private static Fighter findFighter(ServerWorld world,UUID id){
        var bouts=BOUTS.get(world);
        if(bouts==null)return null;
        for(var bout:bouts.values())for(var fighter:bout.fighters)if(fighter.id.equals(id))return fighter;
        return null;
    }

    private static boolean frontGuard(VillagerEntity defender,LivingEntity attacker){
        int pose=FightPose.of(defender).halloween$fightPose();
        var towards=attacker.getEntityPos().subtract(defender.getEntityPos()).multiply(1,0,1);
        return (pose==FightPose.GUARD||pose==FightPose.BLOCK)&&towards.lengthSquared()<9
                &&Vec3d.fromPolar(0,defender.getBodyYaw()).dotProduct(towards.normalize())>.4
                &&defender.canSee(attacker);
    }

    static void robbery(ServerWorld world,ServerPlayerEntity player,BlockPos scene){
        var witness=world.getEntitiesByClass(VillagerEntity.class,new Box(scene).expand(12,5,12),
                v->canDefend(world,v)&&v.canSee(player)&&v.squaredDistanceTo(player)<12*12).stream()
                .min(Comparator.comparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(scene)))).orElse(null);
        if(witness!=null)react(world,witness,player);
    }

    private static boolean canDefend(ServerWorld world,VillagerEntity v){
        return available(v)&&!v.isBaby()&&!v.isSleeping()&&!RoyalProfession.isKing(v)
                &&!VillageDefense.isGuard(v)&&!v.getCommandTags().contains("halloween_bandit")
                &&!StreetCrime.active(world,v)&&!owns(world,v);
    }

    private static boolean available(VillagerEntity v){
        return v.isAlive()&&!v.hasVehicle()&&!v.isAiDisabled()&&!v.isLeashed()&&!VillageTrials.busy(v)
                &&!v.getCommandTags().contains("halloween_prisoner");
    }

    private static void react(ServerWorld world,VillagerEntity villager,ServerPlayerEntity player){
        if(!StreetCrime.targetable(player)||!canDefend(world,villager)||!ready(world,player)
                ||REACTION.getOrDefault(villager,0L)>world.getTime())return;
        REACTION.put(villager,world.getTime()+300);
        if(world.random.nextFloat()<.60f)start(world,player,List.of(villager),List.of(),false);
    }

    static boolean running(ServerWorld world,ServerPlayerEntity target){
        return BOUTS.getOrDefault(world,Map.of()).containsKey(target.getUuid());
    }

    static boolean ready(ServerWorld world,ServerPlayerEntity target){
        return RECENT_TARGET.getOrDefault(target,0L)<=world.getTime()
                &&!BOUTS.getOrDefault(world,Map.of()).containsKey(target.getUuid());
    }

    static boolean start(ServerWorld world,ServerPlayerEntity target,List<VillagerEntity> fighters,List<UUID> crowd,boolean gang){
        if(!ready(world,target)||fighters.isEmpty()||fighters.stream().anyMatch(v->owns(world,v)))return false;
        boolean lethal=world.random.nextFloat()<(gang?.15f:.30f);
        var bout=new Bout(target,fighters,crowd,world.getTime(),gang,lethal,world.random.nextBoolean()?1:-1);
        begin(world,target,bout,fighters);
        return true;
    }

    static void begin(ServerWorld world,ServerPlayerEntity target,Bout bout,List<VillagerEntity> fighters){
        BOUTS.computeIfAbsent(world,ignored->new HashMap<>()).put(target.getUuid(),bout);
        for(var fighter:fighters){
            VillageInjuries.calm(fighter);
            VillageProjects.resetWorker(fighter);
            VillageTasks.hold(fighter);
            fighter.setAttacking(true);
            FightPose.of(fighter).halloween$fightPose(FightPose.GUARD);
        }
        target.sendMessage(net.minecraft.text.Text.translatable(bout.gang?"message.halloween.gang_assault":"message.halloween.villager_defends"),true);
    }

    static void interruptGang(ServerWorld world,ServerPlayerEntity player){
        var bout=BOUTS.getOrDefault(world,Map.of()).get(player.getUuid());
        if(bout==null||!bout.gang)return;
        if(bout.stage==Stage.FIGHT)finish(world,bout,player,false);
        else if(bout.stage==Stage.GROUND)flee(world,player,bout);
    }

    static void tick(ServerWorld world){
        StreetCrime.tickPunches(world);
        var bouts=BOUTS.get(world);
        if(bouts==null)return;
        for(var bout:List.copyOf(bouts.values())){
            var target=world.getEntity(bout.target);
            if(target instanceof ServerPlayerEntity dead&&!dead.isAlive()&&bout.stage==Stage.FLEE
                    &&world.getTime()-bout.phaseAt<120){
                for(var id:escaping(bout))if(world.getEntity(id) instanceof VillagerEntity v&&available(v)
                        &&world.getTime()%12==Math.floorMod(v.getId(),12))fleeFrom(world,v,dead.getEntityPos(),bout.escapeDirection);
                continue;
            }
            if(!(target instanceof ServerPlayerEntity player)||!StreetCrime.targetable(player)
                    ||bout.stage==Stage.FIGHT&&player.getEntityPos().squaredDistanceTo(bout.origin)>36*36){
                finish(world,bout,target instanceof ServerPlayerEntity p?p:null,false);
                continue;
            }
            step(world,player,bout);
        }
        if(bouts.isEmpty())BOUTS.remove(world);
    }

    private static void step(ServerWorld world,ServerPlayerEntity player,Bout bout){
        long now=world.getTime();
        if(bout.stage!=Stage.FIGHT){
            var velocity=player.getVelocity();
            player.setVelocity(0,velocity.y,0);
        }
        if(bout.stage==Stage.RISE){
            if(now-bout.phaseAt>=FightPose.RISE_TICKS)finish(world,bout,player,true);
            return;
        }
        if(bout.stage==Stage.FLEE){
            fleeTick(world,player,bout,now);
            return;
        }
        var active=bout.fighters.stream().filter(f->world.getEntity(f.id) instanceof VillagerEntity v&&available(v)).toList();
        if(active.isEmpty()||bout.stage==Stage.FIGHT&&now-bout.started>460){
            if(bout.stage==Stage.FIGHT)finish(world,bout,player,false);
            else flee(world,player,bout);
            return;
        }
        if(bout.stage==Stage.GROUND&&now-bout.phaseAt>=FightPose.FALL_TICKS)
            FightPose.of(player).halloween$fightPose(FightPose.DOWN);
        if(bout.stage==Stage.GROUND&&(now-bout.phaseAt>170||!bout.lethal&&bout.kicks>=active.size()*2)){
            flee(world,player,bout);
            return;
        }
        for(int i=0;i<active.size();i++){
            var fighter=active.get(i);
            var villager=(VillagerEntity)world.getEntity(fighter.id);
            move(world,player,bout,villager,i,now);
            if(fighter.hitAt!=0&&now>=fighter.hitAt){
                fighter.hitAt=0;
                if(villager.squaredDistanceTo(player)<(bout.stage==Stage.GROUND?7.3:5.3)
                        &&villager.canSee(player))hit(world,player,bout,villager);
                if(bout.stage==Stage.GROUND&&now==bout.phaseAt)return;
                if(!player.isAlive()){flee(world,player,bout);return;}
            }
            if(fighter.poseUntil<=now)FightPose.of(villager).halloween$fightPose(FightPose.GUARD);
        }
        if(now<bout.nextStrike||bout.stage==Stage.GROUND&&now-bout.phaseAt<FightPose.FALL_TICKS+4)return;
        for(int n=0;n<active.size();n++){
            int index=(bout.turn+n)%active.size();
            var fighter=active.get(index);
            var villager=(VillagerEntity)world.getEntity(fighter.id);
            if(fighter.poseUntil>now||villager.squaredDistanceTo(player)>(bout.stage==Stage.GROUND?6.25:4.4)
                    ||!villager.canSee(player))continue;
            boolean kick=bout.stage==Stage.GROUND;
            int pose=kick?FightPose.KICK:fighter.left?FightPose.LEFT:FightPose.RIGHT;
            if(!kick)fighter.left=!fighter.left;
            fighter.hitAt=now+(kick?KICK_WINDUP:PUNCH_WINDUP);
            fighter.poseUntil=now+(kick?21:14);
            bout.nextStrike=now+(kick?13:17);
            bout.turn=index+1;
            FightPose.of(villager).halloween$fightPose(pose);
            break;
        }
    }

    private static void move(ServerWorld world,ServerPlayerEntity player,Bout bout,VillagerEntity v,int index,long now){
        VillageInjuries.calm(v);
        VillageTasks.hold(v);
        v.getBrain().forget(MemoryModuleType.WALK_TARGET);
        var delta=player.getEntityPos().subtract(v.getEntityPos());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        v.setHeadYaw(yaw);
        v.setBodyYaw(MathHelper.stepUnwrappedAngleTowards(v.getBodyYaw(),yaw,22));
        v.getLookControl().lookAt(player,25,30);
        if(bout.stage==Stage.FIGHT){
            if(delta.lengthSquared()<=4.4&&v.canSee(player)){v.getNavigation().stop();return;}
            if(now%10==Math.floorMod(v.getId(),10))StreetCrime.pursue(world,v,player,.9);
            return;
        }
        int side=index==2?-bout.side:bout.side;
        double along=index==0?-.40:index==1?-1.45:-.95;
        var spot=player.getEntityPos().add(RailGeometry.rotate(new Vec3d(side*1.34,0,along),bout.bodyYaw));
        if(v.squaredDistanceTo(spot)<.64){v.getNavigation().stop();return;}
        if(now%10==Math.floorMod(v.getId(),10)&&VillagePathBudget.take(world)){
            var nav=v.getNavigation();
            var path=nav.findPathTo(BlockPos.ofFloored(spot),0);
            if(path!=null&&path.reachesTarget())nav.startMovingAlong(path,.83);
            else if(delta.lengthSquared()>6)StreetCrime.pursue(world,v,player,.83);
        }
    }

    private static void hit(ServerWorld world,ServerPlayerEntity player,Bout bout,VillagerEntity attacker){
        if(bout.stage==Stage.FIGHT){
            if(player.getHealth()>KNOCKDOWN_HEALTH){
                float amount=Math.min(4,player.getHealth()-KNOCKDOWN_HEALTH);
                if(!player.damage(world,world.getDamageSources().mobAttack(attacker),amount))return;
                VillageInjuries.impact(world,player,false,amount);
            }
            if(player.getHealth()<=KNOCKDOWN_HEALTH+.001f){
                bout.stage=Stage.GROUND;
                bout.phaseAt=world.getTime();
                bout.nextStrike=bout.phaseAt+FightPose.FALL_TICKS+4;
                bout.bodyYaw=fallDirection(world,player);
                FightPose.of(player).halloween$knockdownYaw(bout.bodyYaw);
                player.stopUsingItem();
                player.closeHandledScreen();
                player.setVelocity(Vec3d.ZERO);
                FightPose.of(player).halloween$fightPose(FightPose.FALL);
                for(var fighter:bout.fighters)fighter.hitAt=0;
            }
        }else if(bout.stage==Stage.GROUND){
            bout.kicks++;
            if(bout.lethal)player.damage(world,world.getDamageSources().mobAttack(attacker),5);
            VillageInjuries.impact(world,player,true,5);
        }
        if(bout.gang){
            attacker.addCommandTag("halloween_crime_street_assault");
            boolean seen=!world.getEntitiesByClass(VillagerEntity.class,attacker.getBoundingBox().expand(16,5,16),
                    v->v!=attacker&&v.isAlive()&&!v.getCommandTags().contains("halloween_bandit")&&v.canSee(attacker)).isEmpty();
            if(seen){
                attacker.addCommandTag("halloween_wanted");
                if(!player.isAlive())attacker.addCommandTag("halloween_crime_murder");
            }
        }
    }

    private static float fallDirection(ServerWorld world,ServerPlayerEntity player){
        for(int offset:new int[]{0,45,-45,90,-90,135,-135,180}){
            float yaw=player.getYaw()+offset;
            var behind=Vec3d.fromPolar(0,yaw).multiply(-1.7);
            var horizontalBody=new Box(-.30,.04,-.30,.30,.62,.30).offset(player.getEntityPos()).stretch(behind);
            if(world.isBlockSpaceEmpty(player,horizontalBody))return yaw;
        }
        return player.getYaw();
    }

    private static List<UUID> escaping(Bout bout){
        var ids=new LinkedHashSet<>(bout.crowd);
        for(var fighter:bout.fighters)ids.add(fighter.id);
        return List.copyOf(ids);
    }

    private static void flee(ServerWorld world,ServerPlayerEntity player,Bout bout){
        bout.stage=Stage.FLEE;
        bout.phaseAt=world.getTime();
        bout.escapeDirection=Vec3d.fromPolar(0,player.getYaw()+90*bout.side);
        for(var id:escaping(bout))if(world.getEntity(id) instanceof VillagerEntity v&&available(v)){
            FightPose.of(v).halloween$fightPose(FightPose.NONE);
            v.setAttacking(false);
            v.setSprinting(true);
            v.getNavigation().stop();
            fleeFrom(world,v,player.getEntityPos(),bout.escapeDirection);
        }
    }

    private static void fleeFrom(ServerWorld world,VillagerEntity v,Vec3d origin,Vec3d direction){
        VillageTasks.hold(v);
        var nav=v.getNavigation();
        if(!nav.isIdle()||!VillagePathBudget.take(world))return;
        nav.setMaxFollowRange(64);
        nav.setCanOpenDoors(true);
        var desired=origin.add(direction.multiply(30));
        var lateral=new Vec3d(-direction.z,0,direction.x);
        var goals=new HashSet<BlockPos>();
        for(int side:new int[]{-6,-3,0,3,6}){
            var center=BlockPos.ofFloored(desired.add(lateral.multiply(side)));
            if(!world.isChunkLoaded(center))continue;
            for(int y=-4;y<=4;y++){
                var p=center.up(y);
                if(world.getBlockState(p.down()).isSolidBlock(world,p.down())
                        &&world.isBlockSpaceEmpty(v,v.getBoundingBox().offset(Vec3d.ofBottomCenter(p).subtract(v.getEntityPos()))))
                    goals.add(p);
            }
        }
        var path=goals.isEmpty()?null:nav.findPathTo(goals,0);
        if(path!=null&&path.reachesTarget())nav.startMovingAlong(path,1.26);
        else{
            var point=NoPenaltyTargeting.findFrom(v,16,7,origin);
            if(point!=null)nav.startMovingTo(point.x,point.y,point.z,1.26);
        }
    }

    private static void fleeTick(ServerWorld world,ServerPlayerEntity player,Bout bout,long now){
        boolean visible=false;
        for(var id:escaping(bout))if(world.getEntity(id) instanceof VillagerEntity v&&available(v)){
            boolean near=v.squaredDistanceTo(player)<24*24;
            var to=v.getEyePos().subtract(player.getEyePos());
            var gaze=Vec3d.fromPolar(player.getPitch(),player.getYaw());
            visible|=to.lengthSquared()<64*64&&gaze.dotProduct(to.normalize())>Math.cos(Math.toRadians(65))&&player.canSee(v);
            if(near&&now%12==Math.floorMod(v.getId(),12))fleeFrom(world,v,player.getEntityPos(),bout.escapeDirection);
        }
        if(visible)bout.clearSince=-1;
        else if(bout.clearSince<0)bout.clearSince=now;
        if(bout.clearSince>=0&&now-bout.clearSince>=60){
            bout.stage=Stage.RISE;
            bout.phaseAt=now;
            FightPose.of(player).halloween$fightPose(FightPose.RISE);
        }
    }

    private static void finish(ServerWorld world,Bout bout,ServerPlayerEntity player,boolean recovered){
        for(var id:escaping(bout))if(world.getEntity(id) instanceof VillagerEntity v){
            FightPose.of(v).halloween$fightPose(FightPose.NONE);
            v.setAttacking(false);
            v.setSprinting(false);
        }
        if(player!=null){
            FightPose.of(player).halloween$fightPose(FightPose.NONE);
            RECENT_TARGET.put(player,world.getTime()+400);
            if(recovered)player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,240,1));
        }
        BOUTS.getOrDefault(world,Map.of()).remove(bout.target);
    }

    private VillageFights(){}
}
