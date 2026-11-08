package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.village.VillagerProfession;

/** Village fighting-ring prototype. Matches are only active while the arena is loaded. */
final class VillageArena {
    private static final String FIGHTER="halloween_fighter";
    private record Match(UUID arena,UUID first,UUID second,long start){}
    private static final Set<UUID> ANNOUNCED=new HashSet<>();
    private static final Map<ServerWorld,Map<UUID,Match>> MATCHES=new WeakHashMap<>();
    static void register(){
        ServerTickEvents.END_WORLD_TICK.register(VillageArena::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((dead,source)->{
            if(!(dead instanceof VillagerEntity loser)||!(loser.getEntityWorld() instanceof ServerWorld w))return;
            var matches=MATCHES.get(w);if(matches==null)return;
            for(Match match:List.copyOf(matches.values())){
                if(!loser.getUuid().equals(match.first())&&!loser.getUuid().equals(match.second()))continue;
                UUID winnerId=loser.getUuid().equals(match.first())?match.second():match.first();
                if(source.getAttacker()==null||!winnerId.equals(source.getAttacker().getUuid())){matches.remove(match.arena());ANNOUNCED.remove(match.arena());return;}
                if(w.getEntity(winnerId) instanceof VillagerEntity winner){
                    winner.removeCommandTag(FIGHTER);winner.addCommandTag("halloween_wealthy");winner.getNavigation().stop();
                    var mansion=w.getEntitiesByClass(MarkerEntity.class,new Box(winner.getBlockPos()).expand(128),m->m.getCommandTags().contains("halloween_mansion_bed")
                        &&m.getCommandTags().stream().noneMatch(t->t.startsWith("halloween_owner_")))
                        .stream().min(Comparator.comparingDouble(winner::squaredDistanceTo)).orElse(null);
                    if(mansion!=null && w.getBlockState(mansion.getBlockPos()).getBlock() instanceof net.minecraft.block.BedBlock){
                        mansion.addCommandTag("halloween_owner_"+winner.getUuidAsString());
                        winner.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.HOME,net.minecraft.util.math.GlobalPos.create(w.getRegistryKey(),mansion.getBlockPos()));
                        winner.getNavigation().startMovingTo(mansion.getX(),mansion.getY(),mansion.getZ(),.55);
                    }
                    var guard=w.getEntitiesByClass(VillagerEntity.class,new Box(winner.getBlockPos()).expand(48),v->v.getCommandTags().contains("halloween_guard"))
                        .stream().min(Comparator.comparingDouble(winner::squaredDistanceTo)).orElse(null);
                    if(guard!=null)guard.dropStack(w,new ItemStack(Items.EMERALD,64));else winner.dropStack(w,new ItemStack(Items.EMERALD,64));
                    celebrate(w,winner);
                }
                matches.remove(match.arena());ANNOUNCED.remove(match.arena());return;
            }
        });
    }
    static boolean startNear(ServerWorld w,net.minecraft.entity.player.PlayerEntity player){
        var square=w.getEntitiesByClass(MarkerEntity.class,new Box(player.getBlockPos()).expand(128),m->m.getCommandTags().contains("halloween_square"))
            .stream().min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
        return square!=null&&startAt(w,square);
    }
    private static boolean startAt(ServerWorld w,MarkerEntity square){
        if (closed(w,square)) return false;
        var matches=MATCHES.computeIfAbsent(w,k->new HashMap<>());if(matches.containsKey(square.getUuid()))return false;
        var candidates=w.getEntitiesByClass(VillagerEntity.class,new Box(square.getBlockPos()).expand(80,32,80),v->v.isAlive()&&!v.isBaby()&&!v.isAiDisabled()
            &&!v.getCommandTags().contains("halloween_bandit")&&!v.getCommandTags().contains("halloween_guard")&&!v.getCommandTags().contains(FIGHTER)
            &&!RoyalProfession.isKing(v)&&!v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY));
        if(candidates.size()<2)return false;
        candidates.sort(Comparator.comparingDouble(v->v.squaredDistanceTo(square)));
        VillagerEntity a=candidates.get(0),b=candidates.get(1);a.addCommandTag(FIGHTER);b.addCommandTag(FIGHTER);
        matches.put(square.getUuid(),new Match(square.getUuid(),a.getUuid(),b.getUuid(),w.getTime()));
        for(var p:w.getPlayers())p.sendMessage(net.minecraft.text.Text.translatable("message.halloween.fight_start"),false);
        return true;
    }
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty()||w.getTime()%20!=0)return;
        var matches=MATCHES.computeIfAbsent(w,k->new HashMap<>());Set<UUID> seen=new HashSet<>();
        for(var player:w.getPlayers())for(var square:w.getEntitiesByClass(MarkerEntity.class,new Box(player.getBlockPos()).expand(128),m->m.getCommandTags().contains("halloween_square"))){
            if(!seen.add(square.getUuid()))continue;
            Match match=matches.get(square.getUuid());
            if(closed(w,square)){
                if(match!=null){
                    for(UUID id:List.of(match.first(),match.second()))if(w.getEntity(id) instanceof VillagerEntity fighter){
                        fighter.removeCommandTag(FIGHTER);fighter.getNavigation().stop();
                        fighter.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);
                    }
                    matches.remove(square.getUuid());ANNOUNCED.remove(square.getUuid());
                }
                continue;
            }
            if(match==null && w.getTime()%72000==0){startAt(w,square);match=matches.get(square.getUuid());}
            if(match==null)continue;
            if(!(w.getEntity(match.first()) instanceof VillagerEntity a)||!(w.getEntity(match.second()) instanceof VillagerEntity b))continue;
            long elapsed=w.getTime()-match.start();
            if(elapsed>3600){a.removeCommandTag(FIGHTER);b.removeCommandTag(FIGHTER);matches.remove(square.getUuid());ANNOUNCED.remove(square.getUuid());continue;}
            if(elapsed<600){
                var spectators=w.getEntitiesByClass(VillagerEntity.class,new Box(square.getBlockPos()).expand(96,40,96),v->v.isAlive()&&v!=a&&v!=b
                    &&!v.isAiDisabled()&&!v.getCommandTags().contains("halloween_bandit"));
                var seats=w.getEntitiesByClass(MarkerEntity.class,new Box(square.getBlockPos()).expand(64),m->m.getCommandTags().contains("halloween_tribune"));
                seats.sort(Comparator.comparing(MarkerEntity::getUuid));spectators.sort(Comparator.comparing(VillagerEntity::getUuid));
                for(int i=0;i<spectators.size();i++){
                    var v=spectators.get(i);if(v.isSleeping())v.wakeUp();
                    var spot=seats.isEmpty()?square:seats.get(i%seats.size());
                    if(v.squaredDistanceTo(spot)>9){v.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(spot.getBlockPos(),.5f,1),60);
                        if(v.getNavigation().isIdle()||v.age%40==0)v.getNavigation().startMovingTo(spot.getX(),spot.getY(),spot.getZ(),.5);}
                }
                guide(a,square.getX()-3,square.getY(),square.getZ());guide(b,square.getX()+3,square.getY(),square.getZ());
                if(elapsed>=540){int number=3-(int)((elapsed-540)/20);
                    w.playSound(null,square.getBlockPos(),net.minecraft.sound.SoundEvents.BLOCK_BELL_USE,net.minecraft.sound.SoundCategory.BLOCKS,2f,1f);
                    for(var p:w.getPlayers())p.sendMessage(net.minecraft.text.Text.literal("§6"+number+"..."),true);}
                continue;
            }
            if(ANNOUNCED.add(square.getUuid())){
                w.playSound(null,square.getBlockPos(),net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),net.minecraft.sound.SoundCategory.BLOCKS,3f,0.8f);
                for(var p:w.getPlayers())p.sendMessage(net.minecraft.text.Text.literal("§cБАХ! Бой начался!"),true);
            }
            if(a.squaredDistanceTo(square)>49)bringToArena(w,a,square,-3);
            if(b.squaredDistanceTo(square)>49)bringToArena(w,b,square,3);
            if(a.squaredDistanceTo(square)>49||b.squaredDistanceTo(square)>49)continue;
            if(a.squaredDistanceTo(b)>4){a.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(b,.6f,1),60); b.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(a,.6f,1),60); a.getNavigation().startMovingTo(b,.6);b.getNavigation().startMovingTo(a,.6);}
            else{a.swingHand(Hand.MAIN_HAND);b.swingHand(Hand.MAIN_HAND);b.damage(w,w.getDamageSources().mobAttack(a),2);if(b.isAlive())a.damage(w,w.getDamageSources().mobAttack(b),2);}
        }
    }
    static boolean closed(ServerWorld world,MarkerEntity square){
        var settlement=VillageEras.settlement(world,square.getBlockPos());
        return VillageIndustrial.civic(settlement);
    }

    private static void bringToArena(ServerWorld w,VillagerEntity v,MarkerEntity square,int offset){
        var center=square.getBlockPos();
        for(int dy=-2;dy<=3;dy++)for(int dz=-3;dz<=3;dz++)for(int dx=-2;dx<=2;dx++){
            var p=center.add(offset+dx,dy,dz);
            if(w.getBlockState(p).isAir()&&w.getBlockState(p.up()).isAir()&&w.getBlockState(p.down()).isSolidBlock(w,p.down())
                &&w.getEntitiesByClass(VillagerEntity.class,new Box(p),other->other!=v).isEmpty()){
                v.requestTeleport(p.getX()+.5,p.getY(),p.getZ()+.5);return;
            }
        }
        guide(v,square.getX()+offset,square.getY(),square.getZ());
    }
    private static void guide(VillagerEntity v,double x,double y,double z){
        if(v.isSleeping())v.wakeUp();
        v.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,new net.minecraft.entity.ai.brain.WalkTarget(net.minecraft.util.math.BlockPos.ofFloored(x,y,z),.55f,1),60);
        if(v.squaredDistanceTo(x,y,z)>4&&(v.getNavigation().isIdle()||v.age%40==0))v.getNavigation().startMovingTo(x,y,z,.55);
    }
    private static void celebrate(ServerWorld world,VillagerEntity winner){
        var explosion=new net.minecraft.component.type.FireworkExplosionComponent(net.minecraft.component.type.FireworkExplosionComponent.Type.LARGE_BALL,
            it.unimi.dsi.fastutil.ints.IntList.of(0xb02030,0x8928c9),it.unimi.dsi.fastutil.ints.IntList.of(),true,true);
        ItemStack rocket=new ItemStack(Items.FIREWORK_ROCKET);rocket.set(net.minecraft.component.DataComponentTypes.FIREWORKS,new net.minecraft.component.type.FireworksComponent(1,List.of(explosion)));
        for(int[] p:new int[][]{{-5,-5},{5,-5},{-5,5},{5,5}})world.spawnEntity(new FireworkRocketEntity(world,winner.getX()+p[0],winner.getY(),winner.getZ()+p[1],rocket.copy()));
    }
    static void celebrateEra(ServerWorld world,net.minecraft.util.math.BlockPos center){
        var effect=new net.minecraft.component.type.FireworkExplosionComponent(net.minecraft.component.type.FireworkExplosionComponent.Type.LARGE_BALL,
            it.unimi.dsi.fastutil.ints.IntList.of(0xffa000,0xf8e1aa,0x8064cc),it.unimi.dsi.fastutil.ints.IntList.of(),true,true);
        ItemStack rocket=new ItemStack(Items.FIREWORK_ROCKET);rocket.set(net.minecraft.component.DataComponentTypes.FIREWORKS,new net.minecraft.component.type.FireworksComponent(1,List.of(effect)));
        for(int[] p:new int[][]{{-12,-12},{12,-12},{-12,12},{12,12},{0,0}}){
            int x=center.getX()+p[0],z=center.getZ()+p[1];int y=world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);
            if(world.isChunkLoaded(new net.minecraft.util.math.ChunkPos(x>>4,z>>4).toLong()))world.spawnEntity(new FireworkRocketEntity(world,x+.5,y,z+.5,rocket.copy()));
        }
    }
    private VillageArena(){}
}

