package dev.halloween;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

final class VillageConvoys {
    private static final String CARRIER = "halloween_goods_carrier";
    private static final String FROM = "halloween_goods_from_";
    private static final String TO = "halloween_goods_to_";
    private static final String DEPARTED = "halloween_goods_departed_";
    private static final String DAY = "goods_convoy_day";
    private static final Map<VillagerEntity, Long> ATTACKERS = new WeakHashMap<>();

    static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 20 != 0 || world.getPlayers().isEmpty()) return;
            var carriers = new java.util.HashSet<java.util.UUID>();
            for (var player : world.getPlayers()) {
                for (var carrier : world.getEntitiesByClass(VillagerEntity.class,
                        new Box(player.getBlockPos()).expand(160, 48, 160), VillageConvoys::owns)) {
                    if (carriers.add(carrier.getUuid())) tick(world, carrier);
                }
            }
            if (world.getTime() % 200 == 0 && VillageBuilders.workHours(world)) dispatch(world);
        });
    }

    static boolean owns(VillagerEntity villager) { return villager.getCommandTags().contains(CARRIER); }

    static boolean fighting(ServerWorld world, VillagerEntity villager) {
        return ATTACKERS.getOrDefault(villager, 0L) > world.getTime();
    }

    private static void dispatch(ServerWorld world) {
        var data = VillageEraData.get(world);
        var memory = world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        int day = (int)(world.getTime() / 24000);
        for (var source : memory.teams) {
            var origin = data.settlements.get(source.village);
            if (origin == null || origin.era < 3 || origin.vault == null || origin.xp.getOrDefault(DAY, -1) == day) continue;
            var loading = VillageEconomy.barrel(origin, 0);
            if (!world.isChunkLoaded(loading) || world.getPlayers().stream().noneMatch(p -> loading.isWithinDistance(p.getBlockPos(), 100))) continue;
            var destination = memory.teams.stream().filter(t -> t != source && t.anchor.isWithinDistance(source.anchor, 280)
                    && !t.anchor.isWithinDistance(source.anchor, 55) && stock(world, data.settlements.get(t.village)) != null)
                    .min(Comparator.comparingDouble(t -> t.anchor.getSquaredDistance(source.anchor))).orElse(null);
            if (destination == null || !(world.getBlockEntity(loading) instanceof BarrelBlockEntity stock)) continue;
            var carrier = world.getEntitiesByClass(VillagerEntity.class, new Box(loading).expand(8, 3, 8),
                    v -> v.isAlive() && !v.isBaby() && !v.hasVehicle() && !v.isSleeping()
                            && v.getOffHandStack().isEmpty() && !owns(v) && !VillageTasks.ownsMovement(v)
                            && !v.getCommandTags().contains("halloween_bandit") && !VillageTrials.busy(v)
                            && (VillageEras.profession(v).equals("none") || VillageEras.profession(v).equals("factory_porter")))
                    .stream().min(Comparator.comparingDouble(v -> v.squaredDistanceTo(Vec3d.ofCenter(loading)))).orElse(null);
            if (carrier == null || !load(world, carrier, stock, source.village, destination.village)) continue;
            origin.xp.put(DAY, day);
            data.markDirty();
            announce(world, carrier, "§6Из казны вышел оружейный обоз в «" + data.settlements.get(destination.village).name + "».");
            return;
        }
    }

    private static Inventory stock(ServerWorld world, VillageEraData.Settlement settlement) {
        if (settlement == null || settlement.vault == null) return null;
        var pos = VillageEconomy.barrel(settlement, 0);
        return world.isChunkLoaded(pos) && world.getBlockEntity(pos) instanceof BarrelBlockEntity barrel ? barrel : null;
    }

    static boolean load(ServerWorld world, VillagerEntity carrier, Inventory stock, long source, long destination) {
        if (owns(carrier) || !carrier.getOffHandStack().isEmpty() || source == destination) return false;
        for (int i = 0; i < stock.size(); i++) {
            var item = stock.getStack(i);
            int amount = item.isOf(VillageItems.MUSKET_CARTRIDGE) && stock.count(VillageItems.MUSKET_CARTRIDGE) >= 40 ? 24
                    : item.isOf(VillageItems.MUSKET) && stock.count(VillageItems.MUSKET) >= 3 ? 1
                    : item.isOf(VillageItems.REVOLVER) && stock.count(VillageItems.REVOLVER) >= 3 ? 1 : 0;
            if (amount == 0) continue;
            carrier.equipStack(EquipmentSlot.OFFHAND, stock.removeStack(i, amount));
            carrier.setDropGuaranteed(EquipmentSlot.OFFHAND);
            carrier.addCommandTag(CARRIER);
            VillageEras.putTag(carrier, FROM, source);
            VillageEras.putTag(carrier, TO, destination);
            VillageEras.putTag(carrier, DEPARTED, world.getTime());
            stock.markDirty();
            return true;
        }
        return false;
    }

    static boolean unload(VillagerEntity carrier, Inventory destination) {
        if (!owns(carrier) || carrier.getOffHandStack().isEmpty()) return false;
        for (int i = 0; i < destination.size(); i++) {
            if (!destination.getStack(i).isEmpty() || !destination.isValid(i, carrier.getOffHandStack())) continue;
            destination.setStack(i, carrier.getOffHandStack());
            carrier.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            destination.markDirty();
            finish(carrier);
            return true;
        }
        return false;
    }

    private static void finish(VillagerEntity carrier) {
        carrier.removeCommandTag(CARRIER);
        carrier.getCommandTags().removeIf(t -> t.startsWith(FROM) || t.startsWith(TO) || t.startsWith(DEPARTED));
        carrier.getNavigation().stop();
        carrier.setEquipmentDropChance(EquipmentSlot.OFFHAND, .085f);
    }

    private static void tick(ServerWorld world, VillagerEntity carrier) {
        if (carrier.getOffHandStack().isEmpty()) { finish(carrier); return; }
        var data = VillageEraData.get(world);
        long from = VillageEras.tag(carrier, FROM, 0);
        long to = VillageEras.tag(carrier, TO, 0);
        boolean returning = world.getTime() - VillageEras.tag(carrier, DEPARTED, world.getTime()) >= 4800;
        var destination = data.settlements.get(returning ? from : to);
        if (destination == null || destination.vault == null) return;
        var goal = VillageEconomy.barrel(destination, 0);
        if (carrier.isSleeping()) carrier.wakeUp();
        VillageTasks.hold(carrier);
        if (carrier.squaredDistanceTo(Vec3d.ofCenter(goal)) <= 12) {
            var storage = stock(world, destination);
            if (storage != null && unload(carrier, storage)) {
                announce(world, carrier, returning ? "§7Обоз вернулся с грузом." : "§aОружейный обоз доставил груз.");
            }
            return;
        }
        if (!returning) ambush(world, carrier);
        if (carrier.isTouchingWater()) { VillageWaterRescue.swimToShore(world, carrier); return; }
        if (!carrier.getNavigation().isIdle() && world.getTime() % 80 != 0) return;
        Vec3d here = carrier.getEntityPos(), end = Vec3d.ofCenter(goal);
        if (here.squaredDistanceTo(end) <= 28 * 28) {
            for (var side : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                if (walkTo(world, carrier, goal.offset(side))) return;
            }
            return;
        }
        Vec3d direction = end.subtract(here).multiply(1, 0, 1).normalize();
        Vec3d sideways = new Vec3d(-direction.z, 0, direction.x);
        for (int side : new int[]{0, -6, 6}) {
            BlockPos next = BlockPos.ofFloored(here.add(direction.multiply(21)).add(sideways.multiply(side)));
            if (!world.isChunkLoaded(next)) continue;
            int height = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, next.getX(), next.getZ());
            if (walkTo(world, carrier, next.withY(height))) return;
        }
    }

    private static boolean walkTo(ServerWorld world, VillagerEntity carrier, BlockPos pos) {
        if (!world.isChunkLoaded(pos) || !world.getFluidState(pos).isEmpty()
                || !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) return false;
        if (!VillagePathBudget.takeConvoy(world)) return false;
        var path = carrier.getNavigation().findPathTo(pos, 0);
        return path != null && path.reachesTarget() && carrier.getNavigation().startMovingAlong(path, .72);
    }

    static void ambush(ServerWorld world, VillagerEntity carrier) {
        var attackers = world.getEntitiesByClass(VillagerEntity.class, carrier.getBoundingBox().expand(16, 4, 16),
                v -> v.isAlive() && !v.isLeashed() && !v.hasVehicle() && !v.isSleeping() && !StreetCrime.active(world, v)
                        && v.getCommandTags().contains("halloween_bandit") && !v.getCommandTags().contains("halloween_prisoner")
                        && !VillageTrials.busy(v) && v.canSee(carrier));
        if (attackers.isEmpty()) return;
        attackers.sort(Comparator.comparingDouble(carrier::squaredDistanceTo));
        var bandit = attackers.getFirst();
        if (!fighting(world, bandit)) announce(world, carrier, "§cЗасада на оружейный обоз!");
        ATTACKERS.put(bandit, world.getTime() + 45);
        VillageTasks.hold(bandit);
        if (bandit.squaredDistanceTo(carrier) > 4) {
            if (VillagePathBudget.takeConvoy(world)) bandit.getNavigation().startMovingTo(carrier, .94);
        } else if (world.getTime() % 40 == 0) {
            bandit.swingHand(Hand.MAIN_HAND);
            if (carrier.damage(world, world.getDamageSources().mobAttack(bandit), 4)) bandit.addCommandTag("halloween_wanted");
        }
    }

    private static void announce(ServerWorld world, VillagerEntity carrier, String message) {
        for (var player : world.getPlayers()) {
            if (carrier.squaredDistanceTo(player) < 64 * 64) player.sendMessage(Text.literal(message), false);
        }
    }

    private VillageConvoys() {}
}
