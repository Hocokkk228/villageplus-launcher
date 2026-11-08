package dev.halloween;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.GameMode;
/** Narrow local launcher integration: open the current owned single-player world to LAN. */
final class LauncherRoomBridge {
 static long checked;static String seen="";
 static void register(){ClientTickEvents.END_CLIENT_TICK.register(client->{long now=System.currentTimeMillis();if(now-checked<400)return;checked=now;var dir=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();var request=dir.resolve("villageplus-room-request.json");try{if(!Files.exists(request))return;var data=JsonParser.parseString(Files.readString(request)).getAsJsonObject();if(!data.has("createdAt")||Math.abs(now-data.get("createdAt").getAsLong())>15000){Files.deleteIfExists(request);return;}String id=data.get("id").getAsString();if(id.equals(seen))return;UUID.fromString(id);seen=id;Files.deleteIfExists(request);int port=data.get("port").getAsInt();if(port<1024||port>65535)throw new IllegalArgumentException("Порт должен быть от 1024 до 65535");String name=data.get("mode").getAsString();var mode=switch(name){case"survival"->GameMode.SURVIVAL;case"creative"->GameMode.CREATIVE;case"adventure"->GameMode.ADVENTURE;case"spectator"->GameMode.SPECTATOR;default->throw new IllegalArgumentException("Неизвестный режим игры");};boolean cheats=data.has("cheats")&&data.get("cheats").getAsBoolean();var server=client.getServer();if(server==null||client.world==null){reply(dir,id,false,"Открой одиночный мир в Minecraft.",port);return;}
  if(server.isRemote()){reply(dir,id,false,"Мир уже открыт на порту "+server.getServerPort()+". Для смены порта вернись в меню и зайди в мир снова.",server.getServerPort());return;}
  boolean opened=server.openToLan(mode,cheats,port);reply(dir,id,opened,opened?"Комната открыта":"Не удалось занять порт. Выбери другой.",port);
 }catch(Exception e){if(!seen.isEmpty())try{reply(dir,seen,false,e.getMessage(),0);}catch(Exception ignored){}}});}
 static void reply(Path dir,String id,boolean ok,String message,int port)throws Exception{var json=new JsonObject();json.addProperty("id",id);json.addProperty("ok",ok);json.addProperty("message",message);json.addProperty("port",port);var file=dir.resolve("villageplus-room-response.json");var temp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temp,json.toString(),StandardCharsets.UTF_8);Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
 private LauncherRoomBridge(){}
}
