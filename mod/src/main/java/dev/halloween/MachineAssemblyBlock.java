package dev.halloween;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
final class MachineAssemblyBlock extends BlockWithEntity {
 static final MapCodec<MachineAssemblyBlock> CODEC=createCodec(MachineAssemblyBlock::new);
 static final EnumProperty<Direction> FACING=Properties.HORIZONTAL_FACING;
 MachineAssemblyBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.SOUTH));}
 @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return CODEC;}
 @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING);}
 @Override public BlockState getPlacementState(ItemPlacementContext c){return getDefaultState().with(FACING,c.getHorizontalPlayerFacing());}
 @Override public BlockEntity createBlockEntity(BlockPos p,BlockState s){return new MachineAssemblyEntity(p,s);}
 @Override protected BlockRenderType getRenderType(BlockState s){return BlockRenderType.MODEL;}
 @Override protected ActionResult onUseWithItem(ItemStack stack,BlockState s,World w,BlockPos p,PlayerEntity player,Hand hand,BlockHitResult hit){if(!stack.isOf(RailContent.HAMMER))return ActionResult.PASS;if(w.getBlockEntity(p) instanceof MachineAssemblyEntity line){if(!w.isClient()){if(player.isSneaking())line.changeProduct(player);else{line.work(8);player.sendMessage(Text.literal(line.status()),true);}}return ActionResult.SUCCESS;}return ActionResult.PASS;}
 @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit){if(w.getBlockEntity(p) instanceof MachineAssemblyEntity line){if(!w.isClient()){if(player.isSneaking())line.changeProduct(player);else player.openHandledScreen(line);}return ActionResult.SUCCESS;}return ActionResult.PASS;}
 @Override protected void onStateReplaced(BlockState s,ServerWorld w,BlockPos p,boolean moved){if(!w.getBlockState(p).isOf(this)&&w.getBlockEntity(p) instanceof MachineAssemblyEntity line){line.refundInstalled();net.minecraft.util.ItemScatterer.spawn(w,p,line);}super.onStateReplaced(s,w,p,moved);}
}
