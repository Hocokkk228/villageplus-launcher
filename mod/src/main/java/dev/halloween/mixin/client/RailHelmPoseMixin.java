package dev.halloween.mixin.client;
import dev.halloween.RailSeatEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BipedEntityModel.class)
public abstract class RailHelmPoseMixin {
 @Inject(method="setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",at=@At("TAIL"))
 private void helmHands(BipedEntityRenderState state,CallbackInfo ci){var c=MinecraftClient.getInstance();if(c.world==null||!(state instanceof net.minecraft.client.render.entity.state.PlayerEntityRenderState player))return;var e=c.world.getEntityById(player.id);if(e!=null&&e.getVehicle() instanceof RailSeatEntity seat&&seat.controls()&&seat.train()!=null&&seat.train().isShip()){var m=(BipedEntityModel<?>)(Object)this;m.rightArm.pitch=-1.25f;m.leftArm.pitch=-1.25f;m.rightArm.yaw=-.18f;m.leftArm.yaw=.18f;}}
}
