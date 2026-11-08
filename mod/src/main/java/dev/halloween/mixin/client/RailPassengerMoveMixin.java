package dev.halloween.mixin.client;
import dev.halloween.RailPassengerClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayerEntity.class)
public abstract class RailPassengerMoveMixin {
 @Inject(method="sendMovementPackets",at=@At("HEAD"),cancellable=true)
 private void carriageCoordinates(CallbackInfo ci){if(RailPassengerClient.aboard((ClientPlayerEntity)(Object)this))ci.cancel();}
}
