package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.*;
/** Real ticking villager walking from platform through a side doorway to a seat. */
final class RailBoardingChecks {
 static RailVehicleEntity coach;static VillagerEntity person;static boolean done;
 static void register(){if(!Boolean.getBoolean("halloween.railBoarding"))return;
  ServerTickEvents.END_SERVER_TICK.register(server->{if(done)return;var w=server.getOverworld();int tick=server.getTicks();try{
   if(tick==40){server.getTickManager().setTickRate(200);for(int x=-1;x<=2;x++)for(int z=-1;z<=4;z++)w.setChunkForced(x,z,true);w.setTimeOfDay(6000);
    for(int x=-6;x<=6;x++)for(int z=20;z<=60;z++)for(int y=99;y<=108;y++)w.setBlockState(new BlockPos(x,y,z),y==99?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
    for(int x=-5;x<=-2;x++)for(int z=28;z<=52;z++)w.setBlockState(new BlockPos(x,101,z),Blocks.STONE_BRICKS.getDefaultState());
    coach=RailContent.VEHICLE.create(w,SpawnReason.EVENT);coach.configure(1,new RailPath(RailPath.straight(new Vec3d(.5,100,0),new Vec3d(.5,100,200))),0,40,null,0);coach.dwell=2000;w.spawnEntity(coach);coach.setDoors(true);
    person=new VillagerEntity(EntityType.VILLAGER,w);person.setPosition(-3.5,102,35);w.spawnEntity(person);
    if(!RailBoarding.invite(w,coach,person))throw new AssertionError("invitation");
   }
   if(person!=null&&person.getVehicle() instanceof RailSeatEntity){if(Math.abs(person.getZ()-40)>6)throw new AssertionError("seat must lie inside coach");done=true;org.slf4j.LoggerFactory.getLogger("rail-boarding-test").info("PASS: villager walked from platform through an open side door and sat in carriage in {} ticks",tick-40);server.stop(false);}
   if(tick%100==0&&person!=null)org.slf4j.LoggerFactory.getLogger("rail-boarding-test").info("tick={} pos={} doors={}",tick,person.getEntityPos(),coach.doorsOpen());
   if(tick>1000)throw new AssertionError("villager did not board at "+person.getEntityPos());
  }catch(Throwable e){done=true;org.slf4j.LoggerFactory.getLogger("rail-boarding-test").error("FAIL",e);server.stop(false);}});
 }
}
