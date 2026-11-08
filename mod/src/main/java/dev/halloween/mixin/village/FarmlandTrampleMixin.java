package dev.halloween.mixin.village;

import dev.halloween.VillageFarms;
import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmlandBlock.class)
public abstract class FarmlandTrampleMixin {
    @Inject(method="onLandedUpon",at=@At("HEAD"))
    private void halloween$beforeTrample(World world,BlockState state,BlockPos pos,Entity entity,double fallDistance,CallbackInfo ci){VillageFarms.beforeTrample(world,pos);}
    @Inject(method="onLandedUpon",at=@At("RETURN"))
    private void halloween$afterTrample(World world,BlockState state,BlockPos pos,Entity entity,double fallDistance,CallbackInfo ci){VillageFarms.afterTrample(world,pos);}
}
