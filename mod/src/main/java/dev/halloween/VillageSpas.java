package dev.halloween;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Replaces the cathedral skyline while leaving the lower palace, court markers and prison intact. */
final class VillageSpas {
    private static final class Survey {
        final BlockPos base;
        final VillageEraPlans.Template cathedral;
        final Map<BlockPos, BlockState> layout;
        final Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
        final java.util.List<VillageCivilWorks.Change> legacy;
        int cursor, legacyCursor;

        Survey(BlockPos base, VillageEraPlans.Template cathedral, java.util.List<VillageCivilWorks.Change> legacy) {
            this.base = base;
            this.cathedral = cathedral;
            layout = layout(base);
            this.legacy = legacy;
        }
    }

    private static final Map<ServerWorld, Map<Long, Survey>> SURVEYS = new WeakHashMap<>();

    static String id(long village) { return "industrial_spas:" + village; }

    static boolean advance(ServerWorld world, VillageMemory.Team team) {
        var projects = VillageProjects.get(world);
        var queued = projects.jobs.stream().filter(job -> job.id.equals(id(team.village))).findFirst().orElse(null);
        if (queued != null && !queued.done) return false;
        var memory = world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        var castle = memory.houses.stream().filter(h -> h.recordKey.contains("/royal_castle:")
                && VillageEras.team(world, h.min) == team).findFirst().orElse(null);
        if (castle == null) return true;
        var base = VillageRelocations.bounds(castle)[0];
        if (queued != null) return SpasInteriors.advance(world, team, base);
        if (projects.jobs.stream().anyMatch(job -> job.village == team.village && job.kind.equals("cathedral") && !job.done)) return false;
        if (base.getY() + SpasArchitecture.HEIGHT > world.getTopYInclusive()) return false;
        var template = VillageEraPlans.template(world, "cologne_cathedral");
        if (template == null) return false;
        var surveys = SURVEYS.computeIfAbsent(world, ignored -> new HashMap<>());
        var survey = surveys.computeIfAbsent(team.village,
                ignored -> new Survey(base, template, projects.jobs.stream()
                        .filter(job -> job.id.equals("industrial_kremlin:" + team.village) || job.id.equals("industrial_kremlin_v2:" + team.village))
                        .flatMap(job -> job.plan.stream()).toList()));
        long deadline = System.nanoTime() + 1_000_000;
        for (int count = 0; count < 1536 && survey.cursor < template.blocks().size() && System.nanoTime() < deadline; count++) {
            var record = survey.cathedral.blocks().get(survey.cursor);
            var local = record.pos();
            if (local.getY() < 0 || local.getY() == 0 && !survey.layout.containsKey(survey.base.add(local))
                    || local.getZ() < 7 || record.state().isAir()
                    || SpasArchitecture.protectedWing(local.getX(), local.getY(), local.getZ())) {
                survey.cursor++;
                continue;
            }
            var position = survey.base.add(record.pos());
            if (!VillageProjects.keepLoaded(world, position)) return false;
            survey.cursor++;
            var replacement = survey.layout.remove(position);
            if (replacement == null) replacement = Blocks.AIR.getDefaultState();
            add(world, projects, survey.changes, position, replacement, record.state(), replacement.isAir());
        }
        if (survey.cursor < template.blocks().size()) return false;

        while (survey.legacyCursor < survey.legacy.size() && System.nanoTime() < deadline) {
            var old = survey.legacy.get(survey.legacyCursor);
            var pos = old.pos();
            if (!VillageProjects.keepLoaded(world, pos)) return false;
            survey.legacyCursor++;
            var local = pos.subtract(survey.base);
            if (old.after().isAir() || SpasArchitecture.protectedWing(local.getX(), local.getY(), local.getZ())) continue;
            if (!world.getBlockState(pos).isOf(old.after().getBlock())) continue;
            var replacement = survey.layout.remove(pos);
            if (replacement == null && survey.changes.containsKey(pos)) continue;
            if (replacement == null) replacement = Blocks.AIR.getDefaultState();
            add(world, projects, survey.changes, pos, replacement, old.after(), replacement.isAir());
        }
        if (survey.legacyCursor < survey.legacy.size()) return false;

        // A separate, bounded pass over the new footprint. Resume at the same position if a chunk unloads.
        var remaining = survey.layout.entrySet().iterator();
        while (remaining.hasNext() && System.nanoTime() < deadline) {
            var entry = remaining.next();
            if (!VillageProjects.keepLoaded(world, entry.getKey())) return false;
            var local = entry.getKey().subtract(survey.base);
            if (SpasArchitecture.protectedWing(local.getX(), local.getY(), local.getZ())) {
                remaining.remove();
                continue;
            }
            add(world, projects, survey.changes, entry.getKey(), entry.getValue(), null, false);
            remaining.remove();
        }
        if (!survey.layout.isEmpty()) return false;
        if (survey.changes.isEmpty()) {
            surveys.remove(team.village);
            return true;
        }
        var chunks = new java.util.HashSet<Long>();
        boolean loaded = true;
        for (var position : survey.changes.keySet()) {
            if (chunks.add(net.minecraft.util.math.ChunkPos.toLong(position.getX() >> 4, position.getZ() >> 4))
                    && !VillageProjects.keepLoaded(world, position)) loaded = false;
        }
        if (!loaded) return false;
        projects.add(world, id(team.village), team.village, "cathedral", survey.changes);
        if (projects.exists(id(team.village))) surveys.remove(team.village);
        return false;
    }

    private static void add(ServerWorld world, VillageProjects projects, Map<BlockPos, BlockState> changes,
                            BlockPos pos, BlockState desired, BlockState original, boolean demolition) {
        if (!world.getWorldBorder().contains(pos) || pos.getY() > world.getTopYInclusive() || VillagePrisons.managed(world, pos)) return;
        var old = world.getBlockState(pos);
        if (old.hasBlockEntity() || VillageConnections.matches(world, pos, desired, old)) return;
        var owned = projects.expected.get(pos);
        boolean recorded = original != null && (old.isOf(original.getBlock()) || owned != null && old.isOf(owned.getBlock()));
        if (recorded || !demolition && old.getFluidState().isEmpty() && (old.isAir() || old.isReplaceable()))
            changes.put(pos, desired);
    }

    static Map<BlockPos, BlockState> layout(BlockPos base) {
        return SpasArchitecture.build(base);
    }

    private VillageSpas() {}
}
