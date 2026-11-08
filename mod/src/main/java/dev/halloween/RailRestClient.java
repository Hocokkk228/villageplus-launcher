package dev.halloween;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
final class RailRestClient {
 static float fade;static int settling;
 static void register(){ClientTickEvents.START_CLIENT_TICK.register(c->{boolean rest=c.player!=null&&c.player.getVehicle() instanceof RailSeatEntity s&&s.resting();if(rest){if(settling++<20)c.options.sneakKey.setPressed(false);fade=Math.min(1,fade+.075f);}else{settling=0;fade=Math.max(0,fade-.08f);}});HudRenderCallback.EVENT.register((draw,tick)->{if(fade<=0)return;var c=net.minecraft.client.MinecraftClient.getInstance();int w=draw.getScaledWindowWidth(),h=draw.getScaledWindowHeight();draw.fill(0,0,w,h,((int)(fade*255)<<24)|0x080a12);if(fade>.8f){String text=c.player!=null&&c.player.getVehicle() instanceof RailSeatEntity s&&s.restWaiting()?"Ожидание остальных игроков…":"Ночная поездка…";draw.drawTextWithShadow(c.textRenderer,text,(w-c.textRenderer.getWidth(text))/2,h/2,0xffd9d4c8);String hint="Пропуск до следующей станции · Shift — проснуться";draw.drawTextWithShadow(c.textRenderer,hint,(w-c.textRenderer.getWidth(hint))/2,h/2+18,0xff97948c);}});}
}
