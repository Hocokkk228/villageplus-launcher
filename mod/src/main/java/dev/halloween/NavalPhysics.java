package dev.halloween;

import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.*;
import net.minecraft.sound.*;
import net.minecraft.storage.*;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;

/** Server-authoritative free navigation. Water is sampled, never removed or replaced. */
final class NavalPhysics {
 private static final ChunkTicketType TICKET=new ChunkTicketType(100,ChunkTicketType.FOR_LOADING|ChunkTicketType.FOR_SIMULATION);
 static final class State {
  double throttle,turnRate;float rudder;long lastControl;int contactCooldown;boolean blocked;
  final boolean[] gunLoaded=new boolean[4];final long[] gunReady=new long[4],gunSmokeUntil=new long[4];
  final SimpleInventory cargo;final SimpleInventory[] barrels=new SimpleInventory[8];
  State(RailVehicleEntity ship){for(int i=0;i<barrels.length;i++){final int index=i;barrels[i]=new SimpleInventory(27){@Override public boolean canPlayerUse(PlayerEntity p){var center=RailGeometry.rotate(NavalGeometry.barrelBoxes(ship.kind()).get(index).getCenter(),ship.getYaw()).add(ship.getEntityPos());return !ship.isRemoved()&&p.getEntityWorld()==ship.getEntityWorld()&&p.getEyePos().squaredDistanceTo(center)<36;}@Override public void onClose(net.minecraft.entity.ContainerUser user){if(user instanceof PlayerEntity p&&p.getEntityWorld() instanceof ServerWorld w)w.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.BLOCK_BARREL_CLOSE,SoundCategory.BLOCKS,.5f,1);}};}cargo=new SimpleInventory(NavalGeometry.cargoSize(ship.kind())){
   @Override public boolean canPlayerUse(PlayerEntity p){return !ship.isRemoved()&&p.getEntityWorld()==ship.getEntityWorld()&&p.getEntityPos().squaredDistanceTo(RailGeometry.rotate(NavalGeometry.cargo(ship.kind()),ship.getYaw()).add(ship.getEntityPos()))<64;}
  };}
 }
 static double surface(ServerWorld w,double x,double z,double nearY){
  int bx=MathHelper.floor(x),bz=MathHelper.floor(z);if(!w.isChunkLoaded(ChunkPos.toLong(bx>>4,bz>>4)))return Double.NaN;
  int start=MathHelper.floor(nearY)+3;
  for(int y=start;y>=start-9;y--){var p=new BlockPos(bx,y,bz);var fluid=w.getFluidState(p);if(fluid.isIn(FluidTags.WATER)&&!w.getFluidState(p.up()).isIn(FluidTags.WATER))return y+fluid.getHeight(w,p);}
  return Double.NaN;
 }
 static void tick(ServerWorld w,RailVehicleEntity ship){
  NavalCannons.tick(w,ship);var state=ship.naval();if(state.contactCooldown>0)state.contactCooldown--;
  if(ship.age%20==0&&w.getPlayers().stream().anyMatch(p->p.squaredDistanceTo(ship)<160*160)){
   var cp=new ChunkPos(ship.getBlockPos());for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.getChunkManager().addTicket(TICKET,new ChunkPos(cp.x+x,cp.z+z),2);
  }
  if(w.getTime()-state.lastControl>10)state.rudder=0;
  ship.rudder(state.rudder);
  double maximum=ship.kind()==3?.46:.38;
  double wind=.82+.18*Math.sin(w.getTime()/5000d+ship.getYaw()*.017);
  double desired=ship.anchored()||!ship.sailsRaised()?0:state.throttle*maximum*wind;
  double acceleration=ship.anchored()?.008:Math.abs(desired)<Math.abs(ship.speed)?.0028:.0017;
  ship.speed+=Math.clamp(desired-ship.speed,-acceleration,acceleration);if(Math.abs(ship.speed)<.0005)ship.speed=0;
  double turn=state.rudder*Math.min(1,Math.abs(ship.speed)/.2)*Math.signum(ship.speed)*.85;
  state.turnRate+=(turn-state.turnRate)*.12;
  float yaw=MathHelper.wrapDegrees(ship.getYaw()+(float)state.turnRate);
  var offset=RailGeometry.rotate(new Vec3d(0,0,ship.speed),yaw);var proposed=ship.getEntityPos().add(offset);
  double water=surface(w,proposed.x,proposed.z,ship.getY());
  boolean afloat=Double.isFinite(water);
  if(afloat){
   double wave=Math.sin((w.getTime()+Math.floorMod(ship.getUuid().hashCode(),300))*.07)*.045;
   proposed=new Vec3d(proposed.x,MathHelper.lerp(.12,ship.getY(),water+wave),proposed.z);
  }
  boolean clear=afloat&&clear(w,ship,proposed,yaw);state.blocked=!clear;
  if(clear){ship.setYaw(yaw);ship.setPosition(proposed);ship.setVelocity(offset.x,0,offset.z);}
  else{
   if(Math.abs(ship.speed)>=.16&&hitsLand(w,ship,proposed,yaw,offset)){
    ship.damage(w,w.getDamageSources().generic(),ship.hullHealth()+1);
    if(ship.isRemoved())return;
   }
   ship.speed=0;state.turnRate*=.5;ship.setVelocity(Vec3d.ZERO);
   if(state.contactCooldown==0){state.contactCooldown=40;w.playSound(null,ship.getX(),ship.getY(),ship.getZ(),SoundEvents.BLOCK_WOOD_HIT,SoundCategory.BLOCKS,.6f,.65f);}
  }
  if(Math.abs(ship.speed)>.07)for(var entity:w.getOtherEntities(ship,RailGeometry.worldBox(NavalGeometry.hull(ship.kind()),ship.getEntityPos(),ship.getYaw()),e->e instanceof LivingEntity&&e.isAlive()&&!e.hasVehicle()&&!e.isSpectator())){
   var local=RailPassengerNetwork.toLocal(ship,entity.getEntityPos());if(NavalGeometry.inside(ship,local)||RailPassengerNetwork.protectedPassenger((LivingEntity)entity))continue;
   var push=RailGeometry.rotate(new Vec3d(Math.copySign(.24,local.x==0?1:local.x),.08,Math.copySign(.1,ship.speed)),ship.getYaw());entity.setVelocity(push);entity.velocityDirty=true;
  }
  ship.navalFlags(ship.anchored(),ship.sailsRaised(),ship.anchored()&&Math.abs(ship.speed)<.025);
  if(Math.abs(ship.speed)>.06&&ship.age%3==0){
   for(int side:new int[]{-1,1}){var bow=RailGeometry.rotate(NavalGeometry.local(ship.kind(),side*.9,.05,13),ship.getYaw()).add(ship.getEntityPos());w.spawnParticles(net.minecraft.particle.ParticleTypes.SPLASH,bow.x,bow.y,bow.z,2,.2,.02,.25,.025);
    var stern=RailGeometry.rotate(NavalGeometry.local(ship.kind(),side*1.1,.02,-13),ship.getYaw()).add(ship.getEntityPos());w.spawnParticles(net.minecraft.particle.ParticleTypes.BUBBLE_POP,stern.x,stern.y,stern.z,1,.15,.02,.2,.01);}
  }
 }
 static boolean clear(ServerWorld w,RailVehicleEntity ship,Vec3d pos,float yaw){
  var hull=RailGeometry.worldBox(NavalGeometry.hull(ship.kind()),pos,yaw);
  for(int x=MathHelper.floor(hull.minX)>>4;x<=MathHelper.floor(hull.maxX)>>4;x++)for(int z=MathHelper.floor(hull.minZ)>>4;z<=MathHelper.floor(hull.maxZ)>>4;z++)if(!w.isChunkLoaded(ChunkPos.toLong(x,z)))return false;
  if(!w.getWorldBorder().contains(hull))return false;
  // Coarse hull bands are only for terrain contact; passengers use the exact model cells.
  for(int z=-14;z<15;z+=4){
   if(w.getBlockCollisions(ship,hullBand(ship,pos,yaw,z)).iterator().hasNext())return false;
  }
  var upper=new java.util.ArrayList<Box>();upper.add(new Box(-3.15,3,-13.5,3.15,6.85,-6.8));upper.add(ship.kind()==3?new Box(-.28,3,3.2,.28,20,3.8):new Box(-.28,3,1.5,.28,23,2.1));if(ship.kind()!=3)upper.add(new Box(-.28,3,7.7,.28,18.8,8.3));
  for(var b:upper){
   var scaled=new Box(b.minX*NavalGeometry.sx(ship.kind()),b.minY,b.minZ*NavalGeometry.sz(ship.kind()),b.maxX*NavalGeometry.sx(ship.kind()),ship.kind()==3&&b.maxY>7?7+(b.maxY-7)*.8:b.maxY,b.maxZ*NavalGeometry.sz(ship.kind()));
   if(w.getBlockCollisions(ship,RailGeometry.worldBox(scaled,pos,yaw)).iterator().hasNext())return false;
  }
  for(var other:RailVehicleEntity.nearby(w,hull))if(other!=ship&&other.isShip()&&RailGeometry.worldBox(NavalGeometry.hull(other.kind()),other.getEntityPos(),other.getYaw()).intersects(hull))return false;
  return true;
 }
 private static Box hullBand(RailVehicleEntity ship,Vec3d pos,float yaw,int z){
  int end=Math.min(15,z+4);
  double nearest=Math.clamp(-1,z,end);
  double width=.18+Math.max(.5,4*Math.pow(Math.max(0,1-Math.pow(Math.abs((nearest+1)/16),2)),.55));
  var low=NavalGeometry.local(ship.kind(),-width,-2.15,z);
  var high=NavalGeometry.local(ship.kind(),width,3.1,end);
  return RailGeometry.worldBox(new Box(low,high),pos,yaw);
 }

 private static boolean hitsLand(ServerWorld w,RailVehicleEntity ship,Vec3d pos,float yaw,Vec3d movement){
  var forward=Direction.getFacing(movement.x,0,movement.z);
  var across=forward.rotateYClockwise();
  int waterline=(int)Math.round(ship.getY());
  for(int z=-14;z<15;z+=4){
   Box band=hullBand(ship,pos,yaw,z);
   for(var shape:w.getBlockCollisions(ship,band)){
    var contact=BlockPos.ofFloored(shape.getBoundingBox().getCenter());
    if(contact.getY()<waterline-1||!dryGround(w,contact))continue;
    // A bank needs a continuous mass behind the contact. A pier, lone block or submerged shoal does not qualify.
    boolean bank=true;
    for(int depth=0;depth<3&&bank;depth++){
     int width=0;
     for(int side=-1;side<=1;side++){
      var top=contact.withY(Math.max(waterline,contact.getY())).offset(forward,depth).offset(across,side);
      if(dryGround(w,top)&&dryGround(w,top.down()))width++;
      else if(top.getY()==waterline&&dryGround(w,top.down())&&dryGround(w,top.down(2))&&w.isAir(top))width++;
     }
     bank=width>=2;
    }
    if(bank)return true;
   }
  }
  return false;
 }

 private static boolean dryGround(ServerWorld w,BlockPos pos){
  if(!w.isChunkLoaded(ChunkPos.toLong(pos.getX()>>4,pos.getZ()>>4)))return false;
  var state=w.getBlockState(pos);
  return state.getFluidState().isEmpty()&&state.isSolidBlock(w,pos)
      &&(state.isIn(BlockTags.DIRT)||state.isIn(BlockTags.BASE_STONE_OVERWORLD)||state.isOf(Blocks.SAND)
          ||state.isOf(Blocks.RED_SAND)||state.isOf(Blocks.GRAVEL)||state.isOf(Blocks.SANDSTONE)
          ||state.isOf(Blocks.RED_SANDSTONE));
 }
 static boolean gangwayOccupied(ServerWorld w,RailVehicleEntity ship){return w.getOtherEntities(ship,ship.getBoundingBox(),e->e instanceof LivingEntity&&e.isAlive()&&!e.hasVehicle()).stream().anyMatch(e->{var p=RailPassengerNetwork.toLocal(ship,e.getEntityPos());return Math.abs(p.x)>3.65*NavalGeometry.sx(ship.kind())&&p.z>=-6.6*NavalGeometry.sz(ship.kind())&&p.z<=-4.2*NavalGeometry.sz(ship.kind());});}
 static void write(RailVehicleEntity ship,WriteView v){var s=ship.naval();v.putFloat("naval_hull_health",ship.hullHealth());v.putDouble("naval_throttle",s.throttle);v.putDouble("naval_turn",s.turnRate);v.putBoolean("naval_anchor",ship.anchored());v.putBoolean("naval_sails",ship.sailsRaised());Inventories.writeData(v,s.cargo.getHeldStacks());for(int i=0;i<s.barrels.length;i++)Inventories.writeData(v.get("barrel_"+i),s.barrels[i].getHeldStacks());for(int i=0;i<NavalCannons.count(ship.kind());i++){v.putBoolean("gun_loaded_"+i,s.gunLoaded[i]);v.putLong("gun_ready_"+i,s.gunReady[i]);}}
 static void read(RailVehicleEntity ship,ReadView v){var s=ship.naval();ship.hullHealth(v.getFloat("naval_hull_health",ship.kind()==3?110:160));s.throttle=Math.clamp(v.getDouble("naval_throttle",0),-.3,1);s.turnRate=v.getDouble("naval_turn",0);boolean anchor=v.getBoolean("naval_anchor",true);ship.navalFlags(anchor,v.getBoolean("naval_sails",true),anchor&&Math.abs(ship.speed)<.025);Inventories.readData(v,s.cargo.getHeldStacks());for(int i=0;i<s.barrels.length;i++)Inventories.readData(v.getReadView("barrel_"+i),s.barrels[i].getHeldStacks());for(int i=0;i<NavalCannons.count(ship.kind());i++){s.gunLoaded[i]=v.getBoolean("gun_loaded_"+i,false);s.gunReady[i]=v.getLong("gun_ready_"+i,0);}}
 private NavalPhysics(){}
}
