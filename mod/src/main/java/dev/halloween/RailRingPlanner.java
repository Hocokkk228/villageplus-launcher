package dev.halloween;
import java.util.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
/** Resumable, bounded discovery of six real villages; no giant synchronous world scan. */
final class RailRingPlanner extends PersistentState {
 static final Codec<RailRingPlanner> CODEC=RecordCodecBuilder.create(i->i.group(BlockPos.CODEC.optionalFieldOf("origin").forGetter(s->Optional.ofNullable(s.origin)),BlockPos.CODEC.listOf().optionalFieldOf("villages",List.of()).forGetter(s->s.villages),Codec.INT.optionalFieldOf("probe",0).forGetter(s->s.probe),Codec.BOOL.optionalFieldOf("complete",false).forGetter(s->s.complete)).apply(i,RailRingPlanner::new));
 static final PersistentStateType<RailRingPlanner> TYPE=new PersistentStateType<>("halloween_rail_ring",()->new RailRingPlanner(Optional.empty(),List.of(),0,false),CODEC,null);
 BlockPos origin;final List<BlockPos> villages;int probe;boolean complete;
 RailRingPlanner(Optional<BlockPos> origin,List<BlockPos> villages,int probe,boolean complete){this.origin=origin.orElse(null);this.villages=new ArrayList<>(villages);this.probe=probe;this.complete=complete;}
 static RailRingPlanner get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(TYPE);}
 static void request(ServerWorld w,BlockPos from){
  var s=get(w);if(s.origin!=null)return;
  s.origin=from.toImmutable();
  var station=RailwayNetwork.get(w).stations.stream().filter(t->t.origin.equals(from)).findFirst().orElse(null);
  if(station!=null)for(var team:w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).teams)
   if(team.village==station.village){s.villages.add(team.anchor);break;}
  s.markDirty();
 }
 static final Map<ServerWorld,Long> GENERATED_AT=new WeakHashMap<>();
 static void register(){net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(w->{if(w.getPlayers().isEmpty()||get(w).origin==null||!RailPreparation.ready(w))return;var network=RailwayNetwork.get(w);if(!network.stations.isEmpty())generateRemoteStation(w,network.stations,network.stations.getFirst().village);});}
 private static void generateRemoteStation(ServerWorld w,List<RailwayNetwork.Station> stations,long local){
  if(stations.isEmpty()||GENERATED_AT.getOrDefault(w,Long.MIN_VALUE)==w.getTime())return;GENERATED_AT.put(w,w.getTime());
  for(var station:stations){
   var projects=VillageProjects.get(w);
   if(station.village!=local&&(!projects.complete(station.village,"rail_station")||!projects.complete(station.village,"rail_foundry"))){
    // Old route detours could lay track underneath an unfinished station's foundation.
    // Only clear track introduced after this surveyed job, before any train is allowed to run.
    if(RailwayNetwork.get(w).lines.stream().noneMatch(l->l.spawned))
     for(var job:projects.jobs)if(job.village==station.village&&!job.done&&(job.kind.equals("rail_station")||job.kind.equals("rail_foundry")))
      for(int i=job.index;i<Math.min(job.index+32,job.plan.size());i++){var c=job.plan.get(i);
       if(!c.after().isOf(RailContent.TRACK)&&!c.before().hasBlockEntity()&&w.isChunkLoaded(c.pos())&&w.getBlockState(c.pos()).isOf(RailContent.TRACK))w.setBlockState(c.pos(),net.minecraft.block.Blocks.AIR.getDefaultState(),net.minecraft.block.Block.NOTIFY_LISTENERS);}
    VillageProjects.materialize(w,station.village,1024,true);return;
   }
  }
 }
 static void tick(ServerWorld w){
  var s=get(w);if(s.origin==null||w.getTime()%20!=0)return;
  var network=RailwayNetwork.get(w);
  if(s.complete){
   var line=network.lines.stream().filter(l->l.id.startsWith("rail_ring:")).findFirst().orElse(null);
   if(line!=null)generateRemoteStation(w,network.stations,line.village);
   return;
  }
  if(s.villages.size()<6){
   var p=RailPreparation.discover(w,s);
   if(p!=null&&s.villages.stream().noneMatch(v->VillageEras.horizontal(v,p)<320*320)){s.villages.add(p);WorldSettlements.discover(w,p);}
   if(s.villages.size()==6){double cx=s.villages.stream().mapToInt(BlockPos::getX).average().orElse(0),cz=s.villages.stream().mapToInt(BlockPos::getZ).average().orElse(0);s.villages.sort(Comparator.comparingDouble(p0->Math.atan2(p0.getZ()-cz,p0.getX()-cx)));var nearest=s.villages.stream().min(Comparator.comparingDouble(p0->VillageEras.horizontal(p0,s.origin))).orElseThrow();Collections.rotate(s.villages,-s.villages.indexOf(nearest));}
   s.markDirty();return;
  }
  var stations=new ArrayList<RailwayNetwork.Station>();
  for(int i=0;i<6;i++){var p=s.villages.get(i);
   var existing=network.stations.stream().filter(station->!stations.contains(station))
       .filter(station->VillageEras.horizontal(station.origin,p)<400*400)
       .min(Comparator.comparingDouble(station->VillageEras.horizontal(station.origin,p))).orElse(null);
   if(existing!=null){stations.add(existing);
    // Finish one generated destination before leasing terrain for another. Alternating distant sites
    // evicts each other's chunks and leaves several stations permanently half materialized.
    var projects=VillageProjects.get(w);
    if(stations.size()>1&&(!projects.complete(existing.village,"rail_station")||!projects.complete(existing.village,"rail_foundry"))){
     generateRemoteStation(w,stations,stations.getFirst().village);return;
    }
    continue;
   }
   if(!stations.isEmpty())generateRemoteStation(w,stations,stations.getFirst().village);
   if(!RailPreparation.prepare(w,p))return;
   var before=s.villages.get(Math.floorMod(i-1,6));var after=s.villages.get((i+1)%6);var facing=Direction.getFacing(after.getX()-before.getX(),0,after.getZ()-before.getZ());var localTeam=VillageEras.team(w,p);long village=localTeam!=null&&VillageEras.horizontal(localTeam.anchor,p)<256*256
       &&stations.stream().noneMatch(station->station.village==localTeam.village)?localTeam.village:p.asLong();RailwayNetwork.planStation(w,village,p,facing);return;
  }
  generateRemoteStation(w,stations,stations.getFirst().village);
  if(RailwayNetwork.connectRing(w,stations)!=null){s.complete=true;s.markDirty();}
 }
 static final Map<ServerWorld,Map<Long,Integer>> HEIGHTS=new WeakHashMap<>();
 static List<Vec3d> ringControls(ServerWorld w,List<RailwayNetwork.Station> stations){
  var out=new ArrayList<Vec3d>();for(int i=0;i<stations.size();i++){
   var a=stations.get(i);var b=stations.get((i+1)%stations.size());out.addAll(RailPath.straight(a.center(0),a.center(64)));
   var from=a.center(128);var to=b.center(-96);out.addAll(RailPath.straight(a.center(64),from));double handle=Math.clamp(from.distanceTo(to)/4,64,220);
   var raw=new RailPath(List.of(from,from.add(a.forward().getOffsetX()*handle,0,a.forward().getOffsetZ()*handle),to.add(-b.forward().getOffsetX()*handle,0,-b.forward().getOffsetZ()*handle),to));
   int count=Math.max(4,(int)(raw.length/64));var points=new ArrayList<Vec3d>();
   for(int k=0;k<=count;k++){double d=raw.length*k/count;var p=raw.center(d);
    if(k>0&&k<count)for(var city:WorldSettlements.get(w).cities){double dx=p.x-city.pos.getX(),dz=p.z-city.pos.getZ(),distance=Math.hypot(dx,dz);if(distance<192){if(distance<1){dx=1;dz=0;distance=1;}p=new Vec3d(city.pos.getX()+dx/distance*224,p.y,city.pos.getZ()+dz/distance*224);}}
    var probe=BlockPos.ofFloored(p);var samples=HEIGHTS.computeIfAbsent(w,key->new HashMap<>());long key=ChunkPos.toLong(probe.getX(),probe.getZ());Integer known=samples.get(key);if(known==null){if(!w.isChunkLoaded(new ChunkPos(probe).toLong())){VillageProjects.keepLoaded(w,probe);return null;}known=RailTerrain.ground(w,probe.getX(),probe.getZ());samples.put(key,known);}double ground=known;double natural=Math.max(w.getSeaLevel()+1,ground);
    double blend=Math.min(1,Math.min(d,raw.length-d)/192);double y=p.y*(1-blend)+natural*blend;points.add(new Vec3d(p.x,y,p.z));}
   points.set(0,from);points.set(count,to);
   // Grading: avoid towers by following the ground; keep climbs gentle enough to walk through a carriage.
   for(int pass=0;pass<6;pass++){for(int k=1;k<count;k++){var prev=points.get(k-1);var p=points.get(k);double max=.07*Math.hypot(p.x-prev.x,p.z-prev.z);points.set(k,new Vec3d(p.x,Math.clamp(p.y,prev.y-max,prev.y+max),p.z));}for(int k=count-1;k>0;k--){var next=points.get(k+1);var p=points.get(k);double max=.07*Math.hypot(p.x-next.x,p.z-next.z);points.set(k,new Vec3d(p.x,Math.clamp(p.y,next.y-max,next.y+max),p.z));}}
   for(int k=0;k<count;k++){var p=points.get(k);var q=points.get(k+1);var tangent0=k==0?from.subtract(a.center(48)).normalize():q.subtract(points.get(k-1)).normalize();var tangent1=k==count-1?b.center(16).subtract(to).normalize():points.get(k+2).subtract(p).normalize();double scale=p.distanceTo(q)/3;out.addAll(List.of(p,p.add(tangent0.multiply(scale)),q.subtract(tangent1.multiply(scale)),q));}
   out.addAll(RailPath.straight(to,b.center(0)));
  }return out;
 }
}
