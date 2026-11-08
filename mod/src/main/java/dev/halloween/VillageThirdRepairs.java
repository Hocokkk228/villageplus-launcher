package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
final class VillageThirdRepairs {
 static boolean patch(BlockPos p){int x=p.getX(),y=p.getY(),z=p.getZ();return (z==14||z==75)&&y>=17&&y<=39&&x>=12&&x<=52||(x==12||x==52)&&y>=17&&y<=19&&z>=14&&z<=75||x>=14&&x<=22&&y>=1&&y<=7&&z>=57&&z<=65;}
 static void register(){ServerTickEvents.END_WORLD_TICK.register(w->{
  if(!w.getPlayers().isEmpty()&&w.getTime()%10==0)for(var team:w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).teams)if(WorldSettlements.loadedNearby(w,team.anchor)&&VillageEraData.get(w).settlement(team).era>=3)VillagePalaceUpgrade.tick(w,team,VillageEraData.get(w).settlement(team));
 });ServerTickEvents.END_WORLD_TICK.register(w->{if(w.getPlayers().isEmpty()||w.getTime()%100!=0)return;VillageEraPlans.repairEntrances(w);var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var jobs=VillageProjects.get(w);
  for(var team:memory.teams){var s=VillageEraData.get(w).settlement(team);if(s.era<2&&s.transitionAt<0||!WorldSettlements.loadedNearby(w,team.anchor))continue;VillageTreasuryRepair.tick(w,team,s);VillageHouseRemnants.tick(w,team,s);VillageEraPlans.repairPlantationCrossings(w,team,s);
   for(var house:List.copyOf(memory.houses))if(house.recordKey.startsWith("gothic_rebuild:")&&!house.recordKey.endsWith(":clear")&&house.max.getX()-house.min.getX()==16&&VillageEras.team(w,house.min)==team){String id="villa_roof_v2:"+house.min.asLong();if(jobs.exists(id))continue;var plan=new LinkedHashMap<BlockPos,net.minecraft.block.BlockState>();boolean loaded=true;
    int roofY=house.blueprint.stream().filter(r->r.state().isOf(Blocks.SMOOTH_QUARTZ)).mapToInt(r->r.pos().getY()).max().orElse(Integer.MIN_VALUE);if(roofY==Integer.MIN_VALUE)continue;
    for(int x=6;x<=10;x++)for(int z=5;z<=9;z++){var p=house.min.withY(roofY).add(x,0,z);if(!w.isChunkLoaded(p)){loaded=false;continue;}var b=w.getBlockState(p);if(b.isAir()||b.isOf(Blocks.SNOW)||b.isOf(Blocks.GLASS))plan.put(p,Blocks.GLASS.getDefaultState());}
    if(loaded&&!plan.isEmpty())jobs.add(w,id,team.village,"factory_supplies",plan);
   }
   if(s.era==VillageEpoch.STEAM&&!VillageIndustrial.pending(s)){long active=jobs.jobs.stream().filter(p->p.village==team.village&&p.id.startsWith("gothic_infill_v3:")&&!p.done).count();if(active<2)for(int i=0;i<12;i++){String key="gothic_infill_v3:"+team.village+":"+i;if(!jobs.exists(key)){VillageSteam.ensureResidence(w,team,s,key,i%3==0?"steam_villa":"steam_townhouse");break;}}}


  }
 });}
 static void royalBed(ServerWorld w,VillageMemory.Team team,BlockPos head){if(!(w.getBlockState(head).getBlock() instanceof BedBlock))return;
  var markers=w.getEntitiesByClass(MarkerEntity.class,new Box(team.anchor).expand(192),m->m.getCommandTags().contains("halloween_royal_bed")&&VillageEras.team(w,m.getBlockPos())==team);
  MarkerEntity marker=markers.isEmpty()?EntityType.MARKER.create(w,SpawnReason.EVENT):markers.getFirst();if(marker==null)return;marker.setPosition(head.getX()+.5,head.getY(),head.getZ()+.5);marker.addCommandTag("halloween_royal_bed");if(markers.isEmpty())w.spawnEntity(marker);for(var old:markers)if(old!=marker)old.discard();
  for(var king:w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(192),v->RoyalProfession.isKing(v)&&VillageEras.belongs(w,v,team.village))){if(king.getBrain().getOptionalRegisteredMemory(net.minecraft.entity.ai.brain.MemoryModuleType.HOME).map(h->h.pos().equals(head)).orElse(false))continue;if(king.isSleeping())king.wakeUp();king.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.HOME,GlobalPos.create(w.getRegistryKey(),head));}
 }
}
