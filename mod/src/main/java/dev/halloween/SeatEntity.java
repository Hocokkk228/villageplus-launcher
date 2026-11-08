package dev.halloween;

import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Saveable passenger anchor (required by 1.21.11 riding). Removed when unused or its chair is broken. */
public final class SeatEntity extends Entity {
    public SeatEntity(EntityType<? extends SeatEntity> type, World world) { super(type,world); setNoGravity(true); }
    @Override protected void initDataTracker(DataTracker.Builder builder) { }
    @Override protected void readCustomData(ReadView view) { }
    @Override protected void writeCustomData(WriteView view) { }
    @Override public boolean damage(ServerWorld w, DamageSource source, float amount) { return false; }
    @Override protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scale) {
        return passenger.getVehicleAttachmentPos(this).add(0,-.55,0);
    }
    @Override public void tick() {
        super.tick();
        if (!getEntityWorld().isClient()) {
            var state=getEntityWorld().getBlockState(getBlockPos());
            if (!hasPassengers() || !(state.getBlock() instanceof FurnitureBlock b) || !b.isSeat()) discard();
        }
    }
}
