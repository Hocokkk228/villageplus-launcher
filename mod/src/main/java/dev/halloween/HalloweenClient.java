package dev.halloween;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EmptyEntityRenderer;

public final class HalloweenClient implements ClientModInitializer {
    private static WantedNetwork.Status wantedStatus;
    @Override public void onInitializeClient() {
        PlayerWeaponMotion.register();VillageBloodClient.register();
        BlastEffectsClient.register();
        MusketControls.register();LauncherCosmeticsClient.register();LauncherRoomBridge.register();if(net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment())LauncherCosmeticsChecks.register();
        RailPassengerClient.register();
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES).registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener(){public net.minecraft.util.Identifier getFabricId(){return net.minecraft.util.Identifier.of("halloween","rail_meshes");}public void reload(net.minecraft.resource.ResourceManager manager){RailRenderers.clearMeshes();NavalRenderer.clear();FieldCannonRenderer.clear();}});
        RailControlsClient.register();
        NavalControlsClient.register();TransportSoundClient.register();RailRestClient.register();
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()){NavalPlayChecks.register();NavalAccessChecks.register();NavalDemo.client();NavalVisualClientChecks.client();SleeperChecks.register();TaigaReadyDemo.client();TaigaVerifyChecks.register();NightCustodyChecks.register();ElevatedCourtChecks.register();EraPhotoClient.register();BarrelClientChecks.register();}
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()&&Boolean.getBoolean("halloween.railOptTest"))RailOptimizationChecks.register();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(WorldStatistics.Report.ID,(m,c)->c.client().execute(()->c.client().setScreen(new WorldStatisticsScreen(m.text()))));
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()&&Boolean.getBoolean("halloween.railMotionTest"))RailMotionPlayChecks.register();
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()&&Boolean.getBoolean("halloween.railSeatSmoothTest"))RailSeatSmoothChecks.register();
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()&&Boolean.getBoolean("halloween.railArrivalTest"))RailArrivalPlayChecks.register();
        IndustrialMachinesClient.register();IndustrialElectronicsClient.register();IndustrialMachinePhotos.register();VillageFightPhotos.register();
        RailVehicleEntity.localPlayer=()->net.minecraft.client.MinecraftClient.getInstance().player;
        EntityRendererRegistry.register(StationResidents.TYPE,StationResidentRenderer::new);
        EntityRendererRegistry.register(RailContent.VEHICLE,RailRenderers.Vehicle::new);
        EntityRendererRegistry.register(RailContent.SEAT,EmptyEntityRenderer::new);
        EntityRendererRegistry.register(NavalContent.BRIG_ENTITY,NavalRenderer::new);
        EntityRendererRegistry.register(NavalContent.CUTTER_ENTITY,NavalRenderer::new);
        EntityRendererRegistry.register(NavalCannons.FIELD_ENTITY,FieldCannonRenderer::new);
        EntityRendererRegistry.register(NavalCannons.BALL_ENTITY,context->new net.minecraft.client.render.entity.FlyingItemEntityRenderer<>(context,1f,false));
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(RailContent.TRACK_ENTITY,RailRenderers.Track::new);
        if(Boolean.getBoolean("halloween.armorPoseTest"))net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(new net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick(){
            boolean done;public void onEndTick(net.minecraft.client.MinecraftClient client){if(done)return;done=true;try{if(Boolean.getBoolean("halloween.exportPlayerPoses"))PlayerWeaponPreviewExport.write();PlayerWeaponMotionChecks.verify();org.slf4j.LoggerFactory.getLogger("halloween-player-pose-test").info("PASS player weapons, both hands, normal/slim skins, armor/sleeve sync and pose reset");VillagerArmorFeature.verifyWalkingSync();org.slf4j.LoggerFactory.getLogger("halloween-armor-pose-test").info("PASS villager gait, armor sync, left and right strikes, firearm recoil and seated pose reset");}catch(Throwable e){org.slf4j.LoggerFactory.getLogger("halloween-armor-pose-test").error("FAIL walking armor",e);}client.scheduleStop();}
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(VillageStatistics.Report.ID,(payload,context)->context.client().execute(()->context.client().setScreen(new VillageStatisticsScreen(payload.text()))));
        EntityRendererRegistry.register(FurnitureContent.SEAT, EmptyEntityRenderer::new);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(WantedNetwork.Status.ID,(payload,context)->context.client().execute(()->wantedStatus=payload));
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((draw,tick)->{
            var c=net.minecraft.client.MinecraftClient.getInstance();var status=wantedStatus;
            if(c.player==null||status==null||!status.wanted())return;
            int width=174,x=draw.getScaledWindowWidth()-width-8,y=30;
            draw.fill(x,y,x+width,y+67,0xdd261719);draw.fill(x,y,x+width,y+2,0xffbb3c43);
            draw.drawTextWithShadow(c.textRenderer,net.minecraft.text.Text.literal("§c§lВ РОЗЫСКЕ"),x+8,y+7,0xffffffff);
            var lines=c.textRenderer.wrapLines(net.minecraft.text.Text.literal("§f"+status.crimes()),width-16);
            for(int i=0;i<Math.min(2,lines.size());i++)draw.drawTextWithShadow(c.textRenderer,lines.get(i),x+8,y+21+i*10,0xffffffff);
            draw.drawTextWithShadow(c.textRenderer,net.minecraft.text.Text.literal("§eСрок: "+status.days()+" дней"),x+8,y+47,0xffffffff);
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(VillageWatchNetwork.Response.ID,(payload,context)->context.client().execute(()->{
            if(context.client().currentScreen instanceof VillageWatchScreen screen)screen.update(payload);
        }));
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player,world,hand)->{
            if(world.isClient()&&player.getStackInHand(hand).isOf(VillageItems.MONITOR)){
                net.minecraft.client.MinecraftClient.getInstance().setScreen(new VillageWatchScreen());return net.minecraft.util.ActionResult.SUCCESS;
            }
            return net.minecraft.util.ActionResult.PASS;
        });
    }
}
