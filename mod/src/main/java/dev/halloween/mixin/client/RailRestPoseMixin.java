package dev.halloween.mixin.client;
import dev.halloween.RailSeatEntity;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerEntityRenderer.class)
public abstract class RailRestPoseMixin {
 @Inject(method="updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",at=@At("TAIL"))
 private void sleepingBerth(PlayerLikeEntity player,PlayerEntityRenderState state,float delta,CallbackInfo ci){if(player.getVehicle() instanceof RailSeatEntity seat&&seat.resting()){state.pose=net.minecraft.entity.EntityPose.SLEEPING;state.sleepingDirection=net.minecraft.util.math.Direction.fromHorizontalDegrees(seat.getYaw()+90);state.hasVehicle=false;state.isInSneakingPose=false;state.limbSwingAmplitude=0;state.rightArmPose=net.minecraft.client.render.entity.model.BipedEntityModel.ArmPose.EMPTY;state.leftArmPose=net.minecraft.client.render.entity.model.BipedEntityModel.ArmPose.EMPTY;}}
}
