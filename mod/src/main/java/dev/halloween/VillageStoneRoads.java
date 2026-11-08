package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
/** Era III upgrades only recorded village paths, not arbitrary player paths. */
final class VillageStoneRoads {
 static void register(){net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(VillageStoneRoads::tick);}
 static void tick(ServerWorld w){
  if(w.getPlayers().isEmpty()||w.getTime()%200!=0)return;
  var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
  for(var team:memory.teams){
   if(VillageEraData.get(w).settlement(team).era<3||projects.jobs.stream().anyMatch(p->p.village==team.village&&p.kind.equals("stone_road")&&!p.done))continue;
   Map<BlockPos,BlockState> plan=new LinkedHashMap<>();
   for(var h:memory.houses){if(VillageEras.team(w,h.min)!=team)continue;
    for(var r:h.blueprint)if(r.state().isOf(Blocks.DIRT_PATH)&&w.isChunkLoaded(new ChunkPos(r.pos()).toLong())&&w.getBlockState(r.pos()).isOf(Blocks.DIRT_PATH)&&w.getFluidState(r.pos().up()).isEmpty()){
     plan.put(r.pos(),Math.floorMod(r.pos().getX()+r.pos().getZ(),7)==0?Blocks.ANDESITE.getDefaultState():Blocks.STONE_BRICKS.getDefaultState());if(plan.size()>=128)break;
    }if(plan.size()>=128)break;
   }
   if(!plan.isEmpty())projects.add(w,"stone_roads:"+team.village+":"+w.getTime(),team.village,"stone_road",plan);
  }
 }
}
