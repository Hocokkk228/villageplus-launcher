package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Opt-in, isolated dedicated-server regression suite; never runs in a normal game. */
final class VillageRegressionChecks {
    static void register(){
        if(!Boolean.getBoolean("halloween.regression")||Boolean.getBoolean("halloween.regression036"))return;
        ServerLifecycleEvents.SERVER_STARTED.register(server->{for(int x=-2;x<=10;x++)for(int z=-2;z<=10;z++)server.getOverworld().setChunkForced(x,z,true);for(int x=21;x<=23;x++)for(int z=18;z<=21;z++)server.getOverworld().setChunkForced(x,z,true);for(int x=25;x<=29;x++)for(int z=21;z<=27;z++)server.getOverworld().setChunkForced(x,z,true);});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTicks()!=40)return;
            try{checkInjuries(server.getOverworld());checkVillagerSwing(server.getOverworld());check(server.getOverworld());MarketChecks.check(server.getOverworld());checkStreetCrime(server.getOverworld());RailRegressionChecks.check(server.getOverworld());IndustrialChecks.check(server.getOverworld());org.slf4j.LoggerFactory.getLogger("halloween-regression").info("ALL VILLAGE REGRESSIONS PASSED");}
            catch(Throwable error){org.slf4j.LoggerFactory.getLogger("halloween-regression").error("REGRESSION FAILED",error);throw new RuntimeException(error);}
            finally{server.stop(false);}
        });
    }
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static void invoke(Class<?> type,String method,Class<?>[] signature,Object... args)throws Exception{var m=type.getDeclaredMethod(method,signature);m.setAccessible(true);m.invoke(null,args);}
    private static VillagerEntity villager(ServerWorld w,BlockPos p){var v=new VillagerEntity(EntityType.VILLAGER,w);v.setPosition(p.getX()+.5,p.getY(),p.getZ()+.5);w.spawnEntity(v);return v;}
    private static void checkInjuries(ServerWorld world){
        var victim=villager(world,new BlockPos(148,210,148));
        var attacker=villager(world,new BlockPos(150,210,148));victim.setNoGravity(true);attacker.setNoGravity(true);
        victim.damage(world,world.getDamageSources().mobAttack(attacker),4);
        require(VillageInjuries.panicking(victim),"actual melee damage interrupts work and starts panic");
        float wounded=victim.getHealth();VillageInjuries.calm(victim);
        VillageInjuries.recover(world,victim,world.getTime());require(victim.getHealth()==wounded,"wounds do not heal immediately during an attack");
        VillageInjuries.recover(world,victim,(world.getTime()/100+6)*100);require(victim.getHealth()>wounded,"health recovers gradually after combat cooldown");
        victim.discard();attacker.discard();
        var point=new BlockPos(148,210,151);var state=VillageProjects.get(world);
        state.add(world,"audit_repair_fixture",991122,"factory",Map.of(point,Blocks.BRICKS.getDefaultState()));
        for(int i=0;i<16;i++)VillageProjects.materialize(world,991122,8);var project=state.jobs.stream().filter(p->p.id.equals("audit_repair_fixture")).findFirst().orElseThrow();
        require(project.done,"audit fixture initially completes");world.removeBlock(point,false);
        require(VillageConstructionAudit.reopen(world,project,0),"a missing block reopens a completed building job");for(int i=0;i<16;i++)VillageProjects.materialize(world,991122,8);
        require(project.done&&world.getBlockState(point).isOf(Blocks.BRICKS),"reopened building is actually repaired");
    }
    private static void checkVillagerSwing(ServerWorld world) {
        var guard = new VillagerEntity(EntityType.VILLAGER, world);
        guard.setPosition(8, 110, 8);
        guard.setAiDisabled(true);
        guard.setNoGravity(true);
        for (var hand : net.minecraft.util.Hand.values()) {
            // Use the entity's real attack and tick paths, not a fabricated render-state curve.
            guard.swingHand(hand);
            float maximum = 0;
            for (int tick = 0; tick < 16; tick++) {
                guard.tick();
                maximum = Math.max(maximum, guard.getHandSwingProgress(1));
            }
            require(maximum > .5f, "villager attack must progress past its zero frame: " + hand);
            require(guard.getHandSwingProgress(1) == 0 && !guard.handSwinging, "villager attack must finish and allow the next strike");
            require(guard.preferredHand == hand, "offhand attack keeps its selected hand");
        }
        guard.discard();
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS actual villager swing advances, resets and repeats with either hand");
    }
    private static void checkStreetCrime(ServerWorld world) {
        var base = new BlockPos(145, 190, 139);
        var first = villager(world, base.west());
        var second = villager(world, base.east());
        var civilian = villager(world, base);
        var witness = villager(world, base.south(4));
        var third= villager(world,base.south());
        first.addCommandTag("halloween_bandit");
        second.addCommandTag("halloween_bandit");
        third.addCommandTag("halloween_bandit");

        var server = world.getServer();
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "StreetObserver");
        var player = new net.minecraft.server.network.ServerPlayerEntity(server, world, profile,
                net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());
        player.networkHandler = new net.minecraft.server.network.ServerPlayNetworkHandler(server,
                new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),
                player, net.minecraft.server.network.ConnectedClientData.createDefault(profile, false)) {
            @Override public boolean canInteractWithGame() { return true; }
        };
        player.setPosition(Vec3d.ofBottomCenter(base.south(12)));
        world.onPlayerConnected(player);
        try {
            player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            var district = new VillageMemory.Team(base.asLong(), base, 0);
            var gang = List.of(first, second);
            var residents = List.of(first, second, civilian);
            require(!StreetCrime.targetable(player), "spectator observes without becoming a target");
            var incident = StreetCrime.find(world, district, residents);
            require(incident != null && incident.victim.equals(civilian.getUuid()) && incident.gang.size() == 2,
                    "two existing bandits select the isolated civilian as one gang");
            require(StreetCrime.advance(world,incident,residents),"first gang member winds up");
            require(civilian.getHealth()==civilian.getMaxHealth(),"damage waits for the punch impact");
            StreetCrime.resolveStrike(world,incident,world.getTime()+5);
            require(civilian.getHealth()<civilian.getMaxHealth(),"first punch actually hurts the civilian");
            civilian.timeUntilRegen = 0;
            require(StreetCrime.advance(world,incident,List.of(first,second,civilian,witness)),"second gang member winds up");
            StreetCrime.resolveStrike(world,incident,world.getTime()+5);
            require(second.getCommandTags().contains("halloween_crime_street_assault")
                    && first.getCommandTags().contains("halloween_wanted")
                    && second.getCommandTags().contains("halloween_wanted"),
                    "gang alternates attacks and a witness reports both attackers");
            witness.addCommandTag("halloween_guard");
            float health = civilian.getHealth();
            require(!StreetCrime.advance(world, incident, List.of(first, second, civilian, witness))
                    && civilian.getHealth() == health && VillageDefense.assigned(world, witness, first),
                    "patrol breaks up the attack and can pursue the wanted bandit");

            player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            player.setPosition(Vec3d.ofBottomCenter(base));
            require(StreetCrime.targetable(player), "ordinary survival player is eligible for street violence");
            var trio=List.of(first,second,third);
            var againstPlayer=new StreetCrime.Incident(player,trio,world.getTime());
            require(StreetCrime.advance(world,againstPlayer,trio)&&VillageFights.running(world,player),
                    "three gang members engage a survival player");
            require(trio.stream().allMatch(v->FightPose.of(v).halloween$fightPose()==FightPose.GUARD),
                    "the whole trio takes a fighting stance");
            player.setPosition(Vec3d.ofBottomCenter(base.south(45)));
            VillageFights.tick(world);
            require(!VillageFights.running(world,player)&&!StreetCrime.advance(world,againstPlayer,trio),
                    "leaving the district breaks the pursuit");
            third.discard();
            player.setPosition(Vec3d.ofBottomCenter(base));
            first.setHealth(5);
            require(VillageBounties.capture(world, player, first, witness)
                    && first.getLeashHolder() == witness && VillageTrials.busy(first)
                    && player.getInventory().count(net.minecraft.item.Items.EMERALD) == 6,
                    "bounty hands over a live suspect and pays only after arrest");
            require(!VillageBounties.capture(world, player, first, witness), "repeat arrest cannot claim a second bounty");
            NightWatch.alarm(world, district, List.of(civilian, witness), base.north(48));
            require(NightWatch.onBoundary(base, base.north(48), 48, 1)
                    && !NightWatch.onBoundary(base, base.north(12), 48, 1)
                    && base.north(48).equals(NightWatch.response(world, district))
                    && VillageTrials.busy(civilian), "night alarm sends civilians to shelter and dispatches a patrol");

            var reserve = new net.minecraft.inventory.SimpleInventory(3);
            var receiving = new net.minecraft.inventory.SimpleInventory(1);
            reserve.setStack(0, new net.minecraft.item.ItemStack(VillageItems.MUSKET_CARTRIDGE, 48));
            require(VillageConvoys.load(world, civilian, reserve, 1, 2)
                    && civilian.getOffHandStack().getCount() == 24 && reserve.count(VillageItems.MUSKET_CARTRIDGE) == 24,
                    "convoy takes physical ammunition from the source");
            float beforeAmbush = civilian.getHealth();
            civilian.timeUntilRegen = 0;
            VillageConvoys.ambush(world, civilian);
            require(VillageConvoys.fighting(world, second)
                    && (world.getTime() % 40 != 0 || civilian.getHealth() < beforeAmbush),
                    "a real bandit intercepts and can damage the convoy porter");

            receiving.setStack(0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.STONE, 64));
            require(!VillageConvoys.unload(civilian, receiving) && civilian.getOffHandStack().getCount() == 24,
                    "blocked destination keeps cargo on the carrier");
            receiving.clear();
            require(VillageConvoys.unload(civilian, receiving)
                    && receiving.count(VillageItems.MUSKET_CARTRIDGE) == 24 && civilian.getOffHandStack().isEmpty()
                    && !VillageConvoys.owns(civilian), "convoy delivery conserves ammunition");

        } finally {
            world.removePlayer(player, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            for (var v : List.of(first, second, civilian, witness)) v.discard();
        }
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info(
                "PASS street crime, convoy ambush and cargo conservation, bounty arrest and night alarm");
    }

    private static void checkGothicRebuild(ServerWorld w,VillageMemory.Team team,VillagerEntity resident)throws Exception{
        var villaTemplate=VillageEraPlans.template(w,"steam_villa");for(int x=6;x<=10;x++)for(int z=5;z<=9;z++){var roof=new BlockPos(x,9,z);require(villaTemplate.blocks().stream().anyMatch(r->r.pos().equals(roof)&&r.state().isOf(Blocks.GLASS)),"Roman upper bedroom has a sealed skylight instead of an open atrium");}
        for(int side=0;side<4;side++){var center=new BlockPos(0,80,0);var min=side<2?new BlockPos(side==0?-105:95,70,-10):new BlockPos(-10,70,side==2?-105:95);var max=min.add(10,12,20);var detour=PerimeterExtensions.route(center,100,side,min,max);require(!detour.isEmpty(),"border plans a bypass behind intersecting buildings");for(int i=1;i<detour.size();i++)require(detour.get(i).getManhattanDistance(detour.get(i-1))==1,"border bypass stays continuous at corners");for(var p:detour)require(p.getX()<min.getX()||p.getX()>max.getX()||p.getZ()<min.getZ()||p.getZ()>max.getZ(),"border bypass stays outside building footprint");}
        var bridgeFrom=new BlockPos(115,170,120);var bridgeTo=new BlockPos(145,170,120);
        for(int x=110;x<=150;x++)for(int z=110;z<=130;z++){w.setBlockState(new BlockPos(x,166,z),Blocks.STONE.getDefaultState());for(int y=167;y<=169;y++)w.setBlockState(new BlockPos(x,y,z),x>=125&&x<=134?Blocks.WATER.getDefaultState():Blocks.DIRT.getDefaultState());}
        var bridgePlan=VillageBridges.plan(w,bridgeFrom,bridgeTo,3);require(bridgePlan!=null&&!bridgePlan.isEmpty(),"workplace across river receives a bridge plan");require(bridgePlan.values().stream().anyMatch(b->b.isOf(Blocks.LANTERN)),"bridge includes real lanterns");
        var bridgeJobs=VillageProjects.get(w);bridgeJobs.add(w,"bridge-regression",bridgeFrom.asLong(),"bridge",bridgePlan);var bridgeJob=bridgeJobs.jobs.stream().filter(j->j.id.equals("bridge-regression")).findFirst().orElseThrow();for(int n=0;n<1000&&!bridgeJob.done;n++)VillageProjects.materialize(w,bridgeFrom.asLong(),1000);require(bridgeJob.done,"builders queue completes a real river crossing");
        require(w.getFluidState(new BlockPos(130,169,122)).isIn(net.minecraft.registry.tag.FluidTags.WATER),"river remains water below the deck");
        var walker=new VillagerEntity(EntityType.VILLAGER,w);walker.setPosition(115.5,170,120.5);walker.setOnGround(true);var crossing=walker.getNavigation().findPathTo(bridgeTo,0);require(crossing!=null&&crossing.reachesTarget(),"ordinary villager navigation reaches the opposite bank over the completed bridge");for(int i=0;i<crossing.getLength();i++)require(w.getFluidState(crossing.getNodePos(i)).isEmpty(),"bridge route does not ask the villager to swim");
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS bridge construction, lanterns, preserved river and villager path across both bank approaches");
        var cathedral=VillageEraPlans.template(w,"cologne_cathedral");require(cathedral!=null&&cathedral.size().getY()==100,"full cathedral template loads");
        for(var point:List.of(new BlockPos(32,28,14),new BlockPos(32,29,75),new BlockPos(17,24,39),new BlockPos(48,24,56)))
            require(cathedral.blocks().stream().anyMatch(r->r.pos().equals(point)&&!r.state().isAir()),"Cathedral roof opening is sealed at "+point);
        require(cathedral.blocks().stream().anyMatch(r->r.pos().equals(new BlockPos(48,8,64))&&r.state().isOf(Blocks.PURPLE_BED)),"Royal bedroom is furnished");
        long transitionFixture=541605L;
        var industrialJobs=VillageProjects.get(w);
        var worksite=new BlockPos(118,200,130);
        for(var kind:List.of("factory","rail_station","rail_foundry"))industrialJobs.add(w,"transition_gate:"+kind,transitionFixture,kind,
                Map.of(worksite.east(kind.length()),Blocks.STONE_BRICKS.getDefaultState()));
        require(!VillageSteam.finishedInfrastructure(industrialJobs,transitionFixture,false,true),"Queued buildings cannot announce III");
        for(int i=0;i<32;i++)VillageProjects.materialize(w,transitionFixture,64);
        require(!VillageSteam.finishedInfrastructure(industrialJobs,transitionFixture,false,false),"A station alone cannot announce III before trains");
        require(!VillageSteam.finishedInfrastructure(industrialJobs,transitionFixture,true,true),"A surveyed coast requires the shipyard");
        require(VillageSteam.finishedInfrastructure(industrialJobs,transitionFixture,false,true),"Inland transition opens after completed factory and running rail");
        require(cathedral.blocks().stream().anyMatch(r->r.pos().equals(new BlockPos(48,8,64))&&r.state().isOf(Blocks.PURPLE_BED)&&r.state().get(BedBlock.PART)==net.minecraft.block.enums.BedPart.HEAD),"cathedral includes real royal sleeping quarters");
        for(int z=14;z<=75;z++)for(int y=17;y<20;y++){var edge=new BlockPos(12,y,z);require(cathedral.blocks().stream().anyMatch(r->r.pos().equals(edge)&&!r.state().isAir()),"cathedral lower roof perimeter closes against exterior wall");}
        for(int x:new int[]{18,32,46})require(cathedral.blocks().stream().anyMatch(r->r.pos().equals(new BlockPos(x,2,5))&&r.state().isAir()),"three cathedral portals stay open");
        require(cathedral.blocks().stream().anyMatch(r->r.pos().equals(new BlockPos(32,2,30))&&r.state().isAir()),"cathedral central aisle is clear");
        BlockPos source=new BlockPos(25,100,10),target=new BlockPos(-5,100,8);
        var head=Blocks.RED_BED.getDefaultState().with(BedBlock.PART,net.minecraft.block.enums.BedPart.HEAD);
        var foot=Blocks.RED_BED.getDefaultState().with(BedBlock.PART,net.minecraft.block.enums.BedPart.FOOT);
        var oldRecords=List.of(new VillageMemory.BlockRecord(source,head),new VillageMemory.BlockRecord(source.south(),foot),new VillageMemory.BlockRecord(source.east(2),Blocks.BARREL.getDefaultState()));
        var house=new VillageMemory.House(source.add(-1,-1,-1),source.add(4,3,4),oldRecords,0,List.of(),0,true,List.of(),List.of(),List.of(),"house","regression_normal_home",List.of());
        require(VillageHousingRebuild.ordinary(house),"ordinary resident home is scheduled for full replacement");
        var professional=new VillageMemory.House(house.min,house.max,oldRecords,0,List.of(),0,true,List.of(),List.of(),List.of(),"house","template:profession_armorer_0",List.of());
        require(!VillageHousingRebuild.ordinary(professional),"professional home is excluded");
        var gothic=new VillageMemory.House(house.min,house.max,oldRecords,0,List.of(),0,true,List.of(),List.of(),List.of(),"house","gothic_rebuild:regression",List.of());
        require(!VillageArchitectureMigration.ordinary(gothic)&&!VillageArchitecture.eligible(gothic),"second-era converters preserve third-era homes");
        var probe=villager(w,new BlockPos(10,170,10));probe.setPosition(10.5,170.5,10.5);
        require(VillageProjects.placementOccupants(w,new BlockPos(10,170,10),Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState()).isEmpty(),"worker above slab does not obstruct its lower collision shape");
        require(VillageProjects.placementOccupants(w,new BlockPos(10,170,10),Blocks.STONE.getDefaultState()).contains(probe),"full block still detects the same worker");
        require(!VillageProjects.outsidePlacement(probe,new BlockPos(10,170,10),new Vec3d(10.5,170,10.5)),"floor below pending window must not count as an escape destination");
        require(VillageProjects.outsidePlacement(probe,new BlockPos(10,170,10),new Vec3d(11.5,170,10.5)),"adjacent clear standing point is a real escape");probe.discard();
        BlockPos trapped=new BlockPos(10,175,10);var exitWorker=villager(w,trapped);var walls=new ArrayList<VillageCivilWorks.Change>();
        for(var direction:Direction.Type.HORIZONTAL){
            w.setBlockState(trapped.offset(direction,2).down(),Blocks.STONE.getDefaultState());
            for(var pos:List.of(trapped.offset(direction),trapped.offset(direction).up())){
                walls.add(new VillageCivilWorks.Change(pos,Blocks.AIR.getDefaultState(),Blocks.STONE_BRICKS.getDefaultState()));
                w.setBlockState(pos,Blocks.DIAMOND_BLOCK.getDefaultState());
            }
        }
        var exitJob=new VillageProjects.Project("roof-exit-test",team.village,"house",trapped.add(-2,-1,-2),trapped.add(2,2,2),walls,0,false,0);
        require(!VillageProjects.openConstructionExit(w,exitJob,exitWorker),"construction exit never removes foreign blocks");
        for(var c:walls)w.setBlockState(c.pos(),c.after());int wallCount=walls.size();
        require(VillageProjects.openConstructionExit(w,exitJob,exitWorker),"trapped builder opens an owned exit");
        require(exitJob.plan.size()==wallCount+2,"both temporary opening blocks remain scheduled for restoration");
        for(var c:exitJob.plan.subList(wallCount,exitJob.plan.size()))require(w.getBlockState(c.pos()).isAir()&&c.after().isOf(Blocks.STONE_BRICKS),"exit retains the exact original wall material");
        VillageProjects.get(w).retire(exitJob);exitWorker.discard();
        {var localWorker=villager(w,new BlockPos(80,180,80));
        var prefix=new VillageCivilWorks.Change(new BlockPos(75,179,80),Blocks.AIR.getDefaultState(),Blocks.STONE.getDefaultState());
        var far=new VillageCivilWorks.Change(new BlockPos(110,180,80),Blocks.AIR.getDefaultState(),Blocks.STONE.getDefaultState());
        var near=new VillageCivilWorks.Change(new BlockPos(82,180,80),Blocks.AIR.getDefaultState(),Blocks.STONE.getDefaultState());
        var nextLayer=new VillageCivilWorks.Change(new BlockPos(81,181,80),Blocks.AIR.getDefaultState(),Blocks.STONE.getDefaultState());
        var localJob=new VillageProjects.Project("local-cathedral-test",team.village,"cathedral",prefix.pos(),far.pos().up(2),List.of(prefix,far,near,nextLayer),1,false,0);
        localJob.plan.subList(1,localJob.plan.size()).clear();localJob.plan.addAll(List.of(far,near,nextLayer));
        require(VillageProjects.nearbyLayerWork(w,localJob,localWorker),"cathedral worker selects reachable independent work on the current layer");
        require(localJob.index==1&&localJob.plan.getFirst().equals(prefix)&&localJob.plan.get(1).equals(near)&&new HashSet<>(localJob.plan).equals(Set.of(prefix,far,near,nextLayer)),"local scheduling preserves completed prefix, all operations and layer dependency");
        localWorker.discard();}
        BlockPos basement=new BlockPos(55,170,55);var stairWorker=villager(w,basement);
        var roof=new ArrayList<VillageCivilWorks.Change>();
        for(var d:Direction.Type.HORIZONTAL){w.setBlockState(basement.offset(d),Blocks.STONE.getDefaultState());w.setBlockState(basement.offset(d,2),Blocks.STONE.getDefaultState());}
        for(var pos:List.of(basement.up(2),basement.east().up(),basement.east().up(2))){w.setBlockState(pos,Blocks.STONE_BRICKS.getDefaultState());roof.add(new VillageCivilWorks.Change(pos,Blocks.AIR.getDefaultState(),Blocks.STONE_BRICKS.getDefaultState()));}
        for(var d:Direction.Type.HORIZONTAL)if(d!=Direction.EAST)w.setBlockState(basement.offset(d).up(),Blocks.DIAMOND_BLOCK.getDefaultState());
        var stairExit=new VillageProjects.Project("basement-exit-test",team.village,"cathedral",basement,basement.add(3,3,3),roof,0,false,0);
        require(VillageProjects.openConstructionExit(w,stairExit,stairWorker),"basement construction opens headroom for a real upward step");
        require(w.getBlockState(basement.up(2)).isAir()&&w.getBlockState(basement.east().up()).isAir()&&w.getBlockState(basement.east().up(2)).isAir(),"stair escape clears jump and body space");
        require(stairExit.plan.size()==6,"temporary stair headroom is scheduled for restoration");
        require(VillageProjects.beginEscape(w,stairWorker,basement.up()),"cleared basement step permits an upward escape rather than treating the step as a wall");
        stairWorker.discard();
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);memory.houses.add(house);
        for(var r:oldRecords)w.setBlockState(r.pos(),r.state(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        w.setBlockState(target,head,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);w.setBlockState(target.south(),foot,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);w.setBlockState(target.east(2),Blocks.BARREL.getDefaultState());
        var from=(net.minecraft.inventory.Inventory)w.getBlockEntity(source.east(2));var to=(net.minecraft.inventory.Inventory)w.getBlockEntity(target.east(2));
        from.setStack(0,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND,9));
        resident.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.HOME,GlobalPos.create(w.getRegistryKey(),source));
        var state=VillageProjects.get(w);String id=VillageHousingRebuild.id(team.village,house);
        var plan=List.of(new VillageCivilWorks.Change(target,Blocks.AIR.getDefaultState(),head),new VillageCivilWorks.Change(target.east(2),Blocks.AIR.getDefaultState(),Blocks.BARREL.getDefaultState()));
        var complete=new VillageProjects.Project(id,team.village,"house",target,target.add(4,3,4),plan,plan.size(),true,0);state.jobs.add(complete);
        var settlement=VillageEraData.get(w).settlement(team);
        require(!VillageHousingRebuild.advance(w,team,settlement),"old home is not complete before demolition");
        require(from.isEmpty()&&to.count(net.minecraft.item.Items.DIAMOND)==9,"all real home contents survive replacement");
        require(resident.getBrain().getOptionalRegisteredMemory(net.minecraft.entity.ai.brain.MemoryModuleType.HOME).orElseThrow().pos().equals(target),"resident home points to the new bed");
        var demolition=state.jobs.stream().filter(p->p.id.equals(id+":clear")).findFirst().orElseThrow();
        for(var c:demolition.plan)w.setBlockState(c.pos(),c.after(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);demolition.index=demolition.plan.size();
        invoke(VillageProjects.class,"finish",new Class<?>[]{ServerWorld.class,VillageProjects.class,VillageMemory.class,VillageProjects.Project.class},w,state,memory,demolition);
        VillageHousingRebuild.advance(w,team,settlement);require(!memory.houses.contains(house),"demolished source leaves repair catalogue after reload-safe phase completion");
        var full=new net.minecraft.inventory.SimpleInventory(1);full.setStack(0,new net.minecraft.item.ItemStack(net.minecraft.item.Items.STONE,64));var cargo=new net.minecraft.inventory.SimpleInventory(1);cargo.setStack(0,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND,9));
        require(VillageHousingRebuild.transfer(cargo,full)==0&&cargo.count(net.minecraft.item.Items.DIAMOND)==9,"full new storage never destroys old cargo");
        require(!VillageCathedral.safe(w,target.east(2),Map.of(),List.of()),"cathedral preserves inventory blocks");
        require(!VillageCathedral.safe(w,new BlockPos(10,100,10),Map.of(),List.of(new Box(new BlockPos(10,100,10)))),"cathedral preserves protected room volume");
        BlockPos castleBase=new BlockPos(20,120,20);var castleRecord=new VillageMemory.House(castleBase,castleBase.add(64,20,87),List.of(new VillageMemory.BlockRecord(castleBase,Blocks.STONE_BRICKS.getDefaultState()),new VillageMemory.BlockRecord(castleBase.add(32,10,25),Blocks.STONE_BRICKS.getDefaultState())),0,List.of(),0,true,List.of(),List.of(),List.of(),"structure","regression:/royal_castle:fixture",List.of());
        memory.houses.add(castleRecord);
        w.setBlockState(castleBase.add(32,10,25),Blocks.STONE_BRICKS.getDefaultState());
        require(!VillageCathedral.advance(w,team,settlement),"cathedral queues work instead of reporting instant completion");
        var placed=new VillageCivilWorks.Change(castleBase,Blocks.AIR.getDefaultState(),Blocks.STONE.getDefaultState());var far=new VillageCivilWorks.Change(castleBase.add(30,4,30),Blocks.AIR.getDefaultState(),Blocks.STONE.getDefaultState());var near=new VillageCivilWorks.Change(castleBase.add(1,4,1),Blocks.AIR.getDefaultState(),Blocks.STONE.getDefaultState());var resumed=new VillageProjects.Project("resume-cathedral",team.village,"cathedral",castleBase,castleBase.add(40,10,40),List.of(placed,far,near),1,false,0);require(resumed.index==1&&resumed.plan.getFirst().equals(placed)&&new HashSet<>(resumed.plan.subList(1,3)).equals(Set.of(far,near)),"cathedral reload reorders only unfinished work and preserves every change and completed prefix");
        var cathedralJob=state.jobs.stream().filter(p->p.id.equals(VillageCathedral.id(team.village))).findFirst().orElseThrow();
        require(cathedralJob.plan.stream().anyMatch(c->c.pos().equals(castleBase.add(32,10,25))&&c.after().isAir()),"old recognised castle interior wall is explicitly removed for the cathedral nave");
        require(cathedralJob.plan.size()>50000&&cathedralJob.max.getY()>=castleBase.getY()+95,"complete cathedral spires enter persistent construction plan");
        require(cathedralJob.plan.stream().noneMatch(c->c.pos().getY()<castleBase.getY()),"cathedral never queues underground demolition");
        state.retire(cathedralJob);memory.houses.remove(castleRecord);
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: full residential replacement, inventory conservation, HOME remap, profession exclusion and cathedral room protection");
    }
    private static void checkSteamHouseAccess(ServerWorld w){
        for(String name:List.of("steam_townhouse","steam_villa")){
            var layout=VillageEraPlans.template(w,name);
            int center=layout.size().getX()/2;
            var base=new BlockPos(name.equals("steam_villa")?144:112,205,144);
            for(int x=base.getX()>>4;x<=(base.getX()+layout.size().getX())>>4;x++)
                w.getChunk(x,base.getZ()>>4);
            for(var r:layout.blocks())w.setBlockState(base.add(r.pos()),r.state(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
            var door=base.add(center,1,2);
            require(w.getBlockState(door.up()).getBlock() instanceof DoorBlock,"Full-height door survives facade pass");
            require(w.isAir(door.south())&&w.isAir(door.south().up()),"Both entrance blocks are clear");
            var walker=new VillagerEntity(EntityType.VILLAGER,w);
            VillageIndustrial.panelNavigation(walker);
            var bottom=base.add(center,1,4);var upper=base.add(center,5,10);
            for(var from:List.of(bottom,upper)){
                walker.setPosition(Vec3d.ofBottomCenter(from));walker.setOnGround(true);
                var path=walker.getNavigation().findPathTo(from.equals(bottom)?upper:bottom,0);
                require(path!=null&&path.reachesTarget(),"Two-way stair path in "+name+" from "+from);
            }
            var lamp=door.south().up();
            w.setBlockState(lamp,Blocks.STONE_BRICK_WALL.getDefaultState());
            w.setBlockState(lamp.up(),Blocks.LANTERN.getDefaultState());
            var cap=base.add(center,4,5);
            w.setBlockState(cap,Blocks.SPRUCE_PLANKS.getDefaultState());
            if(name.equals("steam_townhouse"))w.setBlockState(door.up(),Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState(),Block.FORCE_STATE|Block.NOTIFY_LISTENERS);
            var repairs=SteamHouseAccess.plan(w,base,center,layout);
            require(repairs.get(lamp).isAir()&&repairs.get(lamp.up()).isAir()&&repairs.get(cap).isAir(),"Existing entrance and stairwell receive repair");
            if(name.equals("steam_townhouse"))require(repairs.get(door.up()).getBlock() instanceof DoorBlock,"Existing glass over the door receives an upper door half");
            require(w.getBlockState(door.east(2).south()).isOf(Blocks.STONE_BRICK_WALL)
                    &&w.getBlockState(door.east(2).south().up()).isOf(Blocks.LANTERN),"Lamp is mounted off the doorway on the floor");
            long village=base.asLong();
            VillageProjects.get(w).add(w,"access_fixture:"+name,village,"factory_supplies",repairs);
            require(VillageProjects.get(w).exists("access_fixture:"+name),"Access repair queued on loaded chunks");
            for(int pass=0;pass<32;pass++)VillageProjects.materialize(w,village,64);
            require(w.isAir(cap)&&w.isAir(lamp)&&w.getBlockState(door.up()).getBlock() instanceof DoorBlock,
                    "Completed repair clears the stairwell and restores the two-block doorway: "+w.getBlockState(cap)+", "+w.getBlockState(lamp)+", "+w.getBlockState(door)+", "+w.getBlockState(door.up()));
        }
        var fenceBase=new BlockPos(80,206,80);
        var town=new VillageMemory.Team(521805L,fenceBase,0);
        var oldWall=fenceBase.east(32);var newWall=fenceBase.east(48);
        var jobs=VillageProjects.get(w);
        jobs.add(w,"perimeter_v1:"+town.village+":32:1:0",town.village,"perimeter",Map.of(oldWall,Blocks.STONE_BRICKS.getDefaultState()));
        jobs.add(w,"perimeter_v1:"+town.village+":48:1:0",town.village,"perimeter",Map.of(newWall,Blocks.STONE_BRICKS.getDefaultState()));
        for(int pass=0;pass<16;pass++)VillageProjects.materialize(w,town.village,64);
        require(w.getBlockState(oldWall).isOf(Blocks.STONE_BRICKS)&&w.getBlockState(newWall).isOf(Blocks.STONE_BRICKS),"Both old and new rings exist before cleanup");
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        VillagePerimeter.dismantleInner(w,jobs,memory,town,48);
        for(int pass=0;pass<16;pass++)VillageProjects.materialize(w,town.village,64);
        require(w.isAir(oldWall)&&w.getBlockState(newWall).isOf(Blocks.STONE_BRICKS),"Inner wall is dismantled and the outer ring survives");
        var crossing=VillageEraPlans.plantationCrossing();
        require(crossing.get(net.minecraft.block.SlabBlock.TYPE)==net.minecraft.block.enums.SlabType.TOP
                &&crossing.getFluidState().isOf(net.minecraft.fluid.Fluids.WATER),
                "Plantation crossing fits in the canal without removing water");
        var at=new BlockPos(118,206,166);
        for(var pos:BlockPos.iterate(at.add(-3,-1,-3),at.add(3,-1,3)))w.setBlockState(pos,Blocks.STONE.getDefaultState());
        var mob=new net.minecraft.entity.passive.PigEntity(EntityType.PIG,w);
        mob.setPosition(Vec3d.ofBottomCenter(at));
        w.spawnEntity(mob);
        require(VillageProjects.relocateBlockedMob(w,mob,at)&&VillageProjects.outsidePlacement(mob,at,mob.getEntityPos()),
                "A stuck mob moves onto safe ground and clears wall placement");
        mob.discard();
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS steam house doors, both stair directions, legacy repairs and mob clearance");
    }

    private static void check(ServerWorld w)throws Exception{
        checkSteamHouseAccess(w);
        for(String name:List.of("steam_townhouse","steam_villa","steam_apartments")){
            var template=VillageEraPlans.template(w,name);require(template!=null,"steam template parses: "+name);
            require(template.blocks().stream().anyMatch(r->r.state().getBlock() instanceof net.minecraft.block.BedBlock),"steam template has real beds: "+name);
            require(template.blocks().stream().allMatch(r->r.pos().getX()>=0&&r.pos().getX()<template.size().getX()&&r.pos().getY()>=0&&r.pos().getY()<template.size().getY()&&r.pos().getZ()>=0&&r.pos().getZ()<template.size().getZ()),"steam template geometry stays inside bounds: "+name);
        }
        BlockPos base=new BlockPos(0,100,0);VillageProjects.keepLoaded(w,base);
        for(int x=-8;x<32;x++)for(int z=-8;z<16;z++){w.setBlockState(base.add(x,-1,z),Blocks.STONE.getDefaultState());for(int y=0;y<5;y++)w.setBlockState(base.add(x,y,z),Blocks.AIR.getDefaultState());}
        BlockPos slope=new BlockPos(24,100,24);
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++){
            int height=Math.max(0,x);
            for(int y=-1;y<=9;y++)w.setBlockState(slope.add(x,y,z),y<=height?Blocks.DIRT.getDefaultState():Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        }
        require(VillageGroundSafety.column(w,slope.getX(),slope.getZ()).isEmpty(),"one-sided natural slope never becomes a dirt pillar project");
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=0;y<=8;y++)w.setBlockState(slope.add(x,y,z),Math.max(Math.abs(x),Math.abs(z))>=2&&y<=4?Blocks.DIRT.getDefaultState():Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        var pit=VillageGroundSafety.column(w,slope.getX(),slope.getZ());
        require(!pit.isEmpty()&&pit.keySet().stream().allMatch(p->p.getY()<=slope.getY()+4),"enclosed pit backfill stops at the lowest rim");
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: natural slope rejected; enclosed pit filled only to rim");
        BlockPos window=base.add(10,2,0);
        w.setBlockState(window,Blocks.GLASS_PANE.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        w.setBlockState(window.east(),Blocks.GLASS_PANE.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        w.setBlockState(window.west(),Blocks.STONE_BRICKS.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        var pane=VillageConnections.connected(w,window,w.getBlockState(window));
        require(pane.get(PaneBlock.EAST)&&pane.get(PaneBlock.WEST)&&!pane.get(PaneBlock.NORTH),"old isolated panes reconnect to glass and solid frame");
        w.setBlockState(window,pane,Block.NOTIFY_ALL);
        require(w.getBlockState(window.east()).get(PaneBlock.WEST),"adjacent pane receives neighbour update");
        w.setBlockState(window.east(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        require(!w.getBlockState(window).get(PaneBlock.EAST),"pane disconnects after adjacent glass is removed");
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: glass pane connections repair and track neighbour changes");
        var shooter=new net.minecraft.server.network.ServerPlayerEntity(w.getServer(),w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RevolverTest"),net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());
        shooter.networkHandler=new net.minecraft.server.network.ServerPlayNetworkHandler(w.getServer(),new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),shooter,net.minecraft.server.network.ConnectedClientData.createDefault(shooter.getGameProfile(),false));
        var capture=new BlockPos(-20,110,-20);var center=Vec3d.ofBottomCenter(capture);
        for(int x:new int[]{-10,0,10})for(int y:new int[]{-10,0,10})for(int z:new int[]{-10,0,10})require(PlayerJustice.withinCaptureRange(center.add(x,y,z),capture),"capture includes every face, edge and corner of ten-block cube");
        require(!PlayerJustice.withinCaptureRange(center.add(10.01,0,0),capture)&&!PlayerJustice.withinCaptureRange(center.add(0,-10.01,0),capture),"capture excludes positions beyond ten blocks");
        var captureGuard=villager(w,capture.east(3));
        w.setBlockState(capture,Blocks.AIR.getDefaultState());w.setBlockState(capture.up(),Blocks.AIR.getDefaultState());
        for(int x:new int[]{-10,0,10})for(int y:new int[]{-10,0,10})for(int z:new int[]{-10,0,10}){
            shooter.setPosition(center.add(x,y,z));PlayerJustice.makeLead(w,shooter,captureGuard);shooter.addCommandTag("halloween_player_entering_cell");shooter.setVelocity(.3,.2,-.3);
            require(PlayerJustice.placeCaptive(w,shooter,captureGuard,capture),"real player capture from cube boundary");
            require(shooter.getEntityPos().squaredDistanceTo(center)<1e-8&&shooter.getVelocity().lengthSquared()==0&&!PlayerJustice.leadAttached(w,shooter,captureGuard)&&!shooter.getCommandTags().contains("halloween_player_entering_cell"),"capture stops pulling and clears motion before sealing");
        }
        captureGuard.discard();org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: capture from 27 points throughout ten-block cube, leash removed and velocity cleared");
        shooter.setPosition(-20,110,-20);var gun=new net.minecraft.item.ItemStack(VillageItems.REVOLVER);shooter.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,gun);
        shooter.getInventory().setStack(1,new net.minecraft.item.ItemStack(VillageItems.MUSKET_CARTRIDGE,7));
        for(int round=0;round<6;round++){
            require(((MusketItem)VillageItems.REVOLVER).fire(w,shooter,net.minecraft.util.Hand.MAIN_HAND)==net.minecraft.util.ActionResult.SUCCESS,"revolver fires loaded round "+round);
            for(int tick=0;tick<10;tick++)shooter.getItemCooldownManager().update();
        }
        require(shooter.getInventory().count(VillageItems.MUSKET_CARTRIDGE)==1,"six revolver shots consume exactly six cartridges");
        require(((MusketItem)VillageItems.REVOLVER).fire(w,shooter,net.minecraft.util.Hand.MAIN_HAND)==net.minecraft.util.ActionResult.FAIL,"sixth shot starts long reload rather than firing seventh immediately");
        for(int tick=0;tick<50;tick++)shooter.getItemCooldownManager().update();
        require(((MusketItem)VillageItems.REVOLVER).fire(w,shooter,net.minecraft.util.Hand.MAIN_HAND)==net.minecraft.util.ActionResult.SUCCESS&&shooter.getInventory().count(VillageItems.MUSKET_CARTRIDGE)==0,"revolver resumes after reload and consumes final cartridge");
        for(int tick=0;tick<70;tick++)shooter.getItemCooldownManager().update();
        shooter.getInventory().setStack(1,new net.minecraft.item.ItemStack(VillageItems.MUSKET_CARTRIDGE));
        shooter.setSneaking(true);for(int tick=0;tick<99;tick++)VillageSelfShot.tickPlayer(shooter);shooter.setSneaking(false);
        require(!VillageSelfShot.tryUse(shooter)&&shooter.isAlive(),"unfinished crouch gesture cannot trigger self shot");
        shooter.setSneaking(true);VillageSelfShot.tickPlayer(shooter);shooter.setSneaking(false);
        boolean triggered=VillageSelfShot.tryUse(shooter);require(triggered&&!shooter.isAlive()&&shooter.getInventory().count(VillageItems.MUSKET_CARTRIDGE)==0,"completed explicit gesture: triggered="+triggered+" health="+shooter.getHealth()+" ammo="+shooter.getInventory().count(VillageItems.MUSKET_CARTRIDGE)+" sneak="+shooter.isSneaking()+" creative="+shooter.isCreative());
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: revolver six-shot cycle, reload and exact cartridge consumption");
        BlockPos joined=base.add(20,0,-4);w.setBlockState(joined.east(),Blocks.STONE.getDefaultState());w.setBlockState(joined,Blocks.OAK_FENCE.getDefaultState());var connectedFence=VillageConnections.connected(w,joined,Blocks.OAK_FENCE.getDefaultState());w.setBlockState(joined,connectedFence);
        require(!connectedFence.equals(Blocks.OAK_FENCE.getDefaultState())&&VillageConnections.matches(w,joined,Blocks.OAK_FENCE.getDefaultState(),w.getBlockState(joined)),"neighbour-connected fence counts as an already completed planned block");
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var team=new VillageMemory.Team(17,base,0);memory.teams.add(team);
        var worker=villager(w,base.add(2,0,2));worker.setVillagerData(worker.getVillagerData().withProfession(BuilderProfession.ENTRY));
        BlockPos waterFixture=base.add(-6,0,-6);
        w.setBlockState(waterFixture,Blocks.WATER.getDefaultState());worker.setPosition(waterFixture.getX()+.5,waterFixture.getY(),waterFixture.getZ()+.5);
        VillageWaterRescue.rescueAt(w,worker,worker);
        require(w.getBlockState(waterFixture).isOf(Blocks.WATER),"rescue preserves water instead of draining it");
        require(VillageEraPlans.levelSite(w,waterFixture.getX(),waterFixture.getZ(),1,1,4)==Integer.MIN_VALUE,"building plots reject water");
        require(VillageFactory.drySite(w,waterFixture.getX(),waterFixture.getZ(),1,1)==Integer.MIN_VALUE,"factory never accepts an open-water plot");
        var woodland=base.add(30,0,8);for(int y=0;y<12;y++)w.setBlockState(woodland.up(y),Blocks.OAK_LOG.getDefaultState());require(VillageFactory.drySite(w,woodland.getX(),woodland.getZ(),1,1)==woodland.getY(),"factory survey uses ground below a tree instead of treating its trunk as a mountain");
        worker.setPosition(base.getX()+2.5,base.getY(),base.getZ()+2.5);
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: water preserved and flooded building site rejected");
        require(!VillageEconomy.station(w,worker,"armorer"),"no phantom job without a built workstation");
        var site=base.add(3,0,3);w.setBlockState(site,Blocks.BLAST_FURNACE.getDefaultState());
        memory.houses.add(new VillageMemory.House(site,site,List.of(new VillageMemory.BlockRecord(site,Blocks.BLAST_FURNACE.getDefaultState())),0,List.of(),0,true,List.of()));
        VillageWorkplaceIndex.invalidate(w);require(VillageEconomy.station(w,worker,"armorer"),"completed workstation available: "+w.getBlockState(site)+" loaded="+w.isChunkLoaded(new ChunkPos(site).toLong())+" sites="+VillageWorkplaceIndex.get(w).sites);
        var snowSpawn=base.add(24,0,8);w.setBlockState(snowSpawn,Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS,3));var feet=VillageBuilders.workerSpawnPosition(w,snowSpawn.up());require(feet!=null&&feet.y>=snowSpawn.getY()&&feet.y<snowSpawn.getY()+1,"crew can stand on a partial snow layer without draining or removing terrain");require(w.getBlockState(snowSpawn).isOf(Blocks.SNOW),"snow is preserved when replenishing a building crew");
        var towerHouse=new VillageMemory.House(base.add(20,0,10),base.add(20,3,10),List.of(),0,List.of(),0,true,List.of());for(int y=0;y<4;y++){var column=base.add(20,y,10);w.setBlockState(column,Blocks.SCAFFOLDING.getDefaultState());towerHouse.repairScaffolds.add(column);}var onTop=villager(w,base.add(20,4,10));
        memory.houses.add(towerHouse);BlockPos scaffoldBase=base.add(20,0,10);
        require(!VillageBuilders.canReplaceScaffold(w,scaffoldBase,Blocks.STONE_BRICKS.getDefaultState()),"occupied scaffold remains a continuous safe descent route before masonry replacement");
        require(!VillageBuilders.canReplaceScaffold(w,scaffoldBase,Blocks.AIR.getDefaultState()),"occupied scaffold cannot be replaced with air");
        var foreignScaffold=base.add(28,0,10);w.setBlockState(foreignScaffold,Blocks.SCAFFOLDING.getDefaultState());require(!VillageBuilders.canReplaceScaffold(w,foreignScaffold,Blocks.STONE.getDefaultState()),"construction preserves unrecorded player scaffolds");
        invoke(VillageBuilders.class,"removeRepairScaffolds",new Class<?>[]{ServerWorld.class,VillageMemory.class,VillageMemory.House.class},w,memory,towerHouse);
        require(w.getBlockState(base.add(20,0,10)).isOf(Blocks.SCAFFOLDING),"scaffold base is retained while a worker stands on its top");onTop.setPosition(base.getX()+22.5,base.getY(),base.getZ()+10.5);
        invoke(VillageBuilders.class,"removeRepairScaffolds",new Class<?>[]{ServerWorld.class,VillageMemory.class,VillageMemory.House.class},w,memory,towerHouse);
        require(towerHouse.repairScaffolds.isEmpty(),"unoccupied scaffold column can be fully removed after descent");onTop.discard();memory.houses.remove(towerHouse);
        var other=villager(w,base);require(!VillageEconomy.station(w,other,"armorer"),"one workstation cannot employ two villagers");
        var steamSettlement=VillageEraData.get(w).settlement(team);
        require(!VillageSteam.start(w,team),"wood village cannot skip straight to steam");
        steamSettlement.era=2;steamSettlement.xp.put("architecture_v2",2);
        require(VillageSteam.start(w,team)&&steamSettlement.era==2,"steam transition queues preparation without premature era announcement");
        steamSettlement.era=1;steamSettlement.xp.remove(VillageSteam.PHASE);steamSettlement.xp.remove(VillageSteam.START);
        worker.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,new net.minecraft.item.ItemStack(VillageItems.MUSKET));
        require(!MusketItem.fireGuard(w,worker,other,1),"empty musket cannot shoot");
        worker.getInventory().addStack(new net.minecraft.item.ItemStack(VillageItems.MUSKET_CARTRIDGE));
        require(MusketItem.fireGuard(w,worker,other,1)&&worker.getInventory().count(VillageItems.MUSKET_CARTRIDGE)==0,"guard shot consumes a real cartridge");
        worker.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,net.minecraft.item.ItemStack.EMPTY);
        steamSettlement.xp.put("factory_x",base.getX());steamSettlement.xp.put("factory_y",base.getY());steamSettlement.xp.put("factory_z",base.getZ());
        VillageEras.putTag(worker,"halloween_factory_batch_",1);
        worker.getInventory().addStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_NUGGET));
        worker.getInventory().addStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.GUNPOWDER));
        worker.getInventory().addStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.PAPER));
        worker.setPosition(base.getX()+2.5,base.getY()+1,base.getZ()+3.5);
        var manufacture=VillageEconomy.class.getDeclaredMethod("manufacture",ServerWorld.class,VillageEraData.Settlement.class,VillagerEntity.class);manufacture.setAccessible(true);
        require((boolean)manufacture.invoke(null,w,steamSettlement,worker),"factory completes a supplied cartridge batch");
        require(worker.getInventory().count(VillageItems.MUSKET_CARTRIDGE)==4&&worker.getInventory().count(net.minecraft.item.Items.GUNPOWDER)==0&&worker.getInventory().count(net.minecraft.item.Items.PAPER)==0,"factory consumes carried ingredients and creates exactly four cartridges");
        worker.getInventory().clear();worker.setPosition(base.getX()+2.5,base.getY(),base.getZ()+2.5);
        steamSettlement.xp.remove("factory_x");steamSettlement.xp.remove("factory_y");steamSettlement.xp.remove("factory_z");
        for(long serial:new long[]{0,2,4,6}){
            var order=VillageEconomy.factoryRecipe("weaponsmith",serial);
            VillageEras.putTag(worker,"halloween_factory_batch_",serial);
            for(var material:order.ingredients().entrySet())worker.getInventory().addStack(new net.minecraft.item.ItemStack(material.getKey(),material.getValue()));
            // Reinstall the worksite origin used by this isolated fixture.
            steamSettlement.xp.put("factory_x",base.getX());steamSettlement.xp.put("factory_y",base.getY());steamSettlement.xp.put("factory_z",base.getZ());
            worker.setPosition(base.getX()+2.5,base.getY()+1,base.getZ()+3.5);
            require((boolean)manufacture.invoke(null,w,steamSettlement,worker),"workshop makes weapon batch "+serial);
            require(worker.getInventory().count(order.product())==1,"weapon output exactly one");worker.getInventory().clear();
        }
        worker.setVillagerData(worker.getVillagerData().withProfession(w.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.VILLAGER_PROFESSION).getOrThrow(net.minecraft.village.VillagerProfession.ARMORER)));
        for(long serial=0;serial<4;serial++){
            var order=VillageEconomy.factoryRecipe("armorer",serial);VillageEras.putTag(worker,"halloween_factory_batch_",serial);
            worker.getInventory().addStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND,order.ingredients().get(net.minecraft.item.Items.DIAMOND)));
            require((boolean)manufacture.invoke(null,w,steamSettlement,worker),"armorer makes diamond piece "+serial);
            require(worker.getInventory().count(order.product())==1&&worker.getInventory().count(net.minecraft.item.Items.DIAMOND)==0,"diamond armour consumes exact real ingredients");worker.getInventory().clear();
        }
        worker.setVillagerData(worker.getVillagerData().withProfession(BuilderProfession.ENTRY));worker.setPosition(base.getX()+2.5,base.getY(),base.getZ()+2.5);
        steamSettlement.xp.remove("factory_x");steamSettlement.xp.remove("factory_y");steamSettlement.xp.remove("factory_z");
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: factory batch consumes real carried materials");
        var supplies=new net.minecraft.inventory.SimpleInventory(12);
        for(var stack:List.of(new net.minecraft.item.ItemStack(net.minecraft.item.Items.SPRUCE_LOG),new net.minecraft.item.ItemStack(net.minecraft.item.Items.SUGAR_CANE,3),new net.minecraft.item.ItemStack(net.minecraft.item.Items.COAL,2),new net.minecraft.item.ItemStack(net.minecraft.item.Items.REDSTONE),new net.minecraft.item.ItemStack(net.minecraft.item.Items.FLINT)))supplies.addStack(stack);
        for(int i=0;i<12;i++)VillageEconomy.processFactorySupplies(supplies);
        require(supplies.count(net.minecraft.item.Items.STICK)==8&&supplies.count(net.minecraft.item.Items.SPRUCE_LOG)==0,"factory turns actual spruce log into eight sticks without generating extra wood");
        require(supplies.count(net.minecraft.item.Items.PAPER)==3&&supplies.count(net.minecraft.item.Items.SUGAR_CANE)==0,"factory paper consumes three real cane");
        require(supplies.count(net.minecraft.item.Items.GUNPOWDER)==4&&supplies.count(net.minecraft.item.Items.COAL)==0&&supplies.count(net.minecraft.item.Items.REDSTONE)==0&&supplies.count(net.minecraft.item.Items.FLINT)==0,"industrial powder consumes its registered game recipe ingredients");
        var plannedGarden=new LinkedHashMap<BlockPos,net.minecraft.block.BlockState>();var gardenBase=base.add(40,0,40);for(int x=2;x<=14;x+=2){var p=gardenBase.add(x,0,11);w.setBlockState(p,Blocks.GRASS_BLOCK.getDefaultState());plannedGarden.put(p,Blocks.STONE_BRICKS.getDefaultState());plannedGarden.put(p.north(),Blocks.STONE_BRICKS.getDefaultState());}VillageFactory.supplyGarden(w,gardenBase,plannedGarden);require(plannedGarden.values().stream().filter(state->state.isOf(Blocks.SUGAR_CANE)).count()==7,"new factory garden is planned against the future floor, not rejected by existing grass");
        var caneBase=base.add(0,0,20);var caneRoot=caneBase.add(2,1,11);w.setBlockState(caneRoot.down(),Blocks.DIRT.getDefaultState());w.setBlockState(caneRoot.down().north(),Blocks.WATER.getDefaultState());for(int y=0;y<3;y++)w.setBlockState(caneRoot.up(y),Blocks.SUGAR_CANE.getDefaultState());worker.setPosition(caneRoot.getX()+1.5,caneRoot.getY(),caneRoot.getZ()+.5);var caneStock=new net.minecraft.inventory.SimpleInventory(8);require(VillageEconomy.harvestFactoryCane(w,caneBase,worker,caneStock),"factory worker harvests actual upper cane block");require(caneStock.count(net.minecraft.item.Items.SUGAR_CANE)==1&&w.getBlockState(caneRoot).isOf(Blocks.SUGAR_CANE)&&w.getBlockState(caneRoot.up()).isOf(Blocks.SUGAR_CANE)&&w.getBlockState(caneRoot.up(2)).isAir(),"paper source preserves growing roots and transfers exactly one real cane");worker.setPosition(base.getX()+2.5,base.getY(),base.getZ()+2.5);
        var carried=new net.minecraft.inventory.SimpleInventory(8);carried.addStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_INGOT,3));carried.addStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.STICK,2));carried.addStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.COAL));
        require(VillageEconomy.selectFactoryBatch("weaponsmith",1,carried,null)==16,"missing cartridge inputs do not block a fully supplied musket order");
        require(VillageEconomy.selectFactoryBatch("armorer",0,new net.minecraft.inventory.SimpleInventory(8),null)==-1,"empty inventory cannot fabricate armour");
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: factory wood/paper/powder consume actual inputs; missing orders skipped without free products");
        var haul=new VillageEraData.Settlement(9876543L,"Porter test");
        VillageEraData.get(w).settlements.put(haul.village,haul);
        haul.vault=new VillageEraData.Vault(new BlockPos(100,120,100),0,"",-1,true);
        haul.xp.put("factory_x",100);haul.xp.put("factory_y",120);haul.xp.put("factory_z",110);
        BlockPos stockPos=VillageEconomy.barrel(haul,3),inPos=new BlockPos(103,121,117),outPos=new BlockPos(111,121,117);
        for(var p:List.of(stockPos,inPos,outPos)){w.getChunk(p.getX()>>4,p.getZ()>>4);w.setBlockState(p,Blocks.BARREL.getDefaultState());}
        var stock=(net.minecraft.inventory.Inventory)w.getBlockEntity(stockPos);var input=(net.minecraft.inventory.Inventory)w.getBlockEntity(inPos);var output=(net.minecraft.inventory.Inventory)w.getBlockEntity(outPos);stock.clear();input.clear();output.clear();
        for(int slot=0;slot<6;slot++){var pos=VillageEconomy.barrel(haul,slot);w.getChunk(pos.getX()>>4,pos.getZ()>>4);w.setBlockState(pos,Blocks.BARREL.getDefaultState());((net.minecraft.inventory.Inventory)w.getBlockEntity(pos)).clear();}
        stock.setStack(0,new net.minecraft.item.ItemStack(net.minecraft.item.Items.RAW_IRON,32));
        worker.setPosition(stockPos.getX()+.5,stockPos.getY(),stockPos.getZ()+.5);VillageEconomy.haulFactory(w,haul,worker);
        require(worker.getInventory().count(net.minecraft.item.Items.RAW_IRON)==16&&stock.count(net.minecraft.item.Items.RAW_IRON)==16,"porter withdraws exactly sixteen real raw iron");
        worker.setPosition(inPos.getX()+.5,inPos.getY(),inPos.getZ()+.5);VillageEconomy.haulFactory(w,haul,worker);
        require(input.count(net.minecraft.item.Items.RAW_IRON)==16&&worker.getInventory().isEmpty(),"porter deposits carried iron into the factory input barrel");
        output.setStack(0,new net.minecraft.item.ItemStack(VillageItems.MUSKET_CARTRIDGE,4));
        worker.setPosition(outPos.getX()+.5,outPos.getY(),outPos.getZ()+.5);VillageEconomy.haulFactory(w,haul,worker);
        var arms=VillageEconomy.barrel(haul,0);worker.setPosition(arms.getX()+.5,arms.getY(),arms.getZ()+.5);VillageEconomy.haulFactory(w,haul,worker);
        require(((net.minecraft.inventory.Inventory)w.getBlockEntity(arms)).count(VillageItems.MUSKET_CARTRIDGE)==4&&output.isEmpty()&&worker.getInventory().isEmpty(),"porter returns exact factory output to treasury without duplication");
        worker.setPosition(base.getX()+2.5,base.getY(),base.getZ()+2.5);
        haul.era=3;var arsenal=(net.minecraft.inventory.Inventory)w.getBlockEntity(arms);arsenal.clear();
        arsenal.setStack(0,new net.minecraft.item.ItemStack(VillageItems.MUSKET));arsenal.setStack(1,new net.minecraft.item.ItemStack(VillageItems.SABRE));arsenal.setStack(2,new net.minecraft.item.ItemStack(VillageItems.MUSKET_CARTRIDGE,16));
        var armourPos=VillageEconomy.barrel(haul,1);var armour=(net.minecraft.inventory.Inventory)w.getBlockEntity(armourPos);
        var pieces=List.of(net.minecraft.item.Items.DIAMOND_HELMET,net.minecraft.item.Items.DIAMOND_CHESTPLATE,net.minecraft.item.Items.DIAMOND_LEGGINGS,net.minecraft.item.Items.DIAMOND_BOOTS);
        for(int i=0;i<4;i++)armour.setStack(i,new net.minecraft.item.ItemStack(pieces.get(i)));
        worker.addCommandTag("halloween_guard");
        invoke(VillageEconomy.class,"issue",new Class<?>[]{ServerWorld.class,VillageEraData.Settlement.class,List.class,long.class},w,haul,List.of(worker),123L);
        require(worker.getMainHandStack().isOf(VillageItems.MUSKET)&&arsenal.count(VillageItems.MUSKET)==0,"guard receives an actual treasury musket");
        require(worker.getInventory().count(VillageItems.SABRE)==1&&worker.getInventory().count(VillageItems.MUSKET_CARTRIDGE)==8,"guard receives a real sidearm and cartridges");
        var slots=List.of(net.minecraft.entity.EquipmentSlot.HEAD,net.minecraft.entity.EquipmentSlot.CHEST,net.minecraft.entity.EquipmentSlot.LEGS,net.minecraft.entity.EquipmentSlot.FEET);
        for(int i=0;i<4;i++){require(worker.getEquippedStack(slots.get(i)).isOf(pieces.get(i))&&armour.count(pieces.get(i))==0,"diamond armour issued from real stock");worker.equipStack(slots.get(i),net.minecraft.item.ItemStack.EMPTY);}
        worker.removeCommandTag("halloween_guard");worker.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,net.minecraft.item.ItemStack.EMPTY);worker.getInventory().clear();
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: guard musket, sidearm, ammunition and full diamond armour drawn from treasury");
        VillageEraData.get(w).settlements.remove(haul.village);
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: porter input and output transfers conserve all items");


        require(VillageSteam.modern(Blocks.BRICKS.getDefaultState()).isOf(Blocks.STONE_BRICKS)&&VillageSteam.modern(Blocks.OAK_STAIRS.getDefaultState()).isOf(Blocks.OAK_STAIRS),"gothic walls convert while functional roof shapes survive");
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: steam prerequisites, concrete conversion and real musket ammunition");
        VillageTasks.holdCourt(other);other.getNavigation().stop();require(!other.isPushable(),"seated crowd collision mixin active");
        var changedCrop=new VillageCivilWorks.Change(base,Blocks.POTATOES.getDefaultState(),Blocks.AIR.getDefaultState());
        require(VillageProjects.compatibleChange(changedCrop,Blocks.CARROTS.getDefaultState()),"farmer crop replacement cannot stall the recorded garden conversion");
        var frozen=new VillageCivilWorks.Change(base,Blocks.WATER.getDefaultState(),Blocks.POLISHED_ANDESITE.getDefaultState());
        require(VillageProjects.compatibleChange(frozen,Blocks.ICE.getDefaultState()),"surveyed water freezing does not stall a plaza");
        var connected=new VillageCivilWorks.Change(base,Blocks.SPRUCE_FENCE.getDefaultState(),Blocks.STONE_BRICK_WALL.getDefaultState());
        require(VillageProjects.compatibleChange(connected,Blocks.STONE_BRICK_WALL.getDefaultState().with(net.minecraft.block.WallBlock.UP,false)),"completed wall connection updates do not stall conversion");
        require(!VillageProjects.compatibleChange(frozen,Blocks.DIAMOND_BLOCK.getDefaultState()),"foreign solid blocks remain protected");
        checkGothicRebuild(w,team,worker);
        var roadObserver=new net.minecraft.server.network.ServerPlayerEntity(w.getServer(),w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RoadTest"),net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());w.getPlayers().add(roadObserver);
        BlockPos roadPos=base.add(20,-1,-4);w.setBlockState(roadPos,Blocks.DIRT_PATH.getDefaultState());
        var roadRecord=new VillageMemory.House(roadPos,roadPos,List.of(new VillageMemory.BlockRecord(roadPos,Blocks.DIRT_PATH.getDefaultState())),0,List.of(),0,true,List.of(),List.of(),List.of(),"infrastructure","road-test",List.of());memory.houses.add(roadRecord);
        int oldEra=steamSettlement.era;steamSettlement.era=3;
        var timeField=ServerWorld.class.getDeclaredField("worldProperties");timeField.setAccessible(true);var timeProps=(net.minecraft.world.level.ServerWorldProperties)timeField.get(w);long testTime=w.getTime();timeProps.setTime(200);
        try{VillageStoneRoads.tick(w);}finally{timeProps.setTime(testTime);steamSettlement.era=oldEra;w.getPlayers().remove(roadObserver);}
        var pavedJob=VillageProjects.get(w).jobs.stream().filter(p->p.kind.equals("stone_road")&&p.plan.stream().anyMatch(c->c.pos().equals(roadPos))).findFirst().orElseThrow();
        require(pavedJob.plan.stream().filter(c->c.pos().equals(roadPos)).allMatch(c->c.after().isOf(Blocks.STONE_BRICKS)||c.after().isOf(Blocks.ANDESITE)),"third-era recorded paths queue real stone road construction");
        VillageProjects.get(w).retire(pavedJob);memory.houses.remove(roadRecord);
        var obstructionState=VillageProjects.get(w);
        BlockPos blockedSite=base.add(24,0,3),openSite=base.add(25,0,3);
        w.setBlockState(blockedSite,Blocks.DIAMOND_BLOCK.getDefaultState());
        var blockedChange=new VillageCivilWorks.Change(blockedSite,Blocks.STONE.getDefaultState(),Blocks.BRICKS.getDefaultState());
        var openChange=new VillageCivilWorks.Change(openSite,Blocks.AIR.getDefaultState(),Blocks.BRICKS.getDefaultState());
        var partial=new VillageProjects.Project("obstruction_fixture",team.village,"house",blockedSite,openSite,List.of(blockedChange,openChange),0,false,0);
        require(VillageProjects.deferObstruction(w,obstructionState,partial),"independent work can bypass a protected obstruction");
        require(partial.plan.get(0).equals(openChange)&&partial.plan.get(1).equals(blockedChange)&&partial.index==0,"blocked work remains queued without false progress");
        require(w.getBlockState(blockedSite).isOf(Blocks.DIAMOND_BLOCK),"deferral preserves the player's block");
        var demolition=new VillageCivilWorks.Change(openSite,Blocks.STONE.getDefaultState(),Blocks.AIR.getDefaultState());
        var phases=new VillageProjects.Project("phase_fixture",team.village,"house",blockedSite,openSite,List.of(blockedChange,demolition),0,false,0);
        require(!VillageProjects.deferObstruction(w,obstructionState,phases),"construction and demolition phases remain ordered");
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: protected obstruction deferred without losing work");
        var guard=villager(w,base.add(100,0,0));guard.addCommandTag("halloween_guard");other.addCommandTag("halloween_wanted");
        require(!VillageDefense.assigned(w,guard,other),"ordinary wanted target outside pursuit range is ignored");
        guard.setPosition(base.getX()+10.5,base.getY(),base.getZ()+.5);
        require(VillageDefense.assigned(w,guard,other),"nearby visible wanted target receives a guard");
        var cell=base.add(0,0,8);other.setPosition(cell.getX()+.5,cell.getY(),cell.getZ()+.5);
        for(int x:new int[]{-2,-1,1,2})for(int y=0;y<3;y++)w.setBlockState(cell.add(x,y,1),Blocks.IRON_BARS.getDefaultState());
        guard.setPosition(cell.getX()+.5,cell.getY(),cell.getZ()+.5);VillageTrials.sealCell(w,other,cell);
        require(other.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_cell_pending_")),"gate waits for escort to exit");
        guard.setPosition(cell.getX()+.5,cell.getY(),cell.getZ()+3.5);
        invoke(VillageTrials.class,"closePendingCells",new Class<?>[]{ServerWorld.class},w);
        require(w.getBlockState(cell.south()).isOf(Blocks.IRON_BARS),"gate closes after guard leaves");VillageTrials.openCell(w,other);
        require(w.getBlockState(cell.south()).isAir(),"gate opens on release");
        var square=new net.minecraft.entity.MarkerEntity(EntityType.MARKER,w);square.setPosition(base.getX(),base.getY(),base.getZ());w.spawnEntity(square);
        other.addCommandTag("halloween_trial_1000");other.attachLeash(guard,true);worker.addCommandTag("halloween_trial_1000");
        require(!VillageTrials.waitingPost(square,other).equals(VillageTrials.waitingPost(square,worker)),"two convoys receive separate waiting positions");
        require(VillageTrials.claimCourt(w,square,other),"first convoy occupies court");
        require(!VillageTrials.claimCourt(w,square,worker),"second convoy cannot interrupt active hearing");
        other.detachLeashWithoutDrop();require(VillageTrials.claimCourt(w,square,worker),"orphaned hearing no longer blocks queue");
        other.removeCommandTag("halloween_trial_1000");worker.removeCommandTag("halloween_trial_1000");
        guard.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.INTERACTION_TARGET,worker);
        guard.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(worker,.5f,1));
        VillageEscort.move(guard,base,.6f);
        require(guard.getBrain().getOptionalRegisteredMemory(net.minecraft.entity.ai.brain.MemoryModuleType.INTERACTION_TARGET).isEmpty(),"escort drops social conversation target");
        BlockPos bush=base.add(10,0,6);w.setBlockState(bush,Blocks.OAK_LEAVES.getDefaultState());
        BlockPos safe=VillageTrials.standingPost(w,bush);require(!safe.equals(bush)&&w.getBlockState(safe).getCollisionShape(w,safe).isEmpty(),"waiting post avoids plaza bushes");
        other.setPosition(base.getX()+.5,base.getY(),base.getZ()+.5);guard.setPosition(base.getX()+4.5,base.getY(),base.getZ()+.5);VillageTrials.followEscort(other,guard);
        require(VillageTasks.ownsMovement(other),"captive receives explicit follow movement");
        BlockPos courtCage=base.add(8,0,8);VillageTrials.closeCage(w,courtCage);other.setPosition(courtCage.getX()+.5,courtCage.getY(),courtCage.getZ()-1.5);
        require(!VillageTrials.enterAccused(w,other,courtCage)&&w.getBlockState(courtCage.north()).isAir(),"accused gets an open cage entrance before movement");
        other.setPosition(courtCage.getX()+.5,courtCage.getY(),courtCage.getZ()+.5);
        require(VillageTrials.enterAccused(w,other,courtCage)&&other.getCommandTags().contains("halloween_court_inside")&&w.getBlockState(courtCage.north()).isOf(Blocks.IRON_BARS),"accused enters, cage closes and hearing starts");
        require(VillageEraPlans.stone(Blocks.WALL_TORCH.getDefaultState()).isOf(Blocks.WALL_TORCH),"door wall torches do not gain blocking support columns");
        for(int d=0;d<=128;d++)require(Math.abs(VillageWorldgen.surface(64,d,70)-VillageWorldgen.surface(64,Math.max(0,d-1),70))<=1,"small hills blend smoothly during generation");
        for(int distance=0;distance<=50;distance++)require(VillageTerrain.height(64,distance,180)==64,"castle has 50-block flat apron");
        for(int distance=51;distance<=400;distance++)require(Math.abs(VillageTerrain.height(64,distance,180)-VillageTerrain.height(64,distance-1,180))<=1,"mountain transition has no cliff");
        var twoRooms=new net.minecraft.entity.MarkerEntity(EntityType.MARKER,w);twoRooms.setPosition(base.getX()+70.5,base.getY(),base.getZ()+70.5);twoRooms.addCommandTag("halloween_jail");w.spawnEntity(twoRooms);
        require(VillagePrisons.cellFor(w,twoRooms,other)==null,"unbuilt prison cannot allocate a cell");
        VillagePrisons.prepare(w,twoRooms);
        var layoutsField=VillagePrisons.class.getDeclaredField("LAYOUTS");layoutsField.setAccessible(true);
        var layout=((Map<?,?>)layoutsField.get(null)).get(twoRooms);
        var planField=layout.getClass().getDeclaredField("plan");planField.setAccessible(true);
        var prisonPlan=(List<Map.Entry<BlockPos,net.minecraft.block.BlockState>>)planField.get(layout);
        Set<Long> prisonChunks=new HashSet<>();for(var block:prisonPlan)prisonChunks.add(new ChunkPos(block.getKey()).toLong());
        for(long chunk:prisonChunks){var cp=new ChunkPos(chunk);w.getChunk(cp.x,cp.z);}
        var prisonObserver=new net.minecraft.server.network.ServerPlayerEntity(w.getServer(),w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PrisonObserver"),net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());
        prisonObserver.setPosition(-1000,100,-1000);w.getPlayers().add(prisonObserver);
        try{for(int n=0;n<2000&&!VillagePrisons.ready(w,twoRooms);n++)invoke(VillagePrisons.class,"tick",new Class<?>[]{ServerWorld.class},w);}
        finally{w.getPlayers().remove(prisonObserver);}
        var progressField=layout.getClass().getDeclaredField("index");progressField.setAccessible(true);int prisonIndex=(int)progressField.get(layout);
        require(VillagePrisons.ready(w,twoRooms),"budgeted prison construction completes before allocation: "+prisonIndex+"/"+prisonPlan.size()+" next="+(prisonIndex<prisonPlan.size()?prisonPlan.get(prisonIndex):"none"));
        BlockPos firstRoom=VillagePrisons.cellFor(w,twoRooms,other),secondRoom=VillagePrisons.cellFor(w,twoRooms,guard);
        require(firstRoom!=null&&secondRoom!=null,"completed prison allocates both rooms");
        require(VillagePrisonBeds.forbidden(worker,firstRoom),"civilian cannot claim a prison room bed");
        worker.addCommandTag("halloween_prisoner");require(!VillagePrisonBeds.forbidden(worker,firstRoom),"assigned inmates retain prison sleeping permission");worker.removeCommandTag("halloween_prisoner");
        require(!firstRoom.equals(secondRoom),"first two inmates receive different cells");
        var stairRoute=VillagePrisons.escortRoute(w,twoRooms,other);
        for(int n=1;n<stairRoute.size();n++)require(Math.abs(stairRoute.get(n).getY()-stairRoute.get(n-1).getY())<=1,"escort descends one stair at a time");
        org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: budgeted prison construction, two rooms and stair-by-stair convoy route");
        require(w.getBlockState(firstRoom).isAir()&&w.getBlockState(secondRoom).isAir(),"both cells have walkable interiors");
        // A blocked prefix of the queue must not starve a later project.
        var state=VillageProjects.get(w);
        var intruder=new VillageCivilWorks.Change(firstRoom,Blocks.AIR.getDefaultState(),Blocks.STONE_BRICKS.getDefaultState());
        var overlapping=new VillageProjects.Project("cathedral-prison-test",team.village,"cathedral",firstRoom,firstRoom,List.of(intruder),0,false,0);
        require(VillageProjects.preservePrison(w,state,overlapping,intruder)&&overlapping.index==1&&w.getBlockState(firstRoom).isAir(),"cathedral cannot seal a prison region registered after the original survey");
        // Cell geometry was applied above; finish this fixture before isolating queue fairness.
        for(var roomJob:List.copyOf(state.jobs))if(roomJob.id.startsWith("prison_rooms"))invoke(VillageProjects.class,"finish",new Class<?>[]{ServerWorld.class,VillageProjects.class,VillageMemory.class,VillageProjects.Project.class},w,state,memory,roomJob);
        worker.setPosition(base.getX()+17.5,base.getY(),base.getZ()+2.5);
        for(int n=0;n<9;n++){var p=base.add(n*2,0,0);w.setBlockState(p,Blocks.STONE.getDefaultState());state.add(w,"regression_"+n,team.village,"regression",Map.of(p,Blocks.BRICKS.getDefaultState()));if(n<8)w.setBlockState(p,Blocks.DIAMOND_BLOCK.getDefaultState());}
        var fake=new net.minecraft.server.network.ServerPlayerEntity(w.getServer(),w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Regression"),net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());fake.setPosition(base.getX(),base.getY(),base.getZ());w.getPlayers().add(fake);
        require(VillageFastEras.configure(fake.getCommandSource().withSilent(),true)==1&&VillageFastEras.active(w,team.village),"fast-era command enables only the nearby settlement");
        var fastData = VillageEraData.get(w);
        var fastSettlement = fastData.settlement(team);
        int previousEra = fastSettlement.era;
        fastSettlement.era = 3;
        var late = base.add(25, 5, 1);
        w.getChunk(late);
        state.add(w, "fast_post_transition", team.village, "shipyard", Map.of(late, Blocks.BRICKS.getDefaultState()));
        for (int i = 0; i < 16 && !state.complete(team.village,"shipyard"); i++)
            VillageFastEras.advance(w, team, fastData, fastSettlement);
        require(VillageFastEras.active(w,team.village)&&w.getBlockState(late).isOf(Blocks.BRICKS),
                "fast mode stays active and materializes new port construction in era III");
        w.setBlockState(late, Blocks.AIR.getDefaultState());
        for (int i = 0; i < 64 && w.getBlockState(late).isAir(); i++) VillageBuilders.fastRepair(w, team, 1000, 32);
        require(w.getBlockState(late).isOf(Blocks.BRICKS), "fast repair restores a missing block from a finished project");
        fastSettlement.era = previousEra;
        require(VillageFastEras.configure(fake.getCommandSource().withSilent(),false)==1&&!VillageFastEras.active(w,team.village),"fast-era off preserves world while disabling acceleration");
        fake.addCommandTag("halloween_player_sentenced");fake.addCommandTag("halloween_player_court_announced");fake.addCommandTag("halloween_player_route_idx_7");fake.addCommandTag("halloween_player_crime_murder");PlayerJustice.resetHearing(fake);
        require(!fake.getCommandTags().contains("halloween_player_sentenced")&&!fake.getCommandTags().contains("halloween_player_court_announced")&&fake.getCommandTags().stream().noneMatch(t->t.startsWith("halloween_player_route_")),"repeat surrender clears previous court and convoy phase");
        require(fake.getCommandTags().contains("halloween_player_crime_murder"),"repeat surrender preserves crimes");
        var field=ServerWorld.class.getDeclaredField("worldProperties");field.setAccessible(true);var properties=(net.minecraft.world.level.ServerWorldProperties)field.get(w);long old=w.getTime();
        try{for(int tick=0;tick<=200;tick+=20){properties.setTime(tick);invoke(VillageProjects.class,"tick",new Class<?>[]{ServerWorld.class},w);}}
        finally{w.getPlayers().remove(fake);properties.setTime(old);}
        require(state.jobs.stream().filter(job->job.id.equals("regression_8")).findFirst().orElseThrow().done,"later project progresses despite eight blocked projects");
        require(w.getBlockState(base).isOf(Blocks.DIAMOND_BLOCK),"player obstruction preserved");
        properties.setTime(old+1);
        for(int i=0;i<8;i++)require(VillagePathBudget.take(w),"path budget available");require(!VillagePathBudget.take(w),"path searches bounded");
        BlockPos dungeon=base.add(0,0,28);
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=-1;y<=9;y++)w.setBlockState(dungeon.add(x,y,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        for(int i=0;i<7;i++)for(int lane=0;lane<=1;lane++)w.setBlockState(dungeon.add(lane,5-i,2+i),Blocks.COBBLESTONE_STAIRS.getDefaultState().with(StairsBlock.FACING,Direction.NORTH),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        for(int z=0;z<=8;z++)for(int y=0;y<=2;y++)w.setBlockState(dungeon.add(2,y,z),Blocks.AIR.getDefaultState());
        for(int x=0;x<=2;x++)for(int z=0;z<=1;z++)for(int y=0;y<=2;y++)w.setBlockState(dungeon.add(x,y,z),Blocks.AIR.getDefaultState());
        var jail=new net.minecraft.entity.MarkerEntity(EntityType.MARKER,w);jail.setPosition(dungeon.getX()+.5,dungeon.getY(),dungeon.getZ()+.5);jail.addCommandTag("halloween_jail");w.spawnEntity(jail);
        VillageCivilWorks.dungeonRoute(w,jail);
        var upgrade=memory.houses.stream().filter(h->h.recordKey.equals("dungeon_clearance_v5:"+jail.getUuidAsString())).findFirst().orElseThrow();
        for(var r:upgrade.blueprint)w.setBlockState(r.pos(),r.state(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        properties.setTime(old+201);var route=VillageCivilWorks.dungeonRoute(w,jail);require(route.size()>=7,"wide dungeon route survives extra/missing neighbouring stairs");
        for(int i=1;i<route.size();i++)require(route.get(i).getSquaredDistance(route.get(i-1))<=2,"dungeon route contiguous");
        require(route.getLast().equals(dungeon),"route reaches prison cell");properties.setTime(old);
        if(Boolean.getBoolean("halloween.worldgenRegression")){
            var generated=w.getChunk(3,16);net.minecraft.util.math.BlockBox castleBox=null;
            for(var start:generated.getStructureStarts().values())for(var piece:start.getChildren())if(piece instanceof net.minecraft.structure.PoolStructurePiece pool&&pool.getPoolElement() instanceof net.minecraft.structure.pool.SinglePoolElement single)try{if(single.getIdOrThrow().getPath().endsWith("/royal_castle"))castleBox=pool.getBoundingBox();}catch(RuntimeException ignored){}
            require(castleBox!=null,"real seed generates a castle in the reported village");
            int x=castleBox.getMinX()-8,z=castleBox.getMinZ()-8,floor=castleBox.getMinY();w.getChunk(x>>4,z>>4);
            require(w.getBlockState(new BlockPos(x,floor,z)).isSolidBlock(w,new BlockPos(x,floor,z)),"worldgen builds the flat apron before structures");
            for(int y=floor+25;y<=floor+80;y++)require(w.getBlockState(new BlockPos(x,y,z)).isAir(),"worldgen leaves no terrain or ore above flat apron");
            org.slf4j.LoggerFactory.getLogger("halloween-regression").info("PASS: actual mountainous seed terrain generated before entry at {}",castleBox);
        }
    }
}
