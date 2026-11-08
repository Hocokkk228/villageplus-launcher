package dev.halloween;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
/** Four real workshops. Assemblers haul materials, operate the line, and keep all progress in saved inventories. */
final class IndustrialPlants extends PersistentState {
 static final class Plant {
  static final Codec<Plant> CODEC=RecordCodecBuilder.create(i->i.group(Codec.LONG.fieldOf("village").forGetter(p->p.village),Codec.INT.fieldOf("kind").forGetter(p->p.kind),BlockPos.CODEC.fieldOf("base").forGetter(p->p.base),Codec.STRING.optionalFieldOf("worker","").forGetter(p->p.worker),Codec.STRING.listOf().optionalFieldOf("vehicles",List.of()).forGetter(p->p.vehicles),
   Codec.STRING.optionalFieldOf("courier","").forGetter(p->p.courier),
   Codec.STRING.optionalFieldOf("shipment","").forGetter(p->p.shipment),
   Codec.BOOL.optionalFieldOf("loaded_courier",false).forGetter(p->p.loadedCourier)).apply(i,Plant::new));
  final long village;final int kind;final BlockPos base;String worker;final List<String> vehicles;
  String courier,shipment;
  boolean loadedCourier;
  Plant(long village,int kind,BlockPos base,String worker,List<String> vehicles){
   this(village,kind,base,worker,vehicles,"","",false);
  }
  Plant(long village,int kind,BlockPos base,String worker,List<String> vehicles,String courier,String shipment,boolean loadedCourier){
   this.village=village;this.kind=kind;this.base=base;this.worker=worker;this.vehicles=new ArrayList<>(vehicles);
   this.courier=courier;this.shipment=shipment;this.loadedCourier=loadedCourier;
  }
  BlockPos line(){return base.add(20,1,22);}String id(){return "industrial_plant:"+village+":"+kind;}
 }
 static final PersistentStateType<IndustrialPlants> TYPE=new PersistentStateType<>("halloween_industrial_plants",()->new IndustrialPlants(List.of()),Plant.CODEC.listOf().xmap(IndustrialPlants::new,s->s.plants),null);
 final List<Plant> plants;IndustrialPlants(List<Plant> plants){this.plants=new ArrayList<>(plants);}
 static IndustrialPlants get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(TYPE);}
 static boolean owns(VillagerEntity v){return v.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_machine_worker_")||t.startsWith("halloween_machine_courier_"));}
 static final Map<ServerWorld,Integer> CURSOR=new WeakHashMap<>();
 static void register(){
  net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{
   if(entity instanceof IndustrialVehicleEntity&&entity.getRemovalReason()!=null&&entity.getRemovalReason().shouldDestroy())
    destroyed(world,entity.getUuidAsString());
  });
  net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
   if(entity instanceof VillagerEntity v&&v.getEntityWorld() instanceof ServerWorld world&&owns(v))
    IndustrialDeliveries.onDeath(world,v);
  });
  ServerTickEvents.END_WORLD_TICK.register(w->{if(w.getPlayers().isEmpty()||w.getTime()%5!=1)return;var state=get(w);var data=VillageEraData.get(w);var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
  if(w.getTime()%20==11)for(var team:List.copyOf(memory.teams)){var s=data.settlement(team);if(!VillageIndustrial.civic(s)&&!VillageIndustrial.pending(s)||!IndustrialCityGrowth.playerNear(w,team,s))continue;
   for(int kind=0;kind<4;kind++){final int k=kind;if(state.plants.stream().noneMatch(p->p.village==team.village&&p.kind==k)){survey(w,team,s,kind);break;}}
  }
  if(state.plants.isEmpty())return;int cursor=CURSOR.getOrDefault(w,0);
  for(int n=0;n<state.plants.size();n++){var plant=state.plants.get(Math.floorMod(cursor+n,state.plants.size()));if(!w.isChunkLoaded(plant.line()))continue;var team=memory.teams.stream().filter(t->t.village==plant.village).findFirst().orElse(null);if(team==null)continue;var s=data.settlement(team);
   if(!IndustrialCityGrowth.playerNear(w,team,s)||!VillageProjects.get(w).jobs.stream().anyMatch(j->j.id.equals(plant.id())&&j.done))continue;
   CURSOR.put(w,cursor+n+1);operate(w,team,s,plant);if(w.getBlockEntity(plant.line()) instanceof MachineAssemblyEntity line)IndustrialDeliveries.tick(w,team,plant,line);break;
  }
 });}
 static void destroyed(ServerWorld world,String uuid){
  var state=get(world);
  for(var plant:state.plants)if(plant.vehicles.remove(uuid))state.markDirty();
 }
 static void survey(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s,int kind){
  String cursorKey="machine_plant_search_"+kind;int n=s.xp.getOrDefault(cursorKey,0);double angle=n*2.399963229728653+kind*Math.PI/2;int radius=224+Math.floorMod(n,16)*20;
  int x=team.anchor.getX()+(int)(Math.cos(angle)*radius),z=team.anchor.getZ()+(int)(Math.sin(angle)*radius);var jobs=VillageProjects.get(w);var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
  if(memory.houses.stream().filter(VillageBuildingSites::reserves).anyMatch(h->overlap(h.min,h.max,x,z))||jobs.jobs.stream().filter(j->VillageBuildingSites.building(j.kind)).anyMatch(j->overlap(j.min,j.max,x,z))){next(w,s,cursorKey,n);return;}
  boolean loaded=true;for(int cx=x>>4;cx<=(x+35)>>4;cx++)for(int cz=(z-32)>>4;cz<=(z+43)>>4;cz++)if(!VillageProjects.keepLoaded(w,new BlockPos(cx*16,64,cz*16)))loaded=false;if(!loaded)return;
  int height=VillageEraPlans.levelSite(w,x,z-32,36,76,8);if(height==Integer.MIN_VALUE){next(w,s,cursorKey,n);return;}
  var base=new BlockPos(x,height-1,z);var plan=IndustrialPlantPlans.build(base,kind);
  if(!VillageIndustrial.safe(w,Map.of("factory",plan))){next(w,s,cursorKey,n);return;}
  for(int xx=0;xx<36;xx++)for(int zz=-32;zz<44;zz++)for(int down=1;down<=12;down++){var p=base.add(xx,-down,zz);if(w.getBlockState(p).isSolidBlock(w,p))break;plan.put(p,Blocks.STONE_BRICKS.getDefaultState());}
  var plant=new Plant(team.village,kind,base,"",List.of());jobs.add(w,plant.id(),team.village,"industrial_factory",plan);if(!jobs.exists(plant.id()))return;get(w).plants.add(plant);get(w).markDirty();next(w,s,cursorKey,n);s.xp.merge("industrial_city_extent",radius+100,Math::max);VillageEraData.get(w).markDirty();
 }
 static void next(ServerWorld w,VillageEraData.Settlement s,String key,int n){s.xp.put(key,n+1);VillageEraData.get(w).markDirty();}
 static boolean overlap(BlockPos min,BlockPos max,int x,int z){return min.getX()<=x+37&&max.getX()>=x-2&&min.getZ()<=z+45&&max.getZ()>=z-34;}
 static boolean ready(ServerWorld w,long village){return get(w).plants.stream().filter(p->p.village==village).count()==4&&VillageProjects.get(w).complete(village,"industrial_factory");}
 static void operate(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s,Plant plant){
  if(!VillageBuilders.workHours(w)||!(w.getBlockEntity(plant.line()) instanceof MachineAssemblyEntity line))return;
  if(line.stage==0&&line.progress==0){int target=switch(plant.kind){case 0->line.completed%2;case 1->2+line.completed%2;case 2->4;default->5+line.completed%2;};if(line.product!=target){line.product=target;line.sync();}}
  VillagerEntity worker=null;try{if(!plant.worker.isEmpty()&&w.getEntity(UUID.fromString(plant.worker)) instanceof VillagerEntity v&&v.isAlive())worker=v;}catch(IllegalArgumentException ignored){}
  if(worker==null){worker=w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(IndustrialCityGrowth.radius(s,384)+96),v->v.isAlive()&&!v.isBaby()&&!v.isAiDisabled()&&!v.hasVehicle()&&!VillageTrials.busy(v)&&!VillageDefense.isGuard(v)&&!VillageProjects.owns(v)&&!owns(v)&&!RailWorks.owns(v)&&!NavalWorks.owns(v)&&Set.of("toolsmith","weaponsmith","armorer").contains(VillageEras.profession(v))).stream().min(Comparator.comparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(plant.line())))).orElse(null);
   if(worker==null)return;plant.worker=worker.getUuidAsString();worker.addCommandTag("halloween_machine_worker_"+plant.line().asLong());get(w).markDirty();}
  if(worker.isSleeping())worker.wakeUp();
  var b=line.bill();if(line.stage>=b.size()){if(worker.squaredDistanceTo(Vec3d.ofCenter(plant.line()))>9)IndustrialNavigation.move(w,worker,plant.line().west(1));else line.work(10);return;}String need=b.get(Math.min(line.stage,b.size()-1)).getKey();var recipe=IndustrialMachines.RECIPES.stream().filter(r->r.part().equals(need)).findFirst().orElseThrow();
  // Deliver physically carried resources before taking anything else from a warehouse.
  boolean carrying=false;for(int i=0;i<worker.getInventory().size();i++)if(!worker.getInventory().getStack(i).isEmpty()&&(recipe.inputs().containsKey(worker.getInventory().getStack(i).getItem())||IndustrialMachines.PARTS.containsValue(worker.getInventory().getStack(i).getItem()))){carrying=true;break;}
  if(carrying){if(worker.squaredDistanceTo(Vec3d.ofCenter(plant.line()))>9){IndustrialNavigation.move(w,worker,plant.line().west(1));return;}for(int i=0;i<worker.getInventory().size();i++){var stack=worker.getInventory().getStack(i);if(recipe.inputs().containsKey(stack.getItem())||IndustrialMachines.PARTS.containsValue(stack.getItem())){int amount=Math.min(stack.getCount(),RailWorks.free(line,stack.getItem()));if(amount>0){var item=stack.getItem();RailWorks.take(worker.getInventory(),item,amount);RailWorks.add(line,item,amount);}}}}
  Item missing=recipe.inputs().entrySet().stream().filter(e->line.count(e.getKey())<e.getValue()).map(Map.Entry::getKey).findFirst().orElse(null);
  if(line.count(IndustrialMachines.PARTS.get(need))<b.get(Math.min(line.stage,b.size()-1)).getValue()&&missing!=null&&s.vault!=null){
   var source=java.util.stream.IntStream.range(0,6).mapToObj(i->VillageEconomy.barrel(s,i)).filter(p->w.isChunkLoaded(p)&&w.getBlockEntity(p) instanceof Inventory stock&&stock.count(missing)>0).findFirst().orElse(null);
   if(source!=null){if(worker.squaredDistanceTo(Vec3d.ofCenter(source))>9){IndustrialNavigation.move(w,worker,source);return;}var stock=(Inventory)w.getBlockEntity(source);int amount=Math.min(32,Math.min(stock.count(missing),RailWorks.free(worker.getInventory(),missing)));if(amount>0){RailWorks.take(stock,missing,amount);RailWorks.add(worker.getInventory(),missing,amount);}return;}
  }
  if(worker.squaredDistanceTo(Vec3d.ofCenter(plant.line()))>9){IndustrialNavigation.move(w,worker,plant.line().west(1));return;}
  VillageTasks.hold(worker);worker.setStackInHand(Hand.MAIN_HAND,new ItemStack(RailContent.HAMMER));worker.swingHand(Hand.MAIN_HAND);line.work(VillageFastEras.active(w,team.village)?160:10);
 }
 private IndustrialPlants(){this(List.of());}
}
