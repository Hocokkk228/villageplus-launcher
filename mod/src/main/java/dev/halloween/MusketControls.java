package dev.halloween;
public final class MusketControls {
 private static boolean pressed;
 private static int selfCharge,armedTicks;
 private static float aim,lastAim,kick,lastKick;
 public static float aim(float delta){return lastAim+(aim-lastAim)*delta;}
 public static float kick(float delta){return lastKick+(kick-lastKick)*delta;}

 static void register(){
 net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((draw,tick)->{
  var c=net.minecraft.client.MinecraftClient.getInstance();if(c.player==null||c.options.hudHidden||selfCharge==0)return;
  int x=draw.getScaledWindowWidth()/2-39,y=draw.getScaledWindowHeight()-66;
  for(int i=0;i<20;i++)draw.fill(x+i*4,y,x+i*4+2,y+7,i*5<selfCharge?0xffffb65c:0x704f535a);
 });
 net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->{
  lastAim=aim;lastKick=kick;
  boolean held=c.player!=null&&c.player.getMainHandStack().getItem() instanceof MusketItem;
  if(held&&c.currentScreen==null&&!c.player.isCreative()&&!c.player.isSpectator()){
   if(c.player.isSneaking()){selfCharge=Math.min(100,selfCharge+1);armedTicks=100;}
   else if(selfCharge<100||--armedTicks<=0)selfCharge=0;
  }else{selfCharge=0;armedTicks=0;}
  aim+=(held&&c.currentScreen==null&&c.options.useKey.isPressed()?1-aim:-aim)*.3f;kick*=.6f;
  boolean down=c.options.attackKey.isPressed();
  if(c.player!=null&&c.currentScreen==null&&down&&!pressed&&c.player.getMainHandStack().getItem() instanceof MusketItem&&net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(MusketNetwork.Fire.ID)){
   if(!c.player.getItemCooldownManager().isCoolingDown(c.player.getMainHandStack()))kick=1;
   net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new MusketNetwork.Fire());
  }
  pressed=down;
 });}
}
