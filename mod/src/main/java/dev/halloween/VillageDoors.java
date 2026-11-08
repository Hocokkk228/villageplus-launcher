package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
/** Wooden household doors open for traffic and close after a clear three-second grace period. */
final class VillageDoors {
 private static final Map<ServerWorld,Map<BlockPos,Long>> CLOSING=new WeakHashMap<>();
 static void register(){net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(VillageDoors::tick);}
 static void tick(ServerWorld w){
  if(w.getPlayers().isEmpty()||w.getTime()%10!=0)return;
  var pending=CLOSING.computeIfAbsent(w,k->new HashMap<>());
  for(var e:w.iterateEntities())if(e instanceof VillagerEntity v&&v.isAlive()&&!v.isAiDisabled()){
   for(BlockPos q:BlockPos.iterateOutwards(v.getBlockPos(),2,1,2)){
    if(!w.isChunkLoaded(new ChunkPos(q).toLong()))continue;
    var state=w.getBlockState(q);
    if(!(state.getBlock() instanceof DoorBlock door)||!DoorBlock.canOpenByHand(state)||state.get(DoorBlock.HALF)!=DoubleBlockHalf.LOWER||state.get(DoorBlock.POWERED))continue;
    var p=q.toImmutable();
    if(state.get(DoorBlock.OPEN))pending.putIfAbsent(p,w.getTime()+60);
    else if(!v.isSleeping()&&!v.getNavigation().isIdle()&&v.squaredDistanceTo(Vec3d.ofCenter(p))<3.3){door.setOpen(v,w,state,p,true);v.getLookControl().lookAt(p.getX()+.5,p.getY()+1,p.getZ()+.5);v.swingHand(net.minecraft.util.Hand.OFF_HAND);pending.put(p,w.getTime()+60);}
   }
  }
  var it=pending.entrySet().iterator();while(it.hasNext()){
   var entry=it.next();var p=entry.getKey();if(w.getTime()<entry.getValue()||!w.isChunkLoaded(new ChunkPos(p).toLong()))continue;
   var state=w.getBlockState(p);if(!(state.getBlock() instanceof DoorBlock door)||!state.get(DoorBlock.OPEN)){it.remove();continue;}
   if(state.get(DoorBlock.POWERED)){it.remove();continue;}
   if(!w.getOtherEntities(null,new Box(p).stretch(0,1,0).expand(.4),e->e instanceof LivingEntity&&e.isAlive()).isEmpty())continue;
   door.setOpen(null,w,state,p,false);it.remove();
  }
 }
}
