package dev.halloween.mixin.client;

import dev.halloween.PlayerWeaponState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public abstract class PlayerWeaponStateMixin implements PlayerWeaponState {
    @Unique private float halloween$recoil;
    public float halloween$recoil() { return halloween$recoil; }
    public void halloween$recoil(float value) { halloween$recoil = value; }
}
