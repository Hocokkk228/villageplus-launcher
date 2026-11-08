package dev.halloween;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Physical template checks: actual navigation, closed rooms, supported lights and existing-world repairs. */
final class StructureQualityChecks {
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void check(ServerWorld world){
  var base=new BlockPos(400,60,352);
  var template=VillageEraPlans.template(world,"cologne_cathedral");
  for(int x=24;x<=29;x++)for(int z=20;z<=28;z++)world.getChunk(x,z);
  for(var cell:template.blocks())world.setBlockState(base.add(cell.pos()),cell.state(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
  var walker=new VillagerEntity(EntityType.VILLAGER,world);
  walker.setPosition(Vec3d.ofBottomCenter(base.add(32,1,17)));walker.setOnGround(true);SpasInteriors.navigation(walker);
  for(int x:new int[]{17,47})for(int floor:new int[]{0,7,14})for(int z:new int[]{25,39,51,64}){
   var target=base.add(x,floor+1,z);var path=walker.getNavigation().findPathTo(target,0);
   require(path!=null&&path.reachesTarget(),"Cathedral room unreachable: "+target.subtract(base)+" end="+(path==null?"none":path.getNodePos(path.getLength()-1).subtract(base)));
  }
  // Every suspended lamp has a physical chain, attached to a solid ceiling; no floating light fixtures.
  int lamps=0;
  for(var cell:template.blocks())if(cell.state().getBlock() instanceof LanternBlock&&cell.state().get(LanternBlock.HANGING)){
   lamps++;var p=base.add(cell.pos()).up();int length=0;
   while(world.getBlockState(p).getBlock() instanceof ChainBlock&&length++<100)p=p.up();
   require(world.getBlockState(p).isSolidBlock(world,p),"Floating cathedral lamp at "+cell.pos()+" ends at "+p.subtract(base));
  }
  require(lamps>=50,"Palace has room and hall lighting, not a single central lantern: "+lamps);
  for(int z:new int[]{14,75})for(int x=24;x<=40;x++)for(int y=17;y<39-Math.abs(x-32);y++)
   require(!world.isAir(base.add(x,y,z)),"Open nave gable at "+new BlockPos(x,y,z));
  verifyLighting(world,base,template);
  verifyTreasury(world);
  verifyBridgesAndPursuit(world);
  verifyFastRailway(world);
  require(RailTerrain.natural(Blocks.POINTED_DRIPSTONE.getDefaultState()),"Railway can clear natural cave stalactites instead of endlessly detouring");
  verifyLastBlockStalls(world);
  var station=new RailwayNetwork.Station(51799,new BlockPos(420,250,360),0,0);
  var displaced=RailPath.straight(station.center(0).add(25,0,0),station.center(64).add(25,0,0));
  var aligned=RailwayNetwork.alignedStationControls(displaced,List.of(station));
  require(aligned.equals(RailPath.straight(station.center(0),station.center(64))),"Old detours are repaired back to the real station track span");
  var network=RailwayNetwork.get(world);network.stations.add(station);
  var turnControls=new ArrayList<Vec3d>();turnControls.addAll(RailPath.straight(station.center(0),station.center(64)));
  var far0=Vec3d.ofBottomCenter(station.pos(60,1,64));var far1=Vec3d.ofBottomCenter(station.pos(60,1,0));
  turnControls.addAll(List.of(station.center(64),station.center(90),far0.add(0,0,24),far0));
  turnControls.addAll(RailPath.straight(far0,far1));
  turnControls.addAll(List.of(far1,far1.add(0,0,-24),station.center(-26),station.center(0)));
  var turn=new RailwayNetwork.Line("rail_ring:test_detour",station.village,turnControls,0,false);network.lines.add(turn);
  var obstacle=station.pos(60,1,32);require(RailwayNetwork.detour(world,turn,obstacle),"A foundry beside the station must not permanently block a curve");
  var corrected=network.lines.get(network.lines.size()-1);
  require(corrected.controls.subList(0,4).equals(turn.controls.subList(0,4)),"Detour keeps the platform rails fixed");
  require(corrected.path.center(corrected.path.closest(Vec3d.ofCenter(obstacle))).distanceTo(Vec3d.ofCenter(obstacle))>12,"Curve clears the protected foundry");
  network.lines.remove(corrected);network.stations.remove(station);
  org.slf4j.LoggerFactory.getLogger("structure-quality-test").info("PASS 24 cathedral rooms reachable on three floors, sealed nave gables, {} ceiling-supported lights and treasury repair preserving inventories",lamps);
 }
 static void verifyLighting(ServerWorld world,BlockPos base,VillageEraPlans.Template template){
  int width=65,depth=88,height=40;int[] light=new int[width*depth*height],opacity=new int[light.length];var queue=new ArrayDeque<Integer>();
  for(var cell:template.blocks()){var p=cell.pos();int x=p.getX(),y=p.getY(),z=p.getZ();if(x<0||x>=width||z<0||z>=depth||y<0||y>=height)continue;int i=(y*depth+z)*width+x;opacity[i]=cell.state().getOpacity();if(cell.state().getLuminance()>0){light[i]=cell.state().getLuminance();queue.add(i);}}
  while(!queue.isEmpty()){int i=queue.remove(),x=i%width,z=i/width%depth,y=i/width/depth;for(var d:Direction.values()){int nx=x+d.getOffsetX(),ny=y+d.getOffsetY(),nz=z+d.getOffsetZ();if(nx<0||nx>=width||nz<0||nz>=depth||ny<0||ny>=height)continue;int n=(ny*depth+nz)*width+nx,value=light[i]-Math.max(1,opacity[n]);if(value>light[n]){light[n]=value;queue.add(n);}}}
  var dark=new ArrayList<BlockPos>();
  for(int y:new int[]{1,8,15})for(int x=3;x<=61;x++)for(int z=19;z<=72;z++){
   if(y>1&&(x<14||x>50||x>19&&x<45))continue;
   var p=base.add(x,y,z);boolean covered=false;for(int roof=y+2;roof<40;roof++)if(world.getBlockState(base.add(x,roof,z)).isOpaqueFullCube()){covered=true;break;}if(!covered)continue;if(world.getBlockState(p.down()).isSolidBlock(world,p.down())&&world.getBlockState(p).getCollisionShape(world,p).isEmpty()&&world.getBlockState(p.up()).getCollisionShape(world,p.up()).isEmpty()&&light[(y*depth+z)*width+x]==0)dark.add(p.subtract(base));
  }
  require(dark.isEmpty(),"Unlit inhabited cathedral floor cells: "+dark.stream().limit(12).toList()+" total="+dark.size());
 }
 static void verifyTreasury(ServerWorld world){
  long village=51791;var anchor=new BlockPos(450,30,420);var team=new VillageMemory.Team(village,anchor,0);
  var memory=world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);memory.teams.add(team);
  var settlement=VillageEraData.get(world).settlement(team);settlement.era=2;
  settlement.vault=new VillageEraData.Vault(anchor,0,"",-1,true);
  var template=VillageEraPlans.template(world,"stone_treasury");
  var plan=new LinkedHashMap<BlockPos,BlockState>();
  for(var cell:template.blocks())plan.put(VillageEraPlans.vaultPos(settlement.vault,cell.pos().getZ(),9-cell.pos().getX(),cell.pos().getY()-1),cell.state());
  var projects=VillageProjects.get(world);projects.add(world,"treasury_fixture:"+village,village,"treasury",plan);
  for(int i=0;i<1000&&!projects.complete(village,"treasury");i++)VillageProjects.materialize(world,village,4096);
  require(projects.complete(village,"treasury"),"Treasury fixture finished");
  var hole=VillageEraPlans.vaultPos(settlement.vault,4,0,3);world.setBlockState(hole,Blocks.AIR.getDefaultState());
  var stock=VillageEconomy.barrel(settlement,3);
  world.setBlockState(stock,Blocks.BARREL.getDefaultState());
  var inventory=(net.minecraft.inventory.Inventory)world.getBlockEntity(stock);inventory.setStack(0,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND,17));
  VillageTreasuryRepair.tick(world,team,settlement);
  require(projects.exists("treasury_restoration_v2:"+village),"Existing treasury queues envelope repair");
  for(int i=0;i<1000&&!projects.complete(village,"treasury");i++)VillageProjects.materialize(world,village,4096);
  require(!world.isAir(hole),"Treasury wall hole closes");
  require(inventory.getStack(0).isOf(net.minecraft.item.Items.DIAMOND)&&inventory.getStack(0).getCount()==17,"Treasury stock is preserved");
 }
 static void verifyBridgesAndPursuit(ServerWorld world){
  for(int x=400;x<=466;x++)for(int z=312;z<=351;z++){
   var p=new BlockPos(x,279,z);world.setBlockState(p.down(),Blocks.STONE.getDefaultState());world.setBlockState(p,x>=419&&x<=435?Blocks.WATER.getDefaultState():Blocks.GRASS_BLOCK.getDefaultState());
  }
  var jobs=VillageProjects.get(world);long village=51792;
  jobs.add(world,"bridge-harbor-fixture",village,"shipyard",Map.of(new BlockPos(423,285,322),Blocks.SPRUCE_LOG.getDefaultState(),new BlockPos(431,285,328),Blocks.SPRUCE_LOG.getDefaultState()));
  var from=new BlockPos(405,280,325);
  for(int z:new int[]{325,343}){
   var to=new BlockPos(455,280,z);var plan=VillageBridges.plan(world,from,to,3);
   require(plan!=null&&!plan.isEmpty(),"Bridge is planned to each bank despite an obstructing port: "+to);
   require(plan.keySet().stream().noneMatch(p->p.getX()>=423&&p.getX()<=431&&p.getZ()>=322&&p.getZ()<=328),"Bridge preserves the port footprint");
   jobs.add(world,"bridge-quality:"+z,village,"bridge",plan);
   // The port is a reservation only; finish the crossing independently of its unrelated construction.
   jobs.jobs.stream().filter(p->p.id.equals("bridge-harbor-fixture")).forEach(p->p.done=true);
   for(int i=0;i<1000&&!jobs.complete(village,"bridge");i++)VillageProjects.materialize(world,village,4096);
   require(jobs.complete(village,"bridge"),"Both river crossings finish");
   var walker=new VillagerEntity(EntityType.VILLAGER,world);walker.setPosition(Vec3d.ofBottomCenter(from));walker.setOnGround(true);walker.getNavigation().setMaxFollowRange(128);
   var path=walker.getNavigation().findPathTo(to,0);require(path!=null&&path.reachesTarget(),"Villager walks across the detour to bank "+z);
  }
  var attacker=new VillagerEntity(EntityType.VILLAGER,world);var victim=new VillagerEntity(EntityType.VILLAGER,world);
  attacker.setPosition(447.5,280,320.5);attacker.setOnGround(true);attacker.getNavigation().setMaxFollowRange(128);victim.setPosition(451.5,280,320.5);
  for(int z=315;z<=325;z++)for(int y=280;y<=283;y++)world.setBlockState(new BlockPos(449,y,z),Blocks.STONE_BRICKS.getDefaultState());
  require(StreetCrime.pursue(world,attacker,victim,1),"Pursuer finds a route around the wall rather than walking into it");
  var path=attacker.getNavigation().getCurrentPath();require(path!=null&&path.reachesTarget(),"Pursuer has a reachable melee position");
  var end=Vec3d.ofBottomCenter(path.getTarget());require(end.squaredDistanceTo(victim.getEntityPos())<=4.1,"Pursuit ends close enough to attack");
  org.slf4j.LoggerFactory.getLogger("structure-quality-test").info("PASS two independent bridge approaches, port detour, existing crossing reuse and pursuer routing around a wall");
 }
 static void verifyFastRailway(ServerWorld world){
  long village=51795;var team=new VillageMemory.Team(village,new BlockPos(450,25,420),0);var settlement=VillageEraData.get(world).settlement(team);
  var pos=team.anchor;world.setBlockState(pos.down(),Blocks.STONE_BRICKS.getDefaultState());
  var projects=VillageProjects.get(world);projects.add(world,"fast-railway-fixture",village,"railway",Map.of(pos,RailContent.TRACK.getDefaultState()));
  VillageProjects.materialize(world,village,100);require(!projects.complete(village,"railway"),"Ordinary railway construction is not generated for free");
  settlement.xp.put(VillageFastEras.FLAG,1);for(int i=0;i<100&&!projects.complete(village,"railway");i++)VillageProjects.materialize(world,village,100);
  require(projects.complete(village,"railway")&&world.getBlockState(pos).isOf(RailContent.TRACK),"Explicit admin fast construction includes the initial railway approach");
 }
 static void verifyLastBlockStalls(ServerWorld world){
  long village=51796;var team=new VillageMemory.Team(village,new BlockPos(410,35,385),0);var s=VillageEraData.get(world).settlement(team);s.transitionAt=world.getTime();s.plantation=team.anchor;
  var projects=VillageProjects.get(world);var lectern=new BlockPos(451,40,420);
  world.setBlockState(lectern,Blocks.GRASS_BLOCK.getDefaultState());projects.add(world,"last-lectern-fixture",village,"treasury",Map.of(lectern,Blocks.LECTERN.getDefaultState()));
  world.setBlockState(lectern,RoyalProfession.CEREMONIAL_LECTERN.getDefaultState().with(CeremonialLecternBlock.FACING,Direction.NORTH));
  var footing=s.plantation.add(-1,0,1);world.setBlockState(footing,Blocks.DIRT.getDefaultState());world.setBlockState(footing.up(),Blocks.STONE_BRICK_WALL.getDefaultState());world.setBlockState(footing.up(2),Blocks.LANTERN.getDefaultState());
  projects.add(world,"last-farm-fixture",village,"plantation",Map.of(footing,Blocks.DIRT_PATH.getDefaultState()));
  VillageEraPlans.repairPlantationCrossings(world,team,s);
  for(int i=0;i<100;i++)VillageProjects.materialize(world,village,512);
  require(projects.complete(village,"treasury")&&projects.complete(village,"plantation"),"Legacy treasury and plantation last-block stalls both complete");
  require(world.getBlockState(lectern).isOf(RoyalProfession.CEREMONIAL_LECTERN)&&world.getBlockState(footing).isOf(Blocks.STONE_BRICKS),"Civic lectern is preserved and lamp footing is stable stone");
  var royal=lectern.east(5);world.setBlockState(lectern,Blocks.LECTERN.getDefaultState());world.setBlockState(royal,Blocks.LECTERN.getDefaultState());
  var memory=world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
  memory.houses.add(new VillageMemory.House(royal,royal,List.of(new VillageMemory.BlockRecord(royal,Blocks.LECTERN.getDefaultState())),world.getTime(),List.of(),0,true,List.of(),List.of(),List.of(),"structure","/royal_castle:lectern-fixture",List.of()));
  var square=new net.minecraft.entity.MarkerEntity(EntityType.MARKER,world);square.setPosition(Vec3d.ofBottomCenter(lectern.south(2)));RoyalCourt.replaceOldLectern(world,square);
  require(world.getBlockState(lectern).isOf(Blocks.LECTERN)&&world.getBlockState(royal).isOf(RoyalProfession.CEREMONIAL_LECTERN),"Court upgrades only its own lectern, never the nearby treasury workstation");
  require(projects.expected.get(royal).isOf(RoyalProfession.CEREMONIAL_LECTERN),"Approved court conversion updates the saved construction expectation");
  org.slf4j.LoggerFactory.getLogger("structure-quality-test").info("PASS exact player last-block reproductions: ceremonial lectern and path below a lamp post; court no longer steals treasury lecterns");
 }
 private StructureQualityChecks(){}
}
