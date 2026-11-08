package dev.halloween;

import java.util.Comparator;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;

final class VillageSchool {
    private static final WeakHashMap<VillagerEntity, Long> LESSON = new WeakHashMap<>();

    static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            long hour = Math.floorMod(world.getTimeOfDay(), 24000);
            if (world.getTime() % 20 != 0 || hour < 2000 || hour >= 6000 || world.getPlayers().isEmpty()) return;
            var data = VillageEraData.get(world);
            for (var team : world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).teams) {
                var settlement = data.settlements.get(team.village);
                if (!VillageIndustrial.civic(settlement)) continue;
                var school = VillageIndustrial.schoolSite(world, settlement);
                if (school != null && WorldSettlements.loadedNearby(world, school)) teach(world, settlement, school);
            }
        });
    }

    static int teach(ServerWorld world, VillageEraData.Settlement settlement, BlockPos school) {
        var people = world.getEntitiesByClass(VillagerEntity.class, new Box(school).expand(72, 48, 72),
                v -> v.isAlive() && !v.isSleeping() && v.getCustomer() == null && !v.hasVehicle()
                        && !VillageInjuries.panicking(v) && !VillageTrials.busy(v)
                        && VillageEras.belongs(world, v, settlement.village)
                        && (!VillageTasks.ownsMovement(v) || LESSON.getOrDefault(v, -1L) > world.getTime()));
        var teacher = people.stream().filter(v -> !v.isBaby() && v.getVillagerData().profession().matchesKey(VillagerProfession.LIBRARIAN))
                .min(Comparator.comparingDouble(v -> v.squaredDistanceTo(Vec3d.ofCenter(school)))).orElse(null);
        var students = people.stream().filter(VillagerEntity::isBaby).sorted(Comparator.comparing(VillagerEntity::getUuid))
                .limit(12).toList();
        if (teacher == null || students.isEmpty()) return 0;
        var board = Vec3d.ofCenter(school.add(7, 3, 1));
        attend(world, teacher, school.add(7, 1, 2), Vec3d.ofCenter(school.add(7, 1, 8)));
        for (int i = 0; i < students.size(); i++) {
            attend(world, students.get(i), school.add(3 + i % 4 * 3, 1, 7 + i / 4 * 3), board);
        }
        return students.size();
    }

    private static void attend(ServerWorld world, VillagerEntity villager, BlockPos position, Vec3d look) {
        VillageTasks.hold(villager);
        LESSON.put(villager, world.getTime() + 40);
        var spot = Vec3d.ofBottomCenter(position);
        if (villager.squaredDistanceTo(spot) < 1) {
            villager.getNavigation().stop();
            villager.getLookControl().lookAt(look.x, look.y, look.z);
        } else if (villager.getNavigation().isIdle() && VillagePathBudget.take(world)) {
            villager.getNavigation().setCanOpenDoors(true);
            villager.getNavigation().startMovingTo(spot.x, spot.y, spot.z, .55);
        }
    }

    private VillageSchool() {}
}
