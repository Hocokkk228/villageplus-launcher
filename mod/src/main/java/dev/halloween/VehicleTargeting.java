package dev.halloween;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
/** A large vehicle's empty interior must not swallow clicks aimed at its crew or furniture. */
public final class VehicleTargeting {
 public static EntityHitResult raycast(Entity camera,Vec3d start,Vec3d end,Box area,Predicate<Entity> predicate,double rangeSquared){
  var vehicles=RailVehicleEntity.nearby(camera.getEntityWorld(),area);
  if(vehicles.isEmpty())return ProjectileUtil.raycast(camera,start,end,area,predicate,rangeSquared);
  var result=ProjectileUtil.raycast(camera,start,end,area,e->!(e instanceof RailVehicleEntity)&&predicate.test(e),rangeSquared);
  double nearest=result==null?rangeSquared:result.getPos().squaredDistanceTo(start);
  for(var vehicle:vehicles){if(!predicate.test(vehicle))continue;for(var box:vehicle.collisions(area.expand(1e-6))){var hit=box.contains(start)?java.util.Optional.of(start):box.raycast(start,end);if(hit.isEmpty())continue;double distance=hit.get().squaredDistanceTo(start);if(distance<=nearest){nearest=distance;result=new EntityHitResult(vehicle,hit.get());}}}
  return result;
 }
 private VehicleTargeting(){}
}
