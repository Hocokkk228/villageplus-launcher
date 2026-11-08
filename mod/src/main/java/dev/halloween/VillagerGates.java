package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.math.*;

/** Opens nearby gates for navigating villagers and closes the ones they opened after passage. */
final class VillagerGates {
    private static final Map<ServerWorld,Map<BlockPos,Long>> OPENED=new WeakHashMap<>();
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillagerGates::tick);}
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty()||w.getTime()%10!=0)return;
        var gates=OPENED.computeIfAbsent(w,k->new HashMap<>());Set<UUID> seen=new HashSet<>();
        for(var player:w.getPlayers())for(var v:w.getEntitiesByClass(VillagerEntity.class,new Box(player.getBlockPos()).expand(80),e->e.isAlive()&&!e.isSleeping())){
            if(!seen.add(v.getUuid()))continue;
            var target=v.getBrain().getOptionalRegisteredMemory(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);
            if(v.getNavigation().isIdle()&&target.isEmpty())continue;
            BlockPos origin=v.getBlockPos();
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=-1;dy<=1;dy++){
                BlockPos p=origin.add(dx,dy,dz);var state=w.getBlockState(p);
                boolean gate=state.getBlock() instanceof FenceGateBlock;
                boolean trap=state.getBlock() instanceof TrapdoorBlock;
                if((!gate&&!trap)||v.squaredDistanceTo(Vec3d.ofCenter(p))>6.25)continue;
                if(trap && (v.getBlockY()!=p.getY()||!w.getOtherEntities(null,new Box(p.up()),e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty()))continue;
                if(gate&&state.get(FenceGateBlock.OPEN)||trap&&state.get(TrapdoorBlock.OPEN))continue;
                w.setBlockState(p,gate?state.with(FenceGateBlock.OPEN,true):state.with(TrapdoorBlock.OPEN,true),Block.NOTIFY_ALL);gates.put(p.toImmutable(),w.getTime()+60);
                if(gate)w.playSound(null,p,SoundEvents.BLOCK_FENCE_GATE_OPEN,SoundCategory.BLOCKS,.7f,1);
                if(v.getNavigation().isIdle()&&target.isPresent()){
                    var goal=target.get().getLookTarget().getPos();v.getNavigation().startMovingTo(goal.x,goal.y,goal.z,target.get().getSpeed());
                }
            }
        }
        var iter=gates.entrySet().iterator();while(iter.hasNext()){
            var e=iter.next();BlockPos p=e.getKey();if(w.getTime()<e.getValue()||!w.isChunkLoaded(new ChunkPos(p).toLong()))continue;
            var state=w.getBlockState(p);boolean gate=state.getBlock() instanceof FenceGateBlock,trap=state.getBlock() instanceof TrapdoorBlock;
            if((!gate&&!trap)||(gate&&!state.get(FenceGateBlock.OPEN))||(trap&&!state.get(TrapdoorBlock.OPEN))){iter.remove();continue;}
            if(!w.getOtherEntities(null,new Box(p).expand(.8),entity->entity instanceof net.minecraft.entity.LivingEntity&&entity.isAlive()).isEmpty())continue;
            w.setBlockState(p,gate?state.with(FenceGateBlock.OPEN,false):state.with(TrapdoorBlock.OPEN,false),Block.NOTIFY_ALL);
            if(gate)w.playSound(null,p,SoundEvents.BLOCK_FENCE_GATE_CLOSE,SoundCategory.BLOCKS,.7f,1);iter.remove();
        }
    }
    private VillagerGates(){}
}
