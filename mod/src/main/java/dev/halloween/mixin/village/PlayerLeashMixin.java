package dev.halloween.mixin.village;

import net.minecraft.entity.Leashable;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Players participate in Minecraft's normal leash packet and renderer; custody supplies the constraints. */
@Mixin(PlayerEntity.class)
public abstract class PlayerLeashMixin implements Leashable {
    @Unique private Leashable.LeashData halloween$leash;
    @Override public Leashable.LeashData getLeashData(){return halloween$leash;}
    @Override public void setLeashData(Leashable.LeashData data){halloween$leash=data;}
    @Override public double getLeashSnappingDistance(){return Double.MAX_VALUE;}
    @Override public void snapLongLeash(){}
}
