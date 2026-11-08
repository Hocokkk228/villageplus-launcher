package dev.halloween;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

final class VillageWatchScreen extends Screen {
    private static final String[] ROLES={"monitor.halloween.bandit","monitor.halloween.king","monitor.halloween.deputy","monitor.halloween.guard"};
    private int role=0,targetId=-1,cameraX,cameraY,cameraZ,ticks;
    private boolean positioned;
    private Entity oldCamera;private Perspective oldPerspective;private ArmorStandEntity camera;
    VillageWatchScreen(){super(Text.translatable("item.halloween.village_monitor"));}
    @Override protected void init(){
        if(client==null||client.world==null)return;
        if(camera==null){oldCamera=client.getCameraEntity();oldPerspective=client.options.getPerspective();
            camera=new ArmorStandEntity(client.world,0,0,0);camera.setNoGravity(true);client.options.setPerspective(Perspective.FIRST_PERSON);}
        request();
    }
    private void request(){if(ClientPlayNetworking.canSend(VillageWatchNetwork.Request.ID))ClientPlayNetworking.send(new VillageWatchNetwork.Request(role));}
    void update(VillageWatchNetwork.Response response){
        if(response.role()!=role)return;targetId=response.entityId();cameraX=response.x();cameraY=response.y();cameraZ=response.z();
        if(targetId<0&&client!=null&&oldCamera!=null)client.setCameraEntity(oldCamera);
    }
    @Override public void tick(){
        if(client==null||client.world==null||camera==null){close();return;}
        if(++ticks%20==0)request();
        Entity target=client.world.getEntityById(targetId);
        if(target==null){if(oldCamera!=null)client.setCameraEntity(oldCamera);return;}
        double x=cameraX+.5,y=cameraY+.5,z=cameraZ+.5;
        double dx=target.getX()-x,dz=target.getZ()-z,dy=target.getEyeY()-y;
        float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)));
        if(!positioned){camera.refreshPositionAndAngles(x,y-camera.getEyeHeight(camera.getPose()),z,yaw,pitch);positioned=true;}
        else {camera.setPosition(net.minecraft.util.math.MathHelper.lerp(.2,camera.getX(),x),net.minecraft.util.math.MathHelper.lerp(.2,camera.getY(),y-camera.getEyeHeight(camera.getPose())),net.minecraft.util.math.MathHelper.lerp(.2,camera.getZ(),z));
            camera.setYaw(camera.getYaw()+net.minecraft.util.math.MathHelper.wrapDegrees(yaw-camera.getYaw())*.25f);camera.setPitch(net.minecraft.util.math.MathHelper.lerp(.25f,camera.getPitch(),pitch));}
        if(client.getCameraEntity()!=camera)client.setCameraEntity(camera);
    }
    @Override public boolean keyPressed(KeyInput input){
        if(input.key()==GLFW.GLFW_KEY_SPACE){role=(role+1)%ROLES.length;targetId=-1;positioned=false;request();return true;}
        return super.keyPressed(input);
    }
    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta){}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        context.fill(0,0,width,22,0xB0201B27);context.fill(0,height-23,width,height,0xB0201B27);
        context.drawTextWithShadow(textRenderer,Text.translatable(ROLES[role]),10,7,0xFFFFC774);
        if(targetId<0)context.drawTextWithShadow(textRenderer,Text.translatable("monitor.halloween.not_found"),10,30,0xFFFFB3A4);
        context.drawTextWithShadow(textRenderer,Text.translatable("monitor.halloween.controls"),10,height-16,0xFFE3D9C6);
    }
    @Override public void removed(){if(client!=null){if(oldCamera!=null)client.setCameraEntity(oldCamera);if(oldPerspective!=null)client.options.setPerspective(oldPerspective);}}
    @Override public boolean shouldPause(){return false;}
}
