package dev.halloween;

import java.util.UUID;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.storage.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Movable field carriage: physical ground movement, player towing, real ammunition and shot. */
public final class FieldCannonEntity extends Entity {
 private static final TrackedData<Float> ELEVATION=DataTracker.registerData(FieldCannonEntity.class,TrackedDataHandlerRegistry.FLOAT);
 private static final TrackedData<Long> SHOT=DataTracker.registerData(FieldCannonEntity.class,TrackedDataHandlerRegistry.LONG);
 private final PositionInterpolator interpolator=new PositionInterpolator(this,3);
 UUID towing;boolean loaded;long ready,smokeUntil;float health=40;
 public FieldCannonEntity(EntityType<? extends FieldCannonEntity> type,World world){super(type,world);}
 @Override protected void initDataTracker(DataTracker.Builder b){b.add(ELEVATION,7f);b.add(SHOT,-100L);}
 @Override public PositionInterpolator getInterpolator(){return interpolator;}
 public float elevation(){return dataTracker.get(ELEVATION);}public long shot(){return dataTracker.get(SHOT);}
 @Override public boolean canHit(){return true;}
 @Override public boolean isCollidable(Entity e){return true;}
 @Override public boolean isPushable(){return true;}
 @Override public float getStepHeight(){return .6f;}
 @Override public void tick(){super.tick();if(getEntityWorld().isClient()){interpolator.tick();return;}var w=(ServerWorld)getEntityWorld();
  var velocity=getVelocity();if(towing!=null){var player=w.getPlayerByUuid(towing);if(player==null||!player.isAlive()||player.hasVehicle()||player.squaredDistanceTo(this)>25)towing=null;else{
   var direction=player.getRotationVec(1).multiply(1,0,1).normalize();var target=player.getEntityPos().subtract(direction.multiply(1.6));var delta=target.subtract(getEntityPos());double distance=delta.horizontalLength();var movement=distance>.10?delta.multiply(Math.min(.14,distance*.3)/Math.max(.01,distance)):Vec3d.ZERO;velocity=new Vec3d(movement.x,velocity.y,movement.z);setYaw(player.getYaw());
  }}
  if(towing==null)velocity=velocity.multiply(.78,1,.78);if(!hasNoGravity())velocity=velocity.add(0,-.04,0);move(MovementType.SELF,velocity);setVelocity(velocity.x,isOnGround()?0:velocity.y,velocity.z);velocityDirty=true;
  if(age%4==0&&velocity.horizontalLength()>.02&&isOnGround())w.playSound(null,getX(),getY(),getZ(),SoundEvents.BLOCK_WOOD_STEP,SoundCategory.BLOCKS,.28f,.6f);
  if(age%3==0&&smokeUntil>w.getTime()){var muzzle=muzzle();w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,muzzle.x,muzzle.y,muzzle.z,2,.15,.08,.15,.018);}
 }
 Vec3d aim(){double radians=Math.toRadians(elevation());return RailGeometry.rotate(new Vec3d(0,Math.sin(radians),Math.cos(radians)),getYaw());}
 Vec3d muzzle(){return getEntityPos().add(aim().multiply(1.55)).add(0,1.05,0);}
 @Override public boolean handleAttack(Entity attacker){if(attacker instanceof PlayerEntity player&&player.getMainHandStack().isEmpty()){
  if(getEntityWorld() instanceof ServerWorld&&towing==null&&player.squaredDistanceTo(this)<25){setYaw(player.getYaw());if(player.isSneaking())dataTracker.set(ELEVATION,elevation()>=35?0:Math.min(40,elevation()+10));player.sendMessage(Text.literal("Наведение: "+Math.round(elevation())+"°. Shift + ЛКМ — поднять ствол; ПКМ — зарядить / огонь."),true);}return true;}return false;}
 @Override public ActionResult interact(PlayerEntity player,Hand hand){
  if(getEntityWorld().isClient())return ActionResult.SUCCESS;if(hand!=Hand.MAIN_HAND||player.squaredDistanceTo(this)>25)return ActionResult.FAIL;
  var w=(ServerWorld)getEntityWorld();
  if(player.isSneaking()){
   if(towing!=null&&!towing.equals(player.getUuid())){player.sendMessage(Text.literal("Пушку уже перекатывают."),true);return ActionResult.FAIL;}
   towing=towing==null?player.getUuid():null;player.sendMessage(Text.literal(towing==null?"Лафет установлен.":"Перекатывание: идите вперёд. Shift + ПКМ — оставить."),true);return ActionResult.SUCCESS;
  }
  if(towing!=null){player.sendMessage(Text.literal("Сначала установите лафет: Shift + ПКМ."),true);return ActionResult.SUCCESS;}
  // A separate aiming step prevents an ordinary right-click from unexpectedly firing.
  if(player.getMainHandStack().isOf(Items.STICK)){setYaw(player.getYaw());dataTracker.set(ELEVATION,MathHelper.clamp(-player.getPitch(),-5,40));player.sendMessage(Text.literal("Наведение: "+Math.round(elevation())+"°. Уберите палку для заряжания и выстрела."),true);return ActionResult.SUCCESS;}
  if(w.getTime()<ready){player.sendMessage(Text.literal("Пушка перезаряжается…"),true);return ActionResult.SUCCESS;}
  if(!loaded){if(!player.isCreative()&&(!player.getInventory().contains(new ItemStack(NavalCannons.BALL))||!player.getInventory().contains(new ItemStack(Items.GUNPOWDER)))){player.sendMessage(Text.literal("Нужны ядро и порох. ЛКМ пустой рукой — навести. Shift + ПКМ — катить."),true);return ActionResult.SUCCESS;}
   if(!player.isCreative()){take(player,NavalCannons.BALL);take(player,Items.GUNPOWDER);}loaded=true;ready=w.getTime()+60;w.playSound(null,getX(),getY(),getZ(),SoundEvents.BLOCK_IRON_PLACE,SoundCategory.BLOCKS,.8f,.6f);player.sendMessage(Text.literal("Ядро заряжено. Через 3 секунды ПКМ — огонь."),true);return ActionResult.SUCCESS;
  }
  fire(player);return ActionResult.SUCCESS;
 }
 NavalCannonballEntity fire(PlayerEntity player){var w=(ServerWorld)getEntityWorld();if(towing!=null)return null;var ball=NavalCannons.BALL_ENTITY.create(w,SpawnReason.EVENT);if(ball==null)return null;ball.setOwner(player);var muzzle=muzzle();ball.setPosition(muzzle);ball.setVelocity(aim().multiply(2.4));w.spawnEntity(ball);loaded=false;ready=w.getTime()+80;smokeUntil=w.getTime()+55;dataTracker.set(SHOT,w.getTime());setVelocity(aim().multiply(-.12,0,-.12));
  BlastEffects.muzzle(w,muzzle,aim());
  w.playSound(null,getX(),getY(),getZ(),SoundEvents.ENTITY_GENERIC_EXPLODE,SoundCategory.BLOCKS,2.7f,.65f);return ball;
 }
 private static void take(PlayerEntity p,Item item){for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(item)){p.getInventory().getStack(i).decrement(1);p.getInventory().markDirty();return;}}
 @Override public boolean damage(ServerWorld w,DamageSource source,float amount){if(isInvulnerable())return false;health-=amount;if(health<=0){dropStack(w,new ItemStack(NavalCannons.FIELD_GUN));if(loaded)dropStack(w,new ItemStack(NavalCannons.BALL));discard();}return true;}
 @Override protected void writeCustomData(WriteView v){v.putBoolean("loaded",loaded);v.putLong("ready",ready);v.putFloat("health",health);v.putFloat("elevation",elevation());}
 @Override protected void readCustomData(ReadView v){loaded=v.getBoolean("loaded",false);ready=v.getLong("ready",0);health=v.getFloat("health",40);dataTracker.set(ELEVATION,MathHelper.clamp(v.getFloat("elevation",7),-5,40));}
}
