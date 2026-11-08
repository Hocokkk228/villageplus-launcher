package dev.halloween;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
/** One local-coordinate movement stream replaces world-coordinate movement only while walking aboard. */
public final class RailPassengerClient {
 private static RailVehicleEntity train,lastTrain;
 private static net.minecraft.util.math.Vec3d lastOrigin,lastPlayer;
 private static float lastYaw;private static PlayerEntity lastPassenger;
 public static volatile int corrections;public static volatile int sent;private static boolean wasAboard;public static volatile int teleports;
 public static void resetReference(){lastTrain=null;train=null;lastOrigin=null;lastPlayer=null;lastPassenger=null;wasAboard=false;RailVehicleEntity.clientFrame=null;teleports++;}
 public static boolean aboard(PlayerEntity p){
  if(p==null||p.isSpectator()||p.hasVehicle()||!p.isAlive()||p.getAbilities().flying||!ClientPlayNetworking.canSend(RailPassengerNetwork.Motion.ID)){train=null;return false;}
  if(lastPassenger!=null&&lastPassenger!=p)resetReference();
  if(train!=null&&!train.isRemoved()&&train.getEntityWorld()==p.getEntityWorld()){
   if(RailPassengerNetwork.inside(train,RailPassengerNetwork.toLocal(train,p.getEntityPos())))return true;
   // A train position packet can arrive before the next passenger tick, especially after an FPS stall.
   if(lastTrain==train&&lastPassenger==p&&lastOrigin!=null&&lastPlayer!=null&&p.getEntityPos().squaredDistanceTo(lastPlayer)<9&&RailPassengerNetwork.inside(train,RailGeometry.rotate(p.getEntityPos().subtract(lastOrigin),-lastYaw)))return true;
  }
  train=RailVehicleEntity.nearby(p.getEntityWorld(),p.getBoundingBox().expand(1)).stream().filter(t->RailPassengerNetwork.inside(t,RailPassengerNetwork.toLocal(t,p.getEntityPos()))).findFirst().orElse(null);return train!=null;
 }
 public static void register(){
  ClientPlayNetworking.registerGlobalReceiver(RailPassengerNetwork.TeleportFrame.ID,(packet,context)->context.client().execute(()->{
   var p=context.player();if(p==null||p.hasVehicle()||!(p.getEntityWorld().getEntityById(packet.train()) instanceof RailVehicleEntity ship))return;
   resetReference();train=ship;p.setPosition(RailGeometry.rotate(packet.local(),ship.getYaw()).add(ship.getEntityPos()));
  }));
  ClientTickEvents.START_CLIENT_TICK.register(c->{
   RailVehicleEntity.clientFrame=null;if(!aboard(c.player)){lastTrain=null;return;}
   // Network interpolation may update the carriage between client ticks. Include that displacement too.
   if(lastTrain==train&&lastOrigin!=null&&lastPlayer!=null&&c.player.getEntityPos().squaredDistanceTo(lastPlayer)<9){
    var local=RailGeometry.rotate(c.player.getEntityPos().subtract(lastOrigin),-lastYaw);
    c.player.setPosition(RailGeometry.rotate(local,train.getYaw()).add(train.getEntityPos()));
   }
   RailVehicleEntity.clientFrame=new RailVehicleEntity.ClientFrame(train,c.player,train.getEntityPos(),train.getYaw());
  });
  ClientTickEvents.END_CLIENT_TICK.register(c->{
   var frame=RailVehicleEntity.clientFrame;

   if(frame!=null&&c.player==frame.player()&&!c.player.hasVehicle()&&!c.player.isSpectator()){
    var p=c.player;var local=RailGeometry.rotate(p.getEntityPos().subtract(frame.origin()),-frame.yaw());
    var destination=RailGeometry.rotate(local,frame.train().getYaw()).add(frame.train().getEntityPos());boolean ground=p.isOnGround();
    // This is rigid-body transport, not another input move that ledge/friction rules may shorten.
    p.setPosition(destination);
    if(ground){p.setOnGround(true);p.fallDistance=0;}
   }
   RailVehicleEntity.clientFrame=null;
   if(!aboard(c.player)){if(wasAboard&&c.player!=null&&ClientPlayNetworking.canSend(RailPassengerNetwork.Motion.ID))ClientPlayNetworking.send(new RailPassengerNetwork.Motion(-1,net.minecraft.util.math.Vec3d.ZERO,0,0,false));wasAboard=false;lastTrain=null;return;}wasAboard=true;var p=c.player;
   lastPassenger=p;lastTrain=train;lastOrigin=train.getEntityPos();lastYaw=train.getYaw();lastPlayer=p.getEntityPos();
   ClientPlayNetworking.send(new RailPassengerNetwork.Motion(train.getId(),RailPassengerNetwork.toLocal(train,p.getEntityPos()),p.getYaw(),p.getPitch(),p.isOnGround()));sent++;
  });
  ClientPlayNetworking.registerGlobalReceiver(RailPassengerNetwork.Correction.ID,(packet,c)->c.client().execute(()->{
   if(c.player()==null||!c.player().isAlive()||c.player()!=lastPassenger||!(c.player().getEntityWorld().getEntityById(packet.train()) instanceof RailVehicleEntity vehicle))return;
   if(train!=vehicle)return;corrections++;c.player().setPosition(RailGeometry.rotate(packet.local(),vehicle.getYaw()).add(vehicle.getEntityPos()));lastTrain=vehicle;lastOrigin=vehicle.getEntityPos();lastYaw=vehicle.getYaw();lastPlayer=c.player().getEntityPos();
  }));
 }
}
