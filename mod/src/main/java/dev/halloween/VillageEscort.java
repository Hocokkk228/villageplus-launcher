package dev.halloween;

import java.util.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Convoys retain a route while moving; do not replace it with an unreachable exact node every tick. */
final class VillageEscort {
    private record Progress(BlockPos goal,Vec3d pos,long moved,long next){}
    private static final Map<VillagerEntity,Progress> STATE=new WeakHashMap<>();
    static void move(VillagerEntity guard,BlockPos target,float speed){
        if(guard.isTouchingWater()){VillageWaterRescue.swimToShore((ServerWorld)guard.getEntityWorld(),guard);return;}
        VillageTasks.hold(guard);if(guard.isSleeping())guard.wakeUp();ServerWorld w=(ServerWorld)guard.getEntityWorld();long now=w.getTime();
        guard.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.INTERACTION_TARGET);guard.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.LOOK_TARGET);
        var previous=STATE.get(guard);boolean moved=previous==null||guard.getEntityPos().squaredDistanceTo(previous.pos())>.16;
        long since=moved?now:previous.moved();boolean changed=previous==null||!target.equals(previous.goal());
        if(!changed&&previous.next()>now)return;
        if(!changed&&!guard.getNavigation().isIdle()&&now-since<120){STATE.put(guard,new Progress(target,guard.getEntityPos(),since,now+20));return;}
        STATE.put(guard,new Progress(target,guard.getEntityPos(),since,now+20));
        // Search to the actual destination so a wall does not become an artificial intermediate goal.
        var navigation=guard.getNavigation();navigation.setCanOpenDoors(true);navigation.setMaxFollowRange(128);
        Set<BlockPos> candidates=new LinkedHashSet<>();
        for(BlockPos q:BlockPos.iterateOutwards(target,1,1,1)){
            if(!w.isChunkLoaded(new ChunkPos(q).toLong())||!w.getWorldBorder().contains(q)||!w.getBlockState(q).getCollisionShape(w,q).isEmpty()||!w.getBlockState(q.up()).getCollisionShape(w,q.up()).isEmpty()||w.getBlockState(q.down()).getCollisionShape(w,q.down()).isEmpty())continue;
            if(Vec3d.ofBottomCenter(q).squaredDistanceTo(Vec3d.ofCenter(target))>1.5)continue;
            candidates.add(q.toImmutable());
        }
        if(!budget(w,guard)){STATE.put(guard,new Progress(target,guard.getEntityPos(),since,now+1));return;}
        var path=candidates.isEmpty()?null:navigation.findPathTo(candidates,0);
        if((path==null||!path.reachesTarget())&&now-since>=100){
            Set<BlockPos> alternatives=new LinkedHashSet<>();
            for(BlockPos q:BlockPos.iterateOutwards(target,3,1,3))if(Math.abs(q.getY()-target.getY())<=1&&w.isChunkLoaded(new ChunkPos(q).toLong())&&w.getWorldBorder().contains(q)&&w.getBlockState(q).getCollisionShape(w,q).isEmpty()&&w.getBlockState(q.up()).getCollisionShape(w,q.up()).isEmpty()&&!w.getBlockState(q.down()).getCollisionShape(w,q.down()).isEmpty())alternatives.add(q.toImmutable());
            if(!alternatives.isEmpty()&&budget(w,guard))path=navigation.findPathTo(alternatives,0);
        }
        if(path!=null&&path.getLength()>1&&path.getEnd()!=null){
            var end=path.getEnd().getBlockPos();boolean sameLevel=Math.abs(end.getY()-target.getY())<=1;
            boolean belowGoal=Math.abs(end.getX()-target.getX())<=3&&Math.abs(end.getZ()-target.getZ())<=3&&!sameLevel;
            if(path.reachesTarget()&&sameLevel||!path.reachesTarget()&&!belowGoal&&end.getSquaredDistance(target)+4<guard.getBlockPos().getSquaredDistance(target))navigation.startMovingAlong(path,speed);
        }

    }
    private static boolean budget(ServerWorld w,VillagerEntity guard){return VillageDefense.escorting(guard)?VillagePathBudget.takeConvoy(w):VillagePathBudget.takeEscort(w);}
    private VillageEscort(){}
}
