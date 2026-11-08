package dev.halloween;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Three distinct couples per active village and night; progress survives reloads. */
final class VillageBirths {
    private static final class Night {
        static final Codec<Night> CODEC=RecordCodecBuilder.create(i->i.group(Codec.LONG.fieldOf("village").forGetter(n->n.village),
            Codec.LONG.fieldOf("night").forGetter(n->n.night),Codec.INT.fieldOf("births").forGetter(n->n.births),
            Codec.STRING.listOf().fieldOf("parents").forGetter(n->n.parents)).apply(i,Night::new));
        final long village;long night;int births;final List<String> parents;
        Night(long village,long night,int births,List<String> parents){this.village=village;this.night=night;this.births=births;this.parents=new ArrayList<>(parents);}
    }
    private static final class State extends PersistentState {
        static final Codec<State> CODEC=Night.CODEC.listOf().xmap(State::new,s->s.nights);
        static final PersistentStateType<State> TYPE=new PersistentStateType<>("halloween_village_births",()->new State(List.of()),CODEC,null);
        final List<Night> nights;State(List<Night> nights){this.nights=new ArrayList<>(nights);}
    }
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillageBirths::tick);}
    private static void tick(ServerWorld w){
        long time=w.getTimeOfDay(),hour=Math.floorMod(time,24000);
        if(!w.getRegistryKey().equals(World.OVERWORLD)||w.getPlayers().isEmpty()||w.getTime()%100!=0||hour<13000||hour>=23000)return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var state=w.getPersistentStateManager().getOrCreate(State.TYPE);
        for(var team:memory.teams){
            if(w.getPlayers().stream().noneMatch(p->p.squaredDistanceTo(Vec3d.ofCenter(team.anchor))<192*192
                ||team.finishedExpansions.stream().anyMatch(h->p.squaredDistanceTo(Vec3d.ofCenter(h))<128*128)))continue;
            Night night=state.nights.stream().filter(n->n.village==team.village).findFirst().orElse(null);
            if(night==null){night=new Night(team.village,Math.floorDiv(time,24000),0,List.of());state.nights.add(night);state.markDirty();}
            long currentNight=Math.floorDiv(time,24000);if(night.night!=currentNight){night.night=currentNight;night.births=0;night.parents.clear();state.markDirty();}
            if(night.births>=3)continue;final Night current=night;
            List<VillagerEntity> adults=new ArrayList<>();
            for(var entity:w.iterateEntities())if(entity instanceof VillagerEntity v&&v.isAlive()&&!v.isBaby()&&v.getBreedingAge()<=0&&!v.isAiDisabled()
                &&!VillageDefense.isGuard(v)&&!RoyalProfession.isKing(v)&&!v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)
                &&!v.getCommandTags().contains("halloween_bandit")&&!v.getCommandTags().contains("halloween_rebel")&&!v.getCommandTags().contains("halloween_prisoner")
                &&!v.getCommandTags().contains("halloween_surrender")&&!v.getCommandTags().contains("halloween_fighter")&&!current.parents.contains(v.getUuidAsString())){
                var nearest=memory.teams.stream().min(Comparator.comparingDouble(t->v.squaredDistanceTo(Vec3d.ofCenter(t.anchor)))).orElse(null);
                if(nearest==team&&(v.squaredDistanceTo(Vec3d.ofCenter(team.anchor))<192*192||team.finishedExpansions.stream().anyMatch(h->v.squaredDistanceTo(Vec3d.ofCenter(h))<96*96)))adults.add(v);
            }
            if(adults.size()<2)continue;
            adults.sort(Comparator.comparing(VillagerEntity::getUuid));
            Collections.rotate(adults,-(int)Math.floorMod(w.getTime()/100,adults.size()));VillagerEntity first=adults.getFirst();
            VillagerEntity second=adults.stream().filter(v->v!=first).min(Comparator.comparingDouble(first::squaredDistanceTo)).orElse(null);if(second==null)continue;
            BlockPos birth=null;
            for(BlockPos p:BlockPos.iterateOutwards(first.getBlockPos(),6,2,6))if(w.isChunkLoaded(new ChunkPos(p).toLong())&&w.getBlockState(p).isAir()&&w.getBlockState(p.up()).isAir()
                &&w.getBlockState(p.down()).isSolidBlock(w,p.down())&&w.getOtherEntities(null,new Box(p),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty()){birth=p.toImmutable();break;}
            if(birth==null)continue;VillagerEntity child=first.createChild(w,second);if(child==null)continue;
            child.setBreedingAge(-24000);child.refreshPositionAndAngles(birth.getX()+.5,birth.getY(),birth.getZ()+.5,0,0);child.setPersistent();
            if(!w.spawnEntity(child))continue;
            first.setBreedingAge(6000);second.setBreedingAge(6000);night.parents.add(first.getUuidAsString());night.parents.add(second.getUuidAsString());night.births++;state.markDirty();
            w.spawnParticles(ParticleTypes.HEART,first.getX(),first.getY()+1.5,first.getZ(),5,.4,.4,.4,.01);
            w.spawnParticles(ParticleTypes.HEART,second.getX(),second.getY()+1.5,second.getZ(),5,.4,.4,.4,.01);
        }
    }
    private VillageBirths(){}
}
