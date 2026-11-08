package dev.halloween;

import java.util.Comparator;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

final class VillageWatchNetwork {
    record Request(int role) implements CustomPayload {
        static final Id<Request> ID=new Id<>(Identifier.of("halloween","watch_request"));
        static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.tuple(PacketCodecs.VAR_INT,Request::role,Request::new);
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    record Response(int role,int entityId,int x,int y,int z) implements CustomPayload {
        static final Id<Response> ID=new Id<>(Identifier.of("halloween","watch_response"));
        static final PacketCodec<RegistryByteBuf,Response> CODEC=PacketCodec.tuple(PacketCodecs.VAR_INT,Response::role,PacketCodecs.VAR_INT,Response::entityId,
            PacketCodecs.INTEGER,Response::x,PacketCodecs.INTEGER,Response::y,PacketCodecs.INTEGER,Response::z,Response::new);
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    static void register(){
        PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(Response.ID,Response.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(payload,context)->context.server().execute(()->reply(context.player(),payload.role())));
    }
    private static void reply(ServerPlayerEntity player,int role){
        if(role<0||role>3||(!player.getMainHandStack().isOf(VillageItems.MONITOR)&&!player.getOffHandStack().isOf(VillageItems.MONITOR)))return;
        ServerWorld w=player.getEntityWorld();
        var target=w.getEntitiesByClass(VillagerEntity.class,new Box(player.getBlockPos()).expand(128),v->v.isAlive()&&matches(v,role))
            .stream().min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
        if(target==null){ServerPlayNetworking.send(player,new Response(role,-1,0,0,0));return;}
        BlockPos camera=cameraPosition(w,target);
        ServerPlayNetworking.send(player,new Response(role,target.getId(),camera.getX(),camera.getY(),camera.getZ()));
    }
    private static boolean matches(VillagerEntity v,int role){return switch(role){
        case 0->v.getCommandTags().contains("halloween_bandit")&&!v.getCommandTags().contains("halloween_thief");
        case 1->RoyalProfession.isKing(v);
        case 2->v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY);
        default->v.getCommandTags().contains("halloween_guard");
    };}
    private static BlockPos cameraPosition(ServerWorld w,VillagerEntity target){
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        var h=memory.houses.stream().filter(a->a.kind.equals("house")&&target.squaredDistanceTo(Vec3d.ofCenter(a.min))<30*30)
            .min(Comparator.comparingDouble(a->target.squaredDistanceTo(Vec3d.ofCenter(a.min)))).orElse(null);
        if(h!=null){
            int x=MathHelper.clamp(target.getBlockX(),h.min.getX(),h.max.getX());int z=MathHelper.clamp(target.getBlockZ(),h.min.getZ(),h.max.getZ());
            BlockPos[] candidates={new BlockPos(h.min.getX()-2,h.min.getY()+3,z),new BlockPos(h.max.getX()+2,h.min.getY()+3,z),
                new BlockPos(x,h.min.getY()+3,h.min.getZ()-2),new BlockPos(x,h.min.getY()+3,h.max.getZ()+2)};
            BlockPos best=null;double score=Double.MAX_VALUE;
            for(var p:candidates)if(w.isChunkLoaded(new ChunkPos(p).toLong())&&w.getBlockState(p).isAir()&&w.getBlockState(p.up()).isAir()){
                var ray=w.raycast(new net.minecraft.world.RaycastContext(Vec3d.ofCenter(p),target.getEyePos(),net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE,target));
                double d=target.squaredDistanceTo(Vec3d.ofCenter(p))+(ray.getType()==net.minecraft.util.hit.HitResult.Type.MISS?0:1000);
                if(d<score){score=d;best=p;}
            }
            if(best!=null)return best;
        }
        BlockPos p=target.getBlockPos().add(5,3,5);
        if(!w.getBlockState(p).isAir())p=target.getBlockPos().up(4);return p;
    }
    private VillageWatchNetwork(){}
}
