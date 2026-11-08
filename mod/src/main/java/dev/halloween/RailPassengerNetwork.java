package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Passenger movement is measured in carriage coordinates, independently of network interpolation delay. */
public final class RailPassengerNetwork {
 public interface HandlerState {boolean railwayTeleportPending();}
 public record Motion(int train,Vec3d local,float yaw,float pitch,boolean ground) implements CustomPayload {
  public static final Id<Motion> ID=new Id<>(Identifier.of("halloween","rail_passenger_motion"));
  public static final PacketCodec<RegistryByteBuf,Motion> CODEC=new PacketCodec<>(){
   public Motion decode(RegistryByteBuf b){return new Motion(b.readVarInt(),new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readFloat(),b.readBoolean());}
   public void encode(RegistryByteBuf b,Motion m){b.writeVarInt(m.train);b.writeDouble(m.local.x);b.writeDouble(m.local.y);b.writeDouble(m.local.z);b.writeFloat(m.yaw);b.writeFloat(m.pitch);b.writeBoolean(m.ground);}
  };
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Correction(int train,Vec3d local) implements CustomPayload {
  public static final Id<Correction> ID=new Id<>(Identifier.of("halloween","rail_passenger_correction"));
  public static final PacketCodec<RegistryByteBuf,Correction> CODEC=new PacketCodec<>(){
   public Correction decode(RegistryByteBuf b){return new Correction(b.readVarInt(),new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()));}
   public void encode(RegistryByteBuf b,Correction c){b.writeVarInt(c.train);b.writeDouble(c.local.x);b.writeDouble(c.local.y);b.writeDouble(c.local.z);}
  };
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record TeleportFrame(int train,Vec3d local) implements CustomPayload {
  public static final Id<TeleportFrame> ID=new Id<>(Identifier.of("halloween","vehicle_teleport_frame"));
  public static final PacketCodec<RegistryByteBuf,TeleportFrame> CODEC=new PacketCodec<>(){
   public TeleportFrame decode(RegistryByteBuf b){return new TeleportFrame(b.readVarInt(),new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()));}
   public void encode(RegistryByteBuf b,TeleportFrame p){b.writeVarInt(p.train);b.writeDouble(p.local.x);b.writeDouble(p.local.y);b.writeDouble(p.local.z);}
  };public Id<? extends CustomPayload> getId(){return ID;}
 }
 /** Sent after the vanilla teleport, preserving its destination in a moving body's reference frame. */
 public static void teleportFrame(ServerPlayerEntity player){
  SESSIONS.remove(player);var w=player.getEntityWorld();
  for(var ship:RailVehicleEntity.nearby(w,player.getBoundingBox().expand(1))){var local=toLocal(ship,player.getEntityPos());if(!inside(ship,local))continue;
   if(ServerPlayNetworking.canSend(player,TeleportFrame.ID))ServerPlayNetworking.send(player,new TeleportFrame(ship.getId(),local));return;
  }
 }
 static final class Session {
  final RailVehicleEntity train;Vec3d local;long received,tick;double moved=6;
  Session(RailVehicleEntity train,Vec3d local,long time){this.train=train;this.local=local;received=time;tick=time;}
 }
 private static final Map<ServerPlayerEntity,Session> SESSIONS=new WeakHashMap<>();
 public static boolean active(ServerPlayerEntity player){return walkingVehicle(player)!=null;}
 static RailVehicleEntity walkingVehicle(ServerPlayerEntity player){var s=SESSIONS.get(player);return s!=null&&valid(player,s)?s.train:null;}
 private static boolean valid(ServerPlayerEntity p,Session s){return !((HandlerState)p.networkHandler).railwayTeleportPending()&& p.isAlive()&&!p.isSpectator()&&!p.hasVehicle()&&!s.train.isRemoved()&&s.train.getEntityWorld()==p.getEntityWorld()&&p.getEntityWorld().getTime()-s.received<=100&&p.getEntityPos().squaredDistanceTo(RailGeometry.rotate(s.local,s.train.getYaw()).add(s.train.getEntityPos()))<9;}
 public static boolean protectedPassenger(net.minecraft.entity.LivingEntity entity){
  if(entity.getVehicle() instanceof RailSeatEntity)return true;
  if(entity instanceof ServerPlayerEntity p){var s=SESSIONS.get(p);return s!=null&&p.isAlive()&&s.train.getEntityWorld()==p.getEntityWorld()&&!s.train.isRemoved()&&p.getEntityWorld().getTime()-s.received<=100&&inside(s.train,s.local);}
  return false;
 }
 public static Vec3d toLocal(RailVehicleEntity train,Vec3d world){return RailGeometry.rotate(world.subtract(train.getEntityPos()),-train.getYaw());}
 public static boolean inside(RailVehicleEntity t,Vec3d p){return t.isShip()?NavalGeometry.inside(t,p):t.kind()==1?(inside(p)||t.isSleeper()&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.abs(p.x)<=1.55&&Math.abs(p.z)<=7.85&&p.y>=1.38&&p.y<=5.8):Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.abs(p.x)<=1.35&&p.z>=-5.5&&p.z<=-2.45&&p.y>=1.38&&p.y<=4.3;}
 public static boolean inside(Vec3d p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.abs(p.x)<=1.55&&Math.abs(p.z)<=7.85&&p.y>=1.38&&p.y<=4.5;}
 static void apply(ServerPlayerEntity p,Motion m){
  var w=p.getEntityWorld();
  if(((HandlerState)p.networkHandler).railwayTeleportPending()){SESSIONS.remove(p);return;}
  if(!(w.getEntityById(m.train) instanceof RailVehicleEntity train)||!inside(train,m.local)||p.hasVehicle()||p.isSpectator()||!p.isAlive()||!Float.isFinite(m.yaw)||!Float.isFinite(m.pitch)){SESSIONS.remove(p);return;}
  var s=SESSIONS.get(p);long now=w.getTime();
  if(s==null||s.train!=train||!valid(p,s)){
   // Boarding can occur next to a platform; never accept a remote player claiming a carriage.
   var current=toLocal(train,p.getEntityPos());
   if(train.isShip()?!NavalGeometry.bounds(train.kind()).expand(1).contains(current):Math.abs(current.x)>3||Math.abs(current.z)>12||current.y<.5||current.y>5.5)return;
   s=new Session(train,m.local,now);SESSIONS.put(p,s);
  }
  if(s.tick!=now){s.moved=Math.min(6,s.moved+Math.max(0,now-s.tick)*.65);s.tick=now;}
  var delta=m.local.subtract(s.local);double horizontal=Math.hypot(delta.x,delta.z);
  if(horizontal>1.2||horizontal>s.moved||Math.abs(delta.y)>1.3){if(Boolean.getBoolean("halloween.railMotionTest"))org.slf4j.LoggerFactory.getLogger("rail-validation").info("rate moved={} horizontal={} delta={} now={}",s.moved,horizontal,delta,now);correct(p,s);return;}
  s.moved-=horizontal;s.received=now;
  // The server validates personal movement against physical walls in the current server frame.
  var start=RailGeometry.rotate(s.local,train.getYaw()).add(train.getEntityPos());p.setPosition(start);
  var movement=RailGeometry.rotate(delta,train.getYaw());p.move(net.minecraft.entity.MovementType.SELF,movement);
  var accepted=toLocal(train,p.getEntityPos());
  p.setYaw(m.yaw);p.setPitch(MathHelper.clamp(m.pitch,-90,90));
  p.setOnGround((train.isShip()?NavalGeometry.onFloor(train,accepted):Math.abs(accepted.y-1.5)<.06)&&delta.y<=.01);p.fallDistance=0;p.networkHandler.resetFloatingTicks();
  s.local=accepted;
  // A rejected wall crossing is corrected in the same carriage frame, not teleported to an old world position.
  if(accepted.squaredDistanceTo(m.local)>.09){if(Boolean.getBoolean("halloween.railMotionTest"))org.slf4j.LoggerFactory.getLogger("rail-validation").info("collision requested={} accepted={} delta={}",m.local,accepted,delta);correct(p,s);}
  w.getChunkManager().updatePosition(p);
 }
 private static void correct(ServerPlayerEntity p,Session s){ServerPlayNetworking.send(p,new Correction(s.train.getId(),s.local));}
 static void register(){
  PayloadTypeRegistry.playS2C().register(TeleportFrame.ID,TeleportFrame.CODEC);PayloadTypeRegistry.playC2S().register(Motion.ID,Motion.CODEC);PayloadTypeRegistry.playS2C().register(Correction.ID,Correction.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Motion.ID,(m,c)->c.server().execute(()->apply(c.player(),m)));
  ServerTickEvents.END_WORLD_TICK.register(w->{for(var entry:List.copyOf(SESSIONS.entrySet())){
   var p=entry.getKey();var s=entry.getValue();if(p.getEntityWorld()!=w)continue;
   if(!valid(p,s)){SESSIONS.remove(p);continue;}
   p.setPosition(RailGeometry.rotate(s.local,s.train.getYaw()).add(s.train.getEntityPos()));p.fallDistance=0;p.networkHandler.resetFloatingTicks();w.getChunkManager().updatePosition(p);
  }});
 }
}
