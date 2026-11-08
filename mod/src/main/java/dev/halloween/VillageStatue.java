package dev.halloween;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/** Decorative villager statue: geometry extends beyond its one-block anchor. */
final class VillageStatue {
    static Block BLOCK, TOP, ROYAL, ROYAL_TOP;
    static void register(){
        registerRoyal();
        var id=Identifier.of("halloween","villager_statue");var key=RegistryKey.of(RegistryKeys.BLOCK,id);
        BLOCK=Registry.register(Registries.BLOCK,key,new StatueBlock(AbstractBlock.Settings.create().registryKey(key).mapColor(MapColor.STONE_GRAY).strength(2).sounds(BlockSoundGroup.STONE).nonOpaque(),false,false));
        var topId=Identifier.of("halloween","villager_statue_top");var topKey=RegistryKey.of(RegistryKeys.BLOCK,topId);
        TOP=Registry.register(Registries.BLOCK,topKey,new StatueBlock(AbstractBlock.Settings.create().registryKey(topKey).mapColor(MapColor.STONE_GRAY).strength(2).sounds(BlockSoundGroup.STONE).nonOpaque(),true,false));
        var item=Registry.register(Registries.ITEM,id,new BlockItem(BLOCK,new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,id)).useBlockPrefixedTranslationKey()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e->e.add(item));
    }
    private static void registerRoyal(){
        var id=Identifier.of("halloween","royal_villager_statue");var key=RegistryKey.of(RegistryKeys.BLOCK,id);
        ROYAL=Registry.register(Registries.BLOCK,key,new StatueBlock(AbstractBlock.Settings.create().registryKey(key).mapColor(MapColor.STONE_GRAY).strength(2).sounds(BlockSoundGroup.STONE).nonOpaque(),false,true));
        var upperId=Identifier.of("halloween","royal_villager_statue_top");var upperKey=RegistryKey.of(RegistryKeys.BLOCK,upperId);
        ROYAL_TOP=Registry.register(Registries.BLOCK,upperKey,new StatueBlock(AbstractBlock.Settings.create().registryKey(upperKey).mapColor(MapColor.STONE_GRAY).strength(2).sounds(BlockSoundGroup.STONE).nonOpaque(),true,true));
        var item=Registry.register(Registries.ITEM,id,new BlockItem(ROYAL,new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,id)).useBlockPrefixedTranslationKey()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e->e.add(item));
    }
    private static final class StatueBlock extends Block {
        private final boolean top,royal;
        StatueBlock(AbstractBlock.Settings settings,boolean top,boolean royal){super(settings);this.top=top;this.royal=royal;}
        private Block bottom(){return royal?ROYAL:BLOCK;}
        private Block upper(){return royal?ROYAL_TOP:TOP;}
        @Override public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext context){
            var w=context.getWorld();var p=context.getBlockPos();
            if(top||!w.getBlockState(p.up(2)).isReplaceable())return null;
            return super.getPlacementState(context);
        }
        @Override public void onPlaced(net.minecraft.world.World world,net.minecraft.util.math.BlockPos p,BlockState state,net.minecraft.entity.LivingEntity placer,ItemStack stack){
            super.onPlaced(world,p,state,placer,stack);if(!top&&world.getBlockState(p.up(2)).isReplaceable())world.setBlockState(p.up(2),upper().getDefaultState(),Block.NOTIFY_ALL);
        }
        @Override public BlockState onBreak(net.minecraft.world.World world,net.minecraft.util.math.BlockPos p,BlockState state,net.minecraft.entity.player.PlayerEntity player){
            var other=top?p.down(2):p.up(2);var expected=top?bottom():upper();
            if(world.getBlockState(other).isOf(expected))world.setBlockState(other,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);
            return super.onBreak(world,p,state,player);
        }
    }
    private VillageStatue(){}
}
