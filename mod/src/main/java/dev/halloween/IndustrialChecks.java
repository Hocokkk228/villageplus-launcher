package dev.halloween;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class IndustrialChecks {
    static void check(ServerWorld world) {
        long village = 405023L;
        var team = new VillageMemory.Team(village, new BlockPos(320, 215, 320), 0);
        var data = VillageEraData.get(world);
        world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).teams.add(team);
        var settlement = data.settlement(team);
        var base = team.anchor.add(23, -1, -20);
        for (int x = base.getX() >> 4; x <= (base.getX() + 37) >> 4; x++) {
            for (int z = base.getZ() >> 4; z <= (base.getZ() + 39) >> 4; z++) world.getChunk(x, z);
        }
        for (var floor : BlockPos.iterate(base, base.add(37, 0, 39))) world.setBlockState(floor, Blocks.DIRT.getDefaultState());
        require(!VillageIndustrial.start(settlement), "Wooden era cannot skip directly to IV");
        settlement.era = VillageEpoch.STEAM;
        settlement.eraAt = world.getTime();
        require(VillageIndustrial.start(settlement), "Third-era industrial transition requested");
        VillageIndustrial.tick(world, team, settlement);
        var state = VillageProjects.get(world);
        var parts = state.jobs.stream().filter(job -> job.id.startsWith("industrial_district:" + village + ":")).toList();
        require(parts.size() == 5 && settlement.era == VillageEpoch.STEAM, "Road, two panel blocks, school and shop queue before the era changes: jobs="+parts.size()+" data="+settlement.xp+" ground="+VillageEraPlans.levelSite(world,base.getX(),base.getZ(),38,40,4));
        for (int i = 0; i < 1200 && parts.stream().anyMatch(job -> !job.done); i++)
            VillageProjects.materialize(world, village, 4096);
        require(parts.stream().allMatch(job -> job.done), "Normal project materialization completes district: "
                + parts.stream().map(job -> job.kind + "=" + job.index + "/" + job.plan.size()).toList());
        VillageIndustrial.tick(world, team, settlement);
        require(settlement.era == VillageEpoch.STEAM && VillageIndustrial.civic(settlement),
                "Finished district opens the city without completing the era early");
        require(IndustrialCityGrowth.houses(world,team)==2, "Only completed residential buildings count toward the city");
        require(!IndustrialCityGrowth.finished(1449) && IndustrialCityGrowth.finished(1450)
                && IndustrialCityGrowth.finished(1500), "The city accepts the fifty-home tolerance");
        var plots=new java.util.HashSet<BlockPos>();
        for(int i=0;i<1600;i++)require(plots.add(IndustrialCityGrowth.offset(i)),"City sectors do not repeat");
        require(plots.stream().anyMatch(p->Math.abs(p.getX())*47>384),"City growth exceeds the old territorial cap");
        verifyCourt(world,team,settlement);
        VillageIndustrial.tick(world, team, settlement);
        require(state.jobs.stream().filter(job -> job.id.startsWith("industrial_district:" + village + ":")).count() == 5,
                "Repeated ticks do not duplicate the district");
        var school = base.add(23, 0, 0);
        for (int floor = 0; floor < 3; floor++) {
            require(world.getBlockState(school.add(7, 1 + floor * 5, 3)).isOf(Blocks.LECTERN),
                    "Classroom is furnished on school floor " + (floor + 1));
        }
        require(world.getBlockState(school.add(7, 15, 7)).isOf(Blocks.DEEPSLATE_TILES), "School roof spans the top floor");
        require(world.getBlockState(school.add(3, 12, 13)).isOf(Blocks.GLASS), "Third-floor windows are glazed");
        require(sign(world, school.add(7, 5, 14)).equals("ШКОЛА"), "School sign has text");
        var pupil = new VillagerEntity(EntityType.VILLAGER, world);
        pupil.setPosition(Vec3d.ofBottomCenter(school.add(7, 1, 12)));
        pupil.setOnGround(true);
        VillageIndustrial.panelNavigation(pupil);
        for (int y : new int[]{6, 11}) {
            var route = pupil.getNavigation().findPathTo(school.add(7, y, 10), 0);
            require(route != null && route.reachesTarget(), "Student can reach school floor with landing y=" + y);
        }
        var teacher = new VillagerEntity(EntityType.VILLAGER, world);
        teacher.setVillagerData(teacher.getVillagerData().withProfession(world.getRegistryManager()
                .getOrThrow(net.minecraft.registry.RegistryKeys.VILLAGER_PROFESSION)
                .getOrThrow(net.minecraft.village.VillagerProfession.LIBRARIAN)));
        teacher.setPosition(Vec3d.ofBottomCenter(school.add(7, 1, 2)));
        var student = new VillagerEntity(EntityType.VILLAGER, world);
        student.setBaby(true);
        student.setPosition(Vec3d.ofBottomCenter(school.add(3, 1, 7)));
        for (var villager : java.util.List.of(teacher, student)) {
            villager.addCommandTag("halloween_era_village_" + village);
            world.spawnEntity(villager);
        }
        require(VillageSchool.teach(world, settlement, school) == 1
                && VillageTasks.ownsMovement(teacher) && VillageTasks.ownsMovement(student), "Librarian holds a lesson for a real child");
        teacher.discard();
        student.discard();
        var shop = VillageIndustrial.shop(world, settlement);
        require(shop != null && MarketCrime.find(world, shop.add(3, 1, 4)) != null, "Completed shop supports the market interface");
        require(sign(world, shop.add(7, 5, 0)).equals("ЧЕКРЫЖИК"), "Shop sign has its brand");
        for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) {
            var roof = shop.add(x, 6, z);
            require(world.getBlockState(roof).isSolidBlock(world, roof), "Shop roof closes fully between walls and parapet");
        }
        var tower = base.add(0, 0, 3);
        require(world.getBlockState(tower.add(8, 20, 8)).isOf(Blocks.DEEPSLATE_TILES)
                || world.getBlockState(tower.add(8, 20, 8)).isOf(Blocks.POLISHED_BLACKSTONE), "Panel roof is sealed");
        require(world.getBlockState(tower.add(5, 17, 10)).getBlock() instanceof net.minecraft.block.DoorBlock,
                "Top-floor apartment has its own door");
        require(world.getBlockState(tower.add(3, 18, 2)).isOf(Blocks.GLASS)
                && world.getBlockState(tower.add(3, 18, 1)).isOf(Blocks.SMOOTH_STONE_SLAB), "Panel glass and exterior sill are placed");
        var walker = new VillagerEntity(EntityType.VILLAGER, world);
        walker.setPosition(Vec3d.ofBottomCenter(tower.add(8, 1, 3)));
        walker.setOnGround(true);
        VillageIndustrial.panelNavigation(walker);
        for (int floor = 0; floor < 5; floor++) {
            var bed = tower.add(3, 1 + 4 * floor, 5);
            var block = world.getBlockState(bed);
            require(block.getBlock() instanceof BedBlock && block.get(BedBlock.PART) == BedPart.HEAD,
                    "Usable bed at apartment floor " + (floor + 1));
            var goal = bed.south();
            var path = walker.getNavigation().findPathTo(goal, 0);
            require(path != null && path.reachesTarget(), "Villager can reach apartment floor " + (floor + 1));
        }
        StructureQualityChecks.check(world);
        verifySpas(world);
        org.slf4j.LoggerFactory.getLogger("industrial-test").info("PASS industrial district, housing threshold, courthouse, testimony, shop, school and all five floors");
    }

    private static void verifyCourt(ServerWorld world,VillageMemory.Team team,VillageEraData.Settlement settlement) {
        var square=new net.minecraft.entity.MarkerEntity(EntityType.MARKER,world);
        square.setPosition(Vec3d.ofBottomCenter(team.anchor));
        square.addCommandTag("halloween_square");
        world.spawnEntity(square);
        require(VillageArena.closed(world,square),"Organized fistfights end when the industrial district opens");
        var accused=new VillagerEntity(EntityType.VILLAGER,world);
        require(!VillageTrials.claimCourt(world,square,accused),"A civic trial waits for its courthouse");
        var hall=new BlockPos(336,185,300);
        for(int x=21;x<=22;x++)for(int z=18;z<=19;z++)world.getChunk(x,z);
        settlement.xp.put("courthouse_x",hall.getX());
        settlement.xp.put("courthouse_y",hall.getY());
        settlement.xp.put("courthouse_z",hall.getZ());
        var projects=VillageProjects.get(world);
        projects.add(world,VillageCourthouse.id(team.village),team.village,"courthouse",VillageCourthouse.plan(hall));
        for(int i=0;i<1200&&!projects.complete(team.village,"courthouse");i++)VillageProjects.materialize(world,team.village,4096);
        require(projects.complete(team.village,"courthouse"),"Courthouse is built through the project queue");
        require(VillageTrials.claimCourt(world,square,accused),"Finished indoor court accepts a case");
        var judge=new VillagerEntity(EntityType.VILLAGER,world);
        judge.setPosition(Vec3d.ofBottomCenter(hall.add(11,1,1)));
        judge.setOnGround(true);
        VillageIndustrial.panelNavigation(judge);
        var route=judge.getNavigation().findPathTo(VillageCourthouse.judge(hall),0);
        require(route!=null&&route.reachesTarget(),"Judge can walk from the entrance to the raised desk");
        judge.setVillagerData(judge.getVillagerData().withProfession(RoyalProfession.DEPUTY_ENTRY));
        judge.setPosition(Vec3d.ofBottomCenter(VillageCourthouse.judge(hall)));
        world.spawnEntity(judge);
        require(VillageCourthouse.judgePresent(world,hall),"Court detects a judge in position");
        judge.discard();
        var cage=VillageTrials.cagePosition(world,square);
        require(cage.equals(VillageCourthouse.cage(hall))&&VillageTrials.cageReady(world,cage),"Hearing uses the permanent indoor cage");
        var prosecution=new VillagerEntity(EntityType.VILLAGER,world);
        var defense=new VillagerEntity(EntityType.VILLAGER,world);
        prosecution.setPosition(Vec3d.ofBottomCenter(VillageCourthouse.witness(hall,0)));
        defense.setPosition(Vec3d.ofBottomCenter(VillageCourthouse.witness(hall,1)));
        world.spawnEntity(prosecution);world.spawnEntity(defense);
        accused.addCommandTag("halloween_court_evidence_p:"+prosecution.getUuidAsString());
        accused.addCommandTag("halloween_court_evidence_d:"+defense.getUuidAsString());
        CourtTestimony.speak(world,square,accused,190,"нападение");
        require(!CourtTestimony.acquit(accused),"Prosecution testimony counts toward conviction");
        CourtTestimony.speak(world,square,accused,125,"нападение");
        require(CourtTestimony.acquit(accused),"Defense testimony can change the verdict");
        VillageTrials.removeCage(world,cage);
        require(VillageTrials.cageReady(world,cage),"Release leaves the permanent cage in place");
        prosecution.discard();defense.discard();square.discard();
    }

    private static void verifySpas(ServerWorld world) {
        long village = 405024L;
        var base = new BlockPos(400, 160, 352);
        var team = new VillageMemory.Team(village, base, 0);
        var memory = world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        memory.teams.add(team);
        memory.houses.add(new VillageMemory.House(base, base.add(64, 99, 87), java.util.List.of(), world.getTime(),
                java.util.List.of(), 0, true, java.util.List.of(), java.util.List.of(), java.util.List.of(),
                "structure", "/royal_castle:industrial-test", java.util.List.of()));
        var template = VillageEraPlans.template(world, "cologne_cathedral");
        var layout = VillageSpas.layout(base);
        var spire = template.blocks().stream().filter(r -> r.pos().getY() >= 70 && !r.state().isAir()
                && !layout.containsKey(base.add(r.pos()))).findFirst().orElseThrow();
        var high = base.add(spire.pos());
        var treasury = base.add(30, -2, 30);
        var palace = base.add(48, 8, 64);
        for (var pos : BlockPos.iterate(base.add(1, 0, 4), base.add(64, 0, 84)))
            world.setBlockState(pos, Blocks.STONE_BRICKS.getDefaultState());
        world.setBlockState(high, spire.state());
        world.setBlockState(treasury, Blocks.CHEST.getDefaultState());
        world.setBlockState(palace, Blocks.BOOKSHELF.getDefaultState());
        var projects = VillageProjects.get(world);
        var oldWall = base.add(4, 3, 30);
        projects.add(world, "industrial_kremlin_v2:" + village, village, "cathedral",
                java.util.Map.of(oldWall, Blocks.BRICKS.getDefaultState()));
        for (int i = 0; i < 16; i++) VillageProjects.materialize(world, village, 4);
        require(world.getBlockState(oldWall).isOf(Blocks.BRICKS), "Legacy city wall was installed");
        for (int i = 0; i < 800 && !projects.exists(VillageSpas.id(village)); i++) VillageSpas.advance(world, team);
        require(projects.exists(VillageSpas.id(village)), "Spas survey enqueues a real conversion");
        for (int i = 0; i < 1200 && !VillageSpas.advance(world, team); i++) VillageProjects.materialize(world, village, 4096);
        require(VillageSpas.advance(world, team), "Spas project finishes");
        require(world.isAir(high) && world.isAir(oldWall), "Cathedral spire and prior Kremlin wall are removed");
        var wall = base.add(9, 6, 55);
        var red = world.getBlockState(wall);
        require((red.isOf(Blocks.BRICKS) || red.isOf(Blocks.RED_TERRACOTTA) || red.isOf(Blocks.RED_NETHER_BRICKS)),
                "Red brick nave is built: " + red + ", layout=" + layout.get(wall));
        require(world.isAir(base.add(32, 1, 13)) && world.isAir(base.add(32, 3, 23)), "Entrance reaches the main hall");
        require(world.getBlockState(base.add(32, SpasArchitecture.HEIGHT, 49)).isOf(Blocks.GOLD_BLOCK),
                "Central cross is built at full height");
        require(world.getBlockState(base.add(32, 28, 44)).isOf(Blocks.DARK_PRISMARINE), "Main nave roof is sealed");
        var visitor = new VillagerEntity(EntityType.VILLAGER, world);
        visitor.setPosition(Vec3d.ofBottomCenter(base.add(32, 1, 11)));
        visitor.setOnGround(true);
        SpasInteriors.navigation(visitor);
        var entry = visitor.getNavigation().findPathTo(base.add(32, 1, 39), 0);
        require(entry != null && entry.reachesTarget(), "Village pathfinding reaches the main hall through the west entrance");
        for (int y : new int[]{8, 15, 22}) {
            var gallery = visitor.getNavigation().findPathTo(base.add(18, y, 39), 0);
            require(gallery != null && gallery.reachesTarget(), "Visitor can reach gallery level y=" + y
                    + " last=" + (gallery == null ? "none" : gallery.getNodePos(gallery.getLength()-1).subtract(base))
                    + " nodes=" + (gallery == null ? 0 : gallery.getLength()));
        }
        var office = SpasInteriors.office(world, village);
        require(office != null && world.getBlockState(office.west(2)).isOf(Blocks.LECTERN), "President's desk and standing spot exist");
        var route = visitor.getNavigation().findPathTo(office, 0);
        require(route != null && route.reachesTarget(), "President can walk from the entrance to the office: "
                + (route == null ? "none" : route.getNodePos(route.getLength()-1).subtract(base)));
        var government = VillageEraData.get(world).settlement(team);
        var leader = new VillagerEntity(EntityType.VILLAGER, world);
        leader.setPosition(Vec3d.ofBottomCenter(base.add(32, 1, 12)));
        leader.setVillagerData(leader.getVillagerData().withProfession(net.minecraft.registry.Registries.VILLAGER_PROFESSION
                .getEntry(RoyalProfession.KEY.getValue()).orElseThrow()));
        leader.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(RoyalProfession.CROWN));
        var identity = leader.getUuid();
        require(!RoyalProfession.updateOffice(world, leader), "Earlier eras retain a monarch");
        government.era = VillageEpoch.INDUSTRIAL;
        require(RoyalProfession.updateOffice(world, leader) && RoyalProfession.isPresident(leader)
                && RoyalProfession.isKing(leader) && leader.getUuid().equals(identity)
                && leader.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isEmpty(),
                "IV era makes the same protected leader president and removes the crown");
        leader.setPosition(Vec3d.ofBottomCenter(office));
        leader.setYaw(90);
        leader.setHeadYaw(90);
        leader.setBodyYaw(90);
        leader.setAiDisabled(true);
        world.spawnEntity(leader);

        require(world.getBlockState(treasury).isOf(Blocks.CHEST) && world.getBlockState(palace).isOf(Blocks.BOOKSHELF),
                "Original underground and lower palace fixtures survive");
        org.slf4j.LoggerFactory.getLogger("industrial-test").info("PASS Spas galleries, office access, IV-era president and preserved palace fixtures");
    }

    private static String sign(ServerWorld world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof net.minecraft.block.entity.SignBlockEntity board
                ? board.getFrontText().getMessage(0, false).getString() : "";
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private IndustrialChecks() {}
}
