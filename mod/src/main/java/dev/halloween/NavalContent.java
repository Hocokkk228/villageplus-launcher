package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.screen.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

public final class NavalContent {
 public static EntityType<RailVehicleEntity> BRIG_ENTITY,CUTTER_ENTITY;
 public static Item BRIG,CUTTER;
 static void register(){
  BRIG_ENTITY=type("sailing_brig",2);CUTTER_ENTITY=type("sailing_cutter",3);
  BRIG=item("sailing_brig",2);CUTTER=item("sailing_cutter",3);
  ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e->{e.add(BRIG);e.add(CUTTER);});
  NavalControlNetwork.register();NavalCannons.register();NavalWorks.register();
  CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(CommandManager.literal("fleet").requires(s->s.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.GAMEMASTERS)))
   .then(CommandManager.literal("launch").executes(c->launch(c.getSource().getPlayerOrThrow(),2))
    .then(CommandManager.literal("brig").executes(c->launch(c.getSource().getPlayerOrThrow(),2)))
    .then(CommandManager.literal("cutter").executes(c->launch(c.getSource().getPlayerOrThrow(),3))))));
 }
 private static EntityType<RailVehicleEntity> type(String name,int kind){var key=RegistryKey.of(RegistryKeys.ENTITY_TYPE,Identifier.of("halloween",name));return Registry.register(Registries.ENTITY_TYPE,key,EntityType.Builder.<RailVehicleEntity>create((type,w)->{var ship=new RailVehicleEntity(type,w);ship.configureShip(kind,Vec3d.ZERO,0);return ship;},SpawnGroup.MISC).dimensions(16,24).maxTrackingRange(16).trackingTickInterval(1).build(key));}
 private static Item item(String name,int kind){var key=RegistryKey.of(RegistryKeys.ITEM,Identifier.of("halloween",name));return Registry.register(Registries.ITEM,key,new Item(new Item.Settings().registryKey(key).maxCount(1)){
  @Override public ActionResult use(World world,PlayerEntity player,Hand hand){if(world.isClient())return ActionResult.SUCCESS;return launch(player,kind)>0?consume(player,hand):ActionResult.FAIL;}
 });}
 private static ActionResult consume(PlayerEntity player,Hand hand){if(!player.isCreative())player.getStackInHand(hand).decrement(1);return ActionResult.SUCCESS;}
 private static int launch(PlayerEntity player,int kind){var w=(ServerWorld)player.getEntityWorld();var start=player.getEyePos();var end=start.add(player.getRotationVec(1).multiply(64));var hit=w.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.SOURCE_ONLY,player));
  if(hit.getType()!=HitResult.Type.BLOCK){player.sendMessage(Text.literal("Наведитесь на открытую воду."),true);return 0;}
  var point=hit.getPos();double water=NavalPhysics.surface(w,point.x,point.z,w.getSeaLevel());
  if(!Double.isFinite(water)){player.sendMessage(Text.literal("Для корабля нужна глубокая открытая вода."),true);return 0;}
  var ship=spawn(w,kind,new Vec3d(point.x,water,point.z),player.getYaw());
  if(ship==null){player.sendMessage(Text.literal("Недостаточно глубины или места для корпуса, мачт и трапов."),true);return 0;}
  player.sendMessage(Text.literal("Корабль на воде. Поднимитесь по трапу; ПКМ у штурвала — управление. Пробел — якорь, V — паруса."),false);return 1;
 }
 static RailVehicleEntity spawn(ServerWorld w,int kind,Vec3d pos,float yaw){
  var ship=(kind==3?CUTTER_ENTITY:BRIG_ENTITY).create(w,SpawnReason.EVENT);if(ship==null)return null;ship.configureShip(kind,pos,yaw);
  for(double x:new double[]{-2,0,2})for(double z:new double[]{-10,0,10}){var sample=RailGeometry.rotate(NavalGeometry.local(kind,x,-2.2,z),yaw).add(pos);if(!w.getFluidState(BlockPos.ofFloored(sample)).isIn(net.minecraft.registry.tag.FluidTags.WATER)){ship.discard();return null;}}
  if(!NavalPhysics.clear(w,ship,pos,yaw)){ship.discard();return null;}
  ship.setCustomName(Text.literal(kind==3?"Катер «Чайка»":"Бриг «Северная звезда»"));w.spawnEntity(ship);return ship;
 }
 static ActionResult interact(RailVehicleEntity ship,PlayerEntity player,Hand hand){
  if(!(ship.getEntityWorld() instanceof ServerWorld w))return ActionResult.SUCCESS;var local=RailPassengerNetwork.toLocal(ship,player.getEntityPos());int kind=ship.kind();
  var eye=player.getEyePos();var end=eye.add(player.getRotationVec(1).multiply(player.getEntityInteractionRange()));double nearest=Double.POSITIVE_INFINITY;int barrel=-1;var boxes=NavalGeometry.barrelBoxes(kind);for(int i=0;i<boxes.size();i++){var box=RailGeometry.worldBox(boxes.get(i),ship.getEntityPos(),ship.getYaw());var hit=box.raycast(eye,end);if(hit.isPresent()){double distance=eye.squaredDistanceTo(hit.get());if(distance<nearest){nearest=distance;barrel=i;}}}
  if(barrel>=0){double hull=Double.POSITIVE_INFINITY;for(var box:ship.collisions(new Box(eye,end).expand(.01))){var hit=box.raycast(eye,end);if(hit.isPresent())hull=Math.min(hull,eye.squaredDistanceTo(hit.get()));}if(nearest<=hull+.025){var inventory=ship.naval().barrels[barrel];int index=barrel;player.openHandledScreen(new SimpleNamedScreenHandlerFactory((sync,inv,p)->GenericContainerScreenHandler.createGeneric9x3(sync,inv,inventory),Text.literal("Бочка "+(index+1))));w.playSound(null,player.getX(),player.getY(),player.getZ(),net.minecraft.sound.SoundEvents.BLOCK_BARREL_OPEN,net.minecraft.sound.SoundCategory.BLOCKS,.6f,.96f+w.random.nextFloat()*.08f);return ActionResult.SUCCESS;}}
  var gun=NavalCannons.interact(ship,player,hand);if(gun!=ActionResult.PASS)return gun;
  if(local.squaredDistanceTo(NavalGeometry.helm(kind))<9){
   if(RailDriver.seat(w,ship)!=null){player.sendMessage(Text.literal("Штурвал занят."),true);return ActionResult.FAIL;}
   return sit(w,ship,player,NavalGeometry.helm(kind),true);
  }
  if(local.squaredDistanceTo(NavalGeometry.door(kind))<5){ship.setDoors(!ship.doorsOpen());return ActionResult.SUCCESS;}
  if(local.squaredDistanceTo(NavalGeometry.cargo(kind))<9){player.openHandledScreen(new SimpleNamedScreenHandlerFactory((sync,inventory,p)->kind==3?GenericContainerScreenHandler.createGeneric9x3(sync,inventory,ship.naval().cargo):GenericContainerScreenHandler.createGeneric9x6(sync,inventory,ship.naval().cargo),Text.literal("Трюм — "+ship.getName().getString())));return ActionResult.SUCCESS;}
  var seat=NavalGeometry.seats(kind).stream().min(Comparator.comparingDouble(p->p.squaredDistanceTo(local))).orElseThrow();
  if(seat.squaredDistanceTo(local)<4)return sit(w,ship,player,seat,false);
  player.sendMessage(Text.literal("Поднимитесь по трапу. ПКМ у кресла, двери, груза или штурвала."),true);return ActionResult.SUCCESS;
 }
 static ActionResult sit(ServerWorld w,RailVehicleEntity ship,LivingEntity passenger,Vec3d point,boolean control){
  var world=RailGeometry.rotate(point,ship.getYaw()).add(ship.getEntityPos());if(!w.getOtherEntities(ship,new Box(world,world).expand(.35),e->e instanceof RailSeatEntity&&e.hasPassengers()).isEmpty())return ActionResult.FAIL;
  var seat=RailContent.SEAT.create(w,SpawnReason.EVENT);if(seat==null)return ActionResult.FAIL;seat.owner=ship.getUuid();seat.local=point;seat.control=control;seat.setPosition(world);w.spawnEntity(seat);if(!passenger.startRiding(seat,true,false)){seat.discard();return ActionResult.FAIL;}seat.syncToTrain();return ActionResult.SUCCESS;
 }
 private NavalContent(){}
}
