package dev.halloween;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;

final class VillageBlood {
    static final SimpleParticleType SPLASH=Registry.register(Registries.PARTICLE_TYPE,Identifier.of("halloween","blood_splash"),FabricParticleTypes.simple());
    static void register(){}
}
