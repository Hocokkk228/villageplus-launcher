package dev.halloween;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
/** Actual shader rendering of hull, dry hold, cabin door and both held miniatures. */
final class NavalVisualChecks {
 static final Vec3d ORIGIN=new Vec3d(-1048.5,63,1088.5);
 static volatile boolean ready;static RailVehicleEntity brig,cutter;static int wait,age,view;
 static final double[][] VIEWS={{24,9,29,0,5,1},{13,3,21,0,1.5,10},{0,.96,1.6,0,1.6,6},{0,3,-4.6,0,4.2,-7},{0,3,-4.6,0,4.2,-7},{24,9,29,0,5,1},{0,5,-23,0,4.5,-13},{0,3,5,0,4,20},{0,3,5,0,4,20},{0,6,-11.9,0,7,-10.72}};
 static final String[] NAMES={"brig","hull-bow","hold","door-closed","door-open","cutter","stern-windows","brig-held","cutter-held","helm-seated"};
 static void register(){if(!Boolean.getBoolean("halloween.navalVisual"))return;ServerTickEvents.END_SERVER_TICK.register(server->{if(ready)return;var w=server.getOverworld();if(w.getPlayers().isEmpty())return;try{var cp=new ChunkPos(BlockPos.ofFloored(ORIGIN));for(int x=-3;x<=6;x++)for(int z=-3;z<=3;z++)w.setChunkForced(cp.x+x,cp.z+z,true);if(++wait<80)return;for(var old:RailVehicleEntity.nearby(w,new Box(ORIGIN,ORIGIN).expand(180)))old.discard();brig=NavalContent.spawn(w,2,ORIGIN,0);cutter=NavalContent.spawn(w,3,ORIGIN.add(48,0,0),0);if(brig==null||cutter==null)throw new AssertionError("both ships spawn in ocean");brig.setDoors(false);cutter.setDoors(false);w.setTimeOfDay(6000);w.setWeather(0,6000,false,false);var p=w.getPlayers().getFirst();p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=true;p.sendAbilitiesUpdate();ready=true;camera(server,view);}catch(Throwable e){org.slf4j.LoggerFactory.getLogger("naval-visual").error("FAIL",e);server.stop(false);}});}
 static void camera(net.minecraft.server.MinecraftServer server,int v){var p=server.getOverworld().getPlayers().getFirst();var data=VIEWS[v];var ship=v==5?cutter:brig;var from=ship.getEntityPos().add(data[0],data[1],data[2]);var target=ship.getEntityPos().add(data[3],data[4],data[5]);var d=target.subtract(from.add(0,p.getStandingEyeHeight(),0));float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)-Math.toDegrees(Math.atan2(d.y,d.horizontalLength()));p.getAbilities().flying=true;p.sendAbilitiesUpdate();p.networkHandler.requestTeleport(from.x,from.y,from.z,yaw,pitch);brig.setDoors(v==4);p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,v>=7&&v<=8?new net.minecraft.item.ItemStack(v==7?NavalContent.BRIG:NavalContent.CUTTER):net.minecraft.item.ItemStack.EMPTY);if(v==9)NavalContent.sit(server.getOverworld(),brig,p,NavalGeometry.helm(2),true);}

}
