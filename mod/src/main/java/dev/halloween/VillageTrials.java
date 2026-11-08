package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.village.VillagerProfession;

/** Court state is stored on the accused so it survives a world reload. */
final class VillageTrials {
    private static final String TRIAL="halloween_trial_", PANIC="halloween_panic_", RELEASE="halloween_early_release";
    static void register(){
        ServerLivingEntityEvents.AFTER_DEATH.register((dead,source)->{
            if(!(dead instanceof VillagerEntity victim)||!(dead.getEntityWorld() instanceof ServerWorld w))return;
            for(var court:w.getEntitiesByClass(MarkerEntity.class,new Box(victim.getBlockPos()).expand(384),m->m.getCommandTags().contains("halloween_square")&&ownsCourt(m,victim)))abandonCourt(w,court,victim);
            if(victim.getLeashHolder() instanceof VillagerEntity escort){escort.removeCommandTag("halloween_guard_escorting");escort.removeCommandTag("halloween_executor");}
            if(source.getAttacker()==null)return;
            var witnesses=w.getEntitiesByClass(VillagerEntity.class,new Box(victim.getBlockPos()).expand(20),v->v!=victim&&v.isAlive()&&v.squaredDistanceTo(victim)<=400&&v.canSee(victim));
            if(witnesses.isEmpty())return;
            for(var v:witnesses){
                if(VillageDefense.isGuard(v)||v.getVillagerData().profession().matchesKey(BuilderProfession.KEY)||v.getCommandTags().contains("halloween_court_minister")||v.getCommandTags().contains("halloween_court_builder"))continue;
                clearPrefix(v,PANIC);v.addCommandTag(PANIC+(w.getTime()+1200));if(v.isSleeping())v.wakeUp();
            }
            if(source.getAttacker() instanceof VillagerEntity criminal && criminal!=victim && !RoyalProfession.isKing(victim)){
                CourtTestimony.observe(w,criminal,victim,victim.getBlockPos(),victim.getTarget()==criminal);
                criminal.addCommandTag("halloween_crime_murder");criminal.addCommandTag("halloween_bandit");criminal.addCommandTag("halloween_wanted");
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(VillageTrials::tick);
        ServerTickEvents.END_WORLD_TICK.register(VillageTrials::closePendingCells);
        ServerTickEvents.END_WORLD_TICK.register(w->{if(!w.getPlayers().isEmpty()&&w.getTime()%100==0)maintainCourt(w);});
    }
    static void maintainCourt(ServerWorld w){
        var squares=new ArrayList<MarkerEntity>();
        for(var entity:w.iterateEntities())if(entity instanceof MarkerEntity m&&m.getCommandTags().contains("halloween_square"))squares.add(m);
        for(var square:squares){
            boolean stale=false;
            for(var tag:List.copyOf(square.getCommandTags()))if(tag.startsWith("halloween_active_court_")){
                Entity accused=null;try{accused=w.getEntity(UUID.fromString(tag.substring("halloween_active_court_".length())));}catch(IllegalArgumentException ignored){}
                boolean live=accused!=null&&accused.isAlive()&&hasEscort(w,accused)&&(accused.getCommandTags().contains("halloween_player_custody")||accused.getCommandTags().contains("halloween_execution_pending")||accused.getCommandTags().stream().anyMatch(t->t.startsWith(TRIAL)));
                if(!live){square.removeCommandTag(tag);stale=true;}
            }
            if(stale){releaseMinister(w,square);removeCage(w,cagePosition(w,square));}
            else if(square.getCommandTags().stream().noneMatch(t->t.startsWith("halloween_active_court_")))releaseMinister(w,square);
        }
        for(var entity:w.iterateEntities())if(entity instanceof VillagerEntity v&&v.getCommandTags().contains("halloween_court_builder")){
            boolean needed=squares.stream().anyMatch(q->q.squaredDistanceTo(v)<128*128&&q.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_active_court_"))&&!cageReady(w,cagePosition(w,q)));
            long lease=tagTime(v,"halloween_court_builder_until_");
            if(!needed||lease>=0&&lease<w.getTime()){v.removeCommandTag("halloween_court_builder");clearPrefix(v,"halloween_court_builder_until_");clearPrefix(v,"halloween_court_builder_site_");VillageProjects.resetWorker(v);}
        }
    }
    private static void freeCageBuilders(ServerWorld w,BlockPos c){
        for(var v:w.getEntitiesByClass(VillagerEntity.class,new Box(c).expand(96),v->v.getCommandTags().contains("halloween_court_builder_site_"+c.asLong()))){v.removeCommandTag("halloween_court_builder");clearPrefix(v,"halloween_court_builder_until_");clearPrefix(v,"halloween_court_builder_site_");VillageProjects.resetWorker(v);}
    }
    static void shelter(ServerWorld world, VillagerEntity villager, int ticks) {
        clearPrefix(villager, PANIC);
        villager.addCommandTag(PANIC + (world.getTime() + ticks));
    }

    static boolean busy(VillagerEntity v){return v.getCommandTags().stream().anyMatch(t->t.startsWith(TRIAL)||t.startsWith(PANIC))||v.getCommandTags().contains(RELEASE);}
    static long nextCourtStart(ServerWorld w){long hour=Math.floorMod(w.getTimeOfDay(),24000);return w.getTime()+500+(hour>=12000?24000-hour+500:0);}
    static boolean ownsCourt(MarkerEntity square,Entity accused){return square.getCommandTags().contains("halloween_active_court_"+accused.getUuidAsString());}
    static void abandonCourt(ServerWorld w,MarkerEntity square,Entity accused){if(ownsCourt(square,accused)){releaseMinister(w,square);removeCage(w,cagePosition(w,square));}}
    private static final Map<MarkerEntity,Map<Entity,Integer>> WAITING=new WeakHashMap<>();
    static BlockPos waitingPost(MarkerEntity square,Entity accused){
        var hall=VillageCourthouse.hall((ServerWorld)square.getEntityWorld(),square);
        if(hall!=null)return standingPost((ServerWorld)square.getEntityWorld(),hall.add(11,1,0));
        var slots=WAITING.computeIfAbsent(square,k->new WeakHashMap<>());
        slots.keySet().removeIf(e->!e.isAlive()||!e.getCommandTags().contains("halloween_player_custody")&&e.getCommandTags().stream().noneMatch(t->t.startsWith(TRIAL)));
        Integer slot=slots.get(accused);
        if(slot==null){slot=0;while(slots.containsValue(slot))slot++;slots.put(accused,slot);}
        return standingPost((ServerWorld)square.getEntityWorld(),square.getBlockPos().add(-18+(slot%6)*4,0,-16+(slot/6)*4));
    }
    static BlockPos standingPost(ServerWorld w,BlockPos desired){
        for(BlockPos p:BlockPos.iterateOutwards(desired,3,1,3))if(w.isChunkLoaded(new ChunkPos(p).toLong())&&w.getBlockState(p).getCollisionShape(w,p).isEmpty()&&w.getBlockState(p.up()).getCollisionShape(w,p.up()).isEmpty()&&w.getFluidState(p).isEmpty()&&!w.getBlockState(p.down()).getCollisionShape(w,p.down()).isEmpty())return p.toImmutable();
        return desired;
    }
    static boolean atCourtLevel(Vec3d position,BlockPos cage){return Math.abs(position.y-cage.getY())<=1.25;}
    static boolean courtArrival(VillagerEntity guard,BlockPos cage){var post=standingPost((ServerWorld)guard.getEntityWorld(),cage.add(2,0,-2));return atCourtLevel(guard.getEntityPos(),cage)&&guard.squaredDistanceTo(Vec3d.ofBottomCenter(post))<=9;}
    static void followEscort(VillagerEntity prisoner,VillagerEntity guard){
        VillageInjuries.calm(prisoner);
        VillageTasks.hold(prisoner);if(prisoner.isSleeping())prisoner.wakeUp();
        if(prisoner.squaredDistanceTo(guard)>2.25)VillageEscort.move(prisoner,guard.getBlockPos(),.75f);
        if(prisoner.squaredDistanceTo(guard)>16)guard.getNavigation().stop();
    }
    private static boolean hasEscort(ServerWorld w,Entity accused){
        if(accused instanceof VillagerEntity v)return v.getLeashHolder() instanceof VillagerEntity guard&&guard.isAlive();
        for(String tag:accused.getCommandTags())if(tag.startsWith("halloween_player_guard_"))try{return w.getEntity(UUID.fromString(tag.substring("halloween_player_guard_".length()))) instanceof VillagerEntity guard&&guard.isAlive();}catch(IllegalArgumentException ignored){}
        return false;
    }
    static boolean claimCourt(ServerWorld w,MarkerEntity square,Entity accused){
        VillageCourthouse.selectHall(w,square);
        String prefix="halloween_active_court_";
        for(String tag:List.copyOf(square.getCommandTags()))if(tag.startsWith(prefix)){
            if(tag.equals(prefix+accused.getUuidAsString()))return true;
            try{var existing=w.getEntity(UUID.fromString(tag.substring(prefix.length())));if(existing!=null&&existing.isAlive()&&hasEscort(w,existing)&&!existing.getCommandTags().contains("halloween_player_sentenced")&&(existing.getCommandTags().stream().anyMatch(t->t.startsWith(TRIAL))||existing.getCommandTags().contains("halloween_execution_pending")||existing.getCommandTags().contains("halloween_player_custody")))return false;}catch(IllegalArgumentException ignored){}
            square.removeCommandTag(tag);
        }
        if(VillageCourthouse.required(w,square)&&VillageCourthouse.hall(w,square)==null)return false;
        square.addCommandTag(prefix+accused.getUuidAsString());return true;
    }
    static void arrested(ServerWorld w,VillagerEntity v){
        if(v.getCommandTags().stream().noneMatch(t->t.startsWith(TRIAL))){v.addCommandTag(TRIAL+nextCourtStart(w));
        }
    }
    static boolean startTest(ServerWorld w,net.minecraft.server.network.ServerPlayerEntity player,String sentence){
        var square=w.getEntitiesByClass(MarkerEntity.class,new Box(player.getBlockPos()).expand(160),m->m.getCommandTags().contains("halloween_square"))
            .stream().min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);if(square==null)return false;
        var accused=w.getEntitiesByClass(VillagerEntity.class,new Box(square.getBlockPos()).expand(128),v->v.isAlive()&&!v.isBaby()&&!v.isAiDisabled()
            &&!VillageDefense.isGuard(v)&&!RoyalProfession.isKing(v)&&!v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)
            &&!v.getVillagerData().profession().matchesKey(BuilderProfession.KEY)&&!v.getCommandTags().contains("halloween_prisoner")&&!v.getCommandTags().contains("halloween_surrender")&&!busy(v))
            .stream().min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
        var guard=w.getEntitiesByClass(VillagerEntity.class,new Box(square.getBlockPos()).expand(160),v->VillageDefense.isGuard(v)&&!v.getCommandTags().contains("halloween_player_guarding")&&!v.getCommandTags().contains("halloween_executor")&&!v.getCommandTags().contains("halloween_guard_escorting")&&!VillageCustody.busy(w,v,null))
            .stream().min(Comparator.<VillagerEntity>comparingInt(v->v.getCommandTags().contains("halloween_castle_guard")||v.getCommandTags().contains("halloween_bodyguard")?1:0).thenComparingDouble(v->v.squaredDistanceTo(accused==null?square:accused))).orElse(null);
        if(accused==null||guard==null)return false;
        if(accused.isSleeping())accused.wakeUp();guard.stopRiding();accused.getNavigation().stop();accused.addCommandTag("halloween_bandit");accused.addCommandTag("halloween_surrender");
        accused.addCommandTag("halloween_test_sentence_"+sentence);accused.attachLeash(guard,true);guard.addCommandTag("halloween_guard_escorting");
        arrested(w,accused);return true;
    }
    private static void executeVillager(ServerWorld w,VillagerEntity accused){
        if(!(accused.getLeashHolder() instanceof VillagerEntity guard)){accused.removeCommandTag("halloween_execution_pending");return;}
        var bell=nearestMarker(w,accused,"halloween_bell",128);if(bell==null)return;
        var square=nearestMarker(w,accused,"halloween_square",384);if(square!=null){guideAttendees(w,square);holdMinister(w,square);}
        if(guard.squaredDistanceTo(bell)>9){guide(guard,bell.getBlockPos(),.65f);return;}
        if(accused.squaredDistanceTo(bell)>16){guide(guard,bell.getBlockPos(),.6f);return;}
        guard.getNavigation().stop();accused.getNavigation().stop();
        var era=VillageEras.settlement(w,guard.getBlockPos());
        if(era!=null&&era.era>=3){
            if(!MusketItem.fireGuard(w,guard,accused,1000))return;
            accused.detachLeashWithoutDrop();guard.removeCommandTag("halloween_executor");guard.removeCommandTag("halloween_guard_escorting");if(square!=null)releaseMinister(w,square);return;
        }
        accused.detachLeashWithoutDrop();
        guard.swingHand(Hand.MAIN_HAND);
        accused.damage(w,w.getDamageSources().mobAttack(guard),1000);guard.removeCommandTag("halloween_executor");guard.removeCommandTag("halloween_guard_escorting");
        if(square!=null)releaseMinister(w,square);
    }
    private static long tagTime(VillagerEntity v,String prefix){for(String t:v.getCommandTags())if(t.startsWith(prefix))try{return Long.parseLong(t.substring(prefix.length()));}catch(NumberFormatException ignored){}return -1;}
    private static void clearPrefix(VillagerEntity v,String prefix){for(String t:List.copyOf(v.getCommandTags()))if(t.startsWith(prefix))v.removeCommandTag(t);}
    private static void tick(ServerWorld w){
        if(w.getTime()%5!=0||w.getPlayers().isEmpty())return;
        Set<UUID> seen=new HashSet<>();
        for(var player:w.getPlayers())for(var v:w.getEntitiesByClass(VillagerEntity.class,new Box(player.getBlockPos()).expand(384),VillagerEntity::isAlive)){
            if(!seen.add(v.getUuid()))continue;
            if(VillageDefense.isGuard(v)||v.getVillagerData().profession().matchesKey(BuilderProfession.KEY))clearPrefix(v,PANIC);
            long panic=tagTime(v,PANIC);
            if(panic>=0){
                if(w.getTime()>=panic)clearPrefix(v,PANIC);
                else {var home=v.getBrain().getOptionalRegisteredMemory(MemoryModuleType.HOME);
                    if(home.isPresent()&&home.get().dimension().equals(w.getRegistryKey()))guide(v,home.get().pos(),.8f);
                    else {var beds=w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(v.getBlockPos()).expand(48),m->m.getCommandTags().contains("halloween_mansion_bed"));
                        if(!beds.isEmpty())guide(v,beds.getFirst().getBlockPos(),.8f);
                        else VillageWorkplaceIndex.get(w).beds.stream().filter(p->p.isWithinDistance(v.getBlockPos(),96)).min(Comparator.comparingDouble(p->p.getSquaredDistance(v.getBlockPos()))).ifPresent(p->guide(v,p,.8f));}
                }
            }
            if(v.getCommandTags().contains("halloween_prisoner")&&tagTime(v,"halloween_release_")>=0&&w.getTime()>=tagTime(v,"halloween_release_"))v.addCommandTag(RELEASE);
            if(v.getCommandTags().contains(RELEASE)){releaseTick(w,v);continue;}
            if(v.getCommandTags().contains("halloween_execution_pending")){executeVillager(w,v);continue;}
            if(VillagePrisons.assigned(v)==null&&v.getCommandTags().contains("halloween_surrender")&&!v.getCommandTags().contains("halloween_prisoner")&&!v.getCommandTags().contains("halloween_court_inside")&&!v.getCommandTags().contains("halloween_court_announced")&&v.getLeashHolder() instanceof VillagerEntity escort&&escort.isAlive())followEscort(v,escort);
            long start=tagTime(v,TRIAL);if(start<0)continue;
            var square=nearestMarker(w,v,"halloween_square",384);if(square==null)continue;
            VillageTasks.hold(v);if(v.isSleeping())v.wakeUp();
            var guard=v.getLeashHolder() instanceof VillagerEntity g&&g.isAlive()?g:null;
            if(guard==null){v.detachLeashWithoutDrop();clearPrefix(v,TRIAL);v.removeCommandTag("halloween_court_announced");v.removeCommandTag("halloween_surrender");v.addCommandTag("halloween_wanted");abandonCourt(w,square,v);continue;}
            if(w.getTime()<start-500||!claimCourt(w,square,v)){guide(guard,waitingPost(square,v),.6f);continue;}
            if(!v.getCommandTags().contains("halloween_court_announced")){v.addCommandTag("halloween_court_announced");assemble(w,square);clearPrefix(v,TRIAL);v.addCommandTag(TRIAL+(w.getTime()+500));start=w.getTime()+500;}
            guideAttendees(w,square);holdMinister(w,square);
            var hall=VillageCourthouse.hall(w,square);
            if(hall!=null)CourtTestimony.guide(w,square,v);
            BlockPos cage=cagePosition(w,square);
            if(!cageReady(w,cage)){guideAttendees(w,square);holdMinister(w,square);buildCage(w,cage);guide(guard,standingPost(w,cage.add(2,0,-2)),.6f);continue;}
            BlockPos guardPost=standingPost(w,cage.add(2,0,-2));
            if(!v.getCommandTags().contains("halloween_court_inside")&&(!atCourtLevel(guard.getEntityPos(),cage)||guard.squaredDistanceTo(Vec3d.ofCenter(cage))>64)){
                guide(guard,guardPost,.6f);
                if(!atCourtLevel(guard.getEntityPos(),cage)||guard.squaredDistanceTo(Vec3d.ofCenter(cage))>32*32&&v.squaredDistanceTo(Vec3d.ofCenter(cage))>32*32)continue;
                long since=tagTime(v,"halloween_cage_approach_");if(since<0){v.addCommandTag("halloween_cage_approach_"+w.getTime());continue;}
                if(w.getTime()-since<100)continue;
                if(tagTime(v,"halloween_cage_arrived_")<0)v.addCommandTag("halloween_cage_arrived_"+(w.getTime()-100));
            }
            clearPrefix(v,"halloween_cage_approach_");
            guard.getNavigation().stop();guard.getBrain().forget(MemoryModuleType.WALK_TARGET);
            if(!v.getCommandTags().contains("halloween_court_inside")){
                if(hall!=null&&!VillageCourthouse.judgePresent(w,hall))continue;
                if(!enterAccused(w,v,cage))continue;
            if(!v.getCommandTags().contains("halloween_trial_bell")){
                v.addCommandTag("halloween_trial_bell");
                if(hall==null)for(var p:w.getPlayers())p.sendMessage(Text.literal("§6Начинается суд на площади!"),false);
                var attendees=w.getEntitiesByClass(VillagerEntity.class,new Box(square.getBlockPos()).expand(64),a->a.isAlive()&&a!=v&&!a.getCommandTags().contains("halloween_guard")&&!busy(a));
                var seats=w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(square.getBlockPos()).expand(64),m->m.getCommandTags().contains("halloween_tribune"));
                guideAttendees(w,square);
            }
                if(hall==null)for(var listener:w.getPlayers())listener.sendMessage(Text.literal("§eМинистр: Суд рассматривает обвинения против "+v.getDisplayName().getString()+". Выслушайте решение."),false);
                continue;
            }
            long remaining=tagTime(v,"halloween_court_until_")-w.getTime();
            boolean testCase=v.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_test_sentence_"));
            if(hall!=null)CourtTestimony.speak(w,square,v,remaining,
                    v.getCommandTags().contains("halloween_crime_murder")?"убийство":"ограбление");
            if(remaining>0){v.getNavigation().stop();continue;}
            if(hall!=null&&!testCase&&CourtTestimony.acquit(v)){
                CourtTestimony.announce(w,hall,"§aСудья: Вина не доказана. Подсудимого освободить.");
                clearPrefix(v,TRIAL);clearPrefix(v,"halloween_crime_");clearPrefix(v,"halloween_court_");
                v.removeCommandTag("halloween_wanted");v.removeCommandTag("halloween_surrender");
                v.detachLeashWithoutDrop();guard.removeCommandTag("halloween_guard_escorting");
                CourtTestimony.reset(v,true);removeCage(w,cage);releaseMinister(w,square);continue;
            }
            CourtTestimony.reset(v,true);
            clearPrefix(v,"halloween_court_until_");
            boolean execution=v.getCommandTags().contains("halloween_test_sentence_execution");
            boolean murder=v.getCommandTags().contains("halloween_crime_murder")||v.getCommandTags().contains("halloween_test_sentence_murder");
            boolean assault=v.getCommandTags().contains("halloween_test_sentence_attack");int days=murder?10:assault?1:5;
            if(execution){
                for(var p:w.getPlayers())p.sendMessage(Text.literal("§eСудья: Лицо «"+v.getDisplayName().getString()+"» приговорено к смертной казни. Вердикт вынесен."),false);
            }else for(var p:w.getPlayers())p.sendMessage(Text.literal((hall==null?"§eМинистр: ":"§eСудья: ")+v.getDisplayName().getString()+" осуждён за "+(murder?"убийство":assault?"нападение":"ограбление")+". Приговор: "+days+" дней тюрьмы."),false);
            clearPrefix(v,TRIAL);clearPrefix(v,"halloween_test_sentence_");v.removeCommandTag("halloween_court_announced");v.removeCommandTag("halloween_court_inside");removeCage(w,cage);if(!execution)releaseMinister(w,square);
            if(execution){v.addCommandTag("halloween_execution_pending");guard.addCommandTag("halloween_executor");}
            else v.addCommandTag("halloween_sentence_"+days);
        }
    }
    static boolean enterAccused(ServerWorld w,VillagerEntity accused,BlockPos cage){
        openCageDoor(w,cage);
        if(accused.squaredDistanceTo(Vec3d.ofBottomCenter(cage))>.5){
            long since=tagTime(accused,"halloween_cage_arrived_");
            if(since<0){since=w.getTime();accused.addCommandTag("halloween_cage_arrived_"+since);}
            if(w.getTime()-since>=100){
                Vec3d destination=Vec3d.ofBottomCenter(cage);var box=accused.getBoundingBox().offset(destination.subtract(accused.getEntityPos()));
                if(w.isSpaceEmpty(accused,box)&&w.getOtherEntities(accused,box,e->e instanceof LivingEntity&&e.isAlive()).isEmpty())accused.requestTeleport(destination.x,destination.y,destination.z);
            }else{courtGuide(w,accused,cage,.75f);return false;}
            if(accused.squaredDistanceTo(Vec3d.ofBottomCenter(cage))>.5)return false;
        }
        clearPrefix(accused,"halloween_cage_arrived_");accused.getNavigation().stop();
        accused.addCommandTag("halloween_court_inside");clearPrefix(accused,"halloween_court_until_");accused.addCommandTag("halloween_court_until_"+(w.getTime()+300));closeCage(w,cage);return true;
    }
    // The generated dungeon uses a vertical ladder that vanilla villager navigation cannot descend.
    static BlockPos jailEntrance(ServerWorld w,BlockPos cell){
        BlockPos best=null;int height=Integer.MIN_VALUE;
        for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=1;y<=16;y++){
            BlockPos q=cell.add(x,y,z);if(w.isChunkLoaded(new ChunkPos(q).toLong())&&w.getBlockState(q).getBlock() instanceof LadderBlock&&q.getY()>height){best=q;height=q.getY();}
        }
        if(best==null)for(BlockPos q:BlockPos.iterate(cell.add(-6,0,-6),cell.add(6,7,6)))
            if(w.isChunkLoaded(new ChunkPos(q).toLong())&&w.getBlockState(q).isOf(Blocks.COBBLESTONE_STAIRS)&&q.getY()>height){best=q.up().toImmutable();height=q.getY();}
        return best;
    }
    static void sealCell(ServerWorld w,net.minecraft.entity.Entity prisoner,BlockPos cell){
        if(VillagePrisons.seal(w,prisoner))return;
        prisoner.addCommandTag("halloween_cell_pending_"+cell.asLong());
        closePendingCells(w,true);
    }
    private static void closePendingCells(ServerWorld w){closePendingCells(w,false);}
    private static void closePendingCells(ServerWorld w,boolean immediate){
        if(!immediate&&w.getTime()%10!=0)return;
        if(w.getTime()%200==0)for(var entity:w.iterateEntities()){
            var tags=entity.getCommandTags();if(VillagePrisons.assigned(entity)!=null)continue;
            if(!(tags.contains("halloween_prisoner")||tags.contains("halloween_player_jailed"))||tags.stream().anyMatch(t->t.startsWith("halloween_cell_gate_")||t.startsWith("halloween_cell_pending_")))continue;
            var jail=w.getEntitiesByClass(MarkerEntity.class,new Box(entity.getBlockPos()).expand(8),m->m.getCommandTags().contains("halloween_jail")).stream().min(Comparator.comparingDouble(entity::squaredDistanceTo)).orElse(null);
            if(jail!=null)entity.addCommandTag("halloween_cell_pending_"+jail.getBlockPos().asLong());
        }
        for(var entity:w.iterateEntities())if(entity instanceof VillagerEntity guard)for(String tag:List.copyOf(guard.getCommandTags()))if(tag.startsWith("halloween_cell_exit_")){
            try{BlockPos exit=BlockPos.fromLong(Long.parseLong(tag.substring("halloween_cell_exit_".length())));
                if(guard.squaredDistanceTo(Vec3d.ofCenter(exit))<2.25)guard.removeCommandTag(tag);
                else guide(guard,exit,.7f);
            }catch(NumberFormatException ex){guard.removeCommandTag(tag);}
        }
        for(var prisoner:w.iterateEntities())for(String tag:List.copyOf(prisoner.getCommandTags()))if(tag.startsWith("halloween_cell_pending_")){
            BlockPos cell;try{cell=BlockPos.fromLong(Long.parseLong(tag.substring("halloween_cell_pending_".length())));}catch(NumberFormatException ex){prisoner.removeCommandTag(tag);continue;}
            if(!w.isChunkLoaded(new ChunkPos(cell).toLong()))continue;
            Direction toward=null;int best=-1;
            for(Direction d:Direction.Type.HORIZONTAL){
                BlockPos gap=cell.offset(d);if(!w.getBlockState(gap).isAir())continue;
                int bars=0;for(int i=-3;i<=3;i++)if(w.getBlockState(gap.offset(d.rotateYClockwise(),i)).isOf(Blocks.IRON_BARS))bars++;
                if(bars>best&&bars>=2){best=bars;toward=d;}
            }
            if(toward==null){BlockPos entrance=jailEntrance(w,cell);if(entrance==null)continue;
                toward=Direction.getFacing(entrance.getX()-cell.getX(),0,entrance.getZ()-cell.getZ());if(toward.getAxis()==Direction.Axis.Y)continue;}

            Direction side=toward.rotateYClockwise();BlockPos door=cell.offset(toward);boolean blocked=false;
            for(var guard:w.getEntitiesByClass(VillagerEntity.class,new Box(cell).expand(3,3,3),v->VillageDefense.isGuard(v)&&v.isAlive())){
                double projection=(guard.getX()-(cell.getX()+.5))*toward.getOffsetX()+(guard.getZ()-(cell.getZ()+.5))*toward.getOffsetZ();
                double lateral=Math.abs((guard.getX()-(cell.getX()+.5))*side.getOffsetX()+(guard.getZ()-(cell.getZ()+.5))*side.getOffsetZ());
                if(projection<1.7&&projection> -1.5&&lateral<2.5&&Math.abs(guard.getY()-cell.getY())<2){BlockPos exit=cell.offset(toward,3);guard.addCommandTag("halloween_cell_exit_"+exit.asLong());guide(guard,exit,.7f);blocked=true;}
            }
            if(blocked)continue;
            // Extend existing cell walls up to the ceiling without replacing solid blocks.
            for(int i=-3;i<=3;i++)for(int depth=-1;depth<=1;depth++){
                BlockPos base=cell.offset(side,i).offset(toward,depth);
                if(!w.isChunkLoaded(new ChunkPos(base).toLong())||!w.getBlockState(base).isOf(Blocks.IRON_BARS))continue;
                for(int h=1;h<6;h++){BlockPos q=base.up(h);var old=w.getBlockState(q);
                    if(!old.isAir()&&!old.isOf(Blocks.IRON_BARS))break;
                    if(old.isAir()&&w.getOtherEntities(null,new Box(q),e->e instanceof LivingEntity&&e.isAlive()).isEmpty())w.setBlockState(q,Blocks.IRON_BARS.getDefaultState(),Block.NOTIFY_ALL);
                }
            }
            for(int i=-2;i<=2;i++)for(int h=0;h<6;h++){
                BlockPos q=door.offset(side,i).up(h);if(!w.isChunkLoaded(new ChunkPos(q).toLong())){blocked=true;continue;}
                var old=w.getBlockState(q);if(!old.isAir()&&!old.isOf(Blocks.IRON_BARS))continue;
                if(!w.getOtherEntities(null,new Box(q),e->e instanceof LivingEntity&&e.isAlive()).isEmpty()){blocked=true;continue;}
                if(old.isAir()&&w.setBlockState(q,Blocks.IRON_BARS.getDefaultState(),Block.NOTIFY_ALL))prisoner.addCommandTag("halloween_cell_gate_"+q.asLong());
            }
            if(!blocked)prisoner.removeCommandTag(tag);
        }
    }
    static void openCell(ServerWorld w,net.minecraft.entity.Entity prisoner){
        VillagePrisons.open(w,prisoner);
        for(String tag:List.copyOf(prisoner.getCommandTags())){
            if(tag.startsWith("halloween_cell_pending_")){prisoner.removeCommandTag(tag);continue;}
            if(!tag.startsWith("halloween_cell_gate_"))continue;
            try{BlockPos q=BlockPos.fromLong(Long.parseLong(tag.substring("halloween_cell_gate_".length())));
                if(w.getBlockState(q).isOf(Blocks.IRON_BARS))w.setBlockState(q,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);
            }catch(NumberFormatException ignored){}prisoner.removeCommandTag(tag);
        }
    }
    static void holdMinister(ServerWorld w,net.minecraft.entity.MarkerEntity square){
        RoyalCourt.ensureDeputy(w,square);
        var hall=VillageCourthouse.hall(w,square);
        String owner="halloween_square_"+square.getUuidAsString();
        var search=new Box(square.getBlockPos()).expand(96);
        if(hall!=null)search=search.union(new Box(hall).expand(24));
        var minister=w.getEntitiesByClass(VillagerEntity.class,search,v->v.isAlive()&&v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)
                && (hall==null||v.getCommandTags().contains(owner)))
            .stream().min(Comparator.comparingDouble(v->v.squaredDistanceTo(square))).orElse(null);
        var podium=w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(square.getBlockPos()).expand(32),m->m.getCommandTags().contains("halloween_podium"))
            .stream().min(Comparator.comparingDouble(m->m.squaredDistanceTo(square))).orElse(null);
        if(hall!=null){
            if(minister==null)return;
            if(minister.isSleeping())minister.wakeUp();
            clearPrefix(minister,PANIC);VillageTasks.holdCourt(minister);
            var seat=VillageCourthouse.judge(hall);
            if(minister.squaredDistanceTo(Vec3d.ofBottomCenter(seat))>1.5)courtGuide(w,minister,seat,.6f);
            else {
                minister.getNavigation().stop();minister.getBrain().forget(MemoryModuleType.WALK_TARGET);
                minister.getLookControl().lookAt(seat.getX()+.5,seat.getY(),seat.getZ()-4);
            }
            return;
        }
        if(minister==null||podium==null)return;
        clearPrefix(minister,PANIC);if(minister.isSleeping())minister.wakeUp();
        if(minister.getCommandTags().contains("halloween_court_minister")){minister.getNavigation().stop();return;}
        BlockPos goal=null;
        for(Direction d:Direction.Type.HORIZONTAL){BlockPos p=podium.getBlockPos().offset(d);if(w.getBlockState(p).isAir()&&w.getBlockState(p.up()).isAir()&&w.getBlockState(p.down()).isSolidBlock(w,p.down())){goal=p;break;}}
        if(goal==null)return;
        if(minister.squaredDistanceTo(Vec3d.ofCenter(goal))>1){courtGuide(w,minister,goal,.6f);return;}
        minister.getNavigation().stop();minister.getBrain().forget(MemoryModuleType.WALK_TARGET);minister.getLookControl().lookAt(square.getX(),square.getY(),square.getZ());
        minister.setAiDisabled(true);minister.addCommandTag("halloween_court_minister");
    }
    static void releaseMinister(ServerWorld w,net.minecraft.entity.MarkerEntity square){
        for(String tag:List.copyOf(square.getCommandTags()))if(tag.startsWith("halloween_active_court_"))square.removeCommandTag(tag);
        for(var v:w.getEntitiesByClass(VillagerEntity.class,new Box(square.getBlockPos()).expand(96),a->a.getCommandTags().contains("halloween_court_minister"))){v.removeCommandTag("halloween_court_minister");v.setAiDisabled(false);}
    }
    private static final Map<MarkerEntity,Long> LAST_ASSEMBLY=new WeakHashMap<>();
    private static final Map<MarkerEntity,Long> ASSEMBLY_TICKS=new WeakHashMap<>();
    static void guideAttendees(ServerWorld w,net.minecraft.entity.MarkerEntity square){
        if(VillageCourthouse.hall(w,square)!=null)return;
        if(ASSEMBLY_TICKS.getOrDefault(square,-100L)+20>w.getTime())return;ASSEMBLY_TICKS.put(square,w.getTime());
        if(LAST_ASSEMBLY.getOrDefault(square,-100L)+20>w.getTime())return;
        LAST_ASSEMBLY.put(square,w.getTime());
        var seats=w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(square.getBlockPos()).expand(64),m->m.getCommandTags().contains("halloween_tribune"));
        if(seats.isEmpty())return;seats.sort(Comparator.comparing(net.minecraft.entity.MarkerEntity::getUuid));
        Set<BlockPos> occupied=new HashSet<>();seats.removeIf(s->!occupied.add(s.getBlockPos()));
        var people=w.getEntitiesByClass(VillagerEntity.class,new Box(square.getBlockPos()).expand(160,64,160),v->v.isAlive()&&!v.isBaby()&&!RoyalProfession.isKing(v)&&!v.getVillagerData().profession().matchesKey(BuilderProfession.KEY)&&!VillageDefense.isGuard(v)&&!v.getCommandTags().contains("halloween_court_builder")&&!v.getCommandTags().contains("halloween_surrender")&&!v.getCommandTags().contains("halloween_prisoner")&&!v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY));
        people.sort(Comparator.comparing(VillagerEntity::getUuid));
        for(int i=0;i<people.size();i++){var person=people.get(i);BlockPos spot=i<seats.size()?seats.get(i).getBlockPos():square.getBlockPos().add(-10+(i-seats.size())%10*2,0,8+(i-seats.size())/10*2);VillageTasks.holdCourt(person);clearPrefix(person,PANIC);if(person.isSleeping())person.wakeUp();
            if(person.squaredDistanceTo(Vec3d.ofCenter(spot))>1)courtGuide(w,person,spot,.65f);else{person.getNavigation().stop();person.getBrain().forget(MemoryModuleType.WALK_TARGET);}}
    }
    static void assemble(ServerWorld w,net.minecraft.entity.MarkerEntity square){
        holdMinister(w,square);buildCage(w,cagePosition(w,square));guideAttendees(w,square);
        w.playSound(null,square.getBlockPos(),net.minecraft.sound.SoundEvents.BLOCK_BELL_USE,net.minecraft.sound.SoundCategory.BLOCKS,2,1);
        var seats=w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(square.getBlockPos()).expand(64),m->m.getCommandTags().contains("halloween_tribune"));
        var people=w.getEntitiesByClass(VillagerEntity.class,new Box(square.getBlockPos()).expand(160,64,160),v->v.isAlive()&&!v.isBaby()&&!v.getCommandTags().contains("halloween_guard")&&!busy(v));
        
        var hall=VillageCourthouse.hall(w,square);
        if(hall!=null)CourtTestimony.announce(w,hall,"§6Суд вызывает участников заседания.");
        else for(var p:w.getPlayers())p.sendMessage(Text.literal("§6Сбор жителей на суд."),false);
    }
    private static final Map<ServerWorld,Map<BlockPos,Set<BlockPos>>> CAGES=new WeakHashMap<>();
    private record BuilderProgress(double distance,long changed){}
    private static final Map<UUID,BuilderProgress> COURT_PROGRESS=new HashMap<>();
    private static final String CAGE_CENTRE="halloween_cage_centre_", CAGE_BLOCK="halloween_cage_block_";
    private static MarkerEntity cageOwner(ServerWorld w,BlockPos c){
        return w.getEntitiesByClass(MarkerEntity.class,new Box(c).expand(64),m->m.getCommandTags().contains(CAGE_CENTRE+c.asLong())).stream().findFirst().orElse(null);
    }
    private static void rememberCageBlock(ServerWorld w,BlockPos c,BlockPos p){
        var owner=cageOwner(w,c);if(owner!=null)owner.addCommandTag(CAGE_BLOCK+p.asLong());
        CAGES.computeIfAbsent(w,k->new HashMap<>()).computeIfAbsent(c.toImmutable(),k->new HashSet<>()).add(p.toImmutable());
    }
    static BlockPos cagePosition(ServerWorld w,net.minecraft.entity.MarkerEntity square){
        var hall=VillageCourthouse.hall(w,square);
        if(hall!=null)return VillageCourthouse.cage(hall);
        for(String tag:square.getCommandTags())if(tag.startsWith(CAGE_CENTRE))try{return BlockPos.fromLong(Long.parseLong(tag.substring(CAGE_CENTRE.length())));}catch(NumberFormatException ignored){}
        var bell=w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(square.getBlockPos()).expand(32),m->m.getCommandTags().contains("halloween_bell"))
            .stream().min(Comparator.comparingDouble(square::squaredDistanceTo)).orElse(square);
        for(Direction d:Direction.Type.HORIZONTAL){BlockPos c=bell.getBlockPos().offset(d,4);boolean good=true;
            for(int x=-1;x<=1&&good;x++)for(int z=-1;z<=1&&good;z++)for(int y=0;y<=3;y++){
                BlockPos q=c.add(x,y,z);BlockState state=w.getBlockState(q);
                if(!state.isAir()&&!state.isReplaceable()){good=false;break;}
            }
            if(good&&w.getBlockState(c.down()).isSolidBlock(w,c.down())){square.addCommandTag(CAGE_CENTRE+c.asLong());return c;}
        }
        var fallback=bell.getBlockPos().offset(Direction.EAST,4);square.addCommandTag(CAGE_CENTRE+fallback.asLong());return fallback;
    }
    static boolean cageReady(ServerWorld w,BlockPos c){
        for(int y=0;y<=3;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            if(y<3&&x==0&&z==0||y<2&&x==0&&z==-1)continue;
            if(!w.getBlockState(c.add(x,y,z)).isOf(Blocks.IRON_BARS))return false;
        }return true;
    }
    static void openCageDoor(ServerWorld w,BlockPos c){
        for(int y=0;y<2;y++){BlockPos p=c.add(0,y,-1);if(w.getBlockState(p).isOf(Blocks.IRON_BARS))w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);}
    }
    static void closeCage(ServerWorld w,BlockPos c){
        for(int y=0;y<2;y++){BlockPos p=c.add(0,y,-1);if(w.getBlockState(p).isAir()){
            w.setBlockState(p,Blocks.IRON_BARS.getDefaultState(),Block.NOTIFY_ALL);
            if(!VillageCourthouse.permanent(w,c))rememberCageBlock(w,c,p);
        }}
    }
    static void removeCage(ServerWorld w,BlockPos c){
        if(VillageCourthouse.permanent(w,c)){openCageDoor(w,c);return;}
        for(var b:w.getEntitiesByClass(VillagerEntity.class,new Box(c).expand(48),v->v.getCommandTags().contains("halloween_court_builder")))b.removeCommandTag("halloween_court_builder");
        Set<BlockPos> placed=new HashSet<>();var byPos=CAGES.get(w);if(byPos!=null){var live=byPos.remove(c);if(live!=null)placed.addAll(live);}
        var owner=cageOwner(w,c);
        if(owner!=null){
            for(String tag:List.copyOf(owner.getCommandTags()))if(tag.startsWith(CAGE_BLOCK)){
                try{placed.add(BlockPos.fromLong(Long.parseLong(tag.substring(CAGE_BLOCK.length()))));}catch(NumberFormatException ignored){}
                owner.removeCommandTag(tag);
            }
            owner.removeCommandTag(CAGE_CENTRE+c.asLong());
        }
        for(BlockPos p:placed)if(w.isChunkLoaded(new ChunkPos(p).toLong())&&w.getBlockState(p).isOf(Blocks.IRON_BARS))w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);
    }
    static void buildCage(ServerWorld w,BlockPos c){
        if(cageReady(w,c)){freeCageBuilders(w,c);return;}
        var builders=w.getEntitiesByClass(VillagerEntity.class,new Box(c).expand(160,64,160),v->v.isAlive()&&!v.isAiDisabled()&&!busy(v)&&!v.getCommandTags().contains("halloween_prisoner")&&v.getVillagerData().profession().matchesKey(BuilderProfession.KEY)
            &&(!v.getCommandTags().contains("halloween_court_builder")||v.getCommandTags().contains("halloween_court_builder_site_"+c.asLong())||tagTime(v,"halloween_court_builder_until_")<w.getTime()));
        builders.sort(Comparator.<VillagerEntity>comparingInt(v->v.getCommandTags().contains("halloween_court_builder_site_"+c.asLong())?0:1).thenComparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(c))));
        if(builders.isEmpty())return;
        BlockPos post=null;
        for(BlockPos candidate:List.of(c.add(3,0,0),c.add(-3,0,0),c.add(0,0,3))){var q=standingPost(w,candidate);if(Math.abs(q.getX()-c.getX())<2&&Math.abs(q.getZ()-c.getZ())<2)continue;if(w.isSpaceEmpty(builders.getFirst(),new Box(q).stretch(0,1,0))&&!w.getBlockState(q.down()).getCollisionShape(w,q.down()).isEmpty()){post=q;break;}}
        if(post==null)return;
        for(var worker:builders)if(Math.abs(worker.getX()-c.getX()-.5)<1.9&&Math.abs(worker.getZ()-c.getZ()-.5)<1.9){worker.getNavigation().stop();worker.requestTeleport(post.getX()+.5,post.getY(),post.getZ()+.5);}
        var builder=builders.getFirst();clearPrefix(builder,PANIC);builder.addCommandTag("halloween_court_builder");clearPrefix(builder,"halloween_court_builder_site_");builder.addCommandTag("halloween_court_builder_site_"+c.asLong());clearPrefix(builder,"halloween_court_builder_until_");builder.addCommandTag("halloween_court_builder_until_"+(w.getTime()+120));VillageTasks.hold(builder);if(builder.isSleeping())builder.wakeUp();
        if(builder.squaredDistanceTo(Vec3d.ofCenter(post))>9){VillageProjects.approach(w,builder,post);return;}
        builder.getNavigation().stop();int placed=0;
        for(int y=0;y<=3;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            if(y<3&&x==0&&z==0||x==0&&z==-1&&y<2)continue;BlockPos q=c.add(x,y,z);
            for(var obstacle:w.getEntitiesByClass(VillagerEntity.class,new Box(q),v->v!=builder&&!VillageDefense.escorting(v)&&!busy(v)&&!RoyalProfession.isKing(v)&&!v.getCommandTags().contains("halloween_court_minister")))courtGuide(w,obstacle,post.add(2,0,2),.65f);
            if((w.getBlockState(q).isAir()||w.getBlockState(q).isReplaceable())&&w.getOtherEntities(null,new Box(q),e->e instanceof LivingEntity&&e.isAlive()&&!e.isSpectator()).isEmpty()){
                w.setBlockState(q,Blocks.IRON_BARS.getDefaultState(),Block.NOTIFY_ALL);rememberCageBlock(w,c,q);builder.swingHand(Hand.MAIN_HAND);if(++placed>=4)return;
            }
        }
    }
    private static net.minecraft.entity.MarkerEntity nearestMarker(ServerWorld w,VillagerEntity v,String tag,int range){return w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(v.getBlockPos()).expand(range),m->m.getCommandTags().contains(tag)).stream().min(Comparator.comparingDouble(v::squaredDistanceTo)).orElse(null);}
    private static void courtGuide(ServerWorld w,VillagerEntity v,BlockPos goal,float speed){
        double distance=v.squaredDistanceTo(Vec3d.ofCenter(goal));var progress=COURT_PROGRESS.get(v.getUuid());
        if(progress==null||distance<progress.distance()-.5)COURT_PROGRESS.put(v.getUuid(),new BuilderProgress(distance,w.getTime()));
        else if(w.getTime()-progress.changed()>200&&w.isSpaceEmpty(v,v.getBoundingBox().offset(Vec3d.ofBottomCenter(goal).subtract(v.getEntityPos())))&&w.getBlockState(goal.down()).isSolidBlock(w,goal.down())){v.requestTeleport(goal.getX()+.5,goal.getY(),goal.getZ()+.5);COURT_PROGRESS.remove(v.getUuid());}
        guide(v,goal,speed);
    }
    private static void guide(VillagerEntity v,BlockPos p,float speed){VillageEscort.move(v,p,speed);}
    static boolean requestRelease(ServerWorld w,BlockPos pos){
        var prisoner=w.getEntitiesByClass(VillagerEntity.class,new Box(pos).expand(128),v->v.getCommandTags().contains("halloween_prisoner")&&!v.getCommandTags().contains(RELEASE)).stream().min(Comparator.comparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(pos)))).orElse(null);
        if(prisoner==null)return false;prisoner.addCommandTag(RELEASE);return true;
    }
    private static void releaseTick(ServerWorld w,VillagerEntity prisoner){
        var guard=w.getEntitiesByClass(VillagerEntity.class,new Box(prisoner.getBlockPos()).expand(96),v->v.isAlive()&&v.getCommandTags().contains("halloween_guard")&&!v.hasVehicle()).stream().min(Comparator.comparingDouble(prisoner::squaredDistanceTo)).orElse(null);
        if(guard==null)return;if(guard.squaredDistanceTo(prisoner)>4){guide(guard,prisoner.getBlockPos(),.65f);return;}
        openCell(w,prisoner);prisoner.setAiDisabled(false);prisoner.removeCommandTag(RELEASE);prisoner.removeCommandTag("halloween_prisoner");prisoner.removeCommandTag("halloween_bandit");prisoner.removeCommandTag("halloween_thief");prisoner.removeCommandTag("halloween_wanted");prisoner.removeCommandTag("halloween_surrender");prisoner.removeCommandTag("halloween_escaped");prisoner.removeCommandTag("halloween_crime_murder");prisoner.removeCommandTag("halloween_crime_theft");prisoner.removeCommandTag("halloween_crime_street_assault");clearPrefix(prisoner,"halloween_release_");clearPrefix(prisoner,"halloween_sentence_");
        var choices=List.of(VillagerProfession.FARMER,VillagerProfession.FISHERMAN,VillagerProfession.LIBRARIAN,VillagerProfession.MASON,VillagerProfession.SHEPHERD);
        prisoner.setVillagerData(prisoner.getVillagerData().withProfession(Registries.VILLAGER_PROFESSION.getEntry(Registries.VILLAGER_PROFESSION.get(choices.get(w.random.nextInt(choices.size()))))));prisoner.reinitializeBrain(w);
        for(var p:w.getPlayers())p.sendMessage(Text.literal("§a"+prisoner.getDisplayName().getString()+" досрочно освобождён."),false);
    }
    private VillageTrials(){}
}
