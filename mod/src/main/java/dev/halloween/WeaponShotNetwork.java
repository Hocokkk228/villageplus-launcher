package dev.halloween;

import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** A successful server shot, shared with the shooter and observing clients. */
public final class WeaponShotNetwork {
    public record Shot(UUID shooter) implements CustomPayload {
        public static final Id<Shot> ID = new Id<>(Identifier.of("halloween", "weapon_shot"));
        public static final PacketCodec<RegistryByteBuf, Shot> CODEC = new PacketCodec<>() {
            public Shot decode(RegistryByteBuf buffer) { return new Shot(buffer.readUuid()); }
            public void encode(RegistryByteBuf buffer, Shot shot) { buffer.writeUuid(shot.shooter); }
        };
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    static void register() { PayloadTypeRegistry.playS2C().register(Shot.ID, Shot.CODEC); }

    static void send(ServerPlayerEntity shooter) {
        var packet = new Shot(shooter.getUuid());
        for (var observer : PlayerLookup.tracking(shooter)) {
            if (ServerPlayNetworking.canSend(observer, Shot.ID)) ServerPlayNetworking.send(observer, packet);
        }
        if (ServerPlayNetworking.canSend(shooter, Shot.ID)) ServerPlayNetworking.send(shooter, packet);
    }

    private WeaponShotNetwork() {}
}
