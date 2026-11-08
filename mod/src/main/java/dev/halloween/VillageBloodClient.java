package dev.halloween;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.texture.Sprite;

final class VillageBloodClient {
    private static final class Drop extends BillboardParticle {
        Drop(ClientWorld world,double x,double y,double z,double vx,double vy,double vz,Sprite sprite){
            super(world,x,y,z,sprite);
            velocityX=vx;velocityY=vy+.08;velocityZ=vz;
            scale=.06f+random.nextFloat()*.075f;
            gravityStrength=.85f;velocityMultiplier=.94f;
            maxAge=16+random.nextInt(15);collidesWithWorld=true;
            zRotation=lastZRotation=random.nextFloat()*(float)Math.PI*2;
        }
        @Override protected RenderType getRenderType(){return RenderType.PARTICLE_ATLAS_TRANSLUCENT;}
        @Override public void tick(){super.tick();alpha=Math.min(1,(maxAge-age)/8f);if(onGround){velocityX*=.2;velocityZ*=.2;}}
    }
    static void register(){ParticleFactoryRegistry.getInstance().register(VillageBlood.SPLASH,sprites->(effect,world,x,y,z,vx,vy,vz,random)->new Drop(world,x,y,z,vx,vy,vz,sprites.getSprite(random)));}
}
