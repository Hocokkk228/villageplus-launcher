package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

/** Directly manages the king and his dedicated guards, independent of house snapshot discovery. */
final class RoyalCourt {
    private record Motion(Vec3d position,long lastMoved){}
    private static final Map<UUID,Motion> MOTION=new HashMap<>();
    private static final Map<UUID,Long> TRAPPED_GUARDS=new HashMap<>();
    static void register(){
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((dead,source)->{
            if(!(dead instanceof VillagerEntity v)||!v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)||!(v.getEntityWorld() instanceof ServerWorld w))return;
            String identity="halloween_deputy_id_"+v.getUuidAsString();
            for(var marker:w.getEntitiesByClass(MarkerEntity.class,new Box(v.getBlockPos()).expand(384),m->m.getCommandTags().contains(identity)))marker.removeCommandTag(identity);
        });
        ServerTickEvents.END_WORLD_TICK.register(RoyalCourt::tick);
    }
    private static void tick(ServerWorld w){
        if(w.getTime()%20!=0||w.getPlayers().isEmpty())return;
        rescueDungeonGuards(w);
        if(w.getTime()%200==0){rememberCeremonialLecterns(w);rememberTribunes(w);}
        Set<UUID> seen=new HashSet<>();
        for(var player:w.getPlayers()){
            var kings=w.getEntitiesByClass(VillagerEntity.class,new Box(player.getBlockPos()).expand(128),v->v.isAlive()&&RoyalProfession.isKing(v));
            for(var king:kings)if(seen.add(king.getUuid()))manageKing(w,king);
        }
    }
    /** Recover the original square lectern from saved castle plans, even if it is already broken. */
    static void rememberCeremonialLecterns(ServerWorld w){
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        Set<UUID> seen=new HashSet<>();
        for(var player:w.getPlayers())for(var square:w.getEntitiesByClass(MarkerEntity.class,new Box(player.getBlockPos()).expand(128),m->m.getCommandTags().contains("halloween_square"))){
            if(!seen.add(square.getUuid()))continue;
            String key="ceremonial_lectern_repair:"+square.getUuidAsString();
            if(memory.houses.stream().anyMatch(h->h.recordKey.equals(key)))continue;
            var record=memory.houses.stream().filter(h->h.recordKey.contains("/royal_castle:")).flatMap(h->h.blueprint.stream())
                .filter(r->r.pos().isWithinDistance(square.getBlockPos(),32))
                .filter(r->r.state().isOf(RoyalProfession.CEREMONIAL_LECTERN)||r.state().isOf(net.minecraft.block.Blocks.LECTERN))
                .min(Comparator.comparingDouble(r->r.pos().getSquaredDistance(square.getBlockPos()))).orElse(null);
            if(record==null)continue;
            var state=record.state().isOf(RoyalProfession.CEREMONIAL_LECTERN)?record.state():RoyalProfession.CEREMONIAL_LECTERN.getDefaultState().with(CeremonialLecternBlock.FACING,record.state().get(net.minecraft.block.LecternBlock.FACING));
            var pos=record.pos().toImmutable();
            memory.houses.add(new VillageMemory.House(pos,pos,List.of(new VillageMemory.BlockRecord(pos,state)),w.getTime(),List.of(),0,true,List.of(),List.of(),List.of(),"infrastructure",key,List.of()));
            VillageProjects.recordInfrastructure(w,key,Map.of(pos,state));memory.markDirty();
        }
    }
    /** Preserve the original seating as repairable infrastructure, even in older saves. */
    static void rememberTribunes(ServerWorld w){
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);Set<UUID> seen=new HashSet<>();
        for(var player:w.getPlayers())for(var square:w.getEntitiesByClass(MarkerEntity.class,new Box(player.getBlockPos()).expand(128),m->m.getCommandTags().contains("halloween_square"))){
            if(!seen.add(square.getUuid()))continue;String key="tribune_repair_v1:"+square.getUuidAsString();
            if(memory.houses.stream().anyMatch(h->h.recordKey.equals(key)))continue;
            var seats=w.getEntitiesByClass(MarkerEntity.class,new Box(square.getBlockPos()).expand(64),m->m.getCommandTags().contains("halloween_tribune"));if(seats.isEmpty())continue;
            Map<BlockPos,VillageMemory.BlockRecord> records=new LinkedHashMap<>();
            for(var h:List.copyOf(memory.houses))if(h.recordKey.contains("/royal_castle:"))for(var r:h.blueprint){
                var block=r.state().getBlock();if(!(block instanceof net.minecraft.block.StairsBlock||block instanceof net.minecraft.block.SlabBlock||r.state().isIn(net.minecraft.registry.tag.BlockTags.PLANKS)))continue;
                if(seats.stream().anyMatch(marker->r.pos().isWithinDistance(marker.getBlockPos(),7)))records.putIfAbsent(r.pos(),r);
            }
            if(records.isEmpty())continue;
            int x0=records.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow(),y0=records.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow(),z0=records.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
            int x1=records.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow(),y1=records.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow(),z1=records.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow();
            memory.houses.add(new VillageMemory.House(new BlockPos(x0,y0,z0),new BlockPos(x1,y1,z1),new ArrayList<>(records.values()),w.getTime(),List.of(),0,true,List.of(),List.of(),List.of(),"infrastructure",key,List.of()));memory.markDirty();
        }
    }
    private static void rescueDungeonGuards(ServerWorld w){
        Set<UUID> seen=new HashSet<>();
        for(var player:w.getPlayers())for(var jail:w.getEntitiesByClass(MarkerEntity.class,new Box(player.getBlockPos()).expand(128),m->m.getCommandTags().contains("halloween_jail"))){
            BlockPos top=VillageTrials.jailEntrance(w,jail.getBlockPos());if(top==null)continue;
            for(var guard:w.getEntitiesByClass(VillagerEntity.class,new Box(jail.getBlockPos()).expand(9,8,9),v->v.isAlive()&&(v.getCommandTags().contains("halloween_bodyguard")||v.getCommandTags().contains("halloween_castle_guard"))&&!v.getCommandTags().contains("halloween_player_guarding"))){
                if(!seen.add(guard.getUuid())||guard.getY()>=top.getY()-1)continue;
                long since=TRAPPED_GUARDS.computeIfAbsent(guard.getUuid(),k->w.getTime());
                if(guard.getNavigation().isIdle())guard.getNavigation().startMovingTo(top.getX()+.5,top.getY(),top.getZ()+.5,.6);
                if(w.getTime()-since<200)continue;
                for(BlockPos q:BlockPos.iterateOutwards(top,5,2,5)){
                    if(q.getY()<top.getY()-1||!w.getBlockState(q).isAir()||!w.getBlockState(q.up()).isAir()||!w.getBlockState(q.down()).isSolidBlock(w,q.down()))continue;
                    if(!w.getOtherEntities(guard,new Box(q),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty())continue;
                    guard.requestTeleport(q.getX()+.5,q.getY(),q.getZ()+.5);TRAPPED_GUARDS.remove(guard.getUuid());break;
                }
            }
        }
        TRAPPED_GUARDS.keySet().removeIf(id->!seen.contains(id));
    }
    private static void manageKing(ServerWorld w,VillagerEntity king){
        long currentDay=Math.floorMod(w.getTimeOfDay(),24000L);
        boolean president=RoyalProfession.updateOffice(w,king);
        var team=president?VillageEras.team(w,king.getBlockPos()):null;
        BlockPos office=team!=null&&currentDay>=1000&&currentDay<8500?SpasInteriors.office(w,team.village):null;
        if(office!=null&&w.isChunkLoaded(office)&&!VillageInjuries.panicking(king)&&!VillageTrials.busy(king)){
            if(king.squaredDistanceTo(Vec3d.ofBottomCenter(office))>2.25)walk(king,office,.55f);
            else king.getLookControl().lookAt(office.getX()-2,office.getY()+1,office.getZ()+.5);
        }
        if((currentDay<12542||currentDay>=23460)&&king.isSleeping())king.wakeUp();
        var beds=w.getEntitiesByClass(MarkerEntity.class,new Box(king.getBlockPos()).expand(128),m->m.getCommandTags().contains("halloween_royal_bed"));
        MarkerEntity bed=beds.stream().min(Comparator.comparingDouble(king::squaredDistanceTo)).orElse(null);
        if(bed==null&&w.getTime()%200==0)bed=discoverOldRoyalBed(w,king);
        if(bed!=null&&w.getBlockState(bed.getBlockPos()).getBlock() instanceof BedBlock){
            BlockPos pos=bed.getBlockPos();
            rememberRoyalBed(w,pos);
            VillageCivilWorks.royalStairs(w,bed);
            king.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.HOME,GlobalPos.create(w.getRegistryKey(),pos));
            for(var other:w.getEntitiesByClass(VillagerEntity.class,new Box(pos).expand(3),v->v!=king&&v.isSleeping())){
                if(other.getSleepingPosition().filter(p->p.isWithinDistance(pos,2)).isPresent())other.wakeUp();
            }
            long day=w.getTimeOfDay()%24000;
            if(day>=12542&&day<23460&&!king.isSleeping()){
                if(Math.abs(king.getY()-pos.getY())<1.5&&king.squaredDistanceTo(Vec3d.ofCenter(pos))<6.25)king.sleep(pos);
                else{
                    BlockPos goal=pos;
                    if(king.getY()<pos.getY()-1){
                        BlockPos bottom=VillageCivilWorks.royalStairFoot(w,pos);var facing=w.getBlockState(pos).get(BedBlock.FACING);
                        double dx=king.getX()-bottom.getX(),dz=king.getZ()-bottom.getZ();
                        goal=king.getY()<bottom.getY()+1.5&&dx*dx+dz*dz>9?bottom:bottom.offset(facing,7).up(7);
                    }
                    walk(king,goal,.55f);
                }
            }else if(office==null && day<12000 && king.squaredDistanceTo(Vec3d.ofCenter(pos))>28*28)walk(king,pos,.5f);
        }
        String owner="halloween_king_"+king.getUuidAsString();
        var guards=w.getEntitiesByClass(VillagerEntity.class,new Box(king.getBlockPos()).expand(96),g->g.isAlive()&&g.getCommandTags().contains("halloween_bodyguard")&&g.getCommandTags().contains(owner));
        if(guards.size()>=5)king.addCommandTag("halloween_bodyguards_initialized");
        if(guards.size()<5&&!king.getCommandTags().contains("halloween_bodyguards_initialized"))spawnGuard(w,king.getBlockPos(),owner,"halloween_bodyguard");
        var royal=w.getEntitiesByClass(VillagerEntity.class,new Box(king.getBlockPos()).expand(128),v->v.isAlive()&&v.getCommandTags().contains(owner)&&(v.getCommandTags().contains("halloween_bodyguard")||v.getCommandTags().contains("halloween_castle_guard")));
        royal.sort(Comparator.comparing(VillagerEntity::getUuid));
        for(int i=5;i<royal.size();i++){var v=royal.get(i);if(v.getCommandTags().contains("halloween_player_guarding")||v.getCommandTags().contains("halloween_guard_escorting"))continue;
            for(String tag:List.copyOf(v.getCommandTags()))if(tag.equals("halloween_guard")||tag.equals("halloween_bodyguard")||tag.equals("halloween_castle_guard")||tag.startsWith("halloween_post_")||tag.startsWith("halloween_king_"))v.removeCommandTag(tag);
            v.stopRiding();v.setCustomName(null);v.setVillagerData(v.getVillagerData().withProfession(net.minecraft.registry.Registries.VILLAGER_PROFESSION.getEntry(net.minecraft.util.Identifier.of("minecraft","none")).orElseThrow()));v.reinitializeBrain(w);
            for(var slot:List.of(net.minecraft.entity.EquipmentSlot.MAINHAND,net.minecraft.entity.EquipmentSlot.HEAD,net.minecraft.entity.EquipmentSlot.CHEST,net.minecraft.entity.EquipmentSlot.LEGS,net.minecraft.entity.EquipmentSlot.FEET))v.equipStack(slot,ItemStack.EMPTY);
        }
        guards.removeIf(v->!v.getCommandTags().contains("halloween_bodyguard"));
        guards.sort(Comparator.comparing(VillagerEntity::getUuid));
        Motion previous=MOTION.get(king.getUuid());long lastMoved=previous==null?w.getTime():previous.lastMoved();
        if(previous!=null&&new Vec3d(king.getX(),king.getY(),king.getZ()).squaredDistanceTo(previous.position())>.04)lastMoved=w.getTime();
        MOTION.put(king.getUuid(),new Motion(new Vec3d(king.getX(),king.getY(),king.getZ()),lastMoved));boolean moving=w.getTime()-lastMoved<40;
        int slot=0;
        for(var guard:guards){
            int index=slot++;if(VillageDefense.escorting(guard))continue;
            if(guard.isSleeping())guard.wakeUp();
            if(VillageDefense.fight(w,guard,king.getBlockPos(),14))continue;
            if(!guard.getVillagerData().profession().matchesKey(GuardProfession.ROYAL_KEY)){
                guard.setVillagerData(guard.getVillagerData().withProfession(GuardProfession.ROYAL_ENTRY));guard.setExperience(1);guard.reinitializeBrain(w);
            }
            UUID attacker=VillageJustice.royalThreat(king.getUuid());
            net.minecraft.entity.Entity raw=attacker==null?null:w.getEntity(attacker);
            net.minecraft.entity.LivingEntity danger=raw instanceof net.minecraft.entity.LivingEntity living&&living.isAlive()&&living.squaredDistanceTo(king)<=9&&guard.canSee(living)?living:
                w.getEntitiesByClass(net.minecraft.entity.mob.HostileEntity.class,new Box(king.getBlockPos()).expand(3),e->e.squaredDistanceTo(king)<=9&&e.isAlive()&&e.canTarget(king)&&VillageDefense.surface(w,e)&&king.canSee(e))
                .stream().min(Comparator.comparingDouble(king::squaredDistanceTo)).orElse(null);
            
            if(danger!=null){VillageDefense.combat(guard);if(guard.getNavigation().isIdle()||guard.age%40==0)guard.getNavigation().startMovingTo(danger,.8);if(guard.squaredDistanceTo(danger)<4){guard.swingHand(Hand.MAIN_HAND);danger.damage(w,w.getDamageSources().mobAttack(guard),VillageEconomy.attackDamage(guard));}}
            
            else{
                Direction front=Direction.fromHorizontalDegrees(king.getYaw()),right=front.rotateYClockwise();
                BlockPos slotPos=king.getBlockPos().offset(front,index==4?-3:index<2?2:-2).offset(right,index==4?0:index%2==0?2:-2);
                BlockPos destination=null;
                for(int dy=1;dy>=-2;dy--){BlockPos p=slotPos.up(dy);if(w.getBlockState(p).isAir()&&w.getBlockState(p.up()).isAir()&&w.getBlockState(p.down()).isSolidBlock(w,p.down())){destination=p;break;}}
                if(destination!=null&&guard.squaredDistanceTo(Vec3d.ofCenter(destination))>4)walk(guard,destination,.65f);else hold(guard);
            }
        }
        var squares=w.getEntitiesByClass(MarkerEntity.class,new Box(king.getBlockPos()).expand(128),m->m.getCommandTags().contains("halloween_square"));
        var square=squares.stream().min(Comparator.comparingDouble(king::squaredDistanceTo)).orElse(null);
        if(square!=null){ensureDeputy(w,square);if(w.getTime()%200==0){replaceOldLectern(w,square);ensureLegacyTribunes(w,square);}}

        if(bed==null)return;
        List<MarkerEntity> posts=w.getEntitiesByClass(MarkerEntity.class,new Box(bed.getBlockPos()).expand(90),m->m.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_gate_")||t.startsWith("halloween_patrol_")));
        for(MarkerEntity post:posts){
            String postTag="halloween_post_"+post.getUuidAsString();
            var sentries=w.getEntitiesByClass(VillagerEntity.class,new Box(post.getBlockPos()).expand(96),v->v.isAlive()&&v.getCommandTags().contains(postTag));
            if(sentries.isEmpty()){
                post.addCommandTag("halloween_sentry_initialized");
                continue;
            }
            post.addCommandTag("halloween_sentry_initialized");
            VillagerEntity guard=sentries.getFirst();if(VillageDefense.escorting(guard))continue;if(guard.isSleeping())guard.wakeUp();
            if(VillageDefense.fight(w,guard,post.getBlockPos(),20))continue;
            BlockPos destination=post.getBlockPos();
            if(post.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_patrol_"))&&w.getTime()/200%2==1){
                var alternate=posts.stream().filter(m->m!=post&&m.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_patrol_"))).findFirst().orElse(null);
                if(alternate!=null)destination=alternate.getBlockPos();
            }
            if(guard.squaredDistanceTo(Vec3d.ofCenter(destination))>4)walk(guard,destination,.5f);else hold(guard);
        }
    }
    private static void hold(VillagerEntity guard){
        guard.getNavigation().stop();guard.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.INTERACTION_TARGET);
        guard.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(guard.getBlockPos(),.4f,0),60);
    }
    private static void walk(VillagerEntity v,BlockPos p,float speed){
        v.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(p,speed,1),60);
        if(v.getNavigation().isIdle()||v.age%40==0)v.getNavigation().startMovingTo(p.getX()+.5,p.getY(),p.getZ()+.5,speed);
    }
    private static MarkerEntity discoverOldRoyalBed(ServerWorld w,VillagerEntity king){
        BlockPos origin=king.getBlockPos(),best=null;double bestDist=Double.MAX_VALUE;
        for(int dx=-20;dx<=20;dx++)for(int dz=-20;dz<=20;dz++)for(int dy=-4;dy<=12;dy++){
            BlockPos p=origin.add(dx,dy,dz);if(!w.isChunkLoaded(new ChunkPos(p).toLong()))continue;
            var state=w.getBlockState(p);
            if(!state.isOf(net.minecraft.block.Blocks.PURPLE_BED)||state.get(BedBlock.PART)!=net.minecraft.block.enums.BedPart.HEAD)continue;
            boolean mansion=!w.getEntitiesByClass(MarkerEntity.class,new Box(p).expand(1),m->m.getCommandTags().contains("halloween_mansion_bed")).isEmpty();
            double dist=p.getSquaredDistance(origin);if(!mansion&&dist<bestDist){best=p;bestDist=dist;}
        }
        if(best==null)return null;
        MarkerEntity marker=EntityType.MARKER.create(w,SpawnReason.STRUCTURE);if(marker==null)return null;
        marker.refreshPositionAndAngles(best.getX()+.5,best.getY(),best.getZ()+.5,0,0);marker.addCommandTag("halloween_royal_bed");
        return w.spawnEntity(marker)?marker:null;
    }
    static void ensureLegacyTribunes(ServerWorld w,MarkerEntity square){
        if(w.getTime()%200!=0)return;
        if(!w.getEntitiesByClass(MarkerEntity.class,new Box(square.getBlockPos()).expand(64),m->m.getCommandTags().contains("halloween_tribune")).isEmpty())return;
        BlockPos center=square.getBlockPos();List<BlockPos> candidates=new ArrayList<>();
        for(int dx=-36;dx<=36;dx++)for(int dz=-30;dz<=30;dz++)for(int dy=0;dy<=7;dy++){
            BlockPos p=center.add(dx,dy,dz);if(!w.isChunkLoaded(new ChunkPos(p).toLong()))continue;
            var state=w.getBlockState(p);
            if(state.isOf(net.minecraft.block.Blocks.SPRUCE_STAIRS)&&w.getBlockState(p.up()).isAir()&&w.getBlockState(p.up(2)).isAir()){
                var facing=state.get(net.minecraft.block.StairsBlock.FACING);
                // Bench rows continue along their long axis; reverse rows facing away from the intended seating direction.
                boolean row=w.getBlockState(p.offset(facing.rotateYClockwise())).isOf(net.minecraft.block.Blocks.SPRUCE_STAIRS)
                    ||w.getBlockState(p.offset(facing.rotateYCounterclockwise())).isOf(net.minecraft.block.Blocks.SPRUCE_STAIRS);
                int dot=facing.getOffsetX()*(center.getX()-p.getX())+facing.getOffsetZ()*(center.getZ()-p.getZ());
                if(row&&dot>0)w.setBlockState(p,state.with(net.minecraft.block.StairsBlock.FACING,facing.getOpposite()),net.minecraft.block.Block.NOTIFY_ALL);
                candidates.add(p.up());
            }
        }
        int stride=Math.max(1,candidates.size()/72);
        for(int i=0;i<candidates.size();i+=stride){BlockPos p=candidates.get(i);MarkerEntity m=EntityType.MARKER.create(w,SpawnReason.STRUCTURE);if(m==null)return;
            m.refreshPositionAndAngles(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);m.addCommandTag("halloween_tribune");w.spawnEntity(m);
        }
    }
    private static void rememberRoyalBed(ServerWorld w,BlockPos head){
        var state=w.getBlockState(head);if(!(state.getBlock() instanceof BedBlock))return;
        BlockPos foot=head.offset(state.get(BedBlock.FACING).getOpposite());var footState=w.getBlockState(foot);
        if(!(footState.getBlock() instanceof BedBlock))return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);String key="royal_bed:"+head.asLong();
        if(memory.houses.stream().anyMatch(h->h.recordKey.equals(key)))return;
        memory.houses.add(new VillageMemory.House(head,head,List.of(new VillageMemory.BlockRecord(head,state),new VillageMemory.BlockRecord(foot,footState)),w.getTime(),List.of(),0,true,List.of(),List.of(),List.of(),"structure",key,List.of()));memory.markDirty();
    }
    private static void repairDungeonLadder(ServerWorld w,VillagerEntity king){
        var jails=w.getEntitiesByClass(MarkerEntity.class,new Box(king.getBlockPos()).expand(96),m->m.getCommandTags().contains("halloween_jail"));
        for(var jail:jails){BlockPos centre=jail.getBlockPos();
            for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++)for(int dy=3;dy<=12;dy++){
                BlockPos p=centre.add(dx,dy,dz);if(!w.getBlockState(p).isAir())continue;
                var below=w.getBlockState(p.down());if(!(below.getBlock() instanceof net.minecraft.block.LadderBlock))continue;
                boolean above=false;for(int h=1;h<=3;h++)if(w.getBlockState(p.up(h)).isOf(below.getBlock())){above=true;break;}
                if(above&&below.canPlaceAt(w,p))w.setBlockState(p,below,net.minecraft.block.Block.NOTIFY_ALL);
            }
        }
    }
    static void replaceOldLectern(ServerWorld w,MarkerEntity square){
        BlockPos centre=square.getBlockPos();
        for(int dx=-12;dx<=12;dx++)for(int dz=-12;dz<=12;dz++)for(int dy=-2;dy<=5;dy++){
            BlockPos p=centre.add(dx,dy,dz);var state=w.getBlockState(p);
            if(state.isOf(net.minecraft.block.Blocks.LECTERN)&&!state.get(net.minecraft.block.LecternBlock.HAS_BOOK)){
                var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
                if(memory.houses.stream().noneMatch(h->h.recordKey.contains("/royal_castle:")&&h.blueprint.stream().anyMatch(r->r.pos().equals(p)&&r.state().isOf(net.minecraft.block.Blocks.LECTERN))))continue;
                var upgraded=RoyalProfession.CEREMONIAL_LECTERN.getDefaultState().with(CeremonialLecternBlock.FACING,state.get(net.minecraft.block.LecternBlock.FACING));
                w.setBlockState(p,upgraded,net.minecraft.block.Block.NOTIFY_ALL);
                VillageProjects.recordInfrastructure(w,"ceremonial_lectern_upgrade:"+p.asLong(),Map.of(p,upgraded));return;
            }
        }
    }
    static void ensureDeputy(ServerWorld w,MarkerEntity square){
        String owner="halloween_square_"+square.getUuidAsString(),idPrefix="halloween_deputy_id_";
        var deputies=w.getEntitiesByClass(VillagerEntity.class,new Box(square.getBlockPos()).expand(96),v->v.isAlive()&&v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)
            &&(v.getCommandTags().contains(owner)||v.getCommandTags().stream().noneMatch(tag->tag.startsWith("halloween_square_"))));
        deputies.sort(Comparator.comparing(VillagerEntity::getUuid));
        String saved=square.getCommandTags().stream().filter(tag->tag.startsWith(idPrefix)).findFirst().orElse(null);
        VillagerEntity keep=null;
        if(saved!=null){String uuid=saved.substring(idPrefix.length());keep=deputies.stream().filter(v->v.getUuidAsString().equals(uuid)).findFirst().orElse(null);}
        else if(!deputies.isEmpty()){keep=deputies.getFirst();square.addCommandTag(idPrefix+keep.getUuidAsString());}
        if(keep!=null)keep.addCommandTag(owner);
        for(var duplicate:deputies)if(duplicate!=keep&&!duplicate.getCommandTags().contains("halloween_court_minister")&&!VillageTrials.busy(duplicate)){
            duplicate.setVillagerData(duplicate.getVillagerData().withProfession(net.minecraft.registry.Registries.VILLAGER_PROFESSION.getEntry(net.minecraft.registry.Registries.VILLAGER_PROFESSION.get(net.minecraft.village.VillagerProfession.NONE))));
            if(duplicate.getEquippedStack(EquipmentSlot.HEAD).isOf(RoyalProfession.DEPUTY_CROWN))duplicate.equipStack(EquipmentSlot.HEAD,ItemStack.EMPTY);
            duplicate.removeCommandTag(owner);duplicate.setAiDisabled(false);duplicate.reinitializeBrain(w);
        }
        // An assigned minister may be in an unloaded chunk; do not create another one.
        if(saved!=null||keep!=null)return;
        for(int i=0;i<15;i++){
            BlockPos p=square.getBlockPos().add(w.random.nextBetween(-5,5),0,w.random.nextBetween(-5,5));
            if(!w.getBlockState(p).isAir()||!w.getBlockState(p.up()).isAir()||!w.getBlockState(p.down()).isSolidBlock(w,p.down()))continue;
            var v=EntityType.VILLAGER.create(w,SpawnReason.STRUCTURE);if(v==null)return;
            v.refreshPositionAndAngles(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);v.initialize(w,w.getLocalDifficulty(p),SpawnReason.STRUCTURE,null);
            v.setVillagerData(v.getVillagerData().withProfession(RoyalProfession.DEPUTY_ENTRY));v.setExperience(1);v.reinitializeBrain(w);v.setPersistent();
            v.addCommandTag(owner);v.equipStack(EquipmentSlot.HEAD,new ItemStack(RoyalProfession.DEPUTY_CROWN));if(w.spawnEntity(v))square.addCommandTag(idPrefix+v.getUuidAsString());return;
        }
    }
    private static void spawnGuard(ServerWorld w,BlockPos origin,String owner,String role){
        for(int i=0;i<24;i++){
            BlockPos p=origin.add(w.random.nextBetween(-4,4),w.random.nextBetween(-1,1),w.random.nextBetween(-4,4));
            if(!w.isChunkLoaded(new ChunkPos(p).toLong())||!w.getBlockState(p).isAir()||!w.getBlockState(p.up()).isAir()||!w.getBlockState(p.down()).isSolidBlock(w,p.down()))continue;
            if(!w.getOtherEntities(null,new Box(p).expand(.6,0,.6),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty())continue;
            boolean dungeon=w.getEntitiesByClass(MarkerEntity.class,new Box(p).expand(10,16,10),m->m.getCommandTags().contains("halloween_jail")&&p.getY()<=m.getBlockY()+8).size()>0;
            if(dungeon)continue;
            VillagerEntity v=EntityType.VILLAGER.create(w,SpawnReason.STRUCTURE);if(v==null)return;
            v.refreshPositionAndAngles(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);v.initialize(w,w.getLocalDifficulty(p),SpawnReason.STRUCTURE,null);
            v.setVillagerData(v.getVillagerData().withProfession(GuardProfession.ROYAL_ENTRY));v.setExperience(1);v.reinitializeBrain(w);v.setPersistent();
            v.addCommandTag("halloween_guard");v.addCommandTag(role);v.addCommandTag(owner);v.setCustomName(Text.translatable("entity.halloween.bodyguard"));
            v.equipStack(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));v.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
            v.equipStack(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));v.equipStack(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
            v.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));w.spawnEntity(v);return;
        }
    }
    private RoyalCourt(){}
}
