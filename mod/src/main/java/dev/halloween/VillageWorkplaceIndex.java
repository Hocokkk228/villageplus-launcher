package dev.halloween;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** One shared blueprint scan replaces volume scans and per-villager blueprint scans. */
final class VillageWorkplaceIndex {
    private static final Map<ServerWorld,VillageWorkplaceIndex> CACHE=new WeakHashMap<>();
    private static final Map<ServerWorld,Set<BlockPos>> PLACED=new WeakHashMap<>();
    private static final Map<ServerWorld,Set<BlockPos>> CHECK=new WeakHashMap<>();
    static void register(){
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(world instanceof ServerWorld w&&player.getStackInHand(hand).getItem() instanceof net.minecraft.item.BlockItem item){
                var probe=new VillageWorkplaceIndex();probe.add(BlockPos.ORIGIN,item.getBlock().getDefaultState());
                if(!probe.sites.isEmpty()){
                    var pending=CHECK.computeIfAbsent(w,k->new HashSet<>());pending.add(hit.getBlockPos());pending.add(hit.getBlockPos().offset(hit.getSide()));
                }
            }return net.minecraft.util.ActionResult.PASS;
        });
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world,player,pos,state,be)->{if(world instanceof ServerWorld w){var placed=PLACED.get(w);if(placed!=null)placed.remove(pos);invalidate(w);}});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(w->{
            var pending=CHECK.remove(w);if(pending==null)return;
            var placed=PLACED.computeIfAbsent(w,k->new HashSet<>());placed.addAll(pending);invalidate(w);
        });
    }
    private VillageWorkplaceIndex(){houses=0;projects=0;until=0;}
    final Map<Block,Set<BlockPos>> sites=new HashMap<>();
    final Set<BlockPos> beds=new HashSet<>(),crops=new HashSet<>(),chests=new HashSet<>();
    final int houses,projects;final long until;
    private VillageWorkplaceIndex(ServerWorld w){
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var jobs=VillageProjects.get(w).jobs;
        houses=memory.houses.size();projects=jobs.size();until=w.getTime()+600;
        for(var house:memory.houses)for(var r:house.blueprint)add(r.pos(),r.state());
        for(var project:jobs)if(project.done)for(var c:project.plan)add(c.pos(),c.after());
        for(var pos:PLACED.getOrDefault(w,Set.of()))if(w.isChunkLoaded(new net.minecraft.util.math.ChunkPos(pos).toLong()))add(pos,w.getBlockState(pos));
    }
    private void add(BlockPos p,BlockState state){
        Block b=state.getBlock();
        if(b instanceof BedBlock)beds.add(p);
        else if(b instanceof CropBlock)crops.add(p);
        else if(b instanceof ChestBlock)chests.add(p);
        else if(b==Blocks.COMPOSTER||b==Blocks.BLAST_FURNACE||b==Blocks.GRINDSTONE||b==Blocks.SMITHING_TABLE||b==Blocks.STONECUTTER||b==Blocks.FLETCHING_TABLE||b==Blocks.SMOKER||b==Blocks.BARREL||b==Blocks.LOOM||b==Blocks.CAULDRON||b==Blocks.CARTOGRAPHY_TABLE||b==Blocks.BREWING_STAND||b==Blocks.LECTERN)sites.computeIfAbsent(b,k->new HashSet<>()).add(p);
    }
    static VillageWorkplaceIndex get(ServerWorld w){
        var index=CACHE.get(w);
        if(index==null||index.until<=w.getTime()||index.houses!=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses.size()||index.projects!=VillageProjects.get(w).jobs.size()){
            index=new VillageWorkplaceIndex(w);CACHE.put(w,index);
        }index.beds.removeIf(p->VillagePrisons.managed(w,p));return index;
    }
    static void invalidate(ServerWorld w){CACHE.remove(w);}
}
