package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.*;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.structure.*;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** First stage of village builders: recorded generated houses, repair, and small roof extensions. */
final class VillageBuilders {
    private static final Logger LOG=LoggerFactory.getLogger("halloween-village");
    private static final int RADIUS=320,MAX_HOUSES=8192;
    private static final Map<UUID,BlockPos> ASSIGNMENTS=new HashMap<>();
    private record Escape(BlockPos pos,long started){}
    private static final Map<UUID,Escape> ESCAPES=new HashMap<>();
    private static final Map<ServerWorld,Map<BlockPos,Long>> TEMP_DIRT=new WeakHashMap<>();
    private static final Map<ServerWorld,List<VillagerEntity>> ACTIVE_WORKERS=new WeakHashMap<>();
    private static final Map<ServerWorld,List<VillagerEntity>> ACTIVE_VILLAGERS=new WeakHashMap<>();
    private static final Map<BlockPos,Long> DEFERRED=new HashMap<>();
    private static final Map<UUID,Long> ATTEMPT_STARTED=new HashMap<>();
    private static final Map<UUID,BlockPos> ATTEMPT_TARGET=new HashMap<>();
    private static final class RepairTower {
        final VillageMemory.House house;final BlockPos base;BlockPos target;boolean finishing;
        RepairTower(VillageMemory.House house,BlockPos base,BlockPos target){this.house=house;this.base=base;this.target=target;}
    }
    private static final Map<UUID,RepairTower> TOWERS=new HashMap<>();
    private static final Map<UUID,Integer> CLIMB_TARGET=new HashMap<>();
    private static final Map<UUID,BlockPos> CLIMB_BASE=new HashMap<>();
    static void register(){
        ServerChunkEvents.CHUNK_LOAD.register(VillageBuilders::captureChunk);
        VillageDamageCapture.register();
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(entity instanceof VillagerEntity v && v.getEntityWorld() instanceof ServerWorld w){
                var m=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
                for(var team:m.teams)if(team.builders.remove(v.getUuidAsString())|team.patrolGuards.remove(v.getUuidAsString())|team.revolt.members.remove(v.getUuidAsString()))m.markDirty();
            }
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.MOB_CONVERSION.register((previous,converted,context)->{
            if(previous instanceof VillagerEntity v && v.getEntityWorld() instanceof ServerWorld w){
                var m=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
                for(var team:m.teams)if(team.builders.remove(v.getUuidAsString())|team.patrolGuards.remove(v.getUuidAsString())|team.revolt.members.remove(v.getUuidAsString()))m.markDirty();
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(VillageBuilders::tick);
    }
    private static void tick(ServerWorld world){
        if(!world.getRegistryKey().equals(World.OVERWORLD)||world.getPlayers().isEmpty())return;
        updateClimbers(world,ACTIVE_VILLAGERS.getOrDefault(world,List.of()));
        // Navigation must not manufacture dirt pillars beneath workers.
        if(world.getTime()%20==0)cleanupTemporaryDirt(world);
        if(world.getTime()%20!=0)return;
        VillageMemory memory=world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        spawnTeams(world,memory);
        List<VillagerEntity> residents=new ArrayList<>();
        for(ServerPlayerEntity p:world.getPlayers())for(VillagerEntity v:world.getEntitiesByClass(VillagerEntity.class,new Box(p.getBlockPos()).expand(RADIUS,96,RADIUS),e->e.isAlive()&&!e.isSleeping()&&!e.isAiDisabled()))if(!residents.contains(v))residents.add(v);
        ACTIVE_VILLAGERS.put(world,residents);
        List<VillagerEntity> workers=new ArrayList<>();
        for(ServerPlayerEntity p:world.getPlayers())
            for(VillagerEntity v:world.getEntitiesByClass(VillagerEntity.class,new Box(p.getBlockPos()).expand(RADIUS,96,RADIUS),
                    e->e.isAlive()&&!e.isBaby()&&!e.isSleeping()&&!e.isAiDisabled()&&!VillageTrials.busy(e)&&!VillageProjects.owns(e)&&!e.getCommandTags().contains("halloween_court_builder")&&e.getVillagerData().profession().matchesKey(BuilderProfession.KEY)))
                if(!workers.contains(v))workers.add(v);
        ACTIVE_WORKERS.put(world,workers);
        for(VillagerEntity worker:workers)
            if(worker.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isEmpty())
                worker.equipStack(net.minecraft.entity.EquipmentSlot.HEAD,new net.minecraft.item.ItemStack(BuilderProfession.HARD_HAT));
        if(!workHours(world)||workers.isEmpty())return;
        restoreJobs(world,memory,workers);
        // Drop stale assignments before distributing repairs, including after a reload.
        for(var worker:workers){BlockPos assigned=ASSIGNMENTS.get(worker.getUuid());if(assigned!=null&&memory.houses.stream().noneMatch(h->h.min.equals(assigned)&&!VillageTerrain.removesHouse(world,h)&&findRepair(world,h)!=null))releaseJob(worker);}
        Set<UUID> active=new HashSet<>();for(VillagerEntity worker:workers)active.add(worker.getUuid());
        ASSIGNMENTS.keySet().removeIf(id->!active.contains(id));
        TOWERS.keySet().removeIf(id->world.getEntity(id)!=null&&!world.getEntity(id).isAlive());
        CLIMB_TARGET.keySet().removeIf(id->!CLIMB_BASE.containsKey(id));
        CLIMB_BASE.keySet().removeIf(id->!CLIMB_TARGET.containsKey(id));
        cleanupAbandonedTowers(world,memory);
        // Each available builder can work once per second on a distinct block.
        List<VillageMemory.House> houses=new ArrayList<>(memory.houses);
        if(houses.isEmpty())return;
        Set<UUID> usedWorkers=new HashSet<>();Set<BlockPos> reserved=new HashSet<>();
        int offset=(int)((world.getTime()/20)%houses.size());
        Collections.rotate(houses,-offset);
        Set<BlockPos> construction=new HashSet<>(); for(var team:memory.teams) for(var pos:team.expansions) if(!team.finishedExpansions.contains(pos)) construction.add(pos);
        // Damage to existing structures takes precedence over unfinished new houses.
        houses.sort(Comparator.<VillageMemory.House>comparingInt(h->construction.contains(h.min)?1:0).thenComparingInt(h->ASSIGNMENTS.containsValue(h.min)?0:1));
        for(int n=0;n<Math.min(12,houses.size());n++){
            VillageMemory.House house=houses.get(n);if(VillageTerrain.removesHouse(world,house))continue;
            for(int i=0;i<5;i++){
                VillagerEntity worker=nearestWorker(workers,house,usedWorkers);
                if(worker==null)break;
                VillageMemory.BlockRecord repair=findRepair(world,house,worker,reserved);
                if(repair==null){
                    // Unsupported or deferred blocks must not reserve an idle builder forever.
                    // The saved blueprint remains queued and becomes eligible again after its supports are repaired.
                    releaseJob(worker);
                    RepairTower tower=TOWERS.get(worker.getUuid());if(tower!=null&&tower.house==house){tower.finishing=true;CLIMB_TARGET.remove(worker.getUuid());}
                    break;
                }
                UUID id=worker.getUuid();BlockPos target=repair.pos();
                if(!target.equals(ATTEMPT_TARGET.get(id))){ATTEMPT_TARGET.put(id,target);ATTEMPT_STARTED.put(id,world.getTime());}
                if(world.getTime()-ATTEMPT_STARTED.getOrDefault(id,world.getTime())>1200){
                    RepairTower tower=TOWERS.get(id);
                    if(tower!=null){tower.finishing=true;CLIMB_TARGET.remove(id);}
                    DEFERRED.put(target,world.getTime()+240);ATTEMPT_TARGET.remove(id);ATTEMPT_STARTED.remove(id);releaseJob(worker);worker.getNavigation().stop();continue;
                }
                assignJob(worker,house.min);reserved.add(target);usedWorkers.add(id);
                if(work(world,memory,house,worker,repair,false))ATTEMPT_TARGET.remove(id);
            }
            // Old automatic roof extensions could build inside existing roofs. Cancel them safely.
            if(!house.work.isEmpty()){
                cleanup(world,house,false);house.work.clear();house.workIndex=0;house.extended=true;memory.markDirty();
            }

        }
    }
    static boolean repairing(VillagerEntity v){return ASSIGNMENTS.containsKey(v.getUuid());}
    static void resetWorker(VillagerEntity v){releaseJob(v);ATTEMPT_TARGET.remove(v.getUuid());ATTEMPT_STARTED.remove(v.getUuid());var tower=TOWERS.get(v.getUuid());if(tower!=null)tower.finishing=true;CLIMB_TARGET.remove(v.getUuid());}
    static boolean workHours(ServerWorld world){return Math.floorMod(world.getTimeOfDay(),24000)<12000;}
    private static final String JOB_TAG="halloween_build_job_";
    private static void assignJob(VillagerEntity worker,BlockPos origin){
        String tag=JOB_TAG+origin.asLong();
        if(!worker.getCommandTags().contains(tag)){
            for(String old:List.copyOf(worker.getCommandTags()))if(old.startsWith(JOB_TAG))worker.removeCommandTag(old);
            worker.addCommandTag(tag);
        }
        ASSIGNMENTS.put(worker.getUuid(),origin);
        worker.getBrain().doExclusively(net.minecraft.entity.ai.brain.Activity.WORK);
        worker.getBrain().forget(MemoryModuleType.INTERACTION_TARGET);
    }
    private static void releaseJob(VillagerEntity worker){
        ASSIGNMENTS.remove(worker.getUuid());
        for(String tag:List.copyOf(worker.getCommandTags()))if(tag.startsWith(JOB_TAG))worker.removeCommandTag(tag);
    }
    private static void restoreJobs(ServerWorld world,VillageMemory memory,List<VillagerEntity> workers){
        for(var worker:workers)for(String tag:List.copyOf(worker.getCommandTags()))if(tag.startsWith(JOB_TAG)){
            try{
                BlockPos pos=BlockPos.fromLong(Long.parseLong(tag.substring(JOB_TAG.length())));
                var h=memory.houses.stream().filter(a->a.min.equals(pos)).findFirst().orElse(null);
                if(h!=null&&findRepair(world,h)!=null)ASSIGNMENTS.put(worker.getUuid(),pos);else releaseJob(worker);
            }catch(NumberFormatException ignored){worker.removeCommandTag(tag);}
        }
    }
    private static void captureChunk(ServerWorld world,WorldChunk chunk){
        if(!world.getRegistryKey().equals(World.OVERWORLD)||!chunk.hasStructureReferences())return;
        VillageMemory memory=world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        if(memory.houses.size()>=MAX_HOUSES)return;
        long chunkKey=chunk.getPos().toLong();
        {
            var starts=world.getStructureAccessor().getStructureStarts(chunk.getPos(),VillageBuilders::isVillage);
            for(StructureStart start:starts)for(StructurePiece piece:start.getChildren()){
                if(!(piece instanceof PoolStructurePiece pool))continue;
                Identifier id=villagePieceId(pool.getPoolElement());
                if(id==null)continue;
                String path=id.getPath();
                if(!path.startsWith("village/")||path.contains("abandoned"))continue;
                BlockBox box=piece.getBoundingBox();
                int dx=box.getMaxX()-box.getMinX()+1,dy=box.getMaxY()-box.getMinY()+1,dz=box.getMaxZ()-box.getMinZ()+1;
                if(dx<1||dz<1||dy<1||dx>128||dz>128||dy>64)continue;
                BlockPos pieceMin=new BlockPos(box.getMinX(),box.getMinY(),box.getMinZ()),pieceMax=new BlockPos(box.getMaxX(),box.getMaxY(),box.getMaxZ());
                if(pieceMax.getX()<chunk.getPos().getStartX()||pieceMin.getX()>chunk.getPos().getEndX()
                        ||pieceMax.getZ()<chunk.getPos().getStartZ()||pieceMin.getZ()>chunk.getPos().getEndZ())continue;
                boolean housePiece=path.contains("/houses/")&&!path.endsWith("/fighting_arena")&&dx<=48&&dz<=48&&dy<=64;
                BlockPos min=housePiece?pieceMin:new BlockPos(Math.max(box.getMinX(),chunk.getPos().getStartX()),box.getMinY(),Math.max(box.getMinZ(),chunk.getPos().getStartZ()));
                BlockPos max=housePiece?pieceMax:new BlockPos(Math.min(box.getMaxX(),chunk.getPos().getEndX()),box.getMaxY(),Math.min(box.getMaxZ(),chunk.getPos().getEndZ()));
                long sourceVillage=start.getPos().toLong();
                var settlement=VillageEraData.get(world).settlements.get(sourceVillage);
                if(housePiece&&settlement!=null&&settlement.xp.getOrDefault("gothic_rebuild:"+sourceVillage+":"+pieceMin.asLong(),0)>=2)continue;
                String key=housePiece?"template:"+id+":"+pieceMin.asLong():"structure:"+id+":"+pieceMin.asLong()+":"+pieceMax.asLong()+":"+chunkKey;
                VillageMemory.House house=memory.houses.stream().filter(h->housePiece?h.kind.equals("house")&&h.min.equals(min):h.recordKey.equals(key)).findFirst().orElse(null);
                if(house==null){house=new VillageMemory.House(min,max,List.of(),world.getTime(),List.of(),0,true,List.of(),List.of(),List.of(),housePiece?"house":"structure",key,List.of());memory.houses.add(house);}
                boolean supplement=house.capturedChunks.contains(chunkKey);
                if(supplement&&house.supplementedChunks.contains(chunkKey))continue;
                Set<BlockPos> recorded=new HashSet<>();for(var b:house.blueprint)recorded.add(b.pos());
                for(int y=min.getY();y<=max.getY();y++)for(int x=Math.max(min.getX(),chunk.getPos().getStartX());x<=Math.min(max.getX(),chunk.getPos().getEndX());x++)
                    for(int z=Math.max(min.getZ(),chunk.getPos().getStartZ());z<=Math.min(max.getZ(),chunk.getPos().getEndZ());z++){
                        BlockPos p=new BlockPos(x,y,z);BlockState state=chunk.getBlockState(p);
                        if(isStructural(state)&&(!supplement||state.hasBlockEntity()||!state.getFluidState().isEmpty())&&recorded.add(p)){
                            BlockState saved=state.getBlock() instanceof CropBlock?state.getBlock().getDefaultState():state;
                            house.blueprint.add(new VillageMemory.BlockRecord(p,saved));
                        }
                    }
                if(!supplement)house.capturedChunks.add(chunkKey);
                house.supplementedChunks.add(chunkKey);
                long village=start.getPos().toLong();
                if(memory.teams.stream().noneMatch(t->t.village==village))memory.teams.add(new VillageMemory.Team(village,min,0));
                memory.markDirty();
                if(memory.houses.size()>=MAX_HOUSES)return;
            }
        }
    }
    private static Identifier villagePieceId(net.minecraft.structure.pool.StructurePoolElement element){
        if(element instanceof SinglePoolElement single){
            try{Identifier id=single.getIdOrThrow();return id.getPath().startsWith("village/")?id:null;}catch(RuntimeException ignored){return null;}
        }
        if(element instanceof net.minecraft.structure.pool.ListPoolElement list){
            for(var child:list.getElements()){Identifier id=villagePieceId(child);if(id!=null)return id;}
        }
        return null;
    }
    private static boolean isVillage(Structure structure){
        Identifier id=Registries.STRUCTURE_TYPE.getId(structure.getType());
        // Structure types alone include all jigsaw structures; individual pieces are filtered by village/ houses/ below.
        return id!=null && id.getPath().equals("jigsaw");
    }
    private static void spawnTeams(ServerWorld world,VillageMemory memory){
        for(VillageMemory.Team team:memory.teams){
            if(world.getPlayers().stream().noneMatch(p->p.squaredDistanceTo(Vec3d.ofCenter(team.anchor))<384*384))continue;
            if(team.builders.removeIf(id->{try{var entity=world.getEntity(UUID.fromString(id));return entity!=null&&(!(entity instanceof VillagerEntity v)||!v.isAlive()||!v.getVillagerData().profession().matchesKey(BuilderProfession.KEY));}catch(IllegalArgumentException invalid){return true;}}))memory.markDirty();
            String owner="halloween_builder_team_"+Long.toUnsignedString(team.village);
            List<VillagerEntity> villagers=world.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(80,40,80),v->v.isAlive()&&!v.isBaby());
            for(VillagerEntity v:villagers){
                if(team.builders.size()>=VillageEras.builderTarget(world,team))break;
                if(v.getVillagerData().profession().matchesKey(BuilderProfession.KEY)
                        &&v.getCommandTags().stream().noneMatch(t->t.startsWith("halloween_builder_team_")&&!t.equals(owner))
                        &&!team.builders.contains(v.getUuidAsString())){
                    v.setExperience(Math.max(1,v.getExperience()));v.addCommandTag(owner);team.builders.add(v.getUuidAsString());memory.markDirty();
                }
            }
            if(team.builders.size()>=VillageEras.builderTarget(world,team))continue;
            VillagerEntity worker=villagers.stream().filter(v->v.getVillagerData().profession().matchesKey(net.minecraft.village.VillagerProfession.NONE)
                    &&!v.getCommandTags().contains("halloween_guard")&&!v.getCommandTags().contains("halloween_bandit")&&!Oprichniki.isOfficer(v)).findFirst().orElse(null);
            if(worker==null){
                if(world.getTime()%100!=0)continue;
                for(int i=0;i<20;i++){
                    int x=team.anchor.getX()+world.random.nextBetween(-10,10),z=team.anchor.getZ()+world.random.nextBetween(-10,10);
                    BlockPos p=new BlockPos(x,world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z),z);
                    Vec3d feet=null;for(int dy:new int[]{0,1,-1,2,-2,3,-3}){feet=workerSpawnPosition(world,new BlockPos(x,team.anchor.getY()+dy,z));if(feet!=null)break;}
                    if(feet==null&&Math.abs(p.getY()-team.anchor.getY())<=8)feet=workerSpawnPosition(world,p);
                    if(feet==null)continue;
                    worker=EntityType.VILLAGER.create(world,SpawnReason.STRUCTURE);if(worker==null)break;
                    worker.refreshPositionAndAngles(feet.x,feet.y,feet.z,world.random.nextFloat()*360,0);worker.initialize(world,world.getLocalDifficulty(p),SpawnReason.STRUCTURE,null);
                    if(!world.spawnEntity(worker))worker=null;
                    break;
                }
            }
            if(worker==null)continue;
            worker.setVillagerData(worker.getVillagerData().withProfession(BuilderProfession.ENTRY));worker.setExperience(1);worker.reinitializeBrain(world);
            worker.addCommandTag(owner);worker.setPersistent();team.builders.add(worker.getUuidAsString());memory.markDirty();
        }
    }
    static Vec3d workerSpawnPosition(ServerWorld w,BlockPos top){
        if(!isLoaded(w,top)||!w.getWorldBorder().contains(top)||!w.getFluidState(top).isEmpty())return null;
        var floor=w.getBlockState(top.down()).getCollisionShape(w,top.down());if(floor.isEmpty()||floor.getMax(Direction.Axis.Y)>1)return null;
        var feet=Vec3d.ofBottomCenter(top).add(0,floor.getMax(Direction.Axis.Y)-1,0);var body=new Box(feet.x-.3,feet.y,feet.z-.3,feet.x+.3,feet.y+1.95,feet.z+.3);
        return w.isSpaceEmpty(body)&&w.getOtherEntities(null,body,e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty()?feet:null;
    }
    private static void cleanupTemporaryDirt(ServerWorld world){
        Map<BlockPos,Long> placed=TEMP_DIRT.get(world);if(placed==null)return;
        var iterator=placed.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();BlockPos p=entry.getKey();
            if(!isLoaded(world,p)||world.getTime()<entry.getValue())continue;
            if(!world.getBlockState(p).isOf(Blocks.DIRT)){iterator.remove();continue;}
            if(!world.getBlockState(p.up()).isAir()||!world.getOtherEntities(null,new Box(p).stretch(0,2,0),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty())continue;
            world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);iterator.remove();
        }
    }
    private static void rescueBuilders(ServerWorld world,List<VillagerEntity> workers){
        for(VillagerEntity builder:workers){
            if(!builder.isAlive()||builder.getEntityWorld()!=world||VillageProjects.owns(builder))continue;
            UUID id=builder.getUuid();Escape pending=ESCAPES.get(id);
            if(pending!=null){
                BlockPos p=pending.pos();
                if(world.getTime()-pending.started()>40){ESCAPES.remove(id);continue;}
                if(builder.getY()>=p.getY()+1.02&&world.getBlockState(p).isAir()&&world.getBlockState(p.down()).isSolidBlock(world,p.down())){
                    if(world.setBlockState(p,Blocks.DIRT.getDefaultState(),Block.NOTIFY_ALL))
                        TEMP_DIRT.computeIfAbsent(world,key->new HashMap<>()).put(p.toImmutable(),world.getTime()+400);
                    builder.swingHand(Hand.MAIN_HAND);ESCAPES.remove(id);
                }
                continue;
            }
            if(TOWERS.containsKey(id)||CLIMB_TARGET.containsKey(id)||world.getTime()%10!=0||!builder.isOnGround()||!world.isSkyVisible(builder.getBlockPos().up(2)))continue;
            BlockPos foot=builder.getBlockPos();
            var memory=world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
            boolean indoors=memory.houses.stream().anyMatch(h->foot.getX()>=h.min.getX()&&foot.getX()<=h.max.getX()
                &&foot.getZ()>=h.min.getZ()&&foot.getZ()<=h.max.getZ()&&foot.getY()>=h.min.getY()&&foot.getY()<=h.max.getY()+1);
            if(indoors)continue;
            int banks=0;
            for(Direction d:Direction.Type.HORIZONTAL){
                BlockPos bank=foot.offset(d,2).up(2);var state=world.getBlockState(bank);
                if((state.isIn(BlockTags.DIRT)||state.isIn(BlockTags.BASE_STONE_OVERWORLD)||state.isOf(Blocks.GRAVEL)||state.isOf(Blocks.SAND))&&state.isSolidBlock(world,bank))banks++;
            }
            if(banks<3||!world.getBlockState(foot).isAir()||!world.getBlockState(foot.up(2)).isAir())continue;
            ESCAPES.put(id,new Escape(foot,world.getTime()));
            builder.getNavigation().stop();builder.setVelocity(0,.42,0);builder.velocityDirty=true;
        }
    }
    private static void updateClimbers(ServerWorld world,List<VillagerEntity> workers){
        for(VillagerEntity worker:workers){
            if(!worker.isAlive()||worker.isSleeping()||worker.getEntityWorld()!=world||VillageProjects.clearing(worker)||VillageProjects.owns(worker)&&!TOWERS.containsKey(worker.getUuid()))continue;
            UUID id=worker.getUuid();RepairTower tower=TOWERS.get(id);
            if(tower==null){
                Integer target=CLIMB_TARGET.get(id);BlockPos base=CLIMB_BASE.get(id);
                if(target==null||base==null)continue;
                if(worker.getY()>=target+.05){CLIMB_TARGET.remove(id);CLIMB_BASE.remove(id);continue;}
                steerIntoScaffold(worker,base);worker.setSneaking(false);
                if(world.getBlockState(worker.getBlockPos()).isOf(Blocks.SCAFFOLDING)){
                    Vec3d velocity=worker.getVelocity();worker.setVelocity(velocity.x,.21,velocity.z);worker.velocityDirty=true;
                }
                continue;
            }
            if(!workHours(world))tower.finishing=true;
            if(tower.finishing){
                var entry=new BlockPos(tower.base.getX(),worker.getBlockY(),tower.base.getZ());
                if(worker.isOnGround()&&!world.getBlockState(entry).isOf(Blocks.SCAFFOLDING)&&!world.getBlockState(entry.down()).isOf(Blocks.SCAFFOLDING)){
                    TOWERS.remove(id);CLIMB_TARGET.remove(id);CLIMB_BASE.remove(id);worker.setSneaking(false);continue;
                }
                if(worker.getY()<=tower.base.getY()+.6){
                    worker.setSneaking(false);
                    VillageMemory memory=world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
                    removeTower(world,memory,tower);TOWERS.remove(id);CLIMB_TARGET.remove(id);continue;
                }
                steerIntoScaffold(worker,tower.base);
                worker.setSneaking(true);
                if(world.getBlockState(worker.getBlockPos()).isOf(Blocks.SCAFFOLDING)||world.getBlockState(worker.getBlockPos().down()).isOf(Blocks.SCAFFOLDING)){
                    worker.fallDistance=0;Vec3d v=worker.getVelocity();worker.setVelocity(v.x,-.18,v.z);worker.velocityDirty=true;
                }
                continue;
            }
            Integer target=CLIMB_TARGET.get(id);
            if(target==null)continue;
            steerIntoScaffold(worker,tower.base);worker.setSneaking(false);
            if(worker.getY()>=target+.05){CLIMB_TARGET.remove(id);continue;}
            if(world.getBlockState(worker.getBlockPos()).isOf(Blocks.SCAFFOLDING)
                &&(world.getBlockState(worker.getBlockPos().up(2)).isAir()||world.getBlockState(worker.getBlockPos().up(2)).isOf(Blocks.SCAFFOLDING))){
                Vec3d v=worker.getVelocity();worker.setVelocity(v.x,.21,v.z);worker.velocityDirty=true;
                ATTEMPT_STARTED.put(id,world.getTime());
            }
        }
    }
    private static void steerIntoScaffold(VillagerEntity worker,BlockPos base){
        double dx=base.getX()+.5-worker.getX(),dz=base.getZ()+.5-worker.getZ();
        if(dx*dx+dz*dz>2.25){
            int entryY=Math.max(base.getY(),worker.getBlockY());
            while(entryY>base.getY()&&!worker.getEntityWorld().getBlockState(new BlockPos(base.getX(),entryY,base.getZ())).isOf(Blocks.SCAFFOLDING))entryY--;
            if(worker.getNavigation().isIdle()||worker.age%20==0)worker.getNavigation().startMovingTo(base.getX()+.5,entryY,base.getZ()+.5,.55);
        }else{
            BlockPos entry=new BlockPos(base.getX(),worker.getBlockY(),base.getZ());
            // Do not walk off the edge toward a tower whose entry at this height has become solid masonry.
            if(!worker.getEntityWorld().getBlockState(entry).isOf(Blocks.SCAFFOLDING)&&!worker.getEntityWorld().getBlockState(entry.down()).isOf(Blocks.SCAFFOLDING))return;
            worker.getNavigation().stop();
            var v=worker.getVelocity();worker.setVelocity(MathHelper.clamp(dx*.22,-.10,.10),v.y,MathHelper.clamp(dz*.22,-.10,.10));worker.velocityDirty=true;
        }
    }
    private static void guideToScaffold(VillagerEntity worker,BlockPos base,int targetY){
        if(worker.getY()+1<base.getY()||worker.squaredDistanceTo(Vec3d.ofBottomCenter(base))>36&&worker.getY()<base.getY()+2){VillageProjects.approach((ServerWorld)worker.getEntityWorld(),worker,base);return;}
        CLIMB_BASE.put(worker.getUuid(),base.toImmutable());CLIMB_TARGET.put(worker.getUuid(),targetY);steerIntoScaffold(worker,base);
    }
    static boolean canReplaceScaffold(ServerWorld w,BlockPos pos,BlockState replacement){
        if(!w.getBlockState(pos).isOf(Blocks.SCAFFOLDING))return false;
        for(var house:w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses)if(house.repairScaffolds.contains(pos))return !scaffoldInUse(w,house.repairScaffolds,pos);
        return false;
    }
    static boolean scaffoldInUse(ServerWorld w,Collection<BlockPos> blocks,BlockPos column){
        int low=column.getY(),high=column.getY();for(var p:blocks)if(p.getX()==column.getX()&&p.getZ()==column.getZ()){low=Math.min(low,p.getY());high=Math.max(high,p.getY());}
        return !w.getOtherEntities(null,new Box(column.getX()-.25,low,column.getZ()-.25,column.getX()+1.25,high+3,column.getZ()+1.25),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty();
    }
    private static Set<Long> occupiedScaffoldColumns(ServerWorld w,Collection<BlockPos> blocks){
        Map<Long,List<BlockPos>> columns=new HashMap<>();for(var p:blocks)columns.computeIfAbsent(ChunkPos.toLong(p.getX(),p.getZ()),key->new ArrayList<>()).add(p);
        Set<Long> occupied=new HashSet<>();for(var entry:columns.entrySet())if(scaffoldInUse(w,entry.getValue(),entry.getValue().getFirst()))occupied.add(entry.getKey());return occupied;
    }
    private static void removeTower(ServerWorld w,VillageMemory memory,RepairTower tower){
        if(scaffoldInUse(w,tower.house.repairScaffolds,tower.base))return;
        for(BlockPos p:List.copyOf(tower.house.repairScaffolds)){
            if(p.getX()!=tower.base.getX()||p.getZ()!=tower.base.getZ()||!isLoaded(w,p))continue;
            if(w.getBlockState(p).isOf(Blocks.SCAFFOLDING)&&w.getOtherEntities(null,new Box(p).stretch(0,1,0),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty()){
                w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);tower.house.repairScaffolds.remove(p);memory.markDirty();
            }
        }
    }
    private static void cleanupAbandonedTowers(ServerWorld world,VillageMemory memory){
        for(VillageMemory.House h:memory.houses){
            if(!h.scaffolds.isEmpty()&&h.work.isEmpty())cleanup(world,h,false);
            if(h.repairScaffolds.isEmpty()||TOWERS.values().stream().anyMatch(t->t.house==h))continue;
            boolean occupied=h.repairScaffolds.stream().anyMatch(p->!world.getOtherEntities(null,new Box(p),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty());
            if(!occupied)removeRepairScaffolds(world,memory,h);
        }
    }
    private static void removeRepairScaffolds(ServerWorld world,VillageMemory memory,VillageMemory.House house){
        Set<Long> occupiedColumns=occupiedScaffoldColumns(world,house.repairScaffolds);
        for(BlockPos p:List.copyOf(house.repairScaffolds)){
            if(!isLoaded(world,p)||occupiedColumns.contains(ChunkPos.toLong(p.getX(),p.getZ())))continue;var state=world.getBlockState(p);
            boolean temporary=state.isOf(Blocks.SCAFFOLDING)||state.isOf(Blocks.OAK_PLANKS);
            if(temporary&&world.getOtherEntities(null,new Box(p).stretch(0,2,0),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty()){
                world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);house.repairScaffolds.remove(p);memory.markDirty();
            }else if(!temporary){house.repairScaffolds.remove(p);memory.markDirty();}
        }
    }
    private static boolean isStructural(BlockState s){
        return !s.isAir()&&!(s.getBlock() instanceof FluidBlock)&&!s.isOf(Blocks.STRUCTURE_VOID)&&!s.isOf(Blocks.JIGSAW)&&!s.isOf(Blocks.STRUCTURE_BLOCK);
    }
    private static boolean isLoaded(ServerWorld w,BlockPos p){return w.isChunkLoaded(new ChunkPos(p).toLong());}
    private static VillagerEntity nearestWorker(List<VillagerEntity> workers,VillageMemory.House h,Set<UUID> used){
        BlockPos center=new BlockPos((h.min.getX()+h.max.getX())/2,h.min.getY(),(h.min.getZ()+h.max.getZ())/2);
        return workers.stream().filter(v->(v.squaredDistanceTo(Vec3d.ofCenter(center))<192*192||h.min.equals(ASSIGNMENTS.get(v.getUuid())))&&!used.contains(v.getUuid())&&!ESCAPES.containsKey(v.getUuid()))
                .filter(v->ASSIGNMENTS.get(v.getUuid())==null||h.min.equals(ASSIGNMENTS.get(v.getUuid())))
                .filter(v->!TOWERS.containsKey(v.getUuid())||!TOWERS.get(v.getUuid()).finishing)
                
                .min(Comparator.<VillagerEntity>comparingInt(v->h.min.equals(ASSIGNMENTS.get(v.getUuid()))?0:1).thenComparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(center)))).orElse(null);
    }
    private static boolean needsRepair(ServerWorld world,VillageMemory.BlockRecord record){
        if(VillagePrisons.managed(world,record.pos()))return false;
        if(VillageTerrain.suppressRepair(world,record)||VillageProjects.pending(world,record.pos()))return false;
        if(VillageProjects.superseded(world,record)||!VillageProjects.has(world,record.pos())&&VillageCivilWorks.superseded(world,record))return false;
        BlockState current=world.getBlockState(record.pos());
        if(record.state().isAir())return VillageCivilWorks.mayReplace(world,record.pos(),current,record.state());
        return current.isAir()||current.isOf(Blocks.FIRE)||current.isOf(Blocks.SOUL_FIRE)||(current.getBlock() instanceof FluidBlock && current.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER))||VillageCivilWorks.mayReplace(world,record.pos(),current,record.state())||(VillageExpansion.softPlant(current)&&!current.isOf(record.state().getBlock()))
            ||(current.getBlock() instanceof FluidBlock&&current.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER)
                &&record.state().getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER));
    }
    private static final class RepairCursor { int house, block; }
    private static final Map<VillageMemory.Team, RepairCursor> FAST_REPAIRS = new WeakHashMap<>();

    static int fastRepair(ServerWorld world, VillageMemory.Team team, int scans, int repairs) {
        var memory = world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        var cursor = FAST_REPAIRS.computeIfAbsent(team, ignored -> new RepairCursor());
        int fixed = 0;
        long deadline = System.nanoTime() + 700_000;
        while (!memory.houses.isEmpty() && scans-- > 0 && fixed < repairs && System.nanoTime() < deadline) {
            if (cursor.house >= memory.houses.size()) { cursor.house = 0; cursor.block = 0; }
            var house = memory.houses.get(cursor.house);
            if (cursor.block == 0 && (!house.min.isWithinDistance(team.anchor, 270) || VillageEras.team(world, house.min) != team)
                    || cursor.block >= house.blueprint.size()) {
                cursor.house++;
                cursor.block = 0;
                continue;
            }
            var record = house.blueprint.get(cursor.block++);
            var pos = record.pos();
            if (!isLoaded(world, pos) || !needsRepair(world, record) || world.getBlockState(pos).hasBlockEntity()) continue;
            if (!VillageProjects.placementOccupants(world, pos, record.state()).isEmpty()) continue;
            var state = record.state();
            boolean repaired;
            if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof BedBlock || state.getBlock() instanceof TallPlantBlock) {
                repaired = restorePairedBlock(world, memory, house, record);
            } else {
                repaired = state.canPlaceAt(world, pos)
                        && world.setBlockState(pos, VillageConnections.connected(world, pos, state), Block.NOTIFY_ALL);
            }
            if (repaired) fixed++;
        }
        return fixed;
    }

    static VillageMemory.BlockRecord findRepair(ServerWorld world,VillageMemory.House h){
        for(var record:h.blueprint)if(isLoaded(world,record.pos())&&needsRepair(world,record))return record;
        return null;
    }
    private static VillageMemory.BlockRecord findRepair(ServerWorld world,VillageMemory.House h,VillagerEntity worker,Set<BlockPos> reserved){
        RepairTower tower=TOWERS.get(worker.getUuid());
        if(tower!=null&&tower.house==h&&!tower.finishing){
            for(var r:h.blueprint)if(r.pos().equals(tower.target)&&needsRepair(world,r))return r;
            tower.finishing=true;return null;
        }
        BlockPos previous=ATTEMPT_TARGET.get(worker.getUuid());
        if(h.min.equals(ASSIGNMENTS.get(worker.getUuid()))&&previous!=null&&!reserved.contains(previous)&&DEFERRED.getOrDefault(previous,0L)<=world.getTime())
            for(var r:h.blueprint)if(r.pos().equals(previous)&&isLoaded(world,previous)&&needsRepair(world,r)&&(r.state().getBlock() instanceof DoorBlock||r.state().getBlock() instanceof BedBlock||r.state().getBlock() instanceof TallPlantBlock||r.state().canPlaceAt(world,previous)))return r;
        return h.blueprint.stream().filter(r->isLoaded(world,r.pos())&&needsRepair(world,r)
                &&!reserved.contains(r.pos())&&DEFERRED.getOrDefault(r.pos(),0L)<=world.getTime())
                .filter(r->r.state().getBlock() instanceof DoorBlock||r.state().getBlock() instanceof BedBlock||r.state().getBlock() instanceof TallPlantBlock||r.state().canPlaceAt(world,r.pos()))
                .min(Comparator.<VillageMemory.BlockRecord>comparingInt(r->r.state().isAir()?0:1)
                    .thenComparingInt(r->r.pos().getY())
                    .thenComparingDouble(r->worker.squaredDistanceTo(Vec3d.ofCenter(r.pos())))).orElse(null);
    }
    static boolean work(ServerWorld w,VillageMemory memory,VillageMemory.House h,VillagerEntity villager,VillageMemory.BlockRecord task,boolean construction){
        BlockPos p=task.pos();if(!isLoaded(w,p))return false;
        BlockState current=w.getBlockState(p);
        if(current.equals(task.state())){finishRepairTower(villager,p);return true;}
        if(current.isOf(Blocks.FIRE)||current.isOf(Blocks.SOUL_FIRE)){w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);current=w.getBlockState(p);}
        if(VillageExpansion.softPlant(current)){
            w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);current=w.getBlockState(p);
        }
        boolean restoringInWater=current.getBlock() instanceof FluidBlock&&current.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER);
        if(!current.isAir()&&!restoringInWater&&!VillageCivilWorks.mayReplace(w,p,current,task.state()))return false;
        if(task.state().isAir()&&!VillageCivilWorks.mayReplace(w,p,current,task.state()))return false;
        if(villager.squaredDistanceTo(Vec3d.ofCenter(p))>4.5*4.5){
            BlockPos walk=p;
            if(construction&&!h.work.isEmpty()&&p.getY()>villager.getBlockY()+3){
                BlockPos scaffold=h.work.get(0).pos();guideToScaffold(villager,scaffold,p.getY()-2);return false;
            }
            if(!construction&&p.getY()>villager.getBlockY()+3){prepareRepairTower(w,memory,h,villager,p);return false;}
            tryBridge(w,memory,h,villager,p);
            long elapsed=w.getTime()-ATTEMPT_STARTED.getOrDefault(villager.getUuid(),w.getTime());
            if(villager.getNavigation().isIdle()||elapsed==0||elapsed%40==0){
                villager.getBrain().remember(MemoryModuleType.WALK_TARGET,new WalkTarget(walk,.35f,2),80);
                villager.getNavigation().startMovingTo(walk.getX()+.5,walk.getY(),walk.getZ()+.5,.35);
            }
            return false;
        }
        if(task.state().isOf(Blocks.SCAFFOLDING)&&!task.state().canPlaceAt(w,p))return false;
        Box occupied=task.state().isAir()?new Box(p).stretch(0,1,0):new Box(p);
        if(!w.getOtherEntities(null,occupied,e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()&&!e.isSpectator()).isEmpty())return false;
        if(task.state().getBlock() instanceof DoorBlock||task.state().getBlock() instanceof BedBlock||task.state().getBlock() instanceof TallPlantBlock){
            if(!restorePairedBlock(w,memory,h,task))return false;
        }else if(!w.setBlockState(p,VillageConnections.connected(w,p,task.state()),Block.NOTIFY_ALL))return false;
        villager.swingHand(Hand.MAIN_HAND);
        VillageEras.work(w,villager,"builder",1);
        w.playSound(null,p,task.state().getSoundGroup().getPlaceSound(),SoundCategory.BLOCKS,.65f,1);
        if(construction&&task.state().isOf(Blocks.SCAFFOLDING))h.scaffolds.add(p);
        else if(construction)h.blueprint.add(task);
        if(!construction)finishRepairTower(villager,p);
        memory.markDirty();return true;
    }
    private static void tryBridge(ServerWorld w,VillageMemory memory,VillageMemory.House house,VillagerEntity worker,BlockPos target){
        if(!worker.isOnGround()||Math.abs(target.getY()-worker.getBlockY())>3||house.repairScaffolds.size()>64)return;
        double dx=target.getX()+.5-worker.getX(),dz=target.getZ()+.5-worker.getZ();
        Direction direction=Math.abs(dx)>Math.abs(dz)?(dx>0?Direction.EAST:Direction.WEST):(dz>0?Direction.SOUTH:Direction.NORTH);
        BlockPos floor=worker.getBlockPos().offset(direction).down();
        if(!isLoaded(w,floor)||!w.getWorldBorder().contains(floor)||(!w.getBlockState(floor).isAir()&&!w.getBlockState(floor).isOf(Blocks.WATER))||!w.getBlockState(floor.up()).isAir()||!w.getBlockState(floor.up(2)).isAir())return;
        if(w.getBlockState(floor.down()).isSolidBlock(w,floor.down())&&w.getBlockState(floor).isAir())return;
        if(!w.getOtherEntities(null,new Box(floor),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty())return;
        VillageMemory.BlockRecord permanent=null;
        for(var r:house.blueprint)if(r.pos().equals(floor)&&!r.state().isAir()&&r.state().isSolidBlock(w,floor)){permanent=r;break;}
        BlockState material=permanent==null?Blocks.OAK_PLANKS.getDefaultState():permanent.state();
        if(w.setBlockState(floor,material,Block.NOTIFY_ALL)){
            if(permanent==null)house.repairScaffolds.add(floor);worker.swingHand(Hand.MAIN_HAND);memory.markDirty();
            worker.getNavigation().startMovingTo(floor.getX()+.5,floor.getY()+1,floor.getZ()+.5,.5);
        }
    }
    private static void finishRepairTower(VillagerEntity villager,BlockPos pos){
        RepairTower tower=TOWERS.get(villager.getUuid());
        if(tower!=null&&tower.target.equals(pos)){tower.finishing=true;CLIMB_TARGET.remove(villager.getUuid());}
    }
    static void projectAccess(ServerWorld w,VillageMemory memory,VillageProjects.Project project,VillagerEntity worker,BlockPos target){
        String key="project_access:"+project.id;
        var house=memory.houses.stream().filter(h->h.recordKey.equals(key)).findFirst().orElse(null);
        if(house==null){house=new VillageMemory.House(project.min,project.max,List.of(),w.getTime(),List.of(),0,true,List.of(),List.of(),List.of(),"infrastructure",key,List.of());memory.houses.add(house);memory.markDirty();}
        prepareRepairTower(w,memory,house,worker,target);
        if(Boolean.getBoolean("halloween.eraSoak")&&w.getTime()%2000==0){var tower=TOWERS.get(worker.getUuid());LOG.info("ACCESS worker={} target={} tower={} climb={}",worker.getBlockPos(),target,tower==null?"none":tower.base+" finishing="+tower.finishing,CLIMB_TARGET.get(worker.getUuid()));}

    }
    static void finishProjectAccess(String project){for(var tower:TOWERS.values())if(tower.house.recordKey.equals("project_access:"+project))tower.finishing=true;}
    private static void prepareRepairTower(ServerWorld world,VillageMemory memory,VillageMemory.House house,VillagerEntity worker,BlockPos target){
        UUID id=worker.getUuid();RepairTower tower=TOWERS.get(id);
        if(tower!=null&&(!tower.target.equals(target)||tower.house!=house)){
            if(tower.house==house&&!tower.finishing&&Math.abs(tower.base.getX()-target.getX())<=3&&Math.abs(tower.base.getZ()-target.getZ())<=3)tower.target=target;
            else{tower.finishing=true;return;}
        }
        if(tower==null){
            // Find a supported scaffold entrance first; walking toward an elevated wall at current Y can be unreachable.
            BlockPos base=null;
            outer:for(int r=1;r<=(house.recordKey.startsWith("project_access:")?5:3);r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=r||house.recordKey.startsWith("project_access:")&&dx*dx+dz*dz>25)continue;
                for(int baseY=Math.max(worker.getBlockY(),target.getY()-1);baseY>=Math.max(world.getBottomY()+1,house.min.getY());baseY--){
                BlockPos p=new BlockPos(target.getX()+dx,baseY,target.getZ()+dz);
                if(!isLoaded(world,p)||!world.getWorldBorder().contains(p)||!world.getBlockState(p.down()).isSolidBlock(world,p.down()))continue;
                boolean clear=true;
                for(int y=p.getY();y<=target.getY();y++){
                    BlockPos q=new BlockPos(p.getX(),y,p.getZ());BlockState old=world.getBlockState(q);
                    if(!old.isAir()&&!house.repairScaffolds.contains(q)&&!(old.getBlock() instanceof SnowBlock)&&!VillageCivilWorks.mayClear(world,q,old)){clear=false;break;}
                }
                if(!clear)continue;
                base=p;break outer;
                }
            }
            if(base==null){
                for(BlockPos saved:house.repairScaffolds){
                    if(Math.abs(saved.getX()-target.getX())>3||Math.abs(saved.getZ()-target.getZ())>3||!isLoaded(world,saved)||!world.getBlockState(saved).isOf(Blocks.SCAFFOLDING))continue;
                    BlockPos bottom=saved;while(house.repairScaffolds.contains(bottom.down())&&world.getBlockState(bottom.down()).isOf(Blocks.SCAFFOLDING))bottom=bottom.down();
                    if(world.getBlockState(bottom.down()).isSolidBlock(world,bottom.down())){base=bottom;break;}
                }
            }
            if(base==null)return;
            tower=new RepairTower(house,base,target);TOWERS.put(id,tower);
        }
        for(int y=tower.base.getY();y<=target.getY();y++){
            BlockPos p=new BlockPos(tower.base.getX(),y,tower.base.getZ());
            if(house.repairScaffolds.contains(p)&&world.getBlockState(p).isOf(Blocks.SCAFFOLDING))continue;
            if(!world.getBlockState(p).isAir()){
                if(world.getBlockState(p).getBlock() instanceof SnowBlock||VillageCivilWorks.mayClear(world,p,world.getBlockState(p))){
                    if(worker.squaredDistanceTo(Vec3d.ofCenter(p))>4.5*4.5){guideToScaffold(worker,tower.base,p.getY()-2);return;}
                    if(!world.getOtherEntities(null,new Box(p).stretch(0,1,0),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty())return;
                    world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);worker.swingHand(Hand.MAIN_HAND);return;
                }
                tower.finishing=true;return;
            }
            if(worker.squaredDistanceTo(Vec3d.ofCenter(p))>4.5*4.5){guideToScaffold(worker,tower.base,p.getY()-2);return;}
            // Scaffolding is climbable and can be extended around the crew already inside its column.
            // Treating those climbers as solid-wall obstructions deadlocks a shared tower.
            if(!Blocks.SCAFFOLDING.getDefaultState().canPlaceAt(world,p))return;
            world.setBlockState(p,Blocks.SCAFFOLDING.getDefaultState(),Block.NOTIFY_ALL);
            house.repairScaffolds.add(p);ATTEMPT_STARTED.put(id,world.getTime());memory.markDirty();worker.swingHand(Hand.MAIN_HAND);return;
        }
        guideToScaffold(worker,tower.base,target.getY()-(house.recordKey.startsWith("project_access:")?1:2));
    }
    private static boolean restorePairedBlock(ServerWorld world,VillageMemory memory,VillageMemory.House house,VillageMemory.BlockRecord task){
        BlockState state=task.state();BlockPos other;
        if(state.getBlock() instanceof DoorBlock){
            other=state.get(DoorBlock.HALF)==net.minecraft.block.enums.DoubleBlockHalf.LOWER?task.pos().up():task.pos().down();
        }else if(state.getBlock() instanceof TallPlantBlock){
            other=state.get(TallPlantBlock.HALF)==net.minecraft.block.enums.DoubleBlockHalf.LOWER?task.pos().up():task.pos().down();
        }else{
            Direction facing=state.get(BedBlock.FACING);
            other=task.pos().offset(state.get(BedBlock.PART)==net.minecraft.block.enums.BedPart.FOOT?facing:facing.getOpposite());
        }
        VillageMemory.BlockRecord pair=null;
        for(var record:house.blueprint)if(record.pos().equals(other)&&record.state().isOf(state.getBlock())){pair=record;break;}
        if(pair==null){
            for(var otherPiece:memory.houses){
                if(other.getX()<otherPiece.min.getX()||other.getX()>otherPiece.max.getX()||other.getY()<otherPiece.min.getY()||other.getY()>otherPiece.max.getY()||other.getZ()<otherPiece.min.getZ()||other.getZ()>otherPiece.max.getZ())continue;
                for(var record:otherPiece.blueprint)if(record.pos().equals(other)&&record.state().isOf(state.getBlock())){pair=record;break;}
                if(pair!=null)break;
            }
        }
        if(pair==null||!isLoaded(world,other))return false;
        BlockState existing=world.getBlockState(other);
        if(!existing.isAir()&&!existing.equals(pair.state())&&!VillageCivilWorks.mayReplace(world,other,existing,pair.state()))return false;
        if(!world.getOtherEntities(null,new Box(other),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()&&!e.isSpectator()).isEmpty())return false;
        VillageMemory.BlockRecord first=task,second=pair;
        if(state.getBlock() instanceof DoorBlock&&state.get(DoorBlock.HALF)==net.minecraft.block.enums.DoubleBlockHalf.UPPER){first=pair;second=task;}
        if(state.getBlock() instanceof BedBlock&&state.get(BedBlock.PART)==net.minecraft.block.enums.BedPart.HEAD){first=pair;second=task;}
        if(state.getBlock() instanceof TallPlantBlock&&state.get(TallPlantBlock.HALF)==net.minecraft.block.enums.DoubleBlockHalf.UPPER){first=pair;second=task;}
        if(!first.state().canPlaceAt(world,first.pos()))return false;
        if(world.getBlockState(first.pos()).isAir()||VillageCivilWorks.mayReplace(world,first.pos(),world.getBlockState(first.pos()),first.state()))world.setBlockState(first.pos(),first.state(),Block.NOTIFY_LISTENERS);
        if(world.getBlockState(second.pos()).isAir()||VillageCivilWorks.mayReplace(world,second.pos(),world.getBlockState(second.pos()),second.state()))world.setBlockState(second.pos(),second.state(),Block.NOTIFY_LISTENERS);
        world.updateNeighborsAlways(first.pos(),first.state().getBlock(),null);
        world.updateNeighborsAlways(second.pos(),second.state().getBlock(),null);
        return world.getBlockState(first.pos()).isOf(state.getBlock())&&world.getBlockState(second.pos()).isOf(state.getBlock());
    }
    static void advance(ServerWorld w,VillageMemory memory,VillageMemory.House h,VillagerEntity v){
        while(h.workIndex<h.work.size()){
            var task=h.work.get(h.workIndex);
            if(!isLoaded(w,task.pos()))return;
            BlockState present=w.getBlockState(task.pos());
            if(!present.isAir()&&!present.equals(task.state())){
                // Obstruction means another actor changed the site. Stop rather than edit their work.
                cleanup(w,h,false);h.work.clear();h.workIndex=0;h.extended=true;memory.markDirty();return;
            }
            if(!work(w,memory,h,v,task,true))return;
            h.workIndex++;memory.markDirty();return;
        }
        cleanup(w,h,true);h.work.clear();h.workIndex=0;h.extended=true;memory.markDirty();
    }
    private static void cleanup(ServerWorld w,VillageMemory.House h,boolean completed){
        Set<Long> occupiedColumns=occupiedScaffoldColumns(w,h.scaffolds);
        for(int i=h.scaffolds.size()-1;i>=0;i--){
            BlockPos p=h.scaffolds.get(i);
            if(!isLoaded(w,p)||occupiedColumns.contains(ChunkPos.toLong(p.getX(),p.getZ())))continue;
            if(w.getBlockState(p).isOf(Blocks.SCAFFOLDING)
                    && w.getOtherEntities(null,new Box(p),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty())
                w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);
        }
        h.scaffolds.removeIf(p->isLoaded(w,p)&&!w.getBlockState(p).isOf(Blocks.SCAFFOLDING));
    }
    static boolean planExtension(ServerWorld w,VillageMemory.House h){
        int x0=h.min.getX(),x1=h.max.getX(),z0=h.min.getZ(),z1=h.max.getZ(),base=h.max.getY()+1;
        if(x1-x0<4||z1-z0<4||x1-x0>9||z1-z0>9||base+7>w.getTopYInclusive())return false;
        // Require a genuinely empty roof volume and an accessible, supported temporary scaffold outside.
        for(int y=base;y<=base+7;y++)for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            BlockPos p=new BlockPos(x,y,z);if(!isLoaded(w,p)||!w.getWorldBorder().contains(p)||!w.getBlockState(p).isAir())return false;
        }
        BlockPos scaffold=null;
        for(int side:new int[]{z0-1,z1+1}){
            for(int sx=x0+1;sx<x1;sx++){
                BlockPos candidate=new BlockPos(sx,h.min.getY()+1,side);
                if(!isLoaded(w,candidate)||!w.getWorldBorder().contains(candidate)||!w.getBlockState(candidate.down()).isSolidBlock(w,candidate.down()))continue;
                boolean clear=true;
                for(int y=candidate.getY();y<=base+5;y++)if(!w.getBlockState(new BlockPos(sx,y,side)).isAir()){clear=false;break;}
                if(clear){scaffold=candidate;break;}
            }
            if(scaffold!=null)break;
        }
        if(scaffold==null)return false;
        int entryX=scaffold.getX(),entryZ=scaffold.getZ()<z0?z0:z1;
        BlockState wood=dominantPlanks(h),roof=matchingWood(wood,"stairs",Blocks.OAK_STAIRS),ridge=matchingWood(wood,"slab",Blocks.OAK_SLAB);
        BlockState beam=dominantLog(h);
        long sandstone=h.blueprint.stream().filter(b->Registries.BLOCK.getId(b.state().getBlock()).getPath().contains("sandstone")).count();
        boolean desert=sandstone>h.blueprint.size()/5;
        BlockState wall=desert?Blocks.SANDSTONE.getDefaultState():wood;
        if(desert){roof=Blocks.SANDSTONE_STAIRS.getDefaultState();ridge=Blocks.SANDSTONE_SLAB.getDefaultState();beam=Blocks.CUT_SANDSTONE.getDefaultState();}
        List<VillageMemory.BlockRecord> jobs=new ArrayList<>();
        for(int y=scaffold.getY();y<=base+5;y++){BlockPos p=new BlockPos(scaffold.getX(),y,scaffold.getZ());jobs.add(new VillageMemory.BlockRecord(p,Blocks.SCAFFOLDING.getDefaultState()));}
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)jobs.add(new VillageMemory.BlockRecord(new BlockPos(x,base,z),wood));
        for(int y=base+1;y<=base+3;y++)for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            if(x!=x0&&x!=x1&&z!=z0&&z!=z1)continue;
            // Open entry beside scaffold, and simple windows on alternating wall segments.
            if(z==entryZ&&x==entryX&&y<=base+2)continue;
            boolean corner=(x==x0||x==x1)&&(z==z0||z==z1);
            boolean window=y>=base+2&&((z==z0||z==z1)&&x==(x0+x1)/2 || (x==x0||x==x1)&&z==(z0+z1)/2);
            jobs.add(new VillageMemory.BlockRecord(new BlockPos(x,y,z),corner?beam:window?Blocks.GLASS_PANE.getDefaultState():wall));
        }
        // Close the triangular end walls below the pitched roof.
        for(int x=x0;x<=x1;x++){
            int rise=Math.min(2,Math.min(x-x0,x1-x));
            for(int z:new int[]{z0,z1})for(int step=0;step<rise;step++)
                jobs.add(new VillageMemory.BlockRecord(new BlockPos(x,base+4+step,z),wall));
        }
        // Gabled roof: stairs rise toward the ridge, with slabs forming the cap.
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            int rise=Math.min(2,Math.min(x-x0,x1-x));
            BlockState roofPart=rise==2?ridge:roof.with(StairsBlock.FACING,x<(x0+x1)/2.0?Direction.EAST:Direction.WEST);
            jobs.add(new VillageMemory.BlockRecord(new BlockPos(x,base+4+rise,z),roofPart));
        }
        h.work=jobs;h.workIndex=0;h.scaffolds=new ArrayList<>();return true;
    }
    private static BlockState dominantPlanks(VillageMemory.House h){
        Map<Block,Integer> counts=new HashMap<>();
        for(var b:h.blueprint)if(b.state().isIn(BlockTags.PLANKS))counts.merge(b.state().getBlock(),1,Integer::sum);
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(e->e.getKey().getDefaultState()).orElse(Blocks.OAK_PLANKS.getDefaultState());
    }
    private static BlockState dominantLog(VillageMemory.House h){
        Map<Block,Integer> counts=new HashMap<>();
        for(var b:h.blueprint)if(b.state().isIn(BlockTags.LOGS))counts.merge(b.state().getBlock(),1,Integer::sum);
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(e->e.getKey().getDefaultState()).orElse(Blocks.OAK_LOG.getDefaultState());
    }
    private static BlockState matchingWood(BlockState planks,String suffix,Block fallback){
        String name=Registries.BLOCK.getId(planks.getBlock()).getPath();
        if(name.endsWith("_planks")){
            Identifier id=Identifier.of("minecraft",name.substring(0,name.length()-7)+"_"+suffix);
            if(Registries.BLOCK.containsId(id))return Registries.BLOCK.get(id).getDefaultState();
        }
        return fallback.getDefaultState();
    }
    private VillageBuilders(){}
}
