package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
/** Persistent third-era transition: exterior conversions first, then a completed factory. */
final class VillageSteam {
 static final String PHASE="steam_phase",START="steam_start_day";
 static boolean start(ServerWorld w,VillageMemory.Team t){
  var d=VillageEraData.get(w);var s=d.settlement(t);
  if(s.era!=2||s.transitionAt>=0||VillageArchitectureMigration.running(s))return false;
  s.xp.putIfAbsent(START,(int)(w.getTime()/24000));s.xp.putIfAbsent(PHASE,1);d.markDirty();return true;
 }
 static boolean infrastructureStarted(VillageEraData.Settlement s){
  return s.era>=VillageEpoch.STEAM||s.xp.getOrDefault(PHASE,0)>0;
 }
 static boolean finishedInfrastructure(VillageProjects projects,long village,boolean portRequired,boolean trainsRunning){
  return trainsRunning&&projects.complete(village,"rail_station")&&projects.complete(village,"rail_foundry")
          &&(!portRequired||projects.complete(village,"shipyard"))
          &&projects.jobs.stream().filter(p->p.village==village&&p.kind.equals("factory")).allMatch(p->p.done);
 }
 static BlockState modern(BlockState old){
  if(old.isOf(Blocks.BRICKS)||old.isOf(Blocks.STONE_BRICKS)||old.isOf(Blocks.TUFF_BRICKS))return Blocks.STONE_BRICKS.getDefaultState();
  if(old.isOf(Blocks.POLISHED_ANDESITE))return Blocks.POLISHED_TUFF.getDefaultState();
  return old;
 }
 static void tick(ServerWorld w,VillageMemory.Team t,VillageEraData.Settlement s){
  if(s.era==2&&s.eraAt>=0&&w.getTime()-s.eraAt>=50*24000L)start(w,t);
  if(s.xp.getOrDefault(PHASE,0)==0)return;
  if(!VillageHousingRebuild.advance(w,t,s))return;
  if(!VillageCathedral.advance(w,t,s))return;
  boolean palaceReady=VillagePalaceUpgrade.ready(w,t,s);
  var projects=VillageProjects.get(w);
  var factory=VillageFactory.project(w,s.village);if(factory==null||!factory.done||s.vault==null||!projects.complete(s.village,"treasury"))return;
  if(s.era==2&&(!palaceReady
          ||!finishedInfrastructure(projects,s.village,NavalWorks.portRequired(projects,s),RailwayNetwork.runningAt(w,s.village))))return;
  if(s.era==2){s.era=3;s.eraAt=w.getTime();s.xp.put(PHASE,2);VillageEraData.get(w).markDirty();for(var p:w.getPlayers())if(p.getBlockPos().isWithinDistance(t.anchor,384))p.sendMessage(Text.literal("§6"+s.name+": наступила эпоха готики и пороха!"),false);}
 }
 static void ensureResidence(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s){ensureResidence(w,team,s,"steam_residence_v1:"+s.village,"steam_townhouse");}
 static void ensureResidence(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s,String id,String templateName){
  ensureResidence(w,team,s,id,templateName,false);
 }
 static void ensureResidence(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s,String id,String templateName,boolean central){
  var projects=VillageProjects.get(w);if(projects.exists(id))return;
  var template=VillageEraPlans.template(w,templateName);if(template==null)return;
  int[] rejected=new int[4];
  String search=central?"steam_central_search":"steam_residence_search";
  int serial=s.xp.getOrDefault(search,0),width=template.size().getX(),depth=template.size().getZ();
  var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
  for(int n=serial;n<serial+8;n++){
   double angle=n*2.399963229728653;int radius=central&&n%3!=2?14+Math.floorMod(n,11)*6:id.startsWith("gothic_infill_")?28+Math.floorMod(n,8)*10:28+Math.floorMod(n,16)*12;
   int x=team.anchor.getX()+(int)(Math.cos(angle)*radius),z=team.anchor.getZ()+(int)(Math.sin(angle)*radius);
   if(memory.houses.stream().filter(VillageBuildingSites::reserves).anyMatch(h->h.min.getX()-3<x+width&&h.max.getX()+3>x&&h.min.getZ()-3<z+depth&&h.max.getZ()+3>z)){rejected[0]++;continue;}
   if(projects.jobs.stream().filter(p->!p.done&&VillageBuildingSites.building(p.kind)).anyMatch(p->p.min.getX()-3<x+width&&p.max.getX()+3>x&&p.min.getZ()-3<z+depth&&p.max.getZ()+3>z)){rejected[1]++;continue;}
   boolean loaded=true;for(int cx=x>>4;cx<=(x+width)>>4;cx++)for(int cz=z>>4;cz<=(z+depth)>>4;cz++)if(!VillageProjects.keepLoaded(w,new net.minecraft.util.math.BlockPos(cx*16,64,cz*16)))loaded=false;
   if(!loaded){s.xp.put(search,n);VillageEraData.get(w).markDirty();return;}
   int top=VillageEraPlans.levelSite(w,x,z,width,depth,6);if(top==Integer.MIN_VALUE){rejected[2]++;continue;}
   var origin=new net.minecraft.util.math.BlockPos(x,top-1,z);Map<net.minecraft.util.math.BlockPos,BlockState> plan=new LinkedHashMap<>();boolean safe=true;
   for(var r:template.blocks()){
    var pos=origin.add(r.pos());var old=w.getBlockState(pos);
    if(!w.getWorldBorder().contains(pos)||old.hasBlockEntity()||!old.isAir()&&!old.isReplaceable()&&!VillageProjects.terrain(old)&&!old.isIn(net.minecraft.registry.tag.BlockTags.LOGS)&&!old.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)){safe=false;break;}
    plan.put(pos,r.state());
   }
   if(!safe){rejected[3]++;continue;}
   for(int dx=0;dx<width;dx++)for(int dz=0;dz<depth;dz++)for(int down=1;down<=6;down++){
    var pos=origin.add(dx,-down,dz);if(w.getBlockState(pos).isSolidBlock(w,pos))break;plan.put(pos,Blocks.STONE_BRICKS.getDefaultState());
   }
   projects.add(w,id,s.village,"house",plan);s.xp.put(search,n+1);VillageEraData.get(w).markDirty();return;
  }
  if(Boolean.getBoolean("halloween.recoveryTest"))org.slf4j.LoggerFactory.getLogger("halloween-recovery").info("Home search {}..{} rejections building={} project={} ground={} obstruction={}",serial,serial+8,rejected[0],rejected[1],rejected[2],rejected[3]);
  s.xp.put(search,serial+8);VillageEraData.get(w).markDirty();
 }

}
