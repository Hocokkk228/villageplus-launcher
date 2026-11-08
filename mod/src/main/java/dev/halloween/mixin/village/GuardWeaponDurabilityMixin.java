package dev.halloween.mixin.village;

import dev.halloween.VillageDefense;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NPC guard weapons do not wear out; items still have normal durability for players. */
@Mixin(ItemStack.class)
public abstract class GuardWeaponDurabilityMixin {
    @Inject(method="damage(ILnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/EquipmentSlot;)V",at=@At("HEAD"),cancellable=true)
    private void halloween$guardWeapon(int amount,LivingEntity holder,EquipmentSlot slot,CallbackInfo ci){
        if(slot==EquipmentSlot.MAINHAND&&holder instanceof VillagerEntity guard&&VillageDefense.isGuard(guard))ci.cancel();
    }
}
