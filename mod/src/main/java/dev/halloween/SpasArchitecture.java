package dev.halloween;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Red masonry, deep window reveals, a west bell tower and an elongated church with a central tent. */
final class SpasArchitecture {
    static final int HEIGHT = 124;
    private static final Block RED = Blocks.BRICKS, DARK = Blocks.RED_NETHER_BRICKS;
    private static final Block STONE = Blocks.SMOOTH_SANDSTONE, IVORY = Blocks.CALCITE;
    private static final Block GREEN = Blocks.DARK_PRISMARINE;
    private final BlockPos base;
    private final Map<BlockPos, BlockState> plan = new LinkedHashMap<>();

    private SpasArchitecture(BlockPos base) { this.base = base; }

    static Map<BlockPos, BlockState> build(BlockPos base) {
        var church = new SpasArchitecture(base);
        church.build();
        return church.plan;
    }

    private void build() {
        for (int x = 3; x <= 61; x++) for (int z = 8; z <= 82; z++) {
            put(x, 0, z, (x + z * 3) % 11 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS);
        }
        nave();
        // West bell tower, central tent, four patterned satellite domes and east apses.
        drum(32, 23, 9, 1, 51, true);
        onion(32, 23, 51, 8, 17, new Block[]{Blocks.GOLD_BLOCK, Blocks.YELLOW_TERRACOTTA}, 0);
        drum(32, 49, 11, 28, 59, true);
        tent(32, 49, 59, 10, 42);
        drum(32, 49, 3, 101, 106, false);
        onion(32, 49, 106, 5, 13, new Block[]{Blocks.CYAN_TERRACOTTA, Blocks.GOLD_BLOCK, Blocks.OXIDIZED_CUT_COPPER}, 1);

        drum(15, 38, 7, 1, 39, false);
        onion(15, 38, 39, 7, 17, new Block[]{Blocks.LIGHT_BLUE_CONCRETE, Blocks.WHITE_TERRACOTTA, Blocks.BLUE_TERRACOTTA}, 1);
        drum(49, 38, 7, 1, 43, false);
        onion(49, 38, 43, 7, 18, new Block[]{Blocks.RED_TERRACOTTA, Blocks.GOLD_BLOCK, Blocks.WEATHERED_CUT_COPPER}, 2);
        drum(15, 65, 7, 1, 42, false);
        onion(15, 65, 42, 7, 17, new Block[]{Blocks.OXIDIZED_CUT_COPPER, Blocks.GOLD_BLOCK, Blocks.LIME_TERRACOTTA}, 2);
        drum(49, 65, 7, 1, 40, false);
        onion(49, 65, 40, 7, 17, new Block[]{Blocks.RED_CONCRETE, Blocks.SMOOTH_QUARTZ, Blocks.RED_TERRACOTTA}, 3);

        for (int x : new int[]{21, 32, 43}) {
            drum(x, 74, 5, 1, 19, false);
            onion(x, 74, 19, 4, 10, new Block[]{GREEN, Blocks.GOLD_BLOCK}, 1);
        }
        for (int x : new int[]{11, 53}) {
            drum(x, 24, 4, 1, 16, false);
            tent(x, 24, 16, 4, 11);
            cross(x, 29, 24);
        }
        for (int x = 27; x <= 37; x++) {
            int height = 9 - Math.abs(x - 32);
            for (int z = 12; z <= 34; z++) for (int y = 1; y <= height; y++)
                put(x, y, z, Blocks.AIR);
            put(x, height + 1, 13, IVORY);
            put(x, height + 2, 13, RED);
        }
        for (int x : new int[]{8, 56}) for (int z : new int[]{13, 29, 53, 78}) lamp(x, z);
    }

    private void nave() {
        for (int x = 9; x <= 55; x++) for (int z = 20; z <= 72; z++) {
            boolean side = x == 9 || x == 55;
            boolean face = side || z == 20 || z == 72;
            int along = side ? z - 20 : x - 9;
            for (int y = 1; y <= 28; y++) {
                if (!face && y != 28) continue;
                int bay = Math.floorMod(along, 8);
                boolean window = (bay >= 3 && bay <= 5 && (y >= 8 && y <= 13 || y >= 18 && y <= 22))
                        || bay == 4 && (y == 14 || y == 23);
                boolean trim = y == 4 || y == 15 || y == 25 || y == 27 || bay == 1;
                put(x, y, z, y == 28 ? GREEN : window ? Blocks.ORANGE_STAINED_GLASS
                        : trim ? (y % 2 == 0 ? IVORY : STONE) : masonry(x, y, z));
                if (face && (y == 3 || y == 15 || y == 26) && along % 2 == 0) {
                    int dx = x == 9 ? -1 : x == 55 ? 1 : 0;
                    int dz = z == 20 ? -1 : z == 72 ? 1 : 0;
                    put(x + dx, y, z + dz, Blocks.SANDSTONE_SLAB);
                }
            }
        }
        for (int x : new int[]{15, 25, 39, 49}) for (int z = 27; z <= 67; z += 10) {
            for (int y = 1; y <= 24; y++) {
                put(x, y, z, y % 6 == 0 ? IVORY : Blocks.POLISHED_GRANITE);
                if (y == 24) for (Direction side : Direction.Type.HORIZONTAL)
                    put(x + side.getOffsetX(), y, z + side.getOffsetZ(), STONE);
            }
            plan.put(base.add(x, 3, z + 1), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false));
            put(x, 2, z + 1, Blocks.STONE_BRICK_WALL);
        }
        for (int x = 27; x <= 37; x++) {
            int height = 9 - Math.abs(x - 32);
            for (int z = 14; z <= 24; z++) for (int y = 1; y <= height; y++)
                put(x, y, z, Blocks.AIR);
            put(x, height + 1, 14, IVORY);
            put(x, height + 2, 14, RED);
        }
        for (int x = 25; x <= 39; x++) for (int z = 11; z <= 19; z++)
            put(x, 0, z, (z % 3 == 0 ? Blocks.POLISHED_GRANITE : Blocks.SMOOTH_STONE));
        for (int x = 9; x <= 55; x += 46) for (int z : new int[]{33, 49, 65}) {
            for (int offset = -2; offset <= 2; offset++) {
                for (int y = 1; y <= 6 - Math.abs(offset); y++) put(x, y, z + offset, Blocks.AIR);
                put(x, 7 - Math.abs(offset), z + offset, STONE);
            }
        }
        for (int x : new int[]{9, 55}) gable(x, 49);
    }

    private void gable(int x, int cz) {
        int outer = x == 9 ? x - 1 : x + 1;
        for (int dz = -9; dz <= 9; dz++) {
            int top = 39 - Math.abs(dz);
            for (int y = 26; y <= top; y++) {
                boolean rim = y >= top - 1;
                boolean mosaic = y >= 29 && y <= 34 && Math.abs(dz) <= 4;
                put(outer, y, cz + dz, rim ? IVORY : mosaic
                        ? ((dz + y) % 3 == 0 ? Blocks.BLUE_TERRACOTTA : Blocks.ORANGE_TERRACOTTA) : RED);
            }
        }
    }

    private void drum(int cx, int cz, int radius, int bottom, int top, boolean rich) {
        for (int y = bottom; y <= top; y++) {
            boolean cornice = y == bottom || y == top || y == top - 3 || (y - bottom) % 11 == 0;
            int r = radius + (cornice ? 1 : 0);
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                if (!octagon(x, z, r)) continue;
                if (octagon(x, z, r - (cornice ? 2 : 1)) && y != top) continue;
                int side = Math.min(Math.abs(x), Math.abs(z));
                int band = Math.floorMod(y - bottom, 11);
                boolean window = !cornice && (side <= 1 && band >= 3 && band <= 8 || side == 2 && band >= 4 && band <= 7 && rich);
                var block = cornice ? (y % 2 == 0 ? STONE : IVORY)
                        : window ? Blocks.BROWN_STAINED_GLASS : side == 3 && band > 1 ? Blocks.POLISHED_GRANITE
                        : masonry(cx + x, y, cz + z);
                put(cx + x, y, cz + z, block);
            }
        }
    }

    private void tent(int cx, int cz, int bottom, int radius, int height) {
        for (int h = 0; h <= height; h++) {
            int r = Math.max(1, radius - h * (radius - 1) / height);
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                if (!octagon(x, z, r) || octagon(x, z, r - 2)) continue;
                boolean rib = Math.min(Math.abs(x), Math.abs(z)) == 0 || Math.abs(x) == Math.abs(z);
                Block material = h % 9 == 0 ? STONE : rib ? IVORY
                        : (h + Math.abs(x) * 2 + Math.abs(z)) % 7 < 2 ? GREEN : masonry(x, h, z);
                put(cx + x, bottom + h, cz + z, material);
            }
        }
    }

    private void onion(int cx, int cz, int bottom, int width, int height, Block[] palette, int pattern) {
        int lastRadius = 0;
        for (int y = 0; y <= height; y++) {
            double t = (double) y / height;
            double profile = t < .35 ? .63 + .37 * Math.sin(t / .35 * Math.PI / 2)
                    : Math.pow(Math.max(0, 1 - (t - .35) / .65), 1.25);
            int r = Math.max(1, (int)Math.round(width * profile));
            int inner = Math.max(0, Math.min(r, lastRadius) - 2);
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                int distance = x * x + z * z;
                if (distance > r * r + r / 2 || distance < inner * inner) continue;
                double angle = Math.atan2(z, x) + Math.PI;
                int stripe = (int)Math.floor(angle * 12 / (2 * Math.PI) + (pattern == 1 ? y * .42 : 0));
                int design = switch (pattern) {
                    case 1 -> stripe;
                    case 2 -> stripe + (y + 1) / 2;
                    case 3 -> y + (int)Math.round(Math.sin(angle * 6) * 1.25);
                    default -> (x + z + y) % 9 == 0 ? 1 : 0;
                };
                put(cx + x, bottom + y, cz + z, palette[Math.floorMod(design, palette.length)]);
            }
            lastRadius = r;
        }
        cross(cx, bottom + height + 1, cz);
    }

    private void cross(int x, int y, int z) {
        for (int h = 0; h <= 4; h++) put(x, y + h, z, Blocks.GOLD_BLOCK);
        for (int dx : new int[]{-2, -1, 1, 2}) put(x + dx, y + 3, z, Blocks.GOLD_BLOCK);
    }

    private void lamp(int x, int z) {
        put(x, 1, z, Blocks.COBBLESTONE_WALL);
        put(x, 2, z, Blocks.LANTERN);
    }

    static boolean protectedWing(int x, int y, int z) {
        return x >= 44 && x <= 53 && z >= 55 && z <= 73 && y >= 0 && y <= 9;
    }

    private static boolean octagon(int x, int z, int r) {
        return r >= 0 && Math.max(Math.abs(x), Math.abs(z)) <= r && Math.abs(x) + Math.abs(z) <= r * 3 / 2;
    }

    private static Block masonry(int x, int y, int z) {
        int color = Math.floorMod(x * 7 + y * 3 + z * 11, 19);
        return color == 0 ? DARK : color <= 2 ? Blocks.RED_TERRACOTTA : RED;
    }

    private void put(int x, int y, int z, Block block) {
        plan.put(base.add(x, y, z), block.getDefaultState());
    }
}
