package dev.halloween;

import java.util.LinkedHashMap;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;

final class VillageTreasuryRepair {
    static void tick(ServerWorld world,VillageMemory.Team team,VillageEraData.Settlement settlement){
        if(settlement.vault==null)return;
        var projects=VillageProjects.get(world);
        String id="treasury_restoration_v2:"+team.village;
        if(projects.exists(id)||!projects.complete(team.village,"treasury"))return;
        var template=VillageEraPlans.template(world,"stone_treasury");
        if(template==null)return;
        var facing=net.minecraft.util.math.Direction.fromHorizontalQuarterTurns(settlement.vault.facing);
        var rotation=switch(facing){
            case SOUTH->BlockRotation.NONE;case WEST->BlockRotation.CLOCKWISE_90;
            case NORTH->BlockRotation.CLOCKWISE_180;default->BlockRotation.COUNTERCLOCKWISE_90;
        };
        var plan=new LinkedHashMap<net.minecraft.util.math.BlockPos,BlockState>();
        for(var record:template.blocks()){
            var local=record.pos();int x=local.getX(),y=local.getY(),z=local.getZ();
            if(record.state().isAir())continue;
            var pos=VillageEraPlans.vaultPos(settlement.vault,z,9-x,y-1);
            if(!world.isChunkLoaded(pos)){VillageProjects.keepLoaded(world,pos);return;}
            var actual=world.getBlockState(pos);var wanted=record.state().rotate(rotation);
            if(VillageConnections.matches(world,pos,wanted,actual)||actual.hasBlockEntity())continue;
            var owned=projects.expected.get(pos);
            if(actual.isAir()||actual.isReplaceable()||VillageProjects.terrain(actual)||owned!=null&&owned.isOf(actual.getBlock()))
                plan.put(pos,wanted);
        }
        projects.add(world,id,team.village,"treasury",plan);
    }

    private VillageTreasuryRepair(){}
}
