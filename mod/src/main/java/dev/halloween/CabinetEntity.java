package dev.halloween;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.*;
import net.minecraft.screen.*;
import net.minecraft.sound.*;
import net.minecraft.storage.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

public final class CabinetEntity extends LockableContainerBlockEntity {
    public static BlockEntityType<CabinetEntity> TYPE;
    private DefaultedList<ItemStack> items=DefaultedList.ofSize(54,ItemStack.EMPTY);
    private int viewers;
    public static void registerType(){TYPE=Registry.register(Registries.BLOCK_ENTITY_TYPE,Identifier.of("halloween","cabinet"),
            FabricBlockEntityTypeBuilder.create(CabinetEntity::new,Registries.BLOCK.get(Identifier.of("halloween","haunted_cabinet")),
                    Registries.BLOCK.get(Identifier.of("halloween","coffin_cabinet"))).build());}
    public CabinetEntity(BlockPos p,BlockState s){super(TYPE,p,s);}
    @Override public int size(){return 54;}
    @Override protected DefaultedList<ItemStack> getHeldStacks(){return items;}
    @Override protected void setHeldStacks(DefaultedList<ItemStack> list){items=list;}
    @Override protected Text getContainerName(){return Text.translatable(getCachedState().getBlock().getTranslationKey());}
    @Override protected ScreenHandler createScreenHandler(int id,PlayerInventory inv){return GenericContainerScreenHandler.createGeneric9x6(id,inv,this);}
    @Override protected void readData(ReadView v){super.readData(v);items=DefaultedList.ofSize(54,ItemStack.EMPTY);Inventories.readData(v,items);}
    @Override protected void writeData(WriteView v){super.writeData(v);Inventories.writeData(v,items);}
    @Override public void onOpen(net.minecraft.entity.ContainerUser user){if(user instanceof PlayerEntity p&&!p.isSpectator()){if(viewers++==0)sound(SoundEvents.BLOCK_CHEST_OPEN);}}
    @Override public void onClose(net.minecraft.entity.ContainerUser user){if(user instanceof PlayerEntity p&&!p.isSpectator()){viewers=Math.max(0,viewers-1);if(viewers==0)sound(SoundEvents.BLOCK_CHEST_CLOSE);}}
    private void sound(SoundEvent sound){if(world!=null)world.playSound(null,pos,sound,SoundCategory.BLOCKS,.65f,.8f);}
    public void tick(){
        if(world==null||(world.getTime()&1)!=0)return;
        BlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof TallFurnitureBlock block))return;
        int frame=state.get(TallFurnitureBlock.DOOR),target=viewers>0?4:0;
        if(frame==target)return;
        frame+=Integer.signum(target-frame);
        for(int i=0;i<6;i++){BlockPos p=block.cell(pos,state.get(TallFurnitureBlock.FACING),i);BlockState part=world.getBlockState(p);
            if(part.isOf(block))world.setBlockState(p,part.with(TallFurnitureBlock.DOOR,frame),Block.NOTIFY_LISTENERS);}
    }
}
