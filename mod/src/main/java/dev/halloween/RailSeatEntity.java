package dev.halloween;
import java.util.UUID;
import net.minecraft.entity.*;
import net.minecraft.entity.data.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
public final class RailSeatEntity extends Entity {
 private static final TrackedData<Integer> TRAIN=DataTracker.registerData(RailSeatEntity.class,TrackedDataHandlerRegistry.INTEGER);
 private static final TrackedData<Float> X=DataTracker.registerData(RailSeatEntity.class,TrackedDataHandlerRegistry.FLOAT),Y=DataTracker.registerData(RailSeatEntity.class,TrackedDataHandlerRegistry.FLOAT),Z=DataTracker.registerData(RailSeatEntity.class,TrackedDataHandlerRegistry.FLOAT);
 private static final TrackedData<Boolean> WAIT_REST=DataTracker.registerData(RailSeatEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
 private static final TrackedData<Boolean> REST=DataTracker.registerData(RailSeatEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
 private static final TrackedData<Boolean> CONTROL=DataTracker.registerData(RailSeatEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
 UUID owner;Vec3d local=Vec3d.ZERO;boolean control;private boolean aligned;private float heading;
 public RailSeatEntity(EntityType<? extends RailSeatEntity> type,World world){super(type,world);setNoGravity(true);}
 @Override protected void initDataTracker(DataTracker.Builder b){b.add(WAIT_REST,true);b.add(REST,false);b.add(TRAIN,-1);b.add(X,0f);b.add(Y,0f);b.add(Z,0f);b.add(CONTROL,false);}
 public RailVehicleEntity train(){if(getEntityWorld() instanceof ServerWorld w&&owner!=null&&w.getEntity(owner) instanceof RailVehicleEntity t)return t;return getEntityWorld().getEntityById(dataTracker.get(TRAIN)) instanceof RailVehicleEntity t?t:null;}
 public boolean restWaiting(){return dataTracker.get(WAIT_REST);}
 void restWaiting(boolean value){dataTracker.set(WAIT_REST,value);}
 public boolean resting(){return dataTracker.get(REST);}
 void resting(boolean value){dataTracker.set(REST,value);}
 public boolean controls(){return control||dataTracker.get(CONTROL);}
 @Override public boolean damage(ServerWorld w,DamageSource source,float amount){return false;}
 @Override protected Vec3d getPassengerAttachmentPos(Entity passenger,EntityDimensions dimensions,float scale){var t=train();double offset=resting()?0:t!=null&&t.isShip()?-.885:controls()?-.80:-.90;return passenger.getVehicleAttachmentPos(this).add(0,offset,0);}
 public void syncToTrain(){var t=train();if(t==null)return;
  if(getEntityWorld().isClient()){local=new Vec3d(dataTracker.get(X),dataTracker.get(Y),dataTracker.get(Z));control=dataTracker.get(CONTROL);}
  else{dataTracker.set(TRAIN,t.getId());dataTracker.set(X,(float)local.x);dataTracker.set(Y,(float)local.y);dataTracker.set(Z,(float)local.z);dataTracker.set(CONTROL,control);}
  setPosition(RailGeometry.rotate(local,t.getYaw()).add(t.getEntityPos()));float yaw=t.getYaw()+(controls()||t.isShip()?0:180);if(t.isSleeper())yaw=t.getYaw()+(local.z>RailGeometry.compartmentCenter(local.z)?180:0);setYaw(yaw);
  for(var p:getPassengerList()){if(!aligned)p.setYaw(yaw);else p.setYaw(p.getYaw()+MathHelper.wrapDegrees(yaw-heading));if(p instanceof LivingEntity v)v.setBodyYaw(yaw);updatePassengerPosition(p);}
  heading=yaw;aligned=true;
 }
 @Override public Vec3d getPassengerRidingPos(Entity passenger){var t=train();if(t==null)return super.getPassengerRidingPos(passenger);return RailGeometry.rotate(local,t.getYaw()).add(t.getEntityPos()).add(getPassengerAttachmentPos(passenger,getDimensions(getPose()),1));}
 /** Render the rider in the carriage's reference frame, not through separate world-position interpolation. */
 public Vec3d renderedPassengerPos(Entity passenger,float delta){var t=train();if(t==null)return passenger.getLerpedPos(delta);var origin=new Vec3d(MathHelper.lerp(delta,t.lastRenderX,t.getX()),MathHelper.lerp(delta,t.lastRenderY,t.getY()),MathHelper.lerp(delta,t.lastRenderZ,t.getZ()));return RailGeometry.rotate(local,t.getLerpedYaw(delta)).add(origin).add(getPassengerAttachmentPos(passenger,getDimensions(getPose()),1)).subtract(passenger.getVehicleAttachmentPos(this));}
 @Override public Vec3d updatePassengerForDismount(LivingEntity passenger){var t=train();if(t==null)return super.updatePassengerForDismount(passenger);var aisle=t.isSleeper()?new Vec3d(-.3,1.5,RailGeometry.compartmentCenter(local.z)):t.isShip()?NavalGeometry.dismount(t.kind(),local):new Vec3d(0,1.5,Math.clamp(local.z,-5.5,5.5));return RailGeometry.rotate(aisle,t.getYaw()).add(t.getEntityPos());}
 @Override public void tick(){super.tick();if(getEntityWorld() instanceof ServerWorld){if(resting()&&getFirstPassenger() instanceof net.minecraft.server.network.ServerPlayerEntity p&&!RailRest.isResting(p)){RailRest.wake(p,this);return;}if(!hasPassengers()){discard();return;}if(train()==null){if(age>60)discard();return;}}syncToTrain();}
 @Override protected void writeCustomData(WriteView v){if(owner!=null)v.putString("owner",owner.toString());v.put("local",Vec3d.CODEC,local);v.putBoolean("control",control);v.putBoolean("resting",resting());}
 @Override protected void readCustomData(ReadView v){String id=v.getString("owner","");owner=id.isEmpty()?null:UUID.fromString(id);local=v.read("local",Vec3d.CODEC).orElse(Vec3d.ZERO);control=v.getBoolean("control",false);resting(v.getBoolean("resting",false));}
}
