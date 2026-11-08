package dev.halloween.mixin.client;

import dev.halloween.PlayerWeaponMotion;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.ModelWithArms;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemFeatureRenderer.class)
public abstract class PlayerWeaponGripMixin {
    @Inject(method="renderItem",at=@At("HEAD"),cancellable=true)
    private void halloween$grip(ArmedEntityRenderState state, ItemRenderState item, ItemStack stack, Arm arm,
                               MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState)) return;
        var weapon = PlayerWeaponMotion.weapon(stack);
        if (weapon == PlayerWeaponMotion.Weapon.NONE || item.isEmpty()) return;
        var model = ((FeatureRenderer<?, ?>)(Object)this).getContextModel();
        if (!(model instanceof ModelWithArms)) return;
        matrices.push();
        // The player's arm transform also contains the slim-skin offset.
        ((ModelWithArms)model).setArmAngle(state, arm, matrices);
        float palm = arm == Arm.LEFT ? .0625f : -.0625f;
        if (weapon == PlayerWeaponMotion.Weapon.MUSKET) {
            matrices.translate(palm, 1.068f, -.002f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
        } else if (weapon == PlayerWeaponMotion.Weapon.REVOLVER) {
            matrices.translate(palm, .697f, -.134f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
            matrices.scale(.66f, .66f, .66f);
        } else {
            matrices.translate(palm, .59f, -.069f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-125));
        }
        item.render(matrices, queue, light, OverlayTexture.DEFAULT_UV, state.outlineColor);
        matrices.pop();
        ci.cancel();
    }
}
