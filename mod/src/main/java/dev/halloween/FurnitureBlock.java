package dev.halloween;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
import net.minecraft.world.tick.ScheduledTickView;

public final class FurnitureBlock extends HorizontalFacingBlock {
    public static final MapCodec<FurnitureBlock> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            FurnitureBlock.<FurnitureBlock>createSettingsCodec(),Codec.STRING.fieldOf("kind").forGetter(b->b.kind)).apply(i,FurnitureBlock::new));
    public static final EnumProperty<DoubleBlockHalf> HALF=Properties.DOUBLE_BLOCK_HALF;
    private final String kind;
    public FurnitureBlock(Settings settings,String kind) {
        super(settings);this.kind=kind;
        setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH).with(HALF,DoubleBlockHalf.LOWER));
    }
    public boolean isSeat() { return kind.endsWith("chair")||kind.endsWith("cushion")||kind.equals("witch_stool"); }
    private boolean tall() { return kind.endsWith("cabinet")||kind.equals("pumpkin_scarecrow")||kind.equals("reaper_statue"); }
    @Override protected MapCodec<? extends HorizontalFacingBlock> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState>b){b.add(FACING,HALF);}
    @Override public BlockState getPlacementState(ItemPlacementContext ctx) {
        if(tall()&&(!ctx.getWorld().getBlockState(ctx.getBlockPos().up()).canReplace(ctx)||ctx.getBlockPos().getY()+1>=ctx.getWorld().getTopYInclusive()+1))return null;
        return getDefaultState().with(FACING,ctx.getHorizontalPlayerFacing().getOpposite());
    }
    @Override public void onPlaced(World w,BlockPos p,BlockState s,LivingEntity placer,ItemStack stack) {
        if(tall())w.setBlockState(p.up(),s.with(HALF,DoubleBlockHalf.UPPER),Block.NOTIFY_ALL);
    }
    @Override protected BlockState getStateForNeighborUpdate(BlockState s,WorldView w,ScheduledTickView ticks,BlockPos p,Direction d,BlockPos n,BlockState neighbor,Random r) {
        if(tall()&&d==(s.get(HALF)==DoubleBlockHalf.LOWER?Direction.UP:Direction.DOWN)
                &&(!neighbor.isOf(this)||neighbor.get(HALF)==s.get(HALF)))return Blocks.AIR.getDefaultState();
        return s;
    }
    @Override public BlockState onBreak(World w,BlockPos p,BlockState s,PlayerEntity player) {
        if(!w.isClient()&&player.isCreative()&&tall()) {
            BlockPos other=s.get(HALF)==DoubleBlockHalf.UPPER?p.down():p.up();
            if(w.getBlockState(other).isOf(this))w.setBlockState(other,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);
        }
        return super.onBreak(w,p,s,player);
    }
    @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit) {
        if(!isSeat()||player.isSneaking()||player.hasVehicle())return ActionResult.PASS;
        if(!w.isClient()) {
            if(!w.getEntitiesByClass(SeatEntity.class,new Box(p),e->e.isAlive()).isEmpty())return ActionResult.SUCCESS;
            SeatEntity seat=new SeatEntity(FurnitureContent.SEAT,w);
            double height=kind.endsWith("cushion")?.25:kind.equals("witch_stool")?.47:.54;
            seat.setPosition(p.getX()+.5,p.getY()+height,p.getZ()+.5);seat.setYaw(s.get(FACING).getPositiveHorizontalDegrees());
            if(w.spawnEntity(seat)&&!player.startRiding(seat))seat.discard();
        }
        return ActionResult.SUCCESS;
    }
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c) {
        if(kind.endsWith("cushion"))return Block.createCuboidShape(1,0,1,15,4,15);
        if(kind.equals("witch_stool"))return Block.createCuboidShape(3,0,3,13,8,13);
        if(tall())return Block.createCuboidShape(2,0,2,14,s.get(HALF)==DoubleBlockHalf.UPPER?14:16,14);
        return Block.createCuboidShape(1,0,1,15,kind.equals("witch_table")?12:16,15);
    }
    @Override protected VoxelShape getCollisionShape(BlockState s,BlockView w,BlockPos p,ShapeContext c) {
        if(isSeat()) {
            double h=kind.endsWith("cushion")?4:8;
            VoxelShape shape=Block.createCuboidShape(2,0,2,14,h,14);
            if(kind.endsWith("chair"))shape=VoxelShapes.union(shape,Block.createCuboidShape(2,8,12,14,16,14));
            return VoxelShapes.createHorizontalFacingShapeMap(shape).get(s.get(FACING));
        }
        return getOutlineShape(s,w,p,c);
    }
}
