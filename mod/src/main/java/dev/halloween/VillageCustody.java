package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Exclusive custody and a trail through actual doorways, rather than a pull through walls. */
final class VillageCustody {
    private static final Map<VillagerEntity,ArrayDeque<Vec3d>> TRAILS=new WeakHashMap<>();
    static boolean busy(ServerWorld w,VillagerEntity g,Entity except){
        if(g.isLeashed())return true;
        for(var e:w.getEntitiesByClass(VillagerEntity.class,g.getBoundingBox().expand(32),v->v.isAlive()&&v!=except&&v.getLeashHolder()==g))return true;
        for(var p:w.getPlayers())if(p!=except&&PlayerJustice.leadAttached(w,p,g))return true;
        return false;
    }
    static void resetTrail(VillagerEntity guard){TRAILS.remove(guard);}
    static void release(ServerWorld w,Entity prisoner){
        if(w==null)return;
        String pursuit="halloween_pursuit_"+prisoner.getUuidAsString();
        for(var e:w.iterateEntities())if(e instanceof VillagerEntity g){
            if(g.getCommandTags().contains(pursuit)){TRAILS.remove(g);g.removeCommandTag(pursuit);g.getNavigation().stop();g.setAttacking(false);}
        }
    }
    static void register(){ServerTickEvents.END_WORLD_TICK.register(w->{
        if(w.getTime()%20!=0)return;
        for(var e:w.iterateEntities())if(e instanceof VillagerEntity g&&VillageDefense.isGuard(g)){
            if(g.isLeashed()){g.detachLeashWithoutDrop();for(String t:List.copyOf(g.getCommandTags()))if(t.equals("halloween_wanted")||t.equals("halloween_surrender")||t.startsWith("halloween_trial_"))g.removeCommandTag(t);}
            var held=w.getEntitiesByClass(VillagerEntity.class,g.getBoundingBox().expand(32),v->v.isAlive()&&v.getLeashHolder()==g);
            boolean player=w.getPlayers().stream().anyMatch(p->PlayerJustice.leadAttached(w,p,g));
            for(int i=player?0:1;i<held.size();i++){var v=held.get(i);v.detachLeashWithoutDrop();v.removeCommandTag("halloween_surrender");v.addCommandTag("halloween_wanted");for(String t:List.copyOf(v.getCommandTags()))if(t.startsWith("halloween_trial_"))v.removeCommandTag(t);}
            if(!player)g.removeCommandTag("halloween_player_guarding");
            if(held.isEmpty())g.removeCommandTag("halloween_guard_escorting");
        }
    });}
    static void pull(ServerPlayerEntity p,VillagerEntity g){
        if(p.isCreative()||p.isSpectator())return;
        var trail=TRAILS.computeIfAbsent(g,k->new ArrayDeque<>());Vec3d now=g.getEntityPos();
        if(trail.isEmpty())trail.add(p.getEntityPos());
        if(trail.getLast().squaredDistanceTo(now)>.36)trail.add(now);while(trail.size()>160)trail.removeFirst();
        while(trail.size()>1&&p.getEntityPos().squaredDistanceTo(trail.getFirst())<1.2)trail.removeFirst();
        Vec3d goal=trail.getFirst();Vec3d d=goal.subtract(p.getEntityPos());double horizontal=Math.sqrt(d.x*d.x+d.z*d.z);
        if(horizontal<.35){
            if(d.y>.5&&p.isOnGround()&&p.getEntityWorld().isSpaceEmpty(p,p.getBoundingBox().offset(0,1,0))){p.setVelocity(0,.42,0);p.velocityDirty=true;p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(p));}
            return;
        }
        double speed=Math.min(.38,horizontal*.30),vx=d.x/horizontal*speed,vz=d.z/horizontal*speed;
        var w=p.getEntityWorld();var box=p.getBoundingBox();
        if(!w.isSpaceEmpty(p,box.offset(vx,0,vz))){
            if(d.y>.1&&p.isOnGround()&&w.isSpaceEmpty(p,box.offset(0,.6,0))&&w.isSpaceEmpty(p,box.offset(vx,1,vz))){
                p.setVelocity(vx,.42,vz);p.velocityDirty=true;p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(p));return;
            }
            if(w.isSpaceEmpty(p,box.offset(vx,0,0)))vz=0;else if(w.isSpaceEmpty(p,box.offset(0,0,vz)))vx=0;else{g.getNavigation().stop();return;}
        }
        var old=p.getVelocity();p.setVelocity(vx,d.y>.5&&p.isOnGround()?.42:old.y,vz);p.velocityDirty=true;
        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(p));
    }
    private VillageCustody(){}
}
