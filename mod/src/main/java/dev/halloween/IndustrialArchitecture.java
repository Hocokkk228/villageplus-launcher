package dev.halloween;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Municipal buildings share muted precast panels, recessed windows and simple street fittings. */
final class IndustrialArchitecture {
    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    static void apartments(Map<BlockPos, BlockState> plan, BlockPos base) {
        for (int y = 1; y < 20; y++) for (int along = 2; along <= 14; along++) {
            facade(plan, base.add(along, y, 2), Direction.NORTH, y, along);
            facade(plan, base.add(along, y, 14), Direction.SOUTH, y, along);
            facade(plan, base.add(2, y, along), Direction.WEST, y, along);
            facade(plan, base.add(14, y, along), Direction.EAST, y, along);
        }
        for (int y = 4; y < 20; y += 4) for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) {
            if (x > 2 && x < 14 && z > 2 && z < 14) continue;
            var pos = base.add(x, y, z);
            var old = plan.get(pos);
            if (old != null && old.isOf(Blocks.SPRUCE_PLANKS)) plan.put(pos, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
        }
        for (int floor = 0; floor < 5; floor++) {
            int y = 1 + floor * 4;
            for (int wall : new int[]{5, 11}) for (int z = 3; z <= 13; z++) for (int h = 0; h < 3; h++) {
                plan.put(base.add(wall, y + h, z), (h == 0 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.SMOOTH_SANDSTONE).getDefaultState());
            }
            door(plan, base.add(5, y, 10), Direction.WEST);
            door(plan, base.add(11, y, 10), Direction.EAST);
            for (int x : new int[]{3, 13}) {
                plan.put(base.add(x, y, 12), Blocks.BARREL.getDefaultState());
                plan.put(base.add(x, y + 1, 12), Blocks.BOOKSHELF.getDefaultState());
                plan.put(base.add(x, y + 2, 9), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
                plan.put(base.add(x, y, 8), Blocks.BROWN_CARPET.getDefaultState());
            }
        }
        for (int x = 2; x <= 14; x++) for (int z = 2; z <= 14; z++) {
            var roof = base.add(x, 20, z);
            plan.put(roof, (weather(x, 20, z) == 0 ? Blocks.POLISHED_BLACKSTONE : Blocks.DEEPSLATE_TILES).getDefaultState());
        }
        door(plan, base.add(8, 1, 2), Direction.NORTH);
        for (int x = 6; x <= 10; x++) for (int z = 0; z <= 2; z++)
            plan.put(base.add(x, 3, z), Blocks.POLISHED_ANDESITE_SLAB.getDefaultState());
        plan.put(base.add(9, 2, 2), Blocks.POLISHED_ANDESITE.getDefaultState());
        sign(plan, base.add(9, 2, 1), Direction.NORTH);
    }

    private static void facade(Map<BlockPos, BlockState> plan, BlockPos pos, Direction outside, int y, int along) {
        var old = plan.get(pos);
        if (old == null || old.isAir() || old.getBlock() instanceof DoorBlock) return;
        if (old.isOf(Blocks.GLASS)) {
            // Keep full glass blocks so diagonal neighbours and inset window bays never leave holes.
            plan.put(pos, Blocks.GLASS.getDefaultState());
            if (y % 4 == 2) plan.put(pos.offset(outside), Blocks.SMOOTH_STONE_SLAB.getDefaultState());
        } else {
            var panel = y % 4 == 0 ? Blocks.LIGHT_GRAY_CONCRETE
                    : weather(along, y, pos.getZ()) == 0 ? Blocks.TUFF : Blocks.SMOOTH_STONE;
            plan.put(pos, panel.getDefaultState());
        }
    }

    static Map<BlockPos, BlockState> school(BlockPos base) {
        var plan = new LinkedHashMap<BlockPos, BlockState>();
        for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) for (int y = 0; y <= 15; y++) {
            boolean shell = x == 0 || x == 14 || z == 0 || z == 13;
            int floorY = y % 5;
            boolean glazing = shell && (floorY == 2 || floorY == 3)
                    && (x == 0 || x == 14 ? z >= 2 && z <= 11 && z % 4 != 0 : x >= 2 && x <= 12 && x % 4 != 0);
            var block = z == 14 && y > 0 ? Blocks.AIR
                    : y == 15 ? Blocks.DEEPSLATE_TILES
                    : floorY == 0 ? (shell ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE)
                    : glazing ? Blocks.GLASS
                    : shell ? (floorY == 1 && y < 5 ? Blocks.BRICKS : weather(x, y, z) == 0 ? Blocks.TUFF_BRICKS : Blocks.SMOOTH_STONE)
                    : Blocks.AIR;
            plan.put(base.add(x, y, z), block.getDefaultState());
        }
        for (int y : new int[]{0, 5, 10}) {
            for (int x : new int[]{3, 6, 9, 12}) for (int z : new int[]{5, 8, 11}) {
                if (x == 3 || x == 12) continue;
                plan.put(base.add(x, y + 1, z), Blocks.OAK_SLAB.getDefaultState());
                plan.put(base.add(x, y + 1, z + 1), Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
            }
            for (int x = 4; x <= 10; x++) plan.put(base.add(x, y + 3, 1), Blocks.GREEN_TERRACOTTA.getDefaultState());
            plan.put(base.add(7, y + 1, 3), Blocks.LECTERN.getDefaultState());
            for (int x : new int[]{5, 10}) for (int z : new int[]{4, 10})
                plan.put(base.add(x, y + 4, z), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        }
        // Two flights along opposite side walls leave the main classroom aisles free.
        flight(plan, base, 1, 0, 4, Direction.SOUTH, 5);
        flight(plan, base, 12, 5, 8, Direction.NORTH, 5);
        for (int x : new int[]{6, 7, 8}) door(plan, base.add(x, 1, 13), Direction.SOUTH);
        for (int x = 2; x <= 12; x++) plan.put(base.add(x, 4, 14), Blocks.STONE_BRICK_SLAB.getDefaultState());
        for (int x : new int[]{2, 12}) for (int y = 1; y <= 3; y++)
            plan.put(base.add(x, y, 14), Blocks.POLISHED_ANDESITE.getDefaultState());
        sign(plan, base.add(7, 5, 14), Direction.SOUTH);
        return plan;
    }

    private static void flight(Map<BlockPos, BlockState> plan, BlockPos base, int lane, int y, int z,
                               Direction facing, int steps) {
        for (int x = lane; x <= lane + 1; x++) for (int step = 0; step < steps; step++) {
            var pos = base.add(x, y + step + 1, z + step * facing.getOffsetZ());
            plan.put(pos, Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, facing));
            for (int h = 1; h <= 3; h++) plan.put(pos.up(h), AIR);
            for (int h = 1; h <= step; h++) plan.put(pos.down(h), Blocks.STONE_BRICKS.getDefaultState());
        }
    }

    static Map<BlockPos, BlockState> shop(BlockPos base) {
        var plan = VillageMarkets.plan(base, VillageEpoch.STEAM);
        for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) {
            plan.put(base.add(x, 6, z), (weather(x, 6, z) == 0 ? Blocks.POLISHED_BLACKSTONE : Blocks.DEEPSLATE_TILES).getDefaultState());
            if (x == 0 || x == 14 || z == 0 || z == 14)
                plan.put(base.add(x, 7, z), Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
        }
        for (int x = 1; x <= 13; x++) {
            if (x != 6 && x != 7) {
                plan.put(base.add(x, 1, 1), Blocks.POLISHED_ANDESITE.getDefaultState());
                plan.put(base.add(x, 2, 1), (x % 4 == 1 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.GLASS).getDefaultState());
                plan.put(base.add(x, 3, 1), (x % 4 == 1 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.GLASS).getDefaultState());
            }
            plan.put(base.add(x, 4, 1), Blocks.GREEN_TERRACOTTA.getDefaultState());
            plan.put(base.add(x, 4, 0), Blocks.SMOOTH_STONE_SLAB.getDefaultState());
        }
        sign(plan, base.add(7, 5, 0), Direction.NORTH);
        plan.put(base.add(7, 5, 1), Blocks.GREEN_TERRACOTTA.getDefaultState());
        return plan;
    }

    static void door(Map<BlockPos, BlockState> plan, BlockPos foot, Direction facing) {
        var door = Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, facing);
        plan.put(foot, door);
        plan.put(foot.up(), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void sign(Map<BlockPos, BlockState> plan, BlockPos pos, Direction direction) {
        plan.put(pos, Blocks.SPRUCE_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, direction));
    }

    private static int weather(int x, int y, int z) { return Math.floorMod(x * 19 + y * 13 + z * 7, 17); }

    private IndustrialArchitecture() {}
}
