package dev.halloween;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;
public final class RailSleeperBlock extends Block {
 public static final MapCodec<RailSleeperBlock> CODEC=createCodec(RailSleeperBlock::new);
 public RailSleeperBlock(Settings settings){super(settings);}
 @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
 @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return VoxelShapes.cuboid(0,0,0,1,.125,1);}
}
