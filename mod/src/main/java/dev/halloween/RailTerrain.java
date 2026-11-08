package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
/** Ordinary embankments on land, piers only over water. Processes short sections, never the whole route at once. */
final class RailTerrain {
 static String lastIssue="";static BlockPos lastBlocked;
 static int ground(ServerWorld w,int x,int z){int top=w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);for(int n=0;n<40&&top>w.getBottomY()+1;n++){var p=new BlockPos(x,top-1,z);var s=w.getBlockState(p);if(!s.isIn(net.minecraft.registry.tag.BlockTags.LOGS)&&!s.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)&&!s.isReplaceable())break;top--;}return top;}
 static boolean natural(BlockState s){return s.isOf(Blocks.POINTED_DRIPSTONE)||s.isOf(Blocks.GLOW_LICHEN)||s.isOf(Blocks.NETHERRACK)||s.isOf(Blocks.MAGMA_BLOCK)||s.isOf(Blocks.MOSSY_COBBLESTONE)||s.isOf(Blocks.SUGAR_CANE)||s.isOf(Blocks.BAMBOO)||s.isOf(Blocks.CACTUS)||s.isOf(Blocks.PUMPKIN)||s.isOf(Blocks.MELON)||s.getBlock() instanceof net.minecraft.block.PlantBlock||VillageProjects.terrain(s)||s.isIn(net.minecraft.registry.tag.BlockTags.COAL_ORES)||s.isIn(net.minecraft.registry.tag.BlockTags.IRON_ORES)||s.isIn(net.minecraft.registry.tag.BlockTags.COPPER_ORES)||s.isIn(net.minecraft.registry.tag.BlockTags.GOLD_ORES)||s.isIn(net.minecraft.registry.tag.BlockTags.REDSTONE_ORES)||s.isIn(net.minecraft.registry.tag.BlockTags.LAPIS_ORES)||s.isIn(net.minecraft.registry.tag.BlockTags.DIAMOND_ORES)||s.isIn(net.minecraft.registry.tag.BlockTags.EMERALD_ORES)||s.isOf(Blocks.CALCITE)||s.isOf(Blocks.DRIPSTONE_BLOCK)||s.isOf(Blocks.CLAY)||s.isOf(Blocks.SANDSTONE)||s.isOf(Blocks.RED_SANDSTONE)||s.isOf(Blocks.ICE)||s.isOf(Blocks.PACKED_ICE)||s.isOf(Blocks.BLUE_ICE);}
 static Map<BlockPos,BlockState> plan(ServerWorld w,RailwayNetwork.Line line,double start,double end,boolean request){
  lastBlocked=null;lastIssue="";var state=RailwayNetwork.get(w);var result=new LinkedHashMap<BlockPos,BlockState>();
  for(double d=start;d<end;d+=.5)for(double lane:new double[]{-4,4}){
   var center=line.path.at(d,lane);var box=RailGeometry.worldBox(new Box(-2.4,-1,-.8,2.4,7,.8),center,line.path.yaw(d));
   int floor=(int)Math.floor(center.y)-1;
   for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++){
    var q=new BlockPos(x,floor,z);boolean ready=request?VillageProjects.keepLoaded(w,q):w.isChunkLoaded(new ChunkPos(q).toLong());if(!ready){lastIssue="loading "+q;return null;}
    if(RailwayNetwork.insideStation(state,q.withY(q.getY()+8))||RailwayNetwork.insideStation(state,q))continue;
    for(int h=0;h<=8;h++){var p=q.up(h);var old=w.getBlockState(p);
     if(old.hasBlockEntity()&&!old.isOf(RailContent.TRACK)||VillagePrisons.managed(w,p)){lastIssue="protected "+p+" "+old;lastBlocked=p;return null;}
     if(!old.isAir()&&!old.isReplaceable()&&!natural(old)&&!old.isIn(net.minecraft.registry.tag.BlockTags.LOGS)&&!old.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)&&!old.isOf(RailContent.TRACK)&&!old.isOf(Blocks.GRAVEL)&&!old.isOf(Blocks.STONE_BRICKS)){lastIssue="obstruction "+p+" "+old;lastBlocked=p;return null;}
     result.put(p,h==0?Blocks.GRAVEL.getDefaultState():Blocks.AIR.getDefaultState());
    }
    boolean water=!w.getFluidState(q.down()).isEmpty();
    if(!water)for(int depth=1;depth<=32;depth++){var p=q.down(depth);var old=w.getBlockState(p);if(old.isSolidBlock(w,p)||old.hasBlockEntity())break;if(!old.getFluidState().isEmpty())break;result.put(p,Blocks.DIRT.getDefaultState());}
    else{result.put(q,Blocks.STONE_BRICKS.getDefaultState());if(((int)d)%24==0&&Math.abs(x-center.x)<.6&&Math.abs(z-center.z)<.6)for(int depth=1;depth<=48;depth++){var p=q.down(depth);if(w.getBlockState(p).isSolidBlock(w,p)||w.getBlockState(p).hasBlockEntity())break;result.put(p,Blocks.STONE_BRICKS.getDefaultState());}}
   }
  }
  for(double d=start;d<end;d+=4)for(double lane:new double[]{-4,4})result.put(BlockPos.ofFloored(line.path.at(d,lane)),RailContent.TRACK.getDefaultState());return result;
 }
 static void place(ServerWorld w,RailwayNetwork.Line line,int index){var plan=plan(w,line,index*16,Math.min(line.path.length,(index+1)*16),true);if(plan==null){if(lastBlocked!=null)RailwayNetwork.detour(w,line,lastBlocked);return;}
  for(var e:plan.entrySet()){
   var occupants=VillageProjects.placementOccupants(w,e.getKey(),e.getValue());if(!occupants.isEmpty()){RailDeferred.defer(w,e.getKey(),e.getValue());VillageProjects.clearWorkArea(w,e.getKey(),occupants);continue;}
   w.setBlockState(e.getKey(),VillageConnections.connected(w,e.getKey(),e.getValue()),Block.NOTIFY_LISTENERS);if(e.getValue().isOf(RailContent.TRACK))configure(w,line,e.getKey(),index*16);
  }
  line.next++;RailwayNetwork.get(w).markDirty();
 }
 static void configure(ServerWorld w,RailwayNetwork.Line line,BlockPos pos,double from){if(!(w.getBlockEntity(pos) instanceof RailTrackEntity track))return;for(double d=from;d<Math.min(line.path.length,from+16);d+=4)for(double lane:new double[]{-4,4})if(BlockPos.ofFloored(line.path.at(d,lane)).equals(pos)){var points=new ArrayList<Vec3d>();for(int k=0;k<=8;k++)points.add(line.path.at(d+k*.5,lane).subtract(Vec3d.of(pos)));track.setPoints(points);return;}}
}
