package dev.halloween;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.*;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
final class ArmorVisualCheck {
 static boolean done=false;
 static void register(){ServerTickEvents.END_SERVER_TICK.register(server->{
  if(done||server.getPlayerManager().getPlayerList().isEmpty())return;done=true;
  var p=server.getPlayerManager().getPlayerList().getFirst();var w=server.getOverworld();BlockPos base=p.getBlockPos().add(0,12,0);
  for(int x=-4;x<=4;x++)for(int z=-3;z<=8;z++){w.setBlockState(base.add(x,-1,z),Blocks.STONE_BRICKS.getDefaultState());for(int y=0;y<5;y++)w.setBlockState(base.add(x,y,z),Blocks.AIR.getDefaultState());}
  p.networkHandler.requestTeleport(base.getX()+.5,base.getY(),base.getZ()+.5,0,0);
  w.setBlockState(base.add(3,0,3),VillageStatue.BLOCK.getDefaultState());w.setBlockState(base.add(3,2,3),VillageStatue.TOP.getDefaultState());
  var v=new VillagerEntity(EntityType.VILLAGER,w);v.setPosition(base.getX()+.5,base.getY(),base.getZ()+3.5);v.setYaw(180);v.setAiDisabled(true);v.setPersistent();
  v.equipStack(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));v.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));v.equipStack(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));v.equipStack(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));w.spawnEntity(v);
 });}
 private ArmorVisualCheck(){}
}
