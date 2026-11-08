package dev.halloween;

import java.util.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

/** Opportunistic village thief: no inventory is copied, an actual food stack is transferred. */
final class VillageTheft {
    private static final Map<UUID,BlockPos> TARGETS=new HashMap<>();
    private record Raid(MarketCrime.Stall stall, long started) {}
    private static final Map<VillagerEntity, Raid> RAIDS = new WeakHashMap<>();
    private static final Map<VillagerEntity, Long> NEXT_RAID = new WeakHashMap<>();
    static void tick(ServerWorld w,VillagerEntity thief,List<VillagerEntity> villagers){
        Raid raid = RAIDS.get(thief);
        if (raid == null && w.getTime() % 100 == 0 && w.getTime() >= NEXT_RAID.getOrDefault(thief, 0L)) {
            var stall = MarketCrime.nearbyStock(w, thief);
            if (stall != null) {
                raid = new Raid(stall, w.getTime());
                RAIDS.put(thief, raid);
            }
        }
        if (raid != null) {
            if (w.getTime() - raid.started() < 600) {
                raidMarket(w, thief, raid.stall());
                return;
            }
            RAIDS.remove(thief);
            NEXT_RAID.put(thief, w.getTime() + 1200);
            thief.removeCommandTag("halloween_attempting_theft");
            thief.getNavigation().stop();
        }
        BlockPos target=TARGETS.get(thief.getUuid());
        if(target==null||!(w.getBlockEntity(target) instanceof net.minecraft.block.entity.ChestBlockEntity)){
            if(w.getTime()%100!=0)return;target=null;
            for(BlockPos p:VillageWorkplaceIndex.get(w).chests){
                if(!p.isWithinDistance(thief.getBlockPos(),24))continue;
                if(!w.isChunkLoaded(new ChunkPos(p).toLong()))continue;
                if(w.getBlockEntity(p) instanceof net.minecraft.block.entity.ChestBlockEntity chest && hasFood(chest)){target=p.toImmutable();break;}
            }
            if(target==null)return;TARGETS.put(thief.getUuid(),target);
        }
        thief.addCommandTag("halloween_attempting_theft");
        boolean observed=villagers.stream().anyMatch(v->v!=thief&&v.isAlive()&&!v.isSleeping()&&v.squaredDistanceTo(thief)<144&&v.canSee(thief))
            ||w.getPlayers().stream().anyMatch(p->p.squaredDistanceTo(thief)<144&&p.canSee(thief));
        if(observed){
            if(villagers.stream().anyMatch(v->v.getCommandTags().contains("halloween_guard")&&v.squaredDistanceTo(thief)<144&&v.canSee(thief)))thief.addCommandTag("halloween_wanted");
            return;
        }
        if(thief.squaredDistanceTo(Vec3d.ofCenter(target))>6.25){if(thief.getNavigation().isIdle()||thief.age%40==0)thief.getNavigation().startMovingTo(target.getX()+.5,target.getY(),target.getZ()+.5,.5);return;}
        if(!(w.getBlockEntity(target) instanceof Inventory inventory)){TARGETS.remove(thief.getUuid());return;}
        for(int i=0;i<inventory.size();i++){
            ItemStack stack=inventory.getStack(i);if(stack.isEmpty()||!food(stack)||!thief.getInventory().canInsert(stack))continue;
            ItemStack taken=inventory.removeStack(i,Math.min(3,stack.getCount()));ItemStack rest=thief.getInventory().addStack(taken);
            if(!rest.isEmpty()){
                ItemStack current=inventory.getStack(i);if(current.isEmpty())inventory.setStack(i,rest);else current.increment(rest.getCount());
            }
            inventory.markDirty();thief.swingHand(Hand.MAIN_HAND);
            thief.addCommandTag("halloween_escaped");thief.addCommandTag("halloween_wanted");thief.removeCommandTag("halloween_attempting_theft");TARGETS.remove(thief.getUuid());return;
        }
        TARGETS.remove(thief.getUuid());thief.removeCommandTag("halloween_attempting_theft");
    }
    static void raidMarket(ServerWorld world, VillagerEntity thief, MarketCrime.Stall stall) {
        BlockPos target = stall.barrel();
        if (!world.isChunkLoaded(new ChunkPos(target).toLong())
                || !(world.getBlockEntity(target) instanceof net.minecraft.block.entity.BarrelBlockEntity stock)
                || stock.isEmpty() || stock.isLocked() || thief.squaredDistanceTo(Vec3d.ofCenter(target)) > 48 * 48) {
            RAIDS.remove(thief);
            thief.removeCommandTag("halloween_attempting_theft");
            return;
        }
        thief.addCommandTag("halloween_attempting_theft");
        VillageTasks.hold(thief);
        if (thief.squaredDistanceTo(Vec3d.ofCenter(target)) > 6.25) {
            if (thief.getNavigation().isIdle() || world.getTime() % 60 == 0) {
                for (Direction side : Direction.Type.HORIZONTAL) {
                    if (!VillagePathBudget.take(world)) break;
                    var path = thief.getNavigation().findPathTo(target.offset(side), 0);
                    if (path != null && path.reachesTarget()) {
                        thief.getNavigation().startMovingAlong(path, .8);
                        break;
                    }
                }
            }
            return;
        }
        RAIDS.remove(thief);
        TARGETS.remove(thief.getUuid());
        thief.removeCommandTag("halloween_attempting_theft");
        if (MarketCrime.grab(stock, thief.getInventory()) == 0) return;
        thief.swingHand(Hand.MAIN_HAND);
        thief.getNavigation().stop();
        thief.addCommandTag("halloween_crime_theft");
        thief.addCommandTag("halloween_wanted");
        CourtTestimony.observe(world,thief,null,stall.barrel(),false);
        MarketCrime.alarm(world, stall, true);
    }

    private static boolean hasFood(Inventory inv){for(int i=0;i<inv.size();i++)if(food(inv.getStack(i)))return true;return false;}
    private static boolean food(ItemStack s){return s.isOf(Items.BREAD)||s.isOf(Items.CARROT)||s.isOf(Items.POTATO)||s.isOf(Items.BEETROOT)||s.isOf(Items.APPLE)||s.isOf(Items.WHEAT);}
    private VillageTheft(){}
}
