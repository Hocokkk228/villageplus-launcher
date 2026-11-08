package dev.halloween;

import com.google.common.collect.ImmutableSet;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;

/** Village firefighters use a bucket and remove only water they placed themselves. */
final class VillageFirefighters {
    private static final RegistryKey<VillagerProfession> KEY=RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION,Identifier.of("halloween","firefighter"));
    private static net.minecraft.registry.entry.RegistryEntry<VillagerProfession> profession;
    private static final Map<ServerWorld,Map<BlockPos,Long>> WATER=new WeakHashMap<>();
    static void register(){
        var p=Registry.register(Registries.VILLAGER_PROFESSION,KEY,new VillagerProfession(Text.translatable("entity.halloween.firefighter"),x->false,x->false,ImmutableSet.of(),ImmutableSet.of(),SoundEvents.ENTITY_VILLAGER_AMBIENT));
        profession=Registries.VILLAGER_PROFESSION.getEntry(p);
        ServerTickEvents.END_WORLD_TICK.register(VillageFirefighters::tick);
    }
    private static void tick(ServerWorld w){
        if(!w.getRegistryKey().equals(World.OVERWORLD)||w.getPlayers().isEmpty()||w.getTime()%20!=0)return;
        var water=WATER.computeIfAbsent(w,k->new HashMap<>());
        for(var entry:List.copyOf(water.entrySet()))if(w.getTime()>=entry.getValue()){
            if(w.getBlockState(entry.getKey()).isOf(Blocks.WATER))w.setBlockState(entry.getKey(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);
            water.remove(entry.getKey());
        }
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(var team:memory.teams){
            if(w.getPlayers().stream().noneMatch(p->p.squaredDistanceTo(Vec3d.ofCenter(team.anchor))<128*128))continue;
            String owner="halloween_fire_team_"+Long.toUnsignedString(team.village);
            var workers=w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(128,64,128),v->v.isAlive()&&v.getCommandTags().contains(owner));
            if(workers.size()<2&&w.getTime()%200==0){
                var volunteer=w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(96),v->v.isAlive()&&!v.isBaby()&&v.getVillagerData().profession().matchesKey(VillagerProfession.NONE)&&v.getCommandTags().stream().noneMatch(t->t.startsWith("halloween_"))).stream().findFirst().orElse(null);
                if(volunteer==null){
                    for(int i=0;i<12;i++){
                        int x=team.anchor.getX()+w.random.nextBetween(-12,12),z=team.anchor.getZ()+w.random.nextBetween(-12,12);
                        if(!w.isChunkLoaded(new ChunkPos(new BlockPos(x,team.anchor.getY(),z)).toLong()))continue;
                        BlockPos spot=new BlockPos(x,w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z),z);
                        if(!w.isChunkLoaded(new ChunkPos(spot).toLong())||!w.getBlockState(spot).isAir()||!w.getBlockState(spot.up()).isAir()||!w.getBlockState(spot.down()).isSolidBlock(w,spot.down()))continue;
                        volunteer=net.minecraft.entity.EntityType.VILLAGER.create(w,net.minecraft.entity.SpawnReason.EVENT);if(volunteer==null)break;
                        volunteer.refreshPositionAndAngles(x+.5,spot.getY(),z+.5,0,0);volunteer.initialize(w,w.getLocalDifficulty(spot),net.minecraft.entity.SpawnReason.EVENT,null);
                        if(!w.spawnEntity(volunteer))volunteer=null;break;
                    }
                }
                if(volunteer!=null){volunteer.setVillagerData(volunteer.getVillagerData().withProfession(profession));volunteer.reinitializeBrain(w);volunteer.addCommandTag(owner);volunteer.addCommandTag("halloween_firefighter");volunteer.setPersistent();
                    ItemStack jacket=new ItemStack(Items.LEATHER_CHESTPLATE);jacket.set(net.minecraft.component.DataComponentTypes.DYED_COLOR,new net.minecraft.component.type.DyedColorComponent(0xb52020));volunteer.equipStack(EquipmentSlot.CHEST,jacket);
                    volunteer.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.WATER_BUCKET));workers.add(volunteer);}
            }
            if(w.getTime()%40!=0)continue;
            for(var worker:workers){
                BlockPos fire=null;
                for(BlockPos p:BlockPos.iterateOutwards(worker.getBlockPos(),16,6,16)){
                    if(!w.isChunkLoaded(new ChunkPos(p).toLong()))continue;
                    if(w.getBlockState(p).isOf(Blocks.FIRE)||w.getBlockState(p).isOf(Blocks.SOUL_FIRE)){fire=p.toImmutable();break;}
                }
                if(fire==null)continue;
                if(worker.squaredDistanceTo(Vec3d.ofCenter(fire))>16){worker.getBrain().remember(MemoryModuleType.WALK_TARGET,new WalkTarget(fire,.75f,2),60);worker.getNavigation().startMovingTo(fire.getX()+.5,fire.getY(),fire.getZ()+.5,.75);continue;}
                w.setBlockState(fire,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL|Block.SKIP_DROPS);worker.swingHand(Hand.MAIN_HAND);
                w.playSound(null,fire,SoundEvents.ITEM_BUCKET_EMPTY,net.minecraft.sound.SoundCategory.BLOCKS,1,1);
                BlockPos wet=fire;if(w.getBlockState(wet).isAir()&&w.getOtherEntities(null,new Box(wet),e->e instanceof net.minecraft.entity.LivingEntity).isEmpty()){
                    w.setBlockState(wet,Blocks.WATER.getDefaultState(),Block.NOTIFY_LISTENERS);water.put(wet,w.getTime()+40);
                }
            }
        }
    }
    private VillageFirefighters(){}
}
