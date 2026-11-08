package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
/** Queued U-shaped extensions behind completed buildings crossing the old border. */
final class PerimeterExtensions {
 static List<BlockPos> route(BlockPos anchor,int radius,int side,BlockPos min,BlockPos max){
  boolean alongZ=side<2;int fixed=(alongZ?anchor.getX():anchor.getZ())+((side==0||side==2)?-radius:radius);
  int lo=alongZ?min.getX():min.getZ(),hi=alongZ?max.getX():max.getZ();if(fixed<lo-2||fixed>hi+2)return List.of();
  int start=(alongZ?min.getZ():min.getX())-5,end=(alongZ?max.getZ():max.getX())+5;
  int outside=(side==0||side==2)?lo-6:hi+6;var points=new ArrayList<BlockPos>();
  for(int v=fixed;;v+=outside>fixed?1:-1){points.add(alongZ?new BlockPos(v,0,start):new BlockPos(start,0,v));if(v==outside)break;}
  for(int n=start+1;n<=end;n++)points.add(alongZ?new BlockPos(outside,0,n):new BlockPos(n,0,outside));
  for(int v=outside+(fixed>outside?1:-1);;v+=fixed>outside?1:-1){points.add(alongZ?new BlockPos(v,0,end):new BlockPos(end,0,v));if(v==fixed)break;}return points;
 }
 static void tick(ServerWorld w,VillageMemory.Team team,int radius){var projects=VillageProjects.get(w);
  for(var building:List.copyOf(projects.jobs))if(building.done&&building.village==team.village&&Set.of("house","market","factory","cathedral","rail_station","rail_foundry","shipyard","treasury","stable").contains(building.kind))for(int side=0;side<4;side++){
   String id="perimeter_bypass_v2:"+building.id+":"+side;if(projects.exists(id))continue;var route=route(team.anchor,radius,side,building.min,building.max);if(route.isEmpty())continue;
   var plan=new LinkedHashMap<BlockPos,net.minecraft.block.BlockState>();boolean loaded=true;
   for(var p:route){if(!w.isChunkLoaded(p)){VillageProjects.keepLoaded(w,p);loaded=false;continue;}int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());var ground=p.withY(y-1);if(!w.getFluidState(ground).isEmpty()||!VillageProjects.terrain(w.getBlockState(ground))||!w.getBlockState(ground).isSolidBlock(w,ground))continue;
    boolean gate=projects.jobs.stream().filter(j->j.kind.equals("road")||j.kind.equals("stone_road")).anyMatch(j->p.getX()>=j.min.getX()-2&&p.getX()<=j.max.getX()+2&&p.getZ()>=j.min.getZ()-2&&p.getZ()<=j.max.getZ()+2);if(gate)continue;
    boolean clear=true;for(int h=1;h<=4;h++){var b=w.getBlockState(ground.up(h));if(!b.isAir()&&!b.isReplaceable()){clear=false;break;}}if(!clear)continue;
    for(int h=1;h<=3;h++)plan.put(ground.up(h),Blocks.STONE_BRICKS.getDefaultState());plan.put(ground.up(4),Blocks.STONE_BRICK_WALL.getDefaultState());
   }
   if(!loaded)return;
   // Remove only recorded old perimeter blocks beside the building; preserve all newer construction.
   for(var old:List.copyOf(projects.jobs))if(old.kind.equals("perimeter")&&!old.id.startsWith("perimeter_bypass_v2:"))for(var c:old.plan){var p=c.pos();if(p.getX()<building.min.getX()-4||p.getX()>building.max.getX()+4||p.getZ()<building.min.getZ()-4||p.getZ()>building.max.getZ()+4||plan.containsKey(p))continue;if(!w.isChunkLoaded(p))continue;var actual=w.getBlockState(p);if(actual.isOf(c.after().getBlock())&&!actual.hasBlockEntity()&&c.after().equals(projects.expected.get(p)))plan.put(p,Blocks.AIR.getDefaultState());}
   if(!plan.isEmpty()){projects.add(w,id,team.village,"perimeter",plan);return;}
  }
 }
}
