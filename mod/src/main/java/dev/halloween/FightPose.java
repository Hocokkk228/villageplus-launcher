package dev.halloween;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/** Small tracked pose shared by the server, models, and camera. */
public interface FightPose {
    int NONE=0, GUARD=1, LEFT=2, RIGHT=3, BLOCK=4, KICK=5, FALL=6, DOWN=7, RISE=8;
    int FALL_TICKS=12, RISE_TICKS=16;

    int halloween$fightPose();
    void halloween$fightPose(int pose);
    float halloween$fightAge(float tickDelta);
    int halloween$injury();
    void halloween$injury(int amount);
    float halloween$knockdownYaw();
    void halloween$knockdownYaw(float yaw);

    static FightPose of(LivingEntity entity) { return (FightPose)entity; }
    static boolean down(int pose) { return pose>=FALL; }
    static float recline(int pose,float age) {
        float t=switch(pose) {
            case FALL -> MathHelper.clamp(age/FALL_TICKS,0,1);
            case DOWN -> 1;
            case RISE -> 1-MathHelper.clamp(age/RISE_TICKS,0,1);
            default -> 0;
        };
        return t*t*(3-2*t);
    }
}
