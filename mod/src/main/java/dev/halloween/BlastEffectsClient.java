package dev.halloween;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.block.Blocks;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.util.math.Vec3d;

final class BlastEffectsClient {
    private static int particlesThisTick;

    static void register() {
        var factories = ParticleFactoryRegistry.getInstance();
        factories.register(BlastEffects.FIRE, sprites -> (effect, world, x, y, z, vx, vy, vz, random) ->
                new Cloud(world, x, y, z, vx, vy, vz, sprites, true));
        factories.register(BlastEffects.SOOT, sprites -> (effect, world, x, y, z, vx, vy, vz, random) ->
                new Cloud(world, x, y, z, vx, vy, vz, sprites, false));

        ClientTickEvents.START_CLIENT_TICK.register(client -> particlesThisTick = 0);
        ClientPlayNetworking.registerGlobalReceiver(BlastEffects.Burst.ID, (burst, context) ->
                context.client().execute(() -> {
                    var world = context.client().world;
                    if (world != null && context.client().getNetworkHandler() == context.player().networkHandler) {
                        var camera = context.client().gameRenderer.getCamera().getCameraPos();
                        var particles = context.client().options.getParticles().getValue();
                        double distance = camera.squaredDistanceTo(burst.origin());
                        if (particles != ParticlesMode.MINIMAL && distance <= burst.kind().viewDistance * burst.kind().viewDistance) {
                            render(world, burst, distance > 28 * 28, particles == ParticlesMode.DECREASED);
                        }
                    }
                }));
    }

    /** GodSystem plume layout: a hot core and lobes, followed by rising soot and a low radial wave. */
    private static void render(ClientWorld world, BlastEffects.Burst burst, boolean distant, boolean reduced) {
        var kind = burst.kind();
        var origin = burst.origin();
        double radius = kind.radius;
        boolean small = kind == BlastEffects.Kind.FIREARM || kind == BlastEffects.Kind.ROBBERY;
        boolean muzzle = kind == BlastEffects.Kind.MUZZLE || kind == BlastEffects.Kind.FIREARM;
        if (small && distant) return;
        var forward = new Vec3d(Math.cos(burst.yaw()), 0, Math.sin(burst.yaw()));
        if (!small && !distant) add(world, TintedParticleEffect.create(ParticleTypes.FLASH, 0xffffcd89), origin, Vec3d.ZERO, false);
        add(world, BlastEffects.FIRE, origin.add(0, radius * .28, 0), new Vec3d(radius * .52, 0, 0), distant);
        int lobes = small || distant ? 0 : muzzle ? 2 : 4;
        for (int i = 0; i < lobes; i++) {
            double angle = i * Math.PI / 2;
            Vec3d offset = muzzle ? forward.multiply((i + 1) * .38)
                    : new Vec3d(Math.cos(angle), 0, Math.sin(angle)).multiply(radius * .45);
            add(world, BlastEffects.FIRE, origin.add(offset).add(0, radius * .4, 0),
                    new Vec3d(radius * .34, 0, 0), false);
        }

        var random = world.getRandom();
        int smokeCount = distant ? Math.min(6, kind.smokeCount) : reduced ? kind.smokeCount / 2 : kind.smokeCount;
        for (int i = 0; i < smokeCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            var outward = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
            boolean wave = !small && !muzzle && i % 3 == 0;
            double spread = wave ? radius * .7 : random.nextDouble() * radius * .48;
            var point = origin.add(outward.multiply(spread)).add(0, wave ? .16 : random.nextDouble() * radius * .7, 0);
            var velocity = outward.multiply(wave ? .23 : .045).add(0, wave ? .022 : .07 + random.nextDouble() * .11, 0);
            if (muzzle) velocity = velocity.add(forward.multiply(.11));
            add(world, small ? ParticleTypes.SMOKE : BlastEffects.SOOT, point, velocity, distant);
        }
        if (kind == BlastEffects.Kind.HULL && !distant) {
            var wood = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DARK_OAK_PLANKS.getDefaultState());
            int fragments = reduced ? 10 : 22;
            for (int i = 0; i < fragments; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                var outward = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
                var point = origin.add(outward.multiply(.3 + random.nextDouble() * radius * .6));
                var velocity = outward.multiply(.22 + random.nextDouble() * .4).add(0, .12 + random.nextDouble() * .45, 0);
                add(world, wood, point, velocity, false);
            }
        }
    }

    private static void add(ClientWorld world, ParticleEffect type, Vec3d point, Vec3d velocity, boolean distant) {
        if (particlesThisTick >= 120) return;
        particlesThisTick++;
        world.addParticleClient(type, distant, false, point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
    }

    private static final class Cloud extends BillboardParticle {
        private final SpriteProvider sprites;
        private final boolean fire;
        private final float initialScale;

        Cloud(ClientWorld world, double x, double y, double z, double vx, double vy, double vz,
              SpriteProvider sprites, boolean fire) {
            super(world, x, y, z, sprites.getFirst());
            this.sprites = sprites;
            this.fire = fire;
            collidesWithWorld = false;
            initialScale = fire ? (float) vx : .45f + random.nextFloat() * .6f;
            maxAge = fire ? (initialScale < .35f ? 9 : 22) : 64 + random.nextInt(35);
            velocityX = fire ? 0 : vx;
            velocityY = fire ? .035 : vy;
            velocityZ = fire ? 0 : vz;
            scale = initialScale;
            if (fire) setColor(1f, .66f, .31f);
            else {
                float shade = .19f + random.nextFloat() * .1f;
                setColor(shade, shade, shade * 1.04f);
            }
        }

        @Override
        public void tick() {
            lastX = x;
            lastY = y;
            lastZ = z;
            if (++age >= maxAge) {
                markDead();
                return;
            }
            x += velocityX;
            y += velocityY;
            z += velocityZ;
            if (fire) {
                scale = initialScale * (1 + .27f * age / maxAge);
                alpha = Math.min(1, (maxAge - age) / 8f);
            } else {
                velocityX *= .96;
                velocityZ *= .96;
                velocityY = Math.min(.19, velocityY + .0015);
                scale = initialScale * (1 + age * .018f);
                alpha = .82f * Math.min(1, age / 6f) * Math.min(1, (maxAge - age) / 28f);
            }
            setSprite(sprites.getSprite(age, maxAge));
        }

        @Override
        public int getBrightness(float delta) {
            return fire ? 15728880 : super.getBrightness(delta);
        }

        @Override
        protected RenderType getRenderType() {
            return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
        }
    }

    private BlastEffectsClient() {}
}
