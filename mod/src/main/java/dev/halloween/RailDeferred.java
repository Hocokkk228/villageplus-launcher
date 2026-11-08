package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
/** Defer only occupied blocks, never an entire nine-kilometre route because an animal is standing on one sleeper. */
final class RailDeferred extends PersistentState {
 static final com.mojang.serialization.Codec<RailDeferred> CODEC=VillageMemory.BlockRecord.CODEC.listOf().xmap(RailDeferred::new,s->new ArrayList<>(s.pending.values()));
 static final PersistentStateType<RailDeferred> TYPE=new PersistentStateType<>("halloween_rail_deferred",()->new RailDeferred(List.of()),CODEC,null);
 final Map<BlockPos,VillageMemory.BlockRecord> pending=new LinkedHashMap<>();
 RailDeferred(List<VillageMemory.BlockRecord> blocks){for(var r:blocks)pending.put(r.pos(),r);}
 static RailDeferred get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(TYPE);}
 static void defer(ServerWorld w,BlockPos p,BlockState s){var d=get(w);d.pending.put(p,new VillageMemory.BlockRecord(p,s));d.markDirty();}
 static void tick(ServerWorld w){var d=get(w);int count=0;for(var r:List.copyOf(d.pending.values())){if(++count>128)break;if(!VillageProjects.keepLoaded(w,r.pos()))continue;
  var occupants=VillageProjects.placementOccupants(w,r.pos(),r.state());if(!occupants.isEmpty()){VillageProjects.clearWorkArea(w,r.pos(),occupants);continue;}
  var actual=w.getBlockState(r.pos());if(actual.hasBlockEntity()&&!actual.isOf(RailContent.TRACK)||!RailTerrain.natural(actual)&&!actual.isReplaceable()&&!actual.isAir()&&!actual.isOf(RailContent.TRACK)&&!actual.isOf(Blocks.STONE_BRICKS)&&!actual.isOf(Blocks.GRAVEL))continue;
  w.setBlockState(r.pos(),r.state(),Block.NOTIFY_LISTENERS);if(r.state().isOf(RailContent.TRACK))for(var line:RailwayNetwork.get(w).lines){double at=line.path.closest(Vec3d.of(r.pos()));if(line.path.center(at).squaredDistanceTo(Vec3d.of(r.pos()))<40){RailTerrain.configure(w,line,r.pos(),Math.floor(at/16)*16);break;}}
  d.pending.remove(r.pos());d.markDirty();
 }}
}
