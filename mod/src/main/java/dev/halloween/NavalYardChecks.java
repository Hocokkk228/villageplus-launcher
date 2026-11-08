package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.server.network.*;
import net.minecraft.util.math.*;
import net.minecraft.village.VillagerProfession;
/** Actual builders and a shipwright transport stock and launch a ship in generated ocean terrain. */
final class NavalYardChecks {
 static int age,phase;static boolean done;static final long VILLAGE=490123;static NavalWorks.Site site;static VillageMemory.Team team;static VillagerEntity smith;static ServerPlayerEntity observer;
 static void require(boolean v,String s){if(!v)throw new AssertionError(s);}
 static void register(){if(!Boolean.getBoolean("halloween.navalYardTest"))return;ServerTickEvents.END_SERVER_TICK.register(server->{if(done||server.getTicks()<40)return;var w=server.getOverworld();try{age++;
  if(phase==0){server.getTickManager().setTickRate(1000);w.setTimeOfDay(1000);w.getGameRules().setValue(net.minecraft.world.rule.GameRules.ADVANCE_TIME,false,server);var base=new BlockPos(-1100,63,1088);var cp=new ChunkPos(base);for(int x=-3;x<=5;x++)for(int z=-3;z<=5;z++)w.setChunkForced(cp.x+x,cp.z+z,true);if(age<80)return;
   site=new NavalWorks.Site(base,Direction.SOUTH);for(int x=-8;x<=8;x++)for(int z=-12;z<=-2;z++)w.setBlockState(site.at(x,0,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
   for(var old:w.getOtherEntities(null,new Box(base).expand(100),e->e instanceof RailVehicleEntity||e instanceof VillagerEntity))old.discard();
   observer=new ServerPlayerEntity(server,w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PortObserver"),net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());observer.networkHandler=new ServerPlayNetworkHandler(server,new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),observer,ConnectedClientData.createDefault(observer.getGameProfile(),false));observer.setPosition(Vec3d.ofBottomCenter(site.at(0,8,4)));observer.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);w.onPlayerConnected(observer);
   var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);team=new VillageMemory.Team(VILLAGE,site.at(0,1,-6),0);memory.teams.add(team);memory.markDirty();var era=VillageEraData.get(w).settlement(team);era.era=3;era.xp.put("steam_phase",2);era.xp.put("shipyard_x",base.getX());era.xp.put("shipyard_y",base.getY());era.xp.put("shipyard_z",base.getZ());era.xp.put("shipyard_facing",0);era.vault=new VillageEraData.Vault(site.at(4,1,-7),0,"",-1,true);VillageEraData.get(w).markDirty();
   var stock=VillageEconomy.barrel(era,3);w.setBlockState(stock,Blocks.BARREL.getDefaultState(),Block.NOTIFY_LISTENERS);var inventory=(Inventory)w.getBlockEntity(stock);RailWorks.add(inventory,Items.OAK_LOG,128);RailWorks.add(inventory,Items.RED_WOOL,32);RailWorks.add(inventory,Items.IRON_INGOT,12);
   for(int i=0;i<4;i++){var v=new VillagerEntity(EntityType.VILLAGER,w);v.setVillagerData(v.getVillagerData().withProfession(BuilderProfession.ENTRY));v.setPosition(Vec3d.ofBottomCenter(site.at(i-2,1,-5)));v.addCommandTag("halloween_era_village_"+VILLAGE);v.setPersistent();w.spawnEntity(v);team.builders.add(v.getUuidAsString());}
   smith=new VillagerEntity(EntityType.VILLAGER,w);smith.setVillagerData(smith.getVillagerData().withProfession(net.minecraft.registry.Registries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.TOOLSMITH)));smith.setPosition(Vec3d.ofBottomCenter(site.at(2,1,-4)));smith.addCommandTag("halloween_era_village_"+VILLAGE);smith.addCommandTag("halloween_shipwright_"+VILLAGE);smith.setPersistent();w.spawnEntity(smith);
   var projects=VillageProjects.get(w);projects.add(w,NavalWorks.id(VILLAGE),VILLAGE,"shipyard",NavalWorks.plan(w,site));require(projects.exists(NavalWorks.id(VILLAGE)),"yard queued for ordinary builders");phase=1;age=0;
  }else{var job=VillageProjects.get(w).jobs.stream().filter(p->p.id.equals(NavalWorks.id(VILLAGE))).findFirst().orElseThrow();if(phase==1&&job.done){log("PASS ordinary builders completed pier, piles, workshop and stock barrel at tick "+age);phase=2;}
   if(phase==2){var ships=RailVehicleEntity.nearby(w,new Box(site.base()).expand(80)).stream().filter(RailVehicleEntity::isShip).toList();if(!ships.isEmpty()){require(ships.getFirst().kind()==3&&ships.getFirst().anchored(),"shipwright launched anchored cutter");var era=VillageEraData.get(w).settlement(team);require(era.xp.getOrDefault("shipyard_built",0)==1,"production count increments once");require(w.getBlockEntity(site.at(-2,1,3)) instanceof Inventory in&&in.isEmpty(),"yard consumed exact delivered resources");require(smith.getInventory().isEmpty(),"no duplicated carried resources");log("PASS real shipwright fetched mixed oak timber/red cloth/iron from treasury, carried to yard, worked and launched; tick "+age);w.removePlayer(observer,Entity.RemovalReason.DISCARDED);done=true;server.stop(false);}}
   if(age%2000==0){if(job.index<job.plan.size()){var c=job.plan.get(job.index);log("BLOCK "+c+" actual="+w.getBlockState(c.pos())+" blocked="+job.blocked+" work="+VillageProjects.workPosition(w,job,c)+" workers="+w.getEntitiesByClass(VillagerEntity.class,new Box(site.base()).expand(100),v->team.builders.contains(v.getUuidAsString())).stream().map(v->v.getBlockPos()+":"+VillageProjects.assignedKind(v)).toList());}log("PROGRESS tick="+age+" stage="+phase+" blocks="+job.index+"/"+job.plan.size()+" smith="+smith.getEntityPos());}require(age<80000,"yard construction/production stalled at "+job.index+"/"+job.plan.size()+" smith="+smith.getEntityPos());
  }
 }catch(Throwable e){done=true;org.slf4j.LoggerFactory.getLogger("naval-yard-test").error("FAIL",e);server.stop(false);}});}
 static void log(String s){org.slf4j.LoggerFactory.getLogger("naval-yard-test").info(s);}
}
