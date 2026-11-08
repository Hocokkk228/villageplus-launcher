package dev.halloween.mixin.village;

import dev.halloween.VillageTasks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Seated spectators do not run quadratic crowd-pushing queries or shove one another off seats. */
@Mixin(LivingEntity.class)
public abstract class CourtCrowdMixin {
    private boolean halloween$seated(){return (Object)this instanceof VillagerEntity v&&VillageTasks.attendingCourt(v)&&v.getNavigation().isIdle();}
    @Inject(method="tickCramming",at=@At("HEAD"),cancellable=true)
    private void halloween$seatedCrowd(CallbackInfo ci){if(halloween$seated())ci.cancel();}
    @Inject(method="isPushable",at=@At("HEAD"),cancellable=true)
    private void halloween$seatedPush(CallbackInfoReturnable<Boolean> ci){if(halloween$seated())ci.setReturnValue(false);}
}
