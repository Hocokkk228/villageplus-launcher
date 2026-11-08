package dev.halloween;
final class RailControlsClient {
 static void register(){net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->{
  if(c.player==null)return;if(c.player.getVehicle() instanceof RailSeatEntity s){s.syncToTrain();if(s.controls()&&s.train()!=null&&!s.train().isShip()&&net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(RailControlNetwork.Input.ID)){
   net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new RailControlNetwork.Input((c.options.forwardKey.isPressed()?1:0)-(c.options.backKey.isPressed()?1:0),c.options.jumpKey.isPressed()));
   if(c.player.age%20==0)c.player.sendMessage(net.minecraft.text.Text.literal("W — разгон · S / пробел — тормоз · Shift — выйти"),true);
  }}
 });}
}
