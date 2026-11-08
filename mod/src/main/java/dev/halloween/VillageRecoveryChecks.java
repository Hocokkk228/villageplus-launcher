package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.entity.*;
import net.minecraft.server.network.*;
import net.minecraft.util.math.*;

/** Runs only against an isolated snapshot selected by the developer launch profile. */
final class VillageRecoveryChecks {
    private static ServerPlayerEntity observer;
    private static Set<String> before;
    private static int ticks;
    static void register(){
        if(!Boolean.getBoolean("halloween.recoveryTest"))return;
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            var w=server.getOverworld();before=new HashSet<>();for(var p:VillageProjects.get(w).jobs)before.add(p.id);
            for(int x=-7;x<=8;x++)for(int z=-8;z<=7;z++)w.setChunkForced(x,z,true);
            for(int x=15;x<=18;x++)for(int z=-7;z<=-4;z++)w.setChunkForced(x,z,true);
            var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RecoveryObserver");
            observer=new ServerPlayerEntity(server,w,profile,net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());
            observer.networkHandler=new ServerPlayNetworkHandler(server,new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),observer,ConnectedClientData.createDefault(profile,false));
            observer.setPosition(15,85,-15);observer.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);w.onPlayerConnected(observer);
            server.getTickManager().setTickRate(60);
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(++ticks%1200==0)org.slf4j.LoggerFactory.getLogger("halloween-recovery").info("RECOVERY tick {}",ticks);
            if(ticks<3600)return;
            var w=server.getOverworld();var jobs=VillageProjects.get(w).jobs;
            for(var p:jobs)if(!before.contains(p.id))org.slf4j.LoggerFactory.getLogger("halloween-recovery").info("NEW {} kind={} progress={}/{} done={}",p.id,p.kind,p.index,p.plan.size(),p.done);
            boolean homes=jobs.stream().anyMatch(p->p.id.startsWith("gothic_infill_v3:"));
            boolean palace=jobs.stream().anyMatch(p->p.id.startsWith("cathedral_palace_v5:")&&p.done);
            for(var job:jobs)if(job.id.startsWith("cathedral_palace_v5:")){
                long missing=job.plan.stream().filter(c->!c.after().isAir()&&w.isChunkLoaded(c.pos())&&c.after().equals(VillageProjects.get(w).expected.get(c.pos()))&&!w.getBlockState(c.pos()).isOf(c.after().getBlock())).count();
                org.slf4j.LoggerFactory.getLogger("halloween-recovery").info("Palace physical inspection: {} missing blocks",missing);
                if(missing>0)palace=false;
            }
            org.slf4j.LoggerFactory.getLogger("halloween-recovery").info("{} saved-world recovery: homes={} palace={} stations={} shoreSearch={}",homes&&palace?"PASS":"FAIL",homes,palace,RailwayNetwork.get(w).stations.size(),VillageEraData.get(w).settlements.get(0L).xp.getOrDefault("shipyard_search",0));
            w.removePlayer(observer,Entity.RemovalReason.DISCARDED);server.stop(false);
        });
    }
}
