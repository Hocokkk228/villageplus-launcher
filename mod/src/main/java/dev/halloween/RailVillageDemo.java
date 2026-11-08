package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
/** Explicit developer world fixture: builds a reviewable line between two actual generated villages. */
final class RailVillageDemo {
 static RailwayNetwork.Station a,b;static RailwayNetwork.Line line;static int stage,section;static RailVehicleEntity engine;static int driveTicks;static boolean done;
 static void log(String s,Object...args){org.slf4j.LoggerFactory.getLogger("rail-village-demo").info(s,args);}
 static RailwayNetwork.Station station(ServerWorld w,BlockPos village,Direction facing){
  var s=new RailwayNetwork.Station(village.asLong(),village.offset(facing.rotateYCounterclockwise(),95).offset(facing,-30),facing.getHorizontalQuarterTurns(),0);
  int high=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,village.getX(),village.getZ());
  for(int x=0;x<=66;x+=6)for(int z=0;z<=65;z+=6){var p=s.pos(x,0,z);w.getChunk(p);high=Math.max(high,w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ()));}
  return new RailwayNetwork.Station(village.asLong(),s.origin.withY(high),s.facing,0);
 }
 static void placeBuilding(ServerWorld w,RailwayNetwork.Station station,String name){
  for(var entry:RailwayNetwork.building(w,station,name).entrySet()){w.getChunk(entry.getKey());w.setBlockState(entry.getKey(),VillageConnections.connected(w,entry.getKey(),entry.getValue()),Block.NOTIFY_LISTENERS);}
  var template=VillageEraPlans.template(w,name);
  for(int x=0;x<template.size().getX();x++)for(int z=0;z<template.size().getZ();z++){
   var floor=station.pos(x,0,z);boolean pillar=x%8==0&&z%8==0||x==0||z==0||x==template.size().getX()-1||z==template.size().getZ()-1;
   if(!pillar)continue;for(int d=1;d<=100;d++){var p=floor.down(d);if(w.getBlockState(p).isSolidBlock(w,p))break;w.setBlockState(p,Blocks.STONE_BRICKS.getDefaultState(),Block.NOTIFY_LISTENERS);}
  }
 }
 static void approach(ServerWorld w,BlockPos village,RailwayNetwork.Station s){
  var begin=new Vec3d(village.getX()+.5,w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,village.getX(),village.getZ())-1,village.getZ()+.5);var end=Vec3d.of(s.pos(32,1,-1));double length=begin.distanceTo(end);
  for(int i=0;i<=length;i++){var p=BlockPos.ofFloored(begin.lerp(end,i/length));w.getChunk(p);for(int width=-1;width<=1;width++){
   var q=p.offset(s.side(),width);w.setBlockState(q,Blocks.STONE_BRICKS.getDefaultState(),Block.NOTIFY_LISTENERS);for(int y=1;y<=3;y++)w.setBlockState(q.up(y),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
   if(i%8==0)for(int d=1;d<80;d++){if(w.getBlockState(q.down(d)).isSolidBlock(w,q.down(d)))break;w.setBlockState(q.down(d),Blocks.STONE_BRICKS.getDefaultState(),Block.NOTIFY_LISTENERS);}
  }}
 }
 static void segment(ServerWorld w,double from,double to){
  for(double d=from;d<to;d+=.8)for(double lane:new double[]{-4,4})for(double side:new double[]{-1.5,-.5,.5,1.5}){
   var p=BlockPos.ofFloored(line.path.at(d,lane+side));w.getChunk(p);
   if(RailwayNetwork.insideStation(RailwayNetwork.get(w),p))continue;
   w.setBlockState(p.down(),Blocks.STONE_BRICKS.getDefaultState(),Block.NOTIFY_LISTENERS);
   for(int h=0;h<7;h++)w.setBlockState(p.up(h),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
   if(((int)d)%16==0&&side==-.5)for(int depth=2;depth<=100;depth++){var q=p.down(depth);if(w.getBlockState(q).isSolidBlock(w,q))break;w.setBlockState(q,Blocks.STONE_BRICKS.getDefaultState(),Block.NOTIFY_LISTENERS);}
  }
  for(double d=from;d<to;d+=4)for(double lane:new double[]{-4,4}){var p=BlockPos.ofFloored(line.path.at(d,lane));w.setBlockState(p.down(),Blocks.STONE_BRICKS.getDefaultState(),Block.NOTIFY_LISTENERS);w.setBlockState(p,RailContent.TRACK.getDefaultState(),Block.NOTIFY_LISTENERS);if(w.getBlockEntity(p) instanceof RailTrackEntity track){var points=new ArrayList<Vec3d>();for(int j=0;j<=8;j++)points.add(line.path.at(Math.min(line.path.length,d+j*.5),lane).subtract(Vec3d.of(p)));track.setPoints(points);}}
 }
 static void register(){if(!Boolean.getBoolean("halloween.railVillageDemo"))return;
  ServerTickEvents.END_SERVER_TICK.register(server->{if(done||server.getTicks()<40)return;var w=server.getOverworld();try{
   if(stage==0){var first=w.locateStructure(StructureTags.VILLAGE,new BlockPos(0,70,0),32,false);if(first==null)throw new AssertionError("first village missing");BlockPos second=null;
    for(var offset:List.of(new BlockPos(768,0,0),new BlockPos(0,0,768),new BlockPos(-768,0,0),new BlockPos(0,0,-768))){var candidate=w.locateStructure(StructureTags.VILLAGE,first.add(offset),32,false);if(candidate!=null&&VillageEras.horizontal(first,candidate)>400*400){second=candidate;break;}}
    if(second==null)throw new AssertionError("second village missing");var facing=Direction.getFacing(second.getX()-first.getX(),0,second.getZ()-first.getZ());a=station(w,first,facing);b=station(w,second,facing.getOpposite());
    var state=RailwayNetwork.get(w);state.stations.add(a);state.stations.add(b);placeBuilding(w,a,"railway_station");placeBuilding(w,b,"railway_station");placeBuilding(w,RailwayNetwork.foundry(a),"rail_foundry");approach(w,first,a);approach(w,second,b);line=RailwayNetwork.connect(w,a,b);
    log("VILLAGES {} and {}; STATIONS {} / {}; length={}",first,second,a.origin,b.origin,line.path.length);w.setTimeOfDay(6000);stage=1;
   }else if(stage==1){int total=(int)Math.ceil(line.path.length/16);segment(w,section*16,Math.min(line.path.length,(section+1)*16));section++;if(section%16==0)log("TRACK {}/{}",section,total);
    if(section>=total){line.next=total;line.spawned=true;RailwayNetwork.get(w).markDirty();engine=RailwayNetwork.spawnTrain(w,line.path,-4,40,1).getFirst();RailwayNetwork.spawnTrain(w,line.path,4,line.path.length-8,-1);
     for(int i=0;i<3;i++){var local=new Vec3d(5,3,15+i*3);var p=a.pos((int)local.x,(int)local.y,(int)local.z);var villager=net.minecraft.entity.EntityType.VILLAGER.create(w,SpawnReason.EVENT);if(villager!=null){villager.setPosition(Vec3d.ofBottomCenter(p));w.spawnEntity(villager);}}
     stage=2;server.getTickManager().setTickRate(1000);log("READY: two villages, two stations, two tracks, two trains");
    }
   }else if(stage==2){for(var train:w.getEntitiesByClass(RailVehicleEntity.class,new Box(-100000,-64,-100000,100000,320,100000),e->true)){var cp=new ChunkPos(train.getBlockPos());for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.setChunkForced(cp.x+x,cp.z+z,true);}
    driveTicks++;if(engine.direction==-1){log("PASS: locomotive with driver travelled from first station to second after {} ticks; distance={}",driveTicks,engine.distance);engine.dwell=240;done=true;server.stop(false);}else if(driveTicks>20000)throw new AssertionError("train did not reach destination");
   }
  }catch(Throwable e){done=true;org.slf4j.LoggerFactory.getLogger("rail-village-demo").error("FAIL",e);server.stop(false);}});
 }
}
