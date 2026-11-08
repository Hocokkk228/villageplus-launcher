package dev.halloween;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

final class SteamHouseAccess {
    static boolean queue(ServerWorld world, VillageMemory.House house) {
        String key=house.recordKey;
        if (!house.kind.equals("house") || !(key.startsWith("gothic_rebuild:") || key.startsWith("gothic_infill_")
                || key.startsWith("gothic_expansion:") || key.startsWith("steam_residence_"))) return false;
        int width=house.max.getX()-house.min.getX()+1;
        int depth=house.max.getZ()-house.min.getZ()+1;
        String template=width==17&&depth==15?"steam_villa":width==13&&depth==13?"steam_townhouse":null;
        if(template==null)return false;
        var projects=VillageProjects.get(world);
        String id="steam_house_access:"+house.min.asLong();

        var team=VillageEras.team(world,house.min);
        if(team==null)return false;
        int center=width/2;
        var door=house.blueprint.stream().filter(r->r.pos().getX()==house.min.getX()+center&&r.pos().getZ()==house.min.getZ()+2
                &&r.state().getBlock() instanceof DoorBlock&&r.state().get(DoorBlock.HALF)==DoubleBlockHalf.LOWER).findFirst().orElse(null);
        if(door==null)return false;
        var origin=door.pos().add(-center,-1,-2);
        var layout=VillageEraPlans.template(world,template);
        if(layout==null)return false;
        if(!projects.exists(id)){
            var changes=plan(world,origin,center,layout);
            projects.add(world,id,team.village,"factory_supplies",changes);
            if(projects.exists(id))return true;
        }
        String stepsId="steam_house_steps:"+house.min.asLong();
        if(projects.exists(stepsId))return false;
        var stairs=new LinkedHashMap<BlockPos,BlockState>();
        for(int lane=center-1;lane<=center;lane++)RailStationSafety.groundStairs(world,origin.add(lane,0,0),
                net.minecraft.util.math.Direction.NORTH,stairs);
        stairs.entrySet().removeIf(e->world.getBlockState(e.getKey()).equals(e.getValue()));
        projects.add(world,stepsId,team.village,"factory_supplies",stairs);
        return projects.exists(stepsId);
    }

    static Map<BlockPos,BlockState> plan(ServerWorld world,BlockPos origin,int center,VillageEraPlans.Template layout) {
        var result=new LinkedHashMap<BlockPos,BlockState>();
        for(var record:layout.blocks()){
            var pos=record.pos();
            int x=pos.getX(),y=pos.getY(),z=pos.getZ();
            boolean lamp=z==3&&(x==center&&(y==2||y==3||y==6||y==7)
                    ||x==center+2&&(y==1||y==2||y==5||y==6));
            boolean tread=x>=center-1&&x<=center&&z>=5&&z<=8&&y>=z-4&&y<=z-1;
            boolean doorway=x==center&&(y==1||y==2)&&z==2;
            if(!lamp&&!tread&&!doorway)continue;
            var target=origin.add(pos);
            if(!world.isChunkLoaded(target))return Map.of();
            var state=world.getBlockState(target);
            var desired=record.state();
            if(state.equals(desired)||state.hasBlockEntity()||!state.getFluidState().isEmpty())continue;
            boolean oldLamp=lamp&&x==center&&(state.isOf(Blocks.STONE_BRICK_WALL)||state.isOf(Blocks.LANTERN));
            boolean oldHeadroom=tread&&desired.isAir()&&state.isOf(Blocks.SPRUCE_PLANKS);
            boolean oldGlazing=doorway&&y==2&&state.isOf(Blocks.LIGHT_BLUE_STAINED_GLASS);
            if(oldLamp||oldHeadroom||oldGlazing||state.isAir()&&!desired.isAir())result.put(target,desired);
        }
        return result;
    }

    private SteamHouseAccess(){}
}
