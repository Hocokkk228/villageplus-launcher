package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.SpawnReason;
import net.minecraft.util.math.*;

/** Measures the camera and rendered rider every frame, along a straight and then a curve. */
final class RailSeatSmoothChecks {
 static volatile boolean prepared;static int ticks,frames,teleports,corrections;static double maxCameraError,maxBodyError;static RailSeatEntity seat;
 static void require(boolean v,String why){if(!v)throw new AssertionError(why);}
 static void register(){
  ServerTickEvents.END_SERVER_TICK.register(server->{var w=server.getOverworld();if(prepared||w.getPlayers().isEmpty())return;var p=w.getPlayers().getFirst();p.stopRiding();
   for(var old:RailVehicleEntity.nearby(w,new Box(-100,100,-100,600,250,700)))old.discard();
   var points=new ArrayList<>(RailPath.straight(new Vec3d(0,160,0),new Vec3d(0,160,320)));points.addAll(List.of(new Vec3d(0,160,320),new Vec3d(0,160,386),new Vec3d(54,160,440),new Vec3d(120,160,440)));points.addAll(RailPath.straight(new Vec3d(120,160,440),new Vec3d(500,160,440)));
   var train=RailwayNetwork.spawnTrain(w,new RailPath(points),0,80,1);train.getFirst().dwell=0;train.getFirst().speed=.72;var coach=train.get(1);var cp=new ChunkPos(coach.getBlockPos());for(int x=-1;x<=1;x++)for(int z=-1;z<=2;z++)w.setChunkForced(cp.x+x,cp.z+z,true);
   p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);p.setHealth(20);p.getAbilities().flying=false;p.sendAbilitiesUpdate();p.networkHandler.requestTeleport(coach.getX(),coach.getY()+1.5,coach.getZ(),0,0);
   var chair=RailContent.SEAT.create(w,SpawnReason.EVENT);require(chair!=null,"chair created");chair.owner=coach.getUuid();chair.local=new Vec3d(-.9,2.05,2.2);chair.setPosition(coach.getEntityPos().add(chair.local));w.spawnEntity(chair);p.startRiding(chair,true,false);chair.syncToTrain();prepared=true;
  });
  ClientTickEvents.END_CLIENT_TICK.register(c->{if(!prepared||ticks<0||c.player==null)return;c.options.pauseOnLostFocus=false;c.options.getMaxFps().setValue(40);c.options.getEnableVsync().setValue(false);c.mouse.unlockCursor();if(c.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen)c.setScreen(null);
   try{if(!(c.player.getVehicle() instanceof RailSeatEntity current)||current.train()==null){if(++ticks>100)throw new AssertionError("seat did not synchronize");return;}seat=current;
    require(c.player.isAlive()&&c.player.getHealth()>=19.9,"seated passenger remains unharmed");ticks++;
    if(ticks==60){teleports=RailPassengerClient.teleports;corrections=RailPassengerClient.corrections;}
    if(ticks==120)c.options.setPerspective(Perspective.THIRD_PERSON_BACK);if(ticks==240)c.options.setPerspective(Perspective.FIRST_PERSON);
    var local=RailPassengerNetwork.toLocal(seat.train(),c.player.getEntityPos());require(local.distanceTo(seat.local.add(0,-.90,0))<.015,"actual passenger position jumped away from seat: "+local);
    if(ticks>650){require(frames>500,"enough actual rendered frames at 40 FPS");require(RailPassengerClient.teleports==teleports&&RailPassengerClient.corrections==corrections,"no correction teleports while seated");org.slf4j.LoggerFactory.getLogger("rail-seat-smooth-test").info("PASS: straight and curved full-speed ride, first/third person, {} frames, camera error={}, body error={}, no corrections",frames,maxCameraError,maxBodyError);ticks=-1;c.scheduleStop();}
   }catch(Throwable e){fail(c,e);}
  });
  WorldRenderEvents.END_MAIN.register(context->{var c=MinecraftClient.getInstance();if(ticks<70||seat==null||seat.train()==null||c.player==null)return;
   try{float delta=c.getRenderTickCounter().getTickProgress(false);var expected=seat.renderedPassengerPos(c.player,delta);var state=c.getEntityRenderDispatcher().getRenderer(c.player).getAndUpdateRenderState(c.player,delta);double bodyError=new Vec3d(state.x,state.y,state.z).distanceTo(expected);maxBodyError=Math.max(maxBodyError,bodyError);require(bodyError<.002,"third-person rider drifts from carriage: "+bodyError);
    if(c.options.getPerspective().isFirstPerson()){double cameraError=c.gameRenderer.getCamera().getCameraPos().distanceTo(expected.add(0,c.player.getStandingEyeHeight(),0));maxCameraError=Math.max(maxCameraError,cameraError);require(cameraError<.015,"first-person camera drifts from carriage: "+cameraError);}frames++;
   }catch(Throwable e){fail(c,e);}
  });
 }
 private static void fail(MinecraftClient c,Throwable e){ticks=-1;org.slf4j.LoggerFactory.getLogger("rail-seat-smooth-test").error("FAIL",e);c.scheduleStop();}
}
