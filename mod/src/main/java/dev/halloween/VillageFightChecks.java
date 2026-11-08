package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

final class VillageFightChecks {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("village-fight-checks");
    private static final BlockPos ORIGIN=new BlockPos(32,224,32);
    private static boolean done,lethal,left,right,fall,down,rise;
    private static final Set<UUID> kicked=new HashSet<>();
    private static ServerPlayerEntity player;
    private static VillageFights.Bout bout;
    private static List<VillagerEntity> actors=List.of();
    private static long began,firstClear=-1;

    static void register(){
        if(!Boolean.getBoolean("halloween.fightChecks"))return;
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(done||server.getTicks()<40)return;
            try{tick(server);}
            catch(Throwable error){done=true;LOG.error("FAIL",error);server.stop(false);}
        });
    }

    private static void tick(MinecraftServer server){
        var world=server.getOverworld();
        if(player==null){
            server.getTickManager().setTickRate(200);
            world.setTimeOfDay(6000);
            for(int x=-1;x<=5;x++)for(int z=-1;z<=5;z++)world.setChunkForced(x,z,true);
            for(var p:BlockPos.iterate(ORIGIN.add(-42,-1,-42),ORIGIN.add(42,-1,42)))world.setBlockState(p,Blocks.STONE.getDefaultState());
            spawn(server,world);
        }
        long now=world.getTime();
        int phase=FightPose.of(player).halloween$fightPose();
        fall|=phase==FightPose.FALL;
        down|=phase==FightPose.DOWN;
        rise|=phase==FightPose.RISE;
        for(var actor:actors){
            int action=FightPose.of(actor).halloween$fightPose();
            left|=action==FightPose.LEFT;
            right|=action==FightPose.RIGHT;
            if(action==FightPose.KICK)kicked.add(actor.getUuid());
        }
        if(bout.stage==VillageFights.Stage.GROUND&&!lethal)check(player.getHealth()==6f,"surviving knockdown preserves exactly 6 HP");
        if(bout.stage==VillageFights.Stage.FLEE&&bout.clearSince>=0)firstClear=bout.clearSince;
        if(phase==FightPose.RISE)check(firstClear>=0&&now-firstClear>=60,"rise starts after a continuous three-second clear window");
        if(!lethal&&!VillageFights.running(world,player)){
            var slowness=player.getStatusEffect(StatusEffects.SLOWNESS);
            check(left&&right&&fall&&down&&rise&&kicked.size()==3,"all three fighters kick; left and right punches, fall and rise play");
            check(slowness!=null&&slowness.getAmplifier()==1&&slowness.getDuration()==240,"recovery applies exactly 12 seconds of Slowness II");
            check(actors.stream().allMatch(v->v.squaredDistanceTo(player)>=64*64||Vec3d.fromPolar(player.getPitch(),player.getYaw())
                    .dotProduct(v.getEyePos().subtract(player.getEyePos()).normalize())<=Math.cos(Math.toRadians(65))||!player.canSee(v)),
                    "all gang members have left sight before recovery: "+actors.stream().map(v->v.getEntityPos().toString()).toList());
            LOG.info("PASS three attackers landed both hands and three kicking poses; 6 HP, getaway, three-second wait, rise, Slowness II");
            player.discard();
            actors.forEach(VillagerEntity::discard);
            lethal=true;
            spawn(server,world);
        }else if(lethal&&!player.isAlive()){
            check(bout.stage==VillageFights.Stage.FLEE&&bout.kicks>0,"lethal outcome uses real kicking damage, then flees");
            LOG.info("PASS lethal single-villager outcome with {} kicks",bout.kicks);
            done=true;
            server.stop(false);
        }
        check(now-began<730,"bout timed out: "+bout.stage+" health="+player.getHealth()+" kicks="+bout.kicks
                +" actors="+actors.stream().map(v->v.getEntityPos().toString()).toList());
    }

    private static void spawn(MinecraftServer server,ServerWorld world){
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),lethal?"LethalBout":"SurvivingBout");
        player=new ServerPlayerEntity(server,world,profile,net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());
        player.networkHandler=new net.minecraft.server.network.ServerPlayNetworkHandler(server,
                new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),player,
                net.minecraft.server.network.ConnectedClientData.createDefault(profile,false)){
            @Override public boolean canInteractWithGame(){return true;}
        };
        player.setPosition(Vec3d.ofBottomCenter(ORIGIN));
        world.onPlayerConnected(player);
        player.changeGameMode(GameMode.SURVIVAL);
        var offsets=lethal?List.of(new Vec3d(-1.5,0,-.4)):
                List.of(new Vec3d(-1.5,0,-.4),new Vec3d(-1.5,0,-1.45),new Vec3d(1.5,0,-.95));
        var list=new ArrayList<VillagerEntity>();
        for(var offset:offsets){
            var villager=EntityType.VILLAGER.create(world,SpawnReason.EVENT);
            villager.setPosition(player.getEntityPos().add(offset));
            world.spawnEntity(villager);
            list.add(villager);
        }
        actors=List.copyOf(list);
        began=world.getTime();
        bout=new VillageFights.Bout(player,actors,List.of(),began,!lethal,lethal,-1);
        VillageFights.begin(world,player,bout,actors);
        if(!lethal){
            var defender=actors.getFirst();
            defender.setBodyYaw(-90);
            boolean blocked=false;
            for(int i=0;i<48&&!blocked;i++){
                defender.timeUntilRegen=0;
                defender.damage(world,world.getDamageSources().playerAttack(player),.05f);
                blocked=FightPose.of(defender).halloween$fightPose()==FightPose.BLOCK;
            }
            check(blocked,"a frontal melee blow is stopped by a visible hand block");
            LOG.info("PASS frontal block action triggered through real damage callback");
        }
    }

    private static void check(boolean ok,String problem){if(!ok)throw new AssertionError(problem);}
    private VillageFightChecks(){}
}
