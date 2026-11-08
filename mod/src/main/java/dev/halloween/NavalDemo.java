package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldProperties;
/** Disposable review world. Only runs with an explicit development launch flag. */
final class NavalDemo {
 static int age,stage;static volatile boolean ready;static volatile int brigId;static int clientAge;
 static final Vec3d ORIGIN=new Vec3d(-1048.5,63,1088.5);
 static void register(){if(!Boolean.getBoolean("halloween.navalDemo"))return;
  ServerTickEvents.END_SERVER_TICK.register(server->{var w=server.getOverworld();if(stage<0)return;try{
   if(stage==0){var cp=new ChunkPos(BlockPos.ofFloored(ORIGIN));for(int x=-4;x<=6;x++)for(int z=-4;z<=5;z++)w.setChunkForced(cp.x+x,cp.z+z,true);if(++age<80)return;
    for(var old:w.getOtherEntities(null,new Box(ORIGIN,ORIGIN).expand(180),e->e instanceof RailVehicleEntity||e instanceof FieldCannonEntity||e instanceof RailSeatEntity))old.discard();
    var brig=NavalContent.spawn(w,2,ORIGIN,0);var cutter=NavalContent.spawn(w,3,ORIGIN.add(43,0,0),0);if(brig==null||cutter==null)throw new AssertionError("both original vessels launch in demo ocean");brigId=brig.getId();brig.naval().cargo.setStack(0,new ItemStack(NavalCannons.BALL,64));brig.naval().cargo.setStack(1,new ItemStack(Items.GUNPOWDER,64));cutter.naval().cargo.setStack(0,new ItemStack(NavalCannons.BALL,64));cutter.naval().cargo.setStack(1,new ItemStack(Items.GUNPOWDER,64));
    for(int x=8;x<=37;x++)for(int z=-9;z<=1;z++){var p=BlockPos.ofFloored(ORIGIN.add(x,0,z));w.setBlockState(p,Blocks.SPRUCE_SLAB.getDefaultState(),Block.NOTIFY_LISTENERS);if(x%5==0&&z%5==0)for(int down=1;down<=8;down++)w.setBlockState(p.down(down),Blocks.SPRUCE_LOG.getDefaultState(),Block.NOTIFY_LISTENERS);}
    for(int x:new int[]{12,21,31}){var gun=NavalCannons.FIELD_ENTITY.create(w,SpawnReason.EVENT);gun.setPosition(ORIGIN.add(x,.5,-3));gun.setYaw(0);w.spawnEntity(gun);}
    for(int x:new int[]{10,35}){var p=BlockPos.ofFloored(ORIGIN.add(x,.5,-8));for(int y=1;y<=3;y++)w.setBlockState(p.up(y),Blocks.SPRUCE_FENCE.getDefaultState(),Block.NOTIFY_LISTENERS);w.setBlockState(p.up(4),Blocks.LANTERN.getDefaultState(),Block.NOTIFY_LISTENERS);}
    var chest=BlockPos.ofFloored(ORIGIN.add(23,1,-7));w.setBlockState(chest,Blocks.CHEST.getDefaultState().with(ChestBlock.FACING,Direction.SOUTH),Block.NOTIFY_LISTENERS);if(w.getBlockEntity(chest) instanceof Inventory inv){Item[] goods={NavalCannons.BALL,Items.GUNPOWDER,NavalCannons.FIELD_GUN,NavalContent.BRIG,NavalContent.CUTTER,VillageItems.MUSKET,VillageItems.REVOLVER,VillageItems.MUSKET_CARTRIDGE,VillageItems.RAPIER,VillageItems.SABRE,Items.COOKED_BEEF};for(int i=0;i<goods.length;i++)inv.setStack(i,new ItemStack(goods[i],Math.min(goods[i].getDefaultStack().getMaxCount(),64)));inv.markDirty();}
    for(int x:new int[]{12,21,31})for(int dx=-3;dx<=3;dx++)for(int y=-2;y<=5;y++)w.setBlockState(BlockPos.ofFloored(ORIGIN.add(x+dx,y,30)),Blocks.OAK_PLANKS.getDefaultState(),Block.NOTIFY_LISTENERS);
    w.setSpawnPoint(WorldProperties.SpawnPoint.create(w.getRegistryKey(),BlockPos.ofFloored(ORIGIN.add(23,1,-5)),0,0));w.setTimeOfDay(6000);w.setWeather(0,120000,false,false);stage=1;age=0;
   }else if(stage==1){if(age++<30)return;w.getGameRules().setValue(net.minecraft.world.rule.GameRules.ADVANCE_TIME,false,server);w.getGameRules().setValue(net.minecraft.world.rule.GameRules.ADVANCE_WEATHER,false,server);
    // Release temporary chunk forcing before distributing the review save.
    var cp=new ChunkPos(BlockPos.ofFloored(ORIGIN));for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)w.setChunkForced(cp.x+x,cp.z+z,false);
    for(var p:w.getPlayers()){p.stopRiding();p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);p.networkHandler.requestTeleport(ORIGIN.x+23,ORIGIN.y+.5,ORIGIN.z-5,0,0);p.getInventory().setStack(0,new ItemStack(NavalCannons.BALL,64));p.getInventory().setStack(1,new ItemStack(Items.GUNPOWDER,64));p.getInventory().setStack(2,new ItemStack(NavalCannons.FIELD_GUN));p.getInventory().setStack(3,new ItemStack(NavalContent.BRIG));p.getInventory().setStack(4,new ItemStack(NavalContent.CUTTER));p.currentScreenHandler.sendContentUpdates();}
    ready=true;stage=-1;org.slf4j.LoggerFactory.getLogger("naval-demo").info("READY review ocean: brig + cutter + three wheeled field guns, stocked chest and targets");if(w.getPlayers().isEmpty())server.stop(false);
   }
  }catch(Throwable e){stage=-1;org.slf4j.LoggerFactory.getLogger("naval-demo").error("FAIL",e);server.stop(false);}});
 }
 static void client(){if(!Boolean.getBoolean("halloween.navalDemo"))return;ClientTickEvents.END_CLIENT_TICK.register(c->{if(!ready||c.player==null||c.world==null)return;c.options.pauseOnLostFocus=false;if(c.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen)c.setScreen(null);clientAge++;
  if(clientAge==40){c.player.getAbilities().flying=true;c.player.sendAbilitiesUpdate();c.player.setPosition(ORIGIN.add(30,18,33));c.player.setYaw(135);c.player.setPitch(20);c.options.hudHidden=true;}
  if(clientAge==70)net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,"fleet-049-ingame.png",c.getFramebuffer(),1,t->org.slf4j.LoggerFactory.getLogger("naval-demo").info("SCREENSHOT {}",t.getString()));
  if(clientAge==90){c.player.setPosition(ORIGIN.add(21,3,-1));c.player.setYaw(180);c.player.setPitch(22);}
  if(clientAge==120)net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,"field-cannon-049-ingame.png",c.getFramebuffer(),1,t->{});
  if(clientAge==145){c.options.hudHidden=false;c.player.getAbilities().flying=false;c.player.sendAbilitiesUpdate();c.player.setPosition(ORIGIN.add(23,.5,-5));c.player.setYaw(0);c.player.setPitch(0);}
  if(clientAge==180)c.scheduleStop();
 });}
 private NavalDemo(){}
}
