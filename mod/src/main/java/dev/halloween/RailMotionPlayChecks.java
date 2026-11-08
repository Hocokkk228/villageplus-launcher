package dev.halloween;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.*;
/** Input assertions run on actual client ticks, so render/network lag cannot shorten a key hold. */
final class RailMotionPlayChecks {
 static volatile boolean prepared,startEngine;static RailVehicleEntity engine;static volatile int coachId;static int phase,age,waiting;static Vec3d baseline;static double high;static int corrections,frames,teleports;static RailVehicleEntity train;
 static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 static void controls(MinecraftClient c){c.options.forwardKey.setPressed(false);c.options.backKey.setPressed(false);c.options.jumpKey.setPressed(false);}
 static void register(){if(!Boolean.getBoolean("halloween.railMotionTest"))return;org.slf4j.LoggerFactory.getLogger("rail-motion-test").info("REGISTER");
  ServerTickEvents.END_SERVER_TICK.register(server->{var w=server.getOverworld();if(prepared){if(startEngine){engine.dwell=0;engine.speed=.72;startEngine=false;}return;}if(w.getPlayers().isEmpty())return;var p=w.getPlayers().getFirst();p.stopRiding();
   for(var old:RailVehicleEntity.nearby(w,new Box(-100,100,-100,100,250,400)))old.discard();
   var path=new RailPath(RailPath.straight(new Vec3d(0,160,0),new Vec3d(0,160,2000)));var vehicles=RailwayNetwork.spawnTrain(w,path,0,80,1);engine=vehicles.getFirst();engine.speed=0;engine.dwell=Integer.MAX_VALUE;
   var coach=vehicles.get(1);coachId=coach.getId();var cp=new ChunkPos(coach.getBlockPos());for(int x=-1;x<=1;x++)for(int z=-1;z<=20;z++)w.setChunkForced(cp.x+x,z,true);
   p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);p.setInvulnerable(false);p.setHealth(20);p.getInventory().setStack(8,new net.minecraft.item.ItemStack(VillageItems.WORLD_STATISTICS));p.currentScreenHandler.sendContentUpdates();p.getAbilities().flying=false;p.sendAbilitiesUpdate();p.networkHandler.requestTeleport(coach.getX(),coach.getY()+1.5,coach.getZ(),0,0);prepared=true;org.slf4j.LoggerFactory.getLogger("rail-motion-test").info("PREPARED player={} coach={}",p.getEntityPos(),coach.getId());
  });
  ClientTickEvents.START_CLIENT_TICK.register(c->{if(phase<0||!prepared||c.player==null)return;controls(c);c.options.useKey.setPressed(false);c.options.attackKey.setPressed(false);while(c.options.useKey.wasPressed()){}while(c.options.attackKey.wasPressed()){}c.options.leftKey.setPressed(false);c.options.rightKey.setPressed(false);c.options.sneakKey.setPressed(false);c.options.sprintKey.setPressed(false);c.options.forwardKey.setPressed(phase==2);c.options.backKey.setPressed(phase==4);c.options.jumpKey.setPressed(phase==6&&age<8);c.player.setYaw(phase<8||train==null?0:train.getYaw()+180);});
  ClientTickEvents.END_CLIENT_TICK.register(c->{if(!prepared||phase<0||c.player==null||c.world==null)return;c.options.pauseOnLostFocus=false;if(c.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen)c.setScreen(null);
   try{
    if(train==null){train=c.world.getEntityById(coachId) instanceof RailVehicleEntity t?t:null;if(train==null){if(++waiting>200)throw new AssertionError("test passenger did not reach carriage after setup teleport");return;}}
    require(c.player.isAlive()&&c.player.getHealth()>=19.9f,"survival passenger must not be hurt or killed by train");
    var local=RailPassengerNetwork.toLocal(train,c.player.getEntityPos());age++;
    if(phase==0){if(age<40)return;require(RailPassengerClient.aboard(c.player)&&RailPassengerNetwork.inside(train,local),"client joined the intended coach before departure");startEngine=true;controls(c);baseline=local;corrections=RailPassengerClient.corrections;teleports=RailPassengerClient.teleports;frames=RailPassengerClient.sent;phase++;age=0;}
    else if(phase==1){require(local.squaredDistanceTo(baseline)<.04,"standing player must inherit train speed without slipping: baseline="+baseline+" actual="+local+" corrections="+(RailPassengerClient.corrections-corrections)+" velocity="+c.player.getVelocity()+" testTrain="+train.getId());if(age>=120){c.options.forwardKey.setPressed(true);phase++;age=0;baseline=local;}}
    else if(phase==2&&age>=14){controls(c);require(local.z-baseline.z>1,"forward walking adds personal speed to train speed");phase++;age=0;}
    else if(phase==3&&age>=20){baseline=local;c.options.backKey.setPressed(true);phase++;age=0;}
    else if(phase==4&&age>=22){controls(c);require(local.z<baseline.z-2,"backward walking is free at full speed");phase++;age=0;}
    else if(phase==5&&age>=20){c.options.jumpKey.setPressed(true);high=local.y;phase++;age=0;}
    else if(phase==6){high=Math.max(high,local.y);if(age==8)c.options.jumpKey.setPressed(false);if(age>=35){require(high>2.1,"jump rises above carriage floor");require(Math.abs(local.y-1.5)<.08,"jump lands on same moving floor");phase++;age=0;baseline=local;}}
    else if(phase==7){require(local.squaredDistanceTo(baseline)<.04,"passenger stays with train after jumping: baseline="+baseline+" actual="+local+" corrections="+(RailPassengerClient.corrections-corrections));if(age>=120){require(RailPassengerClient.corrections==corrections,"zero correction teleports during full-speed standing, walking and jumping: "+(RailPassengerClient.corrections-corrections));require(RailPassengerClient.teleports==teleports,"no vanilla world-position rollback teleports while walking aboard");require(RailPassengerClient.sent-frames>250,"relative movement packets actually used");org.slf4j.LoggerFactory.getLogger("rail-motion-test").info("PASS: full-speed standing, forward/backward walking and jumping; zero correction teleports in {} client packets",RailPassengerClient.sent-frames);org.slf4j.LoggerFactory.getLogger("rail-seat-test").info("client action={}",c.interactionManager.interactEntity(c.player,train,net.minecraft.util.Hand.MAIN_HAND));phase=8;age=0;}}
    else if(phase==8){if(c.player.getVehicle() instanceof RailSeatEntity seat){var actual=RailPassengerNetwork.toLocal(train,c.player.getEntityPos());require(Math.abs(actual.y-1.15)<.08,"seated passenger pelvis rests on cushion");if(age>20)require(Math.abs(MathHelper.wrapDegrees(c.player.bodyYaw-train.getYaw()-180))<20,"passenger faces next chair, not seat back");if(age>60){org.slf4j.LoggerFactory.getLogger("rail-motion-test").info("PASS: seated passenger follows interpolated carriage without offset, faces chair front, no damage");c.player.getInventory().setSelectedSlot(8);org.slf4j.LoggerFactory.getLogger("rail-motion-test").info("almanac client stack={} action={}",c.player.getMainHandStack(),c.interactionManager.interactItem(c.player,net.minecraft.util.Hand.MAIN_HAND));phase=9;age=0;}}else if(age>100)throw new AssertionError("passenger could not sit");}
    if(age==0)org.slf4j.LoggerFactory.getLogger("rail-motion-test").info("PHASE {} local={} corrections={}",phase,local,RailPassengerClient.corrections-corrections);
    else if(phase==9){if(c.currentScreen instanceof WorldStatisticsScreen){if(age>5){org.slf4j.LoggerFactory.getLogger("rail-motion-test").info("PASS: world statistics item opens three-tab client screen via server payload");phase=-1;controls(c);c.scheduleStop();}}else if(age>60)throw new AssertionError("world statistics item did not open screen");}
   }catch(Throwable e){phase=-1;controls(c);org.slf4j.LoggerFactory.getLogger("rail-motion-test").error("FAIL",e);c.scheduleStop();}
  });
 }
}
