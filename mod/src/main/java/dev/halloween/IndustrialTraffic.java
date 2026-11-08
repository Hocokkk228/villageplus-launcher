package dev.halloween;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.village.VillagerProfession;
/** Drivers follow finished city streets through ordinary physical movement; bus riders are actual villagers. */
final class IndustrialTraffic {
 static RegistryEntry<VillagerProfession> DRIVER;
 static void register(){var profession=Registry.register(Registries.VILLAGER_PROFESSION,net.minecraft.util.Identifier.of("halloween","motor_driver"),new VillagerProfession(Text.translatable("entity.halloween.motor_driver"),e->false,e->false,com.google.common.collect.ImmutableSet.of(),com.google.common.collect.ImmutableSet.of(),net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_WORK_TOOLSMITH));DRIVER=Registries.VILLAGER_PROFESSION.getEntry(profession);}
 static boolean available(VillagerEntity v){return v.isAlive()&&!v.isBaby()&&!v.isAiDisabled()&&!v.hasVehicle()&&!VillageTrials.busy(v)&&!VillageDefense.isGuard(v)&&!IndustrialPlants.owns(v)&&!RailWorks.owns(v)&&!NavalWorks.owns(v)&&!VillageProjects.owns(v)&&!RailDriver.isDriver(v);}
 static void control(ServerWorld w,IndustrialVehicleEntity v){
  if(v.streetId.isEmpty()||!(v.driver() instanceof VillagerEntity))return;
  var street=CityStreets.get(w).streets.stream().filter(s->s.id.equals(v.streetId)).findFirst().orElse(null);if(street==null){v.brake=true;return;}
  var path=street.loop();double at=street.nearest(v.getEntityPos(),v.streetDistance);v.streetDistance=at;v.lastControl=w.getTime();
  if(v.kind()==IndustrialMachines.Kind.BUS){
   var stops=street.stops();v.nextStop=Math.floorMod(v.nextStop,stops.size());double ahead=path.wrap(stops.get(v.nextStop)-at);
   if(v.dwellUntil>w.getTime()){v.input=0;v.brake=true;if(!v.doors())v.doors(true);if(w.getTime()%20==0)passengers(w,v);return;}
   if(v.dwellUntil>0){v.dwellUntil=0;v.nextStop=(v.nextStop+1)%stops.size();v.visits++;if(v.doors())v.doors(false);ahead=path.wrap(stops.get(v.nextStop)-at);}
   if(ahead<1.4||ahead>path.length-1){
    v.input=0;
    v.brake=true;
    if(Math.abs(v.speed)<.025){v.dwellUntil=w.getTime()+100;v.doors(true);}
    return;
   }
  }
  if(v.doors()){if(v.dwellUntil>w.getTime()){v.input=0;v.brake=true;return;}v.doors(false);}
  var target=path.center(at+3.8);var delta=target.subtract(v.getEntityPos());float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));double turn=MathHelper.wrapDegrees(yaw-v.getYaw());
  v.steering=Math.clamp(turn/25,-1,1);
  v.input=Math.abs(turn)>60?.32:Math.abs(turn)>28?.52:.8;
  if(v.kind()==IndustrialMachines.Kind.BUS){
   double remaining=path.wrap(street.stops().get(v.nextStop)-at);
   v.input=Math.min(v.input,Math.sqrt(.035*Math.max(0,remaining-.7))/v.kind().maxSpeed);
  }
  v.brake=false;
  var aheadBox=v.getBoundingBox().stretch(RailGeometry.rotate(new Vec3d(0,0,10),v.getYaw()));
  for(var other:w.getOtherEntities(v,aheadBox,e->e instanceof IndustrialVehicleEntity)){
   var local=RailGeometry.rotate(other.getEntityPos().subtract(v.getEntityPos()),-v.getYaw());var car=(IndustrialVehicleEntity)other;
   if(local.z>0&&local.z<(v.kind().length+car.kind().length)/2+5&&Math.abs(local.x)<(v.kind().width+car.kind().width)/2+.1){v.brake=true;break;}
  }
  var next=BlockPos.ofFloored(target);if(!w.isChunkLoaded(next)||!w.isChunkLoaded(next.east(5))||!w.isChunkLoaded(next.west(5))){v.input=0;v.brake=true;}
 }
 static void passengers(ServerWorld w,IndustrialVehicleEntity bus){
  if(!bus.doors()||Math.abs(bus.speed)>.025)return;
  if(bus.lastPassengerExchange!=bus.dwellUntil&&bus.visits>0){
   int leaving=0;
   for(var rider:List.copyOf(bus.getPassengerList())){
    if(!(rider instanceof VillagerEntity villager)||bus.isDriver(rider)||leaving>=4)continue;
    var at=alightPosition(w,bus,villager);
    if(at==null)continue;
    rider.stopRiding();
    rider.setPosition(at);
    for(var tag:List.copyOf(rider.getCommandTags()))if(tag.startsWith("halloween_bus_arrival_"))rider.removeCommandTag(tag);
    rider.addCommandTag("halloween_bus_arrival_"+w.getTime());
    leaving++;
   }
  }
  bus.lastPassengerExchange=bus.dwellUntil;int entered=0,approaching=0;for(var person:w.getEntitiesByClass(VillagerEntity.class,bus.getBoundingBox().expand(12,3,12),IndustrialTraffic::available)){
   if(person.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_bus_arrival_")&&recent(t,w.getTime())))continue;
   var entrance=bus.getEntityPos().add(RailGeometry.rotate(new Vec3d(bus.kind().width/2+.9,0,bus.kind().length*.25),bus.getYaw()));
   if(person.getEntityPos().squaredDistanceTo(entrance)>9){IndustrialNavigation.move(w,person,BlockPos.ofFloored(entrance));if(++approaching>=4)break;continue;}
   if(bus.board(person,false)&&++entered>=4)break;
  }
 }
 private static Vec3d alightPosition(ServerWorld world,IndustrialVehicleEntity bus,VillagerEntity rider){
  var street=CityStreets.get(world).streets.stream().filter(s->s.id.equals(bus.streetId)).findFirst().orElse(null);
  if(street==null)return bus.updatePassengerForDismount(rider);
  var path=street.loop();
  for(int distance:new int[]{4,5,6,8})for(int side:new int[]{1,-1})for(double along:new double[]{2,0,-2}){
   var candidate=bus.getEntityPos().add(RailGeometry.rotate(new Vec3d(side*distance,0,along),bus.getYaw()));
   for(int dy:new int[]{0,1,-1}){
    var pos=BlockPos.ofFloored(candidate).up(dy);
    if(!world.isChunkLoaded(pos)||!world.getBlockState(pos.down()).isSideSolidFullSquare(world,pos.down(),Direction.UP))continue;
    var at=Vec3d.ofBottomCenter(pos);
    if(path.center(path.closest(at)).squaredDistanceTo(at)<3.3*3.3)continue;
    var box=rider.getBoundingBox().offset(at.subtract(rider.getEntityPos()));
    if(world.isSpaceEmpty(rider,box)&&world.getOtherEntities(rider,box,e->e instanceof LivingEntity).isEmpty())return at;
   }
  }
  return null;
 }
 static boolean recent(String tag,long time){try{return time-Long.parseLong(tag.substring("halloween_bus_arrival_".length()))<300;}catch(NumberFormatException e){return false;}}
 private IndustrialTraffic(){}
}
