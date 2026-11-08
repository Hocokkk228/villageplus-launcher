package dev.halloween;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
/** Two independent clients against a dedicated server, not an integrated-server substitute. */
final class NavalMultiChecks {
 static RailVehicleEntity ship;static int age,wait;static boolean prepared;
 static void register(){if(!Boolean.getBoolean("halloween.navalMultiServerTest"))return;
  ServerTickEvents.END_SERVER_TICK.register(server->{var w=server.getOverworld();try{
   if(!prepared){if(w.getPlayers().size()<2){if(++wait>6000)throw new AssertionError("two clients did not connect");return;}var start=new Vec3d(-1048.5,63,1088.5);var cp=new ChunkPos(BlockPos.ofFloored(start));for(int x=-4;x<=4;x++)for(int z=-4;z<=8;z++)w.setChunkForced(cp.x+x,cp.z+z,true);if(++age<80)return;
    for(var old:RailVehicleEntity.nearby(w,new Box(start,start).expand(180)))old.discard();ship=NavalContent.spawn(w,2,start,0);if(ship==null)throw new AssertionError("test ship spawn");ship.setCustomName(Text.literal("Fleet Multiplayer Test"));int side=-1;for(var p:w.getPlayers()){p.stopRiding();p.changeGameMode(GameMode.SURVIVAL);p.setHealth(20);p.getAbilities().flying=false;p.sendAbilitiesUpdate();p.networkHandler.requestTeleport(start.x+side*.85,start.y+3,start.z+5,0,0);side=1;}prepared=true;age=0;log("PREPARED two actual network clients");return;
   }
   age++;if(age>120){ship.navalFlags(false,true,false);ship.naval().throttle=1;ship.naval().rudder=.25f;ship.naval().lastControl=w.getTime();}
   if(age>120&&age<450)for(var p:w.getPlayers())if(!p.isAlive()||p.getHealth()<19)throw new AssertionError("passenger harmed");
   if(age>500&&w.getPlayers().isEmpty()){log("PASS dedicated voyage completed; clients disconnected after their own movement assertions");server.stop(false);}
   if(age>1600)throw new AssertionError("client voyage timeout");
  }catch(Throwable error){org.slf4j.LoggerFactory.getLogger("naval-multi-test").error("FAIL",error);server.stop(false);}});
 }
 static void log(String s){org.slf4j.LoggerFactory.getLogger("naval-multi-test").info(s);}
}
