package dev.halloween;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class FurnitureContent {
    public static EntityType<SeatEntity> SEAT;
    public static void register() {
        Identifier seatId=Identifier.of("halloween","seat");
        var entityKey=RegistryKey.of(RegistryKeys.ENTITY_TYPE,seatId);
        SEAT=Registry.register(Registries.ENTITY_TYPE,entityKey,EntityType.Builder.<SeatEntity>create(SeatEntity::new,SpawnGroup.MISC)
                .dimensions(.1f,.1f).disableSummon().maxTrackingRange(8).trackingTickInterval(20).build(entityKey));
        for (String kind:new String[]{"pumpkin_chair","gothic_chair","coffin_chair","witch_stool",
                "pumpkin_cushion","bat_cushion","ghost_cushion","spider_cushion",
                "haunted_cabinet","coffin_cabinet","pumpkin_scarecrow","gargoyle_statue","reaper_statue","witch_table","candle_candelabra",
                "ritual_table","banquet_table"}) {
            Identifier id=Identifier.of("halloween",kind); var key=RegistryKey.of(RegistryKeys.BLOCK,id);
            boolean cloth=kind.endsWith("cushion");
            var settings=AbstractBlock.Settings.create()
                    .registryKey(key).strength(cloth?.3f:1.2f).nonOpaque().mapColor(MapColor.PURPLE)
                    .sounds(cloth?BlockSoundGroup.WOOL:kind.endsWith("statue")?BlockSoundGroup.STONE:BlockSoundGroup.WOOD)
                    .luminance(s->kind.equals("candle_candelabra")&&s.get(CandelabraBlock.LIT)?9:0);
            Block furniture=kind.endsWith("cabinet")||kind.equals("pumpkin_scarecrow")?new TallFurnitureBlock(settings,kind):
                    kind.equals("candle_candelabra")?new CandelabraBlock(settings):
                    kind.equals("ritual_table")||kind.equals("banquet_table")?new LargeTableBlock(settings,kind):new FurnitureBlock(settings,kind);
            Block block=Registry.register(Registries.BLOCK,key,furniture);
            Item item=Registry.register(Registries.ITEM,id,new BlockItem(block,new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM,id)).useBlockPrefixedTranslationKey()));
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e->e.add(item));
        }
        CabinetEntity.registerType();
        ExtraDecorBlock.register();
    }
    private FurnitureContent() { }
}
