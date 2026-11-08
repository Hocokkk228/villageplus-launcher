package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

/** Surveys buildable shorelines for the coastal shipyard. */
final class NavalWorks {
 record Site(BlockPos base,Direction outward){BlockPos at(int x,int y,int z){var right=outward.rotateYCounterclockwise();return base.add(right.getOffsetX()*x+outward.getOffsetX()*z,y,right.getOffsetZ()*x+outward.getOffsetZ()*z);}float yaw(){return Direction.getHorizontalDegreesOrThrow(outward);}}
 static final int PIER_END=50,BERTH=34;
 static String id(long village){return "shipyard_v1:"+village;}
 static boolean owns(VillagerEntity worker){return worker.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_shipwright_"));}
 static void register(){ServerTickEvents.END_WORLD_TICK.register(w->{if(w.getPlayers().isEmpty()||Math.floorMod(w.getTime(),20)!=7)return;var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);for(var team:List.copyOf(memory.teams)){
  var s=VillageEraData.get(w).settlement(team);if(!VillageSteam.infrastructureStarted(s)||w.getPlayers().stream().noneMatch(p->p.getBlockPos().isWithinDistance(team.anchor,320)))continue;
  var job=VillageProjects.get(w).jobs.stream().filter(p->p.id.equals(id(s.village))).findFirst().orElse(null);
  if(job==null){search(w,team,s);continue;}if(!job.done||!s.xp.containsKey("shipyard_x"))continue;
  var site=new Site(new BlockPos(s.xp.get("shipyard_x"),s.xp.get("shipyard_y"),s.xp.get("shipyard_z")),Direction.fromHorizontalQuarterTurns(s.xp.get("shipyard_facing")));upgradePier(w,team,site);work(w,team,s,site);
 }});}
 static boolean portRequired(VillageProjects projects,VillageEraData.Settlement settlement){
  return projects.exists(id(settlement.village))||settlement.xp.getOrDefault("shipyard_search",0)<128;
 }
 static void search(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s){
  int serial=s.xp.getOrDefault("shipyard_search",0);for(int attempt=0;attempt<8;attempt++){int n=serial+attempt;double angle=n*2.399963229728653;int radius=32+Math.floorMod(n,16)*12;var p=new BlockPos(team.anchor.getX()+(int)(Math.cos(angle)*radius),w.getSeaLevel(),team.anchor.getZ()+(int)(Math.sin(angle)*radius));
   for(var outward:Direction.Type.HORIZONTAL){Site site=null;
    for(int back=0;back<=16;back++){var candidate=new Site(p.offset(outward,-back),outward);int checked=survey(w,candidate);if(checked<0){s.xp.put("shipyard_search",n);VillageEraData.get(w).markDirty();return;}if(checked>0){site=candidate;break;}}
    if(site==null)continue;var shore=site.base();var plan=plan(w,site);if(plan.isEmpty())continue;
    var cornerA=site.at(-4,0,-2);var cornerB=site.at(17,0,PIER_END+2);
    int minX=Math.min(cornerA.getX(),cornerB.getX()),maxX=Math.max(cornerA.getX(),cornerB.getX()),minZ=Math.min(cornerA.getZ(),cornerB.getZ()),maxZ=Math.max(cornerA.getZ(),cornerB.getZ());
    if(VillageProjects.get(w).jobs.stream().anyMatch(j->VillageBuildingSites.building(j.kind)&&j.min.getX()<=maxX&&j.max.getX()>=minX&&j.min.getZ()<=maxZ&&j.max.getZ()>=minZ))continue;
    VillageProjects.get(w).add(w,id(s.village),s.village,"shipyard",plan);if(!VillageProjects.get(w).exists(id(s.village)))continue;
    s.xp.put("shipyard_x",shore.getX());s.xp.put("shipyard_y",shore.getY());s.xp.put("shipyard_z",shore.getZ());s.xp.put("shipyard_facing",outward.getHorizontalQuarterTurns());VillageEraData.get(w).markDirty();return;
   }
  }s.xp.put("shipyard_search",serial+8);VillageEraData.get(w).markDirty();
 }
 static boolean valid(ServerWorld w,Site site){return survey(w,site)>0;}
 static int survey(ServerWorld w,Site site){
  // A real nearby shore, a level waterline and deep water for the entire moored hull.
  var land=site.at(0,0,-2);if(!VillageProjects.keepLoaded(w,land))return -1;
  boolean upper=w.getBlockState(land).isSolidBlock(w,land)&&w.getBlockState(land.up()).getCollisionShape(w,land.up()).isEmpty();
  boolean lower=w.getBlockState(land.down()).isSolidBlock(w,land.down())&&(w.getBlockState(land).getCollisionShape(w,land).isEmpty()||w.getBlockState(land).isReplaceable()&&w.getFluidState(land).isEmpty());
  if(!upper&&!lower)return 0;
  var first=site.at(-3,0,-2);var last=site.at(17,0,PIER_END+2);boolean loaded=true;
  for(int cx=Math.min(first.getX(),last.getX())>>4;cx<=(Math.max(first.getX(),last.getX())>>4);cx++)for(int cz=Math.min(first.getZ(),last.getZ())>>4;cz<=(Math.max(first.getZ(),last.getZ())>>4);cz++)if(!VillageProjects.keepLoaded(w,new BlockPos(cx*16,site.base().getY(),cz*16)))loaded=false;
  if(!loaded)return -1;
  for(int x=-3;x<=3;x++)for(int z=0;z<=PIER_END;z++){var p=site.at(x,0,z);if(!w.isChunkLoaded(p))return 0;
   boolean bank=z<=3&&w.getBlockState(p.down()).isSolidBlock(w,p.down());
   if(!bank&&!w.getFluidState(p.down()).isIn(FluidTags.WATER))return 0;
   for(int y=0;y<=6;y++){var old=w.getBlockState(p.up(y));if(!old.isAir()&&!old.isReplaceable()&&!old.isIn(net.minecraft.registry.tag.BlockTags.LOGS)&&!old.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)&&!(bank&&y==0&&VillageProjects.terrain(old)&&!old.hasBlockEntity()))return 0;}
  }
  for(int x=7;x<=16;x+=3)for(int z=BERTH-14;z<=PIER_END;z+=5){var p=site.at(x,-3,z);if(!w.isChunkLoaded(p)||!w.getFluidState(p).isIn(FluidTags.WATER))return 0;}
  return 1;
 }
 static LinkedHashMap<BlockPos,BlockState> plan(ServerWorld w,Site site){var result=new LinkedHashMap<BlockPos,BlockState>();
  for(int x=-3;x<=3;x++)for(int z=-1;z<=PIER_END;z++){
   var p=site.at(x,0,z);var old=w.getBlockState(p);
   for(int h=1;h<=6;h++){var q=p.up(h);var b=w.getBlockState(q);if(b.isIn(net.minecraft.registry.tag.BlockTags.LOGS)||b.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)||b.isReplaceable()&&!b.isAir())result.put(q,Blocks.AIR.getDefaultState());}
   if(z==-1&&old.isSolidBlock(w,p))continue;result.put(p,deck());
   if(Math.abs(x)==3&&z%6==0)addPile(w,p,result);
  }
  // Open-front carpenter's shelter, safe head clearance and unobstructed boarding side.
  for(int z=0;z<=8;z++){for(int x:new int[]{-3,3})if(z%4==0)for(int y=1;y<=4;y++)result.put(site.at(x,y,z),Blocks.SPRUCE_LOG.getDefaultState());for(int x=-3;x<=3;x++)result.put(site.at(x,5,z),Blocks.DEEPSLATE_TILE_SLAB.getDefaultState());}
  result.put(site.at(-2,1,3),Blocks.BARREL.getDefaultState());result.put(site.at(-2,1,5),Blocks.CRAFTING_TABLE.getDefaultState());result.put(site.at(-2,1,7),Blocks.SMITHING_TABLE.getDefaultState());
  for(int z:new int[]{3,7,15,27}){for(int y=1;y<=3;y++)result.put(site.at(-3,y,z),Blocks.SPRUCE_FENCE.getDefaultState());result.put(site.at(-3,4,z),Blocks.LANTERN.getDefaultState());}
  for(int z=10;z<=PIER_END;z++){
   if(z%6!=3)result.putIfAbsent(site.at(-3,1,z),Blocks.SPRUCE_FENCE.getDefaultState());
   if(z%8==0){result.put(site.at(-3,2,z),Blocks.SPRUCE_FENCE.getDefaultState());result.put(site.at(-3,3,z),Blocks.LANTERN.getDefaultState());}
   if(z%6==0)for(int x=-2;x<=2;x++)result.put(site.at(x,-1,z),Blocks.STRIPPED_SPRUCE_LOG.getDefaultState()
           .with(PillarBlock.AXIS,site.outward().rotateYCounterclockwise().getAxis()));
  }
  var ordered=new LinkedHashMap<BlockPos,BlockState>();
  result.entrySet().stream().sorted(Comparator.<Map.Entry<BlockPos,BlockState>>comparingInt(e->e.getValue().isAir()?0:e.getValue().isOf(Blocks.SPRUCE_SLAB)?1:e.getKey().getY()<site.base().getY()?2:3)
    .thenComparingInt(e->e.getValue().isOf(Blocks.SPRUCE_SLAB)?(e.getKey().getX()-site.base().getX())*site.outward().getOffsetX()+(e.getKey().getZ()-site.base().getZ())*site.outward().getOffsetZ():-e.getKey().getY()))
    .forEach(e->ordered.put(e.getKey(),e.getValue()));
  return ordered;
 }
 static void orderRemaining(List<VillageCivilWorks.Change> plan,int index){
  var first=plan.stream().filter(c->c.after().isOf(Blocks.SPRUCE_SLAB)).findFirst().orElse(null);
  if(first==null)return;
  int deckY=first.pos().getY();
  plan.subList(index,plan.size()).sort(Comparator.<VillageCivilWorks.Change>comparingInt(c->c.after().isAir()?0:c.after().isOf(Blocks.SPRUCE_SLAB)?1:c.pos().getY()<deckY?2:3)
    .thenComparingDouble(c->c.after().isOf(Blocks.SPRUCE_SLAB)?VillageEras.horizontal(c.pos(),first.pos()):-c.pos().getY()));
 }
 static BlockPos pileWork(ServerWorld w,VillageProjects.Project job,VillageCivilWorks.Change change){
  var settlement=VillageEraData.get(w).settlements.get(job.village);
  if(settlement==null||!settlement.xp.containsKey("shipyard_y"))return null;
  int deckY=settlement.xp.get("shipyard_y");
  if(change.pos().getY()>=deckY||change.after().isAir())return null;
  var deck=change.pos().withY(deckY);
  return w.getBlockState(deck).isOf(Blocks.SPRUCE_SLAB)?deck.up():null;
 }
 private static BlockState deck(){return Blocks.SPRUCE_SLAB.getDefaultState().with(SlabBlock.TYPE,net.minecraft.block.enums.SlabType.TOP);}
 private static void addPile(ServerWorld w,BlockPos deck,Map<BlockPos,BlockState> plan){
  for(var p=deck.down();p.getY()>w.getBottomY();p=p.down()){
   var state=w.getBlockState(p);
   if(state.isSolidBlock(w,p))break;
   if(!state.isAir()&&!state.isReplaceable()&&!state.getFluidState().isIn(FluidTags.WATER))break;
   plan.put(p,Blocks.SPRUCE_LOG.getDefaultState());
  }
 }
 static void upgradePier(ServerWorld w,VillageMemory.Team team,Site site){
  var jobs=VillageProjects.get(w);String id="shipyard_pier_v2:"+team.village;
  if(jobs.exists(id))return;
  var changes=new LinkedHashMap<BlockPos,BlockState>();
  for(int x=-3;x<=3;x++)for(int z=-1;z<=PIER_END;z++){
   var p=site.at(x,0,z);if(!w.isChunkLoaded(p))return;
   var existing=w.getBlockState(p);if(!existing.isOf(Blocks.SPRUCE_SLAB))continue;
   if(existing.get(SlabBlock.TYPE)==net.minecraft.block.enums.SlabType.BOTTOM)changes.put(p,deck().with(SlabBlock.WATERLOGGED,existing.get(SlabBlock.WATERLOGGED)));
   if(Math.abs(x)==3&&z%6==0)addPile(w,p,changes);
  }
  for(int z=10;z<=PIER_END;z++){
   var side=site.at(-3,0,z);
   if(!w.getBlockState(side).isOf(Blocks.SPRUCE_SLAB))continue;
   if(w.isAir(side.up())&&z%6!=3)changes.put(side.up(),Blocks.SPRUCE_FENCE.getDefaultState());
   if(z%6==0)for(int x=-2;x<=2;x++){
    var beam=site.at(x,-1,z);
    if(w.getBlockState(beam).isAir()||w.getFluidState(beam).isIn(FluidTags.WATER))
     changes.put(beam,Blocks.STRIPPED_SPRUCE_LOG.getDefaultState().with(PillarBlock.AXIS,site.outward().rotateYCounterclockwise().getAxis()));
   }
  }
  jobs.add(w,id,team.village,"shipyard",changes);
 }
 static final Map<Item,Integer> CUTTER_COST=Map.of(Items.SPRUCE_LOG,128,Items.WHITE_WOOL,32,Items.IRON_INGOT,12);
 static final Map<Item,Integer> BRIG_COST=Map.of(Items.SPRUCE_LOG,256,Items.WHITE_WOOL,64,Items.IRON_INGOT,24);
 static boolean matches(ItemStack stack,Item material){return material==Items.SPRUCE_LOG?stack.isIn(net.minecraft.registry.tag.ItemTags.LOGS):material==Items.WHITE_WOOL?stack.isIn(net.minecraft.registry.tag.ItemTags.WOOL):stack.isOf(material);}
 static int count(Inventory inventory,Item material){int n=0;for(int i=0;i<inventory.size();i++)if(matches(inventory.getStack(i),material))n+=inventory.getStack(i).getCount();return n;}
 static void consume(Inventory inventory,Item material,int count){for(int i=0;i<inventory.size()&&count>0;i++){var stack=inventory.getStack(i);if(matches(stack,material)){int n=Math.min(count,stack.getCount());stack.decrement(n);count-=n;}}inventory.markDirty();}
 static boolean supplied(Inventory in,int kind){return (kind==3?CUTTER_COST:BRIG_COST).entrySet().stream().allMatch(e->count(in,e.getKey())>=e.getValue());}
 static RailVehicleEntity produce(ServerWorld w,Site site,Inventory in,int kind){if(!supplied(in,kind))return null;int berth=w.getBlockState(site.at(0,0,45)).isOf(Blocks.SPRUCE_SLAB)?BERTH:18;var origin=Vec3d.ofBottomCenter(site.at(kind==3?9:11,0,berth));var ship=NavalContent.spawn(w,kind,origin,site.yaw());if(ship==null)return null;for(var cost:(kind==3?CUTTER_COST:BRIG_COST).entrySet())consume(in,cost.getKey(),cost.getValue());return ship;}
 static void work(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement settlement,Site site){if(!VillageBuilders.workHours(w))return;var materials=site.at(-2,1,3);if(!(w.getBlockEntity(materials) instanceof Inventory input))return;
  String tag="halloween_shipwright_"+settlement.village;var people=w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(320),v->v.isAlive()&&!v.isBaby()&&!v.isTouchingWater());
  var worker=people.stream().filter(v->v.getCommandTags().contains(tag)).findFirst().orElse(null);if(worker==null){worker=people.stream().filter(v->!owns(v)&&!RailWorks.owns(v)&&!VillageProjects.owns(v)&&VillageEras.profession(v).equals("toolsmith")).findFirst().orElse(null);if(worker==null)return;worker.addCommandTag(tag);}
  int kind=settlement.xp.getOrDefault("shipyard_built",0)%2==0?3:2;var cost=kind==3?CUTTER_COST:BRIG_COST;
  for(int slot=0;slot<worker.getInventory().size();slot++){var stack=worker.getInventory().getStack(slot);if(cost.keySet().stream().noneMatch(item->matches(stack,item)))continue;if(worker.squaredDistanceTo(Vec3d.ofCenter(materials))>9){VillageProjects.approach(w,worker,materials);return;}var actual=stack.getItem();int n=Math.min(stack.getCount(),RailWorks.free(input,actual));if(n>0){RailWorks.take(worker.getInventory(),actual,n);RailWorks.add(input,actual,n);}return;}
  for(var entry:cost.entrySet())if(count(input,entry.getKey())<entry.getValue()&&settlement.vault!=null){var stock=VillageEconomy.barrel(settlement,3);if(worker.squaredDistanceTo(Vec3d.ofCenter(stock))>9){VillageProjects.approach(w,worker,stock);return;}for(int i:new int[]{3,5})if(w.getBlockEntity(VillageEconomy.barrel(settlement,i)) instanceof Inventory inventory){for(int slot=0;slot<inventory.size();slot++){var stack=inventory.getStack(slot);if(!matches(stack,entry.getKey()))continue;var actual=stack.getItem();int n=Math.min(32,Math.min(entry.getValue()-count(input,entry.getKey()),Math.min(inventory.count(actual),RailWorks.free(worker.getInventory(),actual))));if(n>0){RailWorks.take(inventory,actual,n);RailWorks.add(worker.getInventory(),actual,n);return;}}}}
  if(!supplied(input,kind))return;var bench=site.at(-2,1,5);if(worker.squaredDistanceTo(Vec3d.ofCenter(bench))>9){VillageProjects.approach(w,worker,bench);return;}VillageTasks.hold(worker);worker.setStackInHand(Hand.MAIN_HAND,new ItemStack(RailContent.HAMMER));worker.swingHand(Hand.MAIN_HAND);w.playSound(null,bench,net.minecraft.sound.SoundEvents.BLOCK_WOOD_HIT,net.minecraft.sound.SoundCategory.BLOCKS,.5f,.8f);
  int progress=settlement.xp.getOrDefault("shipyard_progress",0)+1;settlement.xp.put("shipyard_progress",progress);VillageEraData.get(w).markDirty();if(progress<30)return;
  var ship=produce(w,site,input,kind);if(ship!=null){settlement.xp.put("shipyard_progress",0);settlement.xp.merge("shipyard_built",1,Integer::sum);VillageEras.work(w,worker,"toolsmith",20);VillageEraData.get(w).markDirty();}
 }
 private NavalWorks(){}
}
