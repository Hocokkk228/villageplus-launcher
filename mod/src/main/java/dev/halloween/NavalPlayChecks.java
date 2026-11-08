package dev.halloween;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

/** Real rendered client sends ordinary movement packets on a freely turning ship. */
final class NavalPlayChecks {
 static volatile boolean prepared,depart;static volatile int shipId;static RailVehicleEntity serverShip,ship;static int phase,age,waiting,corrections,teleports,packets;static Vec3d baseline;static double high;
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void register(){boolean multiplayer=Boolean.getBoolean("halloween.navalMultiClientTest");if(!Boolean.getBoolean("halloween.navalPlayTest")&&!multiplayer)return;
  if(!multiplayer)ServerTickEvents.END_SERVER_TICK.register(server->{var w=server.getOverworld();if(prepared){if(depart){serverShip.navalFlags(false,true,false);serverShip.naval().throttle=1;serverShip.naval().rudder=.25f;serverShip.naval().lastControl=w.getTime();}return;}if(w.getPlayers().isEmpty())return;var p=w.getPlayers().getFirst();var start=new Vec3d(-1048.5,63,1088.5);var cp=new ChunkPos(BlockPos.ofFloored(start));for(int x=-4;x<=4;x++)for(int z=-4;z<=8;z++)w.setChunkForced(cp.x+x,cp.z+z,true);
   if(++waiting<80)return;for(var old:RailVehicleEntity.nearby(w,new Box(start,start).expand(150)))old.discard();serverShip=NavalContent.spawn(w,2,start,0);require(serverShip!=null,"normal ocean accepts test brig");shipId=serverShip.getId();p.stopRiding();p.changeGameMode(GameMode.SURVIVAL);p.setHealth(20);p.getAbilities().flying=false;p.sendAbilitiesUpdate();p.networkHandler.requestTeleport(start.x+.85,start.y+3,start.z+5,0,0);prepared=true;log("PREPARED {}",shipId);
  });
  ClientTickEvents.START_CLIENT_TICK.register(c->{if(!prepared||phase<0||c.player==null)return;c.options.forwardKey.setPressed(phase==2);c.options.backKey.setPressed(phase==4);c.options.jumpKey.setPressed(phase==6&&age<7);c.options.leftKey.setPressed(false);c.options.rightKey.setPressed(false);c.options.sneakKey.setPressed(false);c.options.useKey.setPressed(false);c.options.attackKey.setPressed(false);if(ship!=null)c.player.setYaw(ship.getYaw());});
  ClientTickEvents.END_CLIENT_TICK.register(c->{if(multiplayer&&!prepared&&c.world!=null&&c.player!=null){var found=RailVehicleEntity.nearby(c.world,c.player.getBoundingBox().expand(45)).stream().filter(t->t.isShip()&&t.getName().getString().equals("Fleet Multiplayer Test")).findFirst().orElse(null);if(found!=null){prepared=true;shipId=found.getId();}}if(!prepared||phase<0||c.player==null||c.world==null)return;c.options.pauseOnLostFocus=false;if(c.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen)c.setScreen(null);
   try{if(ship==null){ship=c.world.getEntityById(shipId) instanceof RailVehicleEntity t?t:null;if(ship==null)return;}age++;var local=RailPassengerNetwork.toLocal(ship,c.player.getEntityPos());require(c.player.isAlive()&&c.player.getHealth()>19,"survival passenger unharmed");
    if(phase==0&&age>40){require(RailPassengerClient.aboard(c.player),"client boards real ship");baseline=local;corrections=RailPassengerClient.corrections;teleports=RailPassengerClient.teleports;packets=RailPassengerClient.sent;depart=true;phase++;age=0;}
    else if(phase==1){require(Math.abs(local.x-baseline.x)<.12&&Math.abs(local.z-baseline.z)<.12,"standing stays in ship frame: "+local+" vs "+baseline);if(age>150){phase++;age=0;baseline=local;}}
    else if(phase==2&&age>=8){require(local.z-baseline.z>.6,"walk forward freely while turning: "+local);phase++;age=0;}
    else if(phase==3&&age>=15){baseline=local;phase++;age=0;}
    else if(phase==4&&age>=12){require(local.z-baseline.z<-.8,"walk backward freely: "+local);phase++;age=0;}
    else if(phase==5&&age>=15){high=local.y;phase++;age=0;}
    else if(phase==6){high=Math.max(high,local.y);if(age>=35){require(high>3.65,"player jumps above main deck");require(Math.abs(local.y-3)<.12,"lands back on moving deck "+local);phase++;age=0;baseline=local;}}
    else if(phase==7&&age>=100){require(Math.abs(local.x-baseline.x)<.12&&Math.abs(local.z-baseline.z)<.12,"no deck drift after jump");require(RailPassengerClient.corrections==corrections,"zero server correction teleports "+(RailPassengerClient.corrections-corrections));require(RailPassengerClient.teleports==teleports,"no vanilla teleport rollback");require(RailPassengerClient.sent-packets>300,"actual relative packets sent");if(multiplayer){require(LauncherCosmeticsClient.REMOTE.entrySet().stream().anyMatch(e->!e.getKey().equals(c.player.getUuid())&&e.getValue().size()==2),"other real player's two cosmetic selections synchronized");log("PASS other player's outfit synchronized through server: {}",LauncherCosmeticsClient.REMOTE);}log("PASS real client: free sailing + turns, standing, forward/backward walking, jumping; {} packets; no correction teleports",RailPassengerClient.sent-packets);phase=-1;c.scheduleStop();}
    require(age<500,"stage timeout "+phase);
   }catch(Throwable error){phase=-1;org.slf4j.LoggerFactory.getLogger("naval-client-test").error("FAIL phase="+phase,error);c.scheduleStop();}
  });
 }
 static void log(String text,Object...args){org.slf4j.LoggerFactory.getLogger("naval-client-test").info(text,args);}
}
