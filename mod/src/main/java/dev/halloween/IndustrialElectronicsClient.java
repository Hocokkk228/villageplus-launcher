package dev.halloween;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;

final class IndustrialElectronicsClient {
    private static final Map<ApplianceEntity,Broadcast> PLAYING=new WeakHashMap<>();

    static void register(){
        ApplianceEntity.clientTick=entity->{
            var client=MinecraftClient.getInstance();
            if(client.player==null||entity.getPos().getSquaredDistance(client.player.getEntityPos())>32*32)return;
            var state=entity.getCachedState();
            if(!state.isOf(IndustrialElectronics.RADIO)||!state.get(IndustrialElectronics.Appliance.ON))return;
            int channel=state.get(IndustrialElectronics.Appliance.CHANNEL)%3;
            var previous=PLAYING.get(entity);
            if(previous!=null&&!previous.isDone()&&previous.channel==channel)return;
            var sound=new Broadcast(entity,channel);
            PLAYING.put(entity,sound);
            client.getSoundManager().play(sound);
        };
    }

    private static final class Broadcast extends MovingSoundInstance {
        private final ApplianceEntity radio;
        private final int channel;

        Broadcast(ApplianceEntity radio,int channel){
            super(IndustrialElectronics.STATIONS[channel],SoundCategory.RECORDS,net.minecraft.util.math.random.Random.create());
            this.radio=radio;
            this.channel=channel;
            x=radio.getPos().getX()+.5;
            y=radio.getPos().getY()+.4;
            z=radio.getPos().getZ()+.5;
            repeat=true;
            repeatDelay=0;
            volume=.45f;
        }

        @Override public void tick(){
            var client=MinecraftClient.getInstance();
            var state=radio.getCachedState();
            if(radio.isRemoved()||client.world!=radio.getWorld()||client.player==null
                    ||radio.getPos().getSquaredDistance(client.player.getEntityPos())>36*36
                    ||!state.isOf(IndustrialElectronics.RADIO)||!state.get(IndustrialElectronics.Appliance.ON)
                    ||state.get(IndustrialElectronics.Appliance.CHANNEL)%3!=channel)setDone();
        }
    }

    private IndustrialElectronicsClient(){}
}
