package dev.halloween.mixin.client;

import dev.halloween.FightPose;
import dev.halloween.KnockdownRenderState;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class KnockdownPlayerRendererMixin {
    @Inject(method="updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",at=@At("TAIL"))
    private void halloween$readKnockdown(PlayerLikeEntity player,PlayerEntityRenderState state,float delta,CallbackInfo ci){
        var combat=FightPose.of(player);
        int phase=combat.halloween$fightPose();
        float progress=state.deathTime>0?0:FightPose.recline(phase,combat.halloween$fightAge(delta));
        ((KnockdownRenderState)state).halloween$recline(progress);
        if(!FightPose.down(phase)||state.deathTime>0)return;
        state.pose=EntityPose.STANDING;
        state.bodyYaw=combat.halloween$knockdownYaw();
        state.relativeHeadYaw*=1-progress*.8f;
        state.pitch*=1-progress;
        state.isSwimming=false;
        state.leaningPitch=0;
        state.isGliding=false;
        state.isInSneakingPose=false;
        state.limbSwingAmplitude=0;
        state.handSwingProgress=0;
        state.rightArmPose=BipedEntityModel.ArmPose.EMPTY;
        state.leftArmPose=BipedEntityModel.ArmPose.EMPTY;
    }

    @Inject(method="setupTransforms(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;FF)V",at=@At("TAIL"))
    private void halloween$rotateOntoBack(PlayerEntityRenderState state,MatrixStack matrices,float yaw,float scale,CallbackInfo ci){
        float progress=((KnockdownRenderState)state).halloween$recline();
        if(progress<=0)return;
        matrices.translate(0,.24f*progress,0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90*progress));
    }
}
