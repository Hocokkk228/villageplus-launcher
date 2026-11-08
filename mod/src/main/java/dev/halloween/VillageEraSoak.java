package dev.halloween;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import com.mojang.authlib.GameProfile;
import java.util.*;
/** Opt-in full normal-world epoch soak, with actual ticking and bounded failure reporting. */
final class VillageEraSoak {
 static ServerPlayerEntity observer;static VillageMemory.Team targetTeam;static long lastProgress;static int previous=-1;static boolean started;static boolean stoneSeen;
 static void register(){
  if(!Boolean.getBoolean("halloween.eraSoak"))return;
  ServerLifecycleEvents.SERVER_STARTED.register(server->{
   var w=server.getOverworld();server.getTickManager().setTickRate(10000);
   observer=new ServerPlayerEntity(server,w,new GameProfile(UUID.randomUUID(),"EraObserver"),SyncedClientOptions.createDefault());
   observer.networkHandler=new net.minecraft.server.network.ServerPlayNetworkHandler(server,new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),observer,net.minecraft.server.network.ConnectedClientData.createDefault(observer.getGameProfile(),false));
   observer.setPosition(56,100,264);observer.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);w.onPlayerConnected(observer);
   for(int x=-6;x<=12;x++)for(int z=10;z<=24;z++)w.setChunkForced(x,z,true);
  });
  ServerTickEvents.END_SERVER_TICK.register(server->{
   if(server.getTicks()%10==0)for(var v:server.getOverworld().getEntitiesByClass(net.minecraft.entity.passive.VillagerEntity.class,new net.minecraft.util.math.Box(-100,55,230,100,180,355),e->e.getVillagerData().profession().matchesKey(BuilderProfession.KEY)))if(v.fallDistance>4)org.slf4j.LoggerFactory.getLogger("era-soak").warn("FALL id={} pos={} velocity={} distance={} task={} below={}",v.getId(),v.getEntityPos(),v.getVelocity(),v.fallDistance,VillageProjects.assignedKind(v),server.getOverworld().getBlockState(v.getBlockPos().down()));
   int ticks=server.getTicks();if(ticks==100)server.getTickManager().setTickRate(10000);if(ticks%200!=0)return;
   var w=server.getOverworld();var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
   if(!started&&ticks>=1000){
    var team=memory.teams.isEmpty()?null:memory.teams.getFirst();
    if(team!=null){
     // Command requires a player source; invoke the identical begin method with the discovered team.
     try{var begin=VillageEras.class.getDeclaredMethod("begin",net.minecraft.server.world.ServerWorld.class,VillageMemory.Team.class,VillageEraData.Settlement.class);begin.setAccessible(true);begin.invoke(null,w,team,VillageEraData.get(w).settlement(team));}catch(Exception e){throw new RuntimeException(e);}
     if(Boolean.getBoolean("halloween.eraSoakFast")){VillageEraData.get(w).settlement(team).xp.put(VillageFastEras.FLAG,1);VillageEraData.get(w).markDirty();org.slf4j.LoggerFactory.getLogger("era-soak").info("Admin fast construction enabled, identical to /villageplus fast");}
     targetTeam=team;started=true;
    }
   }
   // Night/day operation has already run through the town rebuild. Keep the final high-rise work window open.
   if(VillageProjects.get(w).jobs.stream().anyMatch(p->p.kind.equals("cathedral")&&!p.done&&p.index>=100000))w.setTimeOfDay(6000);
   var jobs=VillageProjects.get(w).jobs;int progress=jobs.stream().mapToInt(p->p.index).sum();if(progress!=previous){previous=progress;lastProgress=ticks;}
   if(ticks%1000==0)org.slf4j.LoggerFactory.getLogger("era-soak").info("tick={} targetTPS=10000 teams={} progress={} pending={} eras={}",ticks,memory.teams.size(),progress,jobs.stream().filter(p->!p.done).map(p->p.kind+":"+p.index+"/"+p.plan.size()+" blocked="+p.blocked).limit(12).toList(),VillageEraData.get(w).settlements.values().stream().map(s->s.era).toList());
   if(ticks%1000==0)org.slf4j.LoggerFactory.getLogger("era-soak").info("RAILS stations={} lines={} issue={}",RailwayNetwork.get(w).stations.size(),RailwayNetwork.get(w).lines.stream().map(l->l.next+"/"+(int)Math.ceil(l.path.length/16)+":spawned="+l.spawned).toList(),RailTerrain.lastIssue);
   if(ticks%2000==0&&targetTeam!=null)org.slf4j.LoggerFactory.getLogger("era-soak").info("TARGET village={} anchor={} loaded={} entityTick={} nearby={} houses={}",targetTeam.village,targetTeam.anchor,w.isChunkLoaded(targetTeam.anchor),w.shouldTickEntityAt(targetTeam.anchor),WorldSettlements.loadedNearby(w,targetTeam.anchor),memory.houses.stream().filter(h->VillageHousingRebuild.ordinary(h)&&VillageEras.team(w,h.min)==targetTeam).map(h->h.min.toString()).toList());
   if(ticks%2000==0)org.slf4j.LoggerFactory.getLogger("era-soak").info("GATES {}",VillageEraData.get(w).settlements.values().stream().map(s->s.xp.entrySet().stream().filter(e->e.getKey().startsWith("factory_")||e.getKey().startsWith("steam_")||e.getKey().startsWith("palace_")||e.getKey().startsWith("rail_")||e.getKey().startsWith("shipyard_")).toList()).toList());
   if(ticks%10000==0)for(var job:jobs)if(!job.done&&job.index<job.plan.size()){var c=job.plan.get(job.index);org.slf4j.LoggerFactory.getLogger("era-soak").info("BLOCK {} at {} current={} before={} after={} work={}",job.kind,c.pos(),w.getBlockState(c.pos()),c.before(),c.after(),VillageProjects.workPosition(w,job,c));}
   if(ticks%2000==0)for(var job:jobs)if(!job.done&&(job.id.startsWith("gothic_rebuild:")||job.kind.equals("cathedral"))&&job.index<job.plan.size()){
    var c=job.plan.get(job.index);org.slf4j.LoggerFactory.getLogger("era-soak").info("JAM {}",VillageProjects.placementOccupants(w,c.pos(),c.after()).stream().map(e->e instanceof net.minecraft.entity.passive.VillagerEntity v?VillageProjects.clearanceDebug(w,v):e.toString()).toList());
   }
   if(ticks%10000==0)org.slf4j.LoggerFactory.getLogger("era-soak").info("WORKERS {}",w.getEntitiesByClass(net.minecraft.entity.passive.VillagerEntity.class,new net.minecraft.util.math.Box(observer.getBlockPos()).expand(400),v->v.getVillagerData().profession().matchesKey(BuilderProfession.KEY)).stream().map(v->v.getBlockPos()+":"+VillageProjects.assignedKind(v)+":sleep="+v.isSleeping()).toList());
   if(ticks%10000==0)org.slf4j.LoggerFactory.getLogger("era-soak").info("SETTLEMENT {}",VillageEraData.get(w).settlements.values().stream().map(s->"plantation="+s.plantation+" transition="+s.transitionAt+" arch="+s.xp.getOrDefault("architecture_v2",0)).toList());
   if(ticks%10000==0)org.slf4j.LoggerFactory.getLogger("era-soak").info("REBUILD remainingHomes={} jobs={}",memory.houses.stream().filter(VillageHousingRebuild::ordinary).count(),jobs.stream().filter(p->!p.done&&(p.id.startsWith("gothic_rebuild:")||p.kind.equals("cathedral"))).map(p->p.id+":"+p.index+"/"+p.plan.size()).toList());
   var target=targetTeam;var settlement=target==null?null:VillageEraData.get(w).settlement(target);
   if(started&&settlement!=null&&settlement.era>=3)stoneSeen=true;
   if(started&&settlement!=null&&settlement.era>=2&&!stoneSeen){if(VillageSteam.start(w,target)){stoneSeen=true;org.slf4j.LoggerFactory.getLogger("era-soak").info("STONE reached at {} ticks; standard third transition accepted",ticks);}}
   if(started&&stoneSeen&&settlement!=null&&settlement.era==3){
    boolean factory=VillageFactory.project(w,target.village)!=null&&VillageFactory.project(w,target.village).done;
    boolean homes=memory.houses.stream().noneMatch(h->VillageHousingRebuild.ordinary(h)&&VillageEras.team(w,h.min)==target);
    boolean cathedral=jobs.stream().anyMatch(p->p.id.equals(VillageCathedral.id(target.village))&&p.done);
    if(factory&&homes&&cathedral){org.slf4j.LoggerFactory.getLogger("era-soak").info("PASS: III with every residential rebuild and complete cathedral after {} ticks",ticks);server.stop(false);}

   }
   if(ticks>=Integer.getInteger("halloween.eraSoakMaxTicks",1200000)||ticks>=20000&&ticks-lastProgress>96000){org.slf4j.LoggerFactory.getLogger("era-soak").error("FAIL: epoch stalled at tick {} pending {}",ticks,jobs.stream().filter(p->!p.done).map(p->p.kind+":"+p.index+"/"+p.plan.size()).toList());server.stop(false);}
  });
 }
}
