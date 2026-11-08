package dev.halloween.mixin.client;
import dev.halloween.MusketControls;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HeldItemRenderer.class)
public abstract class MusketViewMixin {
 @Inject(method="renderFirstPersonItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"))
 private void village$aim(AbstractClientPlayerEntity player,float delta,float pitch,Hand hand,float swing,ItemStack stack,float equip,MatrixStack matrices,OrderedRenderCommandQueue queue,int light,CallbackInfo ci){
  if(hand!=Hand.MAIN_HAND||!java.util.Set.of("musket","revolver").contains(net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath()))return;
  float aim=MusketControls.aim(delta),kick=MusketControls.kick(delta);
  int side=player.getMainArm()==net.minecraft.util.Arm.RIGHT?1:-1;
  matrices.translate(-side*.58*aim,.28*aim,.16*aim+.1*kick);
  matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-5*kick));
 }
}
