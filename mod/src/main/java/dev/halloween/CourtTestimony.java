package dev.halloween;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

final class CourtTestimony {
    private static final String EVIDENCE="halloween_court_evidence_", SPOKE="halloween_hearing_spoke_";
    private static final String OPEN="halloween_hearing_open", REPLY="halloween_hearing_reply";
    private record Statement(VillagerEntity witness, boolean defense) {}

    static void observe(ServerWorld world, Entity accused, LivingEntity victim, BlockPos scene, boolean defense) {
        var era = VillageEras.settlement(world,scene);
        if (!VillageIndustrial.civic(era)) return;
        var candidates=world.getEntitiesByClass(VillagerEntity.class,new Box(scene).expand(16),v->
                v!=accused&&v!=victim&&eligible(v)&&v.canSee(accused)&&(victim==null||v.canSee(victim)));
        candidates.sort(Comparator.comparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(scene))));
        for (var witness : candidates.stream().limit(2).toList()) {
            String id=witness.getUuidAsString();
            if (accused.getCommandTags().stream().filter(tag->tag.startsWith(EVIDENCE)).count()>=12) break;
            accused.addCommandTag(EVIDENCE+(defense?"d:":"p:")+id);
        }
    }

    private static boolean eligible(VillagerEntity v) {
        return v.isAlive()&&!v.isBaby()&&!v.isAiDisabled()&&!v.isSleeping()
                &&!VillageDefense.isGuard(v)&&!RoyalProfession.isKing(v)
                &&!v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)
                &&!v.getCommandTags().contains("halloween_prisoner");
    }

    private static List<Statement> statements(ServerWorld world, Entity accused) {
        var result=new ArrayList<Statement>();
        var tags=accused.getCommandTags().stream().filter(t->t.startsWith(EVIDENCE)).sorted().toList();
        for (var tag : tags) {
            String data=tag.substring(EVIDENCE.length());
            if(data.length()<3)continue;
            try {
                var entity=world.getEntity(UUID.fromString(data.substring(2)));
                if(entity instanceof VillagerEntity witness&&eligible(witness)
                        &&result.stream().noneMatch(s->s.witness==witness)) result.add(new Statement(witness,data.startsWith("d:")));
            } catch(IllegalArgumentException ignored) {}
        }
        // Prefer one witness per side when both have been recorded.
        var selected=new ArrayList<Statement>();
        for(boolean side:new boolean[]{false,true})result.stream().filter(s->s.defense==side).findFirst().ifPresent(selected::add);
        for(var statement:result)if(selected.size()<2&&!selected.contains(statement))selected.add(statement);
        return selected;
    }

    static void guide(ServerWorld world, MarkerEntity square, Entity accused) {
        var hall=VillageCourthouse.hall(world,square);
        if (hall==null || world.getTime()%20!=0) return;
        var statements=statements(world,accused);
        for (int i=0;i<statements.size();i++) {
            var witness=statements.get(i).witness();
            var post=VillageCourthouse.witness(hall,i);
            if (witness.squaredDistanceTo(Vec3d.ofBottomCenter(post))>128*128) continue;
            VillageTasks.holdCourt(witness);
            if(witness.squaredDistanceTo(Vec3d.ofBottomCenter(post))>1.3)
                VillageEscort.move(witness,post,.6f);
            else {
                witness.getNavigation().stop();
                var cage=VillageCourthouse.cage(hall);
                witness.getLookControl().lookAt(cage.getX()+.5,cage.getY()+1,cage.getZ()+.5);
            }
        }
    }

    static void speak(ServerWorld world, MarkerEntity square, Entity accused, long remaining, String charge) {
        var hall=VillageCourthouse.hall(world,square);
        if (hall==null) return;
        if(accused.addCommandTag(OPEN)) announce(world,hall,"§6Судья: Заседание открыто. Обвинение — "+charge+".");
        if(remaining<=240&&accused.addCommandTag(REPLY)){
            if(accused instanceof ServerPlayerEntity p)p.sendMessage(Text.literal("§eСудья: Подсудимый, можете дать объяснение в чате."),false);
            else announce(world,hall,"§7"+accused.getName().getString()+": Прошу выслушать свидетелей, прежде чем выносить решение.");
        }
        if(remaining>190)return;
        var statements=statements(world,accused);
        for(int i=0;i<statements.size();i++){
            var statement=statements.get(i);
            if(remaining>190-i*65||statement.witness.squaredDistanceTo(Vec3d.ofBottomCenter(VillageCourthouse.witness(hall,i)))>4)continue;
            String tag=SPOKE+(statement.defense?"d:":"p:")+statement.witness.getUuidAsString();
            if(accused.addCommandTag(tag)) announce(world,hall,"§f"+statement.witness.getName().getString()+": "
                    +(statement.defense?"Я видел, что потерпевший тоже вступил в драку. Это могло быть самообороной."
                    :"Я был на месте и видел действия подсудимого."));
        }
    }

    static boolean acquit(Entity accused) {
        int balance=0;
        for(var tag:accused.getCommandTags()){
            if(tag.startsWith(SPOKE+"p:"))balance++;
            else if(tag.startsWith(SPOKE+"d:"))balance--;
        }
        return balance<=0;
    }

    static void reset(Entity accused, boolean evidence) {
        for(var tag:List.copyOf(accused.getCommandTags()))
            if(tag.equals(OPEN)||tag.equals(REPLY)||tag.startsWith(SPOKE)||evidence&&tag.startsWith(EVIDENCE))accused.removeCommandTag(tag);
    }

    static void announce(ServerWorld world,BlockPos hall,String line) {
        for(var listener:world.getPlayers())if(listener.squaredDistanceTo(Vec3d.ofCenter(hall))<=64*64)
            listener.sendMessage(Text.literal(line),false);
    }

    private CourtTestimony() {}
}
