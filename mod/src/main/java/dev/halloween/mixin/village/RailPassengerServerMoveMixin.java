package dev.halloween.mixin.village;
import dev.halloween.RailPassengerNetwork;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayNetworkHandler.class)
public abstract class RailPassengerServerMoveMixin implements RailPassengerNetwork.HandlerState {
 @Shadow private net.minecraft.util.math.Vec3d requestedTeleportPos;
 @Override public boolean railwayTeleportPending(){return requestedTeleportPos!=null;}
 @Shadow public ServerPlayerEntity player;
 @Inject(method="requestTeleport(Lnet/minecraft/entity/EntityPosition;Ljava/util/Set;)V",at=@At("TAIL"))
 private void shipTeleportFrame(net.minecraft.entity.EntityPosition position,java.util.Set<net.minecraft.network.packet.s2c.play.PositionFlag> flags,CallbackInfo ci){RailPassengerNetwork.teleportFrame(player);}
 @Inject(method="onPlayerMove",at=@At("HEAD"),cancellable=true)
 private void ignoreOldWorldFrame(PlayerMoveC2SPacket packet,CallbackInfo ci){
  // Packet threading still follows vanilla until execution reaches the server thread.
  if(player.getEntityWorld().getServer().isOnThread()&&RailPassengerNetwork.active(player))ci.cancel();
 }
}
