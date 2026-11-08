package dev.halloween.mixin.village;
import dev.halloween.WorldSettlements;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.entry.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** /locate discovers a city in the same ledger without forcing its population to tick. */
@Mixin(ChunkGenerator.class)
public abstract class WorldLocateMixin {
 @Inject(method="locateStructure(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/registry/entry/RegistryEntryList;Lnet/minecraft/util/math/BlockPos;IZ)Lcom/mojang/datafixers/util/Pair;",at=@At("RETURN"))
 private void discovered(ServerWorld world,RegistryEntryList<Structure> structures,BlockPos origin,int radius,boolean skip,CallbackInfoReturnable<Pair<BlockPos,RegistryEntry<Structure>>> cir){var p=cir.getReturnValue();if(p!=null&&p.getSecond().getKey().map(k->k.getValue().getPath().startsWith("village")).orElse(false))WorldSettlements.discover(world,p.getFirst());}
}
