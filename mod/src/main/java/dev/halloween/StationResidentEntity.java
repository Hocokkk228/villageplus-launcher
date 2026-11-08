package dev.halloween;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.world.World;
/** A stationary civilian resting on the station floor; no fake bed and no borrowed passenger state. */
public final class StationResidentEntity extends VillagerEntity {
 public StationResidentEntity(EntityType<? extends VillagerEntity> type,World world){super(type,world);setAiDisabled(true);setPersistent();}
 @Override public boolean isPushable(){return false;}
}
