package dev.halloween;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class Decorations {
    public static void register() {
        Identifier garlandId = Identifier.of(HalloweenMod.MOD_ID, "halloween_garland");
        RegistryKey<Block> garlandKey = RegistryKey.of(RegistryKeys.BLOCK, garlandId);
        Block garland = Registry.register(Registries.BLOCK, garlandKey, new GarlandBlock(AbstractBlock.Settings.create()
                .registryKey(garlandKey).mapColor(MapColor.ORANGE).strength(.2f)
                .sounds(BlockSoundGroup.WOOL).nonOpaque().luminance(state -> 10)));
        RegistryKey<Item> garlandItemKey = RegistryKey.of(RegistryKeys.ITEM, garlandId);
        Item garlandItem = Registry.register(Registries.ITEM, garlandItemKey, new BlockItem(garland,
                new Item.Settings().registryKey(garlandItemKey).useBlockPrefixedTranslationKey()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(garlandItem));
        for (String name : new String[]{"sheet_ghost", "little_ghost", "scattered_bones", "crossed_bones", "weathered_skull", "skull_pile"}) {
            Identifier id = Identifier.of(HalloweenMod.MOD_ID, name);
            RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
            boolean ghost = name.endsWith("ghost");
            Block block = Registry.register(Registries.BLOCK, blockKey, new DecorationBlock(AbstractBlock.Settings.create()
                    .registryKey(blockKey).mapColor(MapColor.OFF_WHITE).strength(ghost ? .3f : .6f)
                    .sounds(ghost ? BlockSoundGroup.WOOL : BlockSoundGroup.BONE).nonOpaque(), name));
            RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
            Item item = Registry.register(Registries.ITEM, itemKey, new BlockItem(block,
                    new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey()));
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(item));
        }
    }
    private Decorations() { }
}
