package dev.halloween;

import com.google.common.collect.ImmutableSet;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.village.VillagerProfession;

/** Daytime street maintenance by existing unemployed residents. */
final class VillageJanitors {
    static final RegistryKey<VillagerProfession> KEY=RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,Identifier.of("halloween","janitor"));
    private static RegistryEntry<VillagerProfession> entry;
    private static final Map<UUID,BlockPos> TARGET=new HashMap<>();
    static void register(){
        var profession=Registry.register(Registries.VILLAGER_PROFESSION,KEY,new VillagerProfession(Text.translatable("entity.minecraft.villager.halloween.janitor"),e->false,e->false,ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_WORK_MASON));
        entry=Registries.VILLAGER_PROFESSION.getEntry(profession);ServerTickEvents.END_WORLD_TICK.register(VillageJanitors::tick);
    }
    private static boolean task(ServerWorld w,BlockPos p){
        var state=w.getBlockState(p);String id=Registries.BLOCK.getId(state.getBlock()).getPath();
        var expected=VillageProjects.get(w).expected.get(p);
        return state.isOf(Blocks.VINE)||id.equals("leaf_litter")&&w.getBlockState(p.down()).isOf(Blocks.DIRT_PATH)||expected!=null&&expected.isOf(Blocks.DIRT_PATH)&&state.isOf(Blocks.DIRT);
    }
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty()||w.getTime()%40!=0)return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(var team:List.copyOf(memory.teams)){
            if((VillageEraData.get(w).settlement(team).era<2&&VillageEraData.get(w).settlement(team).transitionAt<0)||w.getPlayers().stream().noneMatch(p->p.getBlockPos().isWithinDistance(team.anchor,320)))continue;
            var people=w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(320,80,320),v->v.isAlive()&&!v.isBaby()&&VillageEras.belongs(w,v,team.village));
            var workers=new ArrayList<>(people.stream().filter(v->v.getVillagerData().profession().matchesKey(KEY)).toList());
            if(workers.size()<10&&w.getTime()%200==0){
                var candidate=people.stream().filter(v->(VillageEras.profession(v).equals("none")||VillageEras.profession(v).equals("nitwit"))&&!VillageTrials.busy(v)).findFirst().orElse(null);
                if(candidate==null){
                    Set<String> civilian=Set.of("mason","fletcher","shepherd","butcher","leatherworker","librarian","cartographer","fisherman");
                    Map<String,Long> counts=new HashMap<>();for(var person:people)counts.merge(VillageEras.profession(person),1L,Long::sum);
                    candidate=people.stream().filter(v->civilian.contains(VillageEras.profession(v))&&counts.getOrDefault(VillageEras.profession(v),0L)>1&&!VillageTrials.busy(v)&&!v.getCommandTags().contains("halloween_prisoner")&&!v.getCommandTags().contains("halloween_bandit")).findFirst().orElse(null);
                }
                if(candidate!=null){candidate.setVillagerData(candidate.getVillagerData().withProfession(entry));candidate.setExperience(1);candidate.reinitializeBrain(w);candidate.addCommandTag("halloween_janitor");candidate.setCustomName(Text.literal("Дворник"));workers.add(candidate);}
            }
            if(!VillageBuilders.workHours(w))continue;
            for(var v:workers){
                if(v.isSleeping()||VillageTrials.busy(v))continue;
                BlockPos target=TARGET.get(v.getUuid());
                if(target==null||!w.isChunkLoaded(new ChunkPos(target).toLong())||!task(w,target)){
                    target=null;
                    for(int i=0;i<100;i++){
                        BlockPos p=v.getBlockPos().add(w.random.nextBetween(-12,12),w.random.nextBetween(-2,5),w.random.nextBetween(-12,12));
                        if(w.isChunkLoaded(new ChunkPos(p).toLong())&&task(w,p)){target=p.toImmutable();break;}
                    }
                    if(target==null){TARGET.remove(v.getUuid());continue;}TARGET.put(v.getUuid(),target);
                }
                if(v.squaredDistanceTo(Vec3d.ofCenter(target))>9){VillageProjects.approach(w,v,target);continue;}
                var old=w.getBlockState(target);String name=Registries.BLOCK.getId(old.getBlock()).getPath();
                if(name.equals("leaf_litter")){
                    BlockPos destination=null;
                    for(BlockPos q:BlockPos.iterateOutwards(target,8,2,8))if(w.isChunkLoaded(new ChunkPos(q).toLong())&&w.getBlockState(q).isAir()&&w.getBlockState(q.down()).isOf(Blocks.GRASS_BLOCK)&&old.canPlaceAt(w,q)){destination=q.toImmutable();break;}
                    if(destination==null){TARGET.remove(v.getUuid());continue;}
                    if(!w.setBlockState(destination,old,Block.NOTIFY_ALL))continue;
                }
                var replacement=old.isOf(Blocks.DIRT)?Blocks.DIRT_PATH.getDefaultState():Blocks.AIR.getDefaultState();
                w.setBlockState(target,replacement,Block.NOTIFY_ALL);boolean empty=v.getMainHandStack().isEmpty();if(empty)v.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.BRUSH));v.swingHand(Hand.MAIN_HAND);if(empty)v.equipStack(EquipmentSlot.MAINHAND,ItemStack.EMPTY);TARGET.remove(v.getUuid());
            }
        }
    }
    private VillageJanitors(){}
}
