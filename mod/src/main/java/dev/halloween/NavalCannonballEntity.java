package dev.halloween;

import java.util.UUID;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.*;
import net.minecraft.util.hit.*;
import net.minecraft.world.World;

/** Visible, gravity-affected iron shot with vanilla swept projectile collision. */
public final class NavalCannonballEntity extends ThrownItemEntity {
 UUID sourceShip;private boolean impacted;
 public NavalCannonballEntity(EntityType<? extends NavalCannonballEntity> type,World world){super(type,world);}
 @Override protected Item getDefaultItem(){return NavalCannons.BALL;}
 @Override protected double getGravity(){return .035;}
 @Override protected boolean canHit(Entity entity){return !(entity instanceof RailVehicleEntity)&&!(entity instanceof RailSeatEntity)&&super.canHit(entity);}
 @Override public void tick(){super.tick();if(!(getEntityWorld() instanceof ServerWorld w)||isRemoved())return;
  if(age>180||isTouchingWater()){if(isTouchingWater())w.spawnParticles(ParticleTypes.SPLASH,getX(),getY(),getZ(),32,.5,.2,.5,.3);discard();return;}
  // Giant ship entity bounds contain empty air. Test actual physical hull/deck geometry instead.
  var end=getEntityPos();var start=end.subtract(getVelocity());var area=getBoundingBox().stretch(getVelocity().negate()).expand(.2);
  for(var ship:RailVehicleEntity.nearby(w,area)){if(!ship.isShip()||ship.getUuid().equals(sourceShip))continue;for(var box:ship.collisions(area))if(box.expand(.15).raycast(start,end).isPresent()){ship.damage(w,getDamageSources().thrown(this,getOwner()),35);impact(w);return;}}
 }
 @Override protected void onCollision(HitResult hit){if(getEntityWorld() instanceof ServerWorld w&&hit.getType()!=HitResult.Type.MISS){setPosition(hit.getPos());if(hit instanceof EntityHitResult target)target.getEntity().damage(w,getDamageSources().thrown(this,getOwner()),22);impact(w);}}
 private void impact(ServerWorld w){if(impacted||isRemoved())return;impacted=true;
  w.createExplosion(this,getX(),getY(),getZ(),2.8f,false,World.ExplosionSourceType.TNT);
  BlastEffects.burst(w,getEntityPos(),BlastEffects.Kind.IMPACT);
  discard();
 }
 @Override protected void writeCustomData(WriteView view){super.writeCustomData(view);if(sourceShip!=null)view.putString("source_ship",sourceShip.toString());}
 @Override protected void readCustomData(ReadView view){super.readCustomData(view);try{sourceShip=UUID.fromString(view.getString("source_ship",""));}catch(IllegalArgumentException e){sourceShip=null;}}
}
