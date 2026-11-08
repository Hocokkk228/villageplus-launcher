package dev.halloween;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
/** Control packets are accepted only from the player actually occupying this engine's control chair. */
final class RailControlNetwork {
 record Input(float throttle,boolean brake) implements CustomPayload {
  static final Id<Input> ID=new Id<>(Identifier.of("halloween","rail_controls"));
  static final PacketCodec<RegistryByteBuf,Input> CODEC=PacketCodec.tuple(PacketCodecs.FLOAT,Input::throttle,PacketCodecs.BOOLEAN,Input::brake,Input::new);
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 static void register(){PayloadTypeRegistry.playC2S().register(Input.ID,Input.CODEC);ServerPlayNetworking.registerGlobalReceiver(Input.ID,(m,c)->c.server().execute(()->apply(c.player(),m)));}
 static void apply(net.minecraft.server.network.ServerPlayerEntity p,Input m){
  if(!(p.getVehicle() instanceof RailSeatEntity s)||!s.controls()||!(s.train() instanceof RailVehicleEntity t)||t.kind()!=0||!Float.isFinite(m.throttle))return;
  t.manualInput=Math.clamp(m.throttle,-1,1);t.manualBrake=m.brake;t.manualAt=p.getEntityWorld().getTime();
 }
}
