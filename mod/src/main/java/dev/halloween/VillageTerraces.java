package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Stone-era retaining walls and walkable descents from exposed plaza edges. */
final class VillageTerraces {
    private static final Map<MarkerEntity,Integer> CURSOR=new WeakHashMap<>();
    private static final Map<VillageProjects.Project,Integer> PAD_CURSOR=new WeakHashMap<>();
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillageTerraces::tick);}
    private static boolean paving(BlockState s){return s.isOf(Blocks.STONE_BRICKS)||s.isOf(Blocks.POLISHED_ANDESITE)||s.isOf(Blocks.SMOOTH_STONE)||s.isOf(Blocks.STONE);}
    private static boolean editable(BlockState s){return s.isAir()||VillageCivilWorks.natural(s)||s.getBlock() instanceof FluidBlock;}
    static Map<BlockPos,BlockState> support(ServerWorld w,BlockPos edge){
        Map<BlockPos,BlockState> plan=new LinkedHashMap<>();boolean gap=false;
        for(int depth=1;depth<=64;depth++){
            BlockPos p=edge.down(depth);if(p.getY()<=w.getBottomY()||!w.isChunkLoaded(new ChunkPos(p).toLong())||VillagePrisons.managed(w,p))return Map.of();
            var state=w.getBlockState(p);
            if(state.isSolidBlock(w,p)){
                if(gap) return plan;
                if(depth>=4)return Map.of();
                if(!VillageCivilWorks.natural(state))return Map.of();
            }else gap=true;
            if(!editable(state)||state.hasBlockEntity()||VillageProjects.pending(w,p))return Map.of();
            plan.put(p,Blocks.STONE_BRICKS.getDefaultState());
        }return Map.of(); // Do not build an unfinished pillar over an unbounded drop.
    }
    static Map<BlockPos,BlockState> stairs(ServerWorld w,BlockPos edge,Direction out){
        Map<BlockPos,BlockState> plan=new LinkedHashMap<>();Direction side=out.rotateYClockwise();
        for(int step=1;step<=48;step++){
            BlockPos foot=edge.offset(out,step).up(1-step);
            if(foot.getY()<=w.getBottomY()+1||!w.isChunkLoaded(new ChunkPos(foot).toLong()))return Map.of();
            boolean landed=step>1;
            for(int width=-1;width<=1;width++){
                BlockPos q=foot.offset(side,width);
                landed&=w.getBlockState(q.down()).isSolidBlock(w,q.down())&&w.getFluidState(q).isEmpty()&&w.getBlockState(q).getCollisionShape(w,q).isEmpty()&&w.getBlockState(q.up()).getCollisionShape(w,q.up()).isEmpty();
            }
            if(landed)return plan;
            for(int width=-1;width<=1;width++){
                BlockPos q=foot.offset(side,width);
                for(BlockPos p:List.of(q.down(),q,q.up())){
                    if(!w.isChunkLoaded(new ChunkPos(p).toLong())||!editable(w.getBlockState(p))||w.getBlockState(p).hasBlockEntity()||VillagePrisons.managed(w,p)||VillageProjects.pending(w,p))return Map.of();
                }
                plan.put(q.down(),Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING,out.getOpposite()));plan.put(q,Blocks.AIR.getDefaultState());plan.put(q.up(),Blocks.AIR.getDefaultState());
                for(int d=2;d<=12;d++){var under=q.down(d);if(w.getBlockState(under).isSolidBlock(w,under))break;if(!editable(w.getBlockState(under))||VillagePrisons.managed(w,under))return Map.of();plan.put(under,Blocks.STONE_BRICKS.getDefaultState());}
            }
        }return Map.of();
    }
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty()||w.getTime()%100!=0)return;
        reinforcePads(w);
        for(var player:w.getPlayers())for(var square:w.getEntitiesByClass(MarkerEntity.class,new Box(player.getBlockPos()).expand(192),m->m.getCommandTags().contains("halloween_square"))){
            var team=VillageEras.team(w,square.getBlockPos());if(team==null||VillageEraData.get(w).settlement(team).era<2)continue;
            var projects=VillageProjects.get(w);if(projects.jobs.stream().anyMatch(p->p.village==team.village&&(p.kind.equals("terrace_support")||p.kind.equals("terrace_stairs"))&&!p.done))continue;
            int start=CURSOR.getOrDefault(square,0);Map<BlockPos,BlockState> supports=new LinkedHashMap<>();int y=square.getBlockY()-1;
            for(int i=0;i<128&&supports.size()<1024;i++){
                int index=Math.floorMod(start+i,65*65);BlockPos edge=new BlockPos(square.getBlockX()+index%65-32,y,square.getBlockZ()+index/65-32);
                if(!w.isChunkLoaded(new ChunkPos(edge).toLong())||!paving(w.getBlockState(edge)))continue;
                for(Direction out:Direction.Type.HORIZONTAL){
                    if(paving(w.getBlockState(edge.offset(out)))||!w.getBlockState(edge.up()).getCollisionShape(w,edge.up()).isEmpty())continue;
                    var wall=support(w,edge);if(wall.isEmpty())continue;supports.putAll(wall);
                    String id="terrace_stairs:"+square.getUuidAsString()+":"+out.asString();
                    if(!projects.exists(id)){var stairs=stairs(w,edge,out);if(!stairs.isEmpty())projects.add(w,id,team.village,"terrace_stairs",stairs);}
                }
            }
            CURSOR.put(square,start+128);
            if(!supports.isEmpty())projects.add(w,"terrace_support:"+square.getUuidAsString()+":"+start,team.village,"terrace_support",supports);
        }
    }
    private static void reinforcePads(ServerWorld w){
        var projects=VillageProjects.get(w);var pads=new ArrayList<>(projects.jobs.stream().filter(p->p.done&&p.index>=p.plan.size()&&(p.kind.equals("farm_plaza")||p.kind.equals("treasury"))).toList());
        if(pads.isEmpty())return;Collections.rotate(pads,-(int)((w.getTime()/100)%pads.size()));
        for(var pad:pads.subList(0,Math.min(4,pads.size()))){
            var settlement=VillageEraData.get(w).settlements.get(pad.village);
            if(settlement==null||settlement.era<2||w.getPlayers().stream().noneMatch(p->p.getBlockPos().isWithinDistance(pad.min,256))||projects.jobs.stream().anyMatch(p->p.village==pad.village&&p.kind.startsWith("terrace_")&&!p.done))continue;
            int start=PAD_CURSOR.getOrDefault(pad,0);Map<BlockPos,BlockState> supports=new LinkedHashMap<>();
            for(int i=0;i<48&&i<pad.plan.size()&&supports.size()<512;i++){
                var c=pad.plan.get(Math.floorMod(start+i,pad.plan.size()));var edge=c.pos();
                if(!w.isChunkLoaded(new ChunkPos(edge).toLong())||!w.getBlockState(edge).equals(c.after())||!paving(c.after())&&!c.after().isOf(Blocks.DIRT_PATH)||!w.getBlockState(edge.up()).getCollisionShape(w,edge.up()).isEmpty())continue;
                for(Direction out:Direction.Type.HORIZONTAL){
                    var adjacent=w.getBlockState(edge.offset(out));if(paving(adjacent)||adjacent.isOf(Blocks.DIRT_PATH))continue;
                    var wall=support(w,edge);if(wall.isEmpty())continue;supports.putAll(wall);
                    String id="terrace_stairs:"+pad.id+":"+out.asString();if(!projects.exists(id)){var descent=stairs(w,edge,out);if(!descent.isEmpty())projects.add(w,id,pad.village,"terrace_stairs",descent);}
                }
            }
            PAD_CURSOR.put(pad,start+48);if(!supports.isEmpty())projects.add(w,"terrace_support:"+pad.id+":"+start,pad.village,"terrace_support",supports);
        }
    }
    private VillageTerraces(){}
}
