package dev.halloween.mixin.village;

import dev.halloween.VillageDamageCapture;
import java.util.List;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExplosionImpl.class)
public abstract class ExplosionCaptureMixin {
    @Shadow @Final private ServerWorld world;
    @Inject(method="destroyBlocks",at=@At("HEAD"))
    private void halloween$rememberTerrain(List<BlockPos> positions,CallbackInfo info){
        VillageDamageCapture.recordExplosion(world,positions);
    }
}
