package dev.halloween;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Durable, bounded construction queues. A changed player block blocks a job instead of being erased. */
final class VillageProjects extends PersistentState {
    static final class Project {
        static final Codec<Project> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("id").forGetter(p->p.id),Codec.LONG.fieldOf("village").forGetter(p->p.village),Codec.STRING.fieldOf("kind").forGetter(p->p.kind),
            BlockPos.CODEC.fieldOf("min").forGetter(p->p.min),BlockPos.CODEC.fieldOf("max").forGetter(p->p.max),
            VillageCivilWorks.Change.CODEC.listOf().fieldOf("plan").forGetter(p->p.plan),Codec.INT.optionalFieldOf("index",0).forGetter(p->p.index),
            Codec.BOOL.optionalFieldOf("done",false).forGetter(p->p.done),Codec.INT.optionalFieldOf("blocked",0).forGetter(p->p.blocked)).apply(i,Project::new));
        final String id,kind;final long village;final BlockPos min,max;final List<VillageCivilWorks.Change> plan;int index,blocked;boolean done;
        Project(String id,long village,String kind,BlockPos min,BlockPos max,List<VillageCivilWorks.Change> plan,int index,boolean done,int blocked){this.id=id;this.village=village;this.kind=kind;this.min=min;this.max=max;this.plan=new ArrayList<>(plan);this.index=index;this.done=done;this.blocked=blocked;if(kind.equals("shipyard")&&!done)NavalWorks.orderRemaining(this.plan,index);if(kind.equals("cathedral")&&!done){var seen=new HashSet<BlockPos>();boolean unique=true;for(int i=index;i<this.plan.size();i++)if(!seen.add(this.plan.get(i).pos())){unique=false;break;}if(unique)this.plan.subList(index,this.plan.size()).sort(workOrder(kind));}}
    }
    private static Comparator<VillageCivilWorks.Change> workOrder(String kind){
        Comparator<VillageCivilWorks.Change> order=Comparator.<VillageCivilWorks.Change>comparingInt(c->c.after().isAir()?0:
                c.after().getBlock() instanceof LanternBlock&&c.after().get(LanternBlock.HANGING)?2:1).thenComparingInt(c->c.after().isAir()?-c.pos().getY():c.pos().getY());
        if(kind.equals("plantation"))order=order.thenComparingInt(c->c.after().getFluidState().isEmpty()?1:0);
        // A large cathedral layer must be worked in nearby patches, not hash-map order across the whole site.
        if(kind.equals("cathedral"))order=order.thenComparingInt(c->Math.floorDiv(c.pos().getX(),8)).thenComparingInt(c->Math.floorDiv(c.pos().getZ(),8)).thenComparingInt(c->c.pos().getZ()).thenComparingInt(c->c.pos().getX());return order;
    }
    static final Codec<VillageProjects> CODEC=Project.CODEC.listOf().xmap(VillageProjects::new,s->s.jobs);
    static final PersistentStateType<VillageProjects> TYPE=new PersistentStateType<>("halloween_era_projects",()->new VillageProjects(List.of()),CODEC,null);
    final List<Project> jobs;final Map<BlockPos,BlockState> expected=new HashMap<>();
    final Map<BlockPos,Integer> unfinished=new HashMap<>();
    private record Motion(Vec3d pos,long since){}
    private static final Map<UUID,Motion> MOTION=new HashMap<>();
    private static final Map<VillagerEntity,Long> NEXT_PATH=new WeakHashMap<>();
    private static final Map<VillagerEntity,Long> WORKERS=new WeakHashMap<>();
    private static final Map<VillagerEntity,Long> CLEARING=new WeakHashMap<>();
    private record Clearance(BlockPos block,long since){}
    private static final Map<net.minecraft.entity.Entity,Clearance> CLEAR_MOTION=new WeakHashMap<>();
    private record Escape(BlockPos block,Vec3d goal,long until){}
    private static final Map<VillagerEntity,Escape> ESCAPES=new WeakHashMap<>();
    private record JobProgress(int index,long since){}
    private static final Map<Project,JobProgress> PROGRESS=new WeakHashMap<>();
    private static final Map<VillagerEntity,Map<String,Long>> REASSIGN_COOLDOWN=new WeakHashMap<>();
    private record Assignment(String project,long until){}
    private static final Map<VillagerEntity,Assignment> ASSIGNED=new WeakHashMap<>();
    private static final net.minecraft.server.world.ChunkTicketType WORK_TICKET=new net.minecraft.server.world.ChunkTicketType(200,net.minecraft.server.world.ChunkTicketType.FOR_LOADING|net.minecraft.server.world.ChunkTicketType.FOR_SIMULATION);
    private static final net.minecraft.server.world.ChunkTicketType GENERATION_TICKET=new net.minecraft.server.world.ChunkTicketType(200,net.minecraft.server.world.ChunkTicketType.FOR_LOADING);
    private static final class ChunkBudget {long tick=Long.MIN_VALUE;int requested;final Map<Long,Long> leases=new HashMap<>(),born=new HashMap<>(),cooldown=new HashMap<>();final Map<Long,BlockPos> waiting=new LinkedHashMap<>();}
    private static final Map<ServerWorld,ChunkBudget> CHUNKS=new WeakHashMap<>();
    /** Tickets let the chunk manager load asynchronously; never generate terrain inside a village iterator. */
    static boolean keepLoaded(ServerWorld w,BlockPos p){
        var chunk=new ChunkPos(p);long key=chunk.toLong(),now=w.getTime();var budget=CHUNKS.computeIfAbsent(w,k->new ChunkBudget());
        if(budget.tick!=now){budget.tick=now;budget.requested=0;
            // Competing surveys must yield slots; continually polling an incomplete site cannot own every ticket forever.
            for(var entry:List.copyOf(budget.born.entrySet()))if(now-entry.getValue()>=200){budget.leases.remove(entry.getKey());budget.born.remove(entry.getKey());budget.cooldown.put(entry.getKey(),now+40);}
            budget.cooldown.entrySet().removeIf(e->e.getValue()<=now);
            // Serve all planners in request order. A busy house survey must not starve the station survey forever.
            for(var it=budget.waiting.entrySet().iterator();it.hasNext()&&budget.leases.size()<64&&budget.requested<2;){
                var request=it.next();long queued=request.getKey();if(budget.cooldown.containsKey(queued))continue;
                var point=request.getValue();var queuedChunk=new ChunkPos(queued);
                w.getChunkManager().addTicket(w.getPlayers().stream().anyMatch(player->player.getBlockPos().isWithinDistance(point,512))?WORK_TICKET:GENERATION_TICKET,queuedChunk,2);
                budget.leases.put(queued,now);budget.born.put(queued,now);budget.requested++;it.remove();
            }
        }
        boolean loaded=w.isChunkLoaded(key);
        // Player/forced chunks need no extra lease. Reserving their slots can starve a remote worksite forever.
        if(loaded&&!budget.leases.containsKey(key)&&w.shouldTickEntityAt(p))return true;
        if(!budget.leases.containsKey(key)){
            if(budget.waiting.size()<128)budget.waiting.putIfAbsent(key,p.toImmutable());
            return loaded;
        }
        if(now-budget.leases.getOrDefault(key,Long.MIN_VALUE/2)>=40){w.getChunkManager().addTicket(w.getPlayers().stream().anyMatch(player->player.getBlockPos().isWithinDistance(p,512))?WORK_TICKET:GENERATION_TICKET,chunk,2);budget.leases.put(key,now);}
        return loaded;
    }
    VillageProjects(List<Project> jobs){this.jobs=new ArrayList<>(jobs);for(var j:jobs)for(var c:j.plan){expected.put(c.pos(),c.after());if(!j.done)unfinished.merge(c.pos(),1,Integer::sum);}}
    static VillageProjects get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(TYPE);}
    private static final Set<VillagerEntity> REPAIR_RESERVED=Collections.newSetFromMap(new WeakHashMap<>());
    static String assignedKind(VillagerEntity v){var a=ASSIGNED.get(v);if(a==null||a.until()<=v.getEntityWorld().getTime())return "";return get((ServerWorld)v.getEntityWorld()).jobs.stream().filter(p->p.id.equals(a.project())&&!p.done).map(p->p.kind).findFirst().orElse("");}
    static void resetWorker(VillagerEntity v){ASSIGNED.remove(v);WORKERS.remove(v);NEXT_PATH.remove(v);MOTION.remove(v.getUuid());}
    static boolean clearing(VillagerEntity v){return CLEARING.getOrDefault(v,0L)>v.getEntityWorld().getTime();}
    static boolean owns(VillagerEntity v){return clearing(v)||!REPAIR_RESERVED.contains(v)&&WORKERS.getOrDefault(v,0L)>v.getEntityWorld().getTime();}
    static boolean superseded(ServerWorld w,VillageMemory.BlockRecord r){var wanted=get(w).expected.get(r.pos());return wanted!=null&&!wanted.equals(r.state());}
    static boolean has(ServerWorld w,BlockPos p){return get(w).expected.containsKey(p);}
    static boolean pending(ServerWorld w,BlockPos pos){return get(w).unfinished.containsKey(pos);}
    static void recordInfrastructure(ServerWorld w,String id,Map<BlockPos,BlockState> blocks){
        var state=get(w);var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(var p:state.jobs)if(!p.done&&(p.id.startsWith("prison_rooms_")||p.kind.equals("dungeon"))&&p.plan.stream().anyMatch(c->blocks.containsKey(c.pos()))){p.done=true;for(var c:p.plan)state.unfinished.computeIfPresent(c.pos(),(pos,n)->n<=1?null:n-1);}
        state.expected.putAll(blocks);
        for(var h:memory.houses)for(int i=0;i<h.blueprint.size();i++){var old=h.blueprint.get(i);var desired=blocks.get(old.pos());if(desired!=null)h.blueprint.set(i,new VillageMemory.BlockRecord(old.pos(),desired));}
        state.markDirty();memory.markDirty();
    }
    void retire(Project p){if(p.done)return;p.done=true;for(var c:p.plan)unfinished.computeIfPresent(c.pos(),(key,n)->n<=1?null:n-1);ASSIGNED.entrySet().removeIf(e->e.getValue().project().equals(p.id));markDirty();}
    boolean exists(String id){return jobs.stream().anyMatch(p->p.id.equals(id));}
    boolean complete(long village,String kind){return jobs.stream().anyMatch(p->p.village==village&&p.kind.equals(kind))&&jobs.stream().filter(p->p.village==village&&p.kind.equals(kind)).allMatch(p->p.done);}
    void add(ServerWorld w,String id,long village,String kind,Map<BlockPos,BlockState> blocks){
        if(exists(id)||blocks.isEmpty())return;
        List<VillageCivilWorks.Change> changes=new ArrayList<>();
        for(var e:blocks.entrySet()){
            if(!w.isChunkLoaded(new ChunkPos(e.getKey()).toLong()))return;
            var before=w.getBlockState(e.getKey());if(before.hasBlockEntity()&&!before.equals(e.getValue())&&!mayRemoveInventory(w,e.getKey()))return;
            changes.add(new VillageCivilWorks.Change(e.getKey().toImmutable(),before,e.getValue()));
        }
        if(!kind.equals("shipyard"))changes.sort(workOrder(kind));
        BlockPos min=new BlockPos(blocks.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow(),blocks.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow(),blocks.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow());
        BlockPos max=new BlockPos(blocks.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow(),blocks.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow(),blocks.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow());
        var job=new Project(id,village,kind,min,max,changes,0,false,0);jobs.add(job);for(var c:changes){expected.put(c.pos(),c.after());unfinished.merge(c.pos(),1,Integer::sum);}markDirty();
    }
    void retireGardens(long village,int x0,int z0,int x1,int z1){
        for(var p:jobs)if(p.village==village&&p.kind.equals("farm_plaza")&&!p.done&&p.min.getX()<=x1&&p.max.getX()>=x0&&p.min.getZ()<=z1&&p.max.getZ()>=z0){p.done=true;for(var c:p.plan)unfinished.computeIfPresent(c.pos(),(pos,n)->n<=1?null:n-1);ASSIGNED.entrySet().removeIf(e->e.getValue().project().equals(p.id));markDirty();}
    }
    static boolean terrain(BlockState state){return VillageCivilWorks.natural(state)||state.isOf(Blocks.FARMLAND)||state.isOf(Blocks.DIRT_PATH)||state.getBlock() instanceof FluidBlock;}
    static boolean compatible(BlockState before,BlockState current){
        return current.isOf(before.getBlock())||current.isAir()||current.isReplaceable()||((before.isAir()||terrain(before))&&terrain(current));
    }
    static boolean mayRemoveInventory(ServerWorld w,BlockPos pos){return VillageHousingRebuild.mayRemoveBed(w,pos)|| VillageRelocations.mayRemove(w,pos)||VillageHousingRebuild.mayRemove(w,pos);}
    static boolean compatibleChange(VillageCivilWorks.Change change,BlockState current){
        // Connections and neighbour updates change properties of blocks another crew already placed.
        if(current.isOf(change.after().getBlock()))return true;
        if(change.before().getBlock() instanceof CropBlock&&current.getBlock() instanceof CropBlock)return true;
        // Underwater vegetation grows or breaks while the deck is built; the surveyed water remains replaceable.
        if(change.before().getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER)
                &&current.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER)
                &&(current.getBlock() instanceof FluidBlock||current.isOf(Blocks.KELP)||current.isOf(Blocks.KELP_PLANT)||current.isOf(Blocks.SEAGRASS)||current.isOf(Blocks.TALL_SEAGRASS)))return true;
        // Terrain already levelled by another crew remains valid when the survey originally saw ice or water.
        if((change.before().isOf(Blocks.ICE)||change.before().isOf(Blocks.WATER))&&terrain(current)&&terrain(change.after()))return true;
        // Open water can freeze between survey and execution; it remains the surveyed terrain.
        if(change.before().isOf(Blocks.WATER)&&current.isOf(Blocks.ICE))return true;
        return compatible(change.before(),current);
    }
    static boolean compatibleAt(ServerWorld w,VillageCivilWorks.Change change,BlockState current){return compatibleChange(change,current)||VillageBuilders.canReplaceScaffold(w,change.pos(),change.after())||get(w).jobs.stream().anyMatch(p->p.kind.equals("cathedral")&&!p.done&&p.plan.contains(change)&&constructionOwned(w,p,change.pos(),current));}
    /** Keep an obstruction in the saved plan, but allow nearby independent work to proceed. */
    static boolean deferObstruction(ServerWorld w,VillageProjects state,Project job){
        int first=job.index;
        var blocked=job.plan.get(first);
        for(int i=first+1;i<Math.min(job.plan.size(),first+128);i++){
            var candidate=job.plan.get(i);
            // Do not reorder work on the same block or mix demolition with building.
            if(candidate.pos().equals(blocked.pos()))break;
            if(candidate.after().isAir()!=blocked.after().isAir())break;
            if(!w.isChunkLoaded(new ChunkPos(candidate.pos()).toLong()))continue;
            var actual=w.getBlockState(candidate.pos());
            if(!actual.equals(candidate.after())&&(!compatibleAt(w,candidate,actual)||actual.hasBlockEntity()&&!mayRemoveInventory(w,candidate.pos())))continue;
            job.plan.remove(first);job.plan.add(i,blocked);state.markDirty();return true;
        }
        return false;
    }
    /** Independent workers finish reachable blocks on the current layer instead of chasing one shared corner. */
    static boolean nearbyLayerWork(ServerWorld w,Project job,VillagerEntity worker){
        if(!(job.kind.equals("cathedral")||job.kind.equals("house")||job.kind.equals("plantation")||job.kind.equals("farm_plaza"))||job.index>=job.plan.size())return false;
        var first=job.plan.get(job.index);
        if(worker.squaredDistanceTo(Vec3d.ofCenter(first.pos()))<=36&&placementOccupants(w,first.pos(),first.after()).isEmpty())return false;
        int best=-1;double distance=36;var seen=new HashSet<BlockPos>();seen.add(first.pos());
        for(int i=job.index+1;i<Math.min(job.plan.size(),job.index+512);i++){
            var candidate=job.plan.get(i);
            if(candidate.pos().getY()!=first.pos().getY()||candidate.after().isAir()!=first.after().isAir())break;
            if(job.kind.equals("plantation")&&candidate.after().getFluidState().isEmpty()!=first.after().getFluidState().isEmpty())continue;
            if(!seen.add(candidate.pos()))break;
            double d=worker.squaredDistanceTo(Vec3d.ofCenter(candidate.pos()));
            if(d>=distance||!w.isChunkLoaded(new ChunkPos(candidate.pos()).toLong())||VillagePrisons.managed(w,candidate.pos()))continue;
            var actual=w.getBlockState(candidate.pos());var expected=get(w).expected.get(candidate.pos());
            if(VillageConnections.matches(w,candidate.pos(),candidate.after(),actual)||expected!=null&&!expected.equals(candidate.after())||!compatibleAt(w,candidate,actual)||actual.hasBlockEntity()||!candidate.after().canPlaceAt(w,candidate.pos())||!placementOccupants(w,candidate.pos(),candidate.after()).isEmpty())continue;
            distance=d;best=i;
        }
        if(best<0)return false;job.plan.add(job.index,job.plan.remove(best));get(w).markDirty();return true;
    }
    static boolean preservePrison(ServerWorld w,VillageProjects state,Project job,VillageCivilWorks.Change change){
        if(!job.kind.equals("cathedral")||!VillagePrisons.managed(w,change.pos()))return false;
        var actual=w.getBlockState(change.pos());job.plan.set(job.index,new VillageCivilWorks.Change(change.pos(),actual,actual));job.index++;job.blocked=0;
        if(change.after().equals(state.expected.get(change.pos())))state.expected.put(change.pos(),actual);
        state.markDirty();return true;
    }
    // A road may meet a foundation erected after its survey. Preserve it and continue the road.
    static boolean bypassRoadFoundation(ServerWorld w,VillageProjects state,Project job,VillageCivilWorks.Change change){
        if(!job.kind.equals("road")||!change.after().isOf(Blocks.DIRT_PATH))return false;
        var actual=w.getBlockState(change.pos());
        if(compatibleChange(change,actual))return false;
        job.plan.set(job.index,new VillageCivilWorks.Change(change.pos(),actual,actual));job.index++;job.blocked=0;
        if(change.after().equals(state.expected.get(change.pos()))){
            state.expected.remove(change.pos());
            for(var other:state.jobs)if(other!=job&&!other.kind.equals("road"))for(var c:other.plan)if(c.pos().equals(change.pos()))state.expected.put(c.pos(),c.after());
        }
        state.markDirty();return true;
    }
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillageProjects::tick);}
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty())return;
        // Loaded workers may live in border chunks that are not entity-ticking. Reserve their simulation first.
        if(w.getTime()%20==0)for(var entity:w.iterateEntities())if(entity instanceof VillagerEntity v&&v.isAlive()&&!v.isAiDisabled()&&v.getVillagerData().profession().matchesKey(BuilderProfession.KEY)&&w.getPlayers().stream().anyMatch(p->VillageEras.horizontal(p.getBlockPos(),v.getBlockPos())<384*384))keepLoaded(w,v.getBlockPos());
        for(var entry:List.copyOf(ESCAPES.entrySet())){
            var v=entry.getKey();var escape=entry.getValue();if(v.getEntityWorld()!=w)continue;keepLoaded(w,v.getBlockPos());
            if(!v.isAlive()||w.getTime()>escape.until()||!v.getBoundingBox().intersects(new Box(escape.block()))){ESCAPES.remove(v);continue;}
            CLEARING.put(v,w.getTime()+40);WORKERS.put(v,w.getTime()+40);VillageTasks.hold(v);v.setSneaking(false);v.getNavigation().stop();
            v.getMoveControl().moveTo(escape.goal().x,escape.goal().y,escape.goal().z,.75);
            if(v.horizontalCollision&&escape.goal().y>v.getY()+.2&&w.isSpaceEmpty(v,v.getBoundingBox().offset(0,1,0)))v.getJumpControl().setActive();
        }
        if(w.getTime()%20!=0)return;
        for(var entry:List.copyOf(CLEARING.entrySet())){if(entry.getKey().getEntityWorld()!=w)continue;if(entry.getValue()<=w.getTime())CLEARING.remove(entry.getKey());else {VillageTasks.hold(entry.getKey());keepLoaded(w,entry.getKey().getBlockPos());}}
        var state=get(w);var data=VillageEraData.get(w);var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        // One-time cancellation of terrain plans created by the old one-sided-rim detector.
        for(var settlement:data.settlements.values())if(settlement.xp.getOrDefault("pit_detector_v2",0)==0){
            for(var old:List.copyOf(state.jobs))if(old.village==settlement.village&&!old.done&&old.kind.equals("ground_safety")){
                state.retire(old);
                for(var change:old.plan)if(change.after().equals(state.expected.get(change.pos()))){
                    state.expected.remove(change.pos());
                    for(var other:state.jobs)if(other!=old&&!other.kind.equals("ground_safety"))for(var c:other.plan)if(c.pos().equals(change.pos()))state.expected.put(c.pos(),c.after());
                }
            }
            settlement.xp.put("pit_detector_v2",1);data.markDirty();
        }
        // Old completed plaza jobs were not always added to the builders' repair catalogue.
        for(var completed:state.jobs)if(completed.done&&completed.index>=completed.plan.size()&&completed.kind.equals("farm_plaza")&&memory.houses.stream().noneMatch(h->h.recordKey.equals(completed.id))){
            var blocks=completed.plan.stream().map(c->new VillageMemory.BlockRecord(c.pos(),c.after())).toList();
            memory.houses.add(new VillageMemory.House(completed.min,completed.max,blocks,w.getTime(),List.of(),0,true,List.of(),List.of(),List.of(),"era",completed.id,List.of()));memory.markDirty();
        }

        Set<UUID> used=new HashSet<>();Map<Long,Integer> conversion=new HashMap<>();int active=0;
        // Release construction workers while recorded buildings or explosion damage need repair.
        Set<Long> damaged=new HashSet<>();
        for(var h:List.copyOf(memory.houses)){
            if(!w.isChunkLoaded(new ChunkPos(h.min).toLong())||w.getPlayers().stream().noneMatch(player->VillageEras.horizontal(player.getBlockPos(),h.min)<384*384))continue;
            var team=VillageEras.team(w,h.min);
            if(team!=null&&!VillageTerrain.removesHouse(w,h)&&!damaged.contains(team.village)&&VillageBuilders.findRepair(w,h)!=null)damaged.add(team.village);
        }
        REPAIR_RESERVED.removeIf(v->v.getEntityWorld()==w);
        for(long village:damaged){
            var available=new ArrayList<VillagerEntity>();
            for(var player:w.getPlayers())for(var v:w.getEntitiesByClass(VillagerEntity.class,new Box(player.getBlockPos()).expand(160),v->v.isAlive()&&!v.isBaby()&&!v.isSleeping()&&!v.isAiDisabled()&&!VillageTrials.busy(v)&&!v.getCommandTags().contains("halloween_court_builder")&&v.getVillagerData().profession().matchesKey(BuilderProfession.KEY)&&VillageEras.belongs(w,v,village)))if(!available.contains(v))available.add(v);
            available.sort(Comparator.comparing(VillagerEntity::getUuid));
            for(int i=0;i<Math.min((data.settlements.get(village)!=null&&data.settlements.get(village).transitionAt>=0||state.jobs.stream().anyMatch(p->p.village==village&&!p.done&&(p.id.startsWith("gothic_rebuild:")||p.kind.equals("cathedral"))))?1:4,Math.min(Math.max(0,available.size()-1),available.size()/2))&&i<available.size();i++)REPAIR_RESERVED.add(available.get(i));
        }
        for(var entry:List.copyOf(ASSIGNED.entrySet())){
            var project=state.jobs.stream().filter(p->p.id.equals(entry.getValue().project())).findFirst().orElse(null);
            if(project!=null&&REPAIR_RESERVED.contains(entry.getKey())){WORKERS.remove(entry.getKey());ASSIGNED.remove(entry.getKey());}
        }
        var nearbyVillages=new HashSet<Long>();for(var team:memory.teams)if(w.getPlayers().stream().anyMatch(player->VillageEras.horizontal(player.getBlockPos(),team.anchor)<384*384))nearbyVillages.add(team.village);
        var queue=new ArrayList<Project>();for(var job:state.jobs)if(!job.done&&(nearbyVillages.contains(job.village)||w.getPlayers().stream().anyMatch(player->VillageEras.horizontal(player.getBlockPos(),job.min)<384*384)))queue.add(job);
        if(!queue.isEmpty())Collections.rotate(queue,-(int)((w.getTime()/20)%queue.size()));
        queue.sort(Comparator.comparingInt(VillageProjects::priority));
        int warmSites=0;
        for(var p:queue){
            if(p.done)continue;
            if(priority(p)>=4&&state.jobs.stream().anyMatch(m->m.village==p.village&&!m.done&&(m.id.startsWith("gothic_rebuild:")||m.kind.equals("cathedral"))))continue;
            if(p.kind.equals("conversion")&&memory.houses.stream().anyMatch(h->p.id.equals("era_stone:"+p.village+":"+h.min.asLong())&&VillageTerrain.removesHouse(w,h))){finish(w,state,memory,p);continue;}
            if(p.kind.equals("conversion")&&conversion.getOrDefault(p.village,0)>=3)continue;
            var settlement=data.settlements.get(p.village);boolean overtime=false;
            // A nighttime pause must not unload the crew and make the worksite undiscoverable at dawn.
            if(warmSites<8&&priority(p)<=3&&p.index<p.plan.size()){
                warmSites++;var site=p.plan.get(p.index).pos();
                keepLoaded(w,site);for(var direction:Direction.Type.HORIZONTAL)keepLoaded(w,site.offset(direction,16));
            }
            if(!VillageBuilders.workHours(w))continue;
            if((p.kind.startsWith("workshop_")||p.kind.equals("house"))&&(VillageArchitecture.conversionsPending(w,p.village)||(settlement!=null&&VillageArchitectureMigration.running(settlement))))continue;
            if(active>=8)break;
            if(p.index>=p.plan.size()){finish(w,state,memory,p);continue;}
            // Already correct air/blocks do not consume worker actions or require travel.
            for(int skipped=0;skipped<2048&&p.index<p.plan.size();skipped++){
                var next=p.plan.get(p.index);if(!w.isChunkLoaded(new ChunkPos(next.pos()).toLong())||!VillageConnections.matches(w,next.pos(),next.after(),w.getBlockState(next.pos())))break;
                p.index++;state.markDirty();
            }
            if(p.index>=p.plan.size()){finish(w,state,memory,p);continue;}
            var progress=PROGRESS.get(p);if(progress==null||progress.index()!=p.index)PROGRESS.put(p,new JobProgress(p.index,w.getTime()));
            else if(w.getTime()-progress.since()>=400){
                for(var assignment:List.copyOf(ASSIGNED.entrySet()))if(assignment.getValue().project().equals(p.id)){
                    var motion=MOTION.get(assignment.getKey().getUuid());
                    // A long walk to a remote site is progress. Do not reroute a moving crew every 20 seconds.
                    if(motion!=null&&w.getTime()-motion.since()<200)continue;
                    REASSIGN_COOLDOWN.computeIfAbsent(assignment.getKey(),k->new HashMap<>()).put(p.id,w.getTime()+200);WORKERS.remove(assignment.getKey());ASSIGNED.remove(assignment.getKey());assignment.getKey().getNavigation().stop();
                }
                PROGRESS.put(p,new JobProgress(p.index,w.getTime()));
            }
            var change=p.plan.get(p.index);if(!keepLoaded(w,change.pos()))continue;
            if(preservePrison(w,state,p,change)||bypassRoadFoundation(w,state,p,change))continue;
            var actual=w.getBlockState(change.pos());
            var latest=state.expected.get(change.pos());
            if(latest!=null&&!latest.equals(change.after())){p.index++;state.markDirty();continue;}
            if(!actual.equals(change.after())&&(!compatibleAt(w,change,actual)||actual.hasBlockEntity()&&!mayRemoveInventory(w,change.pos()))){p.blocked++;deferObstruction(w,state,p);state.markDirty();continue;}
            var builders=w.getEntitiesByClass(VillagerEntity.class,new Box(Vec3d.of(p.min),Vec3d.of(p.max).add(1,1,1)).expand(384,96,384),v->v.isAlive()&&!v.isBaby()&&!v.isAiDisabled()
                &&(p.kind.equals("road")?v.getVillagerData().profession().matchesKey(VillageJanitors.KEY):v.getVillagerData().profession().matchesKey(BuilderProfession.KEY))&&!REPAIR_RESERVED.contains(v)&&!clearing(v)&&!VillageTrials.busy(v)&&!v.getCommandTags().contains("halloween_court_builder")&&!used.contains(v.getUuid())
                &&VillageEras.belongs(w,v,p.village)&&canAssign(state,p,v,w.getTime()));
            builders.sort(Comparator.<VillagerEntity>comparingInt(v->REASSIGN_COOLDOWN.getOrDefault(v,Map.of()).getOrDefault(p.id,0L)>w.getTime()?1:0).thenComparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(change.pos()))));
            if(Boolean.getBoolean("halloween.eraSoak")&&w.getTime()%10000==0&&p.id.startsWith("gothic_rebuild:"))org.slf4j.LoggerFactory.getLogger("era-soak").info("CREW {} index={} candidates={} occupants={}",p.id,p.index,builders.size(),w.getOtherEntities(null,new Box(change.pos()),e->e instanceof LivingEntity&&e.isAlive()).stream().map(e->e.getBlockPos().toString()).toList());
            if(builders.isEmpty())continue;
            active++;if(p.kind.equals("conversion"))conversion.merge(p.village,1,Integer::sum);
            // Multiple builders cooperate on one house; different projects reserve different workers.
            int limit=p.kind.equals("cathedral")?8:p.kind.equals("conversion")||p.kind.equals("house")?4:3;
            // Leave workers for later mandatory sites instead of committing every crew to the first wall.
            long waiting=queue.stream().filter(other->!other.done&&other.village==p.village&&priority(other)<=3).count();
            limit=Math.min(limit,Math.max(1,builders.size()/(int)Math.max(1,Math.min(8-active+1,waiting))));
            for(var worker:builders.subList(0,Math.min(limit,builders.size()))){
                if(clearing(worker))continue;
                used.add(worker.getUuid());keepLoaded(w,worker.getBlockPos());WORKERS.put(worker,w.getTime()+35);VillageTasks.hold(worker);if(worker.isSleeping())worker.wakeUp();
                ASSIGNED.put(worker,new Assignment(p.id,w.getTime()+100));
                int rate=16+VillageEras.personalLevel(worker)*4;
                for(int action=0;action<rate&&p.index<p.plan.size();action++){
                    nearbyLayerWork(w,p,worker);
                    var c=p.plan.get(p.index);if(!w.isChunkLoaded(new ChunkPos(c.pos()).toLong()))break;
                    var current=w.getBlockState(c.pos());
                    if(VillageConnections.matches(w,c.pos(),c.after(),current)){p.index++;state.markDirty();continue;}
                    var newest=state.expected.get(c.pos());
                    if(newest!=null&&!newest.equals(c.after())){p.index++;state.markDirty();continue;}
                    // Neighbour updates change stairs, crops and farmland while a plan is in progress.
                    if(preservePrison(w,state,p,c)||bypassRoadFoundation(w,state,p,c))continue;
                    if(!compatibleAt(w,c,current)||current.hasBlockEntity()&&!mayRemoveInventory(w,c.pos())) {p.blocked++;deferObstruction(w,state,p);state.markDirty();break;}
                    if(c.after().isOf(RailContent.TRACK)&&!RailWorks.supply(w,p.village,worker))break;
                    BlockPos work=workPosition(w,p,c);
                    if(worker.squaredDistanceTo(Vec3d.ofCenter(work))>36){
                        if(work.equals(c.pos())&&work.getY()>worker.getBlockY()+3){
                            approach(w,worker,work);
                            if(worker.getNavigation().isIdle())VillageBuilders.projectAccess(w,memory,p,worker,work);
                        }
                        else approach(w,worker,work);
                        break;
                    }
                    if(!c.after().isAir()){
                        var occupants=placementOccupants(w,c.pos(),c.after());
                        if(!occupants.isEmpty()){
                            clearWorkArea(w,c.pos(),occupants,p);
                            // Do not seal the remaining exits around a worker while deferring this block.
                            break;
                        }
                    }
                    if(!w.setBlockState(c.pos(),VillageConnections.connected(w,c.pos(),c.after()),Block.NOTIFY_ALL))break;
                    if(c.after().isOf(RailContent.TRACK)){RailWorks.take(worker.getInventory(),RailContent.TRACK.asItem(),1);RailwayNetwork.configureTrack(w,c.pos());}
                    worker.swingHand(Hand.MAIN_HAND);VillageEras.work(w,worker,"builder",1);p.index++;p.blocked=0;state.markDirty();
                }
            }
            if(p.index>=p.plan.size())finish(w,state,memory,p);
        }
    }
    static int priority(Project p){
        if(p.kind.equals("dungeon"))return 0;
        if(p.kind.equals("conversion")||p.kind.equals("steam_conversion"))return 1;
        if(p.kind.equals("plantation"))return 2;
        if(p.kind.equals("perimeter_removal")||p.kind.equals("treasury")||p.kind.equals("factory")||p.kind.equals("factory_supplies")||p.kind.equals("bridge")||p.kind.equals("cathedral")||p.id.startsWith("steam_residence_")||p.id.startsWith("gothic_rebuild:"))return 3;
        if(p.kind.equals("railway")||p.kind.equals("rail_station")||p.kind.equals("rail_foundry"))return 4;
        if((p.kind.equals("house")||p.kind.startsWith("workshop_"))&&p.index>0)return 4;
        return p.kind.equals("ground_safety")?6:5;
    }
    private static boolean canAssign(VillageProjects state,Project p,VillagerEntity worker,long time){
        var assignment=ASSIGNED.get(worker);if(assignment==null||assignment.until()<=time||assignment.project().equals(p.id))return true;
        return state.jobs.stream().filter(old->old.id.equals(assignment.project())).findFirst().map(old->old.done||priority(p)<priority(old)).orElse(true);
    }
    static List<net.minecraft.entity.Entity> placementOccupants(ServerWorld w,BlockPos pos,BlockState state){
        var shape=state.getCollisionShape(w,pos);if(shape.isEmpty())return List.of();
        var boxes=shape.getBoundingBoxes().stream().map(b->b.offset(pos)).toList();
        return w.getOtherEntities(null,shape.getBoundingBox().offset(pos),e->e instanceof LivingEntity&&e.isAlive()&&boxes.stream().anyMatch(b->b.intersects(e.getBoundingBox())));
    }
    static boolean outsidePlacement(net.minecraft.entity.Entity entity,BlockPos block,Vec3d destination){
        return !entity.getBoundingBox().offset(destination.subtract(entity.getEntityPos())).intersects(new Box(block));
    }
    static void clearWorkArea(ServerWorld w,BlockPos block,List<net.minecraft.entity.Entity> occupants){clearWorkArea(w,block,occupants,null);}
    static void clearWorkArea(ServerWorld w,BlockPos block,List<net.minecraft.entity.Entity> occupants,Project project){
        for(var occupant:occupants)if(occupant instanceof net.minecraft.entity.mob.MobEntity mob){
            var previous=CLEAR_MOTION.get(mob);
            if(previous==null||!previous.block().equals(block)){previous=new Clearance(block.toImmutable(),w.getTime());CLEAR_MOTION.put(mob,previous);}
            if(w.getTime()-previous.since()>100&&relocateBlockedMob(w,mob,block,project)){
                CLEAR_MOTION.remove(mob);continue;
            }
            if(mob.isAiDisabled())continue;
            if(mob instanceof VillagerEntity v&&w.getTime()-previous.since()>60&&beginEscape(w,v,block))continue;
            if(project!=null&&w.getTime()-previous.since()>100&&openConstructionExit(w,project,mob)){mob.getNavigation().stop();CLEAR_MOTION.remove(mob);}
            if(mob instanceof VillagerEntity v&&clearing(v)&&!mob.getNavigation().isIdle()){CLEARING.put(v,w.getTime()+100);WORKERS.put(v,w.getTime()+100);continue;}
            if(mob instanceof VillagerEntity v){if(v.isSleeping())v.wakeUp();v.setSneaking(false);VillageTasks.hold(v);}
            int attempts=0;boolean routed=false;
            for(BlockPos q:BlockPos.iterateOutwards(block,6,5,6)){
                if(q.equals(block)||!w.isChunkLoaded(new ChunkPos(q).toLong())||!w.getWorldBorder().contains(q)||!w.getFluidState(q).isEmpty())continue;
                var shape=w.getBlockState(q).getCollisionShape(w,q);var below=w.getBlockState(q.down()).getCollisionShape(w,q.down());
                if(shape.isEmpty()&&below.isEmpty())continue;
                double height=shape.isEmpty()?-1+below.getMax(Direction.Axis.Y):shape.getMax(Direction.Axis.Y);
                if(height>1)continue;
                var destination=Vec3d.ofBottomCenter(q).add(0,height,0);
                var stand=BlockPos.ofFloored(destination);
                // A floor below the task can resolve to the occupied task itself. Never route back into it.
                if(!outsidePlacement(mob,block,destination))continue;
                if(!w.isSpaceEmpty(mob,mob.getBoundingBox().offset(destination.subtract(mob.getEntityPos()))))continue;
                if(!w.getOtherEntities(mob,new Box(q).stretch(0,1,0),e->e instanceof LivingEntity&&e.isAlive()).isEmpty())continue;
                if(++attempts>8||!VillagePathBudget.takeClearance(w))break;
                var path=mob.getNavigation().findPathTo(stand,0);
                if(path!=null&&path.reachesTarget()&&mob.getNavigation().startMovingAlong(path,.75)){
                    if(mob instanceof VillagerEntity v)v.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(stand,.75f,0),80);
                    mob.getNavigation().startMovingAlong(path,.75);routed=true;
                    if(mob instanceof VillagerEntity v){CLEARING.put(v,w.getTime()+100);WORKERS.put(v,w.getTime()+100);ASSIGNED.remove(v);}
                    break;
                }
            }
            if(!routed&&project!=null)openConstructionExit(w,project,mob);
        }
    }
    static boolean relocateBlockedMob(ServerWorld w,net.minecraft.entity.mob.MobEntity mob,BlockPos block){
        return relocateBlockedMob(w,mob,block,null);
    }
    static boolean relocateBlockedMob(ServerWorld w,net.minecraft.entity.mob.MobEntity mob,BlockPos block,Project project){
        if(mob.hasVehicle()||mob.hasPassengers()||mob.isLeashed()||mob instanceof VillagerEntity v&&VillageTrials.busy(v))return false;
        var state=get(w);
        for(BlockPos feet:BlockPos.iterateOutwards(mob.getBlockPos(),6,5,6)){
            if(!w.isChunkLoaded(feet)||!w.getWorldBorder().contains(feet)||!w.getFluidState(feet).isEmpty()
                    ||!w.getFluidState(feet.down()).isEmpty())continue;
            if(List.of(feet,feet.up()).stream().anyMatch(p->state.unfinished.containsKey(p)
                    &&!state.expected.getOrDefault(p,Blocks.AIR.getDefaultState()).getCollisionShape(w,p).isEmpty()))continue;
            if(state.unfinished.containsKey(feet.down())&&!state.expected.getOrDefault(feet.down(),Blocks.AIR.getDefaultState())
                    .isSideSolidFullSquare(w,feet.down(),Direction.UP))continue;
            if(!w.getBlockState(feet.down()).isSideSolidFullSquare(w,feet.down(),Direction.UP))continue;
            var goal=Vec3d.ofBottomCenter(feet);
            var box=mob.getBoundingBox().offset(goal.subtract(mob.getEntityPos()));
            if(!outsidePlacement(mob,block,goal)||!w.isSpaceEmpty(mob,box)
                    ||!w.getOtherEntities(mob,box,e->e instanceof LivingEntity&&e.isAlive()).isEmpty())continue;
            mob.getNavigation().stop();
            if(mob instanceof VillagerEntity villager){
                villager.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);
                ESCAPES.remove(villager);
                CLEARING.put(villager,w.getTime()+100);
            }
            mob.requestTeleport(goal.x,goal.y,goal.z);
            mob.setVelocity(Vec3d.ZERO);
            mob.fallDistance=0;
            return true;
        }
        // During earthworks an entire pad may still be planned solid ground. Search its actual edges,
        // rather than repeatedly offering destinations six blocks away inside the same future foundation.
        if(project!=null){
            var candidates=new ArrayList<BlockPos>();
            int x=Math.clamp(mob.getBlockX(),project.min.getX(),project.max.getX());
            int z=Math.clamp(mob.getBlockZ(),project.min.getZ(),project.max.getZ());
            for(int offset=-4;offset<=4;offset++){
                candidates.add(new BlockPos(project.min.getX()-2,0,z+offset));candidates.add(new BlockPos(project.max.getX()+2,0,z+offset));
                candidates.add(new BlockPos(x+offset,0,project.min.getZ()-2));candidates.add(new BlockPos(x+offset,0,project.max.getZ()+2));
            }
            candidates.sort(Comparator.comparingDouble(p->VillageEras.horizontal(p,mob.getBlockPos())));
            for(var column:candidates){
                if(!w.isChunkLoaded(column)){keepLoaded(w,column);continue;}
                int top=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ()),limit=Math.max(w.getBottomY()+1,top-40);
                while(top>limit){var b=w.getBlockState(column.withY(top-1));if(b.isAir()||b.isReplaceable()&&b.getFluidState().isEmpty()||b.isIn(net.minecraft.registry.tag.BlockTags.LOGS)||b.isIn(net.minecraft.registry.tag.BlockTags.LEAVES))top--;else break;}
                var feet=column.withY(top);var goal=Vec3d.ofBottomCenter(feet);var box=mob.getBoundingBox().offset(goal.subtract(mob.getEntityPos()));
                if(!w.getWorldBorder().contains(feet)||!w.getFluidState(feet.down()).isEmpty()||!w.getBlockState(feet.down()).isSideSolidFullSquare(w,feet.down(),Direction.UP)
                        ||List.of(feet,feet.up()).stream().anyMatch(p->state.unfinished.containsKey(p)&&!state.expected.getOrDefault(p,Blocks.AIR.getDefaultState()).getCollisionShape(w,p).isEmpty())
                        ||!outsidePlacement(mob,block,goal)||!w.isSpaceEmpty(mob,box)||!w.getOtherEntities(mob,box,e->e instanceof LivingEntity&&e.isAlive()).isEmpty())continue;
                mob.getNavigation().stop();mob.requestTeleport(goal.x,goal.y,goal.z);mob.setVelocity(Vec3d.ZERO);mob.fallDistance=0;
                return true;
            }
        }
        return false;
    }
    static String clearanceDebug(ServerWorld w,VillagerEntity v){
        var states=new ArrayList<String>();for(var p:BlockPos.iterate(v.getBlockPos().add(-1,-1,-1),v.getBlockPos().add(1,2,1)))if(!w.getBlockState(p).isAir())states.add(p+"="+w.getBlockState(p));
        return v.getEntityPos()+" entityTick="+w.shouldTickEntityAt(v.getBlockPos())+" age="+v.age+" ai="+v.isAiDisabled()+" sneak="+v.isSneaking()+" ground="+v.isOnGround()+" nav="+v.getNavigation().isIdle()+" clear="+clearing(v)+" escape="+ESCAPES.get(v)+" age="+CLEAR_MOTION.get(v)+" states="+states;
    }
    static boolean beginEscape(ServerWorld w,VillagerEntity v,BlockPos block){
        if(ESCAPES.containsKey(v))return true;
        Vec3d best=null;double score=Double.MAX_VALUE;
        for(BlockPos q:BlockPos.iterateOutwards(v.getBlockPos(),4,3,4)){
            if(!w.isChunkLoaded(new ChunkPos(q).toLong())||!w.getFluidState(q).isEmpty()||!w.getFluidState(q.down()).isEmpty())continue;
            var shape=w.getBlockState(q).getCollisionShape(w,q);var below=w.getBlockState(q.down()).getCollisionShape(w,q.down());
            if(shape.isEmpty()&&below.isEmpty())continue;
            double height=shape.isEmpty()?-1+below.getMax(Direction.Axis.Y):shape.getMax(Direction.Axis.Y);
            var goal=Vec3d.ofBottomCenter(q).add(0,height,0);double dx=goal.x-v.getX(),dz=goal.z-v.getZ(),horizontal=Math.hypot(dx,dz);
            if(horizontal<.8||goal.y<v.getY()-3||goal.y>v.getY()+1||!outsidePlacement(v,block,goal)||!w.isSpaceEmpty(v,v.getBoundingBox().offset(goal.subtract(v.getEntityPos()))))continue;
            if(!w.getOtherEntities(v,v.getBoundingBox().offset(goal.subtract(v.getEntityPos())),e->e instanceof LivingEntity&&e.isAlive()).isEmpty())continue;
            if(ESCAPES.entrySet().stream().anyMatch(e->e.getKey()!=v&&e.getKey().getEntityWorld()==w&&e.getValue().goal().squaredDistanceTo(goal)<.8))continue;
            double stepUp=Math.max(0,goal.y-v.getY());
            if(!w.isSpaceEmpty(v,v.getBoundingBox().offset(dx/horizontal*.6,stepUp,dz/horizontal*.6)))continue;
            if(stepUp>.2&&!w.isSpaceEmpty(v,v.getBoundingBox().offset(0,stepUp,0)))continue;
            double cost=horizontal+Math.abs(goal.y-v.getY())*.5;if(cost<score){score=cost;best=goal;}
        }
        if(best==null)return false;ESCAPES.put(v,new Escape(block,best,w.getTime()+120));CLEARING.put(v,w.getTime()+160);WORKERS.put(v,w.getTime()+160);ASSIGNED.remove(v);return true;
    }
    /** A builder trapped by completed walls opens a recorded passage and schedules its restoration. */
    static boolean openConstructionExit(ServerWorld w,Project project,net.minecraft.entity.mob.MobEntity mob){
        var state=get(w);BlockPos feet=mob.getBlockPos();
        for(var direction:Direction.Type.HORIZONTAL){
            BlockPos doorway=feet.offset(direction),outside=feet.offset(direction,2);
            if(!w.isChunkLoaded(new ChunkPos(outside).toLong())||w.getBlockState(outside.down()).getCollisionShape(w,outside.down()).isEmpty())continue;
            Vec3d goal=Vec3d.ofBottomCenter(outside);
            if(!w.isSpaceEmpty(mob,mob.getBoundingBox().offset(goal.subtract(mob.getEntityPos()))))continue;
            var opening=new ArrayList<VillageCivilWorks.Change>();boolean safe=true;
            for(var pos:List.of(doorway,doorway.up())){
                var actual=w.getBlockState(pos);if(actual.getCollisionShape(w,pos).isEmpty())continue;
                boolean owned=constructionOwned(w,project,pos,actual);
                if(!owned||actual.hasBlockEntity()||!w.getFluidState(pos).isEmpty()){safe=false;break;}
                opening.add(new VillageCivilWorks.Change(pos,Blocks.AIR.getDefaultState(),state.expected.getOrDefault(pos,actual)));
            }
            if(!safe||opening.isEmpty())continue;
            for(var change:opening){w.setBlockState(change.pos(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);project.plan.add(change);state.unfinished.merge(change.pos(),1,Integer::sum);}
            state.markDirty();return true;
        }
        // A basement stair can be capped by the next floor. Open headroom for one real upward step.
        for(var direction:Direction.Type.HORIZONTAL){
            BlockPos step=feet.offset(direction).up();
            if(!w.isChunkLoaded(new ChunkPos(step).toLong())||w.getBlockState(step.down()).getCollisionShape(w,step.down()).isEmpty()||!w.getFluidState(step.down()).isEmpty())continue;
            var opening=new ArrayList<VillageCivilWorks.Change>();boolean safe=true;
            for(var pos:List.of(feet.up(2),step,step.up())){
                var actual=w.getBlockState(pos);if(actual.getCollisionShape(w,pos).isEmpty())continue;
                if(actual.hasBlockEntity()||!w.getFluidState(pos).isEmpty()||!constructionOwned(w,project,pos,actual)){safe=false;break;}
                opening.add(new VillageCivilWorks.Change(pos,Blocks.AIR.getDefaultState(),state.expected.getOrDefault(pos,actual)));
            }
            if(!safe||opening.isEmpty())continue;
            for(var change:opening){w.setBlockState(change.pos(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);project.plan.add(change);state.unfinished.merge(change.pos(),1,Integer::sum);}
            state.markDirty();return true;
        }
        return false;
    }
    private static boolean constructionOwned(ServerWorld w,Project project,BlockPos pos,BlockState actual){
        if(actual.getBlock() instanceof SnowBlock)return true;
        var expected=get(w).expected.get(pos);if(expected!=null&&expected.isOf(actual.getBlock()))return true;
        if(project.plan.stream().anyMatch(c->c.pos().equals(pos)&&c.after().isOf(actual.getBlock())))return true;
        return w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses.stream().anyMatch(h->pos.getX()>=h.min.getX()&&pos.getX()<=h.max.getX()&&pos.getY()>=h.min.getY()&&pos.getY()<=h.max.getY()&&pos.getZ()>=h.min.getZ()&&pos.getZ()<=h.max.getZ()&&h.blueprint.stream().anyMatch(r->r.pos().equals(pos)&&r.state().isOf(actual.getBlock())));
    }
    static BlockPos workPosition(ServerWorld w,Project job,VillageCivilWorks.Change change){
        if(job.kind.equals("shipyard")){var pile=NavalWorks.pileWork(w,job,change);if(pile!=null)return pile;}
        boolean foundation=(Set.of("ground_safety","terrace_support","terrace_stairs","relocation_copy","house","treasury","plantation","factory","shipyard","bridge").contains(job.kind)||job.kind.startsWith("workshop_"))&&!change.after().isAir()&&(change.before().isAir()||change.before().getBlock() instanceof FluidBlock);
        if(!foundation)return change.pos();
        int surface=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,change.pos().getX(),change.pos().getZ());
        if(surface-change.pos().getY()>3&&surface-change.pos().getY()<=(job.kind.startsWith("terrace_")?80:24))return change.pos().withY(surface);
        return change.pos();
    }
    /** Catch up a remote city's elapsed simulated construction using the same plans and completion bookkeeping. */
    static void materialize(ServerWorld w,long village,int budget){materialize(w,village,budget,false);}
    static void materialize(ServerWorld w,long village,int budget,boolean remoteRail){long deadline=System.nanoTime()+750_000;var state=get(w);var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(var p:List.copyOf(state.jobs))if(p.village==village&&!p.done&&(!p.kind.equals("railway")||!remoteRail&&VillageFastEras.active(w,village))
                &&(!remoteRail||p.kind.equals("rail_station")||p.kind.equals("rail_foundry"))){
            while(budget-->0&&p.index<p.plan.size()&&System.nanoTime()<deadline){
                var c=p.plan.get(p.index);if(!w.isChunkLoaded(new ChunkPos(c.pos()).toLong())){if(remoteRail||VillageFastEras.active(w,village))keepLoaded(w,c.pos());break;}var actual=w.getBlockState(c.pos());var latest=state.expected.get(c.pos());
                if(preservePrison(w,state,p,c))continue;
                if(VillageConnections.matches(w,c.pos(),c.after(),actual)||latest!=null&&!latest.equals(c.after())){p.index++;state.markDirty();continue;}
                if(!compatibleAt(w,c,actual)||actual.hasBlockEntity()&&!mayRemoveInventory(w,c.pos()))break;
                var occupants=placementOccupants(w,c.pos(),c.after());if(!occupants.isEmpty()){if(remoteRail||VillageFastEras.active(w,village))clearWorkArea(w,c.pos(),occupants,p);if(remoteRail&&Boolean.getBoolean("halloween.eraSoak")&&w.getTime()%1000==0)org.slf4j.LoggerFactory.getLogger("era-soak").info("Remote generation clearing {} at {}",occupants.stream().map(e->e.getType().toString()).toList(),c.pos());break;}
                if(!w.setBlockState(c.pos(),VillageConnections.connected(w,c.pos(),c.after()),Block.NOTIFY_ALL))break;
                p.index++;state.markDirty();
            }
            if(p.index>=p.plan.size())finish(w,state,memory,p);if(budget<=0)return;
        }
    }
    private static void finish(ServerWorld w,VillageProjects state,VillageMemory memory,Project p){
        if(p.done)return;boolean alreadyRecorded=memory.houses.stream().anyMatch(h->h.recordKey.equals(p.id));p.done=true;VillageBuilders.finishProjectAccess(p.id);state.markDirty();VillageWorkplaceIndex.invalidate(w);
        ASSIGNED.entrySet().removeIf(e->e.getValue().project().equals(p.id));
        for(var c:p.plan)state.unfinished.computeIfPresent(c.pos(),(pos,n)->n<=1?null:n-1);
        Map<BlockPos,BlockState> completed=new HashMap<>();for(var c:p.plan)if(c.after().equals(state.expected.get(c.pos())))completed.put(c.pos(),c.after());
        for(var house:memory.houses)for(int i=0;i<house.blueprint.size();i++){
            var old=house.blueprint.get(i);var wanted=completed.get(old.pos());if(wanted!=null)house.blueprint.set(i,new VillageMemory.BlockRecord(old.pos(),wanted));
        }
        if(memory.houses.stream().noneMatch(h->h.recordKey.equals(p.id))){
            var records=p.plan.stream().map(c->new VillageMemory.BlockRecord(c.pos(),c.after())).toList();
            memory.houses.add(new VillageMemory.House(p.min,p.max,records,w.getTime(),List.of(),0,true,List.of(),List.of(),List.of(),p.kind.equals("house")?"house":"era",p.id,List.of()));
        }
        if(p.kind.equals("house"))for(var team:memory.teams)if(team.village==p.village&&!team.finishedExpansions.contains(p.min)){
            team.expansions.add(p.min);team.finishedExpansions.add(p.min);
        }
        if(p.kind.equals("house")&&!alreadyRecorded)VillageEconomy.populateHouse(w,p);
        VillageIndustrial.finishProject(w,p);
        memory.markDirty();
    }
    static void approach(ServerWorld w,VillagerEntity v,BlockPos target){
        if(v.isTouchingWater()){VillageWaterRescue.swimToShore(w,v);return;}
        VillageTasks.hold(v);var previous=MOTION.get(v.getUuid());Vec3d now=v.getEntityPos();
        if(previous==null||now.squaredDistanceTo(previous.pos())>.5)MOTION.put(v.getUuid(),new Motion(now,w.getTime()));
        boolean stalled=previous!=null&&w.getTime()-previous.since()>200;
        if(!stalled&&NEXT_PATH.getOrDefault(v,0L)>w.getTime())return;
        NEXT_PATH.put(v,w.getTime()+40);
        int attempts=0;
        for(BlockPos q:BlockPos.iterateOutwards(target,5,5,5)){
            // Reach the block from beside it, never choose the block being built as the worker's standing node.
            if(q.equals(target)||q.up().equals(target))continue;
            // Navigate through current free space. Future walls/foundations are not present obstacles.
            // Excluding every future solid block makes entire relocation footprints unreachable.
            if(!w.isChunkLoaded(new ChunkPos(q).toLong())||!w.getWorldBorder().contains(q)||!w.getBlockState(q).getCollisionShape(w,q).isEmpty()&&!(w.getBlockState(q).getBlock() instanceof SnowBlock)||!w.getBlockState(q.up()).getCollisionShape(w,q.up()).isEmpty()||!w.getFluidState(q).isEmpty()||w.getBlockState(q.down()).getCollisionShape(w,q.down()).isEmpty())continue;
            if(!w.getOtherEntities(v,new Box(q).stretch(0,1,0),e->e instanceof LivingEntity&&e.isAlive()).isEmpty())continue;
            var floor=w.getBlockState(q).getCollisionShape(w,q);double standingY=q.getY()+(floor.isEmpty()?0:floor.getMax(Direction.Axis.Y));
            if(!w.isSpaceEmpty(v,v.getBoundingBox().offset(q.getX()+.5-v.getX(),standingY-v.getY(),q.getZ()+.5-v.getZ())))continue;
            // Recovery only for a genuinely stranded worker, in a checked empty standing space.
            if(stalled&&v.isOnGround()){v.stopRiding();v.setVelocity(Vec3d.ZERO);v.fallDistance=0;v.requestTeleport(q.getX()+.5,standingY,q.getZ()+.5);MOTION.remove(v.getUuid());return;}
            if(++attempts>4||!VillagePathBudget.take(w))return;
            var path=v.getNavigation().findPathTo(q,0);
            if(path!=null&&path.reachesTarget()) {v.getNavigation().startMovingAlong(path,.65);return;}
        }
    }
}
