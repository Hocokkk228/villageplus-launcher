package dev.halloween;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

public final class CandyBucketBlock extends BlockWithEntity {
    public static final MapCodec<CandyBucketBlock> CODEC = createCodec(CandyBucketBlock::new);
    public static final IntProperty FILL = IntProperty.of("fill", 0, 4);
    public static final IntProperty MOTION = IntProperty.of("motion", 0, 15);
    private static final VoxelShape WALLS = VoxelShapes.union(
            Block.createCuboidShape(3, 0, 3, 13, 1, 13),
            Block.createCuboidShape(2, 1, 2, 3.5, 10, 14), Block.createCuboidShape(12.5, 1, 2, 14, 10, 14),
            Block.createCuboidShape(3.5, 1, 2, 12.5, 10, 3.5), Block.createCuboidShape(3.5, 1, 12.5, 12.5, 10, 14));
    public CandyBucketBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FILL, 0).with(MOTION, 0));
    }
    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(FILL, MOTION); }
    @Override public BlockEntity createBlockEntity(BlockPos p, BlockState s) { return new CandyBucketEntity(p, s); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World w, BlockState s, BlockEntityType<T> t) {
        return w.isClient() ? null : validateTicker(t, CandyBucketContent.BUCKET_ENTITY, CandyBucketEntity::tick);
    }
    @Override protected VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) {
        return Block.createCuboidShape(2, 0, 2, 14, 16, 14);
    }
    @Override protected VoxelShape getCollisionShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return WALLS; }
    @Override protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (!world.getBlockState(pos).isOf(this) && world.getBlockEntity(pos) instanceof CandyBucketEntity bucket && bucket.count > 0) {
            world.spawnEntity(new ItemEntity(world, pos.getX()+.5, pos.getY()+.8, pos.getZ()+.5,
                    new ItemStack(CandyBucketContent.CANDY_CORN, bucket.count)));
        }
        super.onStateReplaced(state, world, pos, moved);
    }
}
