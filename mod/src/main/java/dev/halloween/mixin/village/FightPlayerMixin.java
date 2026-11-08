package dev.halloween.mixin.village;

import dev.halloween.FightPose;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class FightPlayerMixin {
    @Inject(method="updatePose",at=@At("HEAD"),cancellable=true)
    private void halloween$compactKnockdown(CallbackInfo ci){
        var player=(PlayerEntity)(Object)this;
        if(FightPose.down(FightPose.of(player).halloween$fightPose())){
            player.setPose(EntityPose.SWIMMING);
            ci.cancel();
        }
    }

    @ModifyVariable(method="travel",at=@At("HEAD"),argsOnly=true)
    private Vec3d halloween$noCrawlingWhileDown(Vec3d input){
        var player=(PlayerEntity)(Object)this;
        if(!FightPose.down(FightPose.of(player).halloween$fightPose()))return input;
        player.setSprinting(false);
        return Vec3d.ZERO;
    }
}
