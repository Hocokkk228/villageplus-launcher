package dev.halloween;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
/** Three explicit tabs; estimates are labelled rather than presented as a census of unloaded entities. */
final class WorldStatistics {
 record Report(String text) implements CustomPayload {
  static final Id<Report> ID=new Id<>(Identifier.of("halloween","world_statistics"));
  static final PacketCodec<RegistryByteBuf,Report> CODEC=PacketCodec.tuple(PacketCodecs.string(262144),Report::text,Report::new);
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 static String report(ServerWorld w){var data=WorldSettlements.get(w);var cities=data.cities.stream().sorted(java.util.Comparator.comparing(c->c.name)).toList();double stage=cities.stream().mapToDouble(c->WorldSettlements.estimate(w,c)).average().orElse(1);long population=cities.stream().mapToLong(c->WorldSettlements.population(w,c)).sum();
  var out=new StringBuilder("МИРОВОЕ РАЗВИТИЕ\n\nСредняя стадия: ").append(WorldSettlements.development(stage)).append("\nПоселений в реестре: ").append(cities.size()).append("\nНаселение известных городов: ≈ ").append(population).append("\n\nОткрытия и развитие:\n");
  for(var c:cities)out.append(c.name).append(" — ").append(WorldSettlements.development(WorldSettlements.estimate(w,c))).append("\n  ").append(WorldSettlements.inventions(WorldSettlements.estimate(w,c))).append("\n");
  out.append("\nРеестр пополняется при разведке и /locate.\nДалёкие города развиваются по времени мира.\n");
  out.append("\fГОРОДА И НАСЕЛЕНИЕ\nЧисленность приблизительная, включая незагруженных жителей.\n\n");
  for(var c:cities)out.append(c.name).append(" · ≈ ").append(WorldSettlements.population(w,c)).append(" жителей\n  X ").append(c.pos.getX()).append(" · Z ").append(c.pos.getZ()).append(" · ").append(WorldSettlements.development(WorldSettlements.estimate(w,c))).append(c.catchup?" · обновляется":"").append("\n");
  out.append("\fМЕЖДУНАРОДНЫЙ РОЗЫСК\nПоследние известные сведения; граница города не снимает розыск.\n\n");
  if(data.warrants.isEmpty())out.append("Объявленных в розыск пока нет.\n");
  for(var r:data.warrants.values())out.append("★".repeat(Math.clamp(r.stars(),1,3))).append("  ").append(r.name()).append("\n  Дело: ").append(r.origin()).append(" · замечен: ").append(r.city()).append("\n  X ").append(r.pos().getX()).append(" · Z ").append(r.pos().getZ()).append(" · ").append(Math.max(0,(w.getTime()-r.time())/24000)).append(" дней назад\n");
  return out.toString();
 }
 static void register(){PayloadTypeRegistry.playS2C().register(Report.ID,Report.CODEC);net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player,world,hand)->{
  if(!player.getStackInHand(hand).isOf(VillageItems.WORLD_STATISTICS))return ActionResult.PASS;
  if(player instanceof net.minecraft.server.network.ServerPlayerEntity p&&world instanceof ServerWorld w){WorldSettlements.observeWanted(w,p);ServerPlayNetworking.send(p,new Report(report(w)));}return ActionResult.SUCCESS;
 });}
}
