package dev.halloween;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
final class LauncherCosmeticsClient {
 static final Map<UUID,List<Integer>> REMOTE=new HashMap<>();static List<Integer> local=List.of();static long checked,stamp=-2;static boolean dirty=true;
 static void register(){ClientPlayNetworking.registerGlobalReceiver(LauncherCosmetics.Outfit.ID,(p,c)->c.client().execute(()->REMOTE.put(p.player(),p.items())));ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{REMOTE.clear();dirty=true;});ClientPlayConnectionEvents.JOIN.register((h,s,c)->dirty=true);
  LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type,renderer,helper,context)->{if(renderer instanceof PlayerEntityRenderer<?> player)helper.register(new LauncherCosmeticsFeature(player));});
  ClientTickEvents.END_CLIENT_TICK.register(c->{long now=System.currentTimeMillis();if(now-checked>2000){checked=now;var file=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("villageplus-cosmetics.json");try{long modified=Files.getLastModifiedTime(file).toMillis();if(modified!=stamp){stamp=modified;var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();var values=new ArrayList<Integer>();if(json.has("enabled")&&json.get("enabled").getAsBoolean()&&json.has("selection"))for(var e:json.getAsJsonObject("selection").entrySet()){int i=e.getValue().getAsInt();if(i>=0&&i<=62&&!values.contains(i)&&values.size()<8)values.add(i);}local=List.copyOf(values);dirty=true;}}catch(NoSuchFileException e){if(stamp!=-1){stamp=-1;local=List.of();dirty=true;}}catch(Exception e){org.slf4j.LoggerFactory.getLogger("launcher-cosmetics").warn("Could not read cosmetic selection: {}",e.toString());}}
   if(dirty&&c.player!=null&&ClientPlayNetworking.canSend(LauncherCosmetics.Outfit.ID)){ClientPlayNetworking.send(new LauncherCosmetics.Outfit(c.player.getUuid(),local));dirty=false;}
  });
 }
 private LauncherCosmeticsClient(){}
}
