package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.util.math.*;
/** Captures the actual game renderer, including seated villagers and the assembly preview. */
final class IndustrialMachinePhotos {
 static final double[][] VIEWS={{7,224,9,.5,222,.5},{48,227,11,38.5,222.5,.5},{86,225,10,76.5,222.3,.5},{124,225,10,114.5,222.3,.5},{166,227,13,152.5,223,.5},{5.5,222,-10,5,221.5,-6},{280,240,125,244,227,82},{215,226,8,205,223,0}};
 static boolean prepared,positioned;static int view,age;
 static void register(){if(!Boolean.getBoolean("halloween.machinePhotos"))return;
  ClientTickEvents.END_CLIENT_TICK.register(c->{if(c.player==null||c.world==null||c.getServer()==null)return;c.options.pauseOnLostFocus=false;c.options.hudHidden=true;c.options.getFov().setValue(55);c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);if(c.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen)c.setScreen(null);
   if(!positioned){positioned=true;var v=VIEWS[view];c.getServer().execute(()->{var server=c.getServer();var w=server.getOverworld();w.setTimeOfDay(6000);w.setWeather(0,0,false,false);
    if(!prepared){prepared=true;for(var entity:w.getOtherEntities(null,new Box(-12,200,-15,200,250,140),e->e instanceof IndustrialVehicleEntity||e instanceof net.minecraft.entity.passive.VillagerEntity))entity.discard();
     for(var kind:IndustrialMachines.Kind.values()){var car=IndustrialMachines.spawn(w,kind,new Vec3d(kind.ordinal()*38+.5,221,.5),0);if(car==null)throw new IllegalStateException("photo vehicle clearance: "+kind);var driver=EntityType.VILLAGER.create(w,SpawnReason.EVENT);driver.setAiDisabled(true);driver.setPosition(car.getEntityPos());w.spawnEntity(driver);car.board(driver,true);car.doors(kind==IndustrialMachines.Kind.BUS);}
     w.setBlockState(new BlockPos(4,221,-6),IndustrialElectronics.TELEVISION.getDefaultState().with(IndustrialElectronics.Appliance.ON,true));w.setBlockState(new BlockPos(6,221,-6),IndustrialElectronics.RADIO.getDefaultState());
     if(w.getBlockEntity(new BlockPos(205,221,0)) instanceof MachineAssemblyEntity line){line.product=2;line.stage=4;line.progress=20;line.sync();}
    }
    server.getTickManager().setFrozen(false);var player=server.getPlayerManager().getPlayerList().getFirst();player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);var from=new Vec3d(v[0],v[1],v[2]);var d=new Vec3d(v[3],v[4],v[5]).subtract(from.add(0,player.getStandingEyeHeight(),0));player.teleport(w,from.x,from.y,from.z,Set.of(),(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,d.horizontalLength())),true);
   });}
   if(age>5){var v=VIEWS[view];var d=new Vec3d(v[3],v[4],v[5]).subtract(c.player.getEyePos());c.player.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));c.player.setPitch((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalLength())));}
   if(++age==110){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,"industrial-0520-"+(view+1)+".png",c.getFramebuffer(),1,t->{});org.slf4j.LoggerFactory.getLogger("industrial-machine-photos").info("Captured {}",view+1);}
   if(age>120){age=0;positioned=false;if(++view==VIEWS.length)c.scheduleStop();}
  });
 }
 private IndustrialMachinePhotos(){}
}
