package dev.halloween;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** Real four/six-block furniture: clear footprint, local collision, one item per complete table. */
public final class LargeTableBlock extends HorizontalFacingBlock {
    public static final MapCodec<LargeTableBlock> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            LargeTableBlock.<LargeTableBlock>createSettingsCodec(),Codec.STRING.fieldOf("kind").forGetter(b->b.kind)).apply(i,LargeTableBlock::new));
    public static final IntProperty PART=IntProperty.of("part",0,5);
    private final String kind;
    private final int width;
    public LargeTableBlock(Settings s,String kind){super(s);this.kind=kind;width=kind.equals("banquet_table")?3:2;
        setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH).with(PART,0));}
    @Override protected MapCodec<? extends HorizontalFacingBlock> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState>b){b.add(FACING,PART);}
    private BlockPos cell(BlockPos origin,Direction facing,int part){
        return origin.offset(facing.rotateYClockwise(),part%width).offset(facing.getOpposite(),part/width);
    }
    private BlockPos origin(BlockPos pos,BlockState s){
        return pos.offset(s.get(FACING).rotateYCounterclockwise(),s.get(PART)%width).offset(s.get(FACING),s.get(PART)/width);
    }
    @Override public BlockState getPlacementState(ItemPlacementContext ctx){
        Direction f=ctx.getHorizontalPlayerFacing().getOpposite();
        for(int i=0;i<width*2;i++){
            BlockPos p=cell(ctx.getBlockPos(),f,i);
            if(!ctx.getWorld().getWorldBorder().contains(p)||!ctx.getWorld().getBlockState(p).canReplace(ctx))return null;
        }
        return getDefaultState().with(FACING,f);
    }
    @Override public void onPlaced(World w,BlockPos p,BlockState s,LivingEntity placer,ItemStack stack){
        if(!w.isClient())for(int i=1;i<width*2;i++)w.setBlockState(cell(p,s.get(FACING),i),s.with(PART,i),Block.NOTIFY_ALL);
    }
    @Override public BlockState onBreak(World w,BlockPos p,BlockState s,PlayerEntity player){
        if(!w.isClient()&&player.isCreative())clearOthers(w,p,s);
        return super.onBreak(w,p,s,player);
    }
    private void clearOthers(World w,BlockPos p,BlockState s){
        BlockPos o=origin(p,s);
        for(int i=0;i<width*2;i++){
            BlockPos q=cell(o,s.get(FACING),i);BlockState other=w.getBlockState(q);
            if(!q.equals(p)&&other.isOf(this)&&other.get(FACING)==s.get(FACING)&&other.get(PART)==i)
                w.setBlockState(q,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS|Block.SKIP_DROPS);
        }
    }
    @Override protected void onStateReplaced(BlockState s,ServerWorld w,BlockPos p,boolean moved){
        if(!w.getBlockState(p).isOf(this)){
            BlockPos anchor=origin(p,s);BlockState a=w.getBlockState(anchor);
            if(!anchor.equals(p)&&a.isOf(this)&&a.get(PART)==0&&a.get(FACING)==s.get(FACING))w.breakBlock(anchor,true);
            else clearOthers(w,p,s);
        }
        super.onStateReplaced(s,w,p,moved);
    }
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
        int x=s.get(PART)%width,z=s.get(PART)/width;
        VoxelShape shape=Block.createCuboidShape(0,12,0,16,14,16);
        if(x==0||x==width-1){double a=x==0?2:11,b=z==0?2:11;
            shape=VoxelShapes.union(shape,Block.createCuboidShape(a,0,b,a+3,12,b+3));}
        return VoxelShapes.createHorizontalFacingShapeMap(shape).get(s.get(FACING));
    }
}
