package dev.halloween;

import com.google.common.collect.ImmutableSet;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerProfession;

public final class GuardProfession {
    public static final RegistryKey<VillagerProfession> KEY=RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,Identifier.of("halloween","guard"));
    static RegistryEntry<VillagerProfession> ENTRY,ROYAL_ENTRY;
    public static final RegistryKey<VillagerProfession> ROYAL_KEY=RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,Identifier.of("halloween","royal_guard"));
    static void register(){
        VillagerProfession p=Registry.register(Registries.VILLAGER_PROFESSION,KEY,new VillagerProfession(
            Text.translatable("entity.halloween.guard"),x->false,x->false,ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_AMBIENT));
        ENTRY=Registries.VILLAGER_PROFESSION.getEntry(p);
        VillagerProfession royal=Registry.register(Registries.VILLAGER_PROFESSION,ROYAL_KEY,new VillagerProfession(
            Text.translatable("entity.halloween.bodyguard"),x->false,x->false,ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_AMBIENT));
        ROYAL_ENTRY=Registries.VILLAGER_PROFESSION.getEntry(royal);
    }
    private GuardProfession(){}
}
