package dev.halloween;

import com.mojang.serialization.JsonOps;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import org.slf4j.LoggerFactory;

/** Isolated 0.36 integration checks on the dedicated regression world. */
final class Village036Checks {
    private static VillagerEntity routeGuard;
    private static BlockPos routeGoal;
    private static boolean ended;
    private static VillagerEntity swimmer;private static BlockPos swimBank;private static boolean swimPassed;
    private static VillagerEntity routeWorker,routeTreasurer;
    private static BlockPos routeTree,routeStock;
    private static VillageEraData.Settlement routeSettlement;
    private static int resourcePhase;private static boolean guardDone;
    private static final List<VillagerEntity> canalWorkers=new ArrayList<>();
    private static VillagerEntity canalSwimmer;private static BlockPos canalExit;private static boolean canalPassed;
    private static BlockPos occupiedRoof;private static List<VillagerEntity> roofWorkers;private static boolean roofCleared;
    static void register(){
        if(!Boolean.getBoolean("halloween.regression036"))return;
        ServerLifecycleEvents.SERVER_STARTED.register(server->{for(int x=-2;x<=3;x++)for(int z=-2;z<=2;z++)server.getOverworld().setChunkForced(x,z,true);});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(ended||server.getTicks()<60)return;
            try{
                var world=server.getOverworld();
                if(server.getTicks()==60){check(world);startRoofClearance(world);startCanal(world);}
                if(!roofCleared&&roofWorkers!=null&&server.getTicks()%20==0){
                    var occupants=world.getOtherEntities(null,new Box(occupiedRoof),e->e instanceof VillagerEntity&&e.isAlive());
                    if(server.getTicks()>100&&occupants.isEmpty()){roofCleared=true;roofWorkers.forEach(VillagerEntity::discard);LoggerFactory.getLogger("halloween-036-test").info("PASS: builders physically clear a roof placement without changing blocks");}
                    else {for(int i=0;i<8;i++)VillagePathBudget.take(world);VillageProjects.clearWorkArea(world,occupiedRoof,occupants);}
                }
                if(routeGuard!=null&&server.getTicks()%5==0){routeGuard.addCommandTag("halloween_player_guarding");for(int i=0;i<32;i++)VillagePathBudget.takeEscort(world);VillageEscort.move(routeGuard,routeGoal,.65f);}
                if(resourcePhase>0&&resourcePhase<6&&server.getTicks()%20==0)resourceRoute(world);
                if(!guardDone&&routeGuard!=null&&routeGuard.squaredDistanceTo(Vec3d.ofCenter(routeGoal))<4){
                    LoggerFactory.getLogger("halloween-036-test").info("PASS: guard physically walked around wall to destination in {} ticks",server.getTicks()-60);
                    guardDone=true;
                }
                if(swimmer!=null&&!swimPassed){if(server.getTicks()>80&&!swimmer.isTouchingWater()&&swimmer.getY()>=swimBank.getY()){swimPassed=true;swimmer.discard();LoggerFactory.getLogger("halloween-036-test").info("PASS: villager swims up and climbs onto dry bank");}}
                if(!canalPassed&&!canalWorkers.isEmpty()){
                    for(var swimmer:List.copyOf(canalWorkers))if(!swimmer.isTouchingWater()&&swimmer.getY()>=canalExit.getY()&&swimmer.squaredDistanceTo(Vec3d.ofBottomCenter(canalExit))<9){
                        require(swimmer.getHealth()>0,"canal resident survives the route");require(world.getFluidState(new BlockPos(-23,143,-23)).isIn(net.minecraft.registry.tag.FluidTags.WATER),"canal water remains in place");
                        swimmer.discard();canalWorkers.remove(swimmer);
                    }
                    if(canalWorkers.isEmpty()){canalPassed=true;LoggerFactory.getLogger("halloween-036-test").info("PASS: three residents including builder and guard swim from depth around a wall to the only bank; no draining or teleporting");}
                }
                if(canalSwimmer!=null&&!canalPassed&&server.getTicks()%200==0)LoggerFactory.getLogger("halloween-036-test").info("CANAL swimmer={} air={} health={} route={}",canalSwimmer.getEntityPos(),canalSwimmer.getAir(),canalSwimmer.getHealth(),VillageWaterRescue.describe(canalSwimmer));
                if(guardDone&&resourcePhase==6&&swimPassed&&roofCleared&&canalPassed){LoggerFactory.getLogger("halloween-036-test").info("ALL 0.36 CHECKS PASSED");ended=true;server.stop(false);}
                else if(server.getTicks()>2400)throw new AssertionError("routes did not finish: resource phase "+resourcePhase+" worker "+(routeWorker==null?"missing":routeWorker.getBlockPos())+" canal="+canalPassed);
            }catch(Throwable error){LoggerFactory.getLogger("halloween-036-test").error("0.36 CHECK FAILED",error);ended=true;server.stop(false);}
        });
    }

    private static void startCanal(ServerWorld w){
        BlockPos origin=new BlockPos(-20,140,-20);canalExit=origin.add(6,7,-3);
        for(int x=-7;x<=8;x++)for(int z=-6;z<=6;z++)for(int y=0;y<=11;y++){
            boolean wall=Math.abs(x)==6||Math.abs(z)==6||x==0&&z<=3;
            var state=y==0||wall&&y<=9?Blocks.STONE.getDefaultState():y<=5&&Math.abs(x)<6&&Math.abs(z)<6?Blocks.WATER.getDefaultState():Blocks.AIR.getDefaultState();
            w.setBlockState(origin.add(x,y,z),state,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        }
        for(int x=6;x<=8;x++)for(int y=0;y<=9;y++)w.setBlockState(origin.add(x,y,-3),y<=6?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        // Cover the test water: snowy test biomes must not turn the canal into extra dry ice exits.
        for(int x=-7;x<=8;x++)for(int z=-6;z<=6;z++)w.setBlockState(origin.add(x,12,z),Blocks.STONE.getDefaultState());
        for(int i=0;i<3;i++){
            var swimmer=new VillagerEntity(EntityType.VILLAGER,w);swimmer.setPosition(-22.5-i,141,-22.5);
            if(i==0)swimmer.setVillagerData(swimmer.getVillagerData().withProfession(BuilderProfession.ENTRY));
            if(i==1)swimmer.setVillagerData(swimmer.getVillagerData().withProfession(GuardProfession.ENTRY));
            w.spawnEntity(swimmer);canalWorkers.add(swimmer);if(i==0)canalSwimmer=swimmer;
        }
    }
    private static void startRoofClearance(ServerWorld w){
        occupiedRoof=new BlockPos(0,165,30);
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){
            for(int y=-5;y<=4;y++)w.setBlockState(occupiedRoof.add(x,y,z),Blocks.AIR.getDefaultState());
            int height=Math.max(-4,-Math.abs(x));
            w.setBlockState(occupiedRoof.add(x,height-1,z),Blocks.STONE.getDefaultState());
        }
        roofWorkers=new ArrayList<>();
        for(int i=0;i<2;i++){
            var v=new VillagerEntity(EntityType.VILLAGER,w);v.setPosition(occupiedRoof.getX()+.25+.5*i,occupiedRoof.getY(),occupiedRoof.getZ()+.5);w.spawnEntity(v);roofWorkers.add(v);
        }
    }
    private static void require(boolean test,String message){if(!test)throw new AssertionError(message);}
    private static void apply(ServerWorld w,VillageProjects.Project job){
        for(var c:job.plan)w.setBlockState(c.pos(),c.after(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        job.index=job.plan.size();job.done=true;
    }
    private static void checkCourtRecovery(ServerWorld w){
        var square=EntityType.MARKER.create(w,net.minecraft.entity.SpawnReason.EVENT);require(square!=null,"recovery square");square.setPosition(40.5,155,0.5);square.addCommandTag("halloween_square");w.spawnEntity(square);
        var worker=new VillagerEntity(EntityType.VILLAGER,w);worker.setPosition(42.5,155,.5);w.spawnEntity(worker);
        for(int cycle=0;cycle<12;cycle++){
            square.addCommandTag("halloween_active_court_"+UUID.randomUUID());worker.addCommandTag("halloween_court_builder");worker.addCommandTag("halloween_court_builder_until_0");VillageTrials.maintainCourt(w);
            require(square.getCommandTags().stream().noneMatch(t->t.startsWith("halloween_active_court_")),"stale trial ownership cleared, cycle "+cycle);
            require(!worker.getCommandTags().contains("halloween_court_builder"),"builder returns to work, cycle "+cycle);
        }
        BlockPos cage=new BlockPos(40,155,0);for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){w.setBlockState(cage.add(x,-1,z),Blocks.STONE.getDefaultState());for(int y=0;y<=4;y++)w.setBlockState(cage.add(x,y,z),Blocks.AIR.getDefaultState());}
        worker.setVillagerData(worker.getVillagerData().withProfession(BuilderProfession.ENTRY));worker.setPosition(43.5,155,.5);
        for(int cycle=0;cycle<4;cycle++){
            square.addCommandTag("halloween_cage_centre_"+cage.asLong());for(int i=0;i<20;i++)VillageTrials.buildCage(w,cage);
            require(VillageTrials.cageReady(w,cage),"cage rebuilt on consecutive cycle "+cycle);
            VillageTrials.buildCage(w,cage);require(!worker.getCommandTags().contains("halloween_court_builder"),"completed cage releases its builder");VillageTrials.removeCage(w,cage);
            require(!w.getBlockState(cage.up(3)).isOf(Blocks.IRON_BARS),"cage removal is durable");
        }
        worker.discard();square.discard();LoggerFactory.getLogger("halloween-036-test").info("PASS: twelve stale-court recoveries and four complete cage build/remove/rebuild cycles");
    }
    private static void checkTerraces(ServerWorld w){
        BlockPos edge=new BlockPos(0,150,-20);
        for(int x=-2;x<=12;x++)for(int z=-2;z<=2;z++){
            w.setBlockState(edge.add(x,-7,z),Blocks.STONE.getDefaultState());for(int y=-6;y<=3;y++)w.setBlockState(edge.add(x,y,z),Blocks.AIR.getDefaultState());
        }
        w.setBlockState(edge,Blocks.STONE_BRICKS.getDefaultState());w.setBlockState(edge.down(),Blocks.DIRT.getDefaultState());
        var wall=VillageTerraces.support(w,edge);require(!wall.isEmpty()&&wall.containsKey(edge.down(6))&&!wall.containsKey(edge.down(7)),"retaining wall reaches firm ground and stops there");
        require(wall.keySet().stream().allMatch(p->p.getY()<edge.getY()),"retaining wall never grows upward");
        var stairs=VillageTerraces.stairs(w,edge,Direction.EAST);require(!stairs.isEmpty(),"exposed plaza receives descent to lower ground");
        require(stairs.entrySet().stream().filter(e->e.getValue().getBlock() instanceof StairsBlock).count()==18,"three-wide staircase spans six steps before ground landing");
        LoggerFactory.getLogger("halloween-036-test").info("PASS: retaining wall to firm ground, no upward wall, continuous three-wide descent");
    }
    private static void checkGroundAndSwimming(ServerWorld w){
        BlockPos basin=new BlockPos(40,140,-20);swimBank=basin.up(4);
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++){
            for(int y=0;y<=4;y++)w.setBlockState(basin.add(x,y,z),Blocks.STONE.getDefaultState());
            for(int y=5;y<=9;y++)w.setBlockState(basin.add(x,y,z),Blocks.AIR.getDefaultState());
        }
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=1;y<=4;y++)w.setBlockState(basin.add(x,y,z),y==4?Blocks.AIR.getDefaultState():Blocks.WATER.getDefaultState());
        var fill=VillageGroundSafety.column(w,basin.getX(),basin.getZ());require(fill.isEmpty(),"shallow swimming water is preserved instead of being drained or filled");
        w.setBlockState(basin.add(1,3,0),Blocks.FARMLAND.getDefaultState());require(VillageGroundSafety.column(w,basin.getX(),basin.getZ()).isEmpty(),"irrigation is preserved");w.setBlockState(basin.add(1,3,0),Blocks.WATER.getDefaultState());
        var deep=basin.down(8);var change=new VillageCivilWorks.Change(deep,Blocks.AIR.getDefaultState(),Blocks.STONE_BRICKS.getDefaultState());var job=new VillageProjects.Project("deep-foundation",1,"relocation_copy",deep,basin.up(8),List.of(change),0,false,0);
        require(VillageProjects.workPosition(w,job,change).getY()>=basin.getY()+4,"deep foundation is built from surface rather than cave");
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)w.setBlockState(basin.add(x,11,z),Blocks.STONE.getDefaultState());
        swimmer=new VillagerEntity(EntityType.VILLAGER,w);swimmer.addCommandTag("halloween_guard");swimmer.setPosition(basin.getX()+.5,basin.getY()+1,basin.getZ()+.5);w.spawnEntity(swimmer);
        LoggerFactory.getLogger("halloween-036-test").info("PASS: drainage plan, irrigation protection and surface foundation access");
    }
    private static void checkResourceWorkers(ServerWorld w){
        // The felling timer uses -1 as a sentinel; simulate an elapsed 160 ticks with a positive timestamp.
        if(w.getTime()<200)try{var field=ServerWorld.class.getDeclaredField("worldProperties");field.setAccessible(true);((net.minecraft.world.level.ServerWorldProperties)field.get(w)).setTime(200);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}

        BlockPos root=new BlockPos(32,120,32);
        w.setBlockState(root.down(),Blocks.DIRT.getDefaultState());
        for(int i=0;i<3;i++)w.setBlockState(root.up(i),Blocks.OAK_LOG.getDefaultState());
        w.setBlockState(root.up(3),Blocks.OAK_LEAVES.getDefaultState());
        var worker=new VillagerEntity(EntityType.VILLAGER,w);worker.setPosition(33.5,120,32.5);w.spawnEntity(worker);
        VillageEconomy.chop(w,worker);require(w.getBlockState(root.up()).isOf(Blocks.OAK_LOG),"tree survives initial axe strike");
        VillageEras.putTag(worker,"halloween_felling_since_",w.getTime()-160);
        for(int i=0;i<5;i++){VillageEras.putTag(worker,"halloween_chop_at_",-1000);VillageEconomy.chop(w,worker);}
        require(w.getBlockState(root).isOf(Blocks.OAK_LOG)&&w.getBlockState(root.up(2)).isAir(),"lumberjack cuts the complete nearby trunk");
        require(worker.getInventory().count(Items.OAK_LOG)==2,"lumberjack receives two logs");
        BlockPos ore=root.east();w.setBlockState(ore,Blocks.COAL_ORE.getDefaultState());
        VillageEras.putTag(worker,"halloween_mine_at_",-1000);VillageEconomy.mine(w,worker);
        require(w.getBlockState(ore).isAir()&&worker.getInventory().count(Items.COAL)==1,"miner extracts coal into inventory");
        BlockPos diamond=root.east(2);w.setBlockState(diamond,Blocks.DIAMOND_ORE.getDefaultState());VillageEras.putTag(worker,"halloween_mine_at_",-1000);VillageEconomy.mine(w,worker);
        require(w.getBlockState(diamond).isAir()&&worker.getInventory().count(Items.DIAMOND)==1,"miner extracts a real diamond for armour production");
        var settlement=new VillageEraData.Settlement(987654321L,"Test");
        settlement.vault=new VillageEraData.Vault(new BlockPos(0,120,0),0,"",-1,true);
        BlockPos barrel=VillageEconomy.barrel(settlement,3);w.setBlockState(barrel,Blocks.BARREL.getDefaultState());
        var treasurer=new VillagerEntity(EntityType.VILLAGER,w);treasurer.setPosition(barrel.getX()+.5,barrel.getY()+.5,barrel.getZ()+.5);w.spawnEntity(treasurer);
        worker.setPosition(treasurer.getX()+1,treasurer.getY(),treasurer.getZ());
        VillageEconomy.deliver(w,settlement,worker,treasurer,false);
        require(worker.getInventory().count(Items.OAK_LOG)==0&&worker.getInventory().count(Items.COAL)==0,"worker delivers real stacks");
        for(int i=0;i<8;i++)VillageEconomy.manageTreasurer(w,settlement,treasurer,2000);
        var stock=(net.minecraft.block.entity.BarrelBlockEntity)w.getBlockEntity(barrel);
        require(stock!=null&&stock.count(Items.OAK_LOG)==2&&stock.count(Items.COAL)==1,"treasurer stores logs and coal in barrel");
        for(int i=0;i<stock.size();i++)stock.setStack(i,new ItemStack(Items.COBBLESTONE,64));
        worker.getInventory().addStack(new ItemStack(Items.OAK_LOG,2));
        VillageEconomy.deliver(w,settlement,worker,treasurer,false);
        require(worker.getInventory().count(Items.OAK_LOG)==2,"full treasury keeps resources with worker");
        treasurer.discard();worker.discard();w.setBlockState(barrel,Blocks.AIR.getDefaultState());w.setBlockState(root.up(3),Blocks.AIR.getDefaultState());
        LoggerFactory.getLogger("halloween-036-test").info("PASS: lumberjack trunk, miner extraction and handover into treasury barrel");
        startResourceRoute(w);
    }
    private static void startResourceRoute(ServerWorld w){
        BlockPos base=new BlockPos(0,125,40);
        for(int x=-3;x<=23;x++)for(int z=-4;z<=4;z++){
            w.setBlockState(base.add(x,-1,z),Blocks.STONE.getDefaultState());
            for(int y=0;y<6;y++)w.setBlockState(base.add(x,y,z),Blocks.AIR.getDefaultState());
        }
        routeTree=base.add(10,0,0);w.setBlockState(routeTree.down(),Blocks.DIRT.getDefaultState());
        for(int y=0;y<3;y++)w.setBlockState(routeTree.up(y),Blocks.OAK_LOG.getDefaultState());
        w.setBlockState(routeTree.up(3),Blocks.OAK_LEAVES.getDefaultState());
        routeSettlement=new VillageEraData.Settlement(987654322L,"Route test");
        routeSettlement.vault=new VillageEraData.Vault(base.add(20,0,-14),0,"",-1,true);
        routeStock=VillageEconomy.barrel(routeSettlement,3);w.setBlockState(routeStock,Blocks.BARREL.getDefaultState());
        routeWorker=new VillagerEntity(EntityType.VILLAGER,w);routeWorker.setPosition(base.getX()+.5,base.getY(),base.getZ()+.5);w.spawnEntity(routeWorker);
        routeTreasurer=new VillagerEntity(EntityType.VILLAGER,w);routeTreasurer.setPosition(routeStock.getX()+.5,routeStock.getY(),routeStock.getZ()+1.5);w.spawnEntity(routeTreasurer);
        resourcePhase=1;
    }
    private static void resourceRoute(ServerWorld w){
        if(resourcePhase==1){VillageEconomy.chop(w,routeWorker);if(routeWorker.getInventory().count(Items.OAK_LOG)==2){require(routeWorker.getX()>4,"worker walked toward tree");resourcePhase=2;}}
        else if(resourcePhase==2){VillageEconomy.deliver(w,routeSettlement,routeWorker,routeTreasurer,false);if(routeWorker.getInventory().count(Items.OAK_LOG)==0)resourcePhase=3;}
        else if(resourcePhase==3){VillageEconomy.manageTreasurer(w,routeSettlement,routeTreasurer,2000);var stock=(net.minecraft.block.entity.BarrelBlockEntity)w.getBlockEntity(routeStock);if(stock!=null&&stock.count(Items.OAK_LOG)==2){resourcePhase=4;w.setBlockState(routeTree,Blocks.COAL_ORE.getDefaultState());LoggerFactory.getLogger("halloween-036-test").info("PASS: worker walks to tree and treasury, treasurer stores logs");}}
        else if(resourcePhase==4){VillageEconomy.mine(w,routeWorker);if(routeWorker.getInventory().count(Items.COAL)==1)resourcePhase=5;}
        else if(resourcePhase==5){if(routeWorker.getInventory().count(Items.COAL)>0)VillageEconomy.deliver(w,routeSettlement,routeWorker,routeTreasurer,false);else VillageEconomy.manageTreasurer(w,routeSettlement,routeTreasurer,2000);var stock=(net.minecraft.block.entity.BarrelBlockEntity)w.getBlockEntity(routeStock);if(stock!=null&&stock.count(Items.COAL)==1){resourcePhase=6;routeWorker.discard();routeTreasurer.discard();LoggerFactory.getLogger("halloween-036-test").info("PASS: miner walks to ore and returns coal to treasury");}}
    }
    private static void check(ServerWorld w){
        for(int i=0;i<32;i++)VillagePathBudget.takeEscort(w);
        require(VillagePathBudget.takeConvoy(w),"custody path budget survives exhausted patrol budget");
        checkCourtRecovery(w);
        checkTerraces(w);
        checkGroundAndSwimming(w);
        checkResourceWorkers(w);
        BlockPos base=new BlockPos(0,120,0);
        for(int x=-25;x<=48;x++)for(int z=-20;z<=25;z++){
            w.setBlockState(base.add(x,-1,z),Blocks.STONE.getDefaultState());
            for(int y=0;y<=8;y++)w.setBlockState(base.add(x,y,z),Blocks.AIR.getDefaultState());
        }
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);var relocations=VillageRelocations.get(w);
        projects.jobs.clear();projects.expected.clear();projects.unfinished.clear();memory.houses.clear();relocations.moves.clear();
        BlockPos min=base,max=base.add(4,4,4),destination=base.add(20,0,4),chestPos=base.add(1,1,3),bed=base.add(3,1,3);
        for(int x=0;x<=4;x++)for(int z=0;z<=4;z++){
            w.setBlockState(base.add(x,0,z),Blocks.STONE_BRICKS.getDefaultState());
            for(int y=1;y<=3;y++)if(x==0||x==4||z==0||z==4)w.setBlockState(base.add(x,y,z),Blocks.BRICKS.getDefaultState());
        }
        w.setBlockState(base.add(0,2,0),Blocks.OAK_LOG.getDefaultState());
        w.setBlockState(base.add(2,0,2),Blocks.GRASS_BLOCK.getDefaultState());
        w.setBlockState(chestPos,Blocks.CHEST.getDefaultState());var chest=(ChestBlockEntity)w.getBlockEntity(chestPos);require(chest!=null,"source chest exists");chest.setStack(0,new ItemStack(Items.DIAMOND,3));
        var owner=new VillagerEntity(EntityType.VILLAGER,w);owner.setPosition(bed.getX()+.5,bed.getY(),bed.getZ()+.5);owner.getBrain().remember(MemoryModuleType.HOME,GlobalPos.create(w.getRegistryKey(),bed));w.spawnEntity(owner);
        List<VillageRelocations.Cell> cells=new ArrayList<>();List<VillageMemory.BlockRecord> records=new ArrayList<>();
        for(BlockPos p:BlockPos.iterate(min,max)){var be=w.getBlockEntity(p);var state=w.getBlockState(p);cells.add(new VillageRelocations.Cell(p.toImmutable(),state,be==null?new NbtCompound():be.createNbt(w.getRegistryManager())));records.add(new VillageMemory.BlockRecord(p.toImmutable(),state));}
        memory.houses.add(new VillageMemory.House(min,max,records,w.getTime(),List.of(),0,true,List.of()));
        var move=new VillageRelocations.Move("relocate:test036",3600,min,max,destination,cells,0,0,"");relocations.moves.add(move);
        VillageRelocations.advance(w);var copy=projects.jobs.stream().filter(p->p.id.equals(move.id+":copy")).findFirst().orElseThrow();
        require(chest.getStack(0).getCount()==3,"copy planning does not consume source stock");apply(w,copy);
        chest.setStack(1,new ItemStack(Items.EMERALD,5));chest.markDirty();
        VillageRelocations.advance(w);require(move.phase==1,"copy must finish before contents move");
        var ops=w.getRegistryManager().getOps(JsonOps.INSTANCE);var encoded=VillageRelocations.Move.CODEC.encodeStart(ops,move).getOrThrow();
        var resumed=VillageRelocations.Move.CODEC.parse(ops,encoded).getOrThrow();relocations.moves.set(0,resumed);
        w.setBlockState(base.add(0,2,0),Blocks.POLISHED_ANDESITE.getDefaultState());
        w.setBlockState(base.add(2,0,2),Blocks.DIRT_PATH.getDefaultState());
        VillageRelocations.advance(w);VillageRelocations.advance(w);
        require(resumed.phase==2,"resumed move reaches demolition phase");
        var newChest=(ChestBlockEntity)w.getBlockEntity(resumed.translated(chestPos));require(newChest!=null,"destination chest retained");
        require(newChest.getStack(0).isOf(Items.DIAMOND)&&newChest.getStack(0).getCount()==3,"diamonds transferred exactly once");
        require(newChest.getStack(1).isOf(Items.EMERALD)&&newChest.getStack(1).getCount()==5,"contents added during construction transferred");
        require(chest.isEmpty(),"source emptied after handover");
        require(owner.getBrain().getOptionalRegisteredMemory(MemoryModuleType.HOME).orElseThrow().pos().equals(resumed.translated(bed)),"resident HOME follows relocated house");
        chest.setStack(2,new ItemStack(Items.APPLE));require(!VillageRelocations.mayRemove(w,chestPos),"new source contents block demolition");chest.clear();
        var refresh=projects.jobs.stream().filter(p->p.id.equals(move.id+":refresh")).findFirst().orElseThrow();
        apply(w,refresh);VillageRelocations.advance(w);
        require(w.getBlockState(resumed.translated(base.add(0,2,0))).isOf(Blocks.POLISHED_ANDESITE),"source stone conversion copied before demolition");
        require(w.getBlockState(resumed.translated(base.add(2,0,2))).isOf(Blocks.DIRT_PATH),"source changes copied before demolition");
        var clear=projects.jobs.stream().filter(p->p.id.equals(move.id+":clear")).findFirst().orElseThrow();apply(w,clear);VillageRelocations.advance(w);
        require(resumed.phase==3,"relocation finishes after source cleared");
        var staleHouse=new VillageMemory.House(min,max,records,w.getTime(),List.of(),0,true,List.of());
        require(VillageRelocations.alreadyMoved(relocations,staleHouse),"stale source record does not trigger an endless repeat relocation");require(w.getBlockState(chestPos).isAir(),"old chest removed");
        require(newChest.getStack(0).getCount()==3&&newChest.getStack(1).getCount()==5,"demolition preserves destination stock");
        LoggerFactory.getLogger("halloween-036-test").info("PASS: copy, persisted restart, current stock handover, source protection, HOME remap and demolition");
        // The full target is behind a wall. A convoy must find a real opening, not a straight-line proxy.
        for(int x=-8;x<=8;x++)for(int y=0;y<3;y++)if(x!=5&&x!=6)w.setBlockState(base.add(x,y,-8),Blocks.STONE_BRICKS.getDefaultState());
        var guard=new VillagerEntity(EntityType.VILLAGER,w);guard.setPosition(-.5,120,-12.5);guard.setOnGround(true);w.spawnEntity(guard);
        guard.addCommandTag("halloween_player_guarding");VillageEscort.move(guard,base.add(0,0,-4),.65f);var path=guard.getNavigation().getCurrentPath();
        require(path!=null&&path.reachesTarget(),"escort finds path around wall");routeGuard=guard;routeGoal=base.add(0,0,-4);
        for(int i=0;i<path.getLength();i++){var pos=path.getNodePos(i);require(w.getBlockState(pos).getCollisionShape(w,pos).isEmpty(),"escort route stays outside wall "+pos);}
        LoggerFactory.getLogger("halloween-036-test").info("PASS: complete convoy path reaches destination behind wall");
        var design=VillageEraPlans.template(w,"stone_treasury");require(design!=null&&design.size().equals(new Vec3i(19,15,19)),"treasury dimensions");
        for(Direction facing:Direction.Type.HORIZONTAL){var vault=new VillageEraData.Vault(base,facing.getHorizontalQuarterTurns(),"",-1,false);
            for(int i=0;i<6;i++){int side=i*2-5,x=9-side;require(design.blocks().stream().anyMatch(c->c.pos().equals(new BlockPos(x,1,14))&&c.state().isOf(Blocks.BARREL)),"treasury barrel maps to economy slot "+i);}
        }
        LoggerFactory.getLogger("halloween-036-test").info("PASS: treasury dimensions and all six stock slots");
        var settlement=new VillageEraData.Settlement(3600,"Regression");settlement.transitionAt=0;settlement.conversionCount=51;settlement.xp.put("architecture_v2",2);
        require(VillageEras.conversionsDone(projects,3600,settlement),"completed migration not blocked by obsolete conversion count");
        var blocked=new VillageProjects.Project("conversion:test036",3600,"conversion",base,base,List.of(),0,false,0);projects.jobs.add(blocked);
        require(!VillageEras.conversionsDone(projects,3600,settlement),"pending conversion still blocks completion");projects.jobs.remove(blocked);
        LoggerFactory.getLogger("halloween-036-test").info("PASS: era completion handles old counts without ignoring unfinished work");
        // An old entrance must get a continuous road without touching buildings or crops.
        BlockPos roadStart=base.add(25,0,-14),roadEnd=base.add(43,0,-14);
        for(int x=24;x<=44;x++)for(int z=-18;z<=-10;z++)w.setBlockState(base.add(x,-1,z),Blocks.GRASS_BLOCK.getDefaultState());
        BlockPos crop=base.add(31,-1,-13),foundation=base.add(32,-1,-13);
        w.setBlockState(crop,Blocks.FARMLAND.getDefaultState());w.setBlockState(foundation,Blocks.BRICKS.getDefaultState());
        var road=VillageRoadNetwork.plan(w,roadStart,roadEnd);
        require(!road.isEmpty(),"road planner reaches existing entrance");
        require(!road.containsKey(crop)&&!road.containsKey(foundation),"roads preserve fields and foundations");
        for(int x=25;x<=41;x++){final int column=x;require(road.keySet().stream().anyMatch(p->p.getX()==column),"continuous old-house road at "+x);}
        LoggerFactory.getLogger("halloween-036-test").info("PASS: old-house road route is continuous and preserves crops and foundations");
        require(VillageProjects.compatible(Blocks.FARMLAND.getDefaultState(),Blocks.GRASS_BLOCK.getDefaultState()),"trampled garden resumes plaza construction");
        require(VillageProjects.compatible(Blocks.GRASS_BLOCK.getDefaultState(),Blocks.DIRT_PATH.getDefaultState()),"new path does not block farm or workshop");
        require(!VillageProjects.compatible(Blocks.GRASS_BLOCK.getDefaultState(),Blocks.DIAMOND_BLOCK.getDefaultState()),"foreign building remains protected");
        require(VillageRelocations.sourceCompatible(Blocks.GRASS_BLOCK.getDefaultState(),Blocks.DIRT_PATH.getDefaultState()),"source path change allows relocation demolition");
        require(VillageRelocations.sourceCompatible(Blocks.OAK_STAIRS.getDefaultState(),Blocks.STONE_BRICK_STAIRS.getDefaultState()),"recognised stone upgrade does not deadlock relocation");
        require(!VillageRelocations.sourceCompatible(Blocks.BRICKS.getDefaultState(),Blocks.DIAMOND_BLOCK.getDefaultState()),"changed structural source remains protected");
        var conflict=new VillageCivilWorks.Change(foundation,Blocks.GRASS_BLOCK.getDefaultState(),Blocks.DIRT_PATH.getDefaultState());
        var roadJob=new VillageProjects.Project("road:foundation:regression",3600,"road",foundation,foundation,List.of(conflict),0,false,4000);
        projects.jobs.add(roadJob);projects.expected.put(foundation,conflict.after());
        require(VillageProjects.bypassRoadFoundation(w,projects,roadJob,conflict)&&roadJob.index==1,"road continues past new foundation");
        require(w.getBlockState(foundation).isOf(Blocks.BRICKS),"road preserves foundation");
        require(!Blocks.DIRT_PATH.getDefaultState().equals(projects.expected.get(foundation)),"repair system no longer overwrites foundation with road");
        LoggerFactory.getLogger("halloween-036-test").info("PASS: saved queue soil conflicts recover, new foundations survive roads, foreign blocks stay protected");
        BlockPos damagedPlot=base.add(38,-1,-17);w.setBlockState(damagedPlot,Blocks.DIRT.getDefaultState());w.setBlockState(damagedPlot.up(),Blocks.AIR.getDefaultState());
        var cropPlan=List.of(new VillageCivilWorks.Change(damagedPlot,Blocks.DIRT.getDefaultState(),Blocks.FARMLAND.getDefaultState()),new VillageCivilWorks.Change(damagedPlot.up(),Blocks.AIR.getDefaultState(),Blocks.CARROTS.getDefaultState()));
        var farmJob=new VillageProjects.Project("plantation:recovery-test",3600,"plantation",damagedPlot,damagedPlot.up(),cropPlan,2,true,0);projects.jobs.add(farmJob);
        VillageFarms.recoverPlantation(w,memory);
        require(memory.farms.stream().anyMatch(f->f.pos().equals(damagedPlot)&&f.crop().isOf(Blocks.CARROTS)),"existing trampled farmland is queued with its crop");
        LoggerFactory.getLogger("halloween-036-test").info("PASS: old trampled plantation ground is queued for farmer repair");
        try {
            BlockPos cage=base.add(35,0,12);var square=new net.minecraft.entity.MarkerEntity(EntityType.MARKER,w);
            square.setPosition(cage.getX()+.5,cage.getY(),cage.getZ()+.5);square.addCommandTag("halloween_square");
            square.addCommandTag("halloween_cage_centre_"+cage.asLong());w.spawnEntity(square);
            BlockPos bar=cage.add(1,0,0),untouched=cage.add(-1,0,0);w.setBlockState(bar,Blocks.IRON_BARS.getDefaultState());w.setBlockState(untouched,Blocks.IRON_BARS.getDefaultState());
            var remember=VillageTrials.class.getDeclaredMethod("rememberCageBlock",ServerWorld.class,BlockPos.class,BlockPos.class);remember.setAccessible(true);remember.invoke(null,w,cage,bar);
            var field=VillageTrials.class.getDeclaredField("CAGES");field.setAccessible(true);((Map<?,?>)field.get(null)).clear();
            var defendant=new VillagerEntity(EntityType.VILLAGER,w);defendant.setPosition(cage.getX()+3.5,cage.getY(),cage.getZ()+.5);w.spawnEntity(defendant);
            defendant.addCommandTag("halloween_cage_arrived_"+(w.getTime()-100));
            require(VillageTrials.enterAccused(w,defendant,cage),"defendant enters cage after five seconds");
            require(defendant.getCommandTags().contains("halloween_court_inside")&&defendant.squaredDistanceTo(Vec3d.ofBottomCenter(cage))<.5,"defendant placed inside before trial");defendant.discard();
            VillageTrials.removeCage(w,cage);
            require(w.getBlockState(bar).isAir(),"owned court cage removed after reload");
            require(w.getBlockState(untouched).isOf(Blocks.IRON_BARS),"unowned bars remain after cage cleanup");
            square.discard();w.setBlockState(untouched,Blocks.AIR.getDefaultState());
            LoggerFactory.getLogger("halloween-036-test").info("PASS: persistent cage ownership removes only owned blocks after reload");
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        var ministerSquare=new net.minecraft.entity.MarkerEntity(EntityType.MARKER,w);ministerSquare.setPosition(base.getX()+.5,base.getY(),base.getZ()+.5);w.spawnEntity(ministerSquare);
        var deputyA=new VillagerEntity(EntityType.VILLAGER,w);deputyA.setPosition(base.getX()+2.5,base.getY(),base.getZ()+2.5);deputyA.setVillagerData(deputyA.getVillagerData().withProfession(RoyalProfession.DEPUTY_ENTRY));deputyA.addCommandTag("halloween_square_"+ministerSquare.getUuidAsString());w.spawnEntity(deputyA);
        var deputyB=new VillagerEntity(EntityType.VILLAGER,w);deputyB.setPosition(base.getX()+3.5,base.getY(),base.getZ()+2.5);deputyB.setVillagerData(deputyB.getVillagerData().withProfession(RoyalProfession.DEPUTY_ENTRY));deputyB.addCommandTag("halloween_square_"+ministerSquare.getUuidAsString());w.spawnEntity(deputyB);
        RoyalCourt.ensureDeputy(w,ministerSquare);RoyalCourt.ensureDeputy(w,ministerSquare);
        require(deputyA.isAlive()&&deputyB.isAlive(),"duplicate ministers remain alive");
        require((deputyA.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)?1:0)+(deputyB.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)?1:0)==1,"one minister remains after repeated management");
        require(ministerSquare.getCommandTags().stream().anyMatch(tag->tag.startsWith("halloween_deputy_id_")),"minister identity persists on square");
        require(VillageEpoch.steamAvailable()&&VillageEpoch.STEAM==3,"steam era available behind construction prerequisites");
        deputyA.discard();deputyB.discard();ministerSquare.discard();
        LoggerFactory.getLogger("halloween-036-test").info("PASS: duplicate ministers restored to civilians, identity saved, third era reserved");
        BlockPos rising=base.add(40,0,20);w.setBlockState(rising,Blocks.DIRT.getDefaultState());w.setBlockState(rising.up(),Blocks.DIRT.getDefaultState());
        require(VillageEraPlans.levelSite(w,rising.getX()-1,rising.getZ(),2,1,4)==rising.getY()+2,"new house height uses the higher part of the building footprint");
        LoggerFactory.getLogger("halloween-036-test").info("PASS: new house foundation height follows higher terrain");
        require(PlayerJustice.wantedLevel(0,0,0)==1&&PlayerJustice.wantedLevel(0,1,0)==1,"minor offenses are first star");
        require(PlayerJustice.wantedLevel(1,0,0)==2&&PlayerJustice.wantedLevel(0,2,0)==2,"murder and two robberies are second star");
        require(PlayerJustice.wantedLevel(3,0,0)==3&&PlayerJustice.wantedLevel(0,3,0)==3,"three murders or robberies are third star");
        LoggerFactory.getLogger("halloween-036-test").info("PASS: wanted star thresholds");
        checkPrison(w,base.add(0,-30,16));

    }
    @SuppressWarnings("unchecked")
    private static void checkPrison(ServerWorld w,BlockPos a){
        try{
            var jail=new net.minecraft.entity.MarkerEntity(EntityType.MARKER,w);jail.setPosition(a.getX()+.5,a.getY(),a.getZ()+.5);jail.addCommandTag("halloween_jail");jail.addCommandTag("halloween_prison_facing_0");jail.addCommandTag("halloween_prison_rise_14");w.spawnEntity(jail);
            var gate=new net.minecraft.entity.MarkerEntity(EntityType.MARKER,w);gate.setPosition(a.getX()+3.5,a.getY()+8,a.getZ()+.5);gate.addCommandTag("halloween_gate_left");w.spawnEntity(gate);
            BlockPos ladder=a.up(14),obsolete=a.offset(Direction.SOUTH,-12).up(11);
            w.setBlockState(ladder,Blocks.LADDER.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);w.setBlockState(obsolete,Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING,Direction.NORTH));
            VillagePrisons.prepare(w,jail);
            var layouts=VillagePrisons.class.getDeclaredField("LAYOUTS");layouts.setAccessible(true);var layout=((Map<Object,Object>)layouts.get(null)).get(jail);
            var field=layout.getClass().getDeclaredField("plan");field.setAccessible(true);var plan=(List<Map.Entry<BlockPos,BlockState>>)field.get(layout);
            require(plan.stream().anyMatch(e->e.getKey().equals(obsolete)&&e.getValue().isAir()),"obsolete upper stairs are removed");
            require(plan.stream().filter(e->e.getValue().isOf(Blocks.STONE_BRICK_STAIRS)).allMatch(e->e.getKey().getY()<gate.getBlockY()),"dungeon stairs never rise above castle floor");
            for(var e:plan)w.setBlockState(e.getKey(),e.getValue(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
            require(!w.getBlockState(ladder).isOf(Blocks.LADDER),"ladder shaft removed");jail.addCommandTag("halloween_prison_30_v2");
            BlockPos blockedStairs=a.offset(Direction.SOUTH,-4).up(4);w.setBlockState(blockedStairs,Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
            require(VillagePrisons.managed(w,blockedStairs),"dungeon staircase is reserved from building repairs");VillagePrisons.reopenBlockedStairs(w,jail);
            require(w.getBlockState(blockedStairs).isAir(),"stone blocking existing dungeon stairs is cleared");

            var first=new VillagerEntity(EntityType.VILLAGER,w);var second=new VillagerEntity(EntityType.VILLAGER,w);w.spawnEntity(first);w.spawnEntity(second);
            var cell1=VillagePrisons.cellFor(w,jail,first);var cell2=VillagePrisons.cellFor(w,jail,second);
            require(cell1!=null&&cell2!=null&&!cell1.equals(cell2)&&cell1.getSquaredDistance(a)==cell2.getSquaredDistance(a),"first prisoners use nearest left and right cells");
            var route=VillagePrisons.escortRoute(w,jail,first);require(route.getFirst().getY()==gate.getBlockY(),"escort enters stairs at castle floor");
            LoggerFactory.getLogger("halloween-036-test").info("PASS: no ladder, no stairs above castle floor, nearest cells and dungeon entry elevation");
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
    }
    private Village036Checks(){}
}
