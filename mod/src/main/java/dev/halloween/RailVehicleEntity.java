package dev.halloween;
import java.util.*;
import com.google.gson.*;
import net.minecraft.entity.*;
import net.minecraft.entity.data.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
/** A physical carriage, with ordinary walking space rather than an oversized minecart seat. */
public final class RailVehicleEntity extends Entity {
 private static final Map<World,List<java.lang.ref.WeakReference<RailVehicleEntity>>> ACTIVE=Collections.synchronizedMap(new WeakHashMap<>());
 public record ClientFrame(RailVehicleEntity train,PlayerEntity player,Vec3d origin,float yaw){}
 public static volatile ClientFrame clientFrame;
 public static final ThreadLocal<RailVehicleEntity> CARRYING=new ThreadLocal<>();
 private final Map<UUID,Integer> lastImpact=new HashMap<>();
 public static List<RailVehicleEntity> nearby(World w,Box box){List<java.lang.ref.WeakReference<RailVehicleEntity>> refs; synchronized(ACTIVE){refs=ACTIVE.get(w);}if(refs==null)return List.of();List<RailVehicleEntity> out=null;for(var ref:refs){var train=ref.get();if(train!=null&&!train.isRemoved()&&train!=CARRYING.get()&&train.getBoundingBox().intersects(box)){if(out==null)out=new ArrayList<>();out.add(train);}}var frame=clientFrame;if(w.isClient()&&frame!=null&&frame.train().getEntityWorld()==w&&!frame.train().isRemoved()&&frame.train()!=CARRYING.get()&&RailGeometry.worldBox(frame.train().localBounds(),frame.origin(),frame.yaw()).intersects(box)){if(out==null)out=new ArrayList<>();if(!out.contains(frame.train()))out.add(frame.train());}return out==null?List.of():out;}
 private static final TrackedData<Boolean> SLEEPER=DataTracker.registerData(RailVehicleEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
 private static final TrackedData<Integer> COMPARTMENTS=DataTracker.registerData(RailVehicleEntity.class,TrackedDataHandlerRegistry.INTEGER);
 private static final TrackedData<Boolean> DOORS=DataTracker.registerData(RailVehicleEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
 private NavalPhysics.State navalState;
 private float wheelAngle,lastWheelAngle;
 private static final TrackedData<Float> TRAVEL_SPEED=DataTracker.registerData(RailVehicleEntity.class,TrackedDataHandlerRegistry.FLOAT);
 private static final TrackedData<Float> RUDDER=DataTracker.registerData(RailVehicleEntity.class,TrackedDataHandlerRegistry.FLOAT);
 private static final TrackedData<Float> NAV_HP=DataTracker.registerData(RailVehicleEntity.class,TrackedDataHandlerRegistry.FLOAT);
 private static final TrackedData<Long> NAV_SHOT=DataTracker.registerData(RailVehicleEntity.class,TrackedDataHandlerRegistry.LONG);
 private static final TrackedData<Integer> KIND=DataTracker.registerData(RailVehicleEntity.class,TrackedDataHandlerRegistry.INTEGER);
 private static final TrackedData<String> PATH=DataTracker.registerData(RailVehicleEntity.class,TrackedDataHandlerRegistry.STRING);
 private static final TrackedData<Float> LANE=DataTracker.registerData(RailVehicleEntity.class,TrackedDataHandlerRegistry.FLOAT);
 private static final TrackedData<Boolean> NAV_ANCHOR=DataTracker.registerData(RailVehicleEntity.class,TrackedDataHandlerRegistry.BOOLEAN),NAV_SAILS=DataTracker.registerData(RailVehicleEntity.class,TrackedDataHandlerRegistry.BOOLEAN),NAV_GANGWAY=DataTracker.registerData(RailVehicleEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
 private static final net.minecraft.server.world.ChunkTicketType TRAIN_TICKET=new net.minecraft.server.world.ChunkTicketType(80,net.minecraft.server.world.ChunkTicketType.FOR_LOADING|net.minecraft.server.world.ChunkTicketType.FOR_SIMULATION);
 private final PositionInterpolator interpolator=new PositionInterpolator(this,3);
 public static java.util.function.Supplier<PlayerEntity> localPlayer=()->null;
 private RailPath path;private String loaded="";public double distance,speed;public int direction=1,dwell;private UUID leader;private int car;
 public boolean driverCreated;public long driverVacantSince=-1;public float manualInput;public boolean manualBrake;public long manualAt;public double throttle=.72;public List<Double> stationStops=List.of();public int nextStop;public int visits;
 public RailVehicleEntity(EntityType<? extends RailVehicleEntity> type,World world){super(type,world);setNoGravity(true);synchronized(ACTIVE){ACTIVE.computeIfAbsent(world,k->new java.util.concurrent.CopyOnWriteArrayList<>()).add(new java.lang.ref.WeakReference<>(this));}}
 @Override protected void initDataTracker(DataTracker.Builder b){b.add(SLEEPER,false);b.add(COMPARTMENTS,7);b.add(DOORS,false);b.add(NAV_SHOT,-1L);b.add(NAV_HP,160f);b.add(RUDDER,0f);b.add(TRAVEL_SPEED,0f);b.add(NAV_ANCHOR,true);b.add(NAV_SAILS,true);b.add(NAV_GANGWAY,true);b.add(KIND,0);b.add(PATH,"");b.add(LANE,0f);}
 public void setStops(List<Double> stops){stationStops=List.copyOf(stops);nextStop=0;if(path!=null&&!stops.isEmpty()){double best=Double.MAX_VALUE;for(int i=0;i<stops.size();i++){double d=path.wrap(stops.get(i)-distance);if(d>1&&d<best){best=d;nextStop=i;}}}}
 public RailPath route(){loadPath();return path;}
 public boolean isSleeper(){return kind()==1&&dataTracker.get(SLEEPER);}
 RailVehicleEntity engine(){if(leader==null)return this;return getEntityWorld() instanceof ServerWorld w&&w.getEntity(leader) instanceof RailVehicleEntity e?e:null;}
 public int modelKind(){return isSleeper()?4:kind();}
 public void toggleCompartment(int i){if(i>=0&&i<3)dataTracker.set(COMPARTMENTS,compartments()^(1<<i));}
 public int compartments(){return dataTracker.get(COMPARTMENTS);}
 public void openCompartment(double z){int i=(int)Math.round(RailGeometry.compartmentCenter(z)/3.15)+1;dataTracker.set(COMPARTMENTS,compartments()|(1<<i));}
 public List<Vec3d> seats(){return isSleeper()?RailGeometry.sleeperSeats():RailGeometry.seats();}
 public List<Box> collisionCells(){return isShip()?NavalGeometry.cells(kind(),doorsOpen(),gangwayDeployed()):RailGeometry.solidCells(modelKind(),doorsOpen(),compartments());}
 public boolean isShip(){return kind()>=2;}
 public Box localBounds(){return isShip()?NavalGeometry.bounds(kind()):new Box(-1.8,0,-8,1.8,isSleeper()?6.4:5.7,8);}
 public NavalPhysics.State naval(){if(navalState==null)navalState=new NavalPhysics.State(this);return navalState;}
 public float hullHealth(){return dataTracker.get(NAV_HP);}
 void hullHealth(float hp){dataTracker.set(NAV_HP,MathHelper.clamp(hp,0,kind()==3?110:160));}
 public long cannonShot(){return dataTracker.get(NAV_SHOT);}
 void cannonFired(int index){dataTracker.set(NAV_SHOT,(getEntityWorld().getTime()<<3)|index);}
 public boolean anchored(){return dataTracker.get(NAV_ANCHOR);}
 void rudder(float value){if(Math.abs(value-dataTracker.get(RUDDER))>.001f)dataTracker.set(RUDDER,value);}
 public float travelSpeed(){return dataTracker.get(TRAVEL_SPEED);}
 private void syncSpeed(){float value=(float)(Math.rint(Math.abs(speed)*100)/100);if(value!=dataTracker.get(TRAVEL_SPEED))dataTracker.set(TRAVEL_SPEED,value);}
 public float wheelAngle(float delta){return MathHelper.lerp(delta,lastWheelAngle,wheelAngle);}
 public boolean sailsRaised(){return dataTracker.get(NAV_SAILS);}
 public boolean gangwayDeployed(){return dataTracker.get(NAV_GANGWAY);}
 void navalFlags(boolean anchor,boolean sails,boolean gangway){dataTracker.set(NAV_ANCHOR,anchor);dataTracker.set(NAV_SAILS,sails);dataTracker.set(NAV_GANGWAY,gangway);}
 public void configureShip(int variant,Vec3d pos,float yaw){dataTracker.set(KIND,variant==3?3:2);hullHealth(variant==3?110:160);dataTracker.set(PATH,"");setPosition(pos);setYaw(yaw);setBoundingBox(RailGeometry.worldBox(localBounds(),pos,yaw));}
 public boolean doorsOpen(){return dataTracker.get(DOORS);}
 public boolean setDoors(boolean open){if(!isShip()&&open&&Math.abs(speed)>.04)return false;dataTracker.set(DOORS,open);return true;}
 public int kind(){return dataTracker.get(KIND);}
 public void configure(int kind,RailPath route,double lane,double distance,UUID leader,int car){dataTracker.set(KIND,kind);dataTracker.set(SLEEPER,kind==1&&car==2);dataTracker.set(PATH,new Gson().toJson(route.controls.stream().map(v->List.of(Math.rint(v.x*100)/100,Math.rint(v.y*100)/100,Math.rint(v.z*100)/100)).toList()));dataTracker.set(LANE,(float)lane);this.path=route;this.loaded=dataTracker.get(PATH);this.distance=distance;this.leader=leader;this.car=car;updatePose();}
 private void loadPath(){String value=dataTracker.get(PATH);if(value.equals(loaded))return;loaded=value;if(value.isBlank()){path=null;return;}var controls=new ArrayList<Vec3d>();for(var p:JsonParser.parseString(value).getAsJsonArray()){var a=p.getAsJsonArray();controls.add(new Vec3d(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()));}path=new RailPath(controls);}
 private void updatePose(){if(path==null)return;setPosition(path.at(distance,dataTracker.get(LANE)).add(0,.15,0));setYaw(path.yaw(distance));setBoundingBox(RailGeometry.worldBox(localBounds(),getEntityPos(),getYaw()));}
 @Override public PositionInterpolator getInterpolator(){return interpolator;}
 @Override public void tick(){super.tick();var old=getEntityPos();float oldYaw=getYaw();loadPath();
  if(getEntityWorld().isClient()){lastWheelAngle=wheelAngle;wheelAngle+=(dataTracker.get(RUDDER)*270-wheelAngle)*.16f;interpolator.tick();setBoundingBox(RailGeometry.worldBox(localBounds(),getEntityPos(),getYaw()));syncSeats();carry(old,oldYaw,true);return;}
  if(isShip()){TransportSounds.tick((ServerWorld)getEntityWorld(),this);NavalPhysics.tick((ServerWorld)getEntityWorld(),this);if(isRemoved())return;syncSpeed();setBoundingBox(RailGeometry.worldBox(localBounds(),getEntityPos(),getYaw()));syncSeats();carry(old,oldYaw,false);return;}
  if(path==null)return;
  if(age%20==0){var w=(ServerWorld)getEntityWorld();var cp=new ChunkPos(getBlockPos());for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.getChunkManager().addTicket(TRAIN_TICKET,new ChunkPos(cp.x+x,cp.z+z),2);}
  if(kind()==0){RailDriver.ensure((ServerWorld)getEntityWorld(),this);if(dwell>0&&age%20==0)RailStationSafety.tick((ServerWorld)getEntityWorld());}
  if(leader!=null){if(((ServerWorld)getEntityWorld()).getEntity(leader) instanceof RailVehicleEntity engine){distance=engine.distance-car*15;speed=engine.speed;direction=engine.direction;}else return;}
  else {
   boolean operated=kind()!=0||RailDriver.powered((ServerWorld)getEntityWorld(),this);boolean manual=false;
   if(kind()==0){var chair=RailDriver.seat((ServerWorld)getEntityWorld(),this);manual=chair!=null&&chair.getFirstPassenger() instanceof net.minecraft.server.network.ServerPlayerEntity;}
   if(manual){if(getEntityWorld().getTime()-manualAt>10||manualBrake||manualInput<0)throttle=Math.max(0,throttle-.018);else if(manualInput>0)throttle=Math.min(.72,throttle+.008);}
   double remaining=path.closed?(stationStops.isEmpty()?Double.POSITIVE_INFINITY:path.wrap(stationStops.get(nextStop)-distance)):(direction>0?path.length-8-distance:distance-40);
   if(manual)remaining=Double.POSITIVE_INFINITY;
   double desired=operated?(manual?throttle:Math.min(.72,Math.sqrt(Math.max(0,remaining))*.10)):0;
   if(dwell>0){dwell--;desired=0;}
   double prior=speed;speed=desired<speed?Math.max(desired,speed-(operated?.008:.012)):Math.min(desired,speed+.003);
   if(speed<.001)speed=0;
   distance+=direction*Math.min(speed,Math.max(0,remaining));
   if(path.closed&&Double.isFinite(remaining)&&remaining<.16&&dwell==0&&!manual){distance=stationStops.get(nextStop);dwell=300;speed=0;visits++;RailTravel.arrive((ServerWorld)getEntityWorld(),this,nextStop);nextStop=(nextStop+1)%stationStops.size();}
   else if(!path.closed&&remaining<.15&&dwell==0){direction=-direction;dwell=240;speed=0;}
   if(prior>speed+.002&&prior>.06&&age%2==0){var w=(ServerWorld)getEntityWorld();for(int side:new int[]{-1,1})for(int axle:new int[]{-4,3}){var q=getEntityPos().add(RailGeometry.rotate(new Vec3d(side*.85,.5,axle),getYaw()));w.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,q.x,q.y,q.z,5,.12,.03,.12,.07);}}
  }
  if(kind()==1)dataTracker.set(DOORS,Math.abs(speed)<.025);
  syncSpeed();TransportSounds.tick((ServerWorld)getEntityWorld(),this);updatePose();syncSeats();carry(old,oldYaw,false);strikeOutside(old,oldYaw);RailBoarding.tick((ServerWorld)getEntityWorld(),this);
  if(kind()==0&&age%3==0){var smoke=getEntityPos().add(RailGeometry.rotate(new Vec3d(0,5.35,4.2),getYaw()));var w=(ServerWorld)getEntityWorld();
   w.spawnParticles(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE,smoke.x,smoke.y,smoke.z,2,.1,.06,.1,.025);
   if(speed>.03)w.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,smoke.x,smoke.y+.2,smoke.z,2,.15,.1,.15,.035);
  }
 }
 private void syncSeats(){for(var e:getEntityWorld().getOtherEntities(this,getBoundingBox().expand(8),e->e instanceof RailSeatEntity)){var seat=(RailSeatEntity)e;if(seat.train()==this)seat.syncToTrain();}}
 private void carry(Vec3d old,float oldYaw,boolean client){
  if(old.squaredDistanceTo(getEntityPos())<1e-10&&Math.abs(oldYaw-getYaw())<.001)return;
  var area=RailGeometry.worldBox(isShip()?new Box(-8*NavalGeometry.sx(kind()),.35,-14.5*NavalGeometry.sz(kind()),8*NavalGeometry.sx(kind()),8.8,15.5*NavalGeometry.sz(kind())):new Box(-1.8,1.4,-8,1.8,4.4,8),old,oldYaw).expand(.2);
  var occupants=new ArrayList<Entity>(getEntityWorld().getOtherEntities(this,area,e->e instanceof LivingEntity&&!e.hasVehicle()));
  if(client){var local=localPlayer.get();if(local!=null&&!local.hasVehicle()&&area.intersects(local.getBoundingBox())&&!occupants.contains(local))occupants.add(local);}
  for(var e:occupants){
   if(e instanceof PlayerEntity)continue;else if(client)continue;
   var local=RailGeometry.rotate(e.getEntityPos().subtract(old),-oldYaw);
   // Carry a jumping passenger too; vertical motion and controls remain the passenger's own.
   if(isShip()?!NavalGeometry.inside(this,local):Math.abs(local.x)>1.55||Math.abs(local.z)>7.9||local.y<1.42||local.y>4.35)continue;
   var destination=RailGeometry.rotate(local,getYaw()).add(getEntityPos());boolean grounded=e.isOnGround();
   // Transport is a change of reference frame, not passenger input against the train's newly moved walls.
   e.setPosition(destination);
   if(grounded){e.setOnGround(true);e.fallDistance=0;}
  }
 }
 public boolean cabinPassenger(Entity e,Vec3d origin,float yaw){
  if(isShip())return NavalGeometry.inside(this,RailGeometry.rotate(e.getEntityPos().subtract(origin),-yaw));
  var local=RailGeometry.rotate(e.getEntityPos().subtract(origin),-yaw);
  if(Math.abs(local.x)>1.8||Math.abs(local.z)>8.1)return false;
  if(kind()==1)return local.y>=1.1&&local.y<=5.5;
  return local.y>=3.75&&local.y<=6.2||local.z>=-5.8&&local.z<=-1.9&&local.y>=1.1&&local.y<=4.7;
 }
 void strikeOutside(Vec3d old,float oldYaw){
  var delta=getEntityPos().subtract(old);double velocity=delta.length();if(velocity<.025||velocity>2)return;
  var w=(ServerWorld)getEntityWorld();var sweep=RailGeometry.worldBox(new Box(-1.8,0,-7.5,1.8,4.3,7.5),old,oldYaw).union(getBoundingBox());
  lastImpact.entrySet().removeIf(e->age-e.getValue()>40);
  for(var entity:w.getOtherEntities(this,sweep,e->e instanceof LivingEntity&&e.isAlive()&&!e.isSpectator())){
   var victim=(LivingEntity)entity;
   if(RailPassengerNetwork.protectedPassenger(victim)||cabinPassenger(victim,old,oldYaw)||cabinPassenger(victim,getEntityPos(),getYaw()))continue;
   // A trailing car must never run over a passenger standing in the neighbouring car.
   if(nearby(w,victim.getBoundingBox().expand(4)).stream().anyMatch(t->t!=this&&t.cabinPassenger(victim,t.getEntityPos(),t.getYaw())))continue;
   if(age-lastImpact.getOrDefault(victim.getUuid(),-100)<10)continue;
   boolean hit=false;int steps=Math.max(1,(int)Math.ceil(velocity/.15));
   for(int i=0;i<=steps&&!hit;i++){var origin=old.lerp(getEntityPos(),i/(double)steps);float yaw=MathHelper.lerpAngleDegrees(i/(float)steps,oldYaw,getYaw());
    for(var cell:collisionCells())if(RailGeometry.worldBox(cell,origin,yaw).intersects(victim.getBoundingBox())){hit=true;break;}
   }
   if(!hit)continue;lastImpact.put(victim.getUuid(),age);
   victim.damage(w,w.getDamageSources().cramming(),(float)Math.min(30,velocity*90));
   var push=delta.normalize().multiply(Math.min(1.2,velocity*4));victim.setVelocity(push.x,.22,push.z);victim.velocityDirty=true;
   if(victim instanceof net.minecraft.server.network.ServerPlayerEntity player)player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
  }
 }
 public List<Box> collisions(Box query){return collisions(query,null);}
 public List<Box> collisions(Box query,Entity mover){
  var frame=clientFrame;boolean snapshot=getEntityWorld().isClient()&&frame!=null&&frame.train()==this&&frame.player()==mover;
  var origin=snapshot?frame.origin():getEntityPos();float yaw=snapshot?frame.yaw():getYaw();
  var localQuery=RailGeometry.worldBox(query.offset(origin.negate()),Vec3d.ZERO,-yaw);var cells=collisionCells();var out=new ArrayList<Box>();for(var cell:cells){if(!cell.intersects(localQuery))continue;var b=RailGeometry.worldBox(cell,origin,yaw);if(b.intersects(query))out.add(b);}return out;
 }
 @Override public boolean shouldRender(double squaredDistance){return squaredDistance<(isShip()?512*512:256*256);}
 @Override public boolean canHit(){return true;}
 @Override public boolean isCollidable(Entity other){return false;}
 @Override public boolean damage(ServerWorld w,DamageSource source,float amount){
  if(!isShip()||isRemoved()||isInvulnerable()||amount<=0)return false;
  if(source.getSource() instanceof NavalCannonballEntity ball&&getUuid().equals(ball.sourceShip))return false;
  hullHealth(hullHealth()-amount);
  if(hullHealth()>0){
   w.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,getX(),getY()+2,getZ(),10,2,.6,2,.04);
   return true;
  }
  for(var barrel:naval().barrels)for(var stack:barrel.getHeldStacks())if(!stack.isEmpty())dropStack(w,stack.copy());
  for(var stack:naval().cargo.getHeldStacks())if(!stack.isEmpty())dropStack(w,stack.copy());
  dropStack(w,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DARK_OAK_PLANKS,16));
  BlastEffects.burst(w,getEntityPos().add(0,1,0),BlastEffects.Kind.HULL);
  w.playSound(null,getX(),getY(),getZ(),net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),net.minecraft.sound.SoundCategory.BLOCKS,2.5f,.67f);
  w.playSound(null,getX(),getY(),getZ(),net.minecraft.sound.SoundEvents.BLOCK_WOOD_BREAK,net.minecraft.sound.SoundCategory.BLOCKS,2f,.58f);
  discard();
  return true;
 }
 @Override public ActionResult interact(PlayerEntity player,Hand hand){
  if(getEntityWorld().isClient())return ActionResult.SUCCESS;
  if(isShip())return NavalContent.interact(this,player,hand);
  if(kind()==0){var w=(ServerWorld)getEntityWorld();if(player.squaredDistanceTo(RailGeometry.rotate(RailDriver.CHAIR,getYaw()).add(getEntityPos()))>20){player.sendMessage(net.minecraft.text.Text.literal("Подойдите к креслу в кабине."),true);return ActionResult.FAIL;}if(RailDriver.seat(w,this)!=null){player.sendMessage(net.minecraft.text.Text.literal("Место машиниста занято."),true);return ActionResult.FAIL;}
   var anchor=RailContent.SEAT.create(w,SpawnReason.EVENT);if(anchor==null)return ActionResult.FAIL;anchor.owner=getUuid();anchor.local=RailDriver.CHAIR;anchor.control=true;anchor.setPosition(RailGeometry.rotate(anchor.local,getYaw()).add(getEntityPos()));w.spawnEntity(anchor);player.startRiding(anchor,true,false);anchor.syncToTrain();throttle=speed;dwell=0;return ActionResult.SUCCESS;}
  if(kind()!=1)return ActionResult.PASS;
  var local=RailGeometry.rotate(player.getEntityPos().subtract(getEntityPos()),-getYaw());
  if(isSleeper()&&player.isSneaking()&&player instanceof net.minecraft.server.network.ServerPlayerEntity p)return RailRest.begin(p,this);
  if(Math.abs(local.x)>1.4){return setDoors(!doorsOpen())?ActionResult.SUCCESS:ActionResult.FAIL;}
  if(isSleeper())return SleeperInteractions.interact(this,player,hand);
  Vec3d seat=seats().stream().min(Comparator.comparingDouble(p->p.squaredDistanceTo(RailGeometry.rotate(player.getEntityPos().subtract(getEntityPos()),-getYaw())))).orElseThrow();
  Vec3d goal=RailGeometry.rotate(seat,getYaw()).add(getEntityPos());
  if(!getEntityWorld().getOtherEntities(this,new Box(goal,goal).expand(.35),e->e instanceof RailSeatEntity&&e.hasPassengers()).isEmpty())return ActionResult.FAIL;
  var anchor=RailContent.SEAT.create((ServerWorld)getEntityWorld(),SpawnReason.EVENT);if(anchor==null)return ActionResult.FAIL;anchor.owner=getUuid();anchor.local=seat;anchor.setPosition(goal);getEntityWorld().spawnEntity(anchor);player.startRiding(anchor,true,false);return ActionResult.SUCCESS;
 }
 @Override protected void writeCustomData(WriteView v){if(isShip())NavalPhysics.write(this,v);v.putInt("kind",kind());v.putBoolean("sleeper",isSleeper());v.putInt("compartments",compartments());v.putString("path",dataTracker.get(PATH));v.putFloat("lane",dataTracker.get(LANE));v.putDouble("distance",distance);v.putDouble("speed",speed);v.putInt("direction",direction);v.putInt("dwell",dwell);v.putInt("car",car);v.putBoolean("driver_created",driverCreated);v.putLong("driver_vacant_since",driverVacantSince);v.putDouble("throttle",throttle);v.put("stops",com.mojang.serialization.Codec.DOUBLE.listOf(),stationStops);v.putInt("next_stop",nextStop);v.putInt("visits",visits);if(leader!=null)v.putString("leader",leader.toString());}
 @Override protected void readCustomData(ReadView v){dataTracker.set(KIND,Math.clamp(v.getInt("kind",0),0,3));dataTracker.set(PATH,v.getString("path",""));dataTracker.set(LANE,v.getFloat("lane",0));distance=v.getDouble("distance",40);speed=v.getDouble("speed",0);direction=v.getInt("direction",1);dwell=v.getInt("dwell",0);car=v.getInt("car",0);dataTracker.set(SLEEPER,v.getBoolean("sleeper",kind()==1&&car==2));dataTracker.set(COMPARTMENTS,v.getInt("compartments",7)&7);driverCreated=v.getBoolean("driver_created",true);driverVacantSince=v.getLong("driver_vacant_since",-1);throttle=v.getDouble("throttle",.72);stationStops=v.read("stops",com.mojang.serialization.Codec.DOUBLE.listOf()).orElse(List.of());nextStop=stationStops.isEmpty()?0:Math.floorMod(v.getInt("next_stop",0),stationStops.size());visits=v.getInt("visits",0);String id=v.getString("leader","");leader=id.isEmpty()?null:UUID.fromString(id);loadPath();updatePose();if(isShip()){NavalPhysics.read(this,v);setBoundingBox(RailGeometry.worldBox(localBounds(),getEntityPos(),getYaw()));}}
}
