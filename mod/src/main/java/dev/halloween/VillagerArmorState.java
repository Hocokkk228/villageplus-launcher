package dev.halloween;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
public interface VillagerArmorState {
    int halloween$wounds();
    void halloween$wounds(int stage);
    ItemStack halloween$armor(EquipmentSlot slot);
    void halloween$setArmor(EquipmentSlot slot,ItemStack stack);
}
