package dev.halloween;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
/** A vacant engine waits a minute for a player before receiving a replacement driver. */
final class RailDriver {
 static final Vec3d CHAIR=new Vec3d(0,2.05,-4.4);
 static net.minecraft.registry.entry.RegistryEntry<net.minecraft.village.VillagerProfession> PROFESSION;
 static void register(){var id=net.minecraft.util.Identifier.of("halloween","train_driver");var p=net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.VILLAGER_PROFESSION,id,new net.minecraft.village.VillagerProfession(Text.literal("Машинист"),x->false,x->false,com.google.common.collect.ImmutableSet.of(),com.google.common.collect.ImmutableSet.of(),net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_WORK_TOOLSMITH));PROFESSION=net.minecraft.registry.Registries.VILLAGER_PROFESSION.getEntry(p);}
 static boolean isDriver(VillagerEntity v){return v.getCommandTags().contains("halloween_train_driver");}
 static RailSeatEntity seat(ServerWorld w,RailVehicleEntity engine){return w.getEntitiesByClass(RailSeatEntity.class,engine.getBoundingBox().expand(3),s->engine.getUuid().equals(s.owner)&&s.controls()&&s.hasPassengers()).stream().findFirst().orElse(null);}
 static boolean powered(ServerWorld w,RailVehicleEntity engine){var s=seat(w,engine);return s!=null&&s.getFirstPassenger()!=null&&s.getFirstPassenger().isAlive();}
 static void ensure(ServerWorld w,RailVehicleEntity engine){
  if(engine.kind()!=0||engine.age!=1&&engine.age%20!=0)return;
  var occupied=seat(w,engine);if(occupied!=null&&occupied.getFirstPassenger() instanceof net.minecraft.entity.player.PlayerEntity){engine.driverVacantSince=-1;return;}
  String owner="halloween_train_driver_"+engine.getUuidAsString();
  for(var v:w.getEntitiesByClass(VillagerEntity.class,engine.getBoundingBox().expand(16),v->v.isAlive()&&v.getCommandTags().contains(owner))){
   v.getCommandTags().removeIf(t->t.equals("halloween_guard")||t.startsWith("halloween_patrol_team_")||t.equals("halloween_bodyguard")||t.equals("halloween_castle_guard"));
   var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);for(var t:memory.teams)if(t.patrolGuards.remove(v.getUuidAsString())|t.builders.remove(v.getUuidAsString()))memory.markDirty();
   v.setVillagerData(v.getVillagerData().withProfession(PROFESSION));v.setAiDisabled(true);
   if(v.getVehicle() instanceof RailSeatEntity s&&s.train()==engine){s.control=true;engine.driverCreated=true;engine.driverVacantSince=-1;return;}
   if(occupied==null){var anchor=createSeat(w,engine);if(anchor!=null){v.startRiding(anchor,true,false);anchor.syncToTrain();engine.driverCreated=true;engine.driverVacantSince=-1;}}return;
  }
  if(occupied!=null){engine.driverVacantSince=-1;return;}
  if(engine.driverCreated){if(engine.driverVacantSince<0){engine.driverVacantSince=w.getTime();return;}if(w.getTime()-engine.driverVacantSince<1200)return;}
  var driver=EntityType.VILLAGER.create(w,SpawnReason.EVENT);if(driver==null)return;var anchor=createSeat(w,engine);if(anchor==null)return;
  driver.setVillagerData(driver.getVillagerData().withProfession(PROFESSION));driver.setCustomName(Text.literal("Машинист"));driver.setCustomNameVisible(true);driver.setPersistent();driver.setAiDisabled(true);driver.addCommandTag(owner);driver.addCommandTag("halloween_train_driver");driver.equipStack(EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));driver.setPosition(anchor.getEntityPos());w.spawnEntity(driver);driver.startRiding(anchor,true,false);anchor.syncToTrain();
  engine.driverCreated=true;engine.driverVacantSince=-1;
 }
 private static RailSeatEntity createSeat(ServerWorld w,RailVehicleEntity engine){var seat=RailContent.SEAT.create(w,SpawnReason.EVENT);if(seat==null)return null;seat.owner=engine.getUuid();seat.local=CHAIR;seat.control=true;seat.setPosition(RailGeometry.rotate(seat.local,engine.getYaw()).add(engine.getEntityPos()));w.spawnEntity(seat);return seat;}
}
