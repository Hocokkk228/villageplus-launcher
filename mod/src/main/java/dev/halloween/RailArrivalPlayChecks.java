package dev.halloween;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.math.*;
/** Arriving from beyond entity tracking distance must render without respawning the player. */
final class RailArrivalPlayChecks {
 static volatile int engine=-1;static int age;static boolean initiallyAbsent,done;
 static void register(){
  ServerTickEvents.END_SERVER_TICK.register(server->{var w=server.getOverworld();if(engine!=-1||w.getPlayers().isEmpty())return;var p=w.getPlayers().getFirst();
   for(var old:RailVehicleEntity.nearby(w,new Box(-100,100,-1000,100,250,1000)))old.discard();
   for(int x=4;x<=10;x++)for(int z=-8;z<=8;z++)w.setBlockState(new BlockPos(x,160,z),net.minecraft.block.Blocks.STONE.getDefaultState());
   p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);p.setInvulnerable(false);p.setHealth(20);p.networkHandler.requestTeleport(6.5,161,0,180,0);
   for(int z=-28;z<=8;z++)for(int x=-1;x<=1;x++)w.setChunkForced(x,z,true);
   var path=new RailPath(RailPath.straight(new Vec3d(0,160,-400),new Vec3d(0,160,1000)));var root=RailwayNetwork.spawnTrain(w,path,0,40,1).getFirst();root.speed=.72;root.dwell=0;engine=root.getId();
  });
  ClientTickEvents.END_CLIENT_TICK.register(c->{if(done||engine<0||c.world==null||c.player==null)return;age++;c.options.pauseOnLostFocus=false;if(c.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen)c.setScreen(null);c.player.setYaw(180);c.player.setPitch(0);
   try{var e=c.world.getEntityById(engine);if(age<20&&e==null)initiallyAbsent=true;
    if(!c.player.isAlive())throw new AssertionError("station observer died");
    if(e!=null&&e.squaredDistanceTo(c.player)<10000&&RailRenderers.observedVehicles.contains(engine)){
     if(!initiallyAbsent)throw new AssertionError("train must start outside client tracking");
     org.slf4j.LoggerFactory.getLogger("rail-arrival-test").info("PASS: incoming train tracked and rendered without respawn after {} client ticks",age);done=true;c.scheduleStop();
    }else if(age>1200)throw new AssertionError("incoming train never rendered; tracked="+(e!=null));
   }catch(Throwable e){done=true;org.slf4j.LoggerFactory.getLogger("rail-arrival-test").error("FAIL",e);c.scheduleStop();}
  });
 }
}
