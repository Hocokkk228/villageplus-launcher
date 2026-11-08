package dev.halloween;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;

public final class ExtraDecorBlock extends HorizontalFacingBlock {
    public static final MapCodec<ExtraDecorBlock> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            ExtraDecorBlock.<ExtraDecorBlock>createSettingsCodec(),Codec.STRING.fieldOf("kind").forGetter(b->b.kind)).apply(i,ExtraDecorBlock::new));
    private final String kind;
    public ExtraDecorBlock(Settings settings,String kind){super(settings);this.kind=kind;setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends HorizontalFacingBlock> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState>b){b.add(FACING);}
    @Override public BlockState getPlacementState(ItemPlacementContext c){return getDefaultState().with(FACING,c.getHorizontalPlayerFacing().getOpposite());}
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
        VoxelShape shape=switch(kind){
            case "pumpkin_rug","bat_rug","spiderweb_rug"->Block.createCuboidShape(0,0,0,16,1,16);
            case "grave_mound"->Block.createCuboidShape(1,0,1,15,5,15);
            case "witch_cauldron"->Block.createCuboidShape(2,0,2,14,14,14);
            case "potion_shelf"->Block.createCuboidShape(1,0,4,15,16,13);
            case "spellbook_pedestal"->Block.createCuboidShape(2,0,2,14,15,14);
            case "haunted_clock"->Block.createCuboidShape(3,0,4,13,16,12);
            case "gravestone_cross"->Block.createCuboidShape(3,0,5,13,16,11);
            default->Block.createCuboidShape(2,0,3,14,16,13);
        };
        return VoxelShapes.createHorizontalFacingShapeMap(shape).get(s.get(FACING));
    }
    public static void register(){
        for(String kind:new String[]{"pumpkin_rug","bat_rug","spiderweb_rug","witch_cauldron","potion_shelf",
                "spellbook_pedestal","haunted_clock","gravestone_cross","grave_mound","bat_lantern"}){
            Identifier id=Identifier.of("halloween",kind);var key=RegistryKey.of(RegistryKeys.BLOCK,id);
            boolean rug=kind.endsWith("rug");
            Block block=Registry.register(Registries.BLOCK,key,new ExtraDecorBlock(AbstractBlock.Settings.create().registryKey(key)
                    .strength(rug?.2f:1).nonOpaque().mapColor(MapColor.PURPLE).sounds(rug?BlockSoundGroup.WOOL:BlockSoundGroup.WOOD)
                    .luminance(s->kind.equals("bat_lantern")?12:kind.equals("witch_cauldron")?4:0),kind));
            Item item=Registry.register(Registries.ITEM,id,new BlockItem(block,new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,id)).useBlockPrefixedTranslationKey()));
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e->e.add(item));
        }
    }
}
