package dev.halloween;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

final class IndustrialCityGrowth {
    static final int TARGET = 1500, TOLERANCE = 50;
    private static final int WIDTH=27, DEPTH=40, GRID=47;
    private static final String PREFIX="industrial_sector:", SEARCH="industrial_sector_search";
    private static final String RADIUS="industrial_city_extent";

    static boolean finished(long houses) { return houses>=TARGET-TOLERANCE; }

    static int radius(VillageEraData.Settlement settlement,int normal) {
        return settlement==null?normal:Math.max(normal,settlement.xp.getOrDefault(RADIUS,normal));
    }

    static boolean playerNear(ServerWorld world,VillageMemory.Team team,VillageEraData.Settlement settlement){
        double reach=radius(settlement,384)+448d;
        return world.getPlayers().stream().anyMatch(p->VillageEras.horizontal(p.getBlockPos(),team.anchor)<reach*reach);
    }

    static long houses(ServerWorld world,VillageMemory.Team team) {
        return world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses.stream()
                .filter(h->VillageEras.residential(h) && !VillageTerrain.removesHouse(world,h) && VillageEras.team(world,h.min)==team)
                .map(h->h.min).distinct().count();
    }

    static boolean panelProject(String id) { return id.startsWith(PREFIX)&&id.contains(":panel_"); }

    static void advance(ServerWorld world,VillageMemory.Team team,VillageEraData.Settlement settlement,
                        VillageEraPlans.Template template,long houses) {
        if(finished(houses))return;
        var jobs=VillageProjects.get(world);
        if(jobs.jobs.stream().anyMatch(p->p.village==team.village&&p.id.startsWith(PREFIX)&&!p.done))return;
        int cursor=settlement.xp.getOrDefault(SEARCH,0);
        var point=offset(cursor);
        int x=team.anchor.getX()+point.getX()*GRID-WIDTH/2;
        int z=team.anchor.getZ()+point.getZ()*GRID-DEPTH/2;
        var center=new BlockPos(x+WIDTH/2,team.anchor.getY(),z+DEPTH/2);
        var memory=world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        double distance=VillageEras.horizontal(team.anchor,center);
        boolean foreign=memory.teams.stream().anyMatch(other->other!=team&&VillageEras.horizontal(other.anchor,center)<distance);
        if(foreign || memory.houses.stream().filter(VillageBuildingSites::reserves).anyMatch(h->overlap(h.min,h.max,x,z))
                || jobs.jobs.stream().filter(p->!p.done&&VillageBuildingSites.building(p.kind)).anyMatch(p->overlap(p.min,p.max,x,z))){
            next(world,settlement,cursor);return;
        }
        if(world.getPlayers().stream().noneMatch(p->VillageEras.horizontal(p.getBlockPos(),center)<448*448))return;
        boolean loaded=true;
        for(int cx=x>>4;cx<=(x+WIDTH-1)>>4;cx++)for(int cz=z>>4;cz<=(z+DEPTH-1)>>4;cz++)
            if(!VillageProjects.keepLoaded(world,new BlockPos(cx<<4,64,cz<<4)))loaded=false;
        if(!loaded)return;
        int surface=VillageEraPlans.levelSite(world,x,z,WIDTH,DEPTH,4);
        if(surface==Integer.MIN_VALUE||surface+25>world.getTopYInclusive()){
            next(world,settlement,cursor);return;
        }
        var base=new BlockPos(x,surface-1,z);
        var first=VillageIndustrial.panel(base.add(0,0,3),template);
        var second=VillageIndustrial.panel(base.add(0,0,22),template);
        var road=new LinkedHashMap<BlockPos,BlockState>();
        for(int dx=0;dx<WIDTH;dx++)for(int dz=0;dz<DEPTH;dz++){
            var ground=base.add(dx,0,dz);
            for(int down=1;down<=4;down++){
                var support=ground.down(down);
                if(world.getBlockState(support).isSolidBlock(world,support))break;
                road.put(support,Blocks.STONE_BRICKS.getDefaultState());
            }
            if(dx<17&&(dz>=3&&dz<20||dz>=22&&dz<39))continue;
            road.put(ground,(dx>=19&&dx<=24?Blocks.GRAY_CONCRETE:Blocks.SMOOTH_STONE).getDefaultState());
            for(int y=1;y<=3;y++)road.put(ground.up(y),Blocks.AIR.getDefaultState());
        }
        if(!VillageIndustrial.safe(world,Map.of("road",road,"a",first,"b",second))){
            next(world,settlement,cursor);return;
        }
        String id=PREFIX+team.village+":"+cursor+":";
        jobs.add(world,id+"road",team.village,"industrial_road",road);
        jobs.add(world,id+"panel_a",team.village,"house",first);
        jobs.add(world,id+"panel_b",team.village,"house",second);
        if(!jobs.exists(id+"road")||!jobs.exists(id+"panel_a")||!jobs.exists(id+"panel_b"))return;
        double extent=Math.sqrt(VillageEras.horizontal(team.anchor,base))
                + Math.hypot(WIDTH,DEPTH)+64;
        settlement.xp.merge(RADIUS,(int)Math.ceil(extent),Math::max);
        next(world,settlement,cursor);
    }

    /** Square rings grow with the number of accepted plots, without a fixed city footprint. */
    static BlockPos offset(int index) {
        int ring=(int)Math.ceil((Math.sqrt(index+2d)-1)/2);
        long inner=2L*ring-1;
        int slot=(int)(index-(inner*inner-1));
        int edge=2*ring;
        if(slot<edge)return new BlockPos(ring,0,-ring+slot);
        if(slot<edge*2)return new BlockPos(ring-(slot-edge),0,ring);
        if(slot<edge*3)return new BlockPos(-ring,0,ring-(slot-edge*2));
        return new BlockPos(-ring+(slot-edge*3),0,-ring);
    }

    private static void next(ServerWorld world,VillageEraData.Settlement settlement,int cursor){
        settlement.xp.put(SEARCH,cursor+1);
        VillageEraData.get(world).markDirty();
    }

    private static boolean overlap(BlockPos min,BlockPos max,int x,int z){
        return min.getX()<=x+WIDTH && max.getX()>=x-1 && min.getZ()<=z+DEPTH && max.getZ()>=z-1;
    }

    private IndustrialCityGrowth(){}
}
