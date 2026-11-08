package dev.halloween.mixin.village;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Villagers do not advance LivingEntity's attack animation in their vanilla tick. */
@Mixin(VillagerEntity.class)
public abstract class VillagerSwingMixin extends LivingEntity {
    protected VillagerSwingMixin(EntityType<? extends LivingEntity> type, World world) { super(type, world); }

    @Inject(method="tick", at=@At("TAIL"))
    private void halloween$advanceSwing(CallbackInfo ci) {
        // Both sides: server may start subsequent attacks; client animates the vanilla swing packet.
        tickHandSwing();
    }
}
