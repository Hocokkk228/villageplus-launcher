package dev.halloween;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
final class MachineAssembly {
 static Block BLOCK;static BlockEntityType<MachineAssemblyEntity> TYPE;
 static void register(){var id=Identifier.of("halloween","machine_assembly");var key=RegistryKey.of(RegistryKeys.BLOCK,id);
  BLOCK=Registry.register(Registries.BLOCK,key,new MachineAssemblyBlock(AbstractBlock.Settings.create().registryKey(key).strength(4).nonOpaque().sounds(net.minecraft.sound.BlockSoundGroup.METAL)));
  var ik=RegistryKey.of(RegistryKeys.ITEM,id);var item=Registry.register(Registries.ITEM,ik,new BlockItem(BLOCK,new Item.Settings().registryKey(ik).useBlockPrefixedTranslationKey()));
  TYPE=Registry.register(Registries.BLOCK_ENTITY_TYPE,id,FabricBlockEntityTypeBuilder.create(MachineAssemblyEntity::new,BLOCK).build());
  ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e->e.add(item));
 }
 private MachineAssembly(){}
}
