package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.storage.*;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;

/** Durable assembly transactions: completed stages own consumed components until output is committed. */
public final class MachineAssemblyEntity extends LockableContainerBlockEntity {
 private DefaultedList<ItemStack> inventory=DefaultedList.ofSize(54,ItemStack.EMPTY);
 int product,stage,progress,partProgress,completed;String crafting="";long lastWork=Long.MIN_VALUE;long lastChange;
 public MachineAssemblyEntity(BlockPos p,BlockState s){super(MachineAssembly.TYPE,p,s);}
 public IndustrialMachines.Kind kind(){return IndustrialMachines.Kind.values()[Math.clamp(product,0,4)];}
 String title(){return product<5?kind().title:product==5?"Телевизор":"Радиоприёмник";}
 List<Map.Entry<String,Integer>> bill(){if(product<5)return List.copyOf(IndustrialMachines.bill(kind()).entrySet());var b=new LinkedHashMap<String,Integer>();b.put("appliance_cabinet",1);b.put("circuit_board",product==5?2:1);if(product==5)b.put("picture_tube",1);b.put("loudspeaker",product==5?2:1);return List.copyOf(b.entrySet());}
 public int stage(){return stage;}public float stageFraction(){return progress/(float)duration();}
 int duration(){var b=bill();return stage>=b.size()?1:40+b.get(stage).getValue()*12;}
 void changeProduct(PlayerEntity player){if(stage!=0||progress!=0){player.sendMessage(Text.literal("Сборка уже начата: завершите изделие."),true);return;}if(world.getTime()-lastChange<6)return;lastChange=world.getTime();product=(product+1)%7;crafting="";partProgress=0;sync();player.sendMessage(Text.literal("Сборочная линия: "+title()),true);}
 String status(){var b=bill();return title()+" · "+Math.min(stage+1,b.size())+"/"+b.size()+(stage<b.size()?" · "+b.get(stage).getKey()+" ×"+b.get(stage).getValue():" · выдача изделия");}
 /** One operation per world tick, regardless of packets or the number of nearby workers. */
 boolean work(int amount){
  if(!(world instanceof ServerWorld w)||lastWork==w.getTime())return false;lastWork=w.getTime();var b=bill();
  if(stage>=b.size())return finishOutput();var need=b.get(stage);var component=IndustrialMachines.PARTS.get(need.getKey());
  if(count(component)<need.getValue()){fabricate(need.getKey(),amount);return false;}
  progress=Math.min(duration(),progress+Math.clamp(amount,1,200));
  if(progress==duration()){if(!RailWorks.take(this,component,need.getValue()))return false;stage++;progress=0;}
  w.playSound(null,pos,stage%2==0?SoundEvents.BLOCK_ANVIL_HIT:SoundEvents.BLOCK_IRON_PLACE,SoundCategory.BLOCKS,.25f,.9f);
  var center=Vec3d.ofCenter(pos).add(0,1.25,0);w.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,center.x,center.y,center.z,3,.2,.12,.2,.025);sync();return true;
 }
 boolean fabricate(String name,int amount){
  var recipe=IndustrialMachines.RECIPES.stream().filter(r->r.part().equals(name)).findFirst().orElse(null);if(recipe==null||RailWorks.free(this,IndustrialMachines.PARTS.get(name))<1)return false;
  if(recipe.inputs().entrySet().stream().anyMatch(e->count(e.getKey())<e.getValue()))return false;
  if(!crafting.equals(name)){crafting=name;partProgress=0;}
  partProgress=Math.min(recipe.work(),partProgress+Math.clamp(amount,1,200));
  if(partProgress==recipe.work()){for(var e:recipe.inputs().entrySet())RailWorks.take(this,e.getKey(),e.getValue());RailWorks.add(this,IndustrialMachines.PARTS.get(name),1);partProgress=0;crafting="";}
  sync();return true;
 }
 boolean finishOutput(){var item=product<5?IndustrialMachines.PRODUCTS.get(kind()):product==5?IndustrialElectronics.TELEVISION.asItem():IndustrialElectronics.RADIO.asItem();if(RailWorks.free(this,item)<1)return false;RailWorks.add(this,item,1);completed++;stage=progress=0;sync();return true;}
 void refundInstalled(){if(!(world instanceof ServerWorld w))return;var b=bill();for(int i=0;i<Math.min(stage,b.size());i++){var e=b.get(i);net.minecraft.util.ItemScatterer.spawn(w,pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,new ItemStack(IndustrialMachines.PARTS.get(e.getKey()),e.getValue()));}stage=progress=0;sync();}
 void sync(){markDirty();if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}
 @Override public int size(){return 54;}
 @Override protected DefaultedList<ItemStack> getHeldStacks(){return inventory;}
 @Override protected void setHeldStacks(DefaultedList<ItemStack> stacks){inventory=stacks;}
 @Override protected Text getContainerName(){return Text.literal("Сборочная линия — "+title());}
 @Override protected ScreenHandler createScreenHandler(int id,PlayerInventory inv){return GenericContainerScreenHandler.createGeneric9x6(id,inv,this);}
 @Override protected void writeData(WriteView v){super.writeData(v);Inventories.writeData(v,inventory);v.putInt("product",product);v.putInt("assembly_stage",stage);v.putInt("assembly_work",progress);v.putInt("part_work",partProgress);v.putString("crafting",crafting);v.putInt("completed",completed);}
 @Override protected void readData(ReadView v){super.readData(v);inventory=DefaultedList.ofSize(54,ItemStack.EMPTY);Inventories.readData(v,inventory);product=Math.clamp(v.getInt("product",0),0,6);stage=Math.clamp(v.getInt("assembly_stage",0),0,bill().size());progress=Math.clamp(v.getInt("assembly_work",0),0,duration());partProgress=Math.max(0,v.getInt("part_work",0));crafting=v.getString("crafting","");completed=Math.max(0,v.getInt("completed",0));}
 @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
 @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup){return createNbt(lookup);}
}
