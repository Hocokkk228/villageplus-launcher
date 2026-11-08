package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
/** Cologne-inspired castle replacement. Underground prison and live service rooms are masked out. */
final class VillageCathedral {
 static String id(long village){return "cologne_cathedral_v1:"+village;}
 static boolean advance(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement settlement){
  var projects=VillageProjects.get(w);var existing=projects.jobs.stream().filter(p->p.id.equals(id(team.village))).findFirst().orElse(null);if(existing!=null)return existing.done;
  var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
  var castles=memory.houses.stream().filter(h->h.recordKey.contains("/royal_castle:")&&VillageEras.team(w,h.min)==team).toList();
  if(castles.isEmpty())return true;
  var bounds=VillageRelocations.bounds(castles.getFirst());var base=bounds[0];
  var template=VillageEraPlans.template(w,"cologne_cathedral");if(template==null)return false;
  int width=template.size().getX(),depth=template.size().getZ();
  boolean loaded=true;for(int x=base.getX()>>4;x<=(base.getX()+width-1)>>4;x++)for(int z=base.getZ()>>4;z<=(base.getZ()+depth-1)>>4;z++)if(!VillageProjects.keepLoaded(w,new BlockPos(x*16,base.getY(),z*16)))loaded=false;
  if(!loaded)return false;
  List<Box> rooms=new ArrayList<>();Map<BlockPos,BlockState> owned=new HashMap<>();
  for(var h:castles)for(var r:h.blueprint)owned.put(r.pos(),r.state());
  // Keep recorded treasury/workshop volumes that share the castle plot.
  for(var h:memory.houses)if(!h.recordKey.contains("/royal_castle:")&&(h.kind.equals("house")||h.recordKey.contains("treasury")||h.recordKey.contains("factory")||h.recordKey.contains("workshop")||h.recordKey.contains("stable"))&&new Box(Vec3d.of(h.min),Vec3d.of(h.max.add(1,1,1))).intersects(new Box(Vec3d.of(base),Vec3d.of(base.add(width,template.size().getY(),depth)))))rooms.add(new Box(Vec3d.of(h.min),Vec3d.of(h.max.add(1,1,1))).expand(1));
  var markers=w.getEntitiesByClass(MarkerEntity.class,new Box(base).expand(128),m->m.getCommandTags().contains("halloween_jail"));for(var jail:markers)VillagePrisons.prepare(w,jail);
  for(BlockPos p:owned.keySet())if(w.getBlockState(p).hasBlockEntity()||w.getBlockState(p).getBlock() instanceof BedBlock)rooms.add(new Box(p).expand(1,1,1));
  for(var king:w.getEntitiesByClass(VillagerEntity.class,new Box(Vec3d.of(base),Vec3d.of(base.add(width,24,depth))),RoyalProfession::isKing))rooms.add(new Box(king.getBlockPos()).expand(3,2,3));
  Map<BlockPos,BlockState> plan=new LinkedHashMap<>();
  for(var e:owned.entrySet())if(e.getKey().getY()>base.getY()&&safe(w,e.getKey(),owned,rooms))plan.put(e.getKey(),Blocks.AIR.getDefaultState());
  for(var r:template.blocks()){
   var pos=base.add(r.pos());if(pos.getY()<base.getY()||!w.getWorldBorder().contains(pos)||!safe(w,pos,owned,rooms))continue;
   plan.put(pos,r.state());
  }
  projects.add(w,id(team.village),team.village,"cathedral",plan);return false;
 }
 static boolean safe(ServerWorld w,BlockPos p,Map<BlockPos,BlockState> owned,List<Box> rooms){
  if(VillagePrisons.managed(w,p)||rooms.stream().anyMatch(box->box.intersects(new Box(p))))return false;
  var old=w.getBlockState(p);if(old.hasBlockEntity())return false;
  var recorded=owned.get(p);
  return old.isAir()||old.isReplaceable()||VillageProjects.terrain(old)||recorded!=null&&old.isOf(recorded.getBlock());
 }
}
