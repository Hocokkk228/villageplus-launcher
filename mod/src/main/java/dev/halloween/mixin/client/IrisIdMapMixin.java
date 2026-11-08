package dev.halloween.mixin.client;

import dev.halloween.compat.LanternShaderAliases;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.IdMap", remap = false)
public abstract class IrisIdMapMixin {
    @Inject(method = "readProperties", at = @At("RETURN"), cancellable = true, remap = false)
    private static void halloween$lanternAliases(Path path, String name, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(LanternShaderAliases.addAlias(name, cir.getReturnValue()));
    }
}
