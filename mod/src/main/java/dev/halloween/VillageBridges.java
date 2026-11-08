package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
/** River crossings to existing workplaces, queued for builders with open bank approaches. */
final class VillageBridges {
 static List<BlockPos> trace(BlockPos from,BlockPos to){var out=new ArrayList<BlockPos>();int x=from.getX(),z=from.getZ();out.add(new BlockPos(x,0,z));int dx=Math.abs(to.getX()-x),dz=Math.abs(to.getZ()-z),sx=Integer.compare(to.getX(),x),sz=Integer.compare(to.getZ(),z),ix=0,iz=0;
  while(ix<dx||iz<dz){if(ix<dx&&(iz==dz||(long)(ix+1)*dz<=(long)(iz+1)*dx)){x+=sx;ix++;}else{z+=sz;iz++;}out.add(new BlockPos(x,0,z));}return out;}
 private record Target(BlockPos position,VillageMemory.House home) {}
 static final Map<ServerWorld,Map<Long,Integer>> CURSOR=new WeakHashMap<>();
 static void register(){ServerTickEvents.END_WORLD_TICK.register(w->{if(w.getPlayers().isEmpty()||w.getTime()%100!=0)return;var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
  for(var team:memory.teams){if(!WorldSettlements.loadedNearby(w,team.anchor))continue;var s=VillageEraData.get(w).settlement(team);var targets=new ArrayList<Target>();var factory=VillageFactory.entrance(w,team.village);if(factory!=null){
    String key="factory_bridge_v2:"+team.village;
    if(!projects.exists(key)){var from=s.vault==null?team.anchor:s.vault.origin;var crossing=plan(w,from,factory,s.era);if(crossing!=null&&!crossing.isEmpty()){projects.add(w,key,team.village,"bridge",crossing);return;}}
    targets.add(new Target(factory,null));
   }
   for(var job:projects.jobs)if(job.village==team.village&&VillageBuildingSites.building(job.kind)&&!job.kind.equals("cathedral")&&!job.kind.equals("house"))
       targets.add(new Target(new BlockPos((job.min.getX()+job.max.getX())/2,job.min.getY()+1,job.min.getZ()),null));
   for(var house:memory.houses)if(house.kind.equals("house")&&VillageEras.team(w,house.min)==team&&!VillageTerrain.removesHouse(w,house))
       targets.add(new Target(null,house));
   if(targets.isEmpty())continue;
   var cursor=CURSOR.computeIfAbsent(w,k->new HashMap<>());
   int start=cursor.getOrDefault(team.village,0);
   for(int probe=0;probe<Math.min(4,targets.size());probe++){
    int at=Math.floorMod(start+probe,targets.size());
    cursor.put(team.village,at+1);
    var selected=targets.get(at);
    var target=selected.home()==null?selected.position():homeApproach(selected.home());
    if(target==null)continue;
    String id="river_bridge_v3:"+team.village+":"+target.asLong();
    if(projects.exists(id))continue;
    var crossing=plan(w,team.anchor,target,s.era);
    if(crossing==null||crossing.isEmpty())continue;
    projects.add(w,id,team.village,"bridge",crossing);
    if(projects.exists(id))return;
   }
  }
 });}
 static BlockPos homeApproach(VillageMemory.House house){
  for(var record:house.blueprint)if(record.state().getBlock() instanceof DoorBlock
          &&record.state().get(DoorBlock.HALF)==net.minecraft.block.enums.DoubleBlockHalf.LOWER){
   var facing=record.state().get(DoorBlock.FACING);
   var approach=record.pos().offset(facing,4);
   return approach.getX()>=house.min.getX()&&approach.getX()<=house.max.getX()
          &&approach.getZ()>=house.min.getZ()&&approach.getZ()<=house.max.getZ()
          ?record.pos().offset(facing.getOpposite(),4):approach;
  }
  return null;
 }
 static Map<BlockPos,BlockState> plan(ServerWorld w,BlockPos from,BlockPos to,int era){
  if(VillageEras.horizontal(from,to)>384*384)return Map.of();
  var blocked=new boolean[1];
  var result=route(w,trace(from,to),from,to,era,blocked);
  if(result==null||!blocked[0])return result;
  int dx=to.getX()-from.getX(),dz=to.getZ()-from.getZ();
  for(int shift:new int[]{-12,12}){
   var middle=new BlockPos((from.getX()+to.getX())/2+(Math.abs(dz)>Math.abs(dx)?shift:0),0,
           (from.getZ()+to.getZ())/2+(Math.abs(dx)>=Math.abs(dz)?shift:0));
   var detour=trace(from,middle);var end=trace(middle,to);detour.addAll(end.subList(1,end.size()));
   blocked[0]=false;
   result=route(w,detour,from,to,era,blocked);
   if(result==null||!blocked[0]&&!result.isEmpty())return result;
  }
  return Map.of();
 }
 private static Map<BlockPos,BlockState> route(ServerWorld w,List<BlockPos> line,BlockPos from,BlockPos to,int era,boolean[] blocked){
  int size=line.size();int[] top=new int[size];boolean[] wet=new boolean[size];
  for(int i=0;i<size;i++){var p=line.get(i);if(!w.isChunkLoaded(p)){VillageProjects.keepLoaded(w,p);return null;}top[i]=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());
   int limit=Math.max(w.getBottomY()+1,top[i]-40);
   while(top[i]>limit){var b=w.getBlockState(p.withY(top[i]-1));if(b.isAir()||b.getFluidState().isEmpty()&&b.isReplaceable()||b.isIn(net.minecraft.registry.tag.BlockTags.LOGS)||b.isIn(net.minecraft.registry.tag.BlockTags.LEAVES))top[i]--;else break;}wet[i]=w.getFluidState(p.withY(top[i]-1)).isIn(FluidTags.WATER);}
  Map<BlockPos,BlockState> result=new LinkedHashMap<>();for(int a=0;a<size;a++){if(!wet[a])continue;int end=a;while(end+1<size&&wet[end+1])end++;if(a==0||end==size-1||end-a>96){blocked[0]=true;a=end;continue;}
   int deck=Math.max(top[a],Math.max(top[a-1]-1,top[end+1]-1));int ramp=6+Math.min(20,Math.max(0,deck-top[a]));int first=Math.max(0,a-ramp),last=Math.min(size-1,end+ramp);Map<BlockPos,BlockState> crossing=new LinkedHashMap<>();boolean safe=true;
   for(int i=first;i<=last&&safe;i++){var p=line.get(i);int y=deck;if(i<a)y=Math.max(top[i]-1,deck-(a-i));if(i>end)y=Math.max(top[i]-1,deck-(i-end));
    // A bank more than one block above the deck needs another route, not a cliff at the entrance.
    if(i==first&&Math.abs(y-(top[i]-1))>1||i==last&&Math.abs(y-(top[i]-1))>1){safe=false;break;}
    for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){var floor=new BlockPos(p.getX()+dx,y,p.getZ()+dz);if(!w.isChunkLoaded(floor)){VillageProjects.keepLoaded(w,floor);return null;}
     var existing=w.getBlockState(floor);if(harbor(w,floor)||existing.hasBlockEntity()||!allowed(existing)&&!bridgeOwned(w,floor,existing)){safe=false;break;}
     for(int h=1;h<=3;h++){var body=w.getBlockState(floor.up(h));if(!body.isAir()&&!body.isReplaceable()){if((VillageProjects.terrain(body)||body.isIn(net.minecraft.registry.tag.BlockTags.LOGS)||body.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)||bridgeOwned(w,floor.up(h),body))&&body.getFluidState().isEmpty())crossing.put(floor.up(h),Blocks.AIR.getDefaultState());else{safe=false;break;}}}
     if(!safe)break;crossing.put(floor,Blocks.SPRUCE_PLANKS.getDefaultState());
    }
   }
   if(!safe){blocked[0]=true;a=end;continue;}
   // Rails only on the outside of the combined deck, never through the three-block walking lane.
   var deckBlocks=crossing.entrySet().stream().filter(e->e.getValue().isOf(Blocks.SPRUCE_PLANKS)).map(Map.Entry::getKey).toList();
   var footprint=new HashSet<BlockPos>(deckBlocks);int count=0;for(var floor:deckBlocks){boolean edge=false;for(var d:Direction.Type.HORIZONTAL)if(!footprint.contains(floor.offset(d))){edge=true;break;}if(!edge)continue;
    int nearest=0;double best=Double.MAX_VALUE;for(int i=first;i<=last;i++){double d=VillageEras.horizontal(floor,line.get(i));if(d<best){best=d;nearest=i;}}
    if(nearest<a||nearest>end)continue;crossing.put(floor.up(),Blocks.SPRUCE_FENCE.getDefaultState());
    if(count++%12==0){crossing.put(floor.up(2),Blocks.SPRUCE_FENCE.getDefaultState());crossing.put(floor.up(3),Blocks.LANTERN.getDefaultState());}
   }
   // Water remains underneath; short piers add visible supports without draining the river.
   for(int i=a;i<=end;i+=8){var p=line.get(i).withY(deck);for(int side:new int[]{-2,2}){var pier=Math.abs(to.getX()-from.getX())>Math.abs(to.getZ()-from.getZ())?p.add(0,0,side):p.add(side,0,0);for(int down=1;down<=8;down++){var q=pier.down(down);var b=w.getBlockState(q);if(b.isSolidBlock(w,q))break;if(!b.isAir()&&!b.isReplaceable()&&!b.getFluidState().isIn(FluidTags.WATER))break;crossing.put(q,(era>=2?Blocks.STONE_BRICKS:Blocks.SPRUCE_LOG).getDefaultState());}}}
   result.putAll(crossing);a=end;
  }return result;
 }
 private static boolean bridgeOwned(ServerWorld world,BlockPos pos,BlockState actual){
  var projects=VillageProjects.get(world);var expected=projects.expected.get(pos);
  return expected!=null&&expected.isOf(actual.getBlock())&&projects.jobs.stream().anyMatch(p->p.kind.equals("bridge")
    &&pos.getX()>=p.min.getX()&&pos.getX()<=p.max.getX()&&pos.getZ()>=p.min.getZ()&&pos.getZ()<=p.max.getZ());
 }
 private static boolean harbor(ServerWorld w,BlockPos p){
  return VillageProjects.get(w).jobs.stream().anyMatch(job->job.kind.equals("shipyard")
      &&p.getX()>=job.min.getX()&&p.getX()<=job.max.getX()&&p.getZ()>=job.min.getZ()&&p.getZ()<=job.max.getZ());
 }
 static boolean allowed(BlockState b){return b.isIn(net.minecraft.registry.tag.BlockTags.LOGS)||b.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)||b.isOf(Blocks.SPRUCE_PLANKS)||b.getBlock() instanceof SlabBlock||b.getBlock() instanceof StairsBlock||b.isAir()||b.isReplaceable()||VillageProjects.terrain(b)||b.isOf(Blocks.SNOW);}
}
