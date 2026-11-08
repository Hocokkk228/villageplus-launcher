package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Escape narrow open-topped pits by jumping together and placing dirt underfoot. */
final class VillagePitRescue {
    private static final Map<ServerWorld,Map<BlockPos,Long>> PENDING=new WeakHashMap<>();
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillagePitRescue::tick);}
    private static void tick(ServerWorld w){
        if(!w.getRegistryKey().equals(World.OVERWORLD)||w.getPlayers().isEmpty())return;
        var pending=PENDING.computeIfAbsent(w,k->new HashMap<>());
        for(var entry:List.copyOf(pending.entrySet())){
            BlockPos p=entry.getKey();if(!w.isChunkLoaded(new ChunkPos(p).toLong())||w.getTime()-entry.getValue()>60){pending.remove(p);continue;}
            if(!w.getBlockState(p).isAir()&&!w.getBlockState(p).isOf(Blocks.WATER)){pending.remove(p);continue;}
            if(!w.getOtherEntities(null,new Box(p),e->e instanceof LivingEntity&&e.isAlive()).isEmpty())continue;
            if(w.getBlockState(p.down()).isSolidBlock(w,p.down()))w.setBlockState(p,Blocks.DIRT.getDefaultState(),Block.NOTIFY_ALL);
            pending.remove(p);
        }
        if(w.getTime()%10!=0)return;
        Set<UUID> seen=new HashSet<>();
        for(var player:w.getPlayers())for(var v:w.getEntitiesByClass(VillagerEntity.class,new Box(player.getBlockPos()).expand(128),e->e.isAlive()&&!e.isBaby()&&!e.isAiDisabled()
            &&!e.getCommandTags().contains("halloween_prisoner")&&!e.getCommandTags().contains("halloween_surrender")&&!e.hasVehicle())){
            if(!seen.add(v.getUuid())||!v.isOnGround())continue;
            BlockPos foot=v.getBlockPos();if(pending.containsKey(foot)||!w.isSkyVisible(foot.up(2))||!w.getBlockState(foot.down()).isSolidBlock(w,foot.down()))continue;
            if(!w.getBlockState(foot).isAir()&&!w.getBlockState(foot).isOf(Blocks.WATER))continue;
            int walls=0;for(Direction d:Direction.Type.HORIZONTAL)if(w.getBlockState(foot.offset(d)).isSolidBlock(w,foot.offset(d)))walls++;
            if(walls<3)continue;
            var occupants=w.getEntitiesByClass(VillagerEntity.class,new Box(foot),e->e.isAlive()&&!e.isAiDisabled()&&!e.getCommandTags().contains("halloween_prisoner")&&!e.getCommandTags().contains("halloween_surrender"));
            if(occupants.isEmpty())continue;
            pending.put(foot.toImmutable(),w.getTime());
            for(var trapped:occupants){trapped.getNavigation().stop();var velocity=trapped.getVelocity();trapped.setVelocity(velocity.x,.48,velocity.z);trapped.velocityDirty=true;trapped.swingHand(Hand.MAIN_HAND);}
        }
    }
    private VillagePitRescue(){}
}
