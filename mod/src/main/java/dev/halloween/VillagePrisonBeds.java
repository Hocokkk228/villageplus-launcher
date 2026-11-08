package dev.halloween;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
/** Prison beds belong to assigned inmates, never to civilian housing POIs. */
public final class VillagePrisonBeds {
 public static boolean forbidden(VillagerEntity v,BlockPos bed){return v.getEntityWorld() instanceof ServerWorld w&&VillagePrisons.managed(w,bed)&&!v.getCommandTags().contains("halloween_prisoner");}
 static void register(){net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(w->{
  if(w.getPlayers().isEmpty()||w.getTime()%20!=0)return;
  for(var e:w.iterateEntities())if(e instanceof VillagerEntity v){
   var home=v.getBrain().getOptionalRegisteredMemory(net.minecraft.entity.ai.brain.MemoryModuleType.HOME);
   if(home.isPresent()&&home.get().dimension().equals(w.getRegistryKey())&&forbidden(v,home.get().pos())){
    if(v.isSleeping())v.wakeUp();v.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.HOME);w.getPointOfInterestStorage().remove(home.get().pos());
   }
  }
  VillagePrisons.removeCivilianBedPois(w);
 });}
}
