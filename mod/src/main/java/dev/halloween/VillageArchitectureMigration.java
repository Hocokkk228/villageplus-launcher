package dev.halloween;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Upgrade existing settlements in stages; projects are persisted in VillageProjects. */
final class VillageArchitectureMigration {
    static final String ORDINARY="architecture_v2_house:";
    static final String WORKSHOP="architecture_v2_workshop:";
    static boolean running(VillageEraData.Settlement s){return (s.era>=2||s.transitionAt>=0)&&s.xp.getOrDefault("architecture_v2",0)<2;}
    static String houseId(VillageMemory.House h){return ORDINARY+h.min.asLong()+":"+h.recordKey.hashCode();}
    static boolean ordinary(VillageMemory.House h){return !VillageArchitecture.periodHome(h)&&!h.recordKey.startsWith("steam_")&&!h.recordKey.startsWith("architecture_")&&!h.recordKey.startsWith("era_workshop:")&&!h.recordKey.startsWith("era_house:")&&!h.recordKey.contains("fighting_arena")&&(h.kind.equals("house")||h.recordKey.contains("/houses/"))&&h.blueprint.stream().anyMatch(r->!VillageEraPlans.stone(r.state()).equals(r.state()));}
    static void tick(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s){
        if(!running(s))return;
        var projects=VillageProjects.get(w);var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        if(s.xp.getOrDefault("architecture_v2",0)==0){
            s.xp.put("architecture_v2",1);VillageEraData.get(w).markDirty();
            // Cancel obsolete uncompleted jobs before they can place the old box again.
            for(var p:List.copyOf(projects.jobs))if(p.village==team.village&&!p.done&&(p.id.startsWith("architecture_v1:")||p.id.startsWith("era_workshop:")))projects.retire(p);
        }
        var houses=List.copyOf(memory.houses).stream().filter(h->ordinary(h)&&VillageEras.team(w,h.min)==team&&!VillageTerrain.removesHouse(w,h)).toList();
        for(var h:houses){
            if(projects.exists(houseId(h)))continue;
            Map<BlockPos,BlockState> plan=new LinkedHashMap<>();boolean loaded=true;
            for(var r:h.blueprint){if(!w.isChunkLoaded(new ChunkPos(r.pos()).toLong())){VillageProjects.keepLoaded(w,r.pos());loaded=false;break;}var wanted=VillageEraPlans.stone(r.state());if(!wanted.equals(r.state())&&!w.getBlockState(r.pos()).hasBlockEntity()&&w.getBlockState(r.pos()).isOf(r.state().getBlock()))plan.put(r.pos(),wanted);}
            if(!loaded)return;
            if(!plan.isEmpty()){projects.add(w,houseId(h),team.village,"conversion",plan);return;}
        }
        if(projects.jobs.stream().anyMatch(p->p.village==team.village&&!p.done&&p.id.startsWith(ORDINARY)))return;
        // Each old workshop is rebuilt from its recorded blocks; unknown blocks are never demolished.
        var oldJobs=List.copyOf(projects.jobs).stream().filter(p->p.village==team.village&&p.id.startsWith("era_workshop:")).toList();
        for(var old:oldJobs){
            String id=WORKSHOP+old.id;if(projects.exists(id))continue;
            var door=old.plan.stream().filter(c->c.after().getBlock() instanceof DoorBlock&&c.after().get(DoorBlock.HALF)==DoubleBlockHalf.LOWER).map(VillageCivilWorks.Change::pos).findFirst();
            if(door.isEmpty())continue;
            String role=VillageArchitecture.role(old.id);var template=VillageEraPlans.template(w,"profession_"+role+"_0");if(template==null)return;
            BlockPos d=door.get(),base=new BlockPos(d.getX()-template.size().getX()/2,d.getY()-1,d.getZ()-1);
            Set<BlockPos> owned=new HashSet<>();for(var c:old.plan)owned.add(c.pos());
            Map<BlockPos,BlockState> changes=new LinkedHashMap<>();
            for(var c:old.plan){var p=c.pos();if(!w.isChunkLoaded(new ChunkPos(p).toLong())){VillageProjects.keepLoaded(w,p);return;}if(!w.getBlockState(p).hasBlockEntity()&&!c.after().isAir()&&w.getBlockState(p).isOf(c.after().getBlock()))changes.put(p,Blocks.AIR.getDefaultState());}
            var desired=VillageArchitecture.plan(w,base,role,0);
            for(var entry:desired.entrySet()){
                BlockPos p=entry.getKey();if(!w.isChunkLoaded(new ChunkPos(p).toLong())){VillageProjects.keepLoaded(w,p);return;}
                var current=w.getBlockState(p);if(current.hasBlockEntity())continue;
                if(!owned.contains(p)&&!current.isAir()&&!current.isReplaceable()&&!VillageCivilWorks.natural(current))return;
                changes.put(p,entry.getValue());
            }
            if(!changes.isEmpty())projects.add(w,id,team.village,"conversion",changes);
            return;
        }
        if(projects.jobs.stream().anyMatch(p->p.village==team.village&&!p.done&&p.id.startsWith(WORKSHOP)))return;
        s.xp.put("architecture_v2",2);VillageEraData.get(w).markDirty();
    }
    static void revisitWood(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement settlement){
        if(settlement.era<2&&settlement.transitionAt<0)return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
        // Retire only obsolete second-era conversions accidentally queued for completed third-era homes.
        for(var home:memory.houses)if(VillageArchitecture.periodHome(home))for(var job:projects.jobs)
            if(!job.done&&job.id.equals("structural_wood_v3:"+home.min.asLong()))projects.retire(job);
        for(var house:List.copyOf(memory.houses)){
            if(!ordinary(house)||VillageEras.team(w,house.min)!=team)continue;
            String id="structural_wood_v3:"+house.min.asLong();if(projects.exists(id))continue;
            Map<BlockPos,BlockState> plan=new LinkedHashMap<>();boolean loaded=true;
            for(var r:house.blueprint){if(!w.isChunkLoaded(new ChunkPos(r.pos()).toLong())){loaded=false;break;}var old=w.getBlockState(r.pos());var wanted=VillageEraPlans.stone(old);
                if(!old.hasBlockEntity()&&old.isOf(r.state().getBlock())&&!wanted.equals(old))plan.put(r.pos(),wanted);
            }
            if(loaded&&!plan.isEmpty()){projects.add(w,id,team.village,"conversion",plan);return;}
        }
    }
    private VillageArchitectureMigration(){}
}
