package dev.halloween;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Bounded road surveys and durable construction. Vehicles use the actual finished carriageway. */
final class CityStreets extends PersistentState {
 static final class Street {
  static final Codec<Street> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("id").forGetter(s->s.id),Codec.LONG.fieldOf("village").forGetter(s->s.village),Vec3d.CODEC.listOf().fieldOf("centers").forGetter(s->s.centers)).apply(i,Street::new));
  final String id;final long village;final List<Vec3d> centers;private RailPath loop;private List<Double> stops;
  Street(String id,long village,List<Vec3d> centers){this.id=id;this.village=village;this.centers=List.copyOf(centers);}
  RailPath loop(){if(loop==null)loop=trafficLoop(centers);return loop;}
  List<Double> stops(){
   if(stops==null){
    var t=tangent(centers,centers.size()-1);
    stops=List.of(0.,loop().closest(centers.getLast().add(t.z*6,0,-t.x*6)));
   }
   return stops;
  }
  double nearest(Vec3d position,double previous){
   var path=loop();
   if(previous<0||path.center(previous).squaredDistanceTo(position)>14*14)return path.closest(position);
   double distance=Double.POSITIVE_INFINITY,best=previous;
   for(double next=previous-3;next<=previous+6;next+=.2){
    double error=path.center(next).squaredDistanceTo(position);
    if(error<distance){distance=error;best=next;}
   }
   return path.wrap(best);
  }
 }
 static final PersistentStateType<CityStreets> TYPE=new PersistentStateType<>("halloween_city_streets",()->new CityStreets(List.of()),Street.CODEC.listOf().xmap(CityStreets::new,s->s.streets),null);
 final List<Street> streets;CityStreets(List<Street> streets){this.streets=new ArrayList<>(streets);}
 static CityStreets get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(TYPE);}
 record Sample(int floor,boolean water){}
 record Column(int x,int z,double top,double distance,boolean pavement,int along){}
 record Node(int x,int z,int floor,double cost,double score,Node parent){}
 static final class Work {
  final String id;final long village;final BlockPos start,goal;final PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::score));final Map<Long,Double> costs=new HashMap<>();final Map<Long,Sample> samples=new HashMap<>();
  final LinkedHashMap<BlockPos,BlockState> plan=new LinkedHashMap<>();
  final Map<Long,Column> columns=new LinkedHashMap<>();
  final Set<Long> chunks=new LinkedHashSet<>();
  Iterator<Column> remaining;
  Map<Long,Map<BlockPos,BlockState>> sections;
  Iterator<Map.Entry<Long,Map<BlockPos,BlockState>>> pendingSections;
  Map.Entry<Long,Map<BlockPos,BlockState>> currentSection;
  Column waitingColumn;
  List<Vec3d> path;
  int built,visited;
  boolean waiting,failed;
  Work(String id,long village,BlockPos start,BlockPos goal){this.id=id;this.village=village;this.start=start;this.goal=goal;open.add(new Node(start.getX(),start.getZ(),start.getY()-1,0,0,null));costs.put(key(start.getX(),start.getZ()),0.);}
 }
 static final Map<ServerWorld,Work> WORK=new WeakHashMap<>();
 static long key(int x,int z){return ChunkPos.toLong(x,z);}
 static String id(IndustrialPlants.Plant p){return "city_street:"+p.id();}
 static Street ready(ServerWorld w,IndustrialPlants.Plant plant){return get(w).streets.stream().filter(s->s.id.equals(id(plant))&&finished(w,s.id)).findFirst().orElse(null);}
 static void register(){ServerTickEvents.END_WORLD_TICK.register(w->{if(w.getPlayers().isEmpty()||!RailPreparation.ready(w))return;var work=WORK.get(w);
  if(work==null){if(w.getTime()%20!=13)return;for(var plant:IndustrialPlants.get(w).plants){if(get(w).streets.stream().anyMatch(s->s.id.equals(id(plant))))continue;var s=VillageEraData.get(w).settlements.get(plant.village);if(s==null||!s.xp.containsKey("industrial_x"))continue;
    var start=plant.base.add(18,1,-22);var goal=new BlockPos(s.xp.get("industrial_x")+19,s.xp.get("industrial_y")+1,s.xp.get("industrial_z")-18);
    if(w.getPlayers().stream().noneMatch(p->p.getBlockPos().isWithinDistance(start,768)))continue;work=new Work(id(plant),plant.village,start,goal);WORK.put(w,work);break;}
  }
  if(work!=null){advance(w,work);if(work.failed)WORK.remove(w);else if(work.remaining!=null&&!work.remaining.hasNext()&&work.waitingColumn==null){
    if(queueSections(w,work)){
     get(w).streets.add(new Street(work.id,work.village,work.path));
     get(w).markDirty();
     WORK.remove(w);
    }
  }}
 });}
 static boolean finished(ServerWorld world,String id){
  var jobs=VillageProjects.get(world).jobs;
  return jobs.stream().anyMatch(p->p.id.equals(id)||p.id.startsWith(id+":chunk:"))
      &&jobs.stream().filter(p->p.id.equals(id)||p.id.startsWith(id+":chunk:")).allMatch(p->p.done);
 }
 private static boolean queueSections(ServerWorld world,Work work){
  if(work.sections==null){
   work.sections=new LinkedHashMap<>();
   for(var entry:work.plan.entrySet())
    work.sections.computeIfAbsent(new ChunkPos(entry.getKey()).toLong(),ignored->new LinkedHashMap<>()).put(entry.getKey(),entry.getValue());
   work.pendingSections=work.sections.entrySet().iterator();
  }
  for(int budget=0;budget<2;budget++){
   if(work.currentSection==null){
    if(!work.pendingSections.hasNext())return true;
    work.currentSection=work.pendingSections.next();
   }
   var chunk=new ChunkPos(work.currentSection.getKey());
   if(!VillageProjects.keepLoaded(world,new BlockPos(chunk.getStartX(),64,chunk.getStartZ())))return false;
   String id=work.id+":chunk:"+work.currentSection.getKey();
   VillageProjects.get(world).add(world,id,work.village,"city_street",work.currentSection.getValue());
   if(!VillageProjects.get(world).exists(id))return false;
   work.currentSection=null;
  }
  return false;
 }
 static boolean protectedPlot(ServerWorld w,int x,int z,int radius){
  for(var p:VillageProjects.get(w).jobs)if(VillageBuildingSites.building(p.kind)){
   int z0=p.min.getZ();if(p.kind.equals("industrial_factory")){var plant=IndustrialPlants.get(w).plants.stream().filter(a->a.id().equals(p.id)).findFirst().orElse(null);if(plant!=null)z0=plant.base.getZ();}
   if(x+radius>=p.min.getX()&&x-radius<=p.max.getX()&&z+radius>=z0&&z-radius<=p.max.getZ())return true;
  }
  return w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses.stream().filter(VillageBuildingSites::reserves).filter(h->!h.recordKey.startsWith("industrial_plant:")).anyMatch(h->x+radius>=h.min.getX()&&x-radius<=h.max.getX()&&z+radius>=h.min.getZ()&&z-radius<=h.max.getZ());
 }
 static Sample sample(ServerWorld w,Work work,int x,int z){long key=key(x,z);if(work.samples.containsKey(key))return work.samples.get(key);
  int radius=Math.min(Math.hypot(x-work.start.getX(),z-work.start.getZ()),Math.hypot(x-work.goal.getX(),z-work.goal.getZ()))<16?10:4;
  if(protectedPlot(w,x,z,Math.max(radius,9))){work.samples.put(key,null);return null;}
  boolean water=false;Integer height=null;
  for(int dx:new int[]{-radius,0,radius})for(int dz:new int[]{-radius,0,radius}){
   var probe=new BlockPos(x+dx,64,z+dz);if(!w.isChunkLoaded(probe)){VillageProjects.keepLoaded(w,probe);work.waiting=true;return null;}
   int floor=RailTerrain.ground(w,x+dx,z+dz)-1;if(dx==0&&dz==0)height=floor;
  }
  if(height==null)return null;water=!w.getFluidState(new BlockPos(x,height,z)).isEmpty();
  for(int dx:new int[]{-radius,0,radius})for(int dz:new int[]{-radius,0,radius}){int floor=RailTerrain.ground(w,x+dx,z+dz)-1;var ground=new BlockPos(x+dx,floor,z+dz);var state=w.getBlockState(ground);boolean owned=VillageProjects.get(w).expected.containsKey(ground)&&!protectedPlot(w,ground.getX(),ground.getZ(),0);
   if(!RailTerrain.natural(state)&&!state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)&&!state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)&&!owned){work.samples.put(key,null);return null;}if(Math.abs(floor-height)>3){work.samples.put(key,null);return null;}}
  var result=new Sample(height,water);work.samples.put(key,result);return result;
 }
 static void advance(ServerWorld w,Work work){long until=System.nanoTime()+750_000;work.waiting=false;
  if(work.path==null){int budget=96;while(!work.open.isEmpty()&&budget-->0&&System.nanoTime()<until){var node=work.open.poll();if(node.cost>work.costs.getOrDefault(key(node.x,node.z),Double.POSITIVE_INFINITY)+.01)continue;
    if(Math.hypot(node.x-work.goal.getX(),node.z-work.goal.getZ())<=2.1){var points=new ArrayList<Vec3d>();for(var n=node;n!=null;n=n.parent)points.add(new Vec3d(n.x+.5,n.floor+1,n.z+.5));Collections.reverse(points);work.path=smooth(points);break;}
    if(++work.visited>14000){work.failed=true;break;}
    for(int[] step:new int[][]{{2,0},{-2,0},{0,2},{0,-2}}){int x=node.x+step[0],z=node.z+step[1];if(x<Math.min(work.start.getX(),work.goal.getX())-96||x>Math.max(work.start.getX(),work.goal.getX())+96||z<Math.min(work.start.getZ(),work.goal.getZ())-96||z>Math.max(work.start.getZ(),work.goal.getZ())+96)continue;
     var s=sample(w,work,x,z);if(work.waiting){work.visited--;work.open.add(node);return;}if(s==null||Math.abs(s.floor-node.floor)>1)continue;double cost=node.cost+2+Math.abs(s.floor-node.floor)*2+(s.water?8:0);long key=key(x,z);if(cost>=work.costs.getOrDefault(key,Double.POSITIVE_INFINITY))continue;work.costs.put(key,cost);double score=cost+Math.abs(x-work.goal.getX())+Math.abs(z-work.goal.getZ());work.open.add(new Node(x,z,s.floor,cost,score,node));
    }
   }if(work.open.isEmpty()&&work.path==null)work.failed=true;return;
  }
  if(work.remaining==null){
   while(work.built<work.path.size()-1&&System.nanoTime()<until){
    collectColumns(work,work.built++);
   }
   if(work.built<work.path.size()-1)return;
   work.remaining=work.columns.values().iterator();
  }
  while(work.remaining.hasNext()&&System.nanoTime()<until){
   // Leave the current column pending until its chunk is actually available.
   if(work.waitingColumn==null)work.waitingColumn=work.remaining.next();
   var column=work.waitingColumn;
   var pos=new BlockPos(column.x,(int)Math.ceil(column.top)-1,column.z);
   if(!VillageProjects.keepLoaded(w,pos))return;
   if(!planColumn(w,work,column,pos)){work.failed=true;return;}
   work.waitingColumn=null;
  }
  if(work.waitingColumn!=null){
   var column=work.waitingColumn;
   var pos=new BlockPos(column.x,(int)Math.ceil(column.top)-1,column.z);
   if(!VillageProjects.keepLoaded(w,pos))return;
   if(!planColumn(w,work,column,pos)){work.failed=true;return;}
   work.waitingColumn=null;
  }
 }
 private static void collectColumns(Work work,int index){
  var a=work.path.get(index);
  var b=work.path.get(index+1);
  var forward=b.subtract(a).multiply(1,0,1).normalize();
  var side=new Vec3d(forward.z,0,-forward.x);
  for(double t:new double[]{0,.5,1}){
   var at=a.lerp(b,t);
   boolean terminal=Math.min(at.distanceTo(work.path.getFirst()),at.distanceTo(work.path.getLast()))<=14;
   int radius=terminal?10:4;
   for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
    int distance=dx*dx+dz*dz;
    if(distance>radius*radius)continue;
    int x=(int)Math.floor(at.x)+dx,z=(int)Math.floor(at.z)+dz;
    long key=key(x,z);
    var previous=work.columns.get(key);
    if(previous!=null&&previous.distance<=distance)continue;
    double across=Math.abs(dx*side.x+dz*side.z);
    int along=(int)Math.round(x*forward.x+z*forward.z);
    work.columns.put(key,new Column(x,z,Math.rint(at.y*2)/2,distance,!terminal&&across>=3.5,along));
   }
  }
 }
 private static boolean planColumn(ServerWorld w,Work work,Column column,BlockPos floor){
  if(protectedPlot(w,column.x,column.z,0))return false;
  for(int h=0;h<=5;h++){
   var pos=floor.up(h);
   var actual=w.getBlockState(pos);
   if(actual.hasBlockEntity()||(!RailTerrain.natural(actual)&&!actual.isAir()&&!actual.isReplaceable()
     &&!actual.isIn(net.minecraft.registry.tag.BlockTags.LOGS)&&!actual.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)
     &&!VillageConnections.matches(w,pos,VillageProjects.get(w).expected.getOrDefault(pos,Blocks.AIR.getDefaultState()),actual)))return false;
  }
  boolean slab=column.top!=Math.floor(column.top);
  var material=slab?(column.pavement?Blocks.SMOOTH_STONE_SLAB:Blocks.POLISHED_BLACKSTONE_SLAB)
      :(column.pavement?Blocks.SMOOTH_STONE:Blocks.GRAY_CONCRETE);
  work.plan.put(floor,material.getDefaultState());
  work.chunks.add(ChunkPos.toLong(floor));
  for(int h=1;h<=5;h++)work.plan.put(floor.up(h),Blocks.AIR.getDefaultState());
  boolean water=w.getFluidState(floor.down()).isIn(net.minecraft.registry.tag.FluidTags.WATER);
  if(water)work.plan.put(floor.down(),Blocks.STONE_BRICKS.getDefaultState());
  if(water&&column.pavement&&!slab){
   work.plan.put(floor.up(),Blocks.STONE_BRICK_WALL.getDefaultState());
   if(Math.floorMod(column.along,12)==0)work.plan.put(floor.up(2),Blocks.LANTERN.getDefaultState());
  }
  if(!water||column.pavement&&Math.floorMod(column.along,12)<2){
   for(int depth=water?2:1;depth<=64;depth++){
    var support=floor.down(depth);
    var state=w.getBlockState(support);
    if(state.isSolidBlock(w,support)||state.hasBlockEntity())break;
    work.plan.put(support,Blocks.STONE_BRICKS.getDefaultState());
   }
  }
  return true;
 }
 static List<Vec3d> smooth(List<Vec3d> points){
  var result=new ArrayList<Vec3d>(points.size());
  for(int i=0;i<points.size();i++){
   int radius=Math.min(3,Math.min(i,points.size()-1-i));
   var sum=Vec3d.ZERO;
   for(int j=-radius;j<=radius;j++)sum=sum.add(points.get(i+j));
   result.add(sum.multiply(1d/(2*radius+1)));
  }
  return result;
 }
 static RailPath trafficLoop(List<Vec3d> centers){var points=new ArrayList<Vec3d>();int n=centers.size();double[] lengths=new double[n];for(int i=1;i<n;i++)lengths[i]=lengths[i-1]+centers.get(i).distanceTo(centers.get(i-1));
  for(int i=0;i<n;i++){var t=tangent(centers,i);double width=1.5+4.5*Math.max(0,1-Math.min(lengths[i],lengths[n-1]-lengths[i])/16);points.add(centers.get(i).add(t.z*width,0,-t.x*width));}
  var end=centers.getLast();var direction=tangent(centers,n-1);var side=new Vec3d(direction.z,0,-direction.x);for(int i=1;i<=16;i++){double angle=i*Math.PI/16;points.add(end.add(side.multiply(Math.cos(angle)*6)).add(direction.multiply(Math.sin(angle)*6)));}
  for(int i=n-2;i>=0;i--){var t=tangent(centers,i);double width=1.5+4.5*Math.max(0,1-Math.min(lengths[i],lengths[n-1]-lengths[i])/16);points.add(centers.get(i).add(-t.z*width,0,t.x*width));}
  var start=centers.getFirst();direction=tangent(centers,0);side=new Vec3d(direction.z,0,-direction.x);for(int i=1;i<=16;i++){double angle=Math.PI+i*Math.PI/16;points.add(start.add(side.multiply(Math.cos(angle)*6)).add(direction.multiply(Math.sin(angle)*6)));}
  points.set(points.size()-1,points.getFirst());var controls=new ArrayList<Vec3d>();for(int i=0;i<points.size()-1;i++){var a=points.get(i);var b=points.get(i+1);var before=points.get(Math.floorMod(i-1,points.size()-1));var after=points.get((i+2)%(points.size()-1));double handle=a.distanceTo(b)/3;controls.addAll(List.of(a,a.add(b.subtract(before).normalize().multiply(handle)),b.subtract(after.subtract(a).normalize().multiply(handle)),b));}return new RailPath(controls);
 }
 static Vec3d tangent(List<Vec3d> points,int index){return points.get(Math.min(points.size()-1,index+1)).subtract(points.get(Math.max(0,index-1))).multiply(1,0,1).normalize();}
 private CityStreets(){this(List.of());}
}
