package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import net.minecraft.village.VillagerProfession;
/** Isolated actual-builder/forge/delivery integration; no project indices are advanced by the test. */
final class RailConstructionChecks {
 static RailwayNetwork.Station station;static RailwayNetwork.Line line;static long village=460123;static boolean done;static int phase;
 static void require(boolean value,String s){if(!value)throw new AssertionError(s);}
 static void register(){if(!Boolean.getBoolean("halloween.railConstruction"))return;
  ServerTickEvents.END_SERVER_TICK.register(server->{if(done)return;var w=server.getOverworld();int ticks=server.getTicks();
   try{
    if(ticks==40){server.getTickManager().setTickRate(1000);for(int x=-1;x<=5;x++)for(int z=-1;z<=5;z++)w.setChunkForced(x,z,true);w.setTimeOfDay(1000);
     for(int x=-4;x<=80;x++)for(int z=-4;z<=74;z++){w.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState());for(int y=100;y<=130;y++)w.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);}
     var observer=new ServerPlayerEntity(server,w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RailObserver"),net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());observer.networkHandler=new net.minecraft.server.network.ServerPlayNetworkHandler(server,new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),observer,net.minecraft.server.network.ConnectedClientData.createDefault(observer.getGameProfile(),false));observer.setPosition(35,120,30);observer.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);w.getPlayers().add(observer);
     var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var team=new VillageMemory.Team(village,new BlockPos(70,100,2),0);memory.teams.add(team);memory.markDirty();
     for(int i=0;i<5;i++){var v=new VillagerEntity(EntityType.VILLAGER,w);v.setVillagerData(v.getVillagerData().withProfession(BuilderProfession.ENTRY));v.setPosition(69+i,100,3);v.addCommandTag("halloween_era_village_"+village);v.setPersistent();w.spawnEntity(v);team.builders.add(v.getUuidAsString());}
     var smith=new VillagerEntity(EntityType.VILLAGER,w);smith.setVillagerData(smith.getVillagerData().withProfession(net.minecraft.registry.Registries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.TOOLSMITH)));smith.setPosition(70,100,5);smith.addCommandTag("halloween_era_village_"+village);w.spawnEntity(smith);
     var era=VillageEraData.get(w).settlement(team);era.vault=new VillageEraData.Vault(new BlockPos(68,100,2),0,"",-1,true);var stock=VillageEconomy.barrel(era,3);w.setBlockState(stock,Blocks.BARREL.getDefaultState());var inventory=(Inventory)w.getBlockEntity(stock);inventory.setStack(0,new ItemStack(Items.IRON_INGOT,64));inventory.setStack(1,new ItemStack(Items.COAL,32));
     station=new RailwayNetwork.Station(village,new BlockPos(0,100,0),Direction.SOUTH.getHorizontalQuarterTurns(),0);RailwayNetwork.get(w).stations.add(station);RailwayNetwork.get(w).markDirty();
     VillageProjects.get(w).add(w,RailwayNetwork.stationId(village),village,"rail_station",RailwayNetwork.building(w,station,"railway_station"));VillageProjects.get(w).add(w,RailwayNetwork.foundryId(village),village,"rail_foundry",RailwayNetwork.building(w,RailwayNetwork.foundry(station),"rail_foundry"));phase=1;
    }
    if(station==null)return;var jobs=VillageProjects.get(w).jobs;
    if(phase==1&&ticks%20==0){
     if(!VillageProjects.get(w).exists(RailwayNetwork.stationId(village)))VillageProjects.get(w).add(w,RailwayNetwork.stationId(village),village,"rail_station",RailwayNetwork.building(w,station,"railway_station"));
     if(!VillageProjects.get(w).exists(RailwayNetwork.foundryId(village)))VillageProjects.get(w).add(w,RailwayNetwork.foundryId(village),village,"rail_foundry",RailwayNetwork.building(w,RailwayNetwork.foundry(station),"rail_foundry"));
    }
    if(phase==1&&jobs.stream().anyMatch(p->p.id.equals(RailwayNetwork.stationId(village))&&p.done)&&jobs.stream().anyMatch(p->p.id.equals(RailwayNetwork.foundryId(village))&&p.done)){
     org.slf4j.LoggerFactory.getLogger("rail-construction").info("PASS: ordinary builders completed full station and separate foundry at {} ticks",ticks);
     line=new RailwayNetwork.Line("rail_integration",village,RailPath.straight(station.center(0),station.center(80)),0,false);RailwayNetwork.get(w).lines.add(line);phase=2;
    }
    if(phase==2&&jobs.stream().anyMatch(p->p.id.equals(RailwayNetwork.segmentId(line,0))&&p.done)){
     var job=jobs.stream().filter(p->p.id.equals(RailwayNetwork.segmentId(line,0))).findFirst().orElseThrow();long count=job.plan.stream().filter(c->c.after().isOf(RailContent.TRACK)&&w.getBlockState(c.pos()).isOf(RailContent.TRACK)).count();require(count==8,"eight produced sections laid by builders on two tracks");
     require(station.production>=24,"operator really hammered all intermediate batches");org.slf4j.LoggerFactory.getLogger("rail-construction").info("PASS: real smith fetched stock, forged profiles and sleepers, assembled sections; builders fetched and placed all eight sections; ticks={}",ticks);done=true;server.stop(false);
    }
    if(ticks%2000==0)org.slf4j.LoggerFactory.getLogger("rail-construction").info("tick={} phase={} batches={} jobs={}",ticks,phase,station.production,jobs.stream().filter(p->p.id.startsWith("rail_")).map(p->p.id+":"+p.index+"/"+p.plan.size()+":"+p.done).toList());
    if(ticks>120000)throw new AssertionError("construction/production did not finish");
   }catch(Throwable e){done=true;org.slf4j.LoggerFactory.getLogger("rail-construction").error("FAIL: full railway construction",e);server.stop(false);}
  });
 }
}
