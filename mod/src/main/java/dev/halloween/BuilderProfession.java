package dev.halloween;

import com.google.common.collect.ImmutableSet;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;

/** A real villager profession with its own job-site block. */
final class BuilderProfession {
    static final Identifier ID=Identifier.of("halloween","builder");
    static final RegistryKey<VillagerProfession> KEY=RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,ID);
    static final RegistryKey<PointOfInterestType> POI=RegistryKey.of(RegistryKeys.POINT_OF_INTEREST_TYPE,ID);
    static RegistryEntry<VillagerProfession> ENTRY;
    static Item HARD_HAT;
    static void register(){
        Identifier hatId=Identifier.of("halloween","builder_hard_hat");
        HARD_HAT=Registry.register(Registries.ITEM,hatId,new Item(new Item.Settings()
                .registryKey(RegistryKey.of(RegistryKeys.ITEM,hatId)).maxCount(1)
                .component(net.minecraft.component.DataComponentTypes.EQUIPPABLE,
                    net.minecraft.component.type.EquippableComponent.builder(net.minecraft.entity.EquipmentSlot.HEAD).damageOnHurt(false).build())));
        Identifier stationId=Identifier.of("halloween","builder_workbench");
        var blockKey=RegistryKey.of(RegistryKeys.BLOCK,stationId);
        Block station=Registry.register(Registries.BLOCK,blockKey,new Block(AbstractBlock.Settings.create().registryKey(blockKey)
                .mapColor(MapColor.OAK_TAN).strength(2).sounds(BlockSoundGroup.WOOD)));
        Item item=Registry.register(Registries.ITEM,stationId,new BlockItem(station,new Item.Settings()
                .registryKey(RegistryKey.of(RegistryKeys.ITEM,stationId)).useBlockPrefixedTranslationKey()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e->e.add(item));
        PointOfInterestHelper.register(ID,1,1,station);
        var profession=Registry.register(Registries.VILLAGER_PROFESSION,KEY,new VillagerProfession(
                Text.translatable("entity.minecraft.villager.halloween.builder"),e->e.matchesKey(POI),e->e.matchesKey(POI),
                ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_WORK_MASON));
        ENTRY=Registries.VILLAGER_PROFESSION.getEntry(profession);
    }
    private BuilderProfession(){}
}
