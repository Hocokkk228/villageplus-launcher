package dev.halloween.mixin.client;

import dev.halloween.GuardRiderState;
import dev.halloween.VillagerGrip;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.VillagerHeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ItemHolderEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerHeldItemFeatureRenderer.class)
public abstract class GuardRiderItemMixin {
    @Inject(method="render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ItemHolderEntityRenderState;FF)V",at=@At("RETURN"))
    private void halloween$offhand(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                    ItemHolderEntityRenderState state, float yaw, float pitch, CallbackInfo ci) {
        if (!(state instanceof GuardRiderState render) || render.halloween$offhand().isEmpty()) return;
        if (!(((FeatureRenderer<?, ?>)(Object)this).getContextModel() instanceof VillagerGrip grip)) return;
        matrices.push();
        if (grip.halloween$grip(matrices, true)) {
            render.halloween$offhand().render(matrices, queue, light, OverlayTexture.DEFAULT_UV, 0);
        }
        matrices.pop();
    }

    @Inject(method="applyTransforms",at=@At("HEAD"),cancellable=true)
    private void halloween$mainHand(ItemHolderEntityRenderState state, MatrixStack matrices, CallbackInfo ci) {
        if (((FeatureRenderer<?, ?>)(Object)this).getContextModel() instanceof VillagerGrip grip
                && grip.halloween$grip(matrices, false)) ci.cancel();
    }
}
