package dev.halloween;

import com.mojang.serialization.JsonOps;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import org.slf4j.LoggerFactory;

/** Integration exercise in the isolated village smoke world. */
final class VillageBuilderSmokeChecks {
    private static int ticks;private static boolean done;private static VillageMemory memory;private static VillageMemory.House house;
    private static VillagerEntity worker;private static BlockPos min;private static int lastProgress;private static boolean navigationPhase;
    private static net.minecraft.entity.passive.HorseEntity testHorse;private static BlockPos horseGoal;private static double horseStart;private static boolean horseChecked;
    private static volatile boolean mountedModelChecked;private static volatile String mountedModelFailure;
    private static VillagerEntity courtGuard,courtBuilder;private static net.minecraft.entity.MarkerEntity courtSquare,courtBell,courtPodium;
    private static Vec3d courtPlayerOriginal;private static BlockPos testCage;private static boolean courtChecked;private static int insideTicks,jailedTicks;
    static void register(){ServerTickEvents.END_SERVER_TICK.register(server->{
        if(done||server.getPlayerManager().getPlayerList().isEmpty())return;
        ServerWorld w=server.getOverworld();
        try{
            if(Boolean.getBoolean("halloween.courtRouteRegression")){
                if(++ticks==20){var p=w.getPlayers().getFirst();p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);p.setInvulnerable(true);w.setTimeOfDay(6000);startCourtScenario(w,p.getBlockPos().add(0,5,0));}
                if(ticks>20&&ticks%5==0)for(int i=0;i<32;i++)VillagePathBudget.takeEscort(w);
                if(ticks>40&&w.getPlayers().getFirst().getCommandTags().contains("halloween_player_cage_inside")){
                    var p=w.getPlayers().getFirst();
                    require(courtGuard.squaredDistanceTo(courtPlayerOriginal)>9,"guard physically travels from arrest point");
                    require(p.squaredDistanceTo(Vec3d.ofBottomCenter(testCage))<.5,"player physically inside cage: "+p.getEntityPos()+" cage="+testCage);
                    require(VillageTrials.cageReady(w,testCage)&&w.getBlockState(testCage.north()).isOf(Blocks.IRON_BARS)&&w.getBlockState(testCage.north().up()).isOf(Blocks.IRON_BARS),"cage exists and door is closed");
                    if(++insideTicks==40)net.minecraft.client.MinecraftClient.getInstance().execute(()->{var client=net.minecraft.client.MinecraftClient.getInstance();net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,"custody-0.39.4.png",client.getFramebuffer(),1,t->LoggerFactory.getLogger("halloween-village-test").info("Screenshot: {}",t.getString()));});
                    if(insideTicks==100)LoggerFactory.getLogger("halloween-village-test").info("PASS: player physically inside closed court cage for 100 ticks");
                }
                if(w.getPlayers().getFirst().getCommandTags().contains("halloween_player_jailed")){
                    var p=w.getPlayers().getFirst();var cell=VillagePrisons.assigned(p);require(cell!=null&&p.squaredDistanceTo(Vec3d.ofBottomCenter(cell))<2.25,"player physically in assigned prison cell");
                    var post=VillagePrisons.doorPost(w,p);var facing=Direction.getFacing(post.getX()-cell.getX(),0,post.getZ()-cell.getZ());var gate=cell.offset(facing,2);
                    require(w.getBlockState(gate).isOf(Blocks.IRON_BARS)&&w.getBlockState(gate.up()).isOf(Blocks.IRON_BARS),"assigned cell gate closed");
                    if(++jailedTicks==40)net.minecraft.client.MinecraftClient.getInstance().execute(()->{var client=net.minecraft.client.MinecraftClient.getInstance();net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory,"prison-0.39.4.png",client.getFramebuffer(),1,t->{});});
                    if(jailedTicks>=80){done=true;LoggerFactory.getLogger("halloween-village-test").info("PASS: full court, verdict, dungeon escort and physical imprisonment in closed assigned cell");net.minecraft.client.MinecraftClient.getInstance().execute(()->net.minecraft.client.MinecraftClient.getInstance().scheduleStop());}
                }
                if(ticks%200==0)LoggerFactory.getLogger("halloween-village-test").info("CONVOY guard={} player={} post={} cell={} route={}",courtGuard.getEntityPos(),w.getPlayers().getFirst().getEntityPos(),VillagePrisons.doorPost(w,w.getPlayers().getFirst()),VillagePrisons.assigned(w.getPlayers().getFirst()),w.getPlayers().getFirst().getCommandTags().stream().filter(t->t.startsWith("halloween_player_route_")).toList());
                if(ticks>3600)throw new AssertionError("court route stalled: guard="+courtGuard.getBlockPos()+" player="+w.getPlayers().getFirst().getBlockPos()+" cage="+testCage+" path="+courtGuard.getNavigation().getCurrentPath());
                return;
            }
            if(++ticks==20){
                min=server.getPlayerManager().getPlayerList().getFirst().getBlockPos().add(8,6,8);
                if(Boolean.getBoolean("halloween.crimeRegression")){verifyCrimes(w,min);done=true;net.minecraft.client.MinecraftClient.getInstance().execute(()->net.minecraft.client.MinecraftClient.getInstance().scheduleStop());return;}
                server.getPlayerManager().getPlayerList().getFirst().setInvulnerable(true);
                // A controlled miniature house, with solid ground under the future scaffold.
                for(int y=0;y<=11;y++)for(int x=-1;x<=6;x++)for(int z=-2;z<=6;z++)w.setBlockState(min.add(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
                w.setBlockState(min.add(1,0,-1),Blocks.STONE.getDefaultState());
                for(int y=0;y<=4;y++)for(int x=0;x<=5;x++)w.setBlockState(min.add(x,y,0),Blocks.OAK_PLANKS.getDefaultState());
                worker=new VillagerEntity(EntityType.VILLAGER,w);worker.setInvulnerable(true);worker.setPosition(min.getX()+2.5,min.getY()+1,min.getZ()+2.5);w.spawnEntity(worker);
                BlockPos repair=min.add(2,1,0);var record=new VillageMemory.BlockRecord(repair,Blocks.OAK_PLANKS.getDefaultState());
                memory=new VillageMemory();house=new VillageMemory.House(min,min.add(5,4,5),List.of(record),w.getTime(),List.of(),0,false,List.of());
                w.setBlockState(repair,Blocks.AIR.getDefaultState());
                require(VillageBuilders.findRepair(w,house)!=null,"detect missing house block");
                require(VillageBuilders.work(w,memory,house,worker,record,false),"villager repairs reachable block");
                require(w.getBlockState(repair).isOf(Blocks.OAK_PLANKS),"repair restored original material");
                w.setBlockState(repair,Blocks.DIAMOND_BLOCK.getDefaultState());
                require(!VillageBuilders.work(w,memory,house,worker,record,false)&&w.getBlockState(repair).isOf(Blocks.DIAMOND_BLOCK),"never overwrite player blocks");
                w.setBlockState(repair,Blocks.OAK_PLANKS.getDefaultState());
                verifyCourtAndDefense(w,min,worker);verifyWaterRescue(w,min,worker);verifyTrialItems(w,min);verifyCrimes(w,min);verifyDungeonRoute(w,min,worker);verifyMountedModel();startCourtScenario(w,min);
                require(VillageBuilders.planExtension(w,house),"plan unobstructed second floor");
                memory.houses.add(house);
                var json=VillageMemory.CODEC.encodeStart(JsonOps.INSTANCE,memory).getOrThrow();
                VillageMemory decoded=VillageMemory.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
                require(decoded.houses.size()==1&&decoded.houses.getFirst().work.size()==house.work.size(),"persistent blueprint round-trip");
                lastProgress=ticks;
            }
            if(ticks>20&&house!=null&&!house.extended){
                if(house.workIndex>=house.work.size()){
                    VillageBuilders.advance(w,memory,house,worker);
                }else{
                var task=house.work.get(house.workIndex);BlockPos target=task.pos();
                // Simulate the pathfinding arrival while exercising real server placement and scaffold updates.
                if(!navigationPhase)worker.setPosition(target.getX()+2.2,target.getY(),target.getZ()+.5);
                int before=house.workIndex;VillageBuilders.advance(w,memory,house,worker);
                if(house.workIndex>before)lastProgress=ticks;
                require(ticks-lastProgress<(navigationPhase?400:100),"construction stalled at "+target+" (villager "+worker.getBlockPos()+")");
                }
            }
            if(house!=null&&house.extended&&!navigationPhase){
                require(w.getBlockState(min.add(0,5,0)).isOf(Blocks.OAK_PLANKS),"second-floor platform");
                require(w.getBlockState(min.add(0,9,0)).isOf(Blocks.OAK_STAIRS),"second-floor roof");
                require(w.getBlockState(min.add(1,1,-1)).isAir(),"temporary scaffolding removed after construction");
                LoggerFactory.getLogger("halloween-village-test").info("PASS: repair, player-block protection, persisted work, scaffold cleanup and second floor");
                min=min.add(18,0,0);w.setTimeOfDay(6000);
                for(int y=0;y<=11;y++)for(int x=-3;x<=8;x++)for(int z=-3;z<=8;z++)w.setBlockState(min.add(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
                for(int x=-3;x<=8;x++)for(int z=-3;z<=8;z++)w.setBlockState(min.add(x,0,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
                for(int y=1;y<=4;y++)for(int x=0;x<=5;x++)w.setBlockState(min.add(x,y,0),Blocks.OAK_PLANKS.getDefaultState());
                worker.setPosition(min.getX()+2.5,min.getY()+1,min.getZ()+2.5);
                house=new VillageMemory.House(min,min.add(5,4,5),List.of(),w.getTime(),List.of(),0,false,List.of());
                require(VillageBuilders.planExtension(w,house),"plan real navigation test");
                navigationPhase=true;lastProgress=ticks;
            }
            if(ticks==40&&testHorse!=null)require(testHorse.getNavigation().startMovingTo(horseGoal.getX()+.5,horseGoal.getY(),horseGoal.getZ()+.5,.9),"horse navigation starts");
            if(ticks>=140){require(mountedModelChecked&&mountedModelFailure==null,"mounted zombie villager pose: "+mountedModelFailure);}
            if(!horseChecked&&ticks>=140&&testHorse!=null){
                require(Math.abs(testHorse.getX()-horseStart)>6,"mounted guard horse follows its navigation path at a useful speed (moved "+Math.abs(testHorse.getX()-horseStart)+", pos "+testHorse.getBlockPos()+", goal "+horseGoal+", path "+testHorse.getNavigation().getCurrentPath()+", vehicle "+testHorse.hasPassengers()+")");
                horseChecked=true;testHorse.discard();LoggerFactory.getLogger("halloween-village-test").info("PASS: mounted guard horse movement");
            }
            if(!courtChecked&&ticks>=750&&(w.getPlayers().getFirst().getCommandTags().contains("halloween_player_sentenced")||ticks>=1200)){
                var player=w.getPlayers().getFirst();
                require(player.getCommandTags().contains("halloween_player_sentenced"),"guard brings player to cage and court delivers verdict: tags="+player.getCommandTags()+", cage="+testCage+", ready="+VillageTrials.cageReady(w,testCage)+", player="+player.getBlockPos()+", guard="+courtGuard.getBlockPos()+", builder="+courtBuilder.getBlockPos()+", time="+w.getTime());
                require(!player.getCommandTags().contains("halloween_player_cage_inside"),"player leaves cage after verdict");
                require(!w.getBlockState(testCage.up(3)).isOf(Blocks.IRON_BARS),"builders remove cage after verdict");
                require(courtGuard.squaredDistanceTo(Vec3d.ofCenter(testCage))>1,"escort remains outside the cage");
                PlayerJustice.clear(player);courtGuard.removeCommandTag("halloween_player_guarding");courtGuard.discard();courtBuilder.discard();courtSquare.discard();courtBell.discard();courtPodium.discard();
                player.setPosition(courtPlayerOriginal.x,courtPlayerOriginal.y,courtPlayerOriginal.z);courtChecked=true;
                LoggerFactory.getLogger("halloween-village-test").info("PASS: player escorted to cage, sentenced and released from cage");
            }
            if(navigationPhase&&house.workIndex>8&&worker.getY()>=min.getY()+3&&horseChecked&&courtChecked){
                done=true;LoggerFactory.getLogger("halloween-village-test").info("ALL CHECKS PASSED: villager walked and climbed scaffold to elevation {}",worker.getY()-min.getY());
                net.minecraft.client.MinecraftClient.getInstance().execute(()->net.minecraft.client.MinecraftClient.getInstance().scheduleStop());
            }
        }catch(Throwable e){done=true;LoggerFactory.getLogger("halloween-village-test").error("VILLAGE SMOKE TEST FAILED",e);
            net.minecraft.client.MinecraftClient.getInstance().execute(()->net.minecraft.client.MinecraftClient.getInstance().scheduleStop());}
    });}
    private static void verifyMountedModel(){
        net.minecraft.client.MinecraftClient.getInstance().execute(()->{
            try{
                VillagerArmorFeature.verifyWalkingSync();
                var data=net.minecraft.client.render.entity.model.VillagerResemblingModel.getModelData();
                var root=net.minecraft.client.model.TexturedModelData.of(data,64,64).createModel();
                var model=new net.minecraft.client.render.entity.model.VillagerResemblingModel(root);
                var state=new net.minecraft.client.render.entity.state.VillagerEntityRenderState();var extra=(GuardRiderState)state;
                extra.halloween$setGuard(true);extra.halloween$setMountedGuard(true);extra.halloween$setSwing(.5f);
                model.setAngles(state);
                if(root.getChild("arms").visible||!root.getChild("halloween_right_arm").visible||!root.getChild("halloween_left_arm").visible)throw new AssertionError("combat arms missing");
                if(root.getChild("right_leg").pitch>-.5f||!Float.isFinite(root.getChild("halloween_right_arm").pitch))throw new AssertionError("invalid mounted animation");
                extra.halloween$setGuard(false);extra.halloween$setMountedGuard(false);model.setAngles(state);
                if(!root.getChild("arms").visible||root.getChild("halloween_right_arm").visible||root.getChild("halloween_left_arm").visible)throw new AssertionError("idle folded arms missing");
                LoggerFactory.getLogger("halloween-village-test").info("PASS: zombie villager mounted pose and spear arm");
            }catch(Throwable e){mountedModelFailure=e.toString();}finally{mountedModelChecked=true;}
        });
    }
    private static void verifyCourtAndDefense(ServerWorld w,BlockPos origin,VillagerEntity worker){
        BlockPos center=origin.add(0,0,15);
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){
            w.setBlockState(center.add(x,-1,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
            for(int y=0;y<=5;y++)w.setBlockState(center.add(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        }
        var previous=worker.getVillagerData();worker.setVillagerData(previous.withProfession(BuilderProfession.ENTRY));
        worker.setPosition(center.getX()+4.5,center.getY(),center.getZ()+.5);
        BlockPos existing=center.add(-1,0,-1);w.setBlockState(existing,Blocks.IRON_BARS.getDefaultState());
        for(int i=0;i<40&&!VillageTrials.cageReady(w,center);i++)VillageTrials.buildCage(w,center);
        require(VillageTrials.cageReady(w,center),"court cage is three by three with a roof");
        VillageTrials.closeCage(w,center);require(w.getBlockState(center.add(0,0,-1)).isOf(Blocks.IRON_BARS),"cage closes after entry");
        VillageTrials.removeCage(w,center);require(w.getBlockState(existing).isOf(Blocks.IRON_BARS),"cleanup preserves pre-existing bars");
        require(w.getBlockState(center.add(0,3,0)).isAir(),"temporary cage roof removed");
        BlockPos ladder=center.add(0,1,2);w.setBlockState(ladder,Blocks.LADDER.getDefaultState(),Block.NOTIFY_LISTENERS);
        VillageTrials.sealCell(w,worker,center);
        require(worker.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_cell_gate_")),"cell gate closes behind prisoner");
        VillageTrials.openCell(w,worker);
        require(worker.getCommandTags().stream().noneMatch(t->t.startsWith("halloween_cell_gate_")),"cell gate opens on release");
        w.setBlockState(ladder,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        worker.setVillagerData(previous);worker.setPosition(origin.getX()+2.5,origin.getY()+1,origin.getZ()+2.5);
        var guard=new VillagerEntity(EntityType.VILLAGER,w);guard.setVillagerData(guard.getVillagerData().withProfession(GuardProfession.ENTRY));
        guard.setPosition(center.getX()-4.5,center.getY(),center.getZ()+3.5);guard.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,new net.minecraft.item.ItemStack(net.minecraft.item.Items.CROSSBOW));w.spawnEntity(guard);
        var zombie=new net.minecraft.entity.mob.ZombieEntity(EntityType.ZOMBIE,w);zombie.setPosition(center.getX()+3.5,center.getY(),center.getZ()+3.5);zombie.setAiDisabled(true);w.spawnEntity(zombie);
        VillageDefense.shoot(w,guard,zombie);
        guard.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_SWORD));
        guard.getMainHandStack().damage(10000,guard,net.minecraft.entity.EquipmentSlot.MAINHAND);
        require(guard.getMainHandStack().isOf(net.minecraft.item.Items.IRON_SWORD)&&guard.getMainHandStack().getDamage()==0,"guard sword remains intact after attack");
        var player=w.getPlayers().getFirst();var originalPlayer=new Vec3d(player.getX(),player.getY(),player.getZ());
        long oldLeads=w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(center).expand(24),item->item.getStack().isOf(net.minecraft.item.Items.LEAD)).size();
        player.setPosition(guard.getX()+8,guard.getY(),guard.getZ());
        for(int i=0;i<5;i++)PlayerJustice.makeLead(w,player,guard);
        require(PlayerJustice.leadAttached(w,player,guard),"player custody leash attaches directly to player");
        var mode=player.interactionManager.getGameMode();player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        player.setVelocity(0,0,0);PlayerJustice.pull(player,guard);
        require(player.getVelocity().x<0&&player.getVelocity().horizontalLength()<=.25,"custody pulls toward guard at bounded speed");
        player.changeGameMode(mode);
        PlayerJustice.removeLead(player);require(!PlayerJustice.leadAttached(w,player,guard),"custody leash cleanup");
        require(w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(center).expand(24),item->item.getStack().isOf(net.minecraft.item.Items.LEAD)).size()==oldLeads,"custody does not drop duplicate leads");
        require(w.getEntitiesByClass(net.minecraft.entity.passive.ChickenEntity.class,new Box(center).expand(24),e->e.getCommandTags().contains("halloween_custody_lead")).isEmpty(),"custody does not spawn chickens");
        player.setPosition(originalPlayer.x,originalPlayer.y,originalPlayer.z);
        require(!w.getEntitiesByClass(net.minecraft.entity.projectile.ArrowEntity.class,new Box(center).expand(12),e->e.getOwner()==guard).isEmpty(),"crossbow guard fires a real arrow");
        zombie.discard();guard.discard();
        for(int x=-5;x<=12;x++)for(int z=-6;z<=-3;z++){
            w.setBlockState(center.add(x,-1,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
            for(int y=0;y<=4;y++)w.setBlockState(center.add(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        }
        testHorse=new net.minecraft.entity.passive.HorseEntity(EntityType.HORSE,w);testHorse.setPosition(center.getX()-4.5,center.getY(),center.getZ()-4.5);w.spawnEntity(testHorse);
        var rider=new VillagerEntity(EntityType.VILLAGER,w);rider.setVillagerData(rider.getVillagerData().withProfession(GuardProfession.ENTRY));rider.setPosition(testHorse.getX(),testHorse.getY(),testHorse.getZ());w.spawnEntity(rider);
        require(rider.startRiding(testHorse,true,true),"guard mounts horse");horseStart=testHorse.getX();
        horseGoal=center.add(6,0,-5);
        LoggerFactory.getLogger("halloween-village-test").info("PASS: court cage, prison gate, player leash, safe cleanup and guard crossbow projectile");
    }
    private static void verifyWaterRescue(ServerWorld w,BlockPos origin,VillagerEntity worker){
        BlockPos center=origin.add(-12,1,0);for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
            w.setBlockState(center.add(x,-1,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
            for(int y=0;y<3;y++)w.setBlockState(center.add(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        }
        var trapped=new VillagerEntity(EntityType.VILLAGER,w);trapped.setPosition(center.getX()+.5,center.getY(),center.getZ()+.5);w.spawnEntity(trapped);
        w.setBlockState(center,Blocks.WATER.getDefaultState(),Block.NOTIFY_LISTENERS);
        BlockPos irrigation=center.add(2,0,0);w.setBlockState(irrigation,Blocks.WATER.getDefaultState(),Block.NOTIFY_LISTENERS);
        w.setBlockState(irrigation.east(),Blocks.FARMLAND.getDefaultState(),Block.NOTIFY_LISTENERS);
        VillageWaterRescue.rescueAt(w,worker,trapped);
        require(!w.getBlockState(center).isOf(Blocks.WATER),"builder drains water trapping villager");
        require(w.getBlockState(irrigation).isOf(Blocks.WATER),"builder preserves farm irrigation");
        trapped.discard();LoggerFactory.getLogger("halloween-village-test").info("PASS: flood rescue preserves irrigation");
    }
    private static void startCourtScenario(ServerWorld w,BlockPos origin){
        BlockPos center=origin.add(30,0,0);
        for(int x=-12;x<=32;x++)for(int z=-12;z<=12;z++){
            BlockPos p=center.add(x,0,z);w.setBlockState(p.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
            for(int y=0;y<5;y++)w.setBlockState(p.up(y),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        }
        courtSquare=EntityType.MARKER.create(w,net.minecraft.entity.SpawnReason.EVENT);courtBell=EntityType.MARKER.create(w,net.minecraft.entity.SpawnReason.EVENT);courtPodium=EntityType.MARKER.create(w,net.minecraft.entity.SpawnReason.EVENT);
        require(courtSquare!=null&&courtBell!=null&&courtPodium!=null,"court markers created");
        courtSquare.refreshPositionAndAngles(center.getX()+.5,center.getY(),center.getZ()+.5,0,0);courtSquare.addCommandTag("halloween_square");w.spawnEntity(courtSquare);
        courtBell.refreshPositionAndAngles(center.getX()+.5,center.getY(),center.getZ()+.5,0,0);courtBell.addCommandTag("halloween_bell");w.spawnEntity(courtBell);
        courtPodium.refreshPositionAndAngles(center.getX()+8.5,center.getY(),center.getZ()+.5,0,0);courtPodium.addCommandTag("halloween_podium");w.spawnEntity(courtPodium);
        if(Boolean.getBoolean("halloween.courtRouteRegression")){
            var jail=EntityType.MARKER.create(w,net.minecraft.entity.SpawnReason.EVENT);var gate=EntityType.MARKER.create(w,net.minecraft.entity.SpawnReason.EVENT);
            require(jail!=null&&gate!=null,"prison test markers");BlockPos anchor=center.add(20,-7,8);
            jail.refreshPositionAndAngles(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5,0,0);jail.addCommandTag("halloween_jail");jail.addCommandTag("halloween_prison_facing_"+Direction.SOUTH.getHorizontalQuarterTurns());w.spawnEntity(jail);
            gate.refreshPositionAndAngles(anchor.getX()+.5,center.getY(),anchor.getZ()+.5,0,0);gate.addCommandTag("halloween_gate_left");w.spawnEntity(gate);VillagePrisons.prepare(w,jail);
        }
        testCage=VillageTrials.cagePosition(w,courtSquare);BlockPos post=Boolean.getBoolean("halloween.courtRouteRegression")?center.add(-9,0,-6):testCage.add(0,0,-2);
        courtGuard=new VillagerEntity(EntityType.VILLAGER,w);courtGuard.setVillagerData(courtGuard.getVillagerData().withProfession(GuardProfession.ENTRY));courtGuard.addCommandTag("halloween_guard");courtGuard.addCommandTag("halloween_player_guarding");courtGuard.setPosition(post.getX()+.5,post.getY(),post.getZ()+.5);w.spawnEntity(courtGuard);
        courtBuilder=new VillagerEntity(EntityType.VILLAGER,w);courtBuilder.setVillagerData(courtBuilder.getVillagerData().withProfession(BuilderProfession.ENTRY));courtBuilder.setPosition(testCage.getX()+4.5,testCage.getY(),testCage.getZ()+.5);w.spawnEntity(courtBuilder);
        var player=w.getPlayers().getFirst();courtPlayerOriginal=new Vec3d(player.getX(),player.getY(),player.getZ());
        if(Boolean.getBoolean("halloween.courtRouteRegression"))player.requestTeleport(post.getX()+.5,post.getY(),post.getZ()+1.5);else player.setPosition(post.getX()+.5,post.getY(),post.getZ()+1.5);PlayerJustice.clear(player);
        if(Boolean.getBoolean("halloween.courtRouteRegression"))player.addCommandTag("halloween_player_square_"+courtSquare.getUuidAsString());
        player.addCommandTag("halloween_player_wanted");player.addCommandTag("halloween_player_custody");player.addCommandTag("halloween_player_guard_"+courtGuard.getUuidAsString());
        player.addCommandTag("halloween_player_phase_"+(w.getTime()+500));player.addCommandTag("halloween_player_days_5");player.addCommandTag("halloween_player_crime_theft");
        PlayerJustice.makeLead(w,player,courtGuard);VillageTrials.assemble(w,courtSquare);
    }
    private static void verifyTrialItems(ServerWorld w,BlockPos origin){
        var player=w.getPlayers().getFirst();Vec3d original=new Vec3d(player.getX(),player.getY(),player.getZ());
        BlockPos center=origin.add(-12,1,0);player.setPosition(center.getX()+.5,center.getY(),center.getZ()+.5);player.setSneaking(false);
        var square=EntityType.MARKER.create(w,net.minecraft.entity.SpawnReason.EVENT);require(square!=null,"test court marker");
        square.refreshPositionAndAngles(center.getX()+.5,center.getY(),center.getZ()+.5,0,0);square.addCommandTag("halloween_square");w.spawnEntity(square);
        var guard=new VillagerEntity(EntityType.VILLAGER,w);guard.setVillagerData(guard.getVillagerData().withProfession(GuardProfession.ENTRY));guard.addCommandTag("halloween_guard");guard.setPosition(center.getX()+2.5,center.getY(),center.getZ()+.5);w.spawnEntity(guard);
        var suspect=new VillagerEntity(EntityType.VILLAGER,w);suspect.setPosition(center.getX()+1.5,center.getY(),center.getZ()+.5);w.spawnEntity(suspect);
        for(String t:List.copyOf(player.getCommandTags()))if(t.startsWith("halloween_test_trial_mode_"))player.removeCommandTag(t);
        VillageItems.TRIAL_TEST.use(w,player,net.minecraft.util.Hand.MAIN_HAND);
        var arrested=w.getEntitiesByClass(VillagerEntity.class,new Box(center).expand(128),v->v.getCommandTags().contains("halloween_test_sentence_attack"));
        require(arrested.size()==1&&arrested.getFirst().isLeashed(),"trial test item starts an escorted assault trial");
        var accused=arrested.getFirst();var escort=accused.getLeashHolder() instanceof VillagerEntity v?v:null;accused.detachLeashWithoutDrop();
        for(String t:List.copyOf(accused.getCommandTags()))if(t.startsWith("halloween_trial_")||t.startsWith("halloween_test_sentence_")||t.equals("halloween_bandit")||t.equals("halloween_surrender"))accused.removeCommandTag(t);
        if(escort!=null){escort.removeCommandTag("halloween_guard_escorting");escort.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_SWORD));}
        player.setSneaking(true);VillageItems.TRIAL_TEST.use(w,player,net.minecraft.util.Hand.MAIN_HAND);player.setSneaking(false);
        require(player.getCommandTags().contains("halloween_test_trial_mode_1"),"shift use switches trial punishment");
        VillageItems.EXECUTION_TEST.use(w,player,net.minecraft.util.Hand.MAIN_HAND);
        var execution=w.getEntitiesByClass(VillagerEntity.class,new Box(center).expand(128),v->v.getCommandTags().contains("halloween_test_sentence_execution"));
        require(execution.size()==1&&execution.getFirst().isLeashed(),"execution item starts a separate execution trial");
        accused=execution.getFirst();if(accused.getLeashHolder() instanceof VillagerEntity escort2)escort2.removeCommandTag("halloween_guard_escorting");accused.detachLeashWithoutDrop();
        for(String t:List.copyOf(accused.getCommandTags()))if(t.startsWith("halloween_trial_")||t.startsWith("halloween_test_sentence_")||t.equals("halloween_bandit")||t.equals("halloween_surrender"))accused.removeCommandTag(t);
        for(String t:List.copyOf(player.getCommandTags()))if(t.startsWith("halloween_test_trial_mode_"))player.removeCommandTag(t);
        square.discard();suspect.discard();guard.discard();player.setPosition(original.x,original.y,original.z);
        LoggerFactory.getLogger("halloween-village-test").info("PASS: configurable trial and execution test items");
    }
    private static void verifyCrimes(ServerWorld w,BlockPos origin){
        var player=w.getPlayers().getFirst();var previousMode=player.interactionManager.getGameMode();player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);PlayerJustice.clear(player);
        for(int i=0;i<5;i++){
            var victim=new VillagerEntity(EntityType.VILLAGER,w);victim.setPosition(origin.getX()+3.5,origin.getY()+1,origin.getZ()+3.5);w.spawnEntity(victim);
            require(victim.damage(w,w.getDamageSources().playerAttack(player),1000),"fatal player damage accepted");
        }
        require(player.getCommandTags().contains("halloween_player_count_murder_5"),"five separate murders are recorded");
        require(player.getCommandTags().contains("halloween_player_days_25"),"five murders produce 25 days");
        require(PlayerJustice.wantedLevel(player)==3,"five murders reach the final wanted level");
        require(PlayerJustice.caseDetails(player).size()==5,"each murder retains its own court record");
        PlayerJustice.clear(player);player.changeGameMode(previousMode);LoggerFactory.getLogger("halloween-village-test").info("PASS: five murders recorded as 25 days");
    }
    private static void verifyDungeonRoute(ServerWorld w,BlockPos origin,VillagerEntity worker){
        BlockPos cell=origin.add(0,0,36);
        for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)for(int y=-1;y<=8;y++)w.setBlockState(cell.add(x,y,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
        w.setBlockState(cell,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);w.setBlockState(cell.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
        BlockPos top=cell.add(0,5,2);w.setBlockState(top,Blocks.LADDER.getDefaultState(),Block.NOTIFY_LISTENERS);
        var marker=EntityType.MARKER.create(w,net.minecraft.entity.SpawnReason.EVENT);require(marker!=null,"test jail marker created");
        marker.refreshPositionAndAngles(cell.getX()+.5,cell.getY(),cell.getZ()+.5,0,0);marker.addCommandTag("halloween_jail");w.spawnEntity(marker);
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        var original=new VillageMemory.House(top,top,List.of(new VillageMemory.BlockRecord(top,w.getBlockState(top))),w.getTime(),List.of(),0,true,List.of(),List.of(),List.of(),"structure","test_ladder:"+marker.getUuidAsString(),List.of());memory.houses.add(original);
        VillageCivilWorks.dungeonRoute(w,marker);
        var job=memory.houses.stream().filter(h->h.recordKey.equals("dungeon_stairs_v2:"+marker.getUuidAsString())).findFirst().orElse(null);
        require(job!=null,"builder receives dungeon staircase job");
        var tasks=new java.util.ArrayList<>(job.blueprint);tasks.sort(java.util.Comparator.comparingInt(r->r.state().isAir()?0:1));
        for(var task:tasks){worker.setPosition(task.pos().getX()+2.5,task.pos().getY(),task.pos().getZ()+.5);
            require(VillageBuilders.work(w,memory,job,worker,task,false),"builder can place stair task at "+task.pos());}
        var route=VillageCivilWorks.dungeonRoute(w,marker);require(route.size()>5,"completed stairs produce escort route");
        for(int i=1;i<route.size();i++)require(route.get(i).getSquaredDistance(route.get(i-1))<=3,"escort route has no gaps: "+route);
        require(route.getLast().equals(cell),"escort route ends at the cell");
        marker.discard();worker.setPosition(origin.getX()+2.5,origin.getY()+1,origin.getZ()+2.5);
        LoggerFactory.getLogger("halloween-village-test").info("PASS: builder creates contiguous stairs into the cell");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
