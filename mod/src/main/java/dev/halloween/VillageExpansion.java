package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/** Plans small houses outside recorded buildings; the existing builder repair system constructs them. */
final class VillageExpansion {
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillageExpansion::tick);}
    private static void tick(ServerWorld w){
        if(!w.getRegistryKey().equals(World.OVERWORLD)||w.getPlayers().isEmpty()||w.getTime()%100!=0)return;
        if(!w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).teams.isEmpty())return; // Profession eras own expansion and its exact house cap.
        VillageMemory memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(var team:memory.teams){
            if(w.getPlayers().stream().noneMatch(p->p.squaredDistanceTo(Vec3d.ofCenter(team.anchor))<192*192
                ||team.finishedExpansions.stream().anyMatch(h->p.squaredDistanceTo(Vec3d.ofCenter(h))<128*128)))continue;
            int originals=(int)memory.houses.stream().filter(h->h.kind.equals("house")&&!team.expansions.contains(h.min)&&near(team.anchor,h.min,128)
                &&h.max.getX()-h.min.getX()<20&&h.max.getZ()-h.min.getZ()<20).count();
            if(originals>team.baseHouses){team.baseHouses=originals;memory.markDirty();}
            if(team.baseHouses==0)continue;
            boolean pending=false;
            for(BlockPos pos:team.expansions){
                if(team.finishedExpansions.contains(pos))continue;
                var h=memory.houses.stream().filter(house->house.min.equals(pos)).findFirst().orElse(null);
                if(h==null)continue;
                boolean complete=h.blueprint.stream().allMatch(r->w.isChunkLoaded(new ChunkPos(r.pos()).toLong())&&w.getBlockState(r.pos()).isOf(r.state().getBlock()));
                if(complete){team.finishedExpansions.add(pos);team.nextExpansion=w.getTimeOfDay()+100;memory.markDirty();}
                else pending=true;
            }
            if(pending||team.builders.isEmpty())continue;
            if(team.nextExpansion==0){team.nextExpansion=w.getTimeOfDay()+1200;memory.markDirty();continue;}
            if(w.getTimeOfDay()<team.nextExpansion)continue;
            var plan=findSite(w,memory,team);
            if(plan!=null){memory.houses.add(plan);team.expansions.add(plan.min);team.nextExpansion=w.getTimeOfDay()+100;memory.markDirty();}
        }
    }
    private static boolean near(BlockPos a,BlockPos b,int radius){long x=a.getX()-b.getX(),z=a.getZ()-b.getZ();return x*x+z*z<(long)radius*radius;}
    private static VillageMemory.House findSite(ServerWorld w,VillageMemory memory,VillageMemory.Team team){
        for(int attempt=0;attempt<70;attempt++){
            double angle=w.random.nextDouble()*Math.PI*2;
            BlockPos focus=team.anchor;int radius=w.random.nextBetween(35,120);
            if(!team.finishedExpansions.isEmpty()&&w.random.nextInt(4)!=0){focus=team.finishedExpansions.get(w.random.nextInt(team.finishedExpansions.size()));radius=w.random.nextBetween(12,32);}
            int x=focus.getX()+(int)(Math.cos(angle)*radius),z=focus.getZ()+(int)(Math.sin(angle)*radius);
            if(!w.isChunkLoaded(new ChunkPos(new BlockPos(x,team.anchor.getY(),z)).toLong()))continue;
            int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);
            BlockPos min=new BlockPos(x,y,z),max=min.add(6,8,6);
            if(!w.getWorldBorder().contains(min)||!w.getWorldBorder().contains(max)||y+9>w.getTopYInclusive())continue;
            boolean intersects=memory.houses.stream().anyMatch(h->min.getX()-5<=h.max.getX()&&max.getX()+5>=h.min.getX()&&min.getZ()-5<=h.max.getZ()&&max.getZ()+5>=h.min.getZ());
            if(intersects)continue;
            boolean good=true;
            outer:for(int dx=0;dx<7;dx++)for(int dz=0;dz<7;dz++){
                BlockPos ground=min.add(dx,-1,dz);if(!w.isChunkLoaded(new ChunkPos(ground).toLong())){good=false;break outer;}
                var floor=w.getBlockState(ground);
                if(!(floor.isIn(BlockTags.DIRT)||floor.isOf(Blocks.SAND)||floor.isOf(Blocks.RED_SAND))||!floor.isSolidBlock(w,ground)){good=false;break outer;}
                for(int dy=0;dy<=8;dy++){
                    BlockPos p=min.add(dx,dy,dz);var state=w.getBlockState(p);
                    if(!state.isAir()&&!(dy==0&&softPlant(state))){good=false;break outer;}
                }
            }
            if(!good||!w.getOtherEntities(null,new Box(x,y,z,x+7,y+9,z+7),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty())continue;
            var source=memory.houses.stream().filter(h->h.kind.equals("house")&&!team.expansions.contains(h.min)&&near(team.anchor,h.min,128)
                &&h.max.getX()-h.min.getX()<=6&&h.max.getZ()-h.min.getZ()<=6&&h.max.getY()-h.min.getY()<=8
                &&h.blueprint.size()>20)
                .min(Comparator.comparingDouble(h->h.min.getSquaredDistance(team.anchor))).orElse(null);
            if(source==null)continue;
            var blocks=source.blueprint.stream().filter(r->r.pos().getX()>=source.min.getX()&&r.pos().getX()<=source.max.getX()&&r.pos().getY()>=source.min.getY()&&r.pos().getY()<=source.max.getY()&&r.pos().getZ()>=source.min.getZ()&&r.pos().getZ()<=source.max.getZ())
                .map(r->new VillageMemory.BlockRecord(min.add(r.pos().subtract(source.min)),r.state())).toList();
            return new VillageMemory.House(min,min.add(source.max.subtract(source.min)),blocks,w.getTime(),List.of(),0,true,List.of());
        }
        return null;
    }
    static boolean softPlant(BlockState state){return state.isOf(Blocks.SHORT_GRASS)||state.isOf(Blocks.FERN)||state.isOf(Blocks.DEAD_BUSH);}
    private VillageExpansion(){}
}

