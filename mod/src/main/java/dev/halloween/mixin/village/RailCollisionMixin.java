package dev.halloween.mixin.village;
import dev.halloween.RailVehicleEntity;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.*;
import net.minecraft.world.World;
import net.minecraft.world.EntityView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Append individual floor/wall shapes, never a solid bounding box filling the passenger cabin. */
@Mixin(EntityView.class)
public interface RailCollisionMixin {
 @Inject(method="getEntityCollisions",at=@At("RETURN"),cancellable=true)
 private void railwayShapes(Entity entity,Box box,CallbackInfoReturnable<List<VoxelShape>> cir){
  if(entity instanceof RailVehicleEntity||!((Object)this instanceof World world))return;var trains=RailVehicleEntity.nearby(world,box.expand(1));if(trains.isEmpty())return;var result=new ArrayList<>(cir.getReturnValue());
  for(var other:trains)for(var shape:other.collisions(box,entity))result.add(VoxelShapes.cuboid(shape));
  cir.setReturnValue(result);
 }
}
