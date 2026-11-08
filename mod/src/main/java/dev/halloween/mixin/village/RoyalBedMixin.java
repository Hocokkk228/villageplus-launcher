package dev.halloween.mixin.village;

import dev.halloween.RoyalBedAccess;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
public abstract class RoyalBedMixin {
    @Inject(method="sleep",at=@At("HEAD"),cancellable=true)
    private void halloween$reserveRoyalBed(BlockPos pos,CallbackInfo ci){
        if(!RoyalBedAccess.maySleep((VillagerEntity)(Object)this,pos))ci.cancel();
    }
}
