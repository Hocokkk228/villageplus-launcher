package dev.halloween;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.*;
final class MusketNetwork {
 record Fire() implements CustomPayload {
  static final Id<Fire> ID=new Id<>(Identifier.of("halloween","musket_fire"));
  static final PacketCodec<RegistryByteBuf,Fire> CODEC=PacketCodec.unit(new Fire());
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 static void register(){
  VillageSelfShot.register();
  WeaponShotNetwork.register();
  net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(MusketItem::tickSmoke);
  PayloadTypeRegistry.playC2S().register(Fire.ID,Fire.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Fire.ID,(packet,context)->context.server().execute(()->{
   var p=context.player();if(p.isSpectator()||!p.isAlive())return;
   if(p.getMainHandStack().getItem() instanceof MusketItem musket)musket.fire(p.getEntityWorld(),p,Hand.MAIN_HAND);
  }));
  net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((p,w,h,pos,face)->p.getMainHandStack().getItem() instanceof MusketItem?ActionResult.FAIL:ActionResult.PASS);
  net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((p,w,h,e,hit)->p.getMainHandStack().getItem() instanceof MusketItem?ActionResult.FAIL:ActionResult.PASS);
  net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((p,w,h,e,hit)->p.getMainHandStack().getItem() instanceof MusketItem?ActionResult.FAIL:ActionResult.PASS);
  net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,world,hand,hit)->{
   if (!(player.getMainHandStack().getItem() instanceof MusketItem)) return ActionResult.PASS;
   var block = world.getBlockState(hit.getBlockPos());
   if (block.getBlock() instanceof net.minecraft.block.DoorBlock) return ActionResult.PASS;
   if (player.isSneaking() && block.isOf(net.minecraft.block.Blocks.BARREL)
       && (!(world instanceof net.minecraft.server.world.ServerWorld serverWorld)
           || MarketCrime.find(serverWorld, hit.getBlockPos()) != null)) return ActionResult.PASS;
   return ActionResult.FAIL;
  });
 }
}
