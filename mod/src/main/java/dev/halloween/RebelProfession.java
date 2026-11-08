package dev.halloween;

import com.google.common.collect.ImmutableSet;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerProfession;

final class RebelProfession {
    static final RegistryKey<VillagerProfession> KEY=RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,Identifier.of("halloween","rebel"));
    static RegistryEntry<VillagerProfession> ENTRY;
    static void register(){
        var v=Registry.register(Registries.VILLAGER_PROFESSION,KEY,new VillagerProfession(Text.translatable("entity.halloween.rebel"),x->false,x->false,
            ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_AMBIENT));ENTRY=Registries.VILLAGER_PROFESSION.getEntry(v);
    }
    private RebelProfession(){}
}
