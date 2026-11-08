package dev.halloween.mixin.client;

import dev.halloween.KnockdownRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public abstract class KnockdownRenderStateMixin implements KnockdownRenderState {
    @Unique private float halloween$recline;
    public float halloween$recline(){return halloween$recline;}
    public void halloween$recline(float angle){halloween$recline=angle;}
}
