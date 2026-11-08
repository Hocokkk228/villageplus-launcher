package dev.halloween.mixin.village;

import dev.halloween.VillageTasks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Brain.class)
public abstract class VillageTaskBrainMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void halloween$taskPriority(ServerWorld world,LivingEntity entity,CallbackInfo ci){
        if(entity instanceof VillagerEntity v&&(VillageTasks.ownsMovement(v)||v.hurtTime==0&&!v.isTouchingWater()&&Math.floorMod(world.getTime()+v.getId(),4)!=0))ci.cancel();
    }
}
