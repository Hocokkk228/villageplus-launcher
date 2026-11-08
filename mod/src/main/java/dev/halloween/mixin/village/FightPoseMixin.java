package dev.halloween.mixin.village;

import dev.halloween.FightPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class FightPoseMixin extends net.minecraft.entity.Entity implements FightPose {
    @Unique private static final TrackedData<Integer> FIGHT_POSE =
            DataTracker.registerData(LivingEntity.class,TrackedDataHandlerRegistry.INTEGER);
    @Unique private static final TrackedData<Float> KNOCKDOWN_YAW =
            DataTracker.registerData(LivingEntity.class,TrackedDataHandlerRegistry.FLOAT);
    @Unique private static final TrackedData<Integer> INJURY =
            DataTracker.registerData(LivingEntity.class,TrackedDataHandlerRegistry.INTEGER);
    @Unique private int halloween$poseSince;

    protected FightPoseMixin(EntityType<?> type,World world) { super(type,world); }

    @Inject(method="initDataTracker",at=@At("TAIL"))
    private void halloween$trackFightPose(DataTracker.Builder builder,CallbackInfo ci) {
        builder.add(FIGHT_POSE,NONE);
        builder.add(KNOCKDOWN_YAW,0f);
        builder.add(INJURY,0);
    }

    @Inject(method="onTrackedDataSet",at=@At("TAIL"))
    private void halloween$syncFightPose(TrackedData<?> data,CallbackInfo ci) {
        if(data==FIGHT_POSE)halloween$poseSince=age;
    }

    @Override public int halloween$fightPose(){return dataTracker.get(FIGHT_POSE);}
    @Override public void halloween$fightPose(int pose){dataTracker.set(FIGHT_POSE,pose);}
    @Override public int halloween$injury(){return dataTracker.get(INJURY);}
    @Override public void halloween$injury(int amount){dataTracker.set(INJURY,Math.clamp(amount,0,16));}
    @Override public float halloween$knockdownYaw(){return dataTracker.get(KNOCKDOWN_YAW);}
    @Override public void halloween$knockdownYaw(float yaw){dataTracker.set(KNOCKDOWN_YAW,yaw);}
    @Override public float halloween$fightAge(float delta){return Math.max(0,age-halloween$poseSince+delta);}
}
