package dev.halloween.mixin.village;
import dev.halloween.RailContent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.pathing.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Platforms and the footbridge remain ordinary walkable nodes; the track bed is hazardous. */
@Mixin(LandPathNodeMaker.class)
public abstract class RailPathAvoidanceMixin {
 @Inject(method="getCommonNodeType",at=@At("RETURN"),cancellable=true)
 private static void avoidTrack(BlockView world,BlockPos pos,CallbackInfoReturnable<PathNodeType> cir){if(world.getBlockState(pos).isOf(RailContent.TRACK)||world.getBlockState(pos.down()).isOf(RailContent.TRACK))cir.setReturnValue(PathNodeType.DANGER_OTHER);}
}
