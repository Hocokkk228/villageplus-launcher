package dev.halloween;

import java.util.Set;
import net.minecraft.block.*;

/** Only building footprints reserve plots. Roads, demolitions and historical conversion bounds do not. */
final class VillageBuildingSites {
    static boolean building(String kind) {
        return Set.of("house","factory","cathedral","treasury","plantation","market","stable","rail_station","rail_foundry","shipyard","relocation_copy","industrial_factory","industrial_school","industrial_shop","courthouse").contains(kind) || kind.startsWith("workshop_");
    }
    static boolean reserves(VillageMemory.House house) {
        if (house.kind.equals("infrastructure") || house.recordKey.endsWith(":clear")) return false;
        if (house.kind.equals("era")) return building(house.recordKey.startsWith("gothic_rebuild:") ? "house" : house.kind)
                || house.recordKey.startsWith("industrial_district:")&&(house.recordKey.endsWith(":school")||house.recordKey.endsWith(":shop"))
                || house.recordKey.startsWith("industrial_courthouse:")
                || house.recordKey.startsWith("industrial_plant:") || house.recordKey.startsWith("factory_v1:") || house.recordKey.startsWith("era_treasury")
                || house.recordKey.startsWith("era_workshop:") || house.recordKey.startsWith("cologne_cathedral_");
        if (house.kind.equals("structure") && !house.recordKey.contains("/houses/")
                && !house.recordKey.contains("royal_castle") && !house.recordKey.contains("profession_")) return false;
        // A bed or foundation fragment left by a demolished house is not a surviving building.
        return house.blueprint.stream().filter(r -> !r.state().isAir() && !(r.state().getBlock() instanceof BedBlock)
                && !VillageProjects.terrain(r.state())).limit(24).count() >= 24;
    }
    static java.util.List<net.minecraft.util.math.Box> protectedVolumes(net.minecraft.server.world.ServerWorld world,net.minecraft.util.math.BlockPos min,net.minecraft.util.math.BlockPos max,String source){
        var volume=new net.minecraft.util.math.Box(net.minecraft.util.math.Vec3d.of(min),net.minecraft.util.math.Vec3d.of(max.add(1,1,1)));
        return VillageProjects.get(world).jobs.stream().filter(p->building(p.kind)&&!p.id.equals(source)&&!p.min.equals(min))
                .map(p->new net.minecraft.util.math.Box(net.minecraft.util.math.Vec3d.of(p.min),net.minecraft.util.math.Vec3d.of(p.max.add(1,1,1))))
                .filter(volume::intersects).toList();
    }
    static boolean protectedAt(java.util.List<net.minecraft.util.math.Box> volumes,net.minecraft.util.math.BlockPos pos){
        return volumes.stream().anyMatch(box->box.intersects(new net.minecraft.util.math.Box(pos)));
    }
    private VillageBuildingSites() {}
}
