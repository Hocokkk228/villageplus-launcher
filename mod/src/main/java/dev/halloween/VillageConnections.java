package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
/** Re-evaluate neighbour-dependent geometry, including panels placed by old FORCE_STATE builds. */
final class VillageConnections {
 private static final Map<ServerWorld,Integer> CURSOR=new WeakHashMap<>();
 private record Windows(int houses,long until,List<BlockPos> positions){}
 private static final Map<ServerWorld,Windows> WINDOWS=new WeakHashMap<>();
 static BlockState connected(ServerWorld w,BlockPos pos,BlockState state){return state.getBlock() instanceof PaneBlock||state.getBlock() instanceof FenceBlock||state.getBlock() instanceof WallBlock||state.getBlock() instanceof StairsBlock?Block.postProcessState(state,w,pos):state;}
 static boolean matches(ServerWorld w,BlockPos pos,BlockState expected,BlockState actual){
  if(expected.isOf(Blocks.LECTERN)&&actual.isOf(RoyalProfession.CEREMONIAL_LECTERN))
   return expected.get(LecternBlock.FACING)==actual.get(CeremonialLecternBlock.FACING);
  return actual.equals(connected(w,pos,expected));
 }
 static void register(){net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(w->{
  if(w.getPlayers().isEmpty()||w.getTime()%20!=0)return;
  var houses=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses;
  var cache=WINDOWS.get(w);
  if(cache==null||cache.houses()!=houses.size()||cache.until()<=w.getTime()){
   var found=new LinkedHashSet<BlockPos>();for(var h:houses)for(var r:h.blueprint)if(r.state().getBlock() instanceof PaneBlock)found.add(r.pos());
   cache=new Windows(houses.size(),w.getTime()+600,List.copyOf(found));WINDOWS.put(w,cache);
  }
  var panes=cache.positions();
  if(panes.isEmpty())return;int start=CURSOR.getOrDefault(w,0);
  for(int i=0;i<Math.min(128,panes.size());i++){
   var p=panes.get(Math.floorMod(start+i,panes.size()));if(!w.isChunkLoaded(new ChunkPos(p).toLong()))continue;
   var state=w.getBlockState(p);if(!(state.getBlock() instanceof PaneBlock))continue;
   var fixed=connected(w,p,state);if(!fixed.equals(state))w.setBlockState(p,fixed,Block.NOTIFY_ALL);
  }CURSOR.put(w,Math.floorMod(start+128,panes.size()));
 });}
}
