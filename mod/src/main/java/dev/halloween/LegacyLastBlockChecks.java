package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.*;
import net.minecraft.util.math.*;
/** Read-only source snapshot; this check modifies only a developer copy of the player's save. */
final class LegacyLastBlockChecks {
 static final long VILLAGE=68719476550L;static int age;static boolean done;static ServerPlayerEntity observer;
 static void register(){if(!Boolean.getBoolean("halloween.legacyLastBlocks"))return;
  ServerTickEvents.END_SERVER_TICK.register(server->{if(done||server.getTicks()<40)return;var w=server.getOverworld();try{
   if(observer==null){server.getTickManager().setTickRate(1000);w.setTimeOfDay(6000);
    for(var center:List.of(new BlockPos(-2944,68,226),new BlockPos(-3212,76,52)))for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.setChunkForced((center.getX()>>4)+x,(center.getZ()>>4)+z,true);
    observer=new ServerPlayerEntity(server,w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"LegacyLastBlocks"),net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());observer.networkHandler=new ServerPlayNetworkHandler(server,new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),observer,ConnectedClientData.createDefault(observer.getGameProfile(),false));observer.setPosition(-2920,90,250);observer.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);w.onPlayerConnected(observer);
   }
   age++;var projects=VillageProjects.get(w);var s=VillageEraData.get(w).settlements.get(VILLAGE);
   if(s==null)throw new AssertionError("Wrong snapshot: original settlement is missing");
   var team=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).teams.stream().filter(t->t.village==VILLAGE).findFirst().orElseThrow();
   if(age%20==0){VillageEraPlans.repairPlantationCrossings(w,team,s);VillageTreasuryRepair.tick(w,team,s);VillageProjects.materialize(w,VILLAGE,512);}
   var treasury=projects.jobs.stream().filter(p->p.id.equals("era_treasury_v36:"+VILLAGE)).findFirst().orElseThrow();var farm=projects.jobs.stream().filter(p->p.id.equals("era_plantation_v2:"+VILLAGE)).findFirst().orElseThrow();
   if(treasury.done&&farm.done&&s.era>=2&&projects.jobs.stream().anyMatch(p->p.id.equals("treasury_restoration_v2:"+VILLAGE)&&p.done)){
    if(!w.getBlockState(new BlockPos(-2944,68,226)).isOf(RoyalProfession.CEREMONIAL_LECTERN))throw new AssertionError("Legacy ceremonial lectern was destroyed");
    if(!w.getBlockState(new BlockPos(-3212,76,52)).isOf(Blocks.STONE_BRICKS)||!w.getBlockState(new BlockPos(-3212,78,52)).isOf(Blocks.LANTERN))throw new AssertionError("Plantation lamp footing did not stabilize");
    for(int i=0;i<6;i++)if(!(w.getBlockEntity(VillageEconomy.barrel(s,i)) instanceof net.minecraft.inventory.Inventory))throw new AssertionError("Treasury stock container missing: "+i);
    done=true;org.slf4j.LoggerFactory.getLogger("legacy-last-block-test").info("PASS actual player snapshot: treasury {}/{}, plantation {}/{}, era {} in {} ticks; lectern, lamp and all six stock barrels preserved",treasury.index,treasury.plan.size(),farm.index,farm.plan.size(),s.era,age);w.removePlayer(observer,Entity.RemovalReason.DISCARDED);server.stop(false);
   }
   if(age%500==0)for(var job:projects.jobs)if(job.id.startsWith("treasury_restoration")&&!job.done&&job.index<job.plan.size()){var c=job.plan.get(job.index);org.slf4j.LoggerFactory.getLogger("legacy-last-block-test").info("REPAIR {}/{} cell={} current={} occupants={}",job.index,job.plan.size(),c,w.getBlockState(c.pos()),VillageProjects.placementOccupants(w,c.pos(),c.after()).stream().map(e->e.getType()+":"+e.getBlockPos()).toList());}
   if(age%500==0)org.slf4j.LoggerFactory.getLogger("legacy-last-block-test").info("tick={} treasury={}/{} farm={}/{} era={}",age,treasury.index,treasury.plan.size(),farm.index,farm.plan.size(),s.era);
   if(age>3000)throw new AssertionError("The real player's two last blocks did not finish");
  }catch(Throwable e){done=true;org.slf4j.LoggerFactory.getLogger("legacy-last-block-test").error("FAIL",e);server.stop(false);}});
 }
}
