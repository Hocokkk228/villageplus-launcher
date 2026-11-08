package dev.halloween;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;

final class VillageItems {
    static Item MUSKET,MUSKET_CARTRIDGE,REVOLVER,RAPIER,SABRE;
    static Item FIGHT,MONITOR,REVOLT,TRIAL_TEST,EXECUTION_TEST,STATISTICS,WORLD_STATISTICS;
    static void register(){
        var musketId=Identifier.of("halloween","musket");
        MUSKET=Registry.register(Registries.ITEM,musketId,new MusketItem(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,musketId)).maxCount(1)));
        var ammoId=Identifier.of("halloween","musket_cartridge");
        MUSKET_CARTRIDGE=Registry.register(Registries.ITEM,ammoId,new Item(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,ammoId))));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e->{e.add(MUSKET);e.add(MUSKET_CARTRIDGE);});
        var revolverId=Identifier.of("halloween","revolver");
        REVOLVER=Registry.register(Registries.ITEM,revolverId,new MusketItem(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,revolverId)).maxCount(1)));
        var rapierId=Identifier.of("halloween","rapier");
        RAPIER=Registry.register(Registries.ITEM,rapierId,new Item(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,rapierId)).sword(ToolMaterial.IRON,5.5f,-1.8f)));
        var sabreId=Identifier.of("halloween","sabre");
        SABRE=Registry.register(Registries.ITEM,sabreId,new Item(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,sabreId)).sword(ToolMaterial.IRON,6.5f,-2.4f)));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e->{e.add(REVOLVER);e.add(RAPIER);e.add(SABRE);});
        var statisticsId=Identifier.of("halloween","village_statistics");
        STATISTICS=Registry.register(Registries.ITEM,statisticsId,new Item(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,statisticsId)).maxCount(1)));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e->e.add(STATISTICS));
        var worldStatsId=Identifier.of("halloween","world_statistics");
        WORLD_STATISTICS=Registry.register(Registries.ITEM,worldStatsId,new Item(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,worldStatsId)).maxCount(1)));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e->e.add(WORLD_STATISTICS));
        var fightId=Identifier.of("halloween","fight_event");
        FIGHT=Registry.register(Registries.ITEM,fightId,new VillageFightItem(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,fightId)).maxCount(1)));
        var monitorId=Identifier.of("halloween","village_monitor");
        MONITOR=Registry.register(Registries.ITEM,monitorId,new Item(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,monitorId)).maxCount(1)));
        var revoltId=Identifier.of("halloween","rebellion_event");
        REVOLT=Registry.register(Registries.ITEM,revoltId,new RebellionEventItem(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,revoltId)).maxCount(1)));
        var trialId=Identifier.of("halloween","trial_test");
        TRIAL_TEST=Registry.register(Registries.ITEM,trialId,new TrialEventItem(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,trialId)).maxCount(1),false));
        var executionId=Identifier.of("halloween","execution_test");
        EXECUTION_TEST=Registry.register(Registries.ITEM,executionId,new TrialEventItem(new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,executionId)).maxCount(1),true));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e->{e.add(FIGHT);e.add(MONITOR);e.add(REVOLT);e.add(TRIAL_TEST);e.add(EXECUTION_TEST);});
    }
    private VillageItems(){}
}
