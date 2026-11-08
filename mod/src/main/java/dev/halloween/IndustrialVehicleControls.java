package dev.halloween;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
/** Only the recorded driver controls a vehicle; stale inputs brake instead of accelerating forever. */
final class IndustrialVehicleControls {
 record Input(float throttle,float steering,boolean brake,boolean doors) implements CustomPayload {
  static final Id<Input> ID=new Id<>(Identifier.of("halloween","industrial_controls"));
  static final PacketCodec<RegistryByteBuf,Input> CODEC=PacketCodec.tuple(PacketCodecs.FLOAT,Input::throttle,PacketCodecs.FLOAT,Input::steering,PacketCodecs.BOOLEAN,Input::brake,PacketCodecs.BOOLEAN,Input::doors,Input::new);
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 static void register(){PayloadTypeRegistry.playC2S().register(Input.ID,Input.CODEC);ServerPlayNetworking.registerGlobalReceiver(Input.ID,(m,c)->c.server().execute(()->apply(c.player(),m)));}
 static void apply(net.minecraft.server.network.ServerPlayerEntity p,Input m){
  if(!(p.getVehicle() instanceof IndustrialVehicleEntity v)||!v.isDriver(p)||!Float.isFinite(m.throttle)||!Float.isFinite(m.steering))return;
  v.input=Math.clamp(m.throttle,-1,1);v.steering=Math.clamp(m.steering,-1,1);v.brake=m.brake;v.lastControl=p.getEntityWorld().getTime();
  if(m.doors&&Math.abs(v.speed)<.03&&p.getEntityWorld().getTime()-v.lastDoor>8){v.doors(!v.doors());v.lastDoor=p.getEntityWorld().getTime();}
 }
 private IndustrialVehicleControls(){}
}
