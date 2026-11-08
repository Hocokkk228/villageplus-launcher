package dev.halloween.mixin.client;
import dev.halloween.RailPassengerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** A real server teleport replaces the old carriage reference instead of being undone next tick. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class RailPassengerTeleportMixin {
 @Inject(method="onPlayerPositionLook",at=@At("HEAD"))
 private void changeReference(PlayerPositionLookS2CPacket packet,CallbackInfo ci){if(MinecraftClient.getInstance().isOnThread())RailPassengerClient.resetReference();}
}
