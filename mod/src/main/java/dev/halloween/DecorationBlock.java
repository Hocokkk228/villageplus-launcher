package dev.halloween;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
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

public final class DecorationBlock extends HorizontalFacingBlock {
    public static final MapCodec<DecorationBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DecorationBlock.<DecorationBlock>createSettingsCodec(),
            Codec.STRING.fieldOf("decoration").forGetter(block -> block.decoration)
    ).apply(instance, DecorationBlock::new));
    private final String decoration;
    private final Map<Direction, VoxelShape> shapes;

    public DecorationBlock(Settings settings, String decoration) {
        super(settings);
        this.decoration = decoration;
        VoxelShape shape = switch (decoration) {
            case "sheet_ghost" -> VoxelShapes.union(
                    Block.createCuboidShape(3, 2, 4, 13, 14, 12),
                    Block.createCuboidShape(1, 6, 5, 15, 10, 11));
            case "little_ghost" -> Block.createCuboidShape(3, 2, 4, 13, 12, 12);
            case "scattered_bones" -> Block.createCuboidShape(1, 0, 1, 15, 3, 15);
            case "crossed_bones" -> Block.createCuboidShape(2, 0, 2, 14, 4, 14);
            case "weathered_skull" -> Block.createCuboidShape(4, 0, 4, 12, 8, 12);
            case "skull_pile" -> Block.createCuboidShape(1, 0, 2, 15, 11, 14);
            default -> throw new IllegalArgumentException("Unknown decoration: " + decoration);
        };
        shapes = VoxelShapes.createHorizontalFacingShapeMap(shape);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends HorizontalFacingBlock> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapes.get(state.get(FACING));
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return decoration.endsWith("ghost") ? VoxelShapes.empty() : shapes.get(state.get(FACING));
    }
}
