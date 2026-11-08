package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
/** Small server-validated cosmetic selections. No model files arrive through game packets. */
public final class LauncherCosmetics {
 public record Outfit(UUID player,List<Integer> items) implements CustomPayload{
  public static final Id<Outfit> ID=new Id<>(Identifier.of("halloween","launcher_cosmetics"));
  public static final PacketCodec<RegistryByteBuf,Outfit> CODEC=new PacketCodec<>(){public Outfit decode(RegistryByteBuf b){var id=b.readUuid();int n=b.readVarInt();if(n<0||n>8)throw new IllegalArgumentException("Too many cosmetics");var items=new ArrayList<Integer>();for(int i=0;i<n;i++){int item=b.readVarInt();if(item<0||item>62)throw new IllegalArgumentException("Unknown cosmetic");items.add(item);}return new Outfit(id,List.copyOf(items));}public void encode(RegistryByteBuf b,Outfit p){b.writeUuid(p.player);b.writeVarInt(p.items.size());for(int i:p.items)b.writeVarInt(i);}};
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 static final Map<ServerPlayerEntity,List<Integer>> OUTFITS=new WeakHashMap<>();
 static void register(){PayloadTypeRegistry.playC2S().register(Outfit.ID,Outfit.CODEC);PayloadTypeRegistry.playS2C().register(Outfit.ID,Outfit.CODEC);ServerPlayNetworking.registerGlobalReceiver(Outfit.ID,(m,c)->c.server().execute(()->{var selected=m.items.stream().distinct().limit(8).toList();OUTFITS.put(c.player(),selected);var packet=new Outfit(c.player().getUuid(),selected);for(var p:c.server().getPlayerManager().getPlayerList())if(ServerPlayNetworking.canSend(p,Outfit.ID))ServerPlayNetworking.send(p,packet);}));ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{for(var e:OUTFITS.entrySet())if(e.getKey().isAlive()&&ServerPlayNetworking.canSend(handler.player,Outfit.ID))ServerPlayNetworking.send(handler.player,new Outfit(e.getKey().getUuid(),e.getValue()));});ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->OUTFITS.remove(handler.player));}
 private LauncherCosmetics(){}
}
