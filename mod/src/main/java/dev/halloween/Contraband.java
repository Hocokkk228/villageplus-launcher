package dev.halloween;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

final class Contraband {
    private static final int SALE_WINDOW = 20 * 60 * 8;
    private static final String SALE_DAY = "contraband_sale_day";
    private static final String SALE_COUNT = "contraband_sale_count";
    private static final Map<ServerPlayerEntity, Travel> TRAVELLERS = new WeakHashMap<>();

    private record Delivery(long village, long expires, boolean musket, boolean revolver) {}
    record Port(long village, NavalWorks.Site site) {
        BlockPos checkpoint() { return site.at(0, 1, 8); }

        boolean contains(BlockPos hull) {
            BlockPos delta = hull.subtract(site.base());
            var outward = site.outward();
            var side = outward.rotateYCounterclockwise();
            int along = delta.getX() * outward.getOffsetX() + delta.getZ() * outward.getOffsetZ();
            int across = delta.getX() * side.getOffsetX() + delta.getZ() * side.getOffsetZ();
            return along >= -2 && along <= 42 && Math.abs(across) <= 22 && Math.abs(delta.getY()) <= 5;
        }
    }
    private record Inspection(UUID guard, BlockPos checkpoint, long expires, boolean sea) {}
    private static final class Travel {
        ServerWorld world;
        RailwayNetwork.Station station;
        Port port;
        UUID vessel;
        Delivery delivery;
        Inspection inspection;
    }

    static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 20 != 0 || world.getPlayers().isEmpty()) return;
            var ports = ports(world);
            for (var player : world.getPlayers()) {
                if (!eligible(player)) continue;
                var travel = state(world, player);
                inspect(world, player, travel);
                var vehicle = player.getVehicle() instanceof RailSeatEntity seat ? seat.train()
                        : RailPassengerNetwork.walkingVehicle(player);
                if (vehicle != null && vehicle.isShip()) {
                    if (vehicle.anchored() && Math.abs(vehicle.speed) < .025) {
                        for (var port : ports) if (port.contains(vehicle.getBlockPos())) {
                            dock(world, port, player, vehicle);
                            break;
                        }
                    }
                    continue;
                }
                // A passenger boarding at the first station may never have received an arrival event.
                if (player.hasVehicle() || RailPassengerNetwork.active(player)) continue;
                for (var station : RailwayNetwork.get(world).stations) {
                    if (insideStation(station, player.getBlockPos())) {
                        travel.station = station;
                        break;
                    }
                }
            }
        });
    }

    private static boolean insideStation(RailwayNetwork.Station station, BlockPos pos) {
        var delta = pos.subtract(station.origin);
        int along = delta.getX() * station.forward().getOffsetX() + delta.getZ() * station.forward().getOffsetZ();
        int across = delta.getX() * station.side().getOffsetX() + delta.getZ() * station.side().getOffsetZ();
        return along >= -4 && along <= 65 && across >= 0 && across <= 25
                && delta.getY() >= -1 && delta.getY() <= 9;
    }

    private static Travel state(ServerWorld world, ServerPlayerEntity player) {
        Travel travel = TRAVELLERS.get(player);
        if (travel == null || travel.world != world) {
            travel = new Travel();
            travel.world = world;
            TRAVELLERS.put(player, travel);
        }
        return travel;
    }

    private static boolean eligible(ServerPlayerEntity player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && !player.getCommandTags().contains("halloween_player_custody")
                && !player.getCommandTags().contains("halloween_player_jailed");
    }

    static boolean restricted(long village) {
        long key = village ^ (village >>> 31);
        return (Long.rotateLeft(key * 0x9e3779b97f4a7c15L, 13) & 3L) < 2;
    }

    static boolean restrictedDestination(RailwayNetwork.Station from, RailwayNetwork.Station to) {
        return from != null && from.village != to.village && restricted(to.village);
    }

    static void arrive(ServerWorld world, RailwayNetwork.Station station, ServerPlayerEntity player) {
        if (!eligible(player)) return;
        Travel travel = state(world, player);
        boolean border = restrictedDestination(travel.station, station);
        if (travel.station != null && travel.station.village != station.village) travel.inspection = null;
        travel.station = station;
        if (border) cross(world, player, travel, station.village, station.pos(12, 3, 30), null);
    }

    private static java.util.List<Port> ports(ServerWorld world) {
        var ports = new java.util.ArrayList<Port>();
        for (var settlement : VillageEraData.get(world).settlements.values()) {
            var xp = settlement.xp;
            if (!xp.containsKey("shipyard_x")) continue;
            var base = new BlockPos(xp.get("shipyard_x"), xp.get("shipyard_y"), xp.get("shipyard_z"));
            if (!world.isChunkLoaded(base) || world.getPlayers().stream().noneMatch(p -> base.isWithinDistance(p.getBlockPos(), 80))) continue;
            if (VillageProjects.get(world).jobs.stream().noneMatch(job -> job.done && job.id.equals(NavalWorks.id(settlement.village)))) continue;
            ports.add(new Port(settlement.village, new NavalWorks.Site(base,
                    net.minecraft.util.math.Direction.fromHorizontalQuarterTurns(xp.get("shipyard_facing")))));
        }
        return ports;
    }

    static void dock(ServerWorld world, Port port, ServerPlayerEntity player, RailVehicleEntity ship) {
        if (!eligible(player)) return;
        Travel travel = state(world, player);
        Port previous = travel.port;
        boolean sameVessel = ship.getUuid().equals(travel.vessel);
        if (sameVessel && previous != null && previous.village() == port.village()) return;
        travel.port = port;
        travel.vessel = ship.getUuid();
        if (!sameVessel || previous == null) return;
        travel.inspection = null;
        if (restricted(port.village())) cross(world, player, travel, port.village(), port.checkpoint(), ship);
    }

    private static void cross(ServerWorld world, ServerPlayerEntity player, Travel travel, long village,
                              BlockPos checkpoint, RailVehicleEntity ship) {
        boolean sea = ship != null;
        player.sendMessage(Text.literal("§eЗдесь запрещён ввоз огнестрельного оружия. "
                + (sea ? "На причале досматривают пассажиров." : "Вокзал досматривает багаж.")), false);
        boolean musket = player.getInventory().count(VillageItems.MUSKET) > 0 || stowed(ship, VillageItems.MUSKET);
        boolean revolver = player.getInventory().count(VillageItems.REVOLVER) > 0 || stowed(ship, VillageItems.REVOLVER);
        if (musket || revolver) {
            travel.delivery = new Delivery(village, world.getTime() + SALE_WINDOW, musket, revolver);
            player.sendMessage(Text.literal("§7Скупщик ждёт у дальней правой бочки рынка. Shift + ПКМ с оружием в руке."), false);
        }
        if (world.random.nextInt(3) != 0) return;
        var guard = world.getEntitiesByClass(VillagerEntity.class, new Box(checkpoint).expand(28, 7, 33),
                g -> VillageDefense.isGuard(g) && !VillageDefense.escorting(g) && !VillageDefense.engaged(g) && !g.hasVehicle())
                .stream().min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
        if (guard == null) return;
        travel.inspection = new Inspection(guard.getUuid(), checkpoint, world.getTime() + 280, sea);
        player.sendMessage(Text.literal(sea ? "§6Портовая стража: после высадки остановитесь для досмотра."
                : "§6Вокзальная стража: остановитесь для досмотра."), false);
    }

    static boolean stowed(RailVehicleEntity ship, Item item) {
        if (ship == null) return false;
        if (ship.naval().cargo.count(item) > 0) return true;
        for (Inventory barrel : ship.naval().barrels) if (barrel.count(item) > 0) return true;
        return false;
    }

    private static void inspect(ServerWorld world, ServerPlayerEntity player, Travel travel) {
        var inspection = travel.inspection;
        if (inspection == null) return;
        if (world.getTime() >= inspection.expires || !inspection.checkpoint.isWithinDistance(player.getBlockPos(), inspection.sea ? 60 : 40)
                || !(world.getEntity(inspection.guard) instanceof VillagerEntity guard) || !VillageDefense.isGuard(guard)
                || VillageDefense.escorting(guard) || VillageDefense.engaged(guard)) {
            travel.inspection = null;
            return;
        }
        if (player.hasVehicle() || RailPassengerNetwork.active(player)) return;
        if (guard.squaredDistanceTo(player) > 6.25 || !guard.canSee(player)) {
            if (VillagePathBudget.take(world)) guard.getNavigation().startMovingTo(player, .8);
            return;
        }
        travel.inspection = null;
        int moved = confiscate(player.getInventory(), guard.getInventory());
        if (moved == 0) {
            player.sendMessage(Text.literal(firstWeapon(player.getInventory()) < 0
                    ? "§7Досмотр окончен. Запрещённого груза нет."
                    : "§7Досмотр прерван. У патруля нет места для изъятого груза."), false);
        } else {
            travel.delivery = null;
            PlayerJustice.smugglingWarrant(player, inspection.checkpoint, inspection.sea);
            player.sendMessage(Text.literal("§cГвардия изъяла оружие: " + moved), false);
        }
    }

    static ActionResult sell(ServerWorld world, ServerPlayerEntity player, MarketCrime.Stall stall) {
        if (!eligible(player) || !player.isSneaking() || !weapon(player.getMainHandStack())) return ActionResult.PASS;
        BlockPos counter = VillageMarkets.origin(stall.settlement()).add(10, 1, 11);
        if (!counter.equals(stall.barrel()) || !restricted(stall.settlement().village)) return ActionResult.PASS;
        Travel travel = state(world, player);
        var delivery = travel.delivery;
        boolean revolver = player.getMainHandStack().isOf(VillageItems.REVOLVER);
        if (delivery == null || delivery.village != stall.settlement().village || world.getTime() >= delivery.expires
                || !(revolver ? delivery.revolver : delivery.musket)) {
            player.sendMessage(Text.literal("§7Скупщик берёт только оружие, привезённое из другого города."), true);
            return ActionResult.SUCCESS;
        }
        if (!world.getEntitiesByClass(VillagerEntity.class, new Box(counter).expand(10, 5, 10),
                g -> VillageDefense.isGuard(g) && g.canSee(player)).isEmpty()) {
            player.sendMessage(Text.literal("§7Скупщик: «Не здесь. Стража рядом»."), true);
            return ActionResult.SUCCESS;
        }
        var xp = stall.settlement().xp;
        int day = (int)(world.getTime() / 24000);
        int sold = xp.getOrDefault(SALE_DAY, -1) == day ? xp.getOrDefault(SALE_COUNT, 0) : 0;
        if (sold >= 3) {
            player.sendMessage(Text.literal("§7Скупщик: «На сегодня хватит»."), true);
            return ActionResult.SUCCESS;
        }
        var gun = player.getMainHandStack();
        int price = revolver ? 9 : 6;
        gun.decrement(1);
        travel.delivery = null;
        xp.put(SALE_DAY, day);
        xp.put(SALE_COUNT, sold + 1);
        VillageEraData.get(world).markDirty();
        var payout = new ItemStack(Items.EMERALD, price);
        player.getInventory().insertStack(payout);
        if (!payout.isEmpty()) player.dropItem(payout, false);
        player.sendMessage(Text.literal("§aСкупщик заплатил " + price + " изумрудов."), true);
        return ActionResult.SUCCESS;
    }

    static int confiscate(Inventory passenger, Inventory evidence) {
        int count = 0;
        for (int i = 0; i < passenger.size(); i++) {
            var stack = passenger.getStack(i);
            if (!weapon(stack)) continue;
            for (int j = 0; j < evidence.size(); j++) {
                if (!evidence.getStack(j).isEmpty() || !evidence.isValid(j, stack)) continue;
                evidence.setStack(j, passenger.removeStack(i, 1));
                count++;
                break;
            }
        }
        if (count > 0) {
            passenger.markDirty();
            evidence.markDirty();
        }
        return count;
    }

    private static int firstWeapon(Inventory inventory) {
        for (int i = 0; i < inventory.size(); i++) if (weapon(inventory.getStack(i))) return i;
        return -1;
    }

    private static boolean weapon(ItemStack stack) {
        return stack.isOf(VillageItems.MUSKET) || stack.isOf(VillageItems.REVOLVER);
    }

    private Contraband() {}
}
