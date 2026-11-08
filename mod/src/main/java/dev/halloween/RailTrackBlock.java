package dev.halloween;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
public final class RailTrackBlock extends BlockWithEntity {
 public static final MapCodec<RailTrackBlock> CODEC=createCodec(RailTrackBlock::new);
 public RailTrackBlock(Settings settings){super(settings);}
 @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return CODEC;}
 @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new RailTrackEntity(pos,state);}
 @Override protected BlockRenderType getRenderType(BlockState state){return BlockRenderType.INVISIBLE;}
 @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return VoxelShapes.cuboid(-1,0,-1,2,.22,3);}
 @Override protected VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return VoxelShapes.cuboid(-1,0,0,2,.16,1);}
 @Override public void onPlaced(World world,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.onPlaced(world,pos,state,placer,stack);if(world.getBlockEntity(pos) instanceof RailTrackEntity track){var f=placer==null?net.minecraft.util.math.Direction.SOUTH:placer.getHorizontalFacing();var points=new java.util.ArrayList<net.minecraft.util.math.Vec3d>();for(int i=0;i<=8;i++)points.add(new net.minecraft.util.math.Vec3d(.5+f.getOffsetX()*i*.5,0,.5+f.getOffsetZ()*i*.5));track.setPoints(points);}}
}
