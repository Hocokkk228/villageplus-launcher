package dev.halloween;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

public final class TallFurnitureBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final MapCodec<TallFurnitureBlock> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            TallFurnitureBlock.<TallFurnitureBlock>createSettingsCodec(),Codec.STRING.fieldOf("kind").forGetter(b->b.kind)).apply(i,TallFurnitureBlock::new));
    public static final IntProperty PART=IntProperty.of("part",0,5),DOOR=IntProperty.of("door",0,4);
    private final String kind;
    private final int width;
    public TallFurnitureBlock(Settings settings,String kind){super(settings);this.kind=kind;width=kind.endsWith("cabinet")?2:1;
        setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH).with(PART,0).with(DOOR,0));}
    @Override protected MapCodec<? extends HorizontalFacingBlock> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState>b){b.add(FACING,PART,DOOR);}
    public BlockPos cell(BlockPos origin,Direction f,int i){return origin.offset(f.rotateYClockwise(),i%width).up(i/width);}
    public BlockPos origin(BlockPos p,BlockState s){return p.offset(s.get(FACING).rotateYCounterclockwise(),s.get(PART)%width).down(s.get(PART)/width);}
    @Override public BlockState getPlacementState(ItemPlacementContext c){
        Direction f=c.getHorizontalPlayerFacing().getOpposite();
        for(int i=0;i<width*3;i++){BlockPos p=cell(c.getBlockPos(),f,i);
            if(p.getY()>c.getWorld().getTopYInclusive()||!c.getWorld().getWorldBorder().contains(p)||!c.getWorld().getBlockState(p).canReplace(c))return null;}
        return getDefaultState().with(FACING,f);
    }
    @Override public void onPlaced(World w,BlockPos p,BlockState s,LivingEntity placer,ItemStack stack){
        if(!w.isClient())for(int i=1;i<width*3;i++)w.setBlockState(cell(p,s.get(FACING),i),s.with(PART,i),Block.NOTIFY_ALL);
    }
    private void clearOthers(World w,BlockPos p,BlockState s){
        BlockPos o=origin(p,s);
        for(int i=0;i<width*3;i++){BlockPos q=cell(o,s.get(FACING),i);BlockState b=w.getBlockState(q);
            if(!q.equals(p)&&b.isOf(this)&&b.get(PART)==i&&b.get(FACING)==s.get(FACING))w.setBlockState(q,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS|Block.SKIP_DROPS);}
    }
    @Override public BlockState onBreak(World w,BlockPos p,BlockState s,PlayerEntity player){
        if(!w.isClient()&&player.isCreative()){
            BlockPos o=origin(p,s);
            if(w.getBlockEntity(o) instanceof CabinetEntity cabinet)net.minecraft.util.ItemScatterer.spawn(w,o,cabinet);
            clearOthers(w,p,s);
        }
        return super.onBreak(w,p,s,player);
    }
    @Override protected void onStateReplaced(BlockState s,ServerWorld w,BlockPos p,boolean moved){
        if(!w.getBlockState(p).isOf(this)){
            if(w.getBlockEntity(p) instanceof CabinetEntity cabinet)net.minecraft.util.ItemScatterer.spawn(w,p,cabinet);
            BlockPos o=origin(p,s);BlockState anchor=w.getBlockState(o);
            if(!o.equals(p)&&anchor.isOf(this)&&anchor.get(PART)==0&&anchor.get(FACING)==s.get(FACING))w.breakBlock(o,true);
            else clearOthers(w,p,s);
        }
        super.onStateReplaced(s,w,p,moved);
    }
    @Override public BlockEntity createBlockEntity(BlockPos p,BlockState s){return width==2&&s.get(PART)==0?new CabinetEntity(p,s):null;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World w,BlockState s,BlockEntityType<T> t){
        return !w.isClient()&&t==CabinetEntity.TYPE?(world,p,state,entity)->((CabinetEntity)entity).tick():null;
    }
    @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit){
        if(width!=2)return ActionResult.PASS;
        if(!w.isClient()&&w.getBlockEntity(origin(p,s)) instanceof CabinetEntity cabinet)player.openHandledScreen(cabinet);
        return ActionResult.SUCCESS;
    }
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
        VoxelShape shape=width==2?Block.createCuboidShape(0,0,2,16,16,14):Block.createCuboidShape(2,0,4,14,16,12);
        return VoxelShapes.createHorizontalFacingShapeMap(shape).get(s.get(FACING));
    }
}
