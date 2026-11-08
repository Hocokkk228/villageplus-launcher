package dev.halloween;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** The first industrial district shares a surveyed foundation and survives through the normal project queue. */
final class VillageIndustrial {
    private static final int WIDTH = 38, DEPTH = 40;
    private static final long WAIT = 50 * 24000L;
    private static final String START = "industrial_requested";
    private static final String X = "industrial_x", Y = "industrial_y", Z = "industrial_z";
    private static final String[] PARTS = {"road", "panel_a", "panel_b", "school", "shop"};
    private static final String DESIGN = "industrial_district_design";
    private static final String SEARCH = "industrial_search";

    static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof net.minecraft.entity.passive.VillagerEntity villager
                    && villager.getCommandTags().contains("halloween_panel_resident")) panelNavigation(villager);
        });
    }

    static void panelNavigation(net.minecraft.entity.passive.VillagerEntity villager) {
        villager.getNavigation().setMaxFollowRange(96);
        villager.getNavigation().setCanOpenDoors(true);
    }

    static boolean start(VillageEraData.Settlement settlement) {
        if (settlement.era != VillageEpoch.STEAM) return false;
        settlement.xp.put(START, 1);
        return true;
    }

    static boolean civic(VillageEraData.Settlement settlement) {
        return settlement!=null&&(settlement.era>=VillageEpoch.INDUSTRIAL||settlement.xp.getOrDefault("industrial_city_open",0)>0);
    }

    static boolean pending(VillageEraData.Settlement settlement) {
        return settlement.era == VillageEpoch.STEAM && settlement.xp.getOrDefault(START, 0) != 0;
    }

    static String id(long village, String part) { return "industrial_district:" + village + ":" + part; }

    static BlockPos shop(ServerWorld world, VillageEraData.Settlement settlement) {
        if (!settlement.xp.containsKey(X) || !done(VillageProjects.get(world), settlement.village, "shop")) return null;
        return origin(settlement).add(23, 0, 19);
    }

    static BlockPos schoolSite(ServerWorld world, VillageEraData.Settlement settlement) {
        if (!settlement.xp.containsKey(X) || !done(VillageProjects.get(world), settlement.village, "school")) return null;
        return origin(settlement).add(23, 0, 0);
    }

    static void tick(ServerWorld world, VillageMemory.Team team, VillageEraData.Settlement settlement) {
        if (settlement.era == VillageEpoch.INDUSTRIAL) {
            VillageSpas.advance(world, team);
            upgradeDistrict(world, settlement);
            VillageCourthouse.advance(world, team, settlement);
            var template=VillageEraPlans.template(world,"steam_apartments");
            if(template!=null)IndustrialCityGrowth.advance(world,team,settlement,template,IndustrialCityGrowth.houses(world,team));
            return;
        }
        if (settlement.era != VillageEpoch.STEAM) return;
        if (!pending(settlement)) {
            if (settlement.eraAt < 0 || world.getTime() - settlement.eraAt < WAIT) return;
            start(settlement);
            VillageEraData.get(world).markDirty();
        }
        boolean landmarkDone = VillageSpas.advance(world, team);
        var template = VillageEraPlans.template(world, "steam_apartments");
        if (template == null) return;
        var projects = VillageProjects.get(world);
        if (!settlement.xp.containsKey(X)) {
            var base = survey(world, team, settlement);
            if (base == null) return;
            var plans = plans(base, template);
            if (!safe(world, plans)) return;
            settlement.xp.put(DESIGN, 2);
            settlement.xp.put(X, base.getX());
            settlement.xp.put(Y, base.getY());
            settlement.xp.put(Z, base.getZ());
            VillageEraData.get(world).markDirty();
            queue(world, projects, settlement.village, plans);
        } else if (!queued(projects, settlement.village)) {
            var base = origin(settlement);
            if (loaded(world, base.getX(), base.getZ())) queue(world, projects, settlement.village, plans(base, template));
        }
        for (String part : PARTS) {
            if (!done(projects, settlement.village, part)) return;
        }
        if (!landmarkDone) return;
        if (settlement.xp.putIfAbsent("industrial_city_open",1)==null) {
            VillageEraData.get(world).markDirty();
            for (var player:world.getPlayers()) if(player.getBlockPos().isWithinDistance(team.anchor,384))
                player.sendMessage(Text.literal("§6"+settlement.name+": первый городской район открыт. Город продолжает строиться."),false);
        }
        VillageCourthouse.advance(world,team,settlement);
        long houses=IndustrialCityGrowth.houses(world,team);
        if(!IndustrialCityGrowth.finished(houses)){
            IndustrialCityGrowth.advance(world,team,settlement,template,houses);
            return;
        }
        if(!IndustrialPlants.ready(world,team.village)
                ||!projects.jobs.stream().anyMatch(p->p.id.equals(VillageCourthouse.id(team.village))&&p.done)
                ||IndustrialPlants.get(world).plants.stream().filter(p->p.village==team.village)
                    .anyMatch(p->CityStreets.ready(world,p)==null))return;
        settlement.era = VillageEpoch.INDUSTRIAL;
        settlement.eraAt = world.getTime();
        VillageEraData.get(world).markDirty();
        for (var player : world.getPlayers()) {
            if (player.getBlockPos().isWithinDistance(team.anchor, 384))
                player.sendMessage(Text.literal("§6" + settlement.name + ": завершён переход в индустриальную эпоху!"), false);
        }
    }

    private static void upgradeDistrict(ServerWorld world, VillageEraData.Settlement settlement) {
        if (settlement.xp.getOrDefault(DESIGN, 0) >= 2 || !settlement.xp.containsKey(X)) return;
        var jobs = VillageProjects.get(world);
        for (String part : PARTS) if (!done(jobs, settlement.village, part)) return;
        var base = origin(settlement);
        if (!loaded(world, base.getX(), base.getZ())) return;
        var template = VillageEraPlans.template(world, "steam_apartments");
        if (template == null) return;
        var updated = plans(base, template);
        for (String part : new String[]{"panel_a", "panel_b", "school", "shop"}) {
            String oldId = id(settlement.village, part);
            String newId = oldId + ":facade_v2";
            if (jobs.exists(newId)) continue;
            var oldJob = jobs.jobs.stream().filter(p -> p.id.equals(oldId)).findFirst().orElseThrow();
            var changes = new LinkedHashMap<BlockPos, BlockState>();
            var previous = new java.util.HashMap<BlockPos, BlockState>();
            for (var change : oldJob.plan) previous.put(change.pos(), change.after());
            for (var entry : updated.get(part).entrySet()) {
                var pos = entry.getKey();
                var desired = entry.getValue();
                var actual = world.getBlockState(pos);
                if (VillageConnections.matches(world, pos, desired, actual) || actual.hasBlockEntity()) continue;
                var old = previous.get(pos);
                if (old != null && VillageConnections.matches(world, pos, old, actual)
                        || actual.isAir() || actual.isReplaceable()) changes.put(pos, desired);
            }
            jobs.add(world, newId, settlement.village, "industrial_renovation", changes);
            if (!changes.isEmpty() && !jobs.exists(newId)) return;
        }
        settlement.xp.put(DESIGN, 2);
        VillageEraData.get(world).markDirty();
    }

    static void finishProject(ServerWorld world, VillageProjects.Project job) {
        if (!job.id.startsWith("industrial_district:")) return;
        var settlement = VillageEraData.get(world).settlements.get(job.village);
        if (settlement == null || !settlement.xp.containsKey(X)) return;
        var base = origin(settlement);
        String part = job.id.substring(id(job.village, "").length()).replace(":facade_v2", "");
        switch (part) {
            case "school" -> label(world, base.add(30, 5, 14), "ШКОЛА", "№ " + (1 + Math.floorMod(job.village, 99)));
            case "shop" -> label(world, base.add(30, 5, 19),
                    job.village % 2 == 0 ? "ВОСЬМЁРОЧКА" : "ЧЕКРЫЖИК", "ПРОДУКТЫ");
            case "panel_a" -> label(world, base.add(9, 2, 4), "ДОМ 1", "ПОДЪЕЗД 1");
            case "panel_b" -> label(world, base.add(9, 2, 23), "ДОМ 2", "ПОДЪЕЗД 1");
        }
    }

    private static void label(ServerWorld world, BlockPos pos, String top, String bottom) {
        if (!(world.getBlockEntity(pos) instanceof net.minecraft.block.entity.SignBlockEntity sign) || sign.isWaxed()) return;
        sign.setText(sign.getFrontText().withColor(net.minecraft.util.DyeColor.WHITE)
                .withMessage(0, Text.literal(top)).withMessage(1, Text.literal(bottom)), true);
        sign.setWaxed(true);
    }

    private static BlockPos origin(VillageEraData.Settlement settlement) {
        return new BlockPos(settlement.xp.get(X), settlement.xp.get(Y), settlement.xp.get(Z));
    }

    private static boolean done(VillageProjects projects, long village, String part) {
        return projects.jobs.stream().anyMatch(job -> job.id.equals(id(village, part)) && job.done);
    }

    private static boolean queued(VillageProjects projects, long village) {
        for (String part : PARTS)
            if (!projects.exists(id(village, part))) return false;
        return true;
    }

    private static BlockPos survey(ServerWorld world, VillageMemory.Team team, VillageEraData.Settlement settlement) {
        int n = settlement.xp.getOrDefault(SEARCH, 0);
        int radius = 42 + Math.floorMod(n, 18) * 8;
        double angle = n * 2.399963229728653;
        int x = team.anchor.getX() + (int)(Math.cos(angle) * radius) - WIDTH / 2;
        int z = team.anchor.getZ() + (int)(Math.sin(angle) * radius) - DEPTH / 2;
        var memory = world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        boolean occupied = memory.houses.stream().filter(VillageBuildingSites::reserves).anyMatch(h -> overlaps(h.min, h.max, x, z))
                || VillageProjects.get(world).jobs.stream().filter(j -> !j.done && VillageBuildingSites.building(j.kind))
                .anyMatch(j -> overlaps(j.min, j.max, x, z));
        if (!occupied && !loaded(world, x, z)) return null;
        settlement.xp.put(SEARCH, n + 1);
        VillageEraData.get(world).markDirty();
        if (occupied) return null;
        int surface = VillageEraPlans.levelSite(world, x, z, WIDTH, DEPTH, 4);
        return surface == Integer.MIN_VALUE || surface + 26 >= world.getTopYInclusive()
                ? null : new BlockPos(x, surface - 1, z);
    }

    private static boolean loaded(ServerWorld world, int x, int z) {
        boolean loaded = true;
        for (int cx = x >> 4; cx <= (x + WIDTH - 1) >> 4; cx++) {
            for (int cz = z >> 4; cz <= (z + DEPTH - 1) >> 4; cz++) {
                if (!VillageProjects.keepLoaded(world, new BlockPos(cx << 4, 64, cz << 4))) loaded = false;
            }
        }
        return loaded;
    }

    private static boolean overlaps(BlockPos min, BlockPos max, int x, int z) {
        return min.getX() <= x + WIDTH && max.getX() >= x - 1 && min.getZ() <= z + DEPTH && max.getZ() >= z - 1;
    }

    static boolean safe(ServerWorld world, Map<String, Map<BlockPos, BlockState>> plans) {
        for (var plan : plans.values()) for (var pos : plan.keySet()) {
            var old = world.getBlockState(pos);
            if (!world.getWorldBorder().contains(pos) || old.hasBlockEntity() || !old.getFluidState().isEmpty()) return false;
            if (!old.isAir() && !old.isReplaceable() && !VillageProjects.terrain(old)
                    && !old.isIn(BlockTags.LOGS) && !old.isIn(BlockTags.LEAVES)) return false;
        }
        return true;
    }

    private static void queue(ServerWorld world, VillageProjects projects, long village,
                              Map<String, Map<BlockPos, BlockState>> plans) {
        projects.add(world, id(village, "road"), village, "industrial_road", plans.get("road"));
        projects.add(world, id(village, "panel_a"), village, "house", plans.get("panel_a"));
        projects.add(world, id(village, "panel_b"), village, "house", plans.get("panel_b"));
        projects.add(world, id(village, "school"), village, "industrial_school", plans.get("school"));
        projects.add(world, id(village, "shop"), village, "industrial_shop", plans.get("shop"));
    }

    static Map<String, Map<BlockPos, BlockState>> plans(BlockPos base, VillageEraPlans.Template template) {
        var first = panel(base.add(0, 0, 3), template);
        var second = panel(base.add(0, 0, 22), template);
        var school = IndustrialArchitecture.school(base.add(23, 0, 0));
        var shop = IndustrialArchitecture.shop(base.add(23, 0, 19));
        var road = new LinkedHashMap<BlockPos, BlockState>();
        for (int x = 0; x < WIDTH; x++) for (int z = 0; z < DEPTH; z++) {
            boolean panelPlot = x < 17 && (z >= 3 && z < 20 || z >= 22 && z < 39);
            boolean schoolPlot = x >= 23 && z < 15;
            boolean shopPlot = x >= 23 && z >= 19 && z < 34;
            var floor = base.add(x, 0, z);
            for (int down = 1; down <= 4; down++) road.put(floor.down(down), Blocks.STONE_BRICKS.getDefaultState());
            if (panelPlot || schoolPlot || shopPlot) continue;
            boolean carriageway = x >= 18 && x <= 21 || z >= 16 && z <= 17;
            road.put(floor, (carriageway ? Blocks.GRAY_CONCRETE : Blocks.SMOOTH_STONE).getDefaultState());
            for (int y = 1; y <= 4; y++) road.put(floor.up(y), Blocks.AIR.getDefaultState());
        }
        return Map.of("road", road, "panel_a", first, "panel_b", second, "school", school, "shop", shop);
    }

    static Map<BlockPos, BlockState> panel(BlockPos base, VillageEraPlans.Template template) {
        var blocks = new LinkedHashMap<BlockPos, BlockState>();
        for (var block : template.blocks()) {
            int y = block.pos().getY();
            if (y < 20) {
                var state = block.state();
                if (state.isOf(Blocks.LIGHT_GRAY_CONCRETE)) {
                    int seam = block.pos().getX() + block.pos().getZ();
                    state = (y % 4 == 0 || seam % 5 == 0 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.WHITE_CONCRETE).getDefaultState();
                }
                blocks.put(base.add(block.pos()), state);
            } else if (y >= 36) {
                blocks.put(base.add(block.pos().down(16)), block.state());
            }
        }
        // Alternating lanes leave the landing open and give the previous flight full headroom.
        for (int x = 6; x <= 10; x++) for (int z = 4; z <= 9; z++) for (int y = 1; y <= 18; y++) {
            blocks.put(base.add(x, y, z), (y % 4 == 0 ? Blocks.SPRUCE_PLANKS : Blocks.AIR).getDefaultState());
        }
        for (int floor = 0; floor < 4; floor++) {
            boolean south = floor % 2 == 0;
            int lane = south ? 6 : 9;
            var stair = Blocks.STONE_BRICK_STAIRS.getDefaultState().with(net.minecraft.block.StairsBlock.FACING,
                    south ? Direction.SOUTH : Direction.NORTH);
            for (int x = lane; x <= lane + 1; x++) for (int step = 0; step < 4; step++) {
                var pos = base.add(x, floor * 4 + 1 + step, south ? 5 + step : 8 - step);
                blocks.put(pos, stair);
                blocks.put(pos.up(), Blocks.AIR.getDefaultState());
                blocks.put(pos.up(2), Blocks.AIR.getDefaultState());
                blocks.put(pos.up(3), Blocks.AIR.getDefaultState());
                for (int below = 1; below <= step; below++)
                    blocks.put(pos.down(below), Blocks.STONE_BRICKS.getDefaultState());
            }
        }
        for (int floor = 0; floor < 5; floor++)
            blocks.put(base.add(8, 3 + floor * 4, 4), Blocks.SEA_LANTERN.getDefaultState());
        IndustrialArchitecture.apartments(blocks, base);
        return blocks;
    }

    private VillageIndustrial() {}
}
