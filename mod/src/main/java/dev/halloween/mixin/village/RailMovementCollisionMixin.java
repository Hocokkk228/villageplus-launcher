package dev.halloween.mixin.village;
import dev.halloween.RailVehicleEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Lithium replaces the ordinary entity-collision lookup. Keep moving geometry in the final motion solver too. */
@Mixin(value=Entity.class,priority=900)
public abstract class RailMovementCollisionMixin {
 @Inject(method="adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",at=@At("RETURN"),cancellable=true)
 private void railwayMovement(Vec3d requested,CallbackInfoReturnable<Vec3d> cir){
  var e=(Entity)(Object)this;if(e instanceof RailVehicleEntity)return;
  var motion=cir.getReturnValue();var box=e.getBoundingBox();var area=box.stretch(motion).expand(1e-7);var trains=RailVehicleEntity.nearby(e.getEntityWorld(),area);if(trains.isEmpty())return;
  var shapes=new java.util.ArrayList<VoxelShape>();for(var t:trains)for(var b:t.collisions(area,e))shapes.add(VoxelShapes.cuboid(b));if(shapes.isEmpty())return;
  double x=motion.x,y=motion.y,z=motion.z;
  y=VoxelShapes.calculateMaxOffset(Direction.Axis.Y,box,shapes,y);box=box.offset(0,y,0);
  boolean zFirst=Math.abs(x)<Math.abs(z);
  if(zFirst){z=VoxelShapes.calculateMaxOffset(Direction.Axis.Z,box,shapes,z);box=box.offset(0,0,z);}
  x=VoxelShapes.calculateMaxOffset(Direction.Axis.X,box,shapes,x);box=box.offset(x,0,0);
  if(!zFirst)z=VoxelShapes.calculateMaxOffset(Direction.Axis.Z,box,shapes,z);
  cir.setReturnValue(new Vec3d(x,y,z));
 }
}
