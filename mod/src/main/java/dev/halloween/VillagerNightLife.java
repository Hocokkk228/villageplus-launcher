package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** A small, rotating share of ordinary villagers stays awake on some nights. */
public final class VillagerNightLife {
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillagerNightLife::tick);}
    public static boolean staysAwake(VillagerEntity v){
        if(!(v.getEntityWorld() instanceof ServerWorld w)||v.isBaby()||v.getCommandTags().contains("halloween_guard")
            ||RoyalProfession.isKing(v)||v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY))return false;
        long time=w.getTimeOfDay(),hour=Math.floorMod(time,24000);if(hour<13000||hour>22500)return false;
        long day=Math.floorDiv(time,24000);return Math.floorMod(v.getUuid().hashCode()+(int)day*31,10)==0;
    }
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty()||w.getTime()%200!=0)return;Set<UUID> seen=new HashSet<>();
        for(var player:w.getPlayers())for(var v:w.getEntitiesByClass(VillagerEntity.class,new Box(player.getBlockPos()).expand(80),VillagerNightLife::staysAwake)){
            if(!seen.add(v.getUuid())||v.isAiDisabled()||v.getCommandTags().contains("halloween_surrender")||v.getCommandTags().contains("halloween_bandit"))continue;
            if(v.isSleeping())v.wakeUp();if(!v.getNavigation().isIdle())continue;
            int x=v.getBlockX()+w.random.nextBetween(-10,10),z=v.getBlockZ()+w.random.nextBetween(-10,10);
            int y=w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);
            if(Math.abs(y-v.getBlockY())<=3)v.getNavigation().startMovingTo(x+.5,y,z+.5,.38);
        }
    }
    private VillagerNightLife(){}
}
