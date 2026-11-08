package dev.halloween;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.util.BlockRotation;
import net.minecraft.world.*;
/** Persistent two-track links between actual village locations; construction uses the ordinary builder queue. */
public final class RailwayNetwork extends PersistentState {
 static final class Station {
  static final Codec<Station> CODEC=RecordCodecBuilder.create(i->i.group(Codec.LONG.fieldOf("village").forGetter(s->s.village),BlockPos.CODEC.fieldOf("origin").forGetter(s->s.origin),Codec.INT.fieldOf("facing").forGetter(s->s.facing),Codec.INT.optionalFieldOf("production",0).forGetter(s->s.production),Codec.INT.optionalFieldOf("access",0).forGetter(s->s.access),Codec.STRING.listOf().optionalFieldOf("residents",List.of()).forGetter(s->s.residents)).apply(i,Station::new));
  final long village;final BlockPos origin;final int facing;int production,access;final List<String> residents;
  Station(long village,BlockPos origin,int facing,int production){this(village,origin,facing,production,0);}
  Station(long village,BlockPos origin,int facing,int production,int access){this(village,origin,facing,production,access,List.of());}
  Station(long village,BlockPos origin,int facing,int production,int access,List<String> residents){this.residents=new ArrayList<>(residents);this.access=access;this.village=village;this.origin=origin;this.facing=facing;this.production=production;}
  Direction forward(){return Direction.fromHorizontalQuarterTurns(facing);}Direction side(){return forward().rotateYCounterclockwise();}
  BlockPos pos(int x,int y,int z){return origin.offset(side(),x).offset(forward(),z).up(y);}
  Vec3d center(int z){return Vec3d.ofBottomCenter(pos(12,1,z));}
 }
 static final class Line {
  static final Codec<Line> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("id").forGetter(s->s.id),Codec.LONG.fieldOf("village").forGetter(s->s.village),Vec3d.CODEC.listOf().fieldOf("controls").forGetter(s->s.controls),Codec.INT.optionalFieldOf("next",0).forGetter(s->s.next),Codec.BOOL.optionalFieldOf("spawned",false).forGetter(s->s.spawned),Codec.INT.optionalFieldOf("detours",0).forGetter(s->s.detours),Codec.INT.optionalFieldOf("revision",0).forGetter(s->s.revision)).apply(i,Line::new));
  final String id;final long village;final List<Vec3d> controls;final RailPath path;final int revision;int next,detours;boolean spawned;
  Line(String id,long village,List<Vec3d> controls,int next,boolean spawned){this(id,village,controls,next,spawned,0);}
  Line(String id,long village,List<Vec3d> controls,int next,boolean spawned,int detours){this(id,village,controls,next,spawned,detours,0);}
  Line(String id,long village,List<Vec3d> controls,int next,boolean spawned,int detours,int revision){this.revision=revision;this.detours=detours;this.id=id;this.village=village;this.controls=List.copyOf(controls);path=new RailPath(controls);this.next=next;this.spawned=spawned;}
 }
 static final Codec<RailwayNetwork> CODEC=RecordCodecBuilder.create(i->i.group(Station.CODEC.listOf().fieldOf("stations").forGetter(s->s.stations),Line.CODEC.listOf().fieldOf("lines").forGetter(s->s.lines)).apply(i,RailwayNetwork::new));
 static final PersistentStateType<RailwayNetwork> TYPE=new PersistentStateType<>("halloween_railway",()->new RailwayNetwork(List.of(),List.of()),CODEC,null);
 final List<Station> stations;final List<Line> lines;
 RailwayNetwork(List<Station> stations,List<Line> lines){this.stations=new ArrayList<>(stations);this.lines=new ArrayList<>(lines);}
 static RailwayNetwork get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(TYPE);}
 static BlockRotation rotation(Direction d){return switch(d){case WEST->BlockRotation.CLOCKWISE_90;case NORTH->BlockRotation.CLOCKWISE_180;case EAST->BlockRotation.COUNTERCLOCKWISE_90;default->BlockRotation.NONE;};}
 static Map<BlockPos,BlockState> building(ServerWorld w,Station station,String name){
  var template=VillageEraPlans.template(w,name);if(template==null)return Map.of();var blocks=new LinkedHashMap<BlockPos,BlockState>();
  for(var r:template.blocks())blocks.put(station.pos(r.pos().getX(),r.pos().getY(),r.pos().getZ()),r.state().rotate(rotation(station.forward())));
  if(name.equals("railway_station"))access(station,blocks);return blocks;
 }
 static void access(Station station,Map<BlockPos,BlockState> blocks){
  for(int z=4;z<=45;z++){
   blocks.put(station.pos(23,1,z),Blocks.STONE_BRICKS.getDefaultState());
   blocks.put(station.pos(23,2,z),Blocks.SMOOTH_STONE.getDefaultState());
  }
  // The template's front border is two blocks below the platform. Bridge it at every stair mouth.
  for(int base:new int[]{3,11,19})for(int lane=0;lane<2;lane++){
   blocks.put(station.pos(base+lane,1,0),Blocks.STONE_BRICKS.getDefaultState());blocks.put(station.pos(base+lane,2,0),Blocks.SMOOTH_STONE.getDefaultState());
   for(int h=3;h<=5;h++)blocks.put(station.pos(base+lane,h,0),Blocks.AIR.getDefaultState());
  }
  for(int base:new int[]{3,11,19})for(int step=0;step<3;step++)for(int lane=0;lane<2;lane++){
   var p=station.pos(base+lane,step,-3+step);blocks.put(p,Blocks.STONE_BRICK_STAIRS.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING,station.forward()));for(int h=1;h<=3;h++)blocks.put(p.up(h),Blocks.AIR.getDefaultState());
  }
  {
   // Main hall entrances are cut into the actual outer walls, with stairs reaching natural ground.
   for(int door:new int[]{12,30})for(int dz=0;dz<3;dz++){
    for(int h=3;h<=6;h++)blocks.put(station.pos(41,h,door+dz),Blocks.AIR.getDefaultState());
    for(int step=0;step<3;step++){var p=station.pos(43+step,2-step,door+dz);blocks.put(p,Blocks.STONE_BRICK_STAIRS.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING,station.side().getOpposite()));for(int h=1;h<=3;h++)blocks.put(p.up(h),Blocks.AIR.getDefaultState());}
    blocks.put(station.pos(42,2,door+dz),Blocks.SMOOTH_SANDSTONE.getDefaultState());
   }
   for(int x=30;x<=34;x++){for(int h=3;h<=6;h++)blocks.put(station.pos(x,h,4),Blocks.AIR.getDefaultState());for(int z=0;z<4;z++)blocks.put(station.pos(x,2,z),Blocks.SMOOTH_SANDSTONE.getDefaultState());
    for(int step=0;step<3;step++){var p=station.pos(x,step,-3+step);blocks.put(p,Blocks.STONE_BRICK_STAIRS.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING,station.forward()));for(int h=1;h<=3;h++)blocks.put(p.up(h),Blocks.AIR.getDefaultState());}
   }
  } }
 static Station foundry(Station s){return new Station(s.village,s.pos(47,0,6),s.facing,0);}
 static String stationId(long village){return "rail_station_v1:"+village;}
 static String foundryId(long village){return "rail_foundry_v1:"+village;}
 static boolean loaded(ServerWorld w,Collection<BlockPos> positions){boolean ready=true;var chunks=new HashSet<Long>();for(var pos:positions)if(chunks.add(new ChunkPos(pos).toLong())&&!VillageProjects.keepLoaded(w,pos))ready=false;return ready;}
 static boolean protect(ServerWorld w,Map<BlockPos,BlockState> blocks){for(var e:blocks.entrySet()){var old=w.getBlockState(e.getKey());if(old.hasBlockEntity()||!old.isAir()&&!old.isReplaceable()&&!VillageProjects.terrain(old)&&!old.isIn(net.minecraft.registry.tag.BlockTags.LOGS)&&!old.isIn(net.minecraft.registry.tag.BlockTags.LEAVES))return false;}return true;}
 static Station planStation(ServerWorld w,long village,BlockPos villageCenter,Direction forward){
  var network=get(w);var old=network.stations.stream().filter(s->s.village==village).findFirst().orElse(null);if(old!=null)return old;
  var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
  var eraData=VillageEraData.get(w);
  var settlement=eraData.settlements.computeIfAbsent(village,id->new VillageEraData.Settlement(id,WorldSettlements.discover(w,villageCenter).name));
  int serial=settlement.xp.getOrDefault("station_search_v3",0);
  for(int n=serial;n<serial+4;n++){
   if(settlement!=null){settlement.xp.put("station_search_v3",n+1);VillageEraData.get(w).markDirty();}
   double angle=n*2.399963229728653;int r=settlement.xp.getOrDefault("railway_requested",0)>0?112+Math.floorMod(n,16)*16:64+Math.floorMod(n,20)*12;int x=villageCenter.getX()+(int)(Math.cos(angle)*r),z=villageCenter.getZ()+(int)(Math.sin(angle)*r);
   Direction tangent=forward;
   boolean automatic=settlement==null||settlement.xp.getOrDefault("railway_requested",0)==0;
   if(automatic){tangent=Direction.getFacing(-(z-villageCenter.getZ()),0,x-villageCenter.getX());if(tangent.getOffsetX()*forward.getOffsetX()+tangent.getOffsetZ()*forward.getOffsetZ()<0)tangent=tangent.getOpposite();}
   else if(Math.abs((x-villageCenter.getX())*forward.rotateYCounterclockwise().getOffsetX()+(z-villageCenter.getZ())*forward.rotateYCounterclockwise().getOffsetZ())<224)continue;
   var probe=new Station(village,new BlockPos(x,64,z),tangent.getHorizontalQuarterTurns(),0);var corners=List.of(probe.pos(0,0,-3),probe.pos(66,0,65));int x0=Math.min(corners.get(0).getX(),corners.get(1).getX()),x1=Math.max(corners.get(0).getX(),corners.get(1).getX()),z0=Math.min(corners.get(0).getZ(),corners.get(1).getZ()),z1=Math.max(corners.get(0).getZ(),corners.get(1).getZ());
   if(memory.houses.stream().filter(VillageBuildingSites::reserves).anyMatch(h->h.min.getX()<=x1&&h.max.getX()>=x0&&h.min.getZ()<=z1&&h.max.getZ()>=z0)){siteDebug(n,"building footprint");continue;}
   if(projects.jobs.stream().filter(p->VillageBuildingSites.building(p.kind)).anyMatch(p->p.min.getX()<=x1&&p.max.getX()>=x0&&p.min.getZ()<=z1&&p.max.getZ()>=z0)){siteDebug(n,"project footprint");continue;}
   var need=new ArrayList<BlockPos>();for(int cx=x0>>4;cx<=x1>>4;cx++)for(int cz=z0>>4;cz<=z1>>4;cz++)need.add(new BlockPos(cx*16,64,cz*16));if(!loaded(w,need)){if(settlement!=null)settlement.xp.put("station_search_v3",n);return null;}
   int height=VillageEraPlans.levelSite(w,x0,z0,x1-x0+1,z1-z0+1,12);if(height==Integer.MIN_VALUE){siteDebug(n,"terrain or water");continue;}
   var station=new Station(village,new BlockPos(x,height-1,z),tangent.getHorizontalQuarterTurns(),0);var stationBlocks=building(w,station,"railway_station");var forgeBlocks=building(w,foundry(station),"rail_foundry");
   if(stationBlocks.isEmpty()||forgeBlocks.isEmpty()||!protect(w,stationBlocks)||!protect(w,forgeBlocks)){siteDebug(n,"protected block");continue;}
   foundation(w,stationBlocks);foundation(w,forgeBlocks);
   projects.add(w,stationId(village),village,"rail_station",stationBlocks);projects.add(w,foundryId(village),village,"rail_foundry",forgeBlocks);
   if(!projects.exists(stationId(village))||!projects.exists(foundryId(village)))return null;
   network.stations.add(station);network.markDirty();return station;
  }return null;
 }
 static void connectVillageRoad(ServerWorld w,VillageMemory.Team team,Station station){
  var projects=VillageProjects.get(w);
  String id="station_village_road:"+team.village;
  if(projects.exists(id)||!projects.complete(team.village,"rail_station"))return;
  var entrances=new ArrayList<>(List.of(station.pos(4,1,-4),station.pos(12,1,-4),station.pos(32,1,-4),
          station.pos(46,1,13),station.pos(46,1,31)));
  entrances.sort(Comparator.comparingDouble(p->VillageEras.horizontal(team.anchor,p)));
  for(var entrance:entrances){
   if(!w.isChunkLoaded(entrance)||!VillagePathBudget.take(w))return;
   var plan=VillageRoadNetwork.plan(w,entrance,team.anchor);
   if(plan.isEmpty())continue;
   plan.replaceAll((p,b)->Blocks.STONE_BRICKS.getDefaultState());
   projects.add(w,id,team.village,"stone_road",plan);
   if(projects.exists(id))return;
  }
 }
 private static void siteDebug(int candidate,String why){if(Boolean.getBoolean("halloween.recoveryTest")||Boolean.getBoolean("halloween.eraSoak"))org.slf4j.LoggerFactory.getLogger("halloween-recovery").info("Station candidate {} rejected: {}",candidate,why);}
 private static void foundation(ServerWorld w,Map<BlockPos,BlockState> plan){
  var floors=new HashMap<Long,BlockPos>();for(var p:List.copyOf(plan.keySet()))floors.merge(ChunkPos.toLong(p.getX(),p.getZ()),p,(a,b)->a.getY()<b.getY()?a:b);
  for(var p:floors.values())for(int depth=1;depth<=16;depth++){var q=p.down(depth);if(w.getBlockState(q).isSolidBlock(w,q))break;plan.put(q,Blocks.STONE_BRICKS.getDefaultState());}
 }
 static Line connect(ServerWorld w,Station a,Station b){
  var state=get(w);String id="rail_line:"+Math.min(a.village,b.village)+":"+Math.max(a.village,b.village);var existing=state.lines.stream().filter(l->l.id.equals(id)).findFirst().orElse(null);if(existing!=null)return existing;
  var controls=new ArrayList<Vec3d>();controls.addAll(RailPath.straight(a.center(0),a.center(64)));
  var from=a.center(64);var to=b.center(64);double handle=Math.max(48,from.distanceTo(to)/3);
  controls.addAll(List.of(from,from.add(a.forward().getOffsetX()*handle,0,a.forward().getOffsetZ()*handle),to.add(b.forward().getOffsetX()*handle,0,b.forward().getOffsetZ()*handle),to));
  controls.addAll(RailPath.straight(b.center(64),b.center(0)));var line=new Line(id,a.village,controls,0,false);state.lines.add(line);state.markDirty();return line;
 }
 static Line connectRing(ServerWorld w,List<Station> stations){var state=get(w);var existing=state.lines.stream().filter(l->l.id.startsWith("rail_ring:")).findFirst().orElse(null);if(existing!=null)return existing;
  var controls=RailRingPlanner.ringControls(w,stations);if(controls==null)return null;var line=new Line("rail_ring:"+stations.getFirst().village,stations.getFirst().village,controls,0,false);state.lines.add(line);state.markDirty();return line;
 }
 static List<RailVehicleEntity> startLine(ServerWorld w,Line line){
  var all=new ArrayList<RailVehicleEntity>();if(line.path.closed){for(var route:List.of(line.path,line.path.reversed())){var first=stationsFor(w,line).stream().min(Comparator.comparingDouble(s->s.center(0).squaredDistanceTo(line.path.center(0)))).orElseThrow();var train=spawnTrain(w,route,-4,route.closest(first.center(route==line.path?48:16)),1);var root=train.getFirst();var stops=stationsFor(w,line).stream().map(s->route.closest(s.center(route==line.path?48:16))).sorted().toList();root.setStops(stops);all.addAll(train);}}
  else{all.addAll(spawnTrain(w,line.path,-4,40,1));all.addAll(spawnTrain(w,line.path,4,line.path.length-8,-1));}return all;
 }
 static List<Station> stationsFor(ServerWorld w,Line line){return get(w).stations.stream().filter(s->line.path.center(line.path.closest(s.center(32))).squaredDistanceTo(s.center(32))<64).toList();}
 static List<Vec3d> alignedStationControls(List<Vec3d> original,List<Station> stations){
  var fixed=new ArrayList<>(original);
  for(var station:stations){int best=-1;double error=Double.MAX_VALUE;var from=station.center(0);var to=station.center(64);
   for(int i=0;i+3<fixed.size();i+=4){var direction=fixed.get(i+3).subtract(fixed.get(i)).normalize();if(direction.dotProduct(to.subtract(from).normalize())<.7)continue;
    double score=fixed.get(i).squaredDistanceTo(from)+fixed.get(i+3).squaredDistanceTo(to);if(score<error){error=score;best=i;}}
   if(best<0||error<.001||error>128*128*2)continue;
   var startDelta=from.subtract(fixed.get(best));var endDelta=to.subtract(fixed.get(best+3));
   var straight=RailPath.straight(from,to);for(int i=0;i<4;i++)fixed.set(best+i,straight.get(i));
   if(best>=4){fixed.set(best-1,from);fixed.set(best-2,fixed.get(best-2).add(startDelta));}
   if(best+4<fixed.size()){fixed.set(best+4,to);fixed.set(best+5,fixed.get(best+5).add(endDelta));}
  }
  if(fixed.getFirst().squaredDistanceTo(fixed.getLast())<128*128&&original.getFirst().squaredDistanceTo(original.getLast())<.001)fixed.set(fixed.size()-1,fixed.getFirst());
  return fixed;
 }
 static boolean detour(ServerWorld w,Line line,BlockPos obstacle){
  if(line.spawned||!line.path.closed)return false;
  var state=get(w);
  var hit=Vec3d.ofCenter(obstacle);
  // Give old saves whose legacy detours exhausted the limit a fresh budget.
  if(line.detours>=48)return false;
  if(line.detours==0||line.detours==24){
   var aligned=alignedStationControls(line.controls,stationsFor(w,line));
   if(!aligned.equals(line.controls)){
    return replaceRoute(w,line,aligned,obstacle);
   }
  }
  double at=line.path.closest(hit);
  var tangent=line.path.tangent(at);
  var side=new Vec3d(tangent.z,0,-tangent.x).normalize();
  var building=VillageProjects.get(w).jobs.stream()
      .filter(p->VillageBuildingSites.building(p.kind)
          &&obstacle.getX()>=p.min.getX()&&obstacle.getX()<=p.max.getX()
          &&obstacle.getZ()>=p.min.getZ()&&obstacle.getZ()<=p.max.getZ())
      .findFirst().orElse(null);
  var center=building==null?hit:Vec3d.ofCenter(building.min).add(Vec3d.ofCenter(building.max)).multiply(.5);
  if(side.dotProduct(line.path.center(at).subtract(center))<0)side=side.negate();

  var stations=stationsFor(w,line);
  var controls=new ArrayList<Vec3d>();
  for(var point:line.controls){
   double distance=Math.hypot(point.x-hit.x,point.z-hit.z);
   double strength=distance<160?64*(.5+.5*Math.cos(Math.PI*distance/160)):0;
   for(var station:stations){
    var offset=point.subtract(station.center(0));
    double along=offset.x*station.forward().getOffsetX()+offset.z*station.forward().getOffsetZ();
    double across=Math.abs(offset.x*station.side().getOffsetX()+offset.z*station.side().getOffsetZ());
    // Lock the station and its tangent, while allowing the outer approach to curve.
    double endDistance=Math.max(-along,along-64);
    if(across<22)strength*=Math.clamp((endDistance-18)/32,0,1);
   }
   controls.add(point.add(side.multiply(strength)));
  }
  if(controls.equals(line.controls))return false;
  return replaceRoute(w,line,controls,obstacle);
 }
 private static boolean replaceRoute(ServerWorld world,Line previous,List<Vec3d> controls,BlockPos obstacle){
  var state=get(world);
  int index=state.lines.indexOf(previous);
  if(index<0)return false;
  var replacement=new Line(previous.id,previous.village,controls,0,false,previous.detours+1,previous.revision+1);
  state.lines.set(index,replacement);
  state.markDirty();

  var projects=VillageProjects.get(world);
  for(int segment=0;segment<Math.min(7,previous.next);segment++){
   String obsolete=segmentId(previous,segment);
   for(var job:projects.jobs)if(job.id.equals(obsolete)&&!job.done)projects.retire(job);
  }
  var newTracks=new HashSet<BlockPos>();
  for(double d=0;d<replacement.path.length;d+=4)for(double lane:new double[]{-4,4})
   newTracks.add(BlockPos.ofFloored(replacement.path.at(d,lane)));
  for(double d=0;d<previous.next*16;d+=4)for(double lane:new double[]{-4,4}){
   var pos=BlockPos.ofFloored(previous.path.at(d,lane));
   if(newTracks.contains(pos))continue;
   if(world.isChunkLoaded(pos)){
    if(world.getBlockState(pos).isOf(RailContent.TRACK))world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
   }else RailDeferred.defer(world,pos,Blocks.AIR.getDefaultState());
  }
  var deferred=RailDeferred.get(world);
  deferred.pending.entrySet().removeIf(e->e.getValue().state().isOf(RailContent.TRACK)
      &&e.getKey().isWithinDistance(obstacle,240)&&!newTracks.contains(e.getKey()));
  deferred.markDirty();
  org.slf4j.LoggerFactory.getLogger("halloween-railway").info("Rerouted railway at {} (revision {})",obstacle,replacement.revision);
  return true;
 }
 static boolean insideStation(RailwayNetwork state,BlockPos pos){
  for(var s:state.stations){var a=s.pos(0,0,-3);var b=s.pos(45,24,65);if(pos.getX()>=Math.min(a.getX(),b.getX())&&pos.getX()<=Math.max(a.getX(),b.getX())&&pos.getZ()>=Math.min(a.getZ(),b.getZ())&&pos.getZ()<=Math.max(a.getZ(),b.getZ())&&pos.getY()>=a.getY()&&pos.getY()<=b.getY())return true;}return false;
 }
 static String segmentId(Line line,int index){return line.id+(line.revision==0?"":":revision_"+line.revision)+":"+index;}
 private static boolean trainStartReady(ServerWorld w,Line line){
  double[] starts;
  if(line.path.closed){
   var station=stationsFor(w,line).stream().min(Comparator.comparingDouble(s->s.center(0).squaredDistanceTo(line.path.center(0)))).orElse(null);
   if(station==null)return false;
   starts=new double[]{line.path.closest(station.center(48)),line.path.closest(station.center(16))};
  }else starts=new double[]{40,line.path.length-8};
  boolean ready=true;
  for(double start:starts)for(int offset=-32;offset<=32;offset+=16){
   var pos=BlockPos.ofFloored(line.path.at(Math.clamp(start+offset,0,line.path.length),0));
   if(!VillageProjects.keepLoaded(w,pos))ready=false;
  }
  return ready;
 }
 static boolean runningAt(ServerWorld w,long village){
  return get(w).lines.stream().anyMatch(line->line.spawned&&stationsFor(w,line).stream().anyMatch(station->station.village==village));
 }
 static void planNext(ServerWorld w,Line line){
  var state=get(w);var projects=VillageProjects.get(w);int total=(int)Math.ceil(line.path.length/16);
  if(line.next>0&&line.next<=7){var previous=projects.jobs.stream().filter(p->p.id.equals(segmentId(line,line.next-1))).findFirst().orElse(null);if(previous==null||!previous.done)return;}
  if(line.next>=total){
   if(!line.spawned&&trainStartReady(w,line)&&!startLine(w,line).isEmpty()){line.spawned=true;state.markDirty();}
   return;
  }
  if(line.path.closed&&state.stations.stream().anyMatch(s->!projects.complete(s.village,"rail_station")))return;
  if(line.next>=7){RailTerrain.place(w,line,line.next);return;}
  int index=line.next;var plan=RailTerrain.plan(w,line,index*16,Math.min(line.path.length,(index+1)*16),true);if(plan==null){if(RailTerrain.lastBlocked!=null)detour(w,line,RailTerrain.lastBlocked);return;}
  projects.add(w,segmentId(line,index),line.village,"railway",plan);if(projects.exists(segmentId(line,index))){line.next++;state.markDirty();}
 }
 static void configureTrack(ServerWorld w,BlockPos pos){
  if(!(w.getBlockEntity(pos) instanceof RailTrackEntity track))return;
  for(var line:get(w).lines)for(double d=Math.max(0,(line.next-2)*16);d<Math.min(line.path.length,(line.next+1)*16);d+=4)for(double lane:new double[]{-4,4})if(BlockPos.ofFloored(line.path.at(d,lane)).equals(pos)){
   var points=new ArrayList<Vec3d>();for(int i=0;i<=8;i++)points.add(line.path.at(Math.min(line.path.length,d+i*.5),lane).subtract(Vec3d.of(pos)));track.setPoints(points);return;
  }
 }
 public static List<RailVehicleEntity> spawnTrain(ServerWorld w,RailPath path,double lane,double distance,int direction){
  var result=new ArrayList<RailVehicleEntity>();UUID leader=null;
  for(int car=0;car<3;car++){var vehicle=RailContent.VEHICLE.create(w,SpawnReason.EVENT);if(vehicle==null)throw new IllegalStateException("rail entity creation");vehicle.configure(car==0?0:1,path,lane,distance-car*15,leader,car);vehicle.direction=direction;vehicle.dwell=100;w.spawnEntity(vehicle);result.add(vehicle);if(car==0)leader=vehicle.getUuid();}
  return result;
 }
 static void showcase(ServerWorld w,BlockPos origin){
  var station=new Station(origin.asLong(),origin,Direction.SOUTH.getHorizontalQuarterTurns(),0);var blocks=building(w,station,"railway_station");blocks.putAll(building(w,foundry(station),"rail_foundry"));
  for(var entry:blocks.entrySet())w.setBlockState(entry.getKey(),VillageConnections.connected(w,entry.getKey(),entry.getValue()),Block.NOTIFY_LISTENERS);
  var path=new RailPath(RailPath.straight(station.center(0),station.center(220)));
  for(int d=0;d<220;d++)for(int x=-5;x<=5;x++)w.setBlockState(BlockPos.ofFloored(path.at(d,x)).down(),Blocks.STONE_BRICKS.getDefaultState());
  for(int d=0;d<220;d+=4)for(double lane:new double[]{-4,4}){var p=BlockPos.ofFloored(path.at(d,lane));w.setBlockState(p,RailContent.TRACK.getDefaultState());if(w.getBlockEntity(p) instanceof RailTrackEntity track){var points=new ArrayList<Vec3d>();for(int i=0;i<=8;i++)points.add(path.at(d+i*.5,lane).subtract(Vec3d.of(p)));track.setPoints(points);}}
  spawnTrain(w,path,-4,40,1);spawnTrain(w,path,4,55,-1);
 }
 static void register(){
  CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->dispatcher.register(CommandManager.literal("railway").requires(s->s.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.GAMEMASTERS)))
   .then(CommandManager.literal("showcase").executes(c->{var p=c.getSource().getPlayerOrThrow();var w=p.getEntityWorld();var origin=p.getBlockPos().add(16,12,16);showcase(w,origin);p.sendMessage(Text.literal("Вокзал, цех и два состава готовы к осмотру."),false);return 1;}))
   .then(CommandManager.literal("build").executes(c->{var p=c.getSource().getPlayerOrThrow();var w=p.getEntityWorld();var team=VillageEras.team(w,p.getBlockPos());if(team==null)return 0;var request=VillageEraData.get(w).settlement(team);request.xp.put("railway_requested",p.getHorizontalFacing().getHorizontalQuarterTurns()+1);VillageEraData.get(w).markDirty();planStation(w,team.village,team.anchor,p.getHorizontalFacing());p.sendMessage(Text.literal("Строители выбирают сухую площадку для вокзала и рельсового цеха."),false);return 1;}))
   .then(CommandManager.literal("connect").executes(c->{var p=c.getSource().getPlayerOrThrow();RailRingPlanner.request(p.getEntityWorld(),p.getBlockPos());p.sendMessage(Text.literal("Планируется кольцо через шесть деревень. Поезда пойдут навстречу по разным путям."),false);return 1;}))
   .then(CommandManager.literal("ring").executes(c->{var p=c.getSource().getPlayerOrThrow();RailRingPlanner.request(p.getEntityWorld(),p.getBlockPos());p.sendMessage(Text.literal("Начат поиск шести деревень и площадок вокзалов."),false);return 1;}))));
  ServerTickEvents.END_WORLD_TICK.register(w->{if(w.getPlayers().isEmpty()||w.getTime()%20!=0)return;var state=get(w);RailWorks.tick(w,state);RailRingPlanner.tick(w);RailStationSafety.tick(w);RailDeferred.tick(w);if(w.getTime()%20==0){
   var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);for(var team:memory.teams){var s=VillageEraData.get(w).settlement(team);boolean requested=s.xp.getOrDefault("railway_requested",0)>0;
    if(!requested&&!VillageSteam.infrastructureStarted(s))continue;
    var station=planStation(w,team.village,team.anchor,requested?Direction.fromHorizontalQuarterTurns(s.xp.get("railway_requested")-1):Direction.SOUTH);
    if(station!=null&&w.getTime()%100==0)connectVillageRoad(w,team,station);}
   if(!state.stations.isEmpty()&&state.lines.isEmpty())RailRingPlanner.request(w,state.stations.getFirst().origin);
  }for(var line:List.copyOf(state.lines))for(int budget=0;budget<4;budget++){int before=line.next;planNext(w,line);if(line.next==before||line.next<=7)break;}});
 }
}
