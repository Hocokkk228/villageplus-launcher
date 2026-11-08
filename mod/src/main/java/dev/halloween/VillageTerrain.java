package dev.halloween;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.*;
import net.minecraft.structure.pool.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.WorldChunk;

/** Loaded-chunk earthworks: 50 flat blocks, followed by a slope no steeper than 1:2. */
final class VillageTerrain extends PersistentState {
    static final class Zone {
        static final Codec<Zone> CODEC=RecordCodecBuilder.create(i->i.group(BlockPos.CODEC.fieldOf("min").forGetter(z->z.min),BlockPos.CODEC.fieldOf("max").forGetter(z->z.max),Codec.LONG.listOf().optionalFieldOf("finished",List.of()).forGetter(z->new ArrayList<>(z.finished))).apply(i,Zone::new));
        final BlockPos min,max;final Set<Long> finished;
        Zone(BlockPos min,BlockPos max,List<Long> done){this.min=min;this.max=max;finished=new HashSet<>(done);}
    }
    static final Codec<VillageTerrain> CODEC=Zone.CODEC.listOf().xmap(VillageTerrain::new,s->s.zones);
    static final PersistentStateType<VillageTerrain> TYPE=new PersistentStateType<>("halloween_terrain_v1",()->new VillageTerrain(List.of()),CODEC,null);
    final List<Zone> zones;VillageTerrain(List<Zone> zones){this.zones=new ArrayList<>(zones);}
    private static final class Tile {final Zone zone;final ChunkPos chunk;int column,y=Integer.MIN_VALUE,target,top,original;Map<BlockPos,BlockState> houses;Tile(Zone z,ChunkPos c){zone=z;chunk=c;}}
    private static final Map<ServerWorld,ArrayDeque<Tile>> QUEUES=new WeakHashMap<>();
    private static VillageTerrain get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(TYPE);}
    static int height(int floor,int distance,int original){int rise=Math.max(0,distance-50)/2;return Math.max(floor-rise,Math.min(original,floor+rise));}
    private static int distance(Zone z,int x,int v){return Math.max(Math.max(z.min.getX()-x,x-z.max.getX()),Math.max(z.min.getZ()-v,v-z.max.getZ()));}
    static boolean removesHouse(ServerWorld w,VillageMemory.House h){return false;}
    static boolean suppressRepair(ServerWorld w,VillageMemory.BlockRecord r){return false;}
    static void register(){ServerChunkEvents.CHUNK_LOAD.register(VillageTerrain::load);ServerTickEvents.END_WORLD_TICK.register(VillageTerrain::tick);}
    private static Identifier id(StructurePoolElement p){if(p instanceof SinglePoolElement s)try{return s.getIdOrThrow();}catch(RuntimeException ignored){}if(p instanceof ListPoolElement l)for(var c:l.getElements()){var id=id(c);if(id!=null)return id;}return null;}
    private static void enqueue(ServerWorld w,Zone z,ChunkPos c){if(z.finished.contains(c.toLong())||distance(z,c.getCenterX(),c.getCenterZ())>1036)return;var q=QUEUES.computeIfAbsent(w,k->new ArrayDeque<>());if(q.stream().noneMatch(t->t.zone==z&&t.chunk.equals(c)))q.add(new Tile(z,c));}
    private static void load(ServerWorld w,WorldChunk chunk){
        if(!w.getRegistryKey().equals(World.OVERWORLD))return;var state=get(w);
        if(chunk.hasStructureReferences())for(var start:w.getStructureAccessor().getStructureStarts(chunk.getPos(),s->true))for(var piece:start.getChildren())if(piece instanceof PoolStructurePiece pool){
            var name=id(pool.getPoolElement());if(name==null||!name.getNamespace().equals("halloween")||!name.getPath().endsWith("/royal_castle"))continue;
            var box=piece.getBoundingBox();BlockPos min=new BlockPos(box.getMinX(),box.getMinY(),box.getMinZ()),max=new BlockPos(box.getMaxX(),box.getMaxY(),box.getMaxZ());
            if(state.zones.stream().noneMatch(z->z.min.equals(min))){var zone=new Zone(min,max,List.of());state.zones.add(zone);state.markDirty();
                for(int x=(min.getX()-1024)>>4;x<=(max.getX()+1024)>>4;x++)for(int z=(min.getZ()-1024)>>4;z<=(max.getZ()+1024)>>4;z++)if(w.isChunkLoaded(ChunkPos.toLong(x,z)))enqueue(w,zone,new ChunkPos(x,z));
            }
        }
        for(var zone:state.zones)enqueue(w,zone,chunk.getPos());
    }
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty())return;var q=QUEUES.get(w);if(q==null||q.isEmpty())return;int budget=2048;
        while(budget-->0&&!q.isEmpty()){
            Tile t=q.peek();if(!w.isChunkLoaded(t.chunk.toLong())){q.remove();continue;}
            if(t.column>=256){t.zone.finished.add(t.chunk.toLong());get(w).markDirty();q.remove();continue;}
            int x=t.chunk.getStartX()+(t.column&15),z=t.chunk.getStartZ()+(t.column>>4),d=Math.max(0,distance(t.zone,x,z));
            if(d>1024){t.column++;continue;}
            if(t.houses==null){t.houses=new HashMap<>();var m=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);for(var h:m.houses)if(removesHouse(w,h)&&h.min.getX()<=t.chunk.getEndX()&&h.max.getX()>=t.chunk.getStartX()&&h.min.getZ()<=t.chunk.getEndZ()&&h.max.getZ()>=t.chunk.getStartZ())for(var r:h.blueprint)t.houses.put(r.pos(),r.state());}
            if(t.y==Integer.MIN_VALUE){t.top=w.getTopY(Heightmap.Type.WORLD_SURFACE,x,z)-1;t.original=t.top;t.target=height(t.zone.min.getY(),d,t.top);t.y=Math.max(t.top,t.target);if(d>50&&t.target==t.original){t.column++;t.y=Integer.MIN_VALUE;continue;}
                // Preserve structures on the new ground level, including the castle itself.
                int protectedTop=d==0?t.zone.max.getY():t.target;
                for(var h:w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses)if(h.kind.equals("house")&&!removesHouse(w,h)&&x>=h.min.getX()&&x<=h.max.getX()&&z>=h.min.getZ()&&z<=h.max.getZ())protectedTop=Math.max(protectedTop,h.max.getY());
                t.top=protectedTop;
            }
            if(t.y<=t.target){
                if(d>0&&t.original<t.target&&t.y>t.original){BlockPos fill=new BlockPos(x,t.y--,z);if(w.getBlockState(fill).isAir()||w.getBlockState(fill).getBlock() instanceof FluidBlock)w.setBlockState(fill,Blocks.DIRT.getDefaultState(),Block.NOTIFY_LISTENERS);continue;}
                BlockPos floor=new BlockPos(x,t.target,z);if(d>0&&(w.getBlockState(floor).isAir()||VillageCivilWorks.natural(w.getBlockState(floor))))w.setBlockState(floor,Blocks.GRASS_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS);t.column++;t.y=Integer.MIN_VALUE;continue;}
            BlockPos p=new BlockPos(x,t.y--,z);var old=w.getBlockState(p);
            boolean natural=VillageCivilWorks.natural(old)||old.isIn(BlockTags.LOGS)||old.isIn(BlockTags.LEAVES)||old.getBlock() instanceof FluidBlock;
            boolean generated=t.houses.containsKey(p)&&old.isOf(t.houses.get(p).getBlock());
            boolean insideCore=d==0;
            if(generated||(p.getY()>t.top||insideCore&&VillageCivilWorks.natural(old))&&natural)w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS|Block.SKIP_DROPS);
        }
    }
}
