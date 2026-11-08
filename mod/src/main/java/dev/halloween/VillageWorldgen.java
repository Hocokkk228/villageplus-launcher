package dev.halloween;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.structure.*;
import net.minecraft.structure.pool.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;

/** Runs before feature/structure placement, never against an already running village. */
public final class VillageWorldgen {
    private static Identifier id(StructurePoolElement e){if(e instanceof SinglePoolElement s)try{return s.getIdOrThrow();}catch(RuntimeException ignored){}if(e instanceof ListPoolElement l)for(var c:l.getElements()){var r=id(c);if(r!=null)return r;}return null;}
    private static PoolStructurePiece castle(StructureStart start){for(var p:start.getChildren())if(p instanceof PoolStructurePiece s){var id=id(s.getPoolElement());if(id!=null&&id.getNamespace().equals("halloween")&&id.getPath().endsWith("/royal_castle"))return s;}return null;}
    private static int distance(BlockBox b,int x,int z){return Math.max(0,Math.max(Math.max(b.getMinX()-x,x-b.getMaxX()),Math.max(b.getMinZ()-z,z-b.getMaxZ())));}
    private static boolean intersects(BlockBox a,BlockBox b,int pad){return a.getMinX()<=b.getMaxX()+pad&&a.getMaxX()>=b.getMinX()-pad&&a.getMinZ()<=b.getMaxZ()+pad&&a.getMaxZ()>=b.getMinZ()-pad;}
    public static int surface(int floor,int d,int original){double t=Math.clamp((d-50)/334.0,0,1);double blend=t*t*(3-2*t);return (int)Math.round(floor+(original-floor)*blend);}
    private record Region(long seed,int x,int z){}
    private static final Map<Region,Map<Long,BlockBox>> CORES=new java.util.concurrent.ConcurrentHashMap<>();
    public static void remember(long seed,BlockBox box){var region=new Region(seed,Math.floorDiv(box.getCenter().getX(),512),Math.floorDiv(box.getCenter().getZ(),512));CORES.computeIfAbsent(region,k->new java.util.concurrent.ConcurrentHashMap<>()).put(ChunkPos.toLong(box.getMinX(),box.getMinZ()),box);}
    public static void align(Chunk chunk){align(chunk,0);}
    public static void align(Chunk chunk,long seed){
        for(var entry:new ArrayList<>(chunk.getStructureStarts().entrySet())){
            var start=entry.getValue();var castle=castle(start);if(castle==null)continue;var core=castle.getBoundingBox();int floor=core.getMinY();remember(seed,core);
            List<StructurePiece> pieces=new ArrayList<>();List<BlockBox> occupied=new ArrayList<>();occupied.add(core);pieces.add(castle);
            var originals=new ArrayList<>(start.getChildren());originals.sort(Comparator.comparingInt(p->p.getBoundingBox().getMinX()));
            for(var raw:originals){
                if(raw==castle)continue;if(!(raw instanceof PoolStructurePiece p)){pieces.add(raw);continue;}var name=id(p.getPoolElement());if(name==null){pieces.add(p);continue;}
                if(name.getPath().endsWith("/dungeon")){pieces.add(p);continue;}
                if(name.getPath().contains("/streets/"))continue;
                var old=p.getBoundingBox();int sx=old.getBlockCountX(),sz=old.getBlockCountZ();BlockBox best=null;double score=Double.MAX_VALUE;
                for(var neighbour:occupied)for(int side=0;side<4;side++)for(int alignment=0;alignment<3;alignment++){
                    int x=side==0?neighbour.getMaxX()+5:side==1?neighbour.getMinX()-sx-4:alignment==0?neighbour.getMinX():alignment==1?neighbour.getMaxX()-sx+1:(neighbour.getMinX()+neighbour.getMaxX()-sx+1)/2;
                    int z=side==2?neighbour.getMaxZ()+5:side==3?neighbour.getMinZ()-sz-4:alignment==0?neighbour.getMinZ():alignment==1?neighbour.getMaxZ()-sz+1:(neighbour.getMinZ()+neighbour.getMaxZ()-sz+1)/2;
                    BlockBox site=new BlockBox(x,floor,z,x+sx-1,floor+old.getBlockCountY()-1,z+sz-1);
                    if(occupied.stream().anyMatch(o->intersects(site,o,4)))continue;
                    double d=site.getCenter().getSquaredDistance(core.getCenter());if(d<score){score=d;best=site;}
                }
                if(best==null){pieces.add(p);continue;}
                p.translate(best.getMinX()-old.getMinX(),floor+1-(old.getMinY()+p.getGroundLevelDelta()),best.getMinZ()-old.getMinZ());occupied.add(p.getBoundingBox());pieces.add(p);
            }
            chunk.setStructureStart(entry.getKey(),new StructureStart(start.getStructure(),start.getPos(),start.getReferences(),new StructurePiecesList(pieces)));
        }
    }
    public static void terrain(StructureWorldAccess world,Chunk chunk,StructureAccessor accessor){
        var cores=new ArrayList<BlockBox>();for(var start:accessor.getStructureStarts(chunk.getPos(),s->true)){var c=castle(start);if(c!=null)cores.add(c.getBoundingBox());}
        int rx=Math.floorDiv(chunk.getPos().getCenterX(),512),rz=Math.floorDiv(chunk.getPos().getCenterZ(),512);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){var known=CORES.get(new Region(world.getSeed(),rx+dx,rz+dz));if(known!=null)for(var box:known.values())if(!cores.contains(box))cores.add(box);}
        if(cores.isEmpty())return;
        for(int x=chunk.getPos().getStartX();x<=chunk.getPos().getEndX();x++)for(int z=chunk.getPos().getStartZ();z<=chunk.getPos().getEndZ();z++){
            BlockBox core=null;int nearest=Integer.MAX_VALUE;for(var b:cores){int d=distance(b,x,z);if(d<nearest){nearest=d;core=b;}}
            if(nearest>=384)continue;int original=chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x&15,z&15)-1,target=surface(core.getMinY(),nearest,original);
            boolean desert=world.getBiome(new BlockPos(x,target,z)).getKey().map(k->k.getValue().getPath().contains("desert")).orElse(false);
            BlockState top=(desert?Blocks.SAND:Blocks.GRASS_BLOCK).getDefaultState(),under=(desert?Blocks.SANDSTONE:Blocks.DIRT).getDefaultState();
            // Before structures exist it is safe to clear the entire cut, including ores and paths.
            for(int y=original;y>target;y--)chunk.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState(),Block.FORCE_STATE);
            for(int y=Math.min(original+1,target-3);y<target;y++)chunk.setBlockState(new BlockPos(x,y,z),y<target-3?Blocks.STONE.getDefaultState():under,Block.FORCE_STATE);
            chunk.setBlockState(new BlockPos(x,target,z),top,Block.FORCE_STATE);
            int solid=0;for(int y=target-1;y>=Math.max(world.getBottomY(),target-96);y--){var p=new BlockPos(x,y,z);var old=chunk.getBlockState(p);if(old.isAir()||!old.getFluidState().isEmpty()){solid=0;chunk.setBlockState(p,y>=target-3?under:Blocks.STONE.getDefaultState(),Block.FORCE_STATE);}else if(++solid>=6)break;}

        }
    }
}
