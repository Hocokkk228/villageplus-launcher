package dev.halloween;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
final class NavalControlNetwork {
 record Input(float thrust,float rudder,boolean anchor,boolean sails) implements CustomPayload {
  static final Id<Input> ID=new Id<>(Identifier.of("halloween","naval_controls"));
  static final PacketCodec<RegistryByteBuf,Input> CODEC=PacketCodec.tuple(PacketCodecs.FLOAT,Input::thrust,PacketCodecs.FLOAT,Input::rudder,PacketCodecs.BOOLEAN,Input::anchor,PacketCodecs.BOOLEAN,Input::sails,Input::new);
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 static void register(){PayloadTypeRegistry.playC2S().register(Input.ID,Input.CODEC);ServerPlayNetworking.registerGlobalReceiver(Input.ID,(input,context)->context.server().execute(()->apply(context.player(),input)));}
 static void apply(net.minecraft.server.network.ServerPlayerEntity player,Input input){
  if(!(player.getVehicle() instanceof RailSeatEntity seat)||!seat.controls()||seat.train()==null||!seat.train().isShip()||!Float.isFinite(input.thrust)||!Float.isFinite(input.rudder))return;
  var ship=seat.train();var s=ship.naval();var w=player.getEntityWorld();s.lastControl=w.getTime();s.rudder=Math.clamp(input.rudder,-1,1);s.throttle=Math.clamp(s.throttle+Math.clamp(input.thrust,-1,1)*.012,-.3,1);
  if(input.anchor){if(ship.anchored()&&NavalPhysics.gangwayOccupied(w,ship))player.sendMessage(Text.literal("На трапе пассажир — дождитесь посадки."),true);else{boolean anchored=!ship.anchored();ship.navalFlags(anchored,ship.sailsRaised(),anchored&&Math.abs(ship.speed)<.025);if(anchored)s.throttle=0;w.playSound(null,ship.getX(),ship.getY(),ship.getZ(),SoundEvents.BLOCK_CHAIN_PLACE,SoundCategory.BLOCKS,.8f,.7f);}}
  if(input.sails){ship.navalFlags(ship.anchored(),!ship.sailsRaised(),ship.gangwayDeployed());w.playSound(null,ship.getX(),ship.getY(),ship.getZ(),SoundEvents.BLOCK_WOOL_PLACE,SoundCategory.BLOCKS,.8f,.75f);}
  if(player.age%20==0)player.sendMessage(Text.literal((ship.anchored()?"⚓ Якорь":"⛵ В пути")+" · "+Math.round(Math.abs(ship.speed)*38.88)+" уз. · Корпус "+Math.round(ship.hullHealth())+" · W/S — ход · A/D — руль · пробел — якорь · V — паруса"+(s.blocked?" · Путь перекрыт":"")),true);
 }
}
