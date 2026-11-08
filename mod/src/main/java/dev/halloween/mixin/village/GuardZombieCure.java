package dev.halloween.mixin.village;

import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ZombieVillagerEntity.class)
public interface GuardZombieCure {
    @Invoker("finishConversion") void halloween$cureGuard(ServerWorld world);
}
