package dev.halloween;

import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Fixed broadside guns. All ammunition, range and reload checks happen on the server. */
final class NavalCannons {
 static Item BALL,FIELD_GUN;static EntityType<FieldCannonEntity> FIELD_ENTITY;static EntityType<NavalCannonballEntity> BALL_ENTITY;
 static void register(){
  var key=RegistryKey.of(RegistryKeys.ITEM,Identifier.of("halloween","cannonball"));BALL=Registry.register(Registries.ITEM,key,new Item(new Item.Settings().registryKey(key)));
  var entityKey=RegistryKey.of(RegistryKeys.ENTITY_TYPE,Identifier.of("halloween","cannonball"));BALL_ENTITY=Registry.register(Registries.ENTITY_TYPE,entityKey,EntityType.Builder.<NavalCannonballEntity>create(NavalCannonballEntity::new,SpawnGroup.MISC).dimensions(.35f,.35f).maxTrackingRange(12).trackingTickInterval(1).build(entityKey));
  var fieldKey=RegistryKey.of(RegistryKeys.ENTITY_TYPE,Identifier.of("halloween","field_cannon"));FIELD_ENTITY=Registry.register(Registries.ENTITY_TYPE,fieldKey,EntityType.Builder.<FieldCannonEntity>create(FieldCannonEntity::new,SpawnGroup.MISC).dimensions(1.85f,1.4f).maxTrackingRange(10).trackingTickInterval(1).build(fieldKey));
  var itemKey=RegistryKey.of(RegistryKeys.ITEM,Identifier.of("halloween","field_cannon"));FIELD_GUN=Registry.register(Registries.ITEM,itemKey,new Item(new Item.Settings().registryKey(itemKey).maxCount(1)){
   @Override public ActionResult useOnBlock(ItemUsageContext c){if(!(c.getWorld() instanceof ServerWorld w))return ActionResult.SUCCESS;var p=c.getBlockPos().offset(c.getSide());var box=new Box(p.getX()-.425,p.getY(),p.getZ()-.425,p.getX()+1.425,p.getY()+1.4,p.getZ()+1.425);if(!w.isSpaceEmpty(box)||!w.getFluidState(p).isEmpty()||!w.getBlockState(p.down()).isSolidBlock(w,p.down()))return ActionResult.FAIL;var gun=FIELD_ENTITY.create(w,SpawnReason.EVENT);if(gun==null)return ActionResult.FAIL;gun.setPosition(p.getX()+.5,p.getY(),p.getZ()+.5);gun.setYaw(c.getPlayerYaw());w.spawnEntity(gun);if(c.getPlayer()!=null&&!c.getPlayer().isCreative())c.getStack().decrement(1);return ActionResult.SUCCESS;}
  });
  net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e->{e.add(BALL);e.add(FIELD_GUN);});
 }
 static int count(int kind){return kind==3?2:4;}
 static int side(int index){return index%2==0?-1:1;}
 static Vec3d position(int kind,int index){return NavalGeometry.local(kind,side(index)*2.85,3.65,kind==3?7.6:index<2?2.9:7.6);}
 static Vec3d muzzle(int kind,int index){return position(kind,index).add(side(index)*1.25*NavalGeometry.sx(kind),.20,0);}
 static Box box(int kind,int index){var p=position(kind,index);return new Box(p.add(-1.1*NavalGeometry.sx(kind),-.65,-.7*NavalGeometry.sz(kind)),p.add(1.1*NavalGeometry.sx(kind),.6,.7*NavalGeometry.sz(kind)));}
 static ActionResult interact(RailVehicleEntity ship,PlayerEntity player,Hand hand){
  var start=RailPassengerNetwork.toLocal(ship,player.getEyePos());var end=start.add(RailGeometry.rotate(player.getRotationVec(1),-ship.getYaw()).multiply(player.getEntityInteractionRange()));
  int chosen=-1;double distance=Double.MAX_VALUE;
  for(int i=0;i<count(ship.kind());i++){var hit=box(ship.kind(),i).raycast(start,end);if(hit.isPresent()&&hit.get().squaredDistanceTo(start)<distance){distance=hit.get().squaredDistanceTo(start);chosen=i;}}
  if(chosen<0)return ActionResult.PASS;if(hand!=Hand.MAIN_HAND)return ActionResult.SUCCESS;
  return use(ship,player,chosen);
 }
 static ActionResult use(RailVehicleEntity ship,PlayerEntity player,int index){
  if(!(ship.getEntityWorld() instanceof ServerWorld w)||index<0||index>=count(ship.kind()))return ActionResult.FAIL;
  if(player.getEyePos().squaredDistanceTo(RailGeometry.rotate(position(ship.kind(),index),ship.getYaw()).add(ship.getEntityPos()))>36)return ActionResult.FAIL;
  var state=ship.naval();long now=w.getTime();
  if(now<state.gunReady[index]){player.sendMessage(Text.literal("Пушка перезаряжается…"),true);return ActionResult.SUCCESS;}
  if(!state.gunLoaded[index]){
   if(!player.isCreative()&&(!has(player,BALL)||!has(player,Items.GUNPOWDER))){player.sendMessage(Text.literal("Нужны ядро и порох. ПКМ — зарядить, повторный ПКМ — выстрел."),true);return ActionResult.SUCCESS;}
   if(!player.isCreative()){take(player,BALL);take(player,Items.GUNPOWDER);}state.gunLoaded[index]=true;state.gunReady[index]=now+60;
   sound(w,ship,position(ship.kind(),index),SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(),.8f,.6f);player.sendMessage(Text.literal("Ядро заряжено. Подготовка — 3 секунды."),true);return ActionResult.SUCCESS;
  }
  fire(ship,player,index);return ActionResult.SUCCESS;
 }
 static NavalCannonballEntity fire(RailVehicleEntity ship,PlayerEntity player,int index){
  var w=(ServerWorld)ship.getEntityWorld();var s=ship.naval();s.gunLoaded[index]=false;s.gunReady[index]=w.getTime()+80;s.gunSmokeUntil[index]=w.getTime()+55;
  var origin=RailGeometry.rotate(muzzle(ship.kind(),index),ship.getYaw()).add(ship.getEntityPos());
  var direction=RailGeometry.rotate(new Vec3d(side(index),.12,0).normalize(),ship.getYaw());
  var ball=BALL_ENTITY.create(w,SpawnReason.EVENT);if(ball==null)return null;ball.setOwner(player);ball.sourceShip=ship.getUuid();ball.setPosition(origin);ball.setVelocity(direction.multiply(2.4).add(ship.getVelocity()));w.spawnEntity(ball);ship.cannonFired(index);
  BlastEffects.muzzle(w,origin,direction);
  sound(w,ship,muzzle(ship.kind(),index),SoundEvents.ENTITY_GENERIC_EXPLODE.value(),2.7f,.65f);sound(w,ship,muzzle(ship.kind(),index),SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,2f,.55f);
  return ball;
 }
 static void tick(ServerWorld w,RailVehicleEntity ship){if(ship.age%3!=0)return;for(int i=0;i<count(ship.kind());i++)if(ship.naval().gunSmokeUntil[i]>w.getTime()){
  var p=RailGeometry.rotate(muzzle(ship.kind(),i),ship.getYaw()).add(ship.getEntityPos());w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,p.x,p.y,p.z,2,.15,.08,.15,.015);
 }}
 static void sound(ServerWorld w,RailVehicleEntity ship,Vec3d local,SoundEvent sound,float volume,float pitch){var p=RailGeometry.rotate(local,ship.getYaw()).add(ship.getEntityPos());w.playSound(null,p.x,p.y,p.z,sound,SoundCategory.BLOCKS,volume,pitch);}
 private static boolean has(PlayerEntity p,Item item){return p.getInventory().contains(new ItemStack(item));}
 private static void take(PlayerEntity p,Item item){for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(item)){p.getInventory().getStack(i).decrement(1);p.getInventory().markDirty();return;}}
 private NavalCannons(){}
}
