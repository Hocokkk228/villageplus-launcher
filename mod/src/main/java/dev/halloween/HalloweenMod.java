package dev.halloween;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class HalloweenMod implements ModInitializer {
    public static final String MOD_ID = "halloween";
    public static final Identifier PUMPKIN_ID = Identifier.of(MOD_ID, "pumpkin_lantern");
    public static final RegistryKey<Block> PUMPKIN_KEY = RegistryKey.of(RegistryKeys.BLOCK, PUMPKIN_ID);
    public static final Block PUMPKIN_LANTERN = new PumpkinLanternBlock(AbstractBlock.Settings.create()
            .registryKey(PUMPKIN_KEY)
            .mapColor(MapColor.ORANGE)
            .strength(1.0f)
            .sounds(BlockSoundGroup.WOOD)
            .nonOpaque()
            .luminance(state -> 15));

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, PUMPKIN_KEY, PUMPKIN_LANTERN);
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, PUMPKIN_ID);
        Item item = Registry.register(Registries.ITEM, itemKey,
                new BlockItem(PUMPKIN_LANTERN, new Item.Settings()
                        .registryKey(itemKey).useBlockPrefixedTranslationKey()
                        .component(DataComponentTypes.EQUIPPABLE, EquippableComponent.builder(EquipmentSlot.HEAD)
                                .damageOnHurt(false).build())));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(item));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var players = server.getPlayerManager();
            players.setViewDistance(Math.min(8, players.getViewDistance()));
        });
        VillageItems.register();
        MusketNetwork.register();
        BlastEffects.register();
        VillageWatchNetwork.register();
        WantedNetwork.register();
        Decorations.register();
        CandyBucketContent.register();
        FurnitureContent.register();
        RailContent.register();StationResidents.register();
        NavalContent.register();IndustrialMachines.register();IndustrialElectronics.register();MachineAssembly.register();IndustrialPlants.register();CityStreets.register();IndustrialMachineChecks.register();MachineFactoryFlowChecks.register();TransportSounds.register();RailRest.register();LauncherCosmetics.register();
        NavalChecks.register();NavalVisualChecks.register();NavalYardChecks.register();NavalDemo.register();CannonChecks.register();NavalMultiChecks.register();
        RailPassengerNetwork.register();
        RailControlNetwork.register();
        WorldSettlements.register();WorldSettlementChecks.register();FactoryFlowChecks.register();
        WorldStatistics.register();
        RailBoardingChecks.register();
        RailVillageDemo.register();
        RailwayNetwork.register();RailRingPlanner.register();
        RailConstructionChecks.register();
        RailNetworkChecks.register();
        RailAccessChecks.register();
        RailRingDemo.register();TaigaReadyDemo.register();StationTrackChecks.register();
        BuilderProfession.register();
        RoyalProfession.register();
        GuardProfession.register();
        RebelProfession.register();
        VillageCivilWorks.register();
        VillageBuilders.register();
        // Disabled: this process filled natural caves below structures without a reversible snapshot.
        VillageExpansion.register();
        VillageFarms.register();
        VillageJustice.register();
        VillageTrials.register();
        VillagePrisons.register();
        VillagePrisonBeds.register();
        VillageCustody.register();
        // Disabled: broad terrain filling can bury neighboring houses and dungeon entrances.
        // Do not apply global terrain reshaping to existing or newly generated settlements.
        ReleaseBlock.register();
        VillageDefense.register();
        VillagePopulation.register();
        VillageBirths.register();
        VillageRebellion.register();
        VillagerNightLife.register();
        RoyalCourt.register();
        VillagerGates.register();
        VillageDoors.register();
        RoyalCoronation.register();
        VillageArena.register();
        VillageCourthouse.register();
        PlayerJustice.register();
        VillageBounties.register();
        VillageConvoys.register();
        Contraband.register();
        ArmsDealers.register();
        VillageFirefighters.register();
        VillageGroundSafety.register();
        VillageTerraces.register();
        // Underfoot dirt pillars are not a navigation mechanism.
        VillageEras.register();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(VillageEraPlans::repairEntrances);
        VillageProjects.register();
        VillageConnections.register();
        VillageRelocations.register();
        VillageEconomy.register();
        VillageJanitors.register();
        VillageRoadNetwork.register();
        VillageStoneRoads.register();
        VillagePerimeter.register();
        VillageStable.register();
        VillageFactory.register();
        Oprichniki.register();
        VillageStatue.register();
        VillageWorkplaceIndex.register();
        VillageRegressionChecks.register();
        VillageEraSoak.register();VillageFastEras.register();VillageMarkets.register();VillageIndustrial.register();VillageSchool.register();VillageThirdRepairs.register();VillageRecoveryChecks.register();VillageConstructionAudit.register();VillageBlood.register();VillageInjuries.register();VillageFights.register();VillageFightChecks.register();VillageBridges.register();RailPreparationChecks.register();GenerationClearanceChecks.register();LegacyLastBlockChecks.register();
        Village036Checks.register();
        VillageStatistics.register();
        VillageWaterRescue.register();
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()&&Boolean.getBoolean("halloween.railPlaytest"))RailPlayChecks.register();
        if (Boolean.getBoolean("halloween.armorVisual")) ArmorVisualCheck.register();
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()
                && Boolean.getBoolean("halloween.smokeTest")) DevelopmentSmokeChecks.register();
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()
                && Boolean.getBoolean("halloween.villageSmokeTest")) VillageBuilderSmokeChecks.register();
    }
}

