package dev.halloween;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import java.util.Map;

public final class PumpkinLanternBlock extends HorizontalFacingBlock {
    public static final MapCodec<PumpkinLanternBlock> CODEC = createCodec(PumpkinLanternBlock::new);
    private static final VoxelShape BODY = VoxelShapes.union(
            Block.createCuboidShape(4, 0, 4, 12, 1.5, 12),
            Block.createCuboidShape(2, 1.5, 3, 14, 3, 13),
            Block.createCuboidShape(1, 3, 1.5, 15, 10, 14.5),
            Block.createCuboidShape(2.5, 10, 3, 13.5, 12, 13),
            Block.createCuboidShape(5, 12, 5, 11, 13, 11));
    private static final VoxelShape OUTLINE = VoxelShapes.union(BODY,
            Block.createCuboidShape(6.5, 12, 6.5, 10, 16, 9.5));
    private static final Map<Direction, VoxelShape> OUTLINES = VoxelShapes.createHorizontalFacingShapeMap(OUTLINE);
    private static final Map<Direction, VoxelShape> COLLISIONS = VoxelShapes.createHorizontalFacingShapeMap(BODY);

    public PumpkinLanternBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() { return CODEC; }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINES.get(state.get(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return COLLISIONS.get(state.get(FACING));
    }
}
