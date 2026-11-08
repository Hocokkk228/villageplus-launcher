package dev.halloween;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.storage.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** A persistent physical vehicle: smooth interpolation, fixed seat assignments and validated driving. */
public final class IndustrialVehicleEntity extends Entity {
 private static final TrackedData<Integer> KIND=DataTracker.registerData(IndustrialVehicleEntity.class,TrackedDataHandlerRegistry.INTEGER);
 private static final TrackedData<Boolean> DOORS=DataTracker.registerData(IndustrialVehicleEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
 private static final TrackedData<Float> SPEED=DataTracker.registerData(IndustrialVehicleEntity.class,TrackedDataHandlerRegistry.FLOAT);
 private static final TrackedData<Float> STEER=DataTracker.registerData(IndustrialVehicleEntity.class,TrackedDataHandlerRegistry.FLOAT);
 private static final TrackedData<String> SEATS=DataTracker.registerData(IndustrialVehicleEntity.class,TrackedDataHandlerRegistry.STRING);
 private final PositionInterpolator interpolator=new PositionInterpolator(this,3);
 private final List<String> seatIds=new ArrayList<>();
 String streetId="";int nextStop,visits;long dwellUntil,lastPassengerExchange;
 double speed,input,steering,streetDistance=-1;boolean brake=true;long lastControl,lastDoor;float health=120;public float doorAngle,lastDoorAngle,wheelRotation,lastWheelRotation;
 public IndustrialVehicleEntity(EntityType<? extends IndustrialVehicleEntity> type,World world){super(type,world);}
 @Override protected void initDataTracker(DataTracker.Builder b){b.add(KIND,0);b.add(DOORS,true);b.add(SPEED,0f);b.add(STEER,0f);b.add(SEATS,"");}
 public IndustrialMachines.Kind kind(){return IndustrialMachines.Kind.values()[Math.clamp(dataTracker.get(KIND),0,IndustrialMachines.Kind.values().length-1)];}
 public float steeringAngle(){return dataTracker.get(STEER);}
 public boolean doors(){return dataTracker.get(DOORS);}
 public void doors(boolean open){dataTracker.set(DOORS,open);if(!getEntityWorld().isClient())getEntityWorld().playSound(null,getX(),getY(),getZ(),open?SoundEvents.BLOCK_IRON_DOOR_OPEN:SoundEvents.BLOCK_IRON_DOOR_CLOSE,SoundCategory.BLOCKS,.7f,.8f);}
 void configure(IndustrialMachines.Kind kind,Vec3d at,float yaw){dataTracker.set(KIND,kind.ordinal());setPosition(at);setYaw(yaw);health=kind==IndustrialMachines.Kind.TANK?500:kind==IndustrialMachines.Kind.APC?300:120;updateBounds();}
 void updateBounds(){setBoundingBox(RailGeometry.worldBox(kind().bounds(),getEntityPos(),getYaw()));}
 @Override public PositionInterpolator getInterpolator(){return interpolator;}
 @Override public boolean canHit(){return true;}
 @Override public boolean isCollidable(Entity e){return !hasPassenger(e)&&!e.isPartOf(this);}
 @Override public boolean isPushable(){return false;}
 @Override public float getStepHeight(){return .6f;}
 int seat(Entity e){var ids=getEntityWorld().isClient()?Arrays.asList(dataTracker.get(SEATS).split(",",-1)):seatIds;return ids.indexOf(e.getUuidAsString());}
 public boolean isDriver(Entity e){return seat(e)==0&&getPassengerList().contains(e);}
 Entity driver(){return getPassengerList().stream().filter(this::isDriver).findFirst().orElse(null);}
 boolean hasDriver(){return getPassengerList().stream().anyMatch(this::isDriver);}
 boolean board(Entity passenger,boolean driver){
  if(passenger.hasVehicle()||passenger.isRemoved())return false;
  while(seatIds.size()<kind().seats)seatIds.add("");
  int at=driver?0:-1;if(driver&&!seatIds.getFirst().isEmpty())return false;
  if(!driver)for(int i=1;i<seatIds.size();i++)if(seatIds.get(i).isEmpty()){at=i;break;}
  if(at<0)return false;seatIds.set(at,passenger.getUuidAsString());syncSeats();
  if(passenger.startRiding(this,true,false)){passenger.setYaw(getYaw());if(passenger instanceof LivingEntity living)living.setBodyYaw(getYaw());updatePassengerPosition(passenger);return true;}seatIds.set(at,"");syncSeats();return false;
 }
 void syncSeats(){dataTracker.set(SEATS,String.join(",",seatIds));}
 @Override protected boolean canAddPassenger(Entity passenger){return getPassengerList().size()<kind().seats&&seat(passenger)>=0;}
 @Override protected void removePassenger(Entity passenger){super.removePassenger(passenger);if(!getEntityWorld().isClient()){int at=seatIds.indexOf(passenger.getUuidAsString());if(at>=0)seatIds.set(at,"");syncSeats();}}
 @Override protected Vec3d getPassengerAttachmentPos(Entity passenger,EntityDimensions dimensions,float scale){int index=Math.max(0,seat(passenger));return passenger.getVehicleAttachmentPos(this).add(RailGeometry.rotate(kind().seat(index).add(0,-.8,0),getYaw()));}
 @Override public Vec3d getPassengerRidingPos(Entity passenger){return getEntityPos().add(getPassengerAttachmentPos(passenger,getDimensions(getPose()),1));}
 public Vec3d renderedPassengerPos(Entity passenger,float delta){var origin=new Vec3d(MathHelper.lerp(delta,lastRenderX,getX()),MathHelper.lerp(delta,lastRenderY,getY()),MathHelper.lerp(delta,lastRenderZ,getZ()));return origin.add(RailGeometry.rotate(kind().seat(Math.max(0,seat(passenger))).add(0,-.8,0),getLerpedYaw(delta)));}
 @Override public ActionResult interact(PlayerEntity p,Hand hand){
  if(hand!=Hand.MAIN_HAND)return ActionResult.PASS;if(getEntityWorld().isClient())return ActionResult.SUCCESS;
  if(p.isSneaking()){if(Math.abs(speed)<.03)doors(!doors());else p.sendMessage(Text.literal("Сначала остановитесь."),true);return ActionResult.SUCCESS;}
  if(!doors()){doors(true);return ActionResult.SUCCESS;}
  boolean driver=seatIds.isEmpty()||seatIds.getFirst().isEmpty();if(!board(p,driver))p.sendMessage(Text.literal("Свободных мест нет."),true);
  return ActionResult.SUCCESS;
 }
 @Override public Vec3d updatePassengerForDismount(LivingEntity passenger){
  var w=getEntityWorld();for(int side:new int[]{-1,1})for(double z:new double[]{kind().length*.30,0,-kind().length*.30}){var at=getEntityPos().add(RailGeometry.rotate(new Vec3d(side*(kind().width/2+.75),0,z),getYaw()));var p=BlockPos.ofFloored(at);if(w.getBlockState(p.down()).isSolidBlock(w,p.down())&&w.isSpaceEmpty(passenger,passenger.getBoundingBox().offset(at.subtract(passenger.getEntityPos()))))return at;}
  return getEntityPos().add(0,kind().height+.2,0);
 }
 @Override public void tick(){super.tick();lastDoorAngle=doorAngle;doorAngle=MathHelper.stepTowards(doorAngle,doors()?1:0,.12f);lastWheelRotation=wheelRotation;wheelRotation+=dataTracker.get(SPEED)*2.6f;
  if(getEntityWorld().isClient()){interpolator.tick();updateBounds();return;}
  var w=(ServerWorld)getEntityWorld();
  for(var p:getPassengerList())if(p instanceof LivingEntity living){living.setBodyYaw(getYaw());if(!(p instanceof PlayerEntity)){living.setYaw(getYaw());living.setHeadYaw(getYaw());}}
  if(age%40==0){var actual=getPassengerList().stream().map(Entity::getUuidAsString).collect(java.util.stream.Collectors.toSet());boolean changed=false;for(int i=0;i<seatIds.size();i++)if(!seatIds.get(i).isEmpty()&&!actual.contains(seatIds.get(i))){seatIds.set(i,"");changed=true;}if(changed)syncSeats();}
  IndustrialTraffic.control(w,this);
  if(!hasDriver()||w.getTime()-lastControl>10){input=0;brake=true;}
  double target=brake||doors()?0:input*kind().maxSpeed;speed+=Math.clamp(target-speed,-.025,.012);
  float oldYaw=getYaw();setYaw(oldYaw+(float)(steering*(speed*(kind()==IndustrialMachines.Kind.BUS?10:kind()==IndustrialMachines.Kind.SEDAN?14:10)+(kind()==IndustrialMachines.Kind.TANK?1.5:0))));updateBounds();
  double vertical=getVelocity().y-.04;
  if(kind()==IndustrialMachines.Kind.FIGHTER&&hasDriver()&&Math.abs(speed)>.6&&getPassengerList().stream().filter(this::isDriver).findFirst().orElse(null) instanceof PlayerEntity pilot){setPitch(pilot.getPitch());setYaw(pilot.getYaw());vertical=-Math.sin(Math.toRadians(getPitch()))*Math.abs(speed);}
  var movement=RailGeometry.rotate(new Vec3d(0,vertical,speed),getYaw());
  // Ordinary traffic yields to people in its lane, rather than pushing them into walls.
  if(driver() instanceof net.minecraft.entity.passive.VillagerEntity
      &&w.getOtherEntities(this,getBoundingBox().stretch(movement.multiply(8)),e->e instanceof LivingEntity&&!hasPassenger(e))
      .stream().anyMatch(person->{
       var local=RailGeometry.rotate(person.getEntityPos().subtract(getEntityPos()),-getYaw());
       return Math.abs(local.x)<kind().width/2+.36&&local.z>kind().length*.20
           &&local.z<kind().length/2+Math.abs(speed)*8+.8;
      })){
   speed=Math.max(0,speed-.06);
   movement=new Vec3d(0,vertical,0);
  }
  var before=getEntityPos();move(MovementType.SELF,movement);if(horizontalCollision){speed=0;if(!w.isSpaceEmpty(this,getBoundingBox())){setYaw(oldYaw);setPosition(before);}}
  setVelocity(0,isOnGround()?0:vertical,0);dataTracker.set(SPEED,(float)speed);dataTracker.set(STEER,(float)steering);velocityDirty=true;updateBounds();
  if(Math.abs(speed)>.03&&age%8==0){var exhaust=getEntityPos().add(RailGeometry.rotate(new Vec3d(-.5,.4,-kind().length/2),getYaw()));w.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,exhaust.x,exhaust.y,exhaust.z,1,.04,.04,.04,.01);}
 }
 @Override public boolean damage(ServerWorld w,DamageSource source,float amount){if(isInvulnerable())return false;health-=amount;if(health<=0){for(var e:List.copyOf(getPassengerList()))e.stopRiding();dropStack(w,new ItemStack(IndustrialMachines.PARTS.get("machine_frame")));discard();}return true;}
 @Override protected void writeCustomData(WriteView v){v.putString("street",streetId);v.putDouble("street_distance",streetDistance);v.putInt("next_bus_stop",nextStop);v.putInt("bus_visits",visits);v.putLong("bus_dwell",dwellUntil);v.putInt("machine_kind",kind().ordinal());v.putBoolean("doors",doors());v.putFloat("health",health);v.putDouble("speed",speed);v.put("seat_ids",com.mojang.serialization.Codec.STRING.listOf(),seatIds);}
 @Override protected void readCustomData(ReadView v){streetId=v.getString("street","");streetDistance=v.getDouble("street_distance",-1);nextStop=v.getInt("next_bus_stop",0);visits=v.getInt("bus_visits",0);dwellUntil=v.getLong("bus_dwell",0);dataTracker.set(KIND,Math.clamp(v.getInt("machine_kind",0),0,4));dataTracker.set(DOORS,v.getBoolean("doors",true));health=v.getFloat("health",120);speed=v.getDouble("speed",0);seatIds.clear();seatIds.addAll(v.read("seat_ids",com.mojang.serialization.Codec.STRING.listOf()).orElse(List.of()));syncSeats();updateBounds();}
}
