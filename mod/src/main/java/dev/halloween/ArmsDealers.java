package dev.halloween;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

final class ArmsDealers {
    enum Site {
        GARAGE(13, 9, 10, 7), ALLEY(4, 4, 1, 2), STATION(4, 4, 1, 2);
        final int width, depth, barrelX, barrelZ;
        Site(int width, int depth, int barrelX, int barrelZ) {
            this.width = width;
            this.depth = depth;
            this.barrelX = barrelX;
            this.barrelZ = barrelZ;
        }
        BlockPos stock(BlockPos base) { return base.add(barrelX, 1, barrelZ); }
        String key() { return "arms_" + name().toLowerCase(java.util.Locale.ROOT); }
        String id(long village) { return key() + ":" + village; }
    }

    static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getPlayers().isEmpty() || world.getTime() % 200 != 0) return;
            for (var settlement : VillageEraData.get(world).settlements.values()) {
                if (settlement.era < 3 || !settlement.xp.containsKey(VillageMarkets.X)
                        || world.getPlayers().stream().noneMatch(p -> VillageMarkets.origin(settlement).isWithinDistance(p.getBlockPos(), 160))) continue;
                for (var site : Site.values()) {
                    BlockPos base = origin(settlement, site);
                    if (base == null) schedule(world, settlement, site);
                    else if (world.isChunkLoaded(site.stock(base)) && complete(world, settlement, site)
                            && world.getBlockEntity(site.stock(base)) instanceof BarrelBlockEntity stock) {
                        restock(world, settlement, stock);
                    }
                }
            }
        });
    }

    private static BlockPos origin(VillageEraData.Settlement settlement, Site site) {
        var xp = settlement.xp;
        String key = site.key();
        if (!xp.containsKey(key + "_x")) return null;
        return new BlockPos(xp.get(key + "_x"), xp.get(key + "_y"), xp.get(key + "_z"));
    }

    private static boolean complete(ServerWorld world, VillageEraData.Settlement settlement, Site site) {
        return VillageProjects.get(world).jobs.stream().anyMatch(job -> job.done && job.id.equals(site.id(settlement.village)));
    }

    private static void schedule(ServerWorld world, VillageEraData.Settlement settlement, Site site) {
        BlockPos market = VillageMarkets.origin(settlement);
        BlockPos center = market;
        if (site == Site.STATION) {
            var station = RailwayNetwork.get(world).stations.stream().filter(s -> s.village == settlement.village).findFirst().orElse(null);
            if (station == null) return;
            center = station.pos(12, 0, 50);
        } else if (MarketCrime.find(world, market.add(3, 1, 4)) == null) return;
        String key = site.key();
        int cursor = settlement.xp.getOrDefault(key + "_search", 0);
        for (int attempt = cursor; attempt < cursor + 5; attempt++) {
            int ring = 18 + Math.floorMod(attempt / 8, 8) * 5;
            double angle = (attempt % 8) * Math.PI / 4 + ((attempt / 8) % 2) * Math.PI / 8;
            int x = center.getX() + (int)Math.round(Math.cos(angle) * ring);
            int z = center.getZ() + (int)Math.round(Math.sin(angle) * ring);
            if (!clear(world, x, z, site)) continue;
            int top = VillageEraPlans.levelSite(world, x, z, site.width, site.depth, 2);
            if (top == Integer.MIN_VALUE) continue;
            BlockPos base = new BlockPos(x, top - 1, z);
            if (!safe(world, base, site)) continue;
            var jobs = VillageProjects.get(world);
            jobs.add(world, site.id(settlement.village), settlement.village, "arms_cache", plan(base, site));
            if (jobs.exists(site.id(settlement.village))) {
                settlement.xp.put(key + "_x", x);
                settlement.xp.put(key + "_y", base.getY());
                settlement.xp.put(key + "_z", z);
                break;
            }
        }
        settlement.xp.put(key + "_search", (cursor + 5) % 128);
        VillageEraData.get(world).markDirty();
    }

    private static boolean clear(ServerWorld world, int x, int z, Site site) {
        int maxX = x + site.width - 1, maxZ = z + site.depth - 1;
        for (int cx = x >> 4; cx <= maxX >> 4; cx++) for (int cz = z >> 4; cz <= maxZ >> 4; cz++) {
            if (!world.isChunkLoaded(net.minecraft.util.math.ChunkPos.toLong(cx, cz))) return false;
        }
        if (VillageProjects.get(world).jobs.stream().anyMatch(j ->
                j.min.getX() <= maxX && j.max.getX() >= x && j.min.getZ() <= maxZ && j.max.getZ() >= z)) return false;
        return world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses.stream().noneMatch(h ->
                h.min.getX() <= maxX && h.max.getX() >= x && h.min.getZ() <= maxZ && h.max.getZ() >= z);
    }

    private static boolean safe(ServerWorld world, BlockPos base, Site site) {
        for (var pos : BlockPos.iterate(base, base.add(site.width - 1, 6, site.depth - 1))) {
            var block = world.getBlockState(pos);
            if (!world.getWorldBorder().contains(pos) || !world.getFluidState(pos).isEmpty() || block.hasBlockEntity()
                    || !block.isAir() && !block.isReplaceable() && !VillageProjects.terrain(block)) return false;
        }
        return true;
    }

    static Map<BlockPos, BlockState> plan(BlockPos base, Site site) {
        var blocks = new LinkedHashMap<BlockPos, BlockState>();
        for (int x = 0; x < site.width; x++) for (int z = 0; z < site.depth; z++) {
            blocks.put(base.add(x, 0, z), (site == Site.GARAGE ? Blocks.SMOOTH_STONE : Blocks.COBBLED_DEEPSLATE).getDefaultState());
            for (int y = 1; y <= 6; y++) blocks.put(base.add(x, y, z), Blocks.AIR.getDefaultState());
        }
        if (site == Site.GARAGE) {
            for (int x = 0; x < 13; x++) for (int z = 1; z <= 6; z++) {
                if (x == 0 || x == 6 || x == 12 || z == 6) for (int y = 1; y <= 3; y++) {
                    blocks.put(base.add(x, y, z), (y == 1 ? Blocks.CRACKED_STONE_BRICKS
                            : (x + z + y) % 7 == 0 ? Blocks.GRANITE : Blocks.BRICKS).getDefaultState());
                }
                blocks.put(base.add(x, 4, z), Blocks.POLISHED_DEEPSLATE_SLAB.getDefaultState());
                if (z == 2 || z == 5) blocks.put(base.add(x, 3, z), Blocks.DARK_OAK_SLAB.getDefaultState());
            }
            for (int x : new int[]{0, 6, 12}) for (int y = 1; y <= 3; y++) {
                blocks.put(base.add(x, y, 1), Blocks.POLISHED_ANDESITE.getDefaultState());
            }
            for (int x : new int[]{2, 4, 8, 10}) blocks.put(base.add(x, 3, 1), Blocks.IRON_BARS.getDefaultState());
            for (int x : new int[]{2, 8}) {
                blocks.put(base.add(x, 1, 4), Blocks.SMITHING_TABLE.getDefaultState());
                blocks.put(base.add(x + 1, 1, 4), Blocks.ANVIL.getDefaultState());
                blocks.put(base.add(x + 2, 1, 5), Blocks.CAULDRON.getDefaultState());
                blocks.put(base.add(x + 1, 3, 3), Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true));
            }
            blocks.put(base.add(1, 1, 7), Blocks.IRON_TRAPDOOR.getDefaultState());
            blocks.put(base.add(8, 1, 7), Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
            blocks.put(base.add(11, 1, 7), Blocks.REDSTONE_TORCH.getDefaultState());
        } else {
            for (int x = 0; x < 4; x++) {
                blocks.put(base.add(x, 1, 3), Blocks.BRICKS.getDefaultState());
                blocks.put(base.add(x, 2, 3), Blocks.CRACKED_STONE_BRICKS.getDefaultState());
                blocks.put(base.add(x, 3, 2), Blocks.DARK_OAK_SLAB.getDefaultState());
                blocks.put(base.add(x, 3, 3), Blocks.DARK_OAK_SLAB.getDefaultState());
            }
            blocks.put(base.add(2, 1, 2), Blocks.REDSTONE_TORCH.getDefaultState());
        }
        blocks.put(site.stock(base), Blocks.BARREL.getDefaultState());
        return blocks;
    }

    private static void restock(ServerWorld world, VillageEraData.Settlement settlement, Inventory stock) {
        if (settlement.vault == null) return;
        BlockPos pos = VillageEconomy.barrel(settlement, 0);
        if (world.isChunkLoaded(pos) && world.getBlockEntity(pos) instanceof BarrelBlockEntity reserve) {
            transfer(reserve, stock, VillageItems.MUSKET, 1, 2);
            transfer(reserve, stock, VillageItems.REVOLVER, 1, 2);
            transfer(reserve, stock, VillageItems.MUSKET_CARTRIDGE, 16, 16);
        }
    }

    static void transfer(Inventory reserve, Inventory stock, Item item, int cap, int keep) {
        int amount = Math.min(cap - stock.count(item), reserve.count(item) - keep);
        if (amount <= 0) return;
        int remaining = amount;
        for (int source = 0; source < reserve.size() && remaining > 0; source++) {
            ItemStack offered = reserve.getStack(source);
            if (!offered.isOf(item)) continue;
            for (int target = 0; target < stock.size() && remaining > 0 && !offered.isEmpty(); target++) {
                ItemStack present = stock.getStack(target);
                if (!present.isEmpty() && !ItemStack.areItemsAndComponentsEqual(present, offered)) continue;
                int available = stock.getMaxCount(offered) - present.getCount();
                int count = Math.min(remaining, Math.min(available, offered.getCount()));
                if (count <= 0) continue;
                ItemStack moved = reserve.removeStack(source, count);
                if (present.isEmpty()) stock.setStack(target, moved);
                else present.increment(moved.getCount());
                remaining -= moved.getCount();
            }
        }
        if (remaining != amount) {
            reserve.markDirty();
            stock.markDirty();
        }
    }

    static ActionResult interact(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BarrelBlockEntity stock) {
        if (player.isCreative() || player.isSpectator()) return ActionResult.PASS;
        for (var settlement : VillageEraData.get(world).settlements.values()) for (var site : Site.values()) {
            BlockPos base = origin(settlement, site);
            if (base == null || !site.stock(base).equals(pos) || !complete(world, settlement, site)) continue;
            var money = player.getMainHandStack();
            if (!money.isOf(Items.EMERALD)) {
                player.sendMessage(Text.literal("§7Скупщик: ПКМ с изумрудами — 8 патронов за 2. Shift + ПКМ — оружие за 10–14."), true);
                return ActionResult.SUCCESS;
            }
            if (!world.getEntitiesByClass(VillagerEntity.class, new Box(pos).expand(8, 4, 8),
                    g -> VillageDefense.isGuard(g) && g.canSee(player)).isEmpty()) {
                player.sendMessage(Text.literal("§7Скупщик: «Уходи. Здесь стража»."), true);
                return ActionResult.SUCCESS;
            }
            boolean gun = player.isSneaking();
            Item item = gun ? (stock.count(VillageItems.MUSKET) > 0 ? VillageItems.MUSKET : VillageItems.REVOLVER)
                    : VillageItems.MUSKET_CARTRIDGE;
            int cost = gun ? (item == VillageItems.MUSKET ? 10 : 14) : 2;
            int amount = gun ? 1 : 8;
            if (money.getCount() < cost || stock.count(item) < amount) {
                player.sendMessage(Text.literal("§7Скупщик: не хватает изумрудов или товара на складе."), true);
                return ActionResult.SUCCESS;
            }
            for (int i = 0; i < stock.size(); i++) {
                if (!stock.getStack(i).isOf(item) || stock.getStack(i).getCount() < amount) continue;
                ItemStack goods = stock.removeStack(i, amount);
                money.decrement(cost);
                player.getInventory().insertStack(goods);
                if (!goods.isEmpty()) player.dropItem(goods, false);
                stock.markDirty();
                player.sendMessage(Text.literal("§aСделка состоялась."), true);
                return ActionResult.SUCCESS;
            }
            player.sendMessage(Text.literal("§7Скупщик: «Полного комплекта пока нет»."), true);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    private ArmsDealers() {}
}
