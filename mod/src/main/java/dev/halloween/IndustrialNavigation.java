package dev.halloween;
import java.util.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
/** Bounded navigation for long warehouse trips. Progress uses reachable paths, never teleportation. */
final class IndustrialNavigation {
 static final Map<VillagerEntity,Long> NEXT=new WeakHashMap<>();
 static void move(ServerWorld w,VillagerEntity worker,BlockPos target){move(w,worker,target,1);}
 static void exact(ServerWorld w,VillagerEntity worker,BlockPos target){move(w,worker,target,0);}
 private static void move(ServerWorld w,VillagerEntity worker,BlockPos target,int tolerance){
  VillageTasks.hold(worker);worker.getNavigation().setCanOpenDoors(true);worker.getNavigation().setMaxFollowRange(96);
  if(NEXT.getOrDefault(worker,0L)>w.getTime())return;NEXT.put(worker,w.getTime()+20);
  double before=worker.getEntityPos().distanceTo(Vec3d.ofCenter(target));
  if(VillagePathBudget.take(w)){var path=worker.getNavigation().findPathTo(target,tolerance);
   if(path!=null&&path.getLength()>1&&(path.reachesTarget()||Vec3d.ofBottomCenter(path.getNodePos(path.getLength()-1)).distanceTo(Vec3d.ofCenter(target))<before-2)){worker.getNavigation().startMovingAlong(path,.9);return;}}
  var direction=Vec3d.ofBottomCenter(target).subtract(worker.getEntityPos());double distance=direction.horizontalLength();if(distance<8)return;var center=BlockPos.ofFloored(worker.getEntityPos().add(direction.multiply(Math.min(40,distance)/distance)));
  int probes=0;for(var q:BlockPos.iterateOutwards(center,4,3,4)){
   if(!w.isChunkLoaded(q)||!w.getBlockState(q.down()).isSolidBlock(w,q.down())||!w.getBlockState(q).getCollisionShape(w,q).isEmpty()||!w.getBlockState(q.up()).getCollisionShape(w,q.up()).isEmpty()||!w.getFluidState(q).isEmpty())continue;
   if(++probes>4||!VillagePathBudget.take(w))return;var path=worker.getNavigation().findPathTo(q,0);if(path!=null&&path.reachesTarget()){worker.getNavigation().startMovingAlong(path,.9);return;}
  }
 }
 private IndustrialNavigation(){}
}
