package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
/** Build -> transfer actual stock -> remap homes -> remove recorded old blocks. Each phase is durable. */
final class VillageHousingRebuild {
 static String id(long village,VillageMemory.House h){return "gothic_rebuild:"+village+":"+h.min.asLong();}
 static boolean ordinary(VillageMemory.House h){
  if(!h.kind.equals("house")||(h.recordKey.startsWith("gothic_rebuild:")||h.recordKey.startsWith("gothic_expansion:")||h.recordKey.startsWith("gothic_infill_")||h.recordKey.startsWith("steam_residence_"))||h.recordKey.contains("profession_")||h.recordKey.contains("workshop")||h.recordKey.contains("royal_castle"))return false;
  for(String role:VillageEras.PROFESSIONS)if(h.recordKey.contains("_"+role+"_")||h.recordKey.contains(":"+role+":"))return false;
  if(h.blueprint.stream().anyMatch(r->Set.of(Blocks.BLAST_FURNACE,Blocks.GRINDSTONE,Blocks.SMITHING_TABLE,Blocks.STONECUTTER,Blocks.FLETCHING_TABLE,Blocks.SMOKER,Blocks.LOOM,Blocks.CARTOGRAPHY_TABLE,Blocks.BREWING_STAND,Blocks.LECTERN).contains(r.state().getBlock())))return false;
  return h.blueprint.stream().anyMatch(r->r.state().getBlock() instanceof BedBlock);
 }
 static boolean contains(VillageMemory.House h,BlockPos p){return p.getX()>=h.min.getX()&&p.getX()<=h.max.getX()&&p.getY()>=h.min.getY()&&p.getY()<=h.max.getY()&&p.getZ()>=h.min.getZ()&&p.getZ()<=h.max.getZ();}
 static boolean mayRemove(ServerWorld w,BlockPos p){
  for(var h:w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses){
   var team=VillageEras.team(w,h.min);if(team==null||!ordinary(h)||!contains(h,p))continue;
   if(VillageEraData.get(w).settlement(team).xp.getOrDefault(id(team.village,h),0)<2)continue;
   return w.getBlockEntity(p) instanceof Inventory inv&&inv.isEmpty();
  }return false;
 }
 static boolean mayRemoveBed(ServerWorld w,BlockPos pos){
  if(!(w.getBlockState(pos).getBlock() instanceof BedBlock))return false;
  var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
  return memory.houses.stream().anyMatch(h->contains(h,pos)&&ordinary(h)&&VillageEras.team(w,h.min)!=null
          &&VillageEraData.get(w).settlement(VillageEras.team(w,h.min)).xp.getOrDefault(id(VillageEras.team(w,h.min).village,h),0)>=2);
 }
 static int transfer(Inventory from,Inventory to){
  int moved=0;
  for(int i=0;i<from.size();i++){
   var stack=from.getStack(i);int before=stack.getCount();if(stack.isEmpty())continue;
   for(int j=0;j<to.size()&&!stack.isEmpty();j++){
    var target=to.getStack(j);if(target.isEmpty()){to.setStack(j,stack.split(Math.min(stack.getCount(),Math.min(stack.getMaxCount(),to.getMaxCountPerStack()))));}
    else if(net.minecraft.item.ItemStack.areItemsAndComponentsEqual(stack,target)){int n=Math.min(stack.getCount(),Math.max(0,Math.min(target.getMaxCount(),to.getMaxCountPerStack())-target.getCount()));target.increment(n);stack.decrement(n);}
   }
   moved+=before-stack.getCount();
  }
  if(moved>0){from.markDirty();to.markDirty();}return moved;
 }
 static boolean advance(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s){
  var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
  for(var old:List.copyOf(memory.houses))if(s.xp.getOrDefault(id(s.village,old),0)==2&&projects.jobs.stream().anyMatch(p->p.id.equals(id(s.village,old)+":clear")&&p.done)){memory.houses.remove(old);memory.markDirty();s.xp.put(id(s.village,old),3);VillageEraData.get(w).markDirty();}
  var houses=List.copyOf(memory.houses).stream().filter(h->ordinary(h)&&VillageEras.team(w,h.min)==team&&!VillageTerrain.removesHouse(w,h)).sorted(Comparator.comparingDouble((VillageMemory.House h)->VillageEras.horizontal(h.min,team.anchor))).toList();
  if(houses.isEmpty())return true;
  long active=projects.jobs.stream().filter(p->p.village==s.village&&p.id.startsWith("gothic_rebuild:")&&!p.done).count();
  if(active<3)for(var candidate:houses){String key=id(s.village,candidate);if(!projects.exists(key)){VillageSteam.ensureResidence(w,team,s,key,Math.floorMod(candidate.min.asLong(),3)==0?"steam_villa":"steam_townhouse",true);break;}}
  for(var h:houses){String id=id(s.village,h);var built=projects.jobs.stream().filter(p->p.id.equals(id)).findFirst().orElse(null);
  if(built==null||!built.done)continue;
  var clear=projects.jobs.stream().filter(p->p.id.equals(id+":clear")).findFirst().orElse(null);
  if(clear!=null){if(clear.done){memory.houses.remove(h);memory.markDirty();s.xp.put(id,3);VillageEraData.get(w).markDirty();}continue;}
  var protectedVolumes=VillageBuildingSites.protectedVolumes(w,h.min,h.max,h.recordKey);
  for(var r:h.blueprint){
   if(VillageBuildingSites.protectedAt(protectedVolumes,r.pos()))continue;
   if(!VillageProjects.keepLoaded(w,r.pos()))return false;
   if(w.getBlockEntity(r.pos()) instanceof Inventory from&&!from.isEmpty()){
    for(var change:built.plan){if(!VillageProjects.keepLoaded(w,change.pos()))return false;if(w.getBlockEntity(change.pos()) instanceof Inventory to)transfer(from,to);if(from.isEmpty())break;}
    if(!from.isEmpty())return false;
   }
  }
  var beds=built.plan.stream().filter(c->c.after().getBlock() instanceof BedBlock&&c.after().get(BedBlock.PART)==BedPart.HEAD).map(VillageCivilWorks.Change::pos).toList();
  if(beds.isEmpty())return false;int resident=0;
  for(var person:w.getEntitiesByClass(VillagerEntity.class,new Box(h.min).expand(384,96,384),v->v.isAlive())){
   var home=person.getBrain().getOptionalRegisteredMemory(MemoryModuleType.HOME);
   if(home.isPresent()&&home.get().dimension().equals(w.getRegistryKey())&&contains(h,home.get().pos()))person.getBrain().remember(MemoryModuleType.HOME,GlobalPos.create(w.getRegistryKey(),beds.get(resident++%beds.size())));
  }
  s.xp.put(id,2);VillageEraData.get(w).markDirty();Map<BlockPos,BlockState> demolition=new LinkedHashMap<>();
  for(var r:h.blueprint){if(VillageBuildingSites.protectedAt(protectedVolumes,r.pos()))continue;var actual=w.getBlockState(r.pos());if(!actual.isOf(r.state().getBlock()))continue;if(actual.hasBlockEntity()&&!(actual.getBlock() instanceof BedBlock)&&!(w.getBlockEntity(r.pos()) instanceof Inventory inv&&inv.isEmpty()))continue;demolition.put(r.pos(),r.pos().getY()==h.min.getY()?Blocks.GRASS_BLOCK.getDefaultState():Blocks.AIR.getDefaultState());}
  if(demolition.isEmpty()){memory.houses.remove(h);memory.markDirty();return false;}
  projects.add(w,id+":clear",s.village,"gothic_demolition",demolition);return false;
  }return false;
 }
}
