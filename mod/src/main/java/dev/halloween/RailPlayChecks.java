package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
/** Opt-in real client exercise; never enabled in normal play. */
final class RailPlayChecks {
 static volatile int ticks;static boolean done;static RailVehicleEntity coach;static BlockPos base;static double relativeStart,maxJump,cruiseDistance;static boolean seated;static String failure;static int correctionStart,framesStart;
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void screenshot(String name){MinecraftClient.getInstance().execute(()->{var c=MinecraftClient.getInstance();ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),1,t->{});});}
 static void camera(ServerPlayerEntity p,Vec3d position,Vec3d target){p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);var d=target.subtract(position);float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));float pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));p.setYaw(yaw);p.setPitch(pitch);p.networkHandler.requestTeleport(position.x,position.y,position.z,yaw,pitch);MinecraftClient.getInstance().execute(()->{var c=MinecraftClient.getInstance();c.options.setPerspective(Perspective.FIRST_PERSON);if(c.player!=null){c.player.setYaw(yaw);c.player.setPitch(pitch);}});}
 static void register(){if(!Boolean.getBoolean("halloween.railPlaytest"))return;

  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->{c.options.pauseOnLostFocus=false;if(c.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen)c.setScreen(null);});
  ServerTickEvents.END_SERVER_TICK.register(server->{if(done||server.getOverworld().getPlayers().isEmpty())return;var w=server.getOverworld();var p=w.getPlayers().getFirst();ticks++;
   try{
    if(ticks==20){for(var old:w.getEntitiesByClass(RailVehicleEntity.class,new Box(-100,100,-100,300,250,400),e->true))old.discard();for(var old:w.getEntitiesByClass(RailSeatEntity.class,new Box(-100,100,-100,300,250,400),e->true))old.discard();base=new BlockPos(0,155,0);p.setInvulnerable(true);w.setTimeOfDay(6000);for(int x=-1;x<=5;x++)for(int z=-2;z<=16;z++)w.setChunkForced(x,z,true);RailwayNetwork.showcase(w,base);coach=w.getEntitiesByClass(RailVehicleEntity.class,new Box(base).expand(300),e->e.kind()==1&&e.getX()<12).stream().min(Comparator.comparingDouble(e->Math.abs(e.distance-25))).orElseThrow();camera(p,Vec3d.of(base).add(-31,25,-16),Vec3d.of(base).add(20,7,29));}
    if(ticks==60)camera(p,Vec3d.of(base).add(-31,25,-16),Vec3d.of(base).add(20,7,29));
    if(ticks==65)screenshot("railway-station-0.46.0.png");
    if(ticks==80){p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);p.setVelocity(Vec3d.ZERO);p.requestTeleport(coach.getX(),coach.getY()+1.5,coach.getZ());MinecraftClient.getInstance().execute(()->{var c=MinecraftClient.getInstance();if(c.player!=null){c.player.setYaw(0);c.player.setPitch(0);}});}
    if(ticks>=80&&ticks%20==0)org.slf4j.LoggerFactory.getLogger("rail-play").info("PLAYER {} COACH {}",p.getEntityPos(),coach.getEntityPos());
    if(ticks==120){require(Math.abs(p.getY()-coach.getY()-1.5)<.25,"client player stands on carriage floor: player="+p.getEntityPos()+" coach="+coach.getEntityPos()+" ground="+p.isOnGround());relativeStart=p.getZ()-coach.getZ();MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().options.forwardKey.setPressed(true));}
    if(ticks==132)MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().options.jumpKey.setPressed(true));
    if(ticks==152)MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().options.jumpKey.setPressed(false));
    if(ticks>=120&&ticks<=160)maxJump=Math.max(maxJump,p.getY()-coach.getY()-1.5);
    if(ticks==160){MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().options.forwardKey.setPressed(false));require(p.getZ()-coach.getZ()-relativeStart>.7,"client walks relative to moving train: player="+p.getEntityPos()+" coach="+coach.getEntityPos()+" relativeStart="+relativeStart);require(maxJump>.5,"client jumps inside moving carriage");p.setVelocity(Vec3d.ZERO);p.requestTeleport(coach.getX(),coach.getY()+1.5,coach.getZ());}
    if(ticks==180)MinecraftClient.getInstance().execute(()->{var c=MinecraftClient.getInstance();if(c.player!=null&&c.world!=null&&c.interactionManager!=null){var vehicle=c.world.getEntityById(coach.getId());if(vehicle!=null)c.interactionManager.interactEntity(c.player,vehicle,Hand.MAIN_HAND);}});
    if(ticks==210){require(p.getVehicle() instanceof RailSeatEntity,"real client right click mounts a carriage seat");seated=true;screenshot("railway-coach-interior-0.46.0.png");}
    if(ticks==240){require(seated&&p.getVehicle() instanceof RailSeatEntity,"seat follows moving carriage without disconnect");p.stopRiding();camera(p,Vec3d.of(base).add(-19,13,47),Vec3d.of(base).add(9,4,32));}
    if(ticks==270)screenshot("steam-train-0.46.0.png");
    if(ticks==290){
     p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);p.getAbilities().flying=false;p.sendAbilitiesUpdate();p.setVelocity(Vec3d.ZERO);p.networkHandler.requestTeleport(coach.getX(),coach.getY()+1.5,coach.getZ(),0,0);relativeStart=0;
    }
    if(ticks==320){relativeStart=p.getZ()-coach.getZ();MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().options.backKey.setPressed(true));}
    if(ticks==335)MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().options.backKey.setPressed(false));
    if(ticks==350){require(p.getZ()-coach.getZ()<relativeStart-1,"backward walking works at cruising speed");require(Math.abs(p.getY()-coach.getY()-1.5)<.3,"walking backward does not fall through floor");relativeStart=p.getZ()-coach.getZ();}
    if(ticks==410){require(Math.abs((p.getZ()-coach.getZ())-relativeStart)<.8,"standing passenger keeps position for sixty cruise ticks");MinecraftClient.getInstance().execute(()->{var c=MinecraftClient.getInstance();c.options.jumpKey.setPressed(true);c.options.forwardKey.setPressed(true);});}
    if(ticks==420)MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().options.jumpKey.setPressed(false));
    if(ticks==430)MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().options.forwardKey.setPressed(false));
    if(ticks==450){require(Math.abs(p.getY()-coach.getY()-1.5)<.3,"cruising jump lands on solid floor");MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().options.rightKey.setPressed(true));}
    if(ticks==480){MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().options.rightKey.setPressed(false));var local=RailGeometry.rotate(p.getEntityPos().subtract(coach.getEntityPos()),-coach.getYaw());require(Math.abs(local.x)<1.5,"real client cannot walk through moving carriage wall");require(local.y>1.4,"side wall collision does not drop passenger");}
    if(ticks==500){coach.configure(1,new RailPath(RailPath.straight(new Vec3d(80,156,0),new Vec3d(80,156,1000))),0,90,null,0);coach.dwell=0;coach.speed=.72;p.setVelocity(Vec3d.ZERO);p.networkHandler.requestTeleport(coach.getX(),coach.getY()+1.5,coach.getZ(),0,0);}
    if(ticks==520){cruiseDistance=coach.distance;relativeStart=p.getZ()-coach.getZ();correctionStart=RailPassengerClient.corrections;framesStart=RailPassengerClient.sent;}
    if(ticks>520&&ticks<720)require(Math.abs(p.getY()-coach.getY()-1.5)<.3,"long ride keeps player on carriage floor");
    if(ticks==720){require(coach.distance-cruiseDistance>120,"zero-correction interval must cover sustained full-speed motion, not station dwell");org.slf4j.LoggerFactory.getLogger("rail-play").info("FRAME total={} segment={} corrections={}",RailPassengerClient.sent,RailPassengerClient.sent-framesStart,RailPassengerClient.corrections-correctionStart);require(RailPassengerClient.sent-framesStart>=40,"walking uses carriage-coordinate movement stream: sent="+(RailPassengerClient.sent-framesStart));require(RailPassengerClient.corrections==correctionStart,"no correction teleports during 200 cruise ticks: corrections="+(RailPassengerClient.corrections-correctionStart)+" sent="+(RailPassengerClient.sent-framesStart));require(Math.abs(p.getZ()-coach.getZ()-relativeStart)<.35,"player speed equals carriage speed while standing");done=true;org.slf4j.LoggerFactory.getLogger("rail-play").info("PASS: real client forward/backward walking, jumping, solid wall, seat, 200 cruise ticks with zero correction teleports and matching passenger speed");MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().scheduleStop());}
   }catch(Throwable e){done=true;failure=e.toString();org.slf4j.LoggerFactory.getLogger("rail-play").error("FAIL: real client railway test tick="+ticks,e);MinecraftClient.getInstance().execute(()->{var c=MinecraftClient.getInstance();c.options.forwardKey.setPressed(false);c.options.backKey.setPressed(false);c.options.rightKey.setPressed(false);c.options.jumpKey.setPressed(false);c.scheduleStop();});}
  });
 }
}
