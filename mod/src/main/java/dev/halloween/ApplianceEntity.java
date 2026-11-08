package dev.halloween;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public final class ApplianceEntity extends BlockEntity {
    static java.util.function.Consumer<ApplianceEntity> clientTick=entity->{};
    public ApplianceEntity(BlockPos pos,BlockState state){super(IndustrialElectronics.APPLIANCE_TYPE,pos,state);}
}
