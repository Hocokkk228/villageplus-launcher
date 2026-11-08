package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.*;
/** Actual navigation, not just a count of staircase blocks in a template. */
final class RailAccessChecks {
 static RailwayNetwork.Station station;static VillagerEntity walker,commuter;static int stage,age;static boolean done;
 static void register(){if(!Boolean.getBoolean("halloween.railAccessTest"))return;ServerTickEvents.END_SERVER_TICK.register(server->{if(done||server.getTicks()<40)return;var w=server.getOverworld();try{
  if(station==null){server.getTickManager().setTickRate(1000);w.setTimeOfDay(6000);for(int x=-2;x<=5;x++)for(int z=-3;z<=7;z++)w.setChunkForced(x,z,true);station=new RailwayNetwork.Station(471,new BlockPos(0,104,0),Direction.SOUTH.getHorizontalQuarterTurns(),0);for(int x=-30;x<=80;x++)for(int z=-35;z<=90;z++){w.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);for(int y=100;y<132;y++)if(!w.getBlockState(new BlockPos(x,y,z)).isAir())w.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);}
   for(var e:RailwayNetwork.building(w,station,"railway_station").entrySet())w.setBlockState(e.getKey(),e.getValue(),Block.NOTIFY_LISTENERS);walker=EntityType.VILLAGER.create(w,SpawnReason.EVENT);if(walker==null)throw new AssertionError("walker");walker.setPosition(Vec3d.ofBottomCenter(station.pos(32,0,-12)));w.spawnEntity(walker);
  }
  RailStationSafety.repair(w,station);
  if(age==0&&stage==0){StationResidents.ensure(w,station);StationResidents.ensure(w,station);if(station.residents.size()!=2)throw new AssertionError("station must own exactly two persistent floor residents");}

  if(stage==5){age++;if(w.getTime()%20==0)RailStationSafety.commuters(w,station);var target=station.pos(12,3,12);
   if(commuter.squaredDistanceTo(Vec3d.ofBottomCenter(target))<4){if(!commuter.getCommandTags().contains("halloween_commute_"+station.village))throw new AssertionError("commuter was not recruited");done=true;org.slf4j.LoggerFactory.getLogger("rail-access-test").info("PASS: ordinary resident recruited 300 blocks away walked into station and reached platform in {} ticks",age);server.stop(false);}
   if(age>5000)throw new AssertionError("distant commuter stuck at "+commuter.getEntityPos()+" tags="+commuter.getCommandTags());return;
  }
  BlockPos[] goals={station.pos(32,3,10),station.pos(20,3,47),station.pos(4,3,47),station.pos(4,3,20),station.pos(4,-4,-10)};var goal=goals[stage];VillageTasks.hold(walker);age++;
  if(age%10==1||walker.getNavigation().isIdle())walker.getNavigation().startMovingTo(goal.getX()+.5,goal.getY(),goal.getZ()+.5,.8);
  if(walker.squaredDistanceTo(Vec3d.ofBottomCenter(goal))<2){org.slf4j.LoggerFactory.getLogger("rail-access-test").info("PASS station navigation stage {} reached {} after {} ticks",stage,goal,age);stage++;age=0;if(stage==3){walker.addCommandTag("halloween_guard");walker.setVillagerData(walker.getVillagerData().withProfession(GuardProfession.ENTRY));}if(stage==goals.length){org.slf4j.LoggerFactory.getLogger("rail-access-test").info("PASS: raised station, entrance landings, bridge and return to ground");
    for(int z=-320;z<-35;z++){w.setChunkForced(1,z>>4,true);w.setChunkForced(2,z>>4,true);for(int x=30;x<=34;x++)w.setBlockState(new BlockPos(x,99,z),Blocks.GRASS_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS);}
    commuter=EntityType.VILLAGER.create(w,SpawnReason.EVENT);if(commuter==null)throw new AssertionError("commuter creation");java.util.UUID id;do{id=java.util.UUID.randomUUID();}while(Math.floorMod(id.hashCode()+(int)(w.getTime()/24000),5)!=0);commuter.setUuid(id);commuter.setPosition(32.5,100,-300);w.spawnEntity(commuter);age=0;}}
  if(age>1500)throw new AssertionError("station navigation stuck at stage "+stage+" actual="+walker.getEntityPos()+" goal="+goal+" idle="+walker.getNavigation().isIdle());
 }catch(Throwable e){done=true;org.slf4j.LoggerFactory.getLogger("rail-access-test").error("FAIL",e);server.stop(false);}});}
}
