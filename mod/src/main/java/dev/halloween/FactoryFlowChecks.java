package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.network.*;
import net.minecraft.util.math.*;
import net.minecraft.util.Identifier;
/** Real moving workers, vanilla furnace ticks, stock porter and final guard issue. */
final class FactoryFlowChecks {
 static boolean setup,done;static int start;static VillagerEntity smith,armorer,porter,guard;static VillageEraData.Settlement settlement;static java.lang.reflect.Method manufacture,issue;static final List<VillagerEntity> people=new ArrayList<>();static final Map<Item,Integer> initial=new HashMap<>();static net.minecraft.server.network.ServerPlayerEntity observer;
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static VillagerEntity worker(net.minecraft.server.world.ServerWorld w,String profession,Vec3d pos){var v=new VillagerEntity(EntityType.VILLAGER,w);v.setVillagerData(v.getVillagerData().withProfession(Registries.VILLAGER_PROFESSION.getEntry(Registries.VILLAGER_PROFESSION.get(Identifier.of(profession)))));v.setExperience(1);v.reinitializeBrain(w);v.setPosition(pos);v.setPersistent();w.spawnEntity(v);people.add(v);return v;}
 static int stored(net.minecraft.server.world.ServerWorld w,Item item){int count=0;for(int k=0;k<6;k++)if(w.getBlockEntity(VillageEconomy.barrel(settlement,k)) instanceof Inventory inv)count+=inv.count(item);return count;}
 static void register(){if(!Boolean.getBoolean("halloween.factoryFlowTest"))return;ServerTickEvents.END_SERVER_TICK.register(server->{if(done||server.getTicks()<40)return;var w=server.getOverworld();try{
 if(!setup){for(int x=-3;x<=3;x++)for(int z=-2;z<=3;z++)w.setChunkForced(x,z,true);for(int x=-38;x<=25;x++)for(int z=-8;z<=35;z++){w.setBlockState(new BlockPos(x,100,z),Blocks.STONE_BRICKS.getDefaultState(),Block.NOTIFY_LISTENERS);for(int y=101;y<=104;y++)w.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);}
 settlement=new VillageEraData.Settlement(505050,"Factory integration");settlement.era=3;settlement.vault=new VillageEraData.Vault(new BlockPos(-27,101,0),0,"",-1,true);settlement.xp.put("factory_x",0);settlement.xp.put("factory_y",100);settlement.xp.put("factory_z",0);VillageEraData.get(w).settlements.put(settlement.village,settlement);
 for(int k=0;k<6;k++)w.setBlockState(VillageEconomy.barrel(settlement,k),Blocks.BARREL.getDefaultState());for(int x:new int[]{3,7,11})w.setBlockState(new BlockPos(x,101,9),Blocks.BLAST_FURNACE.getDefaultState());for(int x:new int[]{3,7,11})w.setBlockState(new BlockPos(x,101,7),Blocks.BARREL.getDefaultState());
 Inventory stock=(Inventory)w.getBlockEntity(VillageEconomy.barrel(settlement,3));Item[] materials={Items.IRON_INGOT,Items.RAW_IRON,Items.RAW_GOLD,Items.COAL,Items.SPRUCE_LOG,Items.SUGAR_CANE,Items.REDSTONE,Items.FLINT,Items.DIAMOND};int[] amounts={16,64,8,64,8,12,8,8,24};for(int i=0;i<materials.length;i++){stock.setStack(i,new ItemStack(materials[i],amounts[i]));initial.put(materials[i],amounts[i]);}
 smith=worker(w,"minecraft:weaponsmith",new Vec3d(1.5,101,-3.5));armorer=worker(w,"minecraft:armorer",new Vec3d(6.5,101,-3.5));porter=worker(w,"halloween:factory_porter",new Vec3d(-20.5,101,1.5));guard=worker(w,"halloween:guard",new Vec3d(-20.5,101,4.5));guard.addCommandTag("halloween_guard");
 manufacture=VillageEconomy.class.getDeclaredMethod("manufacture",net.minecraft.server.world.ServerWorld.class,VillageEraData.Settlement.class,VillagerEntity.class);manufacture.setAccessible(true);issue=VillageEconomy.class.getDeclaredMethod("issue",net.minecraft.server.world.ServerWorld.class,VillageEraData.Settlement.class,List.class,long.class);issue.setAccessible(true);
 observer=new ServerPlayerEntity(server,w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"FactoryObserver"),net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());observer.networkHandler=new ServerPlayNetworkHandler(server,new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),observer,ConnectedClientData.createDefault(observer.getGameProfile(),false));observer.setPosition(-5,110,0);w.getPlayers().add(observer);server.getTickManager().setTickRate(200);w.setTimeOfDay(2000);start=server.getTicks();setup=true;return;}
 int age=server.getTicks()-start;
 if(age%20==0){for(var v:people){VillageTasks.hold(v);if(v.isSleeping())v.wakeUp();}manufacture.invoke(null,w,settlement,smith);manufacture.invoke(null,w,settlement,armorer);VillageEconomy.haulFactory(w,settlement,porter);}
 if(age%400==0)org.slf4j.LoggerFactory.getLogger("factory-flow-test").info("age={} smith={} armorer={} porter={} stored musket={} revolver={} rapier={} sabre={} ammo={} diamonds={}",age,smith.getBlockPos(),armorer.getBlockPos(),porter.getBlockPos(),stored(w,VillageItems.MUSKET),stored(w,VillageItems.REVOLVER),stored(w,VillageItems.RAPIER),stored(w,VillageItems.SABRE),stored(w,VillageItems.MUSKET_CARTRIDGE),stored(w,Items.DIAMOND));
 var armour=List.of(Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS);
 if(stored(w,VillageItems.MUSKET)>0&&stored(w,VillageItems.REVOLVER)>0&&stored(w,VillageItems.RAPIER)>0&&stored(w,VillageItems.SABRE)>0&&stored(w,VillageItems.MUSKET_CARTRIDGE)>=8&&armour.stream().allMatch(item->stored(w,item)>0)){
 issue.invoke(null,w,settlement,List.of(guard),123L);require(guard.getMainHandStack().getItem() instanceof MusketItem,"guard equips real factory firearm");require(guard.getInventory().count(VillageItems.MUSKET_CARTRIDGE)>=8,"guard receives eight real cartridges");for(var slot:List.of(net.minecraft.entity.EquipmentSlot.HEAD,net.minecraft.entity.EquipmentSlot.CHEST,net.minecraft.entity.EquipmentSlot.LEGS,net.minecraft.entity.EquipmentSlot.FEET))require(armour.contains(guard.getEquippedStack(slot).getItem()),"guard equips produced diamond armour");require(stored(w,Items.DIAMOND)==0,"24 real diamonds consumed by four armour recipes");require(porter.getEntityPos().squaredDistanceTo(new Vec3d(-20.5,101,1.5))>16,"porter actually moved between treasury and factory");org.slf4j.LoggerFactory.getLogger("factory-flow-test").info("PASS real factory chain in {} ticks: moving porter, spruce→sticks, cane→paper, ore→gold, powder→cartridges, musket/revolver/rapier/sabre and full diamond armour; guard supplied from returned stock",age);done=true;w.getPlayers().remove(observer);server.stop(false);
 }
 require(age<18000,"factory delivery/production pipeline stalled");
 }catch(Throwable e){done=true;org.slf4j.LoggerFactory.getLogger("factory-flow-test").error("FAIL",e);if(observer!=null)w.getPlayers().remove(observer);server.stop(false);}});}
}
