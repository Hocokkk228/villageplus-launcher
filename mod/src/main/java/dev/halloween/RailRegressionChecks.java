package dev.halloween;
import java.util.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
final class RailRegressionChecks {
 private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 static void check(ServerWorld w){
  var curve=new RailPath(List.of(new Vec3d(0,160,0),new Vec3d(0,160,64),new Vec3d(64,160,64),new Vec3d(64,160,0)));
  require(curve.length>100&&curve.length<130,"arc length follows curve rather than chord");
  for(double d=1;d<curve.length-1;d++){require(Math.abs(curve.at(d,-4).distanceTo(curve.at(d,4))-8)<.01,"two parallel tracks remain separated");require(curve.tangent(d).dotProduct(curve.tangent(d+1))>.99,"curve has no sharp train turns");}
  var input=new SimpleInventory(6);var output=new SimpleInventory(6);input.setStack(0,new ItemStack(Items.IRON_INGOT,6));input.setStack(1,new ItemStack(Items.COAL,2));
  require(RailWorks.produce(input,output,0)&&RailWorks.produce(input,output,1)&&RailWorks.produce(input,output,2),"hammered ingots become profiles, sleepers, then track");
  require(input.isEmpty()&&output.count(RailContent.TRACK.asItem())==1&&output.count(RailContent.PROFILE)==0&&output.count(RailContent.SLEEPER.asItem())==0,"rail production consumes exact inputs and intermediates");
  require(!RailWorks.produce(input,output,0),"empty forge creates no free rails");
  require(RailGeometry.seats().size()==10&&RailGeometry.parts(0).size()>100&&RailGeometry.parts(1).size()>100,"original locomotive and furnished ten-seat coach load");
  var path=new RailPath(RailPath.straight(new Vec3d(140,165,100),new Vec3d(140,165,300)));
  var coach=RailContent.VEHICLE.create(w,SpawnReason.EVENT);require(coach!=null,"coach entity");coach.configure(1,path,0,40,null,0);coach.dwell=1000;w.spawnEntity(coach);
  var passenger=new VillagerEntity(EntityType.VILLAGER,w);passenger.setAiDisabled(true);passenger.setPosition(coach.getX(),coach.getY()+1.5,coach.getZ());w.spawnEntity(passenger);double floor=passenger.getY();
  passenger.move(MovementType.SELF,new Vec3d(0,-2,0));require(Math.abs(passenger.getY()-floor)<.001,"physical train floor supports an unmounted passenger");
  passenger.move(MovementType.SELF,new Vec3d(0,0,3));require(Math.abs(passenger.getZ()-coach.getZ()-3)<.01,"passenger can walk along aisle");
  passenger.move(MovementType.SELF,new Vec3d(4,0,0));require(passenger.getX()-coach.getX()<1.3,"physical side wall prevents walking through coach");
  passenger.setPosition(coach.getX(),floor,coach.getZ());passenger.move(MovementType.SELF,new Vec3d(0,.8,0));require(passenger.getY()>floor+.79,"unmounted passenger can jump inside coach");passenger.move(MovementType.SELF,new Vec3d(0,-2,0));require(Math.abs(passenger.getY()-floor)<.001,"jump lands back on moving-object floor");
  coach.dwell=0;double before=passenger.getZ();for(int i=0;i<40;i++)coach.tick();require(passenger.getZ()>before+1,"moving carriage carries unmounted standing passenger");
  coach.setYaw(45);passenger.setPosition(coach.getEntityPos().add(0,1.5,0));passenger.move(MovementType.SELF,new Vec3d(0,-2,0));require(Math.abs(passenger.getY()-floor)<.001,"rotated carriage supports floor");
  var room=new Box(coach.getX()-.28,coach.getY()+1.52,coach.getZ()-.28,coach.getX()+.28,coach.getY()+3.3,coach.getZ()+.28);require(coach.collisions(room).isEmpty(),"rotated walls do not fill the cabin with their bounding boxes");
  var sitter=new net.minecraft.server.network.ServerPlayerEntity(w.getServer(),w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RailSeatTest"),net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());sitter.networkHandler=new net.minecraft.server.network.ServerPlayNetworkHandler(w.getServer(),new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),sitter,net.minecraft.server.network.ConnectedClientData.createDefault(sitter.getGameProfile(),false));sitter.setPosition(coach.getEntityPos().add(0,1.5,0));
  require(coach.interact(sitter,Hand.MAIN_HAND)==net.minecraft.util.ActionResult.SUCCESS&&sitter.getVehicle() instanceof RailSeatEntity,"working coach seat accepts player");var seat=(RailSeatEntity)sitter.getVehicle();seat.tick();sitter.stopRiding();require(Math.abs(RailPassengerNetwork.toLocal(coach,sitter.getEntityPos()).x)<.01&&Math.abs(RailPassengerNetwork.toLocal(coach,sitter.getEntityPos()).y-1.5)<.01,"dismount lands in carriage aisle, not beside or under train");seat.tick();require(seat.isRemoved(),"dismount releases coach seat");
  var engine=RailContent.VEHICLE.create(w,SpawnReason.EVENT);require(engine!=null,"engine collision fixture");engine.configure(0,path,0,90,null,0);engine.dwell=0;engine.speed=.24;w.spawnEntity(engine);
  var crossing=new VillagerEntity(EntityType.VILLAGER,w);crossing.setAiDisabled(true);crossing.setPosition(engine.getX(),engine.getY()+.3,engine.getZ()+7.8);w.spawnEntity(crossing);float health=crossing.getHealth();double start=engine.distance;
  for(int i=0;i<15;i++)engine.tick();require(crossing.getHealth()<health,"locomotive strikes an exterior living entity");require(engine.distance>start+2,"body on rails cannot stop locomotive");
  require(!w.getEntityCollisions(passenger,new Box(coach.getX()-.2,coach.getY()+1.4,coach.getZ()-.2,coach.getX()+.2,coach.getY()+1.6,coach.getZ()+.2)).isEmpty(),"world collision queries include carriage floor for player validation");
  coach.configure(1,path,0,120,null,0);coach.dwell=0;coach.speed=.24;passenger.setPosition(coach.getX(),coach.getY()+1.5,coach.getZ()-2);float passengerHealth=passenger.getHealth();
  for(int i=0;i<50;i++){
   coach.tick();var backward=RailGeometry.rotate(new Vec3d(0,0,-.06),coach.getYaw());passenger.move(MovementType.SELF,backward);
   var local=RailGeometry.rotate(passenger.getEntityPos().subtract(coach.getEntityPos()),-coach.getYaw());require(local.y>=1.49,"passenger remains on moving floor");
  }
  require(passenger.getHealth()==passengerHealth,"walking passenger never receives run-over damage");org.slf4j.LoggerFactory.getLogger("halloween-rail-test").info("PASS: exterior pedestrian struck, train keeps moving, interior walking passenger unharmed");crossing.discard();engine.discard();
  coach.configure(1,path,0,100,null,0);coach.dwell=1000;coach.speed=0;coach.setDoors(false);
  var doorWorld=RailGeometry.rotate(new Vec3d(1.62,2.5,5.5),coach.getYaw()).add(coach.getEntityPos());var doorway=new Box(doorWorld,doorWorld).expand(.1,.7,.3);
  require(!coach.collisions(doorway).isEmpty(),"closed side door has physical collision");coach.setDoors(true);require(coach.collisions(doorway).isEmpty(),"open side doorway is an actual walkable opening");
  coach.speed=.72;require(!coach.setDoors(true),"door cannot be opened at cruising speed");
  require(!RailPassengerNetwork.inside(new Vec3d(Double.NaN,1.5,0))&&!RailPassengerNetwork.inside(new Vec3d(4,1.5,0)),"invalid passenger coordinates rejected");
  require(RailPassengerNetwork.inside(new Vec3d(0,1.5,0)),"standing passenger lies in local carriage bounds");
  var walker=new net.minecraft.server.network.ServerPlayerEntity(w.getServer(),w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RailWalkTest"),net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());
  walker.networkHandler=new net.minecraft.server.network.ServerPlayNetworkHandler(w.getServer(),new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),walker,net.minecraft.server.network.ConnectedClientData.createDefault(walker.getGameProfile(),false));
  walker.setPosition(coach.getEntityPos().add(0,1.5,0));w.onPlayerConnected(walker);
  RailPassengerNetwork.apply(walker,new RailPassengerNetwork.Motion(coach.getId(),new Vec3d(0,1.5,0),0,0,true));
  require(RailPassengerNetwork.protectedPassenger(walker),"validated walking passenger protected across entire train");
  walker.setPosition(coach.getEntityPos().add(0,.95,0));
  require(RailPassengerNetwork.protectedPassenger(walker),"transient stale server height cannot turn passenger into run-over victim");
  RailPassengerNetwork.apply(walker,new RailPassengerNetwork.Motion(-1,Vec3d.ZERO,0,0,false));
  require(!RailPassengerNetwork.protectedPassenger(walker),"leaving carriage immediately ends passenger protection");
  w.removePlayer(walker,Entity.RemovalReason.DISCARDED);

  require(RailTerrain.natural(Blocks.POPPY.getDefaultState())&&RailTerrain.natural(Blocks.COPPER_ORE.getDefaultState()),"rail cutting accepts natural flowers and ore");
  require(!RailTerrain.natural(Blocks.CHEST.getDefaultState())&&!RailTerrain.natural(Blocks.BRICKS.getDefaultState()),"rail cutting protects inventories and constructed walls");
  var station=VillageEraPlans.template(w,"railway_station");var foundry=VillageEraPlans.template(w,"rail_foundry");require(station!=null&&station.size().getZ()>=60&&foundry!=null,"grand station and separate rail foundry load");
  passenger.discard();coach.discard();
  org.slf4j.LoggerFactory.getLogger("halloween-rail-test").info("PASS: curved double track, real forge inputs, physical floor/walls, walking, jumping, moving support, rotated cabin and working seats");
 }
}
