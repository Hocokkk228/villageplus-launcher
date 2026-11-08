package dev.halloween.mixin.client;

import dev.halloween.HalloweenMod;
import dev.halloween.compat.IrisLanternId;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Hand;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.IdMapUniforms$HeldItemSupplier", remap = false)
public abstract class IrisHeldItemMixin {
    @Shadow @Final private Hand hand;
    @Shadow @Final private Object2IntFunction<Object> itemIdMap;
    @Shadow private int intID;
    @Shadow private int lightValue;
    @Shadow private Vector3f lightColor;
    @Unique private static final Vector3f HALLOWEEN_WARM_LIGHT = new Vector3f(1.0f, 0.55f, 0.16f);

    @Inject(method = "update", at = @At("TAIL"), remap = false)
    private void halloween$wearableLantern(CallbackInfo ci) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;
        boolean held = player.getStackInHand(hand).isOf(HalloweenMod.PUMPKIN_LANTERN.asItem());
        boolean worn = hand == Hand.MAIN_HAND && lightValue < 15
                && player.getEquippedStack(EquipmentSlot.HEAD).isOf(HalloweenMod.PUMPKIN_LANTERN.asItem());
        if (held || worn) {
            intID = IrisLanternId.get(itemIdMap);
            lightValue = 15;
            lightColor = HALLOWEEN_WARM_LIGHT;
        }
    }
}
