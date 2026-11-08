package dev.halloween;
import java.util.*;
import java.util.concurrent.*;
/** Reproduces cold geometry lookups from integrated-server and render threads. */
final class NavalCacheChecks {
 static void verify() throws Exception {
  var workers=Executors.newFixedThreadPool(12);
  try{for(int round=0;round<24;round++){
   for(var type:List.of(RailGeometry.class,NavalGeometry.class))for(var field:type.getDeclaredFields())if(Map.class.isAssignableFrom(field.getType())){field.setAccessible(true);((Map<?,?>)field.get(null)).clear();}
   var gate=new CountDownLatch(1);var ready=new CountDownLatch(12);var pending=new ArrayList<Future<?>>();
   for(int i=0;i<12;i++){final int n=i;pending.add(workers.submit(()->{ready.countDown();try{if(!gate.await(5,TimeUnit.SECONDS))throw new AssertionError("start timeout");for(int j=0;j<8;j++){int kind=(n+j)%4;var parts=RailGeometry.parts(kind);if(parts.isEmpty()||parts!=RailGeometry.parts(kind))throw new AssertionError("unstable cached model");if(kind<2){if(RailGeometry.solidCells(kind,(j&1)==0).isEmpty())throw new AssertionError("empty train cells");}else if(NavalGeometry.cells(kind,(j&1)==0,(j&2)==0).isEmpty())throw new AssertionError("empty ship cells");}}catch(InterruptedException e){throw new RuntimeException(e);}}));}
   if(!ready.await(5,TimeUnit.SECONDS))throw new AssertionError("workers not ready");gate.countDown();for(var result:pending)result.get(10,TimeUnit.SECONDS);
  }}finally{workers.shutdownNow();}
  org.slf4j.LoggerFactory.getLogger("naval-cache-test").info("PASS 24 cold-cache rounds, 12 competing threads, all 4 vehicle kinds and door/gangway collision variants");
 }
}
