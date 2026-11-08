package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

final class VillageFightPhotos {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("village-fight-photos");
    private static final Set<String> CAPTURED=new HashSet<>();
    private static UUID actorId;
    private static boolean externalReady;
    private static int ticks;
    private static volatile boolean started;
    private static int thirdPersonAt;

    static void register(){
        if(!Boolean.getBoolean("halloween.fightPhotos"))return;
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.player==null||client.world==null||client.getServer()==null)return;
            if(++ticks==60)prepare(client);
            if(!started)return;
            var player=client.player;
            var pose=FightPose.of(player);
            int phase=pose.halloween$fightPose();
            if(actorId!=null){
                var actor=client.world.getEntitiesByClass(VillagerEntity.class,player.getBoundingBox().expand(10),
                        v->actorId.equals(v.getUuid())).stream().findFirst().orElse(null);
                if(actor!=null&&phase<FightPose.FALL){
                    var to=actor.getEyePos().subtract(player.getEyePos());
                    player.setYaw((float)Math.toDegrees(Math.atan2(-to.x,to.z)));
                    player.setPitch(4);
                    var attacker=FightPose.of(actor);
                    if(attacker.halloween$fightPose()==FightPose.GUARD)capture(client,"stance");
                    if(attacker.halloween$fightPose()==FightPose.RIGHT&&attacker.halloween$fightAge(0)>=4)capture(client,"punch");
                }
                if(actor!=null&&phase==FightPose.DOWN){
                    var head=player.getEntityPos().add(Vec3d.fromPolar(0,pose.halloween$knockdownYaw()).multiply(-1.45)).add(0,.36,0);
                    var to=actor.getEntityPos().subtract(head);
                    player.setYaw((float)Math.toDegrees(Math.atan2(-to.x,to.z)));
                    player.setPitch(2);
                    if(FightPose.of(actor).halloween$fightPose()==FightPose.KICK){
                        capture(client,"ground");
                        if(thirdPersonAt==0&&FightPose.of(actor).halloween$fightAge(0)>2){
                            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                            client.setCameraEntity(player);
                            thirdPersonAt=ticks;
                        }
                    }
                }
            }
            if(thirdPersonAt>0){
                player.setYaw(pose.halloween$knockdownYaw()+135);
                player.setPitch(28);
            }
            if(thirdPersonAt>0&&ticks>thirdPersonAt+6&&!externalReady){
                externalReady=true;
                capture(client,"knockdown");
            }
            if(CAPTURED.size()==4||ticks>420)client.scheduleStop();
        });
    }

    private static void prepare(MinecraftClient client){
        client.options.pauseOnLostFocus=false;
        client.options.hudHidden=true;
        client.options.getFov().setValue(69);
        client.options.setPerspective(Perspective.FIRST_PERSON);
        client.getServer().execute(()->{
            var server=client.getServer();
            var world=server.getOverworld();
            var player=server.getPlayerManager().getPlayerList().getFirst();
            var stage=new BlockPos(12,222,35);
            for(var leftover:world.getOtherEntities(null,new Box(stage).expand(12,10,12),
                    e->e instanceof VillagerEntity||e instanceof net.minecraft.entity.decoration.ArmorStandEntity))leftover.discard();
            for(var pos:BlockPos.iterate(stage.add(-9,-1,-9),stage.add(9,-1,9)))world.setBlockState(pos,Blocks.STONE_BRICKS.getDefaultState());
            world.setTimeOfDay(6500);
            world.setWeather(0,0,false,false);
            player.changeGameMode(GameMode.SURVIVAL);
            player.teleport(world,12.5,222,35.5,Set.of(),0,4,true);
            player.setHealth(16);
            var actor=EntityType.VILLAGER.create(world,SpawnReason.EVENT);
            actor.setPosition(player.getEntityPos().add(0,0,1.8));
            actor.setHealth(12);
            world.spawnEntity(actor);
            actorId=actor.getUuid();
            var pair=List.of(actor);
            var bout=new VillageFights.Bout(player,pair,List.of(),world.getTime(),false,false,-1);
            VillageFights.begin(world,player,bout,pair);
            started=true;
        });
    }

    private static void capture(MinecraftClient client,String name){
        if(!CAPTURED.add(name))return;
        // Capture after a render frame with the new perspective.
        client.execute(()->ScreenshotRecorder.saveScreenshot(client.runDirectory,"fight-0520-"+name+".png",client.getFramebuffer(),1,
                message->LOG.info("Captured {}",name)));
    }

    private VillageFightPhotos(){}
}
