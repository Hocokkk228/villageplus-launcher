package dev.halloween.mixin.client;
import dev.halloween.VillagerArmorState;
import net.minecraft.client.render.entity.state.VillagerEntityRenderState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.*;
@Mixin(VillagerEntityRenderState.class)
public abstract class VillagerArmorStateMixin implements VillagerArmorState {
 @Unique private int halloween$injury;
 public int halloween$wounds(){return halloween$injury;}
 public void halloween$wounds(int stage){halloween$injury=stage;}
 @Unique private final ItemStack[] halloween$armor={ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
 @Unique private static int halloween$index(EquipmentSlot slot){return switch(slot){case HEAD->0;case CHEST->1;case LEGS->2;case FEET->3;default->0;};}
 public ItemStack halloween$armor(EquipmentSlot slot){return halloween$armor[halloween$index(slot)];}
 public void halloween$setArmor(EquipmentSlot slot,ItemStack stack){halloween$armor[halloween$index(slot)]=stack.copy();}
}
