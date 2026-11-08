package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
/** Roads follow navigable ground between recorded entrances and the village centre. */
final class VillageRoadNetwork {
 private static final Map<Long,Integer> CURSOR=new HashMap<>();
 static void register(){ServerTickEvents.END_WORLD_TICK.register(VillageRoadNetwork::tick);}
 private static void tick(ServerWorld w){
  if(w.getPlayers().isEmpty()||w.getTime()%100!=0)return;var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
  for(var team:List.copyOf(memory.teams)){
   var settlement=VillageEraData.get(w).settlement(team);if(settlement.era<2&&settlement.transitionAt<0)continue;
   if(w.getPlayers().stream().noneMatch(p->p.getBlockPos().isWithinDistance(team.anchor,320)))continue;
   var doors=memory.houses.stream().filter(h->VillageEras.team(w,h.min)==team).flatMap(h->h.blueprint.stream()).filter(r->r.state().getBlock() instanceof DoorBlock&&r.state().get(DoorBlock.HALF)==DoubleBlockHalf.LOWER).map(VillageMemory.BlockRecord::pos).distinct().toList();
   if(settlement.plantation!=null){
    var farm=settlement.plantation.add(12,1,-2);String farmId="road_network_farm_v2:"+team.village;
    if(!projects.exists(farmId)&&w.isChunkLoaded(new ChunkPos(farm).toLong())&&VillagePathBudget.take(w)){
     var farmPlan=plan(w,farm,team.anchor);if(!farmPlan.isEmpty())projects.add(w,farmId,team.village,"road",farmPlan);
    }
   }
   for(var road:List.copyOf(projects.jobs))if(road.village==team.village&&road.done&&road.kind.equals("road")){
    String lightId="road_lights_v1:"+road.id;if(projects.exists(lightId))continue;
    Map<BlockPos,BlockState> lights=new LinkedHashMap<>();int index=0;
    for(var c:road.plan){if(!c.after().isOf(Blocks.DIRT_PATH)||index++%24!=0)continue;
     for(Direction direction:Direction.Type.HORIZONTAL){var ground=c.pos().offset(direction,2);var post=ground.up();
      if(!w.isChunkLoaded(new ChunkPos(ground).toLong())||!w.getBlockState(ground).isSolidBlock(w,ground)||w.getBlockState(ground).isOf(Blocks.DIRT_PATH)||!w.getBlockState(post).isAir()||!w.getBlockState(post.up()).isAir()||!w.getBlockState(post.up(2)).isAir())continue;
      if(lights.keySet().stream().anyMatch(p->p.isWithinDistance(post,10)))break;
      lights.put(post,Blocks.STONE_BRICK_WALL.getDefaultState());lights.put(post.up(),Blocks.STONE_BRICK_WALL.getDefaultState());lights.put(post.up(2),Blocks.LANTERN.getDefaultState());break;
     }
    }
    if(!lights.isEmpty()){projects.add(w,lightId,team.village,"streetlight",lights);break;}
   }
   if(doors.isEmpty())continue;int cursor=Math.floorMod(CURSOR.getOrDefault(team.village,0),doors.size());CURSOR.put(team.village,cursor+1);var door=doors.get(cursor);String id="road_network_v1:"+team.village+":"+door.asLong();if(projects.exists(id))continue;
   if(!w.isChunkLoaded(new ChunkPos(door).toLong()))continue;
   if(!VillagePathBudget.take(w))continue;var plan=plan(w,door,team.anchor);
   if(!plan.isEmpty())projects.add(w,id,team.village,"road",plan);
  }
 }
 static Map<BlockPos,BlockState> plan(ServerWorld w,BlockPos door,BlockPos centre){
   var walker=new VillagerEntity(EntityType.VILLAGER,w);walker.setPosition(door.getX()+.5,door.getY(),door.getZ()+.5);walker.setOnGround(true);walker.getNavigation().setCanOpenDoors(true);walker.getNavigation().setMaxFollowRange(384);
   var path=walker.getNavigation().findPathTo(centre,2);if(path==null||!path.reachesTarget())return new LinkedHashMap<>();
   Map<BlockPos,BlockState> plan=new LinkedHashMap<>();
   for(int i=0;i<path.getLength();i++){
    var ground=path.getNodePos(i).down();
    for(int dx=0;dx<=1;dx++)for(int dz=0;dz<=1;dz++){
     var p=ground.add(dx,0,dz);if(!w.isChunkLoaded(new ChunkPos(p).toLong()))continue;var old=w.getBlockState(p);var above=w.getBlockState(p.up());
     if((old.isOf(Blocks.DIRT)||old.isOf(Blocks.GRASS_BLOCK)||old.isOf(Blocks.COARSE_DIRT)||old.isOf(Blocks.PODZOL))&&above.getCollisionShape(w,p.up()).isEmpty())plan.put(p,VillageEras.settlement(w,door)!=null&&VillageEras.settlement(w,door).era>=3?Blocks.STONE_BRICKS.getDefaultState():Blocks.DIRT_PATH.getDefaultState());
    }
   }
   return plan;
 }
 private VillageRoadNetwork(){}
}
