package dev.halloween;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

final class WantedNetwork {
    record Status(boolean wanted,int days,String crimes) implements CustomPayload {
        static final Id<Status> ID=new Id<>(Identifier.of("halloween","wanted_status"));
        static final PacketCodec<RegistryByteBuf,Status> CODEC=PacketCodec.tuple(PacketCodecs.BOOLEAN,Status::wanted,PacketCodecs.VAR_INT,Status::days,PacketCodecs.STRING,Status::crimes,Status::new);
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    static void register(){PayloadTypeRegistry.playS2C().register(Status.ID,Status.CODEC);}
    static void send(ServerPlayerEntity p,boolean wanted,int days,String crimes){if(ServerPlayNetworking.canSend(p,Status.ID))ServerPlayNetworking.send(p,new Status(wanted,days,(wanted?"★".repeat(PlayerJustice.wantedLevel(p))+"  ":"")+crimes));}
    private WantedNetwork(){}
}
