package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.particle.DustParticleEffect;

/** Damage-triggered injury effects, panic that outranks work, and delayed gradual recovery. */
public final class VillageInjuries {
    private record Injury(long lastDamage,long bleeding){}
    private record Fear(Vec3d threat,long until){}
    private static final Map<VillagerEntity,Injury> INJURIES=new WeakHashMap<>();
    private static final Map<VillagerEntity,Fear> FEAR=new WeakHashMap<>();
    public static boolean panicking(VillagerEntity person){if(captive(person))return false;var f=FEAR.get(person);return f!=null&&f.until>person.getEntityWorld().getTime();}
    private static boolean captive(VillagerEntity person){return person.isLeashed()||person.getCommandTags().contains("halloween_prisoner")||person.getCommandTags().contains("halloween_court_inside")||person.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_trial_"));}
    static void calm(VillagerEntity person){FEAR.remove(person);person.setSprinting(false);}
    static void frighten(VillagerEntity person,Vec3d threat){
        if(person.getEntityWorld() instanceof ServerWorld world&&VillageFights.owns(world,person))return;
        if(VillageDefense.isGuard(person)||person.hasVehicle()||person.isAiDisabled()||captive(person))return;
        if(person.getCommandTags().contains("halloween_bandit")&&person.getHealth()>person.getMaxHealth()*.35f)return;
        VillageTasks.reset(person);VillageProjects.resetWorker(person);
        if(person.isSleeping())person.wakeUp();
        FEAR.put(person,new Fear(threat,person.getEntityWorld().getTime()+240));
        person.setSprinting(true);
    }
    static void blood(ServerWorld world,VillagerEntity person,int count){
        world.spawnParticles(VillageBlood.SPLASH,person.getX(),person.getY()+1,person.getZ(),count,.23,.38,.23,.13);
    }
    static void impact(ServerWorld world,net.minecraft.entity.LivingEntity victim,boolean ground,float force){
        double y=victim.getY()+(ground?.27:victim.getHeight()*.65);
        int drops=Math.min(32,7+(int)force*3);
        world.spawnParticles(VillageBlood.SPLASH,victim.getX(),y,victim.getZ(),drops,.27,.18,.27,ground?.17:.12);
        world.spawnParticles(new DustParticleEffect(0x601326,.62f),victim.getX(),y,victim.getZ(),drops/2,.24,.16,.24,.07);
        if(ground)world.spawnParticles(net.minecraft.particle.ParticleTypes.POOF,victim.getX(),y-.14,victim.getZ(),7,.31,.05,.31,.05);
        world.playSound(null,victim.getBlockPos(),ground?net.minecraft.sound.SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR
                :net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,net.minecraft.sound.SoundCategory.PLAYERS,
                ground?.67f:.88f,ground?.68f:1f);
    }
    static void register(){
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(!(entity instanceof VillagerEntity victim)||!(victim.getEntityWorld() instanceof ServerWorld world))return;
            blood(world,victim,28);
            if(source.getAttacker()!=null)for(var witness:world.getEntitiesByClass(VillagerEntity.class,victim.getBoundingBox().expand(14,6,14),v->v.isAlive()&&v.canSee(source.getAttacker())))frighten(witness,source.getAttacker().getEntityPos());
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity,source,base,taken,blocked)->{
            if(!(entity instanceof VillagerEntity person)||!(person.getEntityWorld() instanceof ServerWorld world)||taken<=0)return;
            var wounds=FightPose.of(person);
            wounds.halloween$injury(wounds.halloween$injury()+Math.max(1,(int)Math.ceil(taken/2)));
            INJURIES.put(person,new Injury(world.getTime(),world.getTime()+Math.min(200,40+(int)(taken*12))));
            blood(world,person,Math.min(36,9+(int)taken*3));
            world.spawnParticles(new DustParticleEffect(0x5d1426,.65f),person.getX(),person.getY()+.87,person.getZ(),
                    Math.min(16,4+(int)taken),.26,.48,.26,.065);
            if(source.getAttacker()!=null){
                var threat=source.getAttacker();
                frighten(person,threat.getEntityPos());
                for(var witness:world.getEntitiesByClass(VillagerEntity.class,person.getBoundingBox().expand(12,5,12),v->v.isAlive()&&v.canSee(person)))frighten(witness,threat.getEntityPos());
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(world->{
            if(world.getPlayers().isEmpty())return;
            long now=world.getTime();
            for(var entry:List.copyOf(FEAR.entrySet())){
                var v=entry.getKey();if(v.getEntityWorld()!=world)continue;
                if(!v.isAlive()||entry.getValue().until<=now){v.setSprinting(false);FEAR.remove(v);continue;}
                if(now%10!=Math.floorMod(v.getId(),10))continue;
                var goal=net.minecraft.entity.ai.NoPenaltyTargeting.findFrom(v,16,7,entry.getValue().threat);
                if(goal!=null){v.getNavigation().startMovingTo(goal.x,goal.y,goal.z,1.15);v.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(goal,1.15f,0));}
            }
            if(now%20!=0)return;
            for(var entity:world.iterateEntities())if(entity instanceof VillagerEntity v&&v.isAlive()){
                var injury=INJURIES.get(v);
                if(v.getHealth()<v.getMaxHealth()&&injury==null){injury=new Injury(now,now);INJURIES.put(v,injury);}
                if(injury==null)continue;
                recover(world,v,now);
            }
        });
    }
    static void recover(ServerWorld world,VillagerEntity v,long now){
        var injury=INJURIES.get(v);if(injury==null)return;
        if(injury.bleeding>now)blood(world,v,2);
        if(now-injury.lastDamage>=400&&!panicking(v)&&now%100==0){
            v.heal(1);
            FightPose.of(v).halloween$injury(FightPose.of(v).halloween$injury()-1);
        }
        if(v.getHealth()>=v.getMaxHealth()&&FightPose.of(v).halloween$injury()==0)INJURIES.remove(v);
    }
    private VillageInjuries(){}
}
