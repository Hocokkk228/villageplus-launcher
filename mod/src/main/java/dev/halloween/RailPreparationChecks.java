package dev.halloween;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.util.math.BlockPos;
/** Normal-world asynchronous discovery and preparation integration fixture. */
final class RailPreparationChecks {
 static int calls;static long worst;static void register(){if(!Boolean.getBoolean("halloween.railPreparationTest"))return;
  ServerTickEvents.END_SERVER_TICK.register(server->{var w=server.getOverworld();int ticks=server.getTicks();if(ticks==100)server.getTickManager().setTickRate(200);
   if(ticks%40!=0)return;var planner=RailRingPlanner.get(w);if(planner.origin==null)RailRingPlanner.request(w,new BlockPos(56,80,264));long begin=System.nanoTime();
   if(planner.villages.size()<6){var p=RailPreparation.discover(w,planner);if(p!=null&&planner.villages.stream().noneMatch(v->VillageEras.horizontal(v,p)<320*320))planner.villages.add(p);}
   else if(RailPreparation.prepare(w,planner.villages.getFirst())){if(worst>50_000_000L)throw new AssertionError("Rail preparation blocked a server tick: "+worst/1e6+"ms");org.slf4j.LoggerFactory.getLogger("rail-preparation-test").info("PASS six real villages discovered; nearby terrain prepared asynchronously; calls={} worstCallMs={}",calls,worst/1e6);server.stop(false);}
   worst=Math.max(worst,System.nanoTime()-begin);calls++;
   if(ticks%1000==0)org.slf4j.LoggerFactory.getLogger("rail-preparation-test").info("tick={} villages={} prepared={} avgTickMs={}",ticks,planner.villages.size(),RailPreparation.WORK.get(w).prepared.size(),server.getAverageTickTime());
   if(ticks>=30000){org.slf4j.LoggerFactory.getLogger("rail-preparation-test").error("FAIL preparation timeout");server.stop(false);}
  });
 }
}
