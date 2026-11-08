package dev.halloween.mixin.client;
import dev.halloween.RailSeatEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityRenderer.class)
public abstract class RailSeatRenderMixin {
 @Inject(method="updateRenderState",at=@At("TAIL"))
 private void carriageFrame(Entity passenger,EntityRenderState state,float delta,CallbackInfo ci){if(passenger.getVehicle() instanceof RailSeatEntity seat&&seat.train()!=null){var pos=seat.renderedPassengerPos(passenger,delta);state.x=pos.x;state.y=pos.y;state.z=pos.z;}else if(passenger.getVehicle() instanceof dev.halloween.IndustrialVehicleEntity vehicle){var pos=vehicle.renderedPassengerPos(passenger,delta);state.x=pos.x;state.y=pos.y;state.z=pos.z;}}
}
