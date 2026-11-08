package dev.halloween;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.*;

/** Saved rosters retain unloaded residents and replace deaths without counting unloaded chunks as losses. */
final class VillagePopulation {
    private static final List<String> PROFESSIONS=List.of("none","farmer","fisherman","shepherd","fletcher","librarian","cartographer","cleric","armorer","weaponsmith","toolsmith","butcher","leatherworker","mason");
    private static final class Group {
        static final Codec<Group> CODEC=RecordCodecBuilder.create(i->i.group(Codec.LONG.fieldOf("village").forGetter(g->g.village),
            Codec.STRING.fieldOf("role").forGetter(g->g.role),Codec.INT.fieldOf("baseline").forGetter(g->g.baseline),
            Codec.STRING.listOf().fieldOf("members").forGetter(g->g.members),Codec.LONG.optionalFieldOf("next_at",0L).forGetter(g->g.nextAt)).apply(i,Group::new));
        final long village;final String role;int baseline;final List<String> members;long nextAt;
        Group(long village,String role,int baseline,List<String> members,long nextAt){this.village=village;this.role=role;this.baseline=baseline;this.members=new ArrayList<>(members);this.nextAt=nextAt;}
    }
    private static final class State extends PersistentState {
        static final Codec<State> CODEC=Group.CODEC.listOf().xmap(State::new,s->s.groups);
        static final PersistentStateType<State> TYPE=new PersistentStateType<>("halloween_population",()->new State(List.of()),CODEC,null);
        final List<Group> groups;State(List<Group> groups){this.groups=new ArrayList<>(groups);}
    }
    static void register(){
        ServerTickEvents.END_WORLD_TICK.register(VillagePopulation::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((v,source)->{if(v.getEntityWorld() instanceof ServerWorld w)remove(w,v.getUuidAsString());});
        ServerLivingEntityEvents.MOB_CONVERSION.register((old,converted,context)->{if(old.getEntityWorld() instanceof ServerWorld w)remove(w,old.getUuidAsString());});
    }
    private static void remove(ServerWorld w,String id){
        State s=w.getPersistentStateManager().getOrCreate(State.TYPE);
        for(Group g:s.groups)if(g.members.remove(id)){g.nextAt=w.getTime()+200;s.markDirty();}
    }
    private static boolean matches(VillagerEntity v,String role){
        if(role.equals("killer"))return v.getCommandTags().contains("halloween_bandit")&&!v.getCommandTags().contains("halloween_thief");
        if(role.equals("thief"))return v.getCommandTags().contains("halloween_bandit")&&v.getCommandTags().contains("halloween_thief");
        return !v.getCommandTags().contains("halloween_bandit")&&v.getVillagerData().profession().matchesKey(RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,Identifier.of("minecraft",role)));
    }
    private static void tick(ServerWorld w){
        if(!w.getRegistryKey().equals(World.OVERWORLD)||w.getPlayers().isEmpty()||w.getTime()%100!=0)return;
        State s=w.getPersistentStateManager().getOrCreate(State.TYPE);var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(var team:memory.teams){
            if(w.getPlayers().stream().noneMatch(p->p.squaredDistanceTo(Vec3d.ofCenter(team.anchor))<384*384))continue;
            String owner="halloween_population_team_"+Long.toUnsignedString(team.village);int bonus=team.finishedExpansions.size()/10;
            var residents=w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(VillageEras.radius(w,team.anchor),96,VillageEras.radius(w,team.anchor)),v->v.isAlive()&&!v.isBaby());
            long civilians=residents.stream().filter(v->!RoyalProfession.isKing(v)&&!VillageDefense.isGuard(v)
                &&!v.getVillagerData().profession().matchesKey(BuilderProfession.KEY)&&!v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)
                &&!v.getCommandTags().contains("halloween_bandit")&&!v.getCommandTags().contains("halloween_rebel")&&!v.getCommandTags().contains("halloween_firefighter")).count();
            int populationCap=Math.min(240,Math.max(48,24+team.baseHouses*2+team.finishedExpansions.size()*2));
            if(residents.size()>=populationCap)continue;
            if(civilians<12&&w.getTime()%200==0){var newcomer=spawn(w,team.anchor);if(newcomer!=null){newcomer.setPersistent();newcomer.addCommandTag(owner);residents.add(newcomer);}}
            List<String> roles=new ArrayList<>(PROFESSIONS);roles.add("killer");roles.add("thief");
            boolean replaced=false;
            for(String role:roles){
                boolean criminal=role.equals("killer")||role.equals("thief");
                Group group=s.groups.stream().filter(g->g.village==team.village&&g.role.equals(role)).findFirst().orElse(null);
                if(group==null){group=new Group(team.village,role,criminal?2:0,List.of(),w.getTime()+200);s.groups.add(group);s.markDirty();}
                if(criminal&&group.baseline<2){group.baseline=2;group.nextAt=Math.min(group.nextAt,w.getTime()+600);s.markDirty();}
                for(String id:List.copyOf(group.members))try{
                    Entity entity=w.getEntity(UUID.fromString(id));
                    if(entity!=null&&(!(entity instanceof VillagerEntity v)||!v.isAlive()||!matches(v,role))){group.members.remove(id);s.markDirty();}
                }catch(IllegalArgumentException ex){group.members.remove(id);s.markDirty();}
                for(var v:residents)if(matches(v,role)&&!group.members.contains(v.getUuidAsString())
                    &&v.getCommandTags().stream().noneMatch(t->t.startsWith("halloween_population_team_")&&!t.equals(owner))){
                    var nearest=memory.teams.stream().min(Comparator.comparingDouble(t->v.squaredDistanceTo(Vec3d.ofCenter(t.anchor)))).orElse(team);
                    if(nearest!=team)continue;group.members.add(v.getUuidAsString());v.addCommandTag(owner);s.markDirty();
                }
                if(!criminal&&group.members.size()-bonus>group.baseline){group.baseline=group.members.size()-bonus;s.markDirty();}
                if(role.equals("none"))group.baseline=0;
                int target=role.equals("none")?0:group.baseline+(role.equals("thief")?0:bonus);
                if(criminal&&VillageIndustrial.civic(VillageEraData.get(w).settlements.get(team.village)))
                    target=Math.max(target,role.equals("killer")?12:3);
                if(!replaced&&role.equals("killer")&&group.members.size()==target&&w.getTime()>=group.nextAt&&w.random.nextInt(8)==0){
                    var turn=residents.stream().filter(v->!RoyalProfession.isKing(v)&&!v.getCommandTags().contains("halloween_guard")
                        &&!v.getCommandTags().contains("halloween_bandit")&&!v.getCommandTags().contains("halloween_rebel")&&!v.getCommandTags().contains("halloween_fighter")
                        &&!v.getVillagerData().profession().matchesKey(BuilderProfession.KEY)&&!v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)).findFirst().orElse(null);
                    if(turn!=null){
                        for(Group old:s.groups)old.members.remove(turn.getUuidAsString());turn.addCommandTag("halloween_bandit");turn.addCommandTag(owner);
                        group.members.add(turn.getUuidAsString());group.nextAt=w.getTime()+2400;s.markDirty();replaced=true;
                    }
                }
                if(replaced||group.members.size()>=target||w.getTime()<group.nextAt)continue;
                VillagerEntity candidate=null;
                // A civilian occasionally turns to murder; otherwise outsiders fill criminal vacancies.
                if(role.equals("killer")&&w.random.nextInt(4)==0)candidate=residents.stream().filter(v->!RoyalProfession.isKing(v)
                    &&!v.getCommandTags().contains("halloween_guard")&&!v.getCommandTags().contains("halloween_bandit")&&!v.getCommandTags().contains("halloween_rebel")
                    &&!v.getCommandTags().contains("halloween_fighter")&&!v.getVillagerData().profession().matchesKey(BuilderProfession.KEY)
                    &&!v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)).findFirst().orElse(null);
                if(!criminal&&!role.equals("none"))candidate=residents.stream().filter(v->v.getVillagerData().profession().matchesKey(VillagerProfession.NONE)&&!Oprichniki.isOfficer(v)
                    &&!v.getCommandTags().contains("halloween_bandit")&&!v.getCommandTags().contains("halloween_guard")&&!v.getCommandTags().contains("halloween_rebel")).findFirst().orElse(null);
                // Ordinary vacancies wait for a completed workplace; avoid spawning replacements
                // for every profession whenever a villager changes jobs.
                if(!criminal&&(candidate==null||!VillageEconomy.station(w,candidate,role)))continue;
                if(candidate==null)candidate=spawn(w,team.anchor);
                if(candidate==null)continue;
                for(Group old:s.groups)if(old!=group)old.members.remove(candidate.getUuidAsString());
                if(criminal){
                    candidate.addCommandTag("halloween_bandit");if(role.equals("thief"))candidate.addCommandTag("halloween_thief");
                }else{
                    var profession=Registries.VILLAGER_PROFESSION.get(Identifier.of("minecraft",role));if(profession==null)continue;
                    candidate.setVillagerData(candidate.getVillagerData().withProfession(Registries.VILLAGER_PROFESSION.getEntry(profession)));
                    var site=candidate.getBrain().getOptionalRegisteredMemory(net.minecraft.entity.ai.brain.MemoryModuleType.JOB_SITE);
                    candidate.setExperience(1);candidate.reinitializeBrain(w);if(site.isPresent())candidate.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.JOB_SITE,site.get());
                }
                candidate.setPersistent();candidate.addCommandTag(owner);group.members.add(candidate.getUuidAsString());group.nextAt=w.getTime()+(criminal?600:1200);s.markDirty();replaced=true;
            }
        }
    }
    private static VillagerEntity spawn(ServerWorld w,BlockPos anchor){
        for(int n=0;n<24;n++){
            int x=anchor.getX()+w.random.nextBetween(-18,18),z=anchor.getZ()+w.random.nextBetween(-18,18);
            if(!w.isChunkLoaded(new ChunkPos(new BlockPos(x,anchor.getY(),z)).toLong()))continue;
            BlockPos p=new BlockPos(x,w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z),z);
            if(Math.abs(p.getY()-anchor.getY())>8||!w.getBlockState(p).isAir()||!w.getBlockState(p.up()).isAir()||!w.getBlockState(p.down()).isSolidBlock(w,p.down())
                ||!w.getOtherEntities(null,new Box(p),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty())continue;
            var v=EntityType.VILLAGER.create(w,SpawnReason.STRUCTURE);if(v==null)return null;
            v.refreshPositionAndAngles(x+.5,p.getY(),z+.5,0,0);v.initialize(w,w.getLocalDifficulty(p),SpawnReason.STRUCTURE,null);
            return w.spawnEntity(v)?v:null;
        }
        return null;
    }
    private VillagePopulation(){}
}
