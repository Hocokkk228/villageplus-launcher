package dev.halloween.mixin.village;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.BlockPos;
@Mixin(LivingEntity.class)
public abstract class PrisonBedMixin {
 @Inject(method="sleep",at=@At("HEAD"),cancellable=true)
 private void village$prisonBed(BlockPos pos,CallbackInfo ci){if((Object)this instanceof VillagerEntity v&&dev.halloween.VillagePrisonBeds.forbidden(v,pos))ci.cancel();}
}
