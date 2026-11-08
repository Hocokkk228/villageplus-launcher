package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
/** Bounded, incremental defensive walls with openings wherever roads cross. */
final class VillagePerimeter {
 static void register(){ServerTickEvents.END_WORLD_TICK.register(VillagePerimeter::tick);}
 static void tick(ServerWorld w){
  if(w.getPlayers().isEmpty()||w.getTime()%200!=0)return;
  var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
  for(var team:List.copyOf(memory.teams)){
   var s=VillageEraData.get(w).settlement(team);if(s.era<2&&s.transitionAt<0)continue;
   if(w.getPlayers().stream().noneMatch(p->p.getBlockPos().isWithinDistance(team.anchor,300)))continue;
   int extent=48;
   for(var h:memory.houses)if(h.kind.equals("house")&&VillageEras.team(w,h.min)==team){extent=Math.max(extent,Math.max(Math.abs(h.min.getX()-team.anchor.getX()),Math.abs(h.min.getZ()-team.anchor.getZ())));extent=Math.max(extent,Math.max(Math.abs(h.max.getX()-team.anchor.getX()),Math.abs(h.max.getZ()-team.anchor.getZ())));}
   if(s.plantation!=null)extent=Math.max(extent,Math.max(Math.abs(s.plantation.getX()+12-team.anchor.getX()),Math.abs(s.plantation.getZ()+24-team.anchor.getZ()))+27);
   int radius=s.xp.getOrDefault("perimeter_radius_v1",0);
   for(var job:projects.jobs)if(job.village==team.village&&!job.done&&VillageBuildingSites.building(job.kind))
       extent=Math.max(extent,Math.max(extent(team.anchor,job.min),extent(team.anchor,job.max)));
   int desired=outerRadius(extent,radius);
   if(desired!=radius){radius=desired;s.xp.put("perimeter_radius_v1",radius);VillageEraData.get(w).markDirty();}
   dismantleInner(w,projects,memory,team,radius);
   int segments=(radius*2+15)/16;
   for(int side=0;side<4;side++)for(int part=0;part<segments;part++){
    String id="perimeter_v1:"+team.village+":"+radius+":"+side+":"+part;if(projects.exists(id))continue;
    Map<BlockPos,BlockState> plan=new LinkedHashMap<>();boolean loaded=true;
    for(int n=part*16;n<Math.min((part+1)*16,radius*2);n++){
     int x=team.anchor.getX()+(side<2?(side==0?-radius:radius):-radius+n),z=team.anchor.getZ()+(side<2?-radius+n:(side==2?-radius:radius));
     BlockPos probe=new BlockPos(x,team.anchor.getY(),z);if(!VillageProjects.keepLoaded(w,probe)){loaded=false;continue;}
     int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);BlockPos ground=new BlockPos(x,y-1,z);
     boolean gate=false;for(int d=-2;d<=2;d++){var q=side<2?ground.add(0,0,d):ground.add(d,0,0);if(w.getBlockState(q).isOf(Blocks.DIRT_PATH)||w.getBlockState(q).isOf(Blocks.STONE_BRICK_SLAB)||w.getBlockState(q).isOf(Blocks.STONE_BRICKS)){gate=true;break;}}
     if(gate)continue;
     boolean clear=true;for(int h=1;h<=5;h++){var q=ground.up(h);var state=w.getBlockState(q);if(!state.isAir()&&!state.isReplaceable()){clear=false;break;}}
     if(!clear||!w.getBlockState(ground).isSolidBlock(w,ground)||!VillageProjects.terrain(w.getBlockState(ground)))continue;
     for(int h=1;h<=3;h++)plan.put(ground.up(h),Blocks.STONE_BRICKS.getDefaultState());
     if(n%3==0)plan.put(ground.up(4),Blocks.STONE_BRICK_WALL.getDefaultState());
     if(n%16==4){plan.put(ground.up(4),Blocks.CHISELED_STONE_BRICKS.getDefaultState());plan.put(ground.up(5),Blocks.LANTERN.getDefaultState());}
    }
    if(!loaded)return;if(plan.isEmpty())continue;projects.add(w,id,team.village,"perimeter",plan);return;
   }
   repairTreeGaps(w,projects,team,radius);
  }
 }
 static int outerRadius(int extent,int current){
  if(current>0&&extent<current-8)return current;
  return Math.max(current,((extent+35+31)/32)*32);
 }
 private static int extent(BlockPos anchor,BlockPos pos){
  return Math.max(Math.abs(anchor.getX()-pos.getX()),Math.abs(anchor.getZ()-pos.getZ()));
 }
 static void dismantleInner(ServerWorld w,VillageProjects projects,VillageMemory memory,VillageMemory.Team team,int radius){
  String current="perimeter_v1:"+team.village+":"+radius+":";
  for(var old:List.copyOf(projects.jobs)){
   if(old.village!=team.village||!old.kind.equals("perimeter")||old.id.startsWith(current))continue;
   boolean bypass=old.id.startsWith("perimeter_bypass_v2:");
   if(!bypass&&Math.max(extent(team.anchor,old.min),extent(team.anchor,old.max))>=radius-1)continue;
   String id="retired_perimeter:"+old.id;
   if(projects.exists(id))continue;
   projects.retire(old);
   var changes=new LinkedHashMap<BlockPos,BlockState>();
   for(var change:old.plan){
    var pos=change.pos();
    if(extent(team.anchor,pos)>=radius-1||change.after().isAir()||!change.after().equals(projects.expected.get(pos)))continue;
    if(!w.isChunkLoaded(pos)){VillageProjects.keepLoaded(w,pos);return;}
    var actual=w.getBlockState(pos);
    if(!actual.isOf(change.after().getBlock())||actual.hasBlockEntity())continue;
    if(memory.houses.stream().anyMatch(h->VillageBuildingSites.reserves(h)&&VillageHousingRebuild.contains(h,pos)))continue;
    changes.put(pos,Blocks.AIR.getDefaultState());
   }
   if(!changes.isEmpty()){projects.add(w,id,team.village,"perimeter_removal",changes);return;}
  }
 }
 private static void repairTreeGaps(ServerWorld w,VillageProjects projects,VillageMemory.Team team,int radius){
  if(projects.jobs.stream().anyMatch(p->p.village==team.village&&p.kind.equals("perimeter")&&!p.done))return;
  for(int side=0;side<4;side++)for(int n=0;n<radius*2;n++){
   int x=team.anchor.getX()+(side<2?(side==0?-radius:radius):-radius+n),z=team.anchor.getZ()+(side<2?-radius+n:(side==2?-radius:radius));
   BlockPos probe=new BlockPos(x,team.anchor.getY(),z);if(!w.isChunkLoaded(new ChunkPos(probe).toLong()))continue;
   int top=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);BlockPos trunk=new BlockPos(x,top-1,z);
   if(!w.getBlockState(trunk).isIn(net.minecraft.registry.tag.BlockTags.LOGS))continue;
   BlockPos base=trunk;while(base.getY()>w.getBottomY()+1&&w.getBlockState(base.down()).isIn(net.minecraft.registry.tag.BlockTags.LOGS))base=base.down();
   if(!VillageProjects.terrain(w.getBlockState(base.down())))continue;
   boolean gate=false;for(int d=-2;d<=2;d++){var q=side<2?base.down().add(0,0,d):base.down().add(d,0,0);if(w.getBlockState(q).isOf(Blocks.DIRT_PATH)||w.getBlockState(q).isOf(Blocks.STONE_BRICK_SLAB)||w.getBlockState(q).isOf(Blocks.STONE_BRICKS)){gate=true;break;}}if(gate)continue;
   String id="perimeter_tree_gap_v1:"+team.village+":"+base.asLong();if(projects.exists(id))continue;
   Map<BlockPos,BlockState> plan=new LinkedHashMap<>();boolean safe=true;
   for(int h=0;h<=Math.max(4,trunk.getY()-base.getY());h++){
    var pos=base.up(h);var old=w.getBlockState(pos);
    if(!old.isAir()&&!old.isReplaceable()&&!old.isIn(net.minecraft.registry.tag.BlockTags.LOGS)&&!old.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)){safe=false;break;}
    plan.put(pos,h<3?Blocks.STONE_BRICKS.getDefaultState():h==3&&n%3==0?Blocks.STONE_BRICK_WALL.getDefaultState():Blocks.AIR.getDefaultState());
   }
   if(safe&&!plan.isEmpty()){projects.add(w,id,team.village,"perimeter",plan);return;}
  }
 }
 private VillagePerimeter(){}
}
