package dev.halloween.mixin.client;

import dev.halloween.PlayerWeaponMotion;
import dev.halloween.PlayerWeaponState;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerWeaponRendererMixin {
    @Inject(method="updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at=@At("TAIL"))
    private void halloween$shot(PlayerLikeEntity entity, PlayerEntityRenderState state, float delta, CallbackInfo ci) {
        ((PlayerWeaponState)state).halloween$recoil(PlayerWeaponMotion.recoil(entity, delta));
    }
}
