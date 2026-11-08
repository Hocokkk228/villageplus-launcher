package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;

/** Builders backfill exposed hazards near the settlement; irrigation and underground rooms are preserved. */
final class VillageGroundSafety {
    private static final Map<VillageMemory.Team,Integer> CURSOR=new WeakHashMap<>();
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillageGroundSafety::tick);}
    static Map<BlockPos,BlockState> column(ServerWorld w,int x,int z){
        Map<BlockPos,BlockState> plan=new LinkedHashMap<>();
        if(!w.isChunkLoaded(ChunkPos.toLong(x>>4,z>>4)))return plan;
        int top=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
        BlockPos surface=new BlockPos(x,top,z);var old=w.getBlockState(surface);
        if(!w.isSkyVisible(surface.up())||old.hasBlockEntity())return plan;
        // A pit must have a dry rim in ALL four directions. A one-sided hill is not a pit.
        int rim=Integer.MAX_VALUE;
        for(Direction d:Direction.Type.HORIZONTAL){
            int bank=Integer.MIN_VALUE;
            for(int n:new int[]{2,4,6}){
                int bx=x+d.getOffsetX()*n,bz=z+d.getOffsetZ()*n;
                if(!w.isChunkLoaded(ChunkPos.toLong(bx>>4,bz>>4)))return plan;
                int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,bx,bz)-1;
                var p=new BlockPos(bx,y,bz);var block=w.getBlockState(p);
                if(VillageProjects.terrain(block)&&!block.hasBlockEntity()&&w.getFluidState(p).isEmpty()&&y-top>=3&&y-top<=8)bank=Math.max(bank,y);
            }
            if(bank==Integer.MIN_VALUE)return plan;
            rim=Math.min(rim,bank);
        }
        boolean water=old.getBlock() instanceof FluidBlock;
        int bottom=top;
        if(water){for(int i=0;i<16;i++){var p=new BlockPos(x,bottom,z);if(w.getBlockState(p).isSolidBlock(w,p))break;bottom--;}}
        if(rim-bottom>16)return plan;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(int y=bottom+1;y<=rim;y++){
            var p=new BlockPos(x,y,z);var state=w.getBlockState(p);
            if(VillagePrisons.managed(w,p)||state.hasBlockEntity()||!state.isAir()&&!state.isReplaceable()&&!(state.getBlock() instanceof FluidBlock))return Map.of();
            if(VillageProjects.pending(w,p)||VillageProjects.get(w).expected.containsKey(p)&&!VillageProjects.get(w).expected.get(p).isAir()&&!VillageProjects.terrain(VillageProjects.get(w).expected.get(p))||memory.houses.stream().anyMatch(h->h.kind.equals("house")&&x>=h.min.getX()&&x<=h.max.getX()&&z>=h.min.getZ()&&z<=h.max.getZ()))return Map.of();
            for(BlockPos q:BlockPos.iterateOutwards(p,4,1,4))if(w.getBlockState(q).isOf(Blocks.FARMLAND))return Map.of();
            var wanted=VillageProjects.get(w).expected.get(p);
            plan.put(p,y==rim&&wanted!=null&&wanted.isOf(Blocks.DIRT_PATH)?wanted:y==rim?Blocks.GRASS_BLOCK.getDefaultState():Blocks.DIRT.getDefaultState());
        }
        return plan;
    }
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty()||w.getTime()%100!=0)return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
        for(var team:memory.teams){
            // Settlement construction owns the crew while any building/transition is unfinished.
            if(projects.jobs.stream().anyMatch(p->p.village==team.village&&!p.done&&!Set.of("ground_safety","road","terrace_support","terrace_stairs","perimeter").contains(p.kind)))continue;
            if(w.getPlayers().stream().noneMatch(p->p.getBlockPos().isWithinDistance(team.anchor,256))||projects.jobs.stream().anyMatch(p->p.village==team.village&&p.kind.equals("ground_safety")&&!p.done))continue;
            Map<BlockPos,BlockState> plan=new LinkedHashMap<>();
            var people=w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(128,64,128),v->v.isAlive()&&(v.isTouchingWater()||v.isOnGround()));
            for(var person:people.stream().limit(12).toList())for(Direction d:Direction.Type.HORIZONTAL){var p=person.getBlockPos().offset(d,2);plan.putAll(column(w,p.getX(),p.getZ()));if(plan.size()>192)break;}
            int start=CURSOR.getOrDefault(team,0);
            for(int i=0;i<96&&plan.size()<256;i++){
                int index=Math.floorMod(start+i,192*192),x=team.anchor.getX()+index%192-96,z=team.anchor.getZ()+index/192-96;plan.putAll(column(w,x,z));
            }
            CURSOR.put(team,start+96);
            if(!plan.isEmpty())projects.add(w,"ground_safety:"+team.village+":"+w.getTime(),team.village,"ground_safety",plan);
        }
    }
    private VillageGroundSafety(){}
}
