package dev.halloween.mixin.client;

import dev.halloween.PlayerWeaponMotion;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class PlayerWeaponModelMixin {
    @Inject(method="setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at=@At("TAIL"))
    private void halloween$weapon(BipedEntityRenderState state, CallbackInfo ci) {
        PlayerWeaponMotion.apply((BipedEntityModel<?>)(Object)this, state);
    }
}
