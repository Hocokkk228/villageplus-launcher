package dev.halloween;
final class IndustrialMachinesClient {
 static boolean doorKey;
 static void register(){IndustrialVehicleItemRenderer.register();
  net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(IndustrialMachines.VEHICLE,IndustrialVehicleRenderer::new);
  net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(MachineAssembly.TYPE,MachineAssemblyRenderer::new);
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->{if(c.player==null)return;
   if(c.player.getVehicle() instanceof IndustrialVehicleEntity v&&v.isDriver(c.player)&&net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(IndustrialVehicleControls.Input.ID)){
    boolean open=c.options.useKey.isPressed();net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new IndustrialVehicleControls.Input((c.options.forwardKey.isPressed()?1:0)-(c.options.backKey.isPressed()?1:0),(c.options.rightKey.isPressed()?1:0)-(c.options.leftKey.isPressed()?1:0),c.options.jumpKey.isPressed(),open&&!doorKey));doorKey=open;
    if(c.player.age%40==0)c.player.sendMessage(net.minecraft.text.Text.literal("W/S — ход · A/D — руль · пробел — тормоз · Shift — выйти · Shift + ПКМ снаружи — двери"),true);
   }else doorKey=false;
  });
 }
 private IndustrialMachinesClient(){}
}
