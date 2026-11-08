package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.*;
import net.minecraft.util.math.Box;
final class TransportSoundClient {
 static final Map<UUID,Loop> playing=new HashMap<>();static int age;
 static void register(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(c.world==null||c.player==null){playing.clear();return;}playing.values().removeIf(Loop::isDone);if(++age%20!=0)return;RailVehicleEntity.nearby(c.world,new Box(c.player.getBlockPos()).expand(72)).stream().sorted(Comparator.comparingDouble(t->t.squaredDistanceTo(c.player))).limit(6).forEach(t->{if(!playing.containsKey(t.getUuid())){var loop=new Loop(t);playing.put(t.getUuid(),loop);c.getSoundManager().play(loop);}});});}
 static final class Loop extends MovingSoundInstance {
  final RailVehicleEntity vehicle;
  Loop(RailVehicleEntity t){super(t.isShip()?TransportSounds.SEA:t.kind()==0?TransportSounds.STEAM:TransportSounds.ROLLING,SoundCategory.BLOCKS,net.minecraft.util.math.random.Random.create());vehicle=t;repeat=true;repeatDelay=0;volume=.01f;tick();}
  @Override public boolean shouldAlwaysPlay(){return true;}
  @Override public void tick(){var c=MinecraftClient.getInstance();if(vehicle.isRemoved()||c.player==null||c.world!=vehicle.getEntityWorld()||vehicle.squaredDistanceTo(c.player)>10000){setDone();return;}x=vehicle.getX();y=vehicle.getY()+1;z=vehicle.getZ();double speed=vehicle.travelSpeed();float target;if(vehicle.isShip()){target=(float)(.17+Math.min(.6,speed*.9));var local=RailPassengerNetwork.toLocal(vehicle,c.player.getEntityPos());if(NavalGeometry.inside(vehicle,local)&&local.y<3)target*=.3f;pitch=.90f+(float)Math.min(.24,speed*.35);}else{target=(float)Math.min(vehicle.kind()==0?.62:.34,speed*(vehicle.kind()==0?1.4:.8));pitch=.65f+(float)Math.min(1.1,speed*1.4);}volume+=(Math.max(.001f,target)-volume)*.10f;}
 }
}
