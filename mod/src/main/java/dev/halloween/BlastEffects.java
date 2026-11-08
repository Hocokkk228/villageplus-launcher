package dev.halloween;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

final class BlastEffects {
    static final SimpleParticleType FIRE = particle("blast_fire");
    static final SimpleParticleType SOOT = particle("blast_soot");

    enum Kind {
        MUZZLE(1.15, 9, 64), IMPACT(2.8, 22, 96), HULL(5.5, 34, 128),
        FIREARM(.38, 4, 48), ROBBERY(.65, 8, 48);

        final double radius;
        final int smokeCount;
        final double viewDistance;

        Kind(double radius, int smokeCount, double viewDistance) {
            this.radius = radius;
            this.smokeCount = smokeCount;
            this.viewDistance = viewDistance;
        }
    }

    record Burst(Vec3d origin, Kind kind, float yaw) implements CustomPayload {
        static final Id<Burst> ID = new Id<>(Identifier.of("halloween", "blast"));
        static final PacketCodec<RegistryByteBuf, Burst> CODEC = new PacketCodec<>() {
            @Override
            public Burst decode(RegistryByteBuf buffer) {
                Vec3d origin = new Vec3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
                return new Burst(origin, buffer.readEnumConstant(Kind.class), buffer.readFloat());
            }

            @Override
            public void encode(RegistryByteBuf buffer, Burst burst) {
                buffer.writeDouble(burst.origin.x);
                buffer.writeDouble(burst.origin.y);
                buffer.writeDouble(burst.origin.z);
                buffer.writeEnumConstant(burst.kind);
                buffer.writeFloat(burst.yaw);
            }
        };

        @Override
        public Id<Burst> getId() { return ID; }
    }

    static void register() {
        PayloadTypeRegistry.playS2C().register(Burst.ID, Burst.CODEC);
    }

    static void muzzle(ServerWorld world, Vec3d origin, Vec3d direction) {
        muzzle(world, origin, direction, Kind.MUZZLE);
    }

    static void firearm(ServerWorld world, Vec3d origin, Vec3d direction) {
        muzzle(world, origin, direction, Kind.FIREARM);
    }

    private static void muzzle(ServerWorld world, Vec3d origin, Vec3d direction, Kind kind) {
        send(world, new Burst(origin, kind, (float) Math.atan2(direction.z, direction.x)));
    }

    static void burst(ServerWorld world, Vec3d origin, Kind kind) {
        send(world, new Burst(origin, kind, 0));
    }

    private static void send(ServerWorld world, Burst burst) {
        double rangeSquared = burst.kind.viewDistance * burst.kind.viewDistance;
        for (var player : world.getPlayers()) {
            if (player.squaredDistanceTo(burst.origin) <= rangeSquared && ServerPlayNetworking.canSend(player, Burst.ID)) {
                ServerPlayNetworking.send(player, burst);
            }
        }
    }

    private static SimpleParticleType particle(String name) {
        return Registry.register(Registries.PARTICLE_TYPE, Identifier.of("halloween", name), FabricParticleTypes.simple());
    }

    private BlastEffects() {}
}
