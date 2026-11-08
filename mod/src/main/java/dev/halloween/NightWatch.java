package dev.halloween;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

final class NightWatch {
    private record Alarm(BlockPos position, long ends, long again) {}
    private static final Map<VillageMemory.Team, Alarm> ALARMS = new WeakHashMap<>();

    static BlockPos response(ServerWorld world, VillageMemory.Team team) {
        var alarm = ALARMS.get(team);
        return alarm != null && alarm.ends > world.getTime() ? alarm.position : null;
    }

    private static boolean night(ServerWorld world) {
        long hour = Math.floorMod(world.getTimeOfDay(), 24000);
        return hour >= 12500 && hour < 23500;
    }

    static BlockPos post(ServerWorld world, VillageMemory.Team team, BlockPos guard) {
        if (!night(world)) return null;
        int radius = VillageEraData.get(world).settlement(team).xp.getOrDefault("perimeter_radius_v1", 0);
        if (radius == 0) return null;
        BlockPos best = null;
        double bestDistance = 96 * 96;
        for (var side : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            var edge = team.anchor.offset(side, radius);
            var along = side.rotateYClockwise();
            int projection = (guard.getX() - edge.getX()) * along.getOffsetX()
                    + (guard.getZ() - edge.getZ()) * along.getOffsetZ();
            for (int offset = -24; offset <= 24; offset += 2) {
                var column = edge.offset(along, Math.clamp(projection + offset, -radius + 2, radius - 2));
                if (!world.isChunkLoaded(column)) continue;
                var p = column.withY(world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        column.getX(), column.getZ()));
                var floor = world.getBlockState(p.down());
                if (!floor.isOf(net.minecraft.block.Blocks.DIRT_PATH) && !floor.isOf(net.minecraft.block.Blocks.STONE_BRICKS)
                        && !floor.isOf(net.minecraft.block.Blocks.ANDESITE)) continue;
                if (world.getBlockState(p.down(2)).isOf(net.minecraft.block.Blocks.STONE_BRICKS)
                        && world.getBlockState(p.down(3)).isOf(net.minecraft.block.Blocks.STONE_BRICKS)) continue;
                if (!world.getBlockState(p).isAir() || !world.getBlockState(p.up()).isAir()) continue;
                double distance = p.getSquaredDistance(guard);
                if (distance < bestDistance) { best = p; bestDistance = distance; }
            }
        }
        return best;
    }

    static void tick(ServerWorld world, VillageMemory.Team team, List<VillagerEntity> residents) {
        if (!night(world) || world.getTime() % 100 != 0) return;
        var prior = ALARMS.get(team);
        if (prior != null && prior.again > world.getTime()) return;
        var settlement = VillageEraData.get(world).settlement(team);
        int radius = settlement.xp.getOrDefault("perimeter_radius_v1", 0);
        if (radius == 0) return;
        for (var guard : residents) {
            if (!VillageDefense.isGuard(guard) || VillageDefense.escorting(guard)
                    || !onBoundary(team.anchor, guard.getBlockPos(), radius, 14)) continue;
            var targets = world.getEntitiesByClass(HostileEntity.class, new Box(guard.getBlockPos()).expand(21, 5, 21),
                    mob -> mob.isAlive() && guard.canSee(mob) && onBoundary(team.anchor, mob.getBlockPos(), radius, 12));
            if (targets.isEmpty()) continue;
            var target = targets.getFirst().getBlockPos();
            alarm(world, team, residents, target);
            return;
        }
    }

    static boolean onBoundary(BlockPos center, BlockPos pos, int radius, int width) {
        int extent = Math.max(Math.abs(center.getX() - pos.getX()), Math.abs(center.getZ() - pos.getZ()));
        return Math.abs(extent - radius) <= width;
    }

    static void alarm(ServerWorld world, VillageMemory.Team team, List<VillagerEntity> residents, BlockPos position) {
        ALARMS.put(team, new Alarm(position.toImmutable(), world.getTime() + 480, world.getTime() + 2400));
        var bells = world.getEntitiesByClass(MarkerEntity.class, new Box(team.anchor).expand(64, 20, 64),
                bell -> bell.getCommandTags().contains("halloween_bell"));
        var bell = bells.isEmpty() ? position : bells.getFirst().getBlockPos();
        world.playSound(null, bell, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 2.6f, .75f);
        for (var player : world.getPlayers()) {
            if (player.squaredDistanceTo(Vec3d.ofCenter(team.anchor)) <= 200 * 200)
                player.sendMessage(Text.literal("§cНочная тревога! Нападение у границы поселения."), false);
        }
        for (var villager : residents) {
            if (!VillageDefense.isGuard(villager) && !RoyalProfession.isKing(villager) && !villager.hasVehicle()
                    && !villager.getCommandTags().contains("halloween_bandit") && !VillageTrials.busy(villager)) {
                VillageTrials.shelter(world, villager, 480);
            }
        }
    }

    private NightWatch() {}
}
