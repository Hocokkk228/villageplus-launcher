package dev.halloween;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
/** Explicit local admin fast-forward, never enabled by ordinary progression. */
final class VillageFastEras {
 static final String FLAG="debug_fast_eras";
 static boolean active(ServerWorld w,long village){return VillageEraData.get(w).settlements.containsKey(village)&&VillageEraData.get(w).settlements.get(village).xp.getOrDefault(FLAG,0)>0;}
 static int configure(net.minecraft.server.command.ServerCommandSource source,boolean enabled)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
  var player=source.getPlayerOrThrow();var w=player.getEntityWorld();var team=VillageEras.team(w,player.getBlockPos());if(team==null){source.sendError(Text.literal("Рядом нет известной деревни."));return 0;}
  var data=VillageEraData.get(w);var s=data.settlement(team);
  s.xp.put(FLAG,enabled?1:0);data.markDirty();source.sendFeedback(()->Text.literal(enabled?"Ускоренное развитие включено для «"+s.name+"». Стройки и ремонт будут ускорены и после перехода эпохи. /villageplus fast off — выключить.":"Ускоренное развитие выключено. Выполненное строительство сохранено."),true);return 1;
 }
 static void register(){
  CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->{
   var fast=CommandManager.literal("fast").executes(c->configure(c.getSource(),true));
   fast.then(CommandManager.literal("on").executes(c->configure(c.getSource(),true)));
   fast.then(CommandManager.literal("off").executes(c->configure(c.getSource(),false)));
   dispatcher.register(CommandManager.literal("villageplus").requires(s->s.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.GAMEMASTERS))).then(fast));
  });
  ServerTickEvents.END_WORLD_TICK.register(w->{if(w.getPlayers().isEmpty())return;long deadline=System.nanoTime()+3_000_000;
   for(var team:w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).teams){if(!active(w,team.village))continue;var data=VillageEraData.get(w);var s=data.settlement(team);
    if(WorldSettlements.loadedNearby(w,team.anchor))advance(w, team, data, s);
    else if(VillageIndustrial.civic(s)&&IndustrialCityGrowth.playerNear(w,team,s))VillageProjects.materialize(w,team.village,4096);
    else continue;
    if(System.nanoTime()>=deadline)break;
   }
  });
 }
 static void advance(ServerWorld world, VillageMemory.Team team, VillageEraData data, VillageEraData.Settlement settlement) {
  if (settlement.era == 1 && settlement.transitionAt < 0) {
   for (var profession : VillageEras.PROFESSIONS) settlement.xp.put(profession, Math.max(240, settlement.xp.getOrDefault(profession, 0)));
   VillageEras.begin(world, team, settlement);
   data.markDirty();
  }
  if (settlement.era == 2) VillageSteam.start(world, team);
  VillageProjects.materialize(world, team.village, 4096);
  VillageBuilders.fastRepair(world, team, 420, 32);
 }

}
