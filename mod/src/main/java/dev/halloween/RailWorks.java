package dev.halloween;
import java.util.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.village.VillagerProfession;
/** Forge steps consume real ingots, fuel and intermediate products, operated by a villager with a hammer. */
final class RailWorks {
 static boolean owns(VillagerEntity v){return v.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_rail_smith_"));}
 static boolean take(Inventory inventory,Item item,int count){if(inventory.count(item)<count)return false;for(int slot=0;slot<inventory.size()&&count>0;slot++){var stack=inventory.getStack(slot);if(stack.isOf(item)){int n=Math.min(count,stack.getCount());stack.decrement(n);count-=n;}}inventory.markDirty();return true;}
 static boolean add(Inventory inventory,Item item,int count){var stack=new ItemStack(item,count);for(int slot=0;slot<inventory.size()&&!stack.isEmpty();slot++){var old=inventory.getStack(slot);if(old.isEmpty()){int n=Math.min(stack.getCount(),Math.min(stack.getMaxCount(),inventory.getMaxCountPerStack()));inventory.setStack(slot,stack.copyWithCount(n));stack.decrement(n);}else if(old.isOf(item)){int n=Math.min(stack.getCount(),old.getMaxCount()-old.getCount());old.increment(n);stack.decrement(n);}}inventory.markDirty();return stack.isEmpty();}
 static int free(Inventory inventory,Item item){int total=0;for(int i=0;i<inventory.size();i++){var s=inventory.getStack(i);if(s.isEmpty())total+=item.getDefaultStack().getMaxCount();else if(s.isOf(item))total+=s.getMaxCount()-s.getCount();}return total;}
 static boolean produce(Inventory input,Inventory output,int cycle){
  int mode=Math.floorMod(cycle,3);Item result=mode==0?RailContent.PROFILE:mode==1?RailContent.SLEEPER.asItem():RailContent.TRACK.asItem();int quantity=mode==0?8:mode==1?4:1;
  if(free(output,result)<quantity)return false;
  if(mode<2){if(input.count(Items.IRON_INGOT)<(mode==0?4:2)||input.count(Items.COAL)<1)return false;take(input,Items.IRON_INGOT,mode==0?4:2);take(input,Items.COAL,1);}
  else{if(output.count(RailContent.PROFILE)<8||output.count(RailContent.SLEEPER.asItem())<4)return false;take(output,RailContent.PROFILE,8);take(output,RailContent.SLEEPER.asItem(),4);}
  return add(output,result,quantity);
 }
 static void tick(ServerWorld w,RailwayNetwork network){
  if(!VillageBuilders.workHours(w))return;
  for(var station:network.stations){var job=VillageProjects.get(w).jobs.stream().filter(p->p.id.equals(RailwayNetwork.foundryId(station.village))&&p.done).findFirst().orElse(null);if(job==null)continue;var forge=RailwayNetwork.foundry(station);var inPos=forge.pos(12,1,4);var outPos=forge.pos(12,1,20);if(!(w.getBlockEntity(inPos) instanceof Inventory input)||!(w.getBlockEntity(outPos) instanceof Inventory output))continue;
   var team=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).teams.stream().filter(t->t.village==station.village).findFirst().orElse(null);if(team==null)continue;var era=VillageEraData.get(w).settlement(team);
   String owner="halloween_rail_smith_"+station.village;
   var people=w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(384),v->v.isAlive()&&!v.isBaby()&&!v.isTouchingWater());
   var worker=people.stream().filter(v->v.getCommandTags().contains(owner)).findFirst().orElse(null);
   if(worker==null){worker=people.stream().filter(v->!owns(v)&&!VillageProjects.owns(v)&&(v.getVillagerData().profession().matchesKey(VillagerProfession.TOOLSMITH)||v.getVillagerData().profession().matchesKey(VillagerProfession.WEAPONSMITH))).min(Comparator.comparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(inPos)))).orElse(null);if(worker!=null)worker.addCommandTag(owner);}
   if(worker==null)continue;if(worker.isSleeping())worker.wakeUp();
   int mode=output.count(RailContent.PROFILE)<8?0:output.count(RailContent.SLEEPER.asItem())<4?1:2;
   boolean rawNeeded=mode<2&&(input.count(Items.IRON_INGOT)<(mode==0?4:2)||input.count(Items.COAL)<1);
   boolean carrying=worker.getInventory().count(Items.IRON_INGOT)>0||worker.getInventory().count(Items.COAL)>0;
   if(rawNeeded&&!carrying&&era.vault!=null){var source=VillageEconomy.barrel(era,3);if(worker.squaredDistanceTo(Vec3d.ofCenter(source))>9){VillageProjects.approach(w,worker,source);continue;}if(w.getBlockEntity(source) instanceof Inventory stock){for(var item:List.of(Items.IRON_INGOT,Items.COAL)){int n=Math.min(16,Math.min(stock.count(item),free(worker.getInventory(),item)));if(n>0){take(stock,item,n);add(worker.getInventory(),item,n);}}}carrying=worker.getInventory().count(Items.IRON_INGOT)>0||worker.getInventory().count(Items.COAL)>0;}
   if(carrying){if(worker.squaredDistanceTo(Vec3d.ofCenter(inPos))>9){VillageProjects.approach(w,worker,inPos);continue;}
    for(var item:List.of(Items.IRON_INGOT,Items.COAL)){int n=Math.min(worker.getInventory().count(item),free(input,item));if(n>0){take(worker.getInventory(),item,n);add(input,item,n);}}
   }
   var anvil=forge.pos(8,1,8);if(worker.squaredDistanceTo(Vec3d.ofCenter(anvil))>6){VillageProjects.approach(w,worker,anvil);continue;}
   VillageTasks.hold(worker);worker.setStackInHand(Hand.MAIN_HAND,new ItemStack(RailContent.HAMMER));worker.swingHand(Hand.MAIN_HAND);
   w.playSound(null,anvil,net.minecraft.sound.SoundEvents.BLOCK_ANVIL_HIT,net.minecraft.sound.SoundCategory.BLOCKS,.35f,.8f+w.random.nextFloat()*.2f);
   w.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,anvil.getX()+.5,anvil.getY()+1,anvil.getZ()+.5,4,.15,.05,.15,.06);
   if(w.getTime()%100==0&&produce(input,output,mode)){station.production++;network.markDirty();VillageEras.work(w,worker,"toolsmith",8);}
  }
 }
 static boolean supply(ServerWorld w,long village,VillagerEntity worker){
  if(worker.getInventory().count(RailContent.TRACK.asItem())>0)return true;var station=RailwayNetwork.get(w).stations.stream().filter(s->s.village==village).findFirst().orElse(null);if(station==null)return false;var output=RailwayNetwork.foundry(station).pos(12,1,20);
  if(worker.squaredDistanceTo(Vec3d.ofCenter(output))>9){VillageProjects.approach(w,worker,output);return false;}
  if(w.getBlockEntity(output) instanceof Inventory stock){int n=Math.min(16,Math.min(stock.count(RailContent.TRACK.asItem()),free(worker.getInventory(),RailContent.TRACK.asItem())));if(n>0){take(stock,RailContent.TRACK.asItem(),n);add(worker.getInventory(),RailContent.TRACK.asItem(),n);}}
  return worker.getInventory().count(RailContent.TRACK.asItem())>0;
 }
 private RailWorks(){}
}
