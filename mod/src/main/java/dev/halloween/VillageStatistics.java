package dev.halloween;

import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;

final class VillageStatistics {
    record Report(String text) implements CustomPayload {
        static final Id<Report> ID=new Id<>(Identifier.of("halloween","village_statistics"));
        static final PacketCodec<RegistryByteBuf,Report> CODEC=PacketCodec.tuple(PacketCodecs.STRING,Report::text,Report::new);
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    record Action(String value) implements CustomPayload {
        static final Id<Action> ID=new Id<>(Identifier.of("halloween","village_statistics_action"));
        static final PacketCodec<RegistryByteBuf,Action> CODEC=PacketCodec.tuple(PacketCodecs.STRING,Action::value,Action::new);
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    private static String status(VillagerEntity v){
        if(v.isSleeping())return "спит";if(v.getCommandTags().contains("halloween_prisoner"))return "в темнице";
        if(v.getCommandTags().contains("halloween_surrender"))return "под конвоем";
        if(Oprichniki.isOfficer(v))return Oprichniki.thought(v);
        String kind=VillageProjects.assignedKind(v);if(!kind.isEmpty())return switch(kind){case "house"->"строит дом";case "road"->"прокладывает дорогу";case "ground_safety"->"засыпает опасные ямы";case "terrace_support"->"укрепляет край площади";case "terrace_stairs"->"строит каменный спуск";case "cathedral"->"перестраивает замок в собор";case "gothic_demolition"->"разбирает старый жилой дом";case "factory"->"строит оружейный завод";case "stable"->"строит конюшню";case "perimeter"->"строит ограду";case "treasury"->"строит казну";default->"работает: "+kind;};
        if(VillageBuilders.repairing(v))return "ремонтирует постройку";
        if(v.getCommandTags().contains("halloween_bandit"))return "разыскивает жертву";
        if(v.getCommandTags().contains("halloween_thief"))return "ищет добычу";
        if(VillageDefense.engaged(v))return "сражается";if(v.isAiDisabled())return "ожидает";
        String role=VillageEras.profession(v);
        if(role.equals("factory_porter"))return v.getInventory().isEmpty()?"идёт за сырьём или готовой продукцией":"переносит заводские запасы";
        if(role.equals("weaponsmith")||role.equals("armorer")){var settlement=VillageEras.settlement((ServerWorld)v.getEntityWorld(),v.getBlockPos());if(settlement!=null&&VillageFactory.base((ServerWorld)v.getEntityWorld(),settlement.village)!=null)return v.getNavigation().isIdle()?"у станка: производство или ожидание сырья":"идёт к складу сырья, печи или станку";}
        if(role.equals("lumberjack"))return v.getNavigation().isIdle()?"ищет или рубит дерево":"идёт за деревом или в казну";
        if(role.equals("miner"))return v.getNavigation().isIdle()?"ищет или добывает руду":"идёт за рудой или в казну";
        return v.getNavigation().isIdle()?"отдыхает или ожидает":"идет";
    }
    private static String report(ServerWorld w,net.minecraft.util.math.BlockPos pos,VillagerEntity selected){
        var team=VillageEras.team(w,pos);if(team==null)return VillageEras.statistics(w,pos);
        var people=w.getEntitiesByClass(VillagerEntity.class,new net.minecraft.util.math.Box(team.anchor).expand(384,96,384),v->v.isAlive()&&VillageEras.belongs(w,v,team.village));
        people.sort(java.util.Comparator.comparing(v->v.getUuidAsString()));
        StringBuilder text=new StringBuilder(VillageEras.statistics(w,pos)).append("\fСОБЫТИЯ\n");
        for(var v:people)if(Oprichniki.isOfficer(v)||v.getCommandTags().contains("halloween_bandit")||v.getCommandTags().contains("halloween_thief"))text.append(v.getName().getString()).append(": ").append(status(v)).append("\n");
        text.append("\fЖИТЕЛИ В ЗАГРУЖЕННЫХ ЧАНКАХ\n");for(var v:people)text.append("v|").append(v.getUuidAsString()).append("|").append(v.getName().getString()).append(" · ").append(VillageEras.label(VillageEras.profession(v))).append(" · ").append(status(v)).append("\n");
        text.append("\fДОМА\n");var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);java.util.Set<String> seen=new java.util.HashSet<>();
        for(var h:memory.houses)if(h.kind.equals("house")&&VillageEras.team(w,h.min)==team){var bounds=VillageRelocations.bounds(h);String key=bounds[0].asLong()+":"+bounds[1].asLong();if(!seen.add(key)||VillageRelocations.alreadyMoved(VillageRelocations.get(w),h))continue;
            var residents=people.stream().filter(v->v.getBrain().getOptionalRegisteredMemory(net.minecraft.entity.ai.brain.MemoryModuleType.HOME).map(home->home.dimension().equals(w.getRegistryKey())&&home.pos().getX()>=bounds[0].getX()&&home.pos().getX()<=bounds[1].getX()&&home.pos().getZ()>=bounds[0].getZ()&&home.pos().getZ()<=bounds[1].getZ()).orElse(false)).map(v->v.getName().getString()).limit(3).toList();
            text.append("h|").append(bounds[0].asLong()).append("|Дом ").append(bounds[0].toShortString()).append(" · ").append(residents.isEmpty()?"жители не найдены":String.join(", ",residents)).append("\n");}
        if(selected!=null)text.append("\fЖИТЕЛЬ\nv|").append(selected.getUuidAsString()).append("|").append(selected.getName().getString()).append(" · ").append(VillageEras.label(VillageEras.profession(selected))).append(" · ").append(status(selected)).append("\n");
        return text.toString();
    }
    static void register(){
        PayloadTypeRegistry.playS2C().register(Report.ID,Report.CODEC);PayloadTypeRegistry.playC2S().register(Action.ID,Action.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Action.ID,(request,context)->context.server().execute(()->applyAction(context.player(),request.value())));
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(!player.getStackInHand(hand).isOf(VillageItems.STATISTICS))return ActionResult.PASS;
            if(player instanceof ServerPlayerEntity p&&world instanceof ServerWorld w){
                var team=VillageEras.team(w,hit.getBlockPos());
                if(team==null)p.sendMessage(net.minecraft.text.Text.literal("§eИспользуйте статистику на блоке деревни."),true);
                else ServerPlayNetworking.send(p,new Report(report(w,hit.getBlockPos(),null)));
            }return ActionResult.SUCCESS;
        });
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(!player.getStackInHand(hand).isOf(VillageItems.STATISTICS)||!(entity instanceof VillagerEntity villager))return ActionResult.PASS;
            if(player instanceof ServerPlayerEntity p&&world instanceof ServerWorld w)ServerPlayNetworking.send(p,new Report(report(w,villager.getBlockPos(),villager)));
            return ActionResult.SUCCESS;
        });
    }
    private static void applyAction(ServerPlayerEntity p,String action){
        if(!p.getMainHandStack().isOf(VillageItems.STATISTICS)&&!p.getOffHandStack().isOf(VillageItems.STATISTICS))return;
        var w=p.getEntityWorld();var team=VillageEras.team(w,p.getBlockPos());if(team==null)return;
        try{
            if(action.startsWith("wake|")){
                var e=w.getEntity(java.util.UUID.fromString(action.substring(5)));if(!(e instanceof VillagerEntity v)||!v.isAlive()||!VillageEras.belongs(w,v,team.village)||VillageTrials.busy(v)||v.getCommandTags().contains("halloween_prisoner")||VillageDefense.escorting(v)||v.getCommandTags().contains("halloween_court_minister"))return;
                if(v.isSleeping())v.wakeUp();VillageProjects.resetWorker(v);VillageBuilders.resetWorker(v);VillageTasks.reset(v);v.setAiDisabled(false);v.getNavigation().stop();v.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);
                p.sendMessage(net.minecraft.text.Text.literal("Житель снова может работать."),true);
            }else if(action.startsWith("home|")){
                var origin=net.minecraft.util.math.BlockPos.fromLong(Long.parseLong(action.substring(5)));var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
                if(memory.houses.stream().noneMatch(h->h.kind.equals("house")&&VillageEras.team(w,h.min)==team&&VillageRelocations.bounds(h)[0].equals(origin)))return;
                for(var q:net.minecraft.util.math.BlockPos.iterateOutwards(origin,8,5,8))if(w.isChunkLoaded(new net.minecraft.util.math.ChunkPos(q).toLong())&&w.getBlockState(q).getCollisionShape(w,q).isEmpty()&&w.getBlockState(q.up()).getCollisionShape(w,q.up()).isEmpty()&&!w.getBlockState(q.down()).getCollisionShape(w,q.down()).isEmpty()&&w.isSpaceEmpty(p,p.getBoundingBox().offset(net.minecraft.util.math.Vec3d.ofBottomCenter(q).subtract(p.getEntityPos())))){p.networkHandler.requestTeleport(q.getX()+.5,q.getY(),q.getZ()+.5,p.getYaw(),p.getPitch());return;}
            }
        }catch(IllegalArgumentException ignored){}
    }
    private VillageStatistics(){}
}
