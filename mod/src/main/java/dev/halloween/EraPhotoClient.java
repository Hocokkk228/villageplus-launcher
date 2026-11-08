package dev.halloween;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.math.Vec3d;
final class EraPhotoClient {
 static final double[][] VIEWS={{4,112,160,4,106,285},{-200,150,175,-70,85,290},{-177,101,296,-150,83,329},{-169,113,194,-134,90,236}};
 static final double[][] INDUSTRIAL={{399,246,283,362,222,320},{360,224,291,353,223,318},{424,210,316,430,169,394},
  {470,194,360,433,173,390},{389,225,319,373,220,310},{359,220,313,373,217,320}};
 static final double[][] SPAS={{500,245,306,432,219,399},{425,189,265,432,216,396},{502,212,389,433,206,396}};
 static final double[][] PRESIDENTIAL={{447.5,175,401.5,451.5,176.2,401.5},{433,163,379,433,171,403},{415,168,392,421,169,384}};
 static final double[][] REPAIRS={{432,62,388,432,62.4,381},{432,62,374,432,62.4,381},{425.5,62,381,432,62.4,381},{446.5,68,418.5,448,69,416},{432,62,370,432,69,399},{500,110,325,432,86,396},{428.5,61.3,385,428.5,62,381.5},{433,61.3,378,432.5,62,381.5},{635,64,366,638.5,63.4,361.5}};
 static boolean prepared;
 static int view,age;static boolean positioned;
 static void register(){if(!Boolean.getBoolean("halloween.eraPhotos"))return;boolean spas=Boolean.getBoolean("halloween.spasPhotos");
 boolean repairs=Boolean.getBoolean("halloween.repairPhotos");
 boolean presidential=Boolean.getBoolean("halloween.presidentialPhotos");
 boolean industrial=Boolean.getBoolean("halloween.industrialPhotos")||spas||presidential||repairs;
 var views=repairs?REPAIRS:presidential?PRESIDENTIAL:spas?SPAS:industrial?INDUSTRIAL:VIEWS;
 int captureAt=industrial?100:350;
 ClientTickEvents.END_CLIENT_TICK.register(c->{if(c.player==null||c.world==null||c.getServer()==null)return;c.options.pauseOnLostFocus=false;c.options.hudHidden=true;c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);if(c.currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen)c.setScreen(null);
  if(!positioned){positioned=true;var v=views[view];c.getServer().execute(()->{var s=c.getServer();var w=s.getOverworld();w.setTimeOfDay(repairs&&view==4?14000:industrial?3700:11000);w.setWeather(0,0,false,false);if(repairs&&!prepared){prepared=true;
   var photoStation=new RailwayNetwork.Station(517999,new net.minecraft.util.math.BlockPos(600,60,352),0,0);
   for(int cx=37;cx<=40;cx++)for(int cz=21;cz<=26;cz++)w.getChunk(cx,cz);
   for(var block:RailwayNetwork.building(w,photoStation,"railway_station").entrySet())w.setBlockState(block.getKey(),block.getValue(),net.minecraft.block.Block.NOTIFY_LISTENERS);
   StationResidents.ensure(w,photoStation);

   for(int i=0;i<3;i++){var npc=new net.minecraft.entity.passive.VillagerEntity(net.minecraft.entity.EntityType.VILLAGER,w);npc.setPosition(428+i*4+.5,61,381.5);npc.setYaw(0);npc.setBodyYaw(0);npc.setHeadYaw(0);npc.setAiDisabled(true);if(i==0)npc.setVillagerData(npc.getVillagerData().withProfession(BuilderProfession.ENTRY));if(i==1)npc.setVillagerData(npc.getVillagerData().withProfession(net.minecraft.registry.Registries.VILLAGER_PROFESSION.getOrThrow(net.minecraft.village.VillagerProfession.FARMER)));w.spawnEntity(npc);}
   }s.getTickManager().setFrozen(true);var p=s.getPlayerManager().getPlayerList().getFirst();p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);var from=new Vec3d(v[0],v[1],v[2]);var d=new Vec3d(v[3],v[4],v[5]).subtract(from.add(0,p.getStandingEyeHeight(),0));p.teleport(w,from.x,from.y,from.z,java.util.Set.of(),(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,d.horizontalLength())),true);});}
  if(industrial&&age>5){var v=views[view];var d=new Vec3d(v[3],v[4],v[5]).subtract(c.player.getEyePos());c.player.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));c.player.setPitch((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalLength())));}
  if(++age==captureAt){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,(repairs?"repairs-0517-":presidential?"presidential-":spas?"spas-":industrial?"era4-v2-":"era3-shaders-")+(view+1)+".png",c.getFramebuffer(),1,t->{});org.slf4j.LoggerFactory.getLogger("era-photos").info("Captured {}",view+1);}
  if(age>captureAt+10){age=0;positioned=false;if(++view>=views.length)c.scheduleStop();}
 });}
}
