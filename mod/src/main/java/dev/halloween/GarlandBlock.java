package dev.halloween;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.enums.StairShape;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;

/** Stair-style facing and corner selection, with a hanging decorative model. */
public final class GarlandBlock extends HorizontalFacingBlock {
    public static final MapCodec<GarlandBlock> CODEC = createCodec(GarlandBlock::new);
    public static final EnumProperty<StairShape> SHAPE = Properties.STAIR_SHAPE;

    public GarlandBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(SHAPE, StairShape.STRAIGHT));
    }
    @Override protected MapCodec<? extends HorizontalFacingBlock> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(FACING, SHAPE); }
    @Override public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
        return state.with(SHAPE, corner(state, ctx.getWorld(), ctx.getBlockPos()));
    }
    @Override protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView ticks,
            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighbor, Random random) {
        return state.with(SHAPE, corner(state, world, pos));
    }
    private static StairShape corner(BlockState state, BlockView world, BlockPos pos) {
        Direction facing = state.get(FACING);
        BlockState front = world.getBlockState(pos.offset(facing));
        if (front.isOf(state.getBlock())) {
            Direction turn = front.get(FACING);
            if (turn.getAxis() != facing.getAxis() && different(state, world, pos, turn.getOpposite())) {
                return turn == facing.rotateYCounterclockwise() ? StairShape.OUTER_LEFT : StairShape.OUTER_RIGHT;
            }
        }
        BlockState back = world.getBlockState(pos.offset(facing.getOpposite()));
        if (back.isOf(state.getBlock())) {
            Direction turn = back.get(FACING);
            if (turn.getAxis() != facing.getAxis() && different(state, world, pos, turn)) {
                return turn == facing.rotateYCounterclockwise() ? StairShape.INNER_LEFT : StairShape.INNER_RIGHT;
            }
        }
        return StairShape.STRAIGHT;
    }
    private static boolean different(BlockState state, BlockView world, BlockPos pos, Direction side) {
        BlockState neighbor = world.getBlockState(pos.offset(side));
        return !neighbor.isOf(state.getBlock()) || neighbor.get(FACING) != state.get(FACING);
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!player.getMainHandStack().isEmpty()) return ActionResult.PASS;
        if (!world.isClient()) {
            BlockState rotated = state.with(FACING, state.get(FACING).rotateYClockwise());
            world.setBlockState(pos, rotated.with(SHAPE, corner(rotated, world, pos)), Block.NOTIFY_ALL);
        }
        return ActionResult.SUCCESS;
    }
    @Override protected BlockState mirror(BlockState state, BlockMirror mirror) {
        if (mirror == BlockMirror.NONE) return state;
        Direction facing = mirror.apply(state.get(FACING));
        StairShape shape = switch (state.get(SHAPE)) {
            case INNER_LEFT -> StairShape.INNER_RIGHT;
            case INNER_RIGHT -> StairShape.INNER_LEFT;
            case OUTER_LEFT -> StairShape.OUTER_RIGHT;
            case OUTER_RIGHT -> StairShape.OUTER_LEFT;
            default -> StairShape.STRAIGHT;
        };
        return state.with(FACING, facing).with(SHAPE, shape);
    }
    @Override protected VoxelShape getCollisionShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return VoxelShapes.empty(); }
    @Override protected VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) {
        // Thin selectable arms follow each of the five actual cord paths.
        Direction f = s.get(FACING);
        Direction left = f.rotateYCounterclockwise(), right = f.rotateYClockwise();
        Direction a = left, b = right;
        switch (s.get(SHAPE)) {
            case OUTER_LEFT -> b = f;
            case OUTER_RIGHT -> a = f;
            case INNER_LEFT -> b = f.getOpposite();
            case INNER_RIGHT -> a = f.getOpposite();
            default -> { }
        }
        return VoxelShapes.union(arm(a), arm(b));
    }
    private static VoxelShape arm(Direction d) {
        return switch (d) {
            case WEST -> Block.createCuboidShape(0, 5, 5, 8, 16, 11);
            case EAST -> Block.createCuboidShape(8, 5, 5, 16, 16, 11);
            case NORTH -> Block.createCuboidShape(5, 5, 0, 11, 16, 8);
            default -> Block.createCuboidShape(5, 5, 8, 11, 16, 16);
        };
    }
}
