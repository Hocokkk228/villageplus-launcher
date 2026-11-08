package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;

/** Saves trampled village plots and lets farmers re-till and replant them. */
public final class VillageFarms {
    private static final Map<ServerWorld,Map<BlockPos,BlockState>> PENDING=new WeakHashMap<>();
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillageFarms::tick);}
    public static void beforeTrample(World world,BlockPos pos){
        if(!(world instanceof ServerWorld w))return;
        BlockState crop=w.getBlockState(pos.up());
        PENDING.computeIfAbsent(w,k->new HashMap<>()).put(pos.toImmutable(),crop.getBlock() instanceof CropBlock?crop.getBlock().getDefaultState():Blocks.AIR.getDefaultState());
    }
    public static void afterTrample(World world,BlockPos pos){
        if(!(world instanceof ServerWorld w))return;
        var pending=PENDING.get(w);BlockState crop=pending==null?null:pending.remove(pos);if(crop==null||!w.getBlockState(pos).isOf(Blocks.DIRT))return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        if(memory.teams.stream().noneMatch(t->pos.isWithinDistance(t.anchor,VillageEras.radius(w,t.anchor)))||memory.farms.size()>=4096)return;
        if(memory.farms.stream().noneMatch(r->r.pos().equals(pos))){memory.farms.add(new VillageMemory.FarmRepair(pos.toImmutable(),crop));memory.markDirty();}
    }
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty()||w.getTime()%20!=0)return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        recoverPlantation(w,memory);
        if(memory.farms.isEmpty())return;
        Set<UUID> used=new HashSet<>();var it=memory.farms.iterator();
        while(it.hasNext()){
            var job=it.next();BlockPos p=job.pos();if(!w.isChunkLoaded(new ChunkPos(p).toLong()))continue;
            BlockState ground=w.getBlockState(p),above=w.getBlockState(p.up());
            if(!ground.isOf(Blocks.DIRT)&&!ground.isOf(Blocks.FARMLAND)){it.remove();memory.markDirty();continue;}
            var farmer=w.getEntitiesByClass(VillagerEntity.class,new Box(p).expand(48),v->v.isAlive()&&!v.isBaby()&&!v.isSleeping()
                &&v.getVillagerData().profession().matchesKey(VillagerProfession.FARMER)&&!used.contains(v.getUuid()))
                .stream().min(Comparator.comparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(p)))).orElse(null);
            if(farmer==null)continue;used.add(farmer.getUuid());
            VillageTasks.hold(farmer);VillageEras.putTag(farmer,"halloween_farm_repair_until_",w.getTime()+25);
            if(farmer.squaredDistanceTo(Vec3d.ofCenter(p))>9){
                farmer.getBrain().remember(MemoryModuleType.WALK_TARGET,new WalkTarget(p,.5f,1),60);
                if(farmer.getNavigation().isIdle()||farmer.age%40==0)farmer.getNavigation().startMovingTo(p.getX()+.5,p.getY(),p.getZ()+.5,.5);continue;
            }
            if(ground.isOf(Blocks.DIRT)){
                if(!above.isAir())continue;
                w.setBlockState(p,Blocks.FARMLAND.getDefaultState(),Block.NOTIFY_ALL);farmer.swingHand(Hand.MAIN_HAND);VillageEras.work(w,farmer,"farmer",1);memory.markDirty();continue;
            }
            if(!job.crop().isAir()&&above.isAir()&&job.crop().canPlaceAt(w,p.up())){w.setBlockState(p.up(),job.crop(),Block.NOTIFY_ALL);farmer.swingHand(Hand.MAIN_HAND);}
            it.remove();memory.markDirty();
        }
    }
    static void recoverPlantation(ServerWorld w,VillageMemory memory){
        // Recover existing trampled plots from the completed plantation blueprint.
        var projects=VillageProjects.get(w);
        for(var project:projects.jobs)if(project.done&&project.kind.equals("plantation")){
            int size=project.plan.size();if(size==0)continue;int start=(int)((w.getTime()/20*128)%size);
            for(int i=0;i<Math.min(128,size);i++){
                var cell=project.plan.get((start+i)%size);var pos=cell.pos();
                if(!cell.after().isOf(Blocks.FARMLAND)||!w.isChunkLoaded(new ChunkPos(pos).toLong())||!w.getBlockState(pos).isOf(Blocks.DIRT)||memory.farms.stream().anyMatch(f->f.pos().equals(pos)))continue;
                var crop=project.plan.stream().filter(c->c.pos().equals(pos.up())&&c.after().getBlock() instanceof CropBlock).map(VillageCivilWorks.Change::after).findFirst().orElse(Blocks.AIR.getDefaultState());
                memory.farms.add(new VillageMemory.FarmRepair(pos,crop));memory.markDirty();
            }
        }
    }
    private VillageFarms(){}
}
