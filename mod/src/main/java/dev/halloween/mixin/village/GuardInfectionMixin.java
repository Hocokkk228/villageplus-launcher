package dev.halloween.mixin.village;

import dev.halloween.VillageDefense;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ZombieEntity.class)
public abstract class GuardInfectionMixin {
    @Inject(method="infectVillager",at=@At("HEAD"),cancellable=true)
    private void halloween$guardCannotBecomeZombie(ServerWorld world,VillagerEntity villager,CallbackInfoReturnable<Boolean> cir){
        // A fallen guard can die normally, but cannot turn his weapons against the village as a zombie.
        if(villager.getCommandTags().contains("halloween_guard")||VillageDefense.isGuard(villager))cir.setReturnValue(false);
    }
}
