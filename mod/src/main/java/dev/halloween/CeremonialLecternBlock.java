package dev.halloween;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;

/** A visual lectern without a job-site POI or inventory. */
public final class CeremonialLecternBlock extends Block {
    public static final MapCodec<CeremonialLecternBlock> CODEC=createCodec(CeremonialLecternBlock::new);
    public static final EnumProperty<Direction> FACING=Properties.HORIZONTAL_FACING;
    public CeremonialLecternBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getPlacementState(ItemPlacementContext ctx){return getDefaultState().with(FACING,ctx.getHorizontalPlayerFacing().getOpposite());}
    @Override protected BlockState rotate(BlockState state,BlockRotation rotation){return state.with(FACING,rotation.rotate(state.get(FACING)));}
    @Override protected BlockState mirror(BlockState state,BlockMirror mirror){return state.rotate(mirror.getRotation(state.get(FACING)));}
}

