package dev.halloween;
import java.util.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
final class NavalRenderer extends EntityRenderer<RailVehicleEntity,NavalRenderer.State> {
 static final Map<Integer,RailMesh> MESHES=new HashMap<>(),GUNS=new HashMap<>(),WHEELS=new HashMap<>();
 static class State extends EntityRenderState {int kind;float yaw;boolean door,sails,gangway;int shotGun;float recoil,wheel;}
 NavalRenderer(EntityRendererFactory.Context context){super(context);shadowRadius=3.5f;}
 static void clear(){MESHES.clear();GUNS.clear();WHEELS.clear();}
 @Override protected boolean canBeCulled(RailVehicleEntity entity){return false;}
 @Override public boolean shouldRender(RailVehicleEntity e,net.minecraft.client.render.Frustum frustum,double x,double y,double z){return e.shouldRender(e.squaredDistanceTo(x,y,z))&&frustum.isVisible(getBoundingBox(e).expand(.5));}
 @Override protected Box getBoundingBox(RailVehicleEntity e){return RailGeometry.worldBox(NavalGeometry.bounds(e.kind()),e.getEntityPos(),e.getYaw());}
 @Override public State createRenderState(){return new State();}
 @Override public void updateRenderState(RailVehicleEntity entity,State state,float delta){super.updateRenderState(entity,state,delta);state.kind=entity.kind();state.wheel=entity.wheelAngle(delta);state.yaw=entity.getLerpedYaw(delta);state.door=entity.doorsOpen();state.sails=entity.sailsRaised();state.gangway=entity.gangwayDeployed();long shot=entity.cannonShot();state.shotGun=(int)(shot&7);double elapsed=entity.getEntityWorld().getTime()-(shot>>3)+delta;state.recoil=shot<0||elapsed<0||elapsed>18?0:(float)(.32*Math.exp(-elapsed/6)*Math.min(1,elapsed*2));}
 @Override public void render(State s,MatrixStack matrices,OrderedRenderCommandQueue queue,CameraRenderState camera){
  int key=s.kind*8+(s.door?1:0)+(s.sails?2:0)+(s.gangway?4:0);var mesh=MESHES.computeIfAbsent(key,k->{var pieces=new ArrayList<RailMesh.Piece>();for(var part:RailGeometry.parts(s.kind)){if(part.group().startsWith("cannon_barrel")||part.group().equals("wheel"))continue;if(part.group().equals("sails")&&!s.sails||part.group().equals("gangway")&&!s.gangway)continue;pieces.add(RailMesh.part(part,s.door));}return new RailMesh(pieces,true);});
  matrices.push();matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-s.yaw));mesh.submit(matrices,queue,s.light);var wheel=WHEELS.computeIfAbsent(s.kind,k->new RailMesh(RailGeometry.parts(k).stream().filter(p->p.group().equals("wheel")).map(p->RailMesh.part(p,false)).toList(),true));var pivot=NavalGeometry.local(s.kind,0,6.98,-10.72);matrices.push();matrices.translate(pivot.x,pivot.y,pivot.z);matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(s.wheel));matrices.translate(-pivot.x,-pivot.y,-pivot.z);wheel.submit(matrices,queue,s.light);matrices.pop();for(int i=0;i<NavalCannons.count(s.kind);i++){final int gun=i;var barrel=GUNS.computeIfAbsent(s.kind*8+i,k->new RailMesh(RailGeometry.parts(s.kind).stream().filter(p->p.group().equals("cannon_barrel"+gun)).map(p->RailMesh.part(p,false)).toList(),true));matrices.push();if(s.shotGun==i)matrices.translate(-NavalCannons.side(i)*s.recoil,0,0);barrel.submit(matrices,queue,s.light);matrices.pop();}matrices.pop();super.render(s,matrices,queue,camera);
 }
}
