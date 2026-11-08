package dev.halloween;
import java.util.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
/** A persistent world ledger. Distant cities advance by time, without ticking imaginary NPCs or loading the entire world. */
public final class WorldSettlements extends PersistentState {
 static final class City {
  static final Codec<City> CODEC=RecordCodecBuilder.create(i->i.group(BlockPos.CODEC.fieldOf("pos").forGetter(c->c.pos),Codec.STRING.fieldOf("name").forGetter(c->c.name),Codec.LONG.fieldOf("seen").forGetter(c->c.seen),Codec.LONG.optionalFieldOf("active",0L).forGetter(c->c.active),Codec.DOUBLE.optionalFieldOf("observed",1d).forGetter(c->c.observed),Codec.DOUBLE.optionalFieldOf("target",1d).forGetter(c->c.target),Codec.BOOL.optionalFieldOf("catchup",false).forGetter(c->c.catchup),Codec.BOOL.optionalFieldOf("home",false).forGetter(c->c.home)).apply(i,City::new));
  final BlockPos pos;final String name;final long seen;long active;double observed,target;boolean catchup,home;
  City(BlockPos p,String name,long seen,long active,double observed,double target,boolean catchup,boolean home){this.pos=p;this.name=name;this.seen=seen;this.active=active;this.observed=observed;this.target=target;this.catchup=catchup;this.home=home;}
 }
 record Warrant(String uuid,String name,int stars,BlockPos pos,String origin,String city,long time){
  static final Codec<Warrant> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("uuid").forGetter(Warrant::uuid),Codec.STRING.fieldOf("name").forGetter(Warrant::name),Codec.INT.fieldOf("stars").forGetter(Warrant::stars),BlockPos.CODEC.fieldOf("pos").forGetter(Warrant::pos),Codec.STRING.fieldOf("origin").forGetter(Warrant::origin),Codec.STRING.fieldOf("city").forGetter(Warrant::city),Codec.LONG.fieldOf("time").forGetter(Warrant::time)).apply(i,Warrant::new));
 }
 static final Codec<WorldSettlements> CODEC=RecordCodecBuilder.create(i->i.group(City.CODEC.listOf().optionalFieldOf("cities",List.of()).forGetter(s->s.cities),Warrant.CODEC.listOf().optionalFieldOf("warrants",List.of()).forGetter(s->new ArrayList<>(s.warrants.values())),Codec.LONG.optionalFieldOf("epoch",-1L).forGetter(s->s.epoch)).apply(i,WorldSettlements::new));
 static final PersistentStateType<WorldSettlements> TYPE=new PersistentStateType<>("halloween_world_cities",()->new WorldSettlements(List.of(),List.of(),-1),CODEC,null);
 long estimateTick=Long.MIN_VALUE;double estimateMean=1;
 final List<City> cities;final Map<String,Warrant> warrants=new LinkedHashMap<>();long epoch;
 WorldSettlements(List<City> cities,List<Warrant> warrants,long epoch){this.cities=new ArrayList<>(cities);for(var r:warrants)this.warrants.put(r.uuid,r);this.epoch=epoch;}
 static WorldSettlements get(ServerWorld w){return w.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE);}
 public static City discover(ServerWorld w,BlockPos pos){var state=get(w);var c=state.cities.stream().filter(v->VillageEras.horizontal(v.pos,pos)<192*192).min(Comparator.comparingDouble(v->VillageEras.horizontal(v.pos,pos))).orElse(null);if(c!=null)return c;
  String[] names={"Берёзовка","Ольховка","Заречье","Сосновый Бор","Лесогорье","Дубровка","Медные Ключи","Каменка","Ясная Поляна","Воронец","Речное","Светлогорье","Тихий Лог","Златополь","Кленовка","Рябиново","Луговое","Северная Застава","Беловодье","Красный Холм","Соколинка"};
  int hash=Math.floorMod(Long.hashCode(new ChunkPos(pos).toLong()),names.length);String initial=names[hash];String name=initial;if(state.cities.stream().anyMatch(v->v.name.equals(initial)))name+=" на "+Math.abs(pos.getX()/16);
  c=new City(pos.toImmutable(),name,w.getTime(),0,1,1,false,false);state.cities.add(c);state.markDirty();return c;
 }
 static double clockStage(long elapsed){double days=Math.max(0,elapsed)/24000d;return Math.min(3,days<8?1+days/8:2+(days-8)/50);}
 static double variation(BlockPos pos){return (Math.floorMod(Long.hashCode(pos.asLong()),1001)/1000d-.5)*.32;}
 static double estimate(ServerWorld w,City c){var state=get(w);if(state.estimateTick!=w.getTime()){state.estimateTick=w.getTime();double clock=clockStage(w.getTime()-Math.max(0,state.epoch));double observed=state.cities.stream().mapToDouble(v->v.observed).max().orElse(1);state.estimateMean=Math.max(clock,observed-.1);}return Math.max(c.observed,Math.clamp(state.estimateMean+variation(c.pos),1,3));}

 static int population(ServerWorld w,City c){int base=22+Math.floorMod(Long.hashCode(c.pos.asLong()),27);double growth=1+(estimate(w,c)-1)*.65;return (int)Math.round(base*growth);}
 static String development(double stage){int era=(int)Math.floor(stage+.0001);String title=VillageEpoch.title(era);if(era<3&&stage-era>.55)return title+" → переход к "+(era+1)+" (≈"+(int)((stage-era)*100)+"%)";return title;}
 static String inventions(double stage){return stage>=4?"городские кварталы, многоэтажное жильё и школы":stage>=3?"порох, мушкеты, рельсы и паровой транспорт":stage>=2?"каменная кладка, металлургия, мастерские":"земледелие, деревообработка, добыча руды";}
 static City nearest(ServerWorld w,BlockPos p){return get(w).cities.stream().min(Comparator.comparingDouble(c->VillageEras.horizontal(c.pos,p))).orElse(null);}
 static void noteMigration(ServerWorld w,LivingEntity v,City city){if(city==null)return;if(v instanceof VillagerEntity person){var team=VillageEras.team(w,city.pos);if(team!=null){person.getCommandTags().removeIf(t->t.startsWith("halloween_era_village_"));person.addCommandTag("halloween_era_village_"+team.village);}}var state=get(w);String id=v.getUuidAsString();var old=state.warrants.get(id);if(old!=null){state.warrants.put(id,new Warrant(id,old.name,old.stars,v.getBlockPos(),old.origin,city.name,w.getTime()));state.markDirty();}}
 static void observeWanted(ServerWorld w,LivingEntity v){var state=get(w);String id=v.getUuidAsString();boolean wanted=v.getCommandTags().contains("halloween_wanted")||v.getCommandTags().contains("halloween_player_wanted");
  if(!wanted||!v.isAlive()||v.getCommandTags().contains("halloween_prisoner")||v.getCommandTags().contains("halloween_player_jailed")){if(state.warrants.remove(id)!=null)state.markDirty();return;}
  var c=nearest(w,v.getBlockPos());String city=c==null?"Вне поселений":c.name;var old=state.warrants.get(id);int stars=v instanceof net.minecraft.server.network.ServerPlayerEntity p?PlayerJustice.wantedLevel(p):v.getCommandTags().contains("halloween_crime_murder")?2:1;
  state.warrants.put(id,new Warrant(id,v.getName().getString(),stars,v.getBlockPos(),old==null?city:old.origin,city,w.getTime()));state.markDirty();
 }
 static boolean loadedNearby(ServerWorld w,BlockPos pos){return w.isChunkLoaded(new ChunkPos(pos).toLong())&&w.shouldTickEntityAt(pos)&&w.getPlayers().stream().anyMatch(p->VillageEras.horizontal(p.getBlockPos(),pos)<Math.pow(Math.max(384,p.getViewDistance()*16),2));}
 static void register(){
  // Use structure starts that Minecraft has already loaded. A repeating /locate can synchronously generate terrain.
  net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((w,chunk)->{if(w!=w.getServer().getOverworld())return;var registry=w.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.STRUCTURE);for(var entry:chunk.getStructureStarts().entrySet()){var id=registry.getId(entry.getKey());if(id!=null&&id.getPath().startsWith("village")&&entry.getValue().hasChildren())discover(w,entry.getValue().getBoundingBox().getCenter());}});
  net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((v,source)->{if(v.getEntityWorld() instanceof ServerWorld w){var data=get(w);if(data.warrants.remove(v.getUuidAsString())!=null)data.markDirty();}});
  ServerTickEvents.END_WORLD_TICK.register(WorldSettlements::tick);
 }
 static void tick(ServerWorld w){
  if(w!=w.getServer().getOverworld()||w.getPlayers().isEmpty())return;var state=get(w);if(state.epoch<0){state.epoch=w.getTime();state.markDirty();}
  var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
  // Spread city planning across ticks; distant cities retain a time-based estimate, never work tickets.
  int selected=(int)Math.floorMod(w.getTime(),Math.max(100,memory.teams.size()));
  if(selected<memory.teams.size()){
   var t=memory.teams.get(selected);
   if(loadedNearby(w,t.anchor)){
    var c=discover(w,t.anchor);var s=VillageEraData.get(w).settlement(t);double actual=s.era+(s.transitionAt>=0?.65:s.xp.getOrDefault(VillageSteam.PHASE,0)==1?.65:0);c.observed=Math.max(c.observed,actual);
    boolean close=w.getPlayers().stream().anyMatch(p->VillageEras.horizontal(p.getBlockPos(),t.anchor)<128*128);
    if(close){c.active=w.getTime();if(state.cities.stream().noneMatch(v->v.home))c.home=true;}
    else if(!c.home&&(c.active==0||w.getTime()-c.active>24000)){c.target=estimate(w,c);c.catchup=c.target>=2&&s.era<(int)c.target;}
    if(c.catchup&&!close){if(c.target>=2&&s.era==1&&s.transitionAt<0)VillageEras.begin(w,t,s);if(c.target>=3&&s.era==2)VillageSteam.start(w,t);if(s.era>=(int)c.target)c.catchup=false;}
    state.markDirty();
   }
  }
  if(w.getTime()%100==0)for(var p:w.getPlayers()){observeWanted(w,p);for(var v:w.getEntitiesByClass(VillagerEntity.class,new Box(p.getBlockPos()).expand(192),v->true))observeWanted(w,v);}
  // One loaded city per slice, one global block budget. Never load chunks to materialize an estimate.
  if(w.getTime()%5==0&&!state.cities.isEmpty()){
   var c=state.cities.get((int)Math.floorMod(w.getTime()/5,state.cities.size()));
   if(c.catchup&&loadedNearby(w,c.pos)){var t=VillageEras.team(w,c.pos);if(t!=null&&w.getPlayers().stream().noneMatch(p->VillageEras.horizontal(p.getBlockPos(),t.anchor)<128*128))VillageProjects.materialize(w,t.village,32);}
  }
 }
}
