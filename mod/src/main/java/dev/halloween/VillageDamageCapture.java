package dev.halloween;

import java.util.List;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/** Record only natural ground before damage; inventories and player-built blocks are never copied. */
public final class VillageDamageCapture {
    static void register(){PlayerBlockBreakEvents.AFTER.register((world,player,pos,state,entity)->{
        if(world instanceof ServerWorld server)manualBreak(server,pos,state);
    });}
    private static void manualBreak(ServerWorld world,BlockPos pos,BlockState state){
        if(VillageCivilWorks.natural(state))record(world,pos,state);
    }
    public static void recordExplosion(ServerWorld world,List<BlockPos> positions){
        for(BlockPos pos:positions)record(world,pos,world.getBlockState(pos));
    }
    private static void record(ServerWorld world,BlockPos pos,BlockState state){
        if(!world.getRegistryKey().equals(World.OVERWORLD)||state.hasBlockEntity()||!state.getFluidState().isEmpty())return;
        if(VillageCivilWorks.hasOverride(world,pos))return;
        String name=Registries.BLOCK.getId(state.getBlock()).getPath();
        if(!java.util.Set.of("dirt","grass_block","coarse_dirt","podzol","rooted_dirt","sand","red_sand","gravel",
                "stone","andesite","granite","diorite","tuff","snow_block","clay").contains(name))return;
        if(pos.getY()<world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ())-6)return;
        VillageMemory memory=world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        VillageMemory.House best=null;double bestDistance=24*24;
        for(var h:memory.houses){
            double dx=pos.getX()-(h.min.getX()+h.max.getX())/2.0,dz=pos.getZ()-(h.min.getZ()+h.max.getZ())/2.0;
            double distance=dx*dx+dz*dz;
            if(distance<bestDistance&&Math.abs(pos.getY()-h.min.getY())<=12){best=h;bestDistance=distance;}
        }
        if(best==null||best.blueprint.size()>=6000)return;
        for(var block:best.blueprint)if(block.pos().equals(pos))return;
        best.blueprint.add(new VillageMemory.BlockRecord(pos.toImmutable(),state));memory.markDirty();
    }
    private VillageDamageCapture(){}
}
