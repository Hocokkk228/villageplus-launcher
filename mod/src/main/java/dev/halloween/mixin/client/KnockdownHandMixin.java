package dev.halloween.mixin.client;

import dev.halloween.FightPose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class KnockdownHandMixin {
    @Inject(method="renderHand",at=@At("HEAD"),cancellable=true)
    private void halloween$hideStandingHand(float tickDelta,boolean sleeping,Matrix4f projection,CallbackInfo ci){
        var player=MinecraftClient.getInstance().player;
        if(player!=null&&player.isAlive()&&FightPose.down(FightPose.of(player).halloween$fightPose()))ci.cancel();
    }
}
