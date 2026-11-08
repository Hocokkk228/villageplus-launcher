package dev.halloween;

import com.google.common.collect.ImmutableSet;
import net.minecraft.registry.*;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerProfession;

final class RoyalProfession {
    static net.minecraft.registry.RegistryKey<VillagerProfession> KEY;
    static RegistryKey<VillagerProfession> REBEL_KEY, PRESIDENT_KEY;
    static net.minecraft.registry.entry.RegistryEntry<VillagerProfession> PRESIDENT_ENTRY;
    static net.minecraft.registry.entry.RegistryEntry<VillagerProfession> REBEL_ENTRY;
    static net.minecraft.item.Item CROWN;
    static net.minecraft.block.Block CEREMONIAL_LECTERN;
    static net.minecraft.item.Item DEPUTY_CROWN;
    static RegistryKey<VillagerProfession> DEPUTY_KEY;
    static net.minecraft.registry.entry.RegistryEntry<VillagerProfession> DEPUTY_ENTRY;
    static void register() {
        Identifier id=Identifier.of("halloween","king");
        KEY=RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,id);
        Identifier lecternId=Identifier.of("halloween","ceremonial_lectern");
        var lecternKey=RegistryKey.of(RegistryKeys.BLOCK,lecternId);
        CEREMONIAL_LECTERN=Registry.register(Registries.BLOCK,lecternKey,new CeremonialLecternBlock(net.minecraft.block.AbstractBlock.Settings.create().registryKey(lecternKey).strength(2.5f).sounds(net.minecraft.sound.BlockSoundGroup.WOOD).nonOpaque()));
        Registry.register(Registries.ITEM,lecternId,new net.minecraft.item.BlockItem(CEREMONIAL_LECTERN,new net.minecraft.item.Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,lecternId)).useBlockPrefixedTranslationKey()));
        REBEL_KEY=RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,Identifier.of("halloween","rebel_king"));
        var rebelKing=Registry.register(Registries.VILLAGER_PROFESSION,REBEL_KEY,new VillagerProfession(Text.translatable("entity.halloween.rebel_king"),p->false,p->false,ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_AMBIENT));
        REBEL_ENTRY=Registries.VILLAGER_PROFESSION.getEntry(rebelKing);
        PRESIDENT_KEY=RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,Identifier.of("halloween","president"));
        var president=Registry.register(Registries.VILLAGER_PROFESSION,PRESIDENT_KEY,
                new VillagerProfession(Text.translatable("entity.halloween.president"),p->false,p->false,
                        ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_AMBIENT));
        PRESIDENT_ENTRY=Registries.VILLAGER_PROFESSION.getEntry(president);
        Identifier deputyId=Identifier.of("halloween","deputy");
        DEPUTY_KEY=RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,deputyId);
        var deputy=Registry.register(Registries.VILLAGER_PROFESSION,DEPUTY_KEY,new VillagerProfession(Text.translatable("entity.halloween.deputy"),p->false,p->false,ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_AMBIENT));
        DEPUTY_ENTRY=Registries.VILLAGER_PROFESSION.getEntry(deputy);
        Identifier silverId=Identifier.of("halloween","deputy_crown");
        DEPUTY_CROWN=Registry.register(Registries.ITEM,silverId,new net.minecraft.item.Item(new net.minecraft.item.Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,silverId)).maxCount(1)
            .component(net.minecraft.component.DataComponentTypes.EQUIPPABLE,net.minecraft.component.type.EquippableComponent.builder(net.minecraft.entity.EquipmentSlot.HEAD).damageOnHurt(false).build())));
        Identifier crownId=Identifier.of("halloween","royal_crown");
        CROWN=Registry.register(Registries.ITEM,crownId,new net.minecraft.item.Item(new net.minecraft.item.Item.Settings()
          .registryKey(RegistryKey.of(RegistryKeys.ITEM,crownId)).maxCount(1)
          .component(net.minecraft.component.DataComponentTypes.EQUIPPABLE,net.minecraft.component.type.EquippableComponent.builder(net.minecraft.entity.EquipmentSlot.HEAD).damageOnHurt(false).build())));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(w->{
            if(w.getTime()%100!=0)return;
            for(var player:w.getPlayers()) for(var v:w.getEntitiesByClass(net.minecraft.entity.passive.VillagerEntity.class,new net.minecraft.util.math.Box(player.getBlockPos()).expand(64),RoyalProfession::isKing))
                if(!updateOffice(w,v) && v.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isEmpty()
                        && !RoyalCoronation.isAnimating(w,v))v.equipStack(net.minecraft.entity.EquipmentSlot.HEAD,new net.minecraft.item.ItemStack(CROWN));
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if (entity instanceof net.minecraft.entity.passive.VillagerEntity leader && isPresident(leader))
                SpasInteriors.navigation(leader);
        });
        Registry.register(Registries.VILLAGER_PROFESSION, KEY,
            new VillagerProfession(Text.translatable("entity.minecraft.villager.halloween.king"), p->false, p->false,
                ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_AMBIENT));
    }
    static boolean isKing(net.minecraft.entity.passive.VillagerEntity v){return v.getVillagerData().profession().matchesKey(KEY)||v.getVillagerData().profession().matchesKey(REBEL_KEY)||isPresident(v);}
    static boolean isPresident(net.minecraft.entity.passive.VillagerEntity villager) {
        return villager.getVillagerData().profession().matchesKey(PRESIDENT_KEY);
    }

    static boolean updateOffice(net.minecraft.server.world.ServerWorld world, net.minecraft.entity.passive.VillagerEntity leader) {
        if (isPresident(leader)) return true;
        long recorded = VillageEras.tag(leader, "halloween_era_village_", Long.MIN_VALUE);
        var settlement = recorded == Long.MIN_VALUE ? VillageEras.settlement(world, leader.getBlockPos())
                : VillageEraData.get(world).settlements.get(recorded);
        if (!isKing(leader) || !VillageIndustrial.civic(settlement)) return false;
        becomePresident(leader);
        return true;
    }

    static void becomePresident(net.minecraft.entity.passive.VillagerEntity leader) {
        leader.setVillagerData(leader.getVillagerData().withProfession(PRESIDENT_ENTRY));
        if (leader.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isOf(CROWN))
            leader.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.item.ItemStack.EMPTY);
        SpasInteriors.navigation(leader);
    }
    private RoyalProfession() {}
}
