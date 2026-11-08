package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.util.math.*;
/** A non-ticking animal on a wide future foundation must survive and cannot deadlock generation. */
final class GenerationClearanceChecks {
 static CowEntity cow;static long village;static int start;static boolean done;
 static void register(){if(!Boolean.getBoolean("halloween.railPreparationTest"))return;
  ServerTickEvents.END_SERVER_TICK.register(server->{if(done||server.getTicks()<40)return;var w=server.getOverworld();try{
   if(cow==null){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){w.setChunkForced(x,z,true);if(!w.isChunkLoaded(ChunkPos.toLong(x,z)))return;}
    for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++){w.setBlockState(new BlockPos(x,280,z),Blocks.STONE.getDefaultState());for(int y=281;y<=284;y++)w.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());}village=517930L+w.getTime();
    cow=EntityType.COW.create(w,SpawnReason.EVENT);if(cow==null)throw new AssertionError("cow fixture");cow.setPosition(.5,281,.5);cow.setAiDisabled(true);w.spawnEntity(cow);
    var plan=new LinkedHashMap<BlockPos,BlockState>();for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=281;y<=282;y++)plan.put(new BlockPos(x,y,z),Blocks.STONE_BRICKS.getDefaultState());
    VillageProjects.get(w).add(w,"remote-clearance-fixture:"+village,village,"rail_station",plan);start=server.getTicks();
   }
   VillageProjects.materialize(w,village,2048,true);
   if(VillageProjects.get(w).complete(village,"rail_station")){
    if(!cow.isAlive()||cow.getHealth()!=cow.getMaxHealth()||Math.abs(cow.getX())<=13&&Math.abs(cow.getZ())<=13)throw new AssertionError("unsafe foundation evacuation "+cow.getEntityPos());
    done=true;org.slf4j.LoggerFactory.getLogger("generation-clearance-test").info("PASS frozen animal moved safely outside a 25 by 25 foundation; full generation finished in {} ticks",server.getTicks()-start);cow.discard();
   }else if(server.getTicks()-start>1000)throw new AssertionError("remote generation still blocked by non-ticking animal");
  }catch(Throwable e){done=true;org.slf4j.LoggerFactory.getLogger("generation-clearance-test").error("FAIL",e);server.stop(false);}});
 }
}
