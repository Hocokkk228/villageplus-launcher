package dev.halloween;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
/** Arrival is one event per stop, so the 30% police check cannot be rerolled every tick. */
final class RailTravel {
 static boolean policeRoll(int stars,double random){return stars>=2&&random<.30;}
 static void arrive(ServerWorld w,RailVehicleEntity engine,int stop){var path=engine.route();if(path==null)return;var station=RailwayNetwork.get(w).stations.stream().min(Comparator.comparingDouble(s->s.center(32).squaredDistanceTo(engine.getEntityPos()))).orElse(null);if(station==null||station.center(32).squaredDistanceTo(engine.getEntityPos())>10000)return;
  for(var p:w.getPlayers())if(aboard(w,engine,p)){
   Contraband.arrive(w,station,p);
   var city=WorldSettlements.discover(w,BlockPos.fromLong(station.village));p.sendMessage(Text.literal("Вокзал: "+city.name),true);
   var from=p.getCommandTags().stream().filter(t->t.startsWith("halloween_departed_city_")).findFirst().orElse(null);String now="halloween_departed_city_"+station.village;
   if(from!=null&&!from.equals(now)&&p.getCommandTags().contains("halloween_player_wanted")&&policeRoll(PlayerJustice.wantedLevel(p),w.random.nextDouble()))meetPolice(w,station,p);
   p.getCommandTags().removeIf(t->t.startsWith("halloween_departed_city_"));p.addCommandTag(now);
  }
  for(var train:RailVehicleEntity.nearby(w,engine.getBoundingBox().expand(50)))if(train.kind()==1&&train.route()!=null&&train.route().controls.equals(path.controls)){
   for(var seat:w.getEntitiesByClass(RailSeatEntity.class,train.getBoundingBox().expand(2),s->train.getUuid().equals(s.owner)&&s.hasPassengers()))if(seat.getFirstPassenger() instanceof VillagerEntity v&&!RailDriver.isDriver(v)){
    if(w.random.nextFloat()<.65){var target=exit(w,station,train);if(target!=null){RailBoarding.leave(w,train,v);v.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.HOME);v.addCommandTag("halloween_arrived_city_"+station.village);WorldSettlements.noteMigration(w,v,city(w,station));}}
   }
   // A small group of actual residents now starts heading to each stopped coach.
   var platform=new Box(station.origin).expand(90,15,90);int admitted=0;
   for(var v:w.getEntitiesByClass(VillagerEntity.class,platform,v->v.isAlive()&&!v.hasVehicle()&&!v.isBaby()&&!RailDriver.isDriver(v)&&!VillageDefense.isGuard(v)&&!RoyalProfession.isKing(v)&&!VillageProjects.owns(v)&&!VillageTrials.busy(v))){if(admitted>=4)break;var goal=train.getEntityPos().add(RailGeometry.rotate(new Vec3d(3,2,5.5),train.getYaw()));v.getNavigation().startMovingTo(goal.x,goal.y,goal.z,.65);admitted++;}
  }
 }
 static WorldSettlements.City city(ServerWorld w,RailwayNetwork.Station s){return WorldSettlements.discover(w,BlockPos.fromLong(s.village));}
 static boolean aboard(ServerWorld w,RailVehicleEntity engine,Entity p){
  RailVehicleEntity train = p.getVehicle() instanceof RailSeatEntity seat ? seat.train()
      : p instanceof ServerPlayerEntity player ? RailPassengerNetwork.walkingVehicle(player) : null;
  return train != null && !train.isShip() && train.route() != null && engine.route() != null
      && train.route().controls.equals(engine.route().controls) && train.squaredDistanceTo(engine) < 60 * 60;
 }
 static BlockPos exit(ServerWorld w,RailwayNetwork.Station s,RailVehicleEntity t){for(int x:new int[]{4,12,20})for(int z=8;z<55;z++){var q=s.pos(x,3,z);if(w.isChunkLoaded(new ChunkPos(q).toLong())&&w.isSpaceEmpty(new Box(q).contract(.05))&&w.getBlockState(q.down()).isSolidBlock(w,q.down())&&q.isWithinDistance(t.getBlockPos(),18))return q;}return null;}
 static void meetPolice(ServerWorld w,RailwayNetwork.Station s,ServerPlayerEntity p){p.sendMessage(Text.literal("§cО вашем приезде сообщили страже."),false);int n=0;
  for(int z=16;z<=40&&n<3;z+=8){var q=s.pos(20,3,z);if(!w.getBlockState(q).getCollisionShape(w,q).isEmpty()||!w.getBlockState(q.up()).getCollisionShape(w,q.up()).isEmpty())continue;var g=EntityType.VILLAGER.create(w,SpawnReason.EVENT);if(g==null)continue;g.setPosition(Vec3d.ofBottomCenter(q));g.setVillagerData(g.getVillagerData().withProfession(GuardProfession.ENTRY));g.addCommandTag("halloween_guard");g.setCustomName(Text.literal("Вокзальная стража"));g.setPersistent();g.equipStack(EquipmentSlot.MAINHAND,new net.minecraft.item.ItemStack(VillageItems.MUSKET));g.getInventory().addStack(new net.minecraft.item.ItemStack(VillageItems.MUSKET_CARTRIDGE,12));w.spawnEntity(g);g.getNavigation().startMovingTo(p,.7);n++;}
 }
}
