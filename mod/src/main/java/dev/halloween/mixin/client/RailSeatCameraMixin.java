package dev.halloween.mixin.client;
import dev.halloween.RailSeatEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(Camera.class)
public abstract class RailSeatCameraMixin {
 @ModifyArgs(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/Camera;setPos(DDD)V"))
 private void carriageFrame(Args args,World world,Entity passenger,boolean thirdPerson,boolean inverse,float delta){
  if(passenger.getVehicle() instanceof dev.halloween.IndustrialVehicleEntity vehicle){var pos=vehicle.renderedPassengerPos(passenger,delta);double eyes=(double)args.get(1)-MathHelper.lerp(delta,passenger.lastY,passenger.getY());args.set(0,pos.x);args.set(1,pos.y+eyes);args.set(2,pos.z);return;}
  if(!(passenger.getVehicle() instanceof RailSeatEntity seat)||seat.train()==null)return;
  var pos=seat.renderedPassengerPos(passenger,delta);double eyes=(double)args.get(1)-MathHelper.lerp(delta,passenger.lastY,passenger.getY());if(seat.resting())eyes=.22;args.set(0,pos.x);args.set(1,pos.y+eyes);args.set(2,pos.z);
 }
}
