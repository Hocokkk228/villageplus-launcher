package dev.halloween;
import java.util.*;
import net.minecraft.server.world.*;
import net.minecraft.world.chunk.*;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.util.math.*;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
/** One asynchronous terrain request at a time; no join/get on the server tick. */
final class RailPreparation {
 static final ChunkTicketType TICKET=new ChunkTicketType(200,ChunkTicketType.FOR_LOADING);
 static final class Work {boolean loading;ChunkPos pending;final Set<Long> prepared=new HashSet<>();long next;}
 static final Map<ServerWorld,Work> WORK=new WeakHashMap<>();
 static boolean ready(ServerWorld w){return w.getServer().getAverageTickTime() < 35;}
 static BlockPos discover(ServerWorld w,RailRingPlanner planner){
  var work=WORK.computeIfAbsent(w,k->new Work());
  if(work.loading){w.getChunkManager().addTicket(TICKET,work.pending,0);if(!w.isChunkLoaded(work.pending.toLong()))return null;var chunk=w.getChunkManager().getWorldChunk(work.pending.x,work.pending.z);if(chunk==null)return null;w.getChunkManager().removeTicket(TICKET,work.pending,0);work.loading=false;
   var registry=w.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
   for(var entry:chunk.getStructureStarts().entrySet())if(registry.getEntry(entry.getKey()).isIn(StructureTags.VILLAGE)&&entry.getValue().hasChildren())return entry.getValue().getBoundingBox().getCenter();
   return null;
  }
  if(!ready(w)||w.getTime()<work.next)return null;
  var placements=w.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE_SET).stream().filter(s->s.structures().stream().anyMatch(e->e.structure().isIn(StructureTags.VILLAGE))).map(s->s.placement()).filter(p->p instanceof RandomSpreadStructurePlacement).map(p->(RandomSpreadStructurePlacement)p).toList();
  if(placements.isEmpty()||planner.probe>=289*placements.size())return null;
  int n=planner.probe++,region=n/placements.size();var placement=placements.get(n%placements.size());
  int radius=(int)Math.ceil((Math.sqrt(region+1)-1)/2),side=Math.max(1,2*radius),offset=region-(2*radius-1)*(2*radius-1),dx=0,dz=0;
  if(radius>0){int edge=offset/side,t=offset%side;switch(edge){case 0->{dx=-radius+t;dz=-radius;}case 1->{dx=radius;dz=-radius+t;}case 2->{dx=radius-t;dz=radius;}default->{dx=-radius;dz=radius-t;}}}
  int spacing=placement.getSpacing();var candidate=placement.getStartChunk(w.getSeed(),(Math.floorDiv(planner.origin.getX()>>4,spacing)+dx)*spacing,(Math.floorDiv(planner.origin.getZ()>>4,spacing)+dz)*spacing);
  planner.markDirty();work.next=w.getTime()+40;work.pending=candidate;work.loading=true;w.getChunkManager().addTicket(TICKET,candidate,0);return null;
 }
 static boolean prepare(ServerWorld w,BlockPos village){
  var work=WORK.computeIfAbsent(w,k->new Work());
  if(work.loading){w.getChunkManager().addTicket(TICKET,work.pending,0);if(!w.isChunkLoaded(work.pending.toLong()))return false;work.prepared.add(work.pending.toLong());w.getChunkManager().removeTicket(TICKET,work.pending,0);work.loading=false;return false;}
  var center=new ChunkPos(village);for(int r=0;r<=2;r++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){if(Math.max(Math.abs(x),Math.abs(z))!=r)continue;var p=new ChunkPos(center.x+x,center.z+z);if(work.prepared.contains(p.toLong())||w.isChunkLoaded(p.toLong()))continue;
   if(!ready(w)||w.getTime()<work.next)return false;work.next=w.getTime()+20;work.pending=p;work.loading=true;w.getChunkManager().addTicket(TICKET,p,0);return false;
  }return true;
 }
}
