package dev.halloween;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

/** Per-world blueprints and construction progress, saved with the world. */
final class VillageMemory extends PersistentState {
    record BlockRecord(BlockPos pos, BlockState state) {
        static final Codec<BlockRecord> CODEC=RecordCodecBuilder.create(i->i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(BlockRecord::pos),
                BlockState.CODEC.fieldOf("state").forGetter(BlockRecord::state)).apply(i,BlockRecord::new));
    }
    static final class House {
        static final Codec<House> CODEC=RecordCodecBuilder.create(i->i.group(
                BlockPos.CODEC.fieldOf("min").forGetter(h->h.min),BlockPos.CODEC.fieldOf("max").forGetter(h->h.max),
                BlockRecord.CODEC.listOf().fieldOf("blueprint").forGetter(h->h.blueprint),
                Codec.LONG.fieldOf("observed").forGetter(h->h.observed),
                BlockRecord.CODEC.listOf().optionalFieldOf("work",List.of()).forGetter(h->h.work),
                Codec.INT.optionalFieldOf("work_index",0).forGetter(h->h.workIndex),
                Codec.BOOL.optionalFieldOf("extended",false).forGetter(h->h.extended),
                BlockPos.CODEC.listOf().optionalFieldOf("scaffolds",List.of()).forGetter(h->h.scaffolds),
                Codec.LONG.listOf().optionalFieldOf("captured_chunks",List.of()).forGetter(h->h.capturedChunks),
                BlockPos.CODEC.listOf().optionalFieldOf("repair_scaffolds",List.of()).forGetter(h->h.repairScaffolds),
                Codec.STRING.optionalFieldOf("kind","house").forGetter(h->h.kind),
                Codec.STRING.optionalFieldOf("record_key","").forGetter(h->h.recordKey),
                Codec.LONG.listOf().optionalFieldOf("supplemented_chunks",List.of()).forGetter(h->h.supplementedChunks)
        ).apply(i,House::new));
        final BlockPos min,max;final List<BlockRecord> blueprint;final long observed;
        List<BlockRecord> work;int workIndex;boolean extended;List<BlockPos> scaffolds;List<Long> capturedChunks;List<BlockPos> repairScaffolds;final String kind,recordKey;final List<Long> supplementedChunks;
        House(BlockPos min,BlockPos max,List<BlockRecord> blueprint,long observed,List<BlockRecord> work,int index,boolean extended,List<BlockPos> scaffolds){
            this(min,max,blueprint,observed,work,index,extended,scaffolds,List.of(),List.of(),"house","",List.of());
        }
        House(BlockPos min,BlockPos max,List<BlockRecord> blueprint,long observed,List<BlockRecord> work,int index,boolean extended,List<BlockPos> scaffolds,List<Long> capturedChunks,List<BlockPos> repairScaffolds,String kind,String recordKey,List<Long> supplementedChunks){
            this.min=min;this.max=max;this.blueprint=new ArrayList<>(blueprint);this.observed=observed;this.kind=kind;this.recordKey=recordKey;this.supplementedChunks=new ArrayList<>(supplementedChunks);
            this.work=new ArrayList<>(work);this.workIndex=index;this.extended=extended;this.scaffolds=new ArrayList<>(scaffolds);this.capturedChunks=new ArrayList<>(capturedChunks);this.repairScaffolds=new ArrayList<>(repairScaffolds);
        }
    }
    static final class Revolt {
        static final Codec<Revolt> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.INT.optionalFieldOf("stage",0).forGetter(r->r.stage),Codec.LONG.optionalFieldOf("next_at",0L).forGetter(r->r.nextAt),
            Codec.LONG.optionalFieldOf("started_at",0L).forGetter(r->r.startedAt),BlockPos.CODEC.optionalFieldOf("camp",BlockPos.ORIGIN).forGetter(r->r.camp),
            BlockRecord.CODEC.listOf().optionalFieldOf("plan",List.of()).forGetter(r->r.plan),Codec.INT.optionalFieldOf("index",0).forGetter(r->r.index),
            Codec.STRING.listOf().optionalFieldOf("members",List.of()).forGetter(r->r.members)).apply(i,Revolt::new));
        int stage,index;long nextAt,startedAt;BlockPos camp;final List<BlockRecord> plan;final List<String> members;
        Revolt(){this(0,0,0,BlockPos.ORIGIN,List.of(),0,List.of());}
        Revolt(int stage,long nextAt,long startedAt,BlockPos camp,List<BlockRecord> plan,int index,List<String> members){
            this.stage=stage;this.nextAt=nextAt;this.startedAt=startedAt;this.camp=camp;this.plan=new ArrayList<>(plan);this.index=index;this.members=new ArrayList<>(members);
        }
        Revolt(Revolt r){this(r.stage,r.nextAt,r.startedAt,r.camp,r.plan,r.index,r.members);}
    }
    static final class Team {
        static final Codec<Team> CODEC=RecordCodecBuilder.create(i->i.group(Codec.LONG.fieldOf("village").forGetter(t->t.village),
                BlockPos.CODEC.fieldOf("anchor").forGetter(t->t.anchor),Codec.INT.fieldOf("created").forGetter(t->t.created),
                Codec.LONG.optionalFieldOf("election_at",0L).forGetter(t->t.electionAt),
                Codec.INT.optionalFieldOf("election_stage",0).forGetter(t->t.electionStage),
                Codec.STRING.optionalFieldOf("royal_killer","").forGetter(t->t.royalKiller),
                Codec.STRING.listOf().optionalFieldOf("builders",List.of()).forGetter(t->t.builders),
                Codec.STRING.listOf().optionalFieldOf("patrol_guards",List.of()).forGetter(t->t.patrolGuards),
                Codec.INT.optionalFieldOf("base_houses",0).forGetter(t->t.baseHouses),
                Codec.LONG.optionalFieldOf("next_expansion",0L).forGetter(t->t.nextExpansion),
                BlockPos.CODEC.listOf().optionalFieldOf("expansions",List.of()).forGetter(t->t.expansions),
                BlockPos.CODEC.listOf().optionalFieldOf("finished_expansions",List.of()).forGetter(t->t.finishedExpansions),
                Revolt.CODEC.optionalFieldOf("revolt",new Revolt()).forGetter(t->t.revolt)).apply(i,Team::new));
        final long village;final BlockPos anchor;int created;long electionAt;int electionStage;String royalKiller;final List<String> builders,patrolGuards;int baseHouses;long nextExpansion;final List<BlockPos> expansions,finishedExpansions;final Revolt revolt;
        Team(long village,BlockPos anchor,int created){this(village,anchor,created,0L,0,"",List.of(),List.of(),0,0L,List.of(),List.of(),new Revolt());}
        Team(long village,BlockPos anchor,int created,long electionAt,int electionStage,String royalKiller,List<String> builders,List<String> patrolGuards,int baseHouses,long nextExpansion,List<BlockPos> expansions,List<BlockPos> finishedExpansions,Revolt revolt){this.revolt=new Revolt(revolt);this.village=village;this.anchor=anchor;this.created=created;this.electionAt=electionAt;this.electionStage=electionStage;this.royalKiller=royalKiller;this.builders=new ArrayList<>(builders);this.patrolGuards=new ArrayList<>(patrolGuards);this.baseHouses=baseHouses;this.nextExpansion=nextExpansion;this.expansions=new ArrayList<>(expansions);this.finishedExpansions=new ArrayList<>(finishedExpansions);}
    }
    record FarmRepair(BlockPos pos,BlockState crop){
        static final Codec<FarmRepair> CODEC=RecordCodecBuilder.create(i->i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(FarmRepair::pos),BlockState.CODEC.fieldOf("crop").forGetter(FarmRepair::crop)).apply(i,FarmRepair::new));
    }
    static final Codec<VillageMemory> CODEC=RecordCodecBuilder.create(i->i.group(
            House.CODEC.listOf().optionalFieldOf("houses",List.of()).forGetter(m->m.houses),
            Team.CODEC.listOf().optionalFieldOf("teams",List.of()).forGetter(m->m.teams),
            FarmRepair.CODEC.listOf().optionalFieldOf("farms",List.of()).forGetter(m->m.farms)).apply(i,VillageMemory::new));
    static final PersistentStateType<VillageMemory> TYPE=new PersistentStateType<>("halloween_village_builders",VillageMemory::new,CODEC,null);
    final List<House> houses;
    final List<Team> teams;
    final List<FarmRepair> farms;
    VillageMemory(){this(List.of(),List.of(),List.of());}
    VillageMemory(List<House> houses){this(houses,List.of(),List.of());}
    VillageMemory(List<House> houses,List<Team> teams,List<FarmRepair> farms){this.houses=new ArrayList<>(houses);this.teams=new ArrayList<>(teams);this.farms=new ArrayList<>(farms);}
    boolean contains(BlockPos min){return houses.stream().anyMatch(h->h.min.equals(min));}
}
