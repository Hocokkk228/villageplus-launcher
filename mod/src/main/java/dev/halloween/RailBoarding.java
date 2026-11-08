package dev.halloween;
import java.util.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
/** Boarding is ordinary movement through an open side entrance, followed by mounting a free seat. */
final class RailBoarding {
 private static final class Boarding {final RailVehicleEntity train;final Vec3d seat;final int side;final double door;final long until;int stage;
  Boarding(RailVehicleEntity train,Vec3d seat,int side,double door,long until){this.train=train;this.seat=seat;this.side=side;this.door=door;this.until=until;}}
 private static final Map<VillagerEntity,Boarding> QUEUE=new WeakHashMap<>();
 private static final class Exit {final RailVehicleEntity train;final int side;final double door;int stage;final long until;Exit(RailVehicleEntity t,int side,double door,long until){this.train=t;this.side=side;this.door=door;this.until=until;}}
 private static final Map<VillagerEntity,Exit> EXIT=new WeakHashMap<>();
 static boolean queued(VillagerEntity v){return QUEUE.containsKey(v)||EXIT.containsKey(v);}
 static void leave(ServerWorld w,RailVehicleEntity train,VillagerEntity v){var local=RailPassengerNetwork.toLocal(train,v.getEntityPos());int side=local.x<0?-1:1;var station=RailwayNetwork.get(w).stations.stream().min(Comparator.comparingDouble(s->s.center(32).squaredDistanceTo(train.getEntityPos()))).orElse(null);
  if(station!=null){var sidePoint=train.getEntityPos().add(RailGeometry.rotate(new Vec3d(side*3,1.5,0),train.getYaw()));var q=BlockPos.ofFloored(sidePoint);if(w.getBlockState(q.down()).getCollisionShape(w,q.down()).isEmpty())side=-side;}
  if(train.isSleeper())train.openCompartment(local.z);v.stopRiding();EXIT.put(v,new Exit(train,side,local.z<0?-5.5:5.5,w.getTime()+600));
 }

 static boolean crossing(RailVehicleEntity train){return QUEUE.values().stream().anyMatch(b->b.train==train)||EXIT.values().stream().anyMatch(b->b.train==train);}
 static boolean reserved(RailVehicleEntity train,Vec3d seat){return QUEUE.values().stream().anyMatch(b->b.train==train&&b.seat.equals(seat));}
 static boolean free(ServerWorld w,RailVehicleEntity train,Vec3d seat){var goal=RailGeometry.rotate(seat,train.getYaw()).add(train.getEntityPos());return !reserved(train,seat)&&w.getOtherEntities(train,new Box(goal,goal).expand(.4),e->e instanceof RailSeatEntity&&e.hasPassengers()).isEmpty();}
 static boolean invite(ServerWorld w,RailVehicleEntity train,VillagerEntity person){
  if(!train.doorsOpen()||RailDriver.isDriver(person)||person.hasVehicle()||QUEUE.containsKey(person)||person.isBaby())return false;
  var local=RailPassengerNetwork.toLocal(train,person.getEntityPos());int side=local.x<0?-1:1;double door=local.z<0?-5.5:5.5;
  var seat=train.seats().stream().filter(s->free(w,train,s)).min(Comparator.comparingDouble(s->s.squaredDistanceTo(new Vec3d(side,2,door)))).orElse(null);if(seat==null)return false;
  if(train.isSleeper())train.openCompartment(seat.z);QUEUE.put(person,new Boarding(train,seat,side,door,w.getTime()+400));return true;
 }
 static void tick(ServerWorld w,RailVehicleEntity train){
  if(train.kind()!=1)return;
  for(var entry:List.copyOf(EXIT.entrySet())){var v=entry.getKey();var b=entry.getValue();if(b.train!=train)continue;if(!v.isAlive()||train.isRemoved()||w.getTime()>b.until||Math.abs(train.speed)>.025){EXIT.remove(v);continue;}
   if(!train.doorsOpen())continue;VillageTasks.hold(v);v.getNavigation().stop();
   var local=train.isSleeper()?(b.stage==0?new Vec3d(1.04,1.5,RailGeometry.compartmentCenter(RailPassengerNetwork.toLocal(train,v.getEntityPos()).z)):b.stage==1?new Vec3d(1.04,1.5,b.door):new Vec3d(b.side*3.1,1.85,b.door)):(b.stage==0?new Vec3d(0,1.5,b.door):new Vec3d(b.side*3.1,1.85,b.door));var goal=RailGeometry.rotate(local,train.getYaw()).add(train.getEntityPos());var delta=goal.subtract(v.getEntityPos());
   if(Math.hypot(delta.x,delta.z)<.3){if(++b.stage>(train.isSleeper()?2:1)){EXIT.remove(v);v.addCommandTag("halloween_rail_arrived");v.removeCommandTag("halloween_rail_escape");}continue;}
   double d=Math.hypot(delta.x,delta.z);v.setVelocity(delta.x/d*.11,v.getVelocity().y,delta.z/d*.11);if(delta.y>.25&&v.isOnGround())v.getJumpControl().setActive();
  }

  if(train.doorsOpen()&&train.age%20==0&&QUEUE.values().stream().filter(b->b.train==train).count()<6){
   var localArea=RailGeometry.worldBox(new Box(-7,.5,-12,7,3.8,12),train.getEntityPos(),train.getYaw());
   var person=w.getEntitiesByClass(VillagerEntity.class,localArea,v->v.isAlive()&&!v.isBaby()&&!v.hasVehicle()&&!v.isTouchingWater()&&!v.isAiDisabled()&&!QUEUE.containsKey(v)&&!EXIT.containsKey(v)&&!v.getCommandTags().contains("halloween_rail_arrived")&&!VillageProjects.owns(v)&&!VillageTrials.busy(v)&&!VillageDefense.isGuard(v)&&!RoyalProfession.isKing(v)&&!v.getCommandTags().contains("halloween_prisoner")).stream().findFirst().orElse(null);
   if(person!=null)invite(w,train,person);
  }
  for(var entry:List.copyOf(QUEUE.entrySet())){
   var v=entry.getKey();var b=entry.getValue();if(b.train!=train)continue;
   if(!v.isAlive()||v.hasVehicle()||train.isRemoved()||w.getTime()>b.until||!train.doorsOpen()){QUEUE.remove(v);continue;}
   if(v.isSleeping())v.wakeUp();VillageTasks.hold(v);v.setSneaking(false);v.getNavigation().stop();
   Vec3d target=train.isSleeper()?(b.stage==0?new Vec3d(b.side*2.25,1.4,b.door):b.stage==1?new Vec3d(1.04,1.5,b.door):b.stage==2?new Vec3d(1.04,1.5,RailGeometry.compartmentCenter(b.seat.z)):b.stage==3?new Vec3d(-.3,1.5,RailGeometry.compartmentCenter(b.seat.z)):new Vec3d(b.seat.x,1.5,RailGeometry.compartmentCenter(b.seat.z)+Math.signum(b.seat.z-RailGeometry.compartmentCenter(b.seat.z))*.42)):b.stage==0?new Vec3d(b.side*2.25,1.4,b.door):b.stage==1?new Vec3d(0,1.5,b.door):new Vec3d(0,1.5,b.seat.z);
   var goal=RailGeometry.rotate(target,train.getYaw()).add(train.getEntityPos());var delta=goal.subtract(v.getEntityPos());
   if(Math.hypot(delta.x,delta.z)<.3&&Math.abs(delta.y)<.65){b.stage++;if(b.stage>(train.isSleeper()?4:2)){
     var anchor=RailContent.SEAT.create(w,SpawnReason.EVENT);if(anchor!=null){anchor.owner=train.getUuid();anchor.local=b.seat;anchor.setPosition(RailGeometry.rotate(b.seat,train.getYaw()).add(train.getEntityPos()));w.spawnEntity(anchor);v.startRiding(anchor,true,false);}QUEUE.remove(v);continue;
   }}
   double distance=Math.hypot(delta.x,delta.z);if(distance>.06){double scale=Math.min(.11,distance)/distance;var velocity=v.getVelocity();v.setVelocity(delta.x*scale,velocity.y,delta.z*scale);v.velocityDirty=true;}
   if(delta.y>.3&&v.isOnGround()&&w.isSpaceEmpty(v,v.getBoundingBox().offset(0,1,0)))v.getJumpControl().setActive();
  }
 }
 private RailBoarding(){}
}
