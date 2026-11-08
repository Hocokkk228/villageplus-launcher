package dev.halloween;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

final class MarketCrime {
    private record GrabTime(ServerWorld world, long tick) {}
    private static final Map<ServerPlayerEntity, GrabTime> LAST_GRAB = new WeakHashMap<>();
    private record Alarm(BlockPos approach, long expires) {}
    private static final Map<ServerWorld, Map<Long, Alarm>> ALARMS = new WeakHashMap<>();

    static BlockPos investigation(ServerWorld world, long village) {
        var alarms = ALARMS.get(world);
        if (alarms == null) return null;
        var alarm = alarms.get(village);
        if (alarm == null) return null;
        if (alarm.expires() <= world.getTime()) {
            alarms.remove(village);
            return null;
        }
        return alarm.approach();
    }

    record Stall(BlockPos barrel, VillageEraData.Settlement settlement, int era) {
        String name() {
            return (era >= 3 ? "Магазин" : "Рынок") + " — " + settlement.name;
        }
    }

    static Stall find(ServerWorld world, BlockPos pos) {
        for (var settlement : VillageEraData.get(world).settlements.values()) {
            var districtShop = VillageIndustrial.shop(world, settlement);
            if (districtShop != null && stockPosition(districtShop, pos)) return new Stall(pos.toImmutable(), settlement, VillageEpoch.INDUSTRIAL);
            int stage = settlement.xp.getOrDefault(VillageMarkets.STAGE, 0);
            if (stage == 0 || !settlement.xp.containsKey(VillageMarkets.X)) continue;
            BlockPos base = VillageMarkets.origin(settlement);
            if (!stockPosition(base, pos)) continue;
            int era = finishedEra(world, settlement, stage);
            if (era > 0) return new Stall(pos.toImmutable(), settlement, era);
        }
        return null;
    }

    private static boolean stockPosition(BlockPos base, BlockPos pos) {
        int dx = pos.getX() - base.getX(), dz = pos.getZ() - base.getZ();
        return pos.getY() == base.getY() + 1 && (dx == 3 || dx == 10) && (dz == 4 || dz == 11);
    }

    private static int finishedEra(ServerWorld world, VillageEraData.Settlement settlement, int stage) {
        int era = 0;
        for (var job : VillageProjects.get(world).jobs) {
            if (!job.done || job.village != settlement.village || !job.kind.equals("market")) continue;
            for (int n = Math.max(1, stage - 1); n <= stage; n++) {
                if (job.id.equals(VillageMarkets.id(settlement.village, n))) era = Math.max(era, n);
            }
        }
        return era;
    }

    static Stall nearbyStock(ServerWorld world, LivingEntity thief) {
        Stall best = null;
        double bestDistance = 40 * 40;
        for (var settlement : VillageEraData.get(world).settlements.values()) {
            int stage = settlement.xp.getOrDefault(VillageMarkets.STAGE, 0);
            if (stage == 0 || !settlement.xp.containsKey(VillageMarkets.X)) continue;
            BlockPos base = VillageMarkets.origin(settlement);
            if (!base.isWithinDistance(thief.getBlockPos(), 55)) continue;
            int era = finishedEra(world, settlement, stage);
            if (era == 0) continue;
            for (int x : new int[]{3, 10}) for (int z : new int[]{4, 11}) {
                BlockPos pos = base.add(x, 1, z);
                double distance = thief.squaredDistanceTo(Vec3d.ofCenter(pos));
                if (distance >= bestDistance || !world.isChunkLoaded(new ChunkPos(pos).toLong())) continue;
                if (world.getBlockEntity(pos) instanceof BarrelBlockEntity stock && !stock.isEmpty()) {
                    best = new Stall(pos, settlement, era);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    static ActionResult interact(ServerWorld world, ServerPlayerEntity player, Hand hand, Stall stall, BarrelBlockEntity stock) {
        if (stall == null || player.isCreative() || player.isSpectator()) return ActionResult.PASS;
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!stock.checkUnlocked(player)) return ActionResult.SUCCESS;
        ActionResult sale = Contraband.sell(world, player, stall);
        if (sale != ActionResult.PASS) return sale;
        if (player.isSneaking() && player.getStackInHand(hand).isEmpty()) {
            GrabTime last = LAST_GRAB.get(player);
            if (last != null && last.world == world && world.getTime() - last.tick < 40) return ActionResult.SUCCESS;
            LAST_GRAB.put(player, new GrabTime(world, world.getTime()));
            int amount = grab(stock, player.getInventory());
            if (amount == 0) {
                player.sendMessage(Text.literal("В бочке пусто или в инвентаре нет места."), true);
            } else {
                player.swingHand(hand);
                boolean seen = PlayerJustice.witnessed(world, player);
                alarm(world, stall, true);
                if (seen) {
                    PlayerJustice.marketWarrant(player, stall);
                    VillageFights.robbery(world,player,stall.barrel());
                }
                player.sendMessage(Text.literal("Схвачено товаров: " + amount), true);
            }
        } else {
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, ignored) -> new StockScreen(syncId, inventory, stock, stall),
                Text.literal(stall.name())));
        }
        return ActionResult.SUCCESS;
    }

    // Move actual stacks. Anything that doesn't fit goes straight back to the same slot.
    static int grab(Inventory stock, Inventory destination) {
        for (int slot = 0; slot < stock.size(); slot++) {
            ItemStack stack = stock.getStack(slot);
            if (stack.isEmpty()) continue;
            ItemStack taken = stock.removeStack(slot, Math.min(12, stack.getCount()));
            int before = taken.getCount();
            for (int target = 0; target < destination.size() && !taken.isEmpty(); target++) {
                if (destination instanceof PlayerInventory && target >= 36) break;
                if (!destination.isValid(target, taken)) continue;
                ItemStack current = destination.getStack(target);
                if (current.isEmpty()) {
                    int n = Math.min(taken.getCount(), destination.getMaxCount(taken));
                    if (n > 0) destination.setStack(target, taken.split(n));
                } else if (ItemStack.areItemsAndComponentsEqual(current, taken)) {
                    int n = Math.min(taken.getCount(), destination.getMaxCount(current) - current.getCount());
                    if (n > 0) {
                        current.increment(n);
                        taken.decrement(n);
                    }
                }
            }
            int moved = before - taken.getCount();
            if (!taken.isEmpty()) {
                ItemStack remaining = stock.getStack(slot);
                if (remaining.isEmpty()) stock.setStack(slot, taken);
                else remaining.increment(taken.getCount());
            }
            if (moved > 0) {
                stock.markDirty();
                destination.markDirty();
                return moved;
            }
        }
        return 0;
    }

    static void alarm(ServerWorld world, Stall stall, boolean loud) {
        BlockPos pos = stall.barrel();
        var alarms = ALARMS.computeIfAbsent(world, ignored -> new HashMap<>());
        alarms.values().removeIf(a -> a.expires() <= world.getTime());
        BlockPos base = stall.era() == VillageEpoch.INDUSTRIAL
                ? VillageIndustrial.shop(world, stall.settlement()) : VillageMarkets.origin(stall.settlement());
        BlockPos approach = base == null ? pos : base.add(7, 1, 0);
        alarms.put(stall.settlement().village, new Alarm(approach, world.getTime() + 400));
        if (loud) BlastEffects.burst(world, Vec3d.ofCenter(pos).add(0, .5, 0), BlastEffects.Kind.ROBBERY);
        world.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, loud ? 2f : 1.3f, .92f);
        Text message = Text.literal("§cТревога! Ограбление. §f" + stall.name());
        for (var player : world.getPlayers()) {
            if (player.squaredDistanceTo(Vec3d.ofCenter(pos)) < 72 * 72) player.sendMessage(message, false);
        }
    }

    private static final class StockScreen extends GenericContainerScreenHandler {
        private final Inventory stock;
        private final Stall stall;
        private boolean reported;

        StockScreen(int id, PlayerInventory player, Inventory stock, Stall stall) {
            super(ScreenHandlerType.GENERIC_9X3, id, player, stock, 3);
            this.stock = stock;
            this.stall = stall;
        }

        @Override
        public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
            if (reported || !(player instanceof ServerPlayerEntity serverPlayer)
                    || !(player.getEntityWorld() instanceof ServerWorld world)) {
                super.onSlotClick(slot, button, action, player);
                return;
            }
            List<ItemStack> before = new ArrayList<>(stock.size());
            for (int i = 0; i < stock.size(); i++) before.add(stock.getStack(i).copy());
            super.onSlotClick(slot, button, action, player);
            if (stockTaken(before, stock) && PlayerJustice.witnessed(world, serverPlayer)) {
                reported = true;
                alarm(world, stall, false);
                PlayerJustice.marketWarrant(serverPlayer, stall);
                VillageFights.robbery(world,serverPlayer,stall.barrel());
            }
        }
    }

    static boolean stockTaken(List<ItemStack> before, Inventory stock) {
        for (int i = 0; i < before.size(); i++) {
            ItemStack previous = before.get(i), current = stock.getStack(i);
            if (!previous.isEmpty() && (!ItemStack.areItemsAndComponentsEqual(previous, current)
                    || current.getCount() < previous.getCount())) return true;
        }
        return false;
    }

    private MarketCrime() {}
}
