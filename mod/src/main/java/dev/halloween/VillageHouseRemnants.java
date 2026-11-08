package dev.halloween;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Only beds belonging to recorded, completed house relocations may be removed. */
final class VillageHouseRemnants {
    static void tick(ServerWorld world,VillageMemory.Team team,VillageEraData.Settlement settlement){
        var memory=world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        var projects=VillageProjects.get(world);
        for(var house:List.copyOf(memory.houses)){
            if(!house.recordKey.startsWith("template:")||settlement.xp.getOrDefault(VillageHousingRebuild.id(team.village,house),0)<3)continue;
            String id="abandoned_beds_v4:"+team.village+":"+house.min.asLong();
            if(projects.jobs.stream().anyMatch(j->j.id.equals(id)&&j.done)){memory.houses.remove(house);memory.markDirty();continue;}
            if(projects.exists(id))continue;
            var plan=new LinkedHashMap<BlockPos,BlockState>();
            for(var record:house.blueprint)if(record.state().getBlock() instanceof BedBlock&&world.isChunkLoaded(record.pos())&&world.getBlockState(record.pos()).getBlock() instanceof BedBlock)plan.put(record.pos(),Blocks.AIR.getDefaultState());
            if(!plan.isEmpty())projects.add(world,id,team.village,"gothic_demolition",plan);
        }
    }
    private VillageHouseRemnants(){}
}
