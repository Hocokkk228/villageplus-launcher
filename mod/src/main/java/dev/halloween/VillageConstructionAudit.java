package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;

/** Bounded verification of completed buildings; a missing wall reopens its actual saved construction plan. */
final class VillageConstructionAudit {
    private static final class Cursor {int job,block;}
    private static final Map<ServerWorld,Cursor> CURSORS=new WeakHashMap<>();
    static boolean reopen(ServerWorld world,VillageProjects.Project job,int at){
        if(!job.done||job.index<job.plan.size())return false;
        var projects=VillageProjects.get(world);
        job.index=at;job.done=false;job.blocked=0;
        for(int i=at;i<job.plan.size();i++)projects.unfinished.merge(job.plan.get(i).pos(),1,Integer::sum);
        projects.markDirty();return true;
    }
    static void register(){ServerTickEvents.END_WORLD_TICK.register(world->{
        if(world.getPlayers().isEmpty()||world.getTime()%20!=0)return;
        var projects=VillageProjects.get(world);if(projects.jobs.isEmpty())return;
        var cursor=CURSORS.computeIfAbsent(world,k->new Cursor());long deadline=System.nanoTime()+600_000;
        int visited=0;
        for(int budget=0;budget<2048&&visited<projects.jobs.size()&&System.nanoTime()<deadline;budget++){
            cursor.job=Math.floorMod(cursor.job,projects.jobs.size());var job=projects.jobs.get(cursor.job);
            if(!job.done||job.index<job.plan.size()||!VillageBuildingSites.building(job.kind)||cursor.block>=job.plan.size()){
                cursor.job++;cursor.block=0;visited++;continue;
            }
            int at=cursor.block++;var change=job.plan.get(at);
            if(change.after().isAir()||!world.isChunkLoaded(change.pos()))continue;
            var latest=projects.expected.get(change.pos());if(latest!=null&&!latest.equals(change.after()))continue;
            if(!world.getBlockState(change.pos()).isOf(change.after().getBlock())){
                reopen(world,job,at);cursor.job++;cursor.block=0;visited++;
            }
        }
    });}
    private VillageConstructionAudit(){}
}
