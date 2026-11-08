package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.BedBlock;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
final class StationResidents {
 static EntityType<StationResidentEntity> TYPE;
 static void register(){
  var key=RegistryKey.of(RegistryKeys.ENTITY_TYPE,Identifier.of("halloween","station_resident"));
  TYPE=Registry.register(Registries.ENTITY_TYPE,key,EntityType.Builder.<StationResidentEntity>create(StationResidentEntity::new,SpawnGroup.MISC).dimensions(.6f,.35f).maxTrackingRange(8).build(key));
  FabricDefaultAttributeRegistry.register(TYPE,VillagerEntity.createVillagerAttributes());
  ServerTickEvents.END_WORLD_TICK.register(w->{if(w.getPlayers().isEmpty()||w.getTime()%100!=0)return;
   for(var station:RailwayNetwork.get(w).stations)if(w.getPlayers().stream().anyMatch(p->p.getBlockPos().isWithinDistance(station.origin,192))){
    repairSleep(w,station);if(VillageProjects.get(w).complete(station.village,"rail_station"))ensure(w,station);
   }
  });
 }
 static void repairSleep(ServerWorld w,RailwayNetwork.Station station){
  for(var v:w.getEntitiesByClass(VillagerEntity.class,new Box(station.pos(21,3,32)).expand(48,15,48),v->!(v instanceof StationResidentEntity)&&v.isSleeping())){
   var bed=v.getSleepingPosition();
   if(bed.isPresent()&&v.squaredDistanceTo(Vec3d.ofCenter(bed.get()))<9&&(!w.isChunkLoaded(bed.get())||w.getBlockState(bed.get()).getBlock() instanceof BedBlock))continue;
   var at=v.getEntityPos();v.wakeUp();v.setPosition(at);v.setPose(EntityPose.STANDING);
  }
 }
 static void ensure(ServerWorld w,RailwayNetwork.Station station){
  for(int slot=station.residents.size();slot<2;slot++){
   var feet=station.pos(38,3,slot==0?9:40);
   if(!w.isChunkLoaded(feet)||!w.getBlockState(feet.down()).isSideSolidFullSquare(w,feet.down(),Direction.UP)||!w.getBlockState(feet).getCollisionShape(w,feet).isEmpty()||!w.getBlockState(feet.up()).getCollisionShape(w,feet.up()).isEmpty())return;
   var resident=TYPE.create(w,SpawnReason.EVENT);if(resident==null)return;
   resident.setVillagerData(resident.getVillagerData().withProfession(Registries.VILLAGER_PROFESSION.getOrThrow(net.minecraft.village.VillagerProfession.NITWIT)));
   resident.setCustomName(net.minecraft.text.Text.literal("Бродяга"));resident.setPosition(Vec3d.ofBottomCenter(feet));resident.setYaw(station.forward().getHorizontalQuarterTurns()*90);resident.setBodyYaw(resident.getYaw());resident.setHeadYaw(resident.getYaw());
   resident.addCommandTag("halloween_station_resident");if(!w.spawnEntity(resident))return;
   station.residents.add(resident.getUuidAsString());RailwayNetwork.get(w).markDirty();
  }
 }
 private StationResidents(){}
}
