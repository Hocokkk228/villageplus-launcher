package dev.halloween;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.sound.*;
import net.minecraft.state.StateManager;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;

/** Uses Minecraft's candle particles, ambient crackle, projectile ignition and extinguishing. */
public final class CandelabraBlock extends AbstractCandleBlock {
    public static final MapCodec<CandelabraBlock> CODEC=createCodec(CandelabraBlock::new);
    private static final List<Vec3d> FLAMES=List.of(new Vec3d(3/16.,15.5/16.,8/16.),
            new Vec3d(8/16.,17/16.,8/16.),new Vec3d(13/16.,15.5/16.,8/16.));
    public CandelabraBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(LIT,false));}
    @Override protected MapCodec<? extends AbstractCandleBlock> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState>b){b.add(LIT);}
    @Override protected Iterable<Vec3d> getParticleOffsets(BlockState s){return FLAMES;}
    @Override protected boolean isNotLit(BlockState s){return !s.get(LIT);}
    @Override protected ActionResult onUseWithItem(ItemStack stack,BlockState s,World w,BlockPos p,PlayerEntity player,Hand hand,BlockHitResult hit){
        if(!s.get(LIT)&&(stack.isOf(Items.FLINT_AND_STEEL)||stack.isOf(Items.FIRE_CHARGE))){
            if(!w.isClient()){
                w.setBlockState(p,s.with(LIT,true),Block.NOTIFY_ALL);
                w.playSound(null,p,SoundEvents.ITEM_FLINTANDSTEEL_USE,SoundCategory.BLOCKS,1,1);
                if(stack.isOf(Items.FLINT_AND_STEEL))stack.damage(1,player,hand);
                else if(!player.isCreative())stack.decrement(1);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
    @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit){
        if(s.get(LIT)&&player.getMainHandStack().isEmpty()){
            if(!w.isClient())extinguish(player,s,w,p);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
        return Block.createCuboidShape(2,0,4,14,17,12);
    }
}
