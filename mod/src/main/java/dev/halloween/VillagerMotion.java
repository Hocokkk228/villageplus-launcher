package dev.halloween;

import net.minecraft.util.math.MathHelper;

/** Continuous curves evaluated from the interpolated limb phase. */
public final class VillagerMotion {
    public record Gait(float step, float stride, float bob, float forwardLean, float turnLean, float twist) {}
    public record Strike(float windup, float hit, float recover) {}

    public static Gait gait(float phase, float amplitude, float turn) {
        float speed = MathHelper.clamp(amplitude, 0, 1);
        float running = smooth((speed - .43f) / .48f);
        float cycle = phase * .6662f;
        float step = MathHelper.cos(cycle);
        return new Gait(step, speed * (.77f + .34f * running),
                -.75f * speed * (1 - MathHelper.cos(cycle * 2)) * (.5f + running * .5f),
                .055f * speed + .19f * running, -.11f * turn,
                MathHelper.sin(cycle) * speed * (.095f + .085f * running));
    }

    public static Strike strike(float phase) {
        float windup = smooth(phase / .25f) * (1 - smooth((phase - .25f) / .19f));
        float hit = smooth((phase - .23f) / .20f) * (1 - smooth((phase - .48f) / .46f));
        float recover = smooth((phase - .47f) / .2f) * (1 - smooth((phase - .72f) / .28f));
        return new Strike(windup, hit, recover);
    }

    private static float smooth(float value) {
        float t = MathHelper.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private VillagerMotion() {}
}
