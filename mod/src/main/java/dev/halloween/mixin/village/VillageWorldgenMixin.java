package dev.halloween.mixin.village;

import dev.halloween.VillageWorldgen;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class VillageWorldgenMixin {
    @Inject(method="setStructureStarts",at=@At("TAIL"))
    private void halloween$align(net.minecraft.registry.DynamicRegistryManager registries,net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator placement,StructureAccessor accessor,Chunk chunk,net.minecraft.structure.StructureTemplateManager templates,net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension,CallbackInfo ci){VillageWorldgen.align(chunk,placement.getStructureSeed());}
    @Inject(method="generateFeatures",at=@At("HEAD"))
    private void halloween$terrain(StructureWorldAccess world,Chunk chunk,StructureAccessor accessor,CallbackInfo ci){VillageWorldgen.terrain(world,chunk,accessor);}
}
