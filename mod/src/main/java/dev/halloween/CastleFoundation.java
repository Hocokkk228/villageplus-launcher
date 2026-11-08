package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.*;
import net.minecraft.structure.pool.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/** Fill unsupported space directly below the castle floor without touching its dungeon. */
final class CastleFoundation {
    private static final class Work {
        final ChunkPos chunk;final BlockBox castle;final List<BlockBox> dungeons;int column;
        Work(ChunkPos chunk,BlockBox castle,List<BlockBox> dungeons){this.chunk=chunk;this.castle=castle;this.dungeons=dungeons;}
    }
    private static final Map<ServerWorld,ArrayDeque<Work>> QUEUES=new WeakHashMap<>();
    static void register(){ServerChunkEvents.CHUNK_LOAD.register(CastleFoundation::onLoad);ServerTickEvents.END_WORLD_TICK.register(CastleFoundation::tick);}
    private static Identifier id(StructurePoolElement element){
        if(element instanceof SinglePoolElement single)try{return single.getIdOrThrow();}catch(RuntimeException ignored){return null;}
        if(element instanceof ListPoolElement list)for(var child:list.getElements()){Identifier id=id(child);if(id!=null)return id;}
        return null;
    }
    private static void onLoad(ServerWorld w,WorldChunk chunk){
        if(!w.getRegistryKey().equals(World.OVERWORLD)||!chunk.hasStructureReferences())return;
        var starts=w.getStructureAccessor().getStructureStarts(chunk.getPos(),s->true);
        for(StructureStart start:starts){
            List<BlockBox> castles=new ArrayList<>(),dungeons=new ArrayList<>();
            for(StructurePiece piece:start.getChildren())if(piece instanceof PoolStructurePiece pool){
                Identifier id=id(pool.getPoolElement());if(id==null||!id.getNamespace().equals("halloween"))continue;
                if(id.getPath().endsWith("/dungeon"))dungeons.add(piece.getBoundingBox());
                else if(id.getPath().endsWith("/royal_castle")||id.getPath().contains("/houses/"))castles.add(piece.getBoundingBox());
            }
            for(BlockBox box:castles){
                if(box.getMaxX()<chunk.getPos().getStartX()||box.getMinX()>chunk.getPos().getEndX()||box.getMaxZ()<chunk.getPos().getStartZ()||box.getMinZ()>chunk.getPos().getEndZ())continue;
                QUEUES.computeIfAbsent(w,k->new ArrayDeque<>()).add(new Work(chunk.getPos(),box,List.copyOf(dungeons)));
            }
        }
    }
    private static void tick(ServerWorld w){
        var queue=QUEUES.get(w);if(queue==null||queue.isEmpty()||w.getPlayers().isEmpty())return;
        int budget=64;
        while(budget-->0&&!queue.isEmpty()){
            Work work=queue.peek();if(!w.isChunkLoaded(work.chunk.toLong())){queue.remove();continue;}
            if(work.column>=256){queue.remove();continue;}
            int index=work.column++,x=work.chunk.getStartX()+(index&15),z=work.chunk.getStartZ()+(index>>4),floor=work.castle.getMinY();
            if(x<work.castle.getMinX()||x>work.castle.getMaxX()||z<work.castle.getMinZ()||z>work.castle.getMaxZ())continue;
            if(work.dungeons.stream().anyMatch(b->x>=b.getMinX()-1&&x<=b.getMaxX()+1&&z>=b.getMinZ()-1&&z<=b.getMaxZ()+1))continue;
            BlockPos base=new BlockPos(x,floor,z);var floorState=w.getBlockState(base);
            if(!floorState.isSolidBlock(w,base)||floorState.hasBlockEntity())continue;
            if(!w.getOtherEntities(null,new Box(x,floor-64,z,x+1,floor+1,z+1),e->e instanceof LivingEntity&&e.isAlive()).isEmpty())continue;
            int solidDepth=0;
            for(int depth=1;depth<=64&&floor-depth>=w.getBottomY();depth++){
                BlockPos p=base.down(depth);var old=w.getBlockState(p);
                if(old.isAir()||old.isOf(Blocks.WATER)||old.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)||old.isIn(net.minecraft.registry.tag.BlockTags.LOGS)||old.isReplaceable()){solidDepth=0;w.setBlockState(p,Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);continue;}
                if(old.hasBlockEntity()||!old.isIn(net.minecraft.registry.tag.BlockTags.DIRT)&&!old.isOf(Blocks.STONE)&&!old.isOf(Blocks.DEEPSLATE)&&!old.isOf(Blocks.GRAVEL)&&!old.isOf(Blocks.SAND))break;
                // A thin terrain shelf is not a foundation: continue to the ground below it.
                if(++solidDepth>=6)break;
            }
        }
    }
    private CastleFoundation(){}
}

