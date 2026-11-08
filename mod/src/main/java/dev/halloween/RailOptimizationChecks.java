package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.*;
/** Same process, same camera and loaded scene: old renderer / batched renderer / old renderer. */
final class RailOptimizationChecks {
 static volatile boolean prepared;static int ticks,phase=-1;static final List<Integer> fps=new ArrayList<>();static final List<Double> medians=new ArrayList<>();
 static void register(){ServerTickEvents.END_SERVER_TICK.register(server->{if(prepared||server.getOverworld().getPlayers().isEmpty())return;var w=server.getOverworld();var p=w.getPlayers().getFirst();p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);p.getAbilities().flying=true;p.sendAbilitiesUpdate();p.networkHandler.requestTeleport(22,76,526,-44,12);prepared=true;w.setTimeOfDay(6000);});
  ClientTickEvents.END_CLIENT_TICK.register(c->{c.options.pauseOnLostFocus=false;c.mouse.unlockCursor();if(c.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen)c.setScreen(null);if(!prepared||c.player==null||c.world==null)return;ticks++;
   for(var key:new net.minecraft.client.option.KeyBinding[]{c.options.forwardKey,c.options.backKey,c.options.leftKey,c.options.rightKey,c.options.jumpKey,c.options.sneakKey,c.options.useKey,c.options.attackKey})key.setPressed(false);
   c.player.setYaw(-44);c.player.setPitch(12);if(ticks<160)return;if(phase<0){phase=0;RailRenderers.legacyBenchmark=true;ticks=0;}
   if(ticks>80&&ticks%20==0)fps.add(c.getCurrentFps());
   if(ticks==260){org.slf4j.LoggerFactory.getLogger("rail-opt-test").info("STAGE {} renderer={} fps={}",phase,RailRenderers.legacyBenchmark?"old":"batched",fps);var sorted=fps.stream().sorted().toList();double median=sorted.get(sorted.size()/2);medians.add(median);fps.clear();
    if(phase==1)net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,"rail-batched-renderer.png",c.getFramebuffer(),1,t->{});
    if(++phase==3){org.slf4j.LoggerFactory.getLogger("rail-opt-test").info("COMPLETE median old/batched/old = {}",medians);RailRenderers.legacyBenchmark=false;c.scheduleStop();}else{RailRenderers.legacyBenchmark=phase!=1;ticks=0;}
   }
  });
 }
}
