package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.item.*;

/** Actual generated ocean, free steering, moving deck, anchor, shore collision and preserved water. */
final class NavalChecks {
 static RailVehicleEntity brig,cutter;static ServerPlayerEntity pilot;static VillagerEntity crew;static BlockPos ocean;static Vec3d start,crewLocal;static ItemStack wreckCargo;static int stage,age;static float heading;static boolean done;static final Map<BlockPos,BlockState> barrier=new LinkedHashMap<>();
 static void require(boolean v,String why){if(!v)throw new AssertionError(why);}
 static void register(){if(!Boolean.getBoolean("halloween.navalTest"))return;
  ServerTickEvents.END_SERVER_TICK.register(server->{if(done||server.getTicks()<40)return;var w=server.getOverworld();try{
   age++;
   if(stage==0){NavalCacheChecks.verify();server.getTickManager().setTickRate(1000);w.setTimeOfDay(6000);var found=w.locateBiome(b->b.matchesKey(BiomeKeys.DEEP_OCEAN)||b.matchesKey(BiomeKeys.DEEP_LUKEWARM_OCEAN),new BlockPos(0,w.getSeaLevel(),0),6400,64,32);require(found!=null,"deep unfrozen ocean found in normal terrain");ocean=found.getFirst();var cp=new ChunkPos(ocean);for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)w.setChunkForced(cp.x+x,cp.z+z,true);stage=1;age=0;log("OCEAN {}",ocean);return;}
   if(stage==1){if(age<60)return;double water=NavalPhysics.surface(w,ocean.getX(),ocean.getZ(),w.getSeaLevel());if(!Double.isFinite(water)){require(age<10000,"ocean chunks loaded");return;}
    start=new Vec3d(ocean.getX()-24.5,water,ocean.getZ()+.5);for(var old:RailVehicleEntity.nearby(w,new Box(start,start).expand(130)))old.discard();brig=NavalContent.spawn(w,2,start,0);cutter=NavalContent.spawn(w,3,start.add(55,0,0),0);require(brig!=null&&cutter!=null,"both original vessels fit naturally generated deep water");require(brig.route()==null&&cutter.route()==null,"ships have no rail path or fixed route");require(brig.anchored()&&brig.gangwayDeployed(),"spawned ship is safely anchored with boarding gangways");require(RailGeometry.parts(2).size()>500&&RailGeometry.parts(3).size()>500,"detailed original vessel models loaded");
    var hull=brig.naval().cargo;hull.setStack(0,new ItemStack(Items.IRON_INGOT,32));require(hull.size()==54&&cutter.naval().cargo.size()==27,"real cargo inventories differ by vessel");
    var point=start.add(NavalGeometry.local(2,2,3,4));crew=EntityType.VILLAGER.create(w,SpawnReason.EVENT);require(crew!=null,"deck passenger created");crew.setAiDisabled(true);crew.setPersistent();crew.setPosition(point);w.spawnEntity(crew);crewLocal=RailPassengerNetwork.toLocal(brig,crew.getEntityPos());
    pilot=new ServerPlayerEntity(server,w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"NavalCaptainTest"),net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());pilot.networkHandler=new net.minecraft.server.network.ServerPlayNetworkHandler(server,new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),pilot,net.minecraft.server.network.ConnectedClientData.createDefault(pilot.getGameProfile(),false));pilot.setPosition(start.add(NavalGeometry.helm(2)));w.onPlayerConnected(pilot);
    double throttle=brig.naval().throttle;NavalControlNetwork.apply(pilot,new NavalControlNetwork.Input(1,1,true,false));require(brig.naval().throttle==throttle&&brig.anchored(),"unseated player cannot remotely steer or lift anchor");require(NavalContent.sit(w,brig,pilot,NavalGeometry.helm(2),true)==net.minecraft.util.ActionResult.SUCCESS,"real helm chair accepts captain");NavalControlNetwork.apply(pilot,new NavalControlNetwork.Input(0,0,true,false));stage=2;age=0;return;
   }
   if(stage==2){NavalControlNetwork.apply(pilot,new NavalControlNetwork.Input(1,0,false,false));if(age>180){require(brig.speed>.12&&brig.getEntityPos().distanceTo(start)>10,"freely sailing vessel accelerates and moves through real water");heading=brig.getYaw();stage=3;age=0;log("PASS acceleration and unrestricted ocean movement, speed={}",brig.speed);}}
   else if(stage==3){NavalControlNetwork.apply(pilot,new NavalControlNetwork.Input(0,1,false,false));if(age>140){require(Math.abs(MathHelper.wrapDegrees(brig.getYaw()-heading))>35,"rudder changes course without a rail route");var local=RailPassengerNetwork.toLocal(brig,crew.getEntityPos());require(local.distanceTo(crewLocal)<.18,"ordinary unmounted passenger stays on turning moving deck: "+local);require(crew.isAlive()&&crew.getHealth()==20,"deck passenger unharmed");NavalControlNetwork.apply(pilot,new NavalControlNetwork.Input(0,0,true,false));stage=4;age=0;log("PASS rudder, moving solid deck and passenger safety");}}
   else if(stage==4){if(age>100){require(brig.speed==0&&brig.anchored()&&brig.gangwayDeployed(),"anchor slows vessel and deploys boarding stairs only after stopping");crew.discard();pilot.stopRiding();brig.setPosition(start);brig.setYaw(0);brig.speed=0;brig.naval().turnRate=0;brig.naval().throttle=0;brig.navalFlags(true,true,true);require(NavalContent.sit(w,brig,pilot,NavalGeometry.helm(2),true)==net.minecraft.util.ActionResult.SUCCESS,"captain can return to helm");
     for(int x=-7;x<=7;x++)for(int y=-3;y<=5;y++){var p=BlockPos.ofFloored(start.add(x,y,28));barrier.put(p,w.getBlockState(p));w.setBlockState(p,Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);}NavalControlNetwork.apply(pilot,new NavalControlNetwork.Input(0,0,true,false));stage=5;age=0;log("PASS anchor and boarding gangways");}}
   else if(stage==5){NavalControlNetwork.apply(pilot,new NavalControlNetwork.Input(1,0,false,false));if(age>300){
    require(!brig.isRemoved()&&brig.naval().blocked&&brig.speed==0,"thin wall stops the hull without destroying it");
    require(brig.getZ()<start.z+16,"vessel never passes through the barrier");
    for(var e:barrier.entrySet())w.setBlockState(e.getKey(),e.getValue(),Block.NOTIFY_LISTENERS);
    barrier.clear();
    require(w.getFluidState(BlockPos.ofFloored(start.add(0,-1,0))).isIn(net.minecraft.registry.tag.FluidTags.WATER),"vessel did not drain or replace ocean water");
    require(brig.naval().cargo.getStack(0).getCount()==32,"cargo survives contact with a wall");
    buildBank(w,-2);
    brig.setPosition(start);brig.speed=0;
    stage=6;age=0;log("PASS thin wall stops ship intact; approaching shoal");
   }}
   else if(stage==6){
    NavalControlNetwork.apply(pilot,new NavalControlNetwork.Input(1,0,false,false));
    if(age>300){
     require(!brig.isRemoved()&&brig.naval().blocked&&brig.speed==0,"submerged shoal stops the hull intact");
     restoreBank(w);
     buildBank(w,-1);
     stage=7;age=0;
     log("PASS submerged shoal stops ship intact; approaching low beach slowly");
    }
   }
   else if(stage==7){
    NavalControlNetwork.apply(pilot,new NavalControlNetwork.Input(.2f,0,false,false));
    if(age>80){
     require(!brig.isRemoved()&&brig.naval().blocked&&brig.speed==0,"low-speed contact with a beach does not wreck a ship");
     wreckCargo=new ItemStack(Items.IRON_INGOT,32);
     wreckCargo.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,net.minecraft.text.Text.literal(UUID.randomUUID().toString()));
     brig.naval().cargo.setStack(0,wreckCargo);
     brig.setPosition(start);brig.speed=0;
     stage=8;age=0;
     log("PASS slow approach stops at the beach; testing a full-speed strike");
    }
   }
   else if(stage==8){
    if(!brig.isRemoved())NavalControlNetwork.apply(pilot,new NavalControlNetwork.Input(1,0,false,false));
    if(brig.isRemoved()||age>300){
     require(brig.isRemoved(),"full-speed strike against an island wrecks the ship");
     require(brig.getZ()<start.z+16,"ship wrecks before it enters land");
     var drops=w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,brig.getBoundingBox().expand(3),item->ItemStack.areItemsAndComponentsEqual(item.getStack(),wreckCargo));
     require(drops.stream().mapToInt(item->item.getStack().getCount()).sum()==32,"island wreck drops the whole cargo once");
     restoreBank(w);
     pilot.stopRiding();w.removePlayer(pilot,Entity.RemovalReason.DISCARDED);done=true;
     log("PASS island wreck, single cargo drop and preserved water; both vessels verified in normal ocean");
     server.stop(false);
    }
   }
   require(age<20000,"naval stage timeout "+stage);
  }catch(Throwable e){done=true;org.slf4j.LoggerFactory.getLogger("naval-test").error("FAIL stage="+stage,e);for(var entry:barrier.entrySet())w.setBlockState(entry.getKey(),entry.getValue(),Block.NOTIFY_LISTENERS);server.stop(false);}});
 }
 private static void buildBank(ServerWorld world,int top){
  var waterline=BlockPos.ofFloored(start).withY((int)Math.round(start.y));
  for(int x=-7;x<=7;x++)for(int y=-3;y<=top;y++)for(int z=28;z<=34;z++){
   var pos=waterline.add(x,y,z);
   barrier.put(pos,world.getBlockState(pos));
   world.setBlockState(pos,Blocks.SANDSTONE.getDefaultState(),Block.NOTIFY_LISTENERS);
  }
 }

 private static void restoreBank(ServerWorld world){
  for(var entry:barrier.entrySet())world.setBlockState(entry.getKey(),entry.getValue(),Block.NOTIFY_LISTENERS);
  barrier.clear();
 }

 static void log(String s,Object...args){org.slf4j.LoggerFactory.getLogger("naval-test").info(s,args);}
}
