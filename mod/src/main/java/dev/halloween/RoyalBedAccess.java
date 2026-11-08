package dev.halloween;

import net.minecraft.block.BedBlock;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class RoyalBedAccess {
    public static boolean maySleep(VillagerEntity villager,BlockPos position){
        if(!(villager.getEntityWorld() instanceof ServerWorld world))return true;
        long day=Math.floorMod(world.getTimeOfDay(),24000L);
        if(RoyalProfession.isKing(villager)&&(day<12542||day>=23460))return false;
        if(VillagerNightLife.staysAwake(villager))return false;
        var bed=world.getBlockState(position);
        if(!(bed.getBlock() instanceof BedBlock))return true;
        BlockPos head=bed.get(BedBlock.PART)==net.minecraft.block.enums.BedPart.HEAD?position:position.offset(bed.get(BedBlock.FACING));
        boolean royal=!world.getEntitiesByClass(MarkerEntity.class,new Box(head).expand(2),m->m.getCommandTags().contains("halloween_royal_bed")&&m.getBlockPos().equals(head)).isEmpty();
        return !royal||RoyalProfession.isKing(villager);
    }
    private RoyalBedAccess(){}
}
