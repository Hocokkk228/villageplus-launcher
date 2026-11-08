package dev.halloween;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class CandyBucketContent {
    public static Item CANDY_CORN;
    public static CandyBucketBlock BUCKET;
    public static BlockEntityType<CandyBucketEntity> BUCKET_ENTITY;
    public static void register() {
        Identifier candyId = Identifier.of("halloween", "candy_corn");
        CANDY_CORN = Registry.register(Registries.ITEM, candyId, new Item(new Item.Settings()
                .registryKey(RegistryKey.of(RegistryKeys.ITEM, candyId))));
        Identifier bucketId = Identifier.of("halloween", "candy_bucket");
        var blockKey = RegistryKey.of(RegistryKeys.BLOCK, bucketId);
        BUCKET = Registry.register(Registries.BLOCK, blockKey, new CandyBucketBlock(AbstractBlock.Settings.create()
                .registryKey(blockKey).mapColor(MapColor.ORANGE).strength(.8f).nonOpaque().sounds(BlockSoundGroup.WOOD)));
        Item bucketItem = Registry.register(Registries.ITEM, bucketId, new BlockItem(BUCKET, new Item.Settings()
                .registryKey(RegistryKey.of(RegistryKeys.ITEM, bucketId)).useBlockPrefixedTranslationKey()));
        BUCKET_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, bucketId,
                FabricBlockEntityTypeBuilder.create(CandyBucketEntity::new, BUCKET).build());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e -> { e.add(bucketItem); e.add(CANDY_CORN); });
    }
    private CandyBucketContent() { }
}
