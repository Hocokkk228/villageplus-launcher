package dev.halloween;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
/** Boarding openings unlock only beside a stopped carriage; ordinary patrols stay on the platform. */
final class RailStationSafety {
 static final int[] EDGES={5,11,13,19};
 static boolean gate(int z){return z>=11&&z<=14||z>=22&&z<=29||z>=35&&z<=42||z>=50&&z<=53;}
 static void repair(ServerWorld w,RailwayNetwork.Station station){repairTracks(w,station);if(station.access>=6)return;var floor=station.pos(12,2,10);if(!w.isChunkLoaded(new ChunkPos(floor).toLong())||!w.getBlockState(floor).isSolidBlock(w,floor))return;
  var plan=new java.util.LinkedHashMap<BlockPos,BlockState>();RailwayNetwork.access(station,plan);
  boolean approachesReady=true;
  for(int base:new int[]{3,11,19,30})for(int lane=0;lane<(base==30?5:2);lane++)approachesReady &= groundStairs(w,station.pos(base+lane,0,-3),station.forward().getOpposite(),plan);
  for(int door:new int[]{12,30})for(int lane=0;lane<3;lane++)approachesReady &= groundStairs(w,station.pos(45,0,door+lane),station.side(),plan);
  for(int base:new int[]{3,11,19})for(int step=0;step<6;step++)for(int lane=0;lane<2;lane++){
   var p=station.pos(base+lane,3+step,49+step);plan.put(p,Blocks.STONE_BRICK_STAIRS.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING,station.forward()));for(int h=1;h<=3;h++)plan.put(p.up(h),Blocks.AIR.getDefaultState());
  }
  for(int base:new int[]{3,11,19})for(int dx=0;dx<2;dx++)for(int z=55;z<=57;z++)for(int h=9;h<=11;h++)plan.put(station.pos(base+dx,h,z),Blocks.AIR.getDefaultState());
  // Reserve full train clearance below the platform bridge, including taller sleepers.
  // Old construction jobs can have left station trim hanging inside the track lane.
  for(int center:new int[]{8,16})for(int x=center-2;x<=center+2;x++)for(int z=1;z<=63;z++)for(int y=2;y<=7;y++){var q=station.pos(x,y,z);var old=w.getBlockState(q);if(!old.hasBlockEntity()&&!old.isAir())plan.put(q,Blocks.AIR.getDefaultState());}
  int budget=256;boolean complete=approachesReady;
  for(var e:plan.entrySet()){var p=e.getKey();if(!w.isChunkLoaded(new ChunkPos(p).toLong())){VillageProjects.keepLoaded(w,p);complete=false;continue;}var actual=w.getBlockState(p);if(actual.equals(e.getValue()))continue;if(actual.hasBlockEntity()||!VillageProjects.placementOccupants(w,p,e.getValue()).isEmpty()){complete=false;continue;}if(budget--<=0){complete=false;continue;}w.setBlockState(p,e.getValue(),Block.NOTIFY_ALL);}
  if(complete){station.access=6;RailwayNetwork.get(w).markDirty();}
 }
 /** Rebuild missing platform track segments from the same spline as the train.
  * Station templates contain air in those lanes, so rebuilding a station used to erase them. */
 static void repairTracks(ServerWorld w,RailwayNetwork.Station station){for(var line:RailwayNetwork.get(w).lines){double at=line.path.closest(station.center(32));if(line.path.center(at).squaredDistanceTo(station.center(32))>64)continue;double from=Math.floor((at-80)/4)*4;for(double d=from;d<=at+80;d+=4){if(!line.path.closed&&(d<0||d>=line.path.length))continue;for(double lane:new double[]{-4,4}){var point=line.path.at(d,lane);var delta=point.subtract(Vec3d.of(station.origin));double x=delta.x*station.side().getOffsetX()+delta.z*station.side().getOffsetZ(),z=delta.x*station.forward().getOffsetX()+delta.z*station.forward().getOffsetZ();if(x<5||x>20||z<-2||z>66)continue;var p=BlockPos.ofFloored(point);if(!w.isChunkLoaded(new ChunkPos(p).toLong()))continue;var old=w.getBlockState(p);if(old.hasBlockEntity()&&!old.isOf(RailContent.TRACK))continue;var points=new java.util.ArrayList<Vec3d>();for(int i=0;i<=8;i++)points.add(line.path.at(d+i*.5,lane).subtract(Vec3d.of(p)));if(!old.isOf(RailContent.TRACK))w.setBlockState(p,RailContent.TRACK.getDefaultState(),Block.NOTIFY_ALL);if(w.getBlockEntity(p) instanceof RailTrackEntity rail&&!rail.points.equals(points))rail.setPoints(points);}}}}
 /** Extend a hovering entrance down to nearby ground, without raising a dirt column. */
 static boolean groundStairs(ServerWorld w,BlockPos bottom,Direction away,java.util.Map<BlockPos,BlockState> plan){
  int landing=0;
  for(int step=1;step<=24;step++){
   var p=bottom.offset(away,step).down(step);
   if(!w.isChunkLoaded(p)){VillageProjects.keepLoaded(w,p);return false;}
   var old=w.getBlockState(p);
   if(old.hasBlockEntity()||!w.getFluidState(p).isEmpty())return false;
   if(!old.isSolidBlock(w,p)||old.isIn(net.minecraft.registry.tag.BlockTags.LOGS)||old.isIn(net.minecraft.registry.tag.BlockTags.LEAVES))continue;
   if(!VillageProjects.terrain(old))return false;
   if(!clearable(w,p.up())||!clearable(w,p.up(2)))return false;
   landing=step;break;
  }
  if(landing==0)return false;
  var steps=new java.util.LinkedHashMap<BlockPos,BlockState>();
  for(int step=1;step<=landing;step++){
   var p=bottom.offset(away,step).down(step);
   if(step<landing){
    var old=w.getBlockState(p);
    if(!clearable(w,p)&&!(old.getBlock() instanceof StairsBlock))return false;
    steps.put(p,Blocks.STONE_BRICK_STAIRS.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING,away.getOpposite()));
    for(var support=p.down();support.getY()>w.getBottomY();support=support.down()){
     if(w.getBlockState(support).isSolidBlock(w,support))break;
     if(!clearable(w,support))return false;
     steps.put(support,Blocks.STONE_BRICKS.getDefaultState());
    }
   }
   for(int h=1;h<=3;h++){
    var head=p.up(h);
    if(!clearable(w,head))return false;
    steps.put(head,Blocks.AIR.getDefaultState());
   }
  }
  plan.putAll(steps);return true;
 }
 private static boolean clearable(ServerWorld w,BlockPos p){
  var state=w.getBlockState(p);
  return w.getFluidState(p).isEmpty()&&!state.hasBlockEntity()&&(state.isAir()||state.isReplaceable()
      ||VillageProjects.terrain(state)||state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)
      ||state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES));
 }

 static void commuters(ServerWorld w,RailwayNetwork.Station station){
  var target=station.pos(12,3,12);String tag="halloween_commute_"+station.village;
  var travellers=w.getEntitiesByClass(net.minecraft.entity.passive.VillagerEntity.class,new Box(station.origin).expand(384,64,384),v->v.isAlive()&&v.getCommandTags().contains(tag));
  for(var v:travellers){if(v.hasVehicle()||v.getCommandTags().contains("halloween_rail_arrived")){v.removeCommandTag(tag);continue;}VillageTasks.hold(v);if((v.getNavigation().isIdle()||w.getTime()%60==0)&&VillagePathBudget.take(w)){v.getNavigation().setMaxFollowRange(384);v.getNavigation().startMovingTo(target.getX()+.5,target.getY(),target.getZ()+.5,.65);}}
  if(w.getTime()%200!=0||travellers.size()>=8)return;int count=travellers.size();
  for(var v:w.getEntitiesByClass(net.minecraft.entity.passive.VillagerEntity.class,new Box(station.origin).expand(384,64,384),v->v.isAlive()&&!v.isBaby()&&!v.hasVehicle()&&!v.isAiDisabled()&&!(v instanceof StationResidentEntity)&&!RailDriver.isDriver(v)&&!VillageDefense.isGuard(v)&&!RoyalProfession.isKing(v)&&!VillageProjects.owns(v)&&!VillageTrials.busy(v)&&!v.getCommandTags().contains("halloween_prisoner"))){
   if(count>=6)break;if(v.getCommandTags().contains("halloween_rail_arrived")){if(w.random.nextInt(8)!=0)continue;v.removeCommandTag("halloween_rail_arrived");}
   if(!v.getCommandTags().contains("halloween_wanted")&&Math.floorMod(v.getUuid().hashCode()+(int)(w.getTime()/24000),5)!=0)continue;
   v.addCommandTag(tag);VillageTasks.hold(v);v.getNavigation().setMaxFollowRange(384);if(VillagePathBudget.take(w))v.getNavigation().startMovingTo(target.getX()+.5,target.getY(),target.getZ()+.5,.65);count++;
  }
 }
 static void tick(ServerWorld w){for(var station:RailwayNetwork.get(w).stations){if(w.getPlayers().stream().noneMatch(p->VillageEras.horizontal(p.getBlockPos(),station.origin)<256*256)&&RailVehicleEntity.nearby(w,new Box(station.origin).expand(100,20,100)).isEmpty())continue;
  repair(w,station);commuters(w,station);for(int x:EDGES)for(int z=2;z<=62;z++){var pos=station.pos(x,3,z);if(!w.isChunkLoaded(new ChunkPos(pos).toLong())||!(w.getBlockState(pos).getBlock() instanceof FenceGateBlock))continue;
   var point=Vec3d.ofCenter(pos);boolean open=RailVehicleEntity.nearby(w,new Box(pos).expand(9)).stream().anyMatch(t->{if(t.kind()!=1||!t.doorsOpen())return false;var local=RailPassengerNetwork.toLocal(t,point);return Math.abs(local.x)<3.6&&Math.abs(Math.abs(local.z)-5.5)<1.1;});
   var old=w.getBlockState(pos);if(old.get(FenceGateBlock.OPEN)!=open)w.setBlockState(pos,old.with(FenceGateBlock.OPEN,open),Block.NOTIFY_ALL);
  }
 }}
}
