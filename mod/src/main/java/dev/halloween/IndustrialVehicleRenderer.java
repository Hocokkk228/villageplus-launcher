package dev.halloween;
import java.util.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
final class IndustrialVehicleRenderer extends EntityRenderer<IndustrialVehicleEntity,IndustrialVehicleRenderer.State> {
 record Group(String name,Vec3d pivot,RailMesh mesh){}
 static final Map<IndustrialMachines.Kind,List<Group>> CACHE=new EnumMap<>(IndustrialMachines.Kind.class);
 static class State extends EntityRenderState{IndustrialMachines.Kind kind;float yaw,pitch,doors,wheels,steer;}
 IndustrialVehicleRenderer(EntityRendererFactory.Context c){super(c);shadowRadius=1.4f;}
 static void clear(){CACHE.clear();BOUNDS.clear();}
 static List<Group> meshes(IndustrialMachines.Kind kind){return CACHE.computeIfAbsent(kind,k->{var groups=new LinkedHashMap<String,List<IndustrialVehicleGeometry.Part>>();for(var part:IndustrialVehicleGeometry.parts(k))groups.computeIfAbsent(part.group(),x->new ArrayList<>()).add(part);return groups.entrySet().stream().map(e->new Group(e.getKey(),e.getValue().getFirst().pivot(),new RailMesh(e.getValue().stream().map(p->new RailMesh.Piece(p.material(),p.box(),0,p.origin(),p.rotation(),p.vertices())).toList(),true))).toList();});}
 @Override public State createRenderState(){return new State();}
 static final Map<IndustrialMachines.Kind,Box> BOUNDS=new EnumMap<>(IndustrialMachines.Kind.class);
 @Override protected boolean canBeCulled(IndustrialVehicleEntity e){return true;}
 @Override protected Box getBoundingBox(IndustrialVehicleEntity e){var local=BOUNDS.computeIfAbsent(e.kind(),kind->{
  Box bounds=kind.bounds();
  for(var part:IndustrialVehicleGeometry.parts(kind))bounds=bounds.union(IndustrialVehicleGeometry.bounds(part));
  return bounds.expand(1.5);
 });
 if(e.kind()==IndustrialMachines.Kind.FIGHTER){
  double reach=Math.max(local.getLengthX(),local.getLengthZ());
  return new Box(e.getEntityPos().add(-reach,-reach,-reach),e.getEntityPos().add(reach,reach,reach));
 }
 return RailGeometry.worldBox(local,e.getEntityPos(),e.getYaw());}
 @Override public void updateRenderState(IndustrialVehicleEntity e,State s,float delta){super.updateRenderState(e,s,delta);s.steer=e.steeringAngle();s.kind=e.kind();s.yaw=e.getLerpedYaw(delta);s.pitch=e.getLerpedPitch(delta);s.doors=MathHelper.lerp(delta,e.lastDoorAngle,e.doorAngle);s.wheels=MathHelper.lerp(delta,e.lastWheelRotation,e.wheelRotation);}
 static void submit(IndustrialMachines.Kind kind,float doors,float wheels,int groupLimit,MatrixStack m,OrderedRenderCommandQueue q,int light){submit(kind,doors,wheels,0,groupLimit,m,q,light);}
 static void submit(IndustrialMachines.Kind kind,float doors,float wheels,float steer,int groupLimit,MatrixStack m,OrderedRenderCommandQueue q,int light){int index=0;for(var group:meshes(kind)){if(index++>=groupLimit)break;m.push();var pivot=group.pivot;
   if(group.name.startsWith("door_")){m.translate(pivot.x,pivot.y,pivot.z);m.multiply((group.name.equals("door_hatch")||group.name.equals("door_canopy"))?RotationAxis.POSITIVE_X.rotationDegrees(-doors*85):RotationAxis.POSITIVE_Y.rotationDegrees(doors*(pivot.x<0?-78:78)));m.translate(-pivot.x,-pivot.y,-pivot.z);}
   if(group.name.equals("steering")){m.translate(pivot.x,pivot.y,pivot.z);m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-steer*110));m.translate(-pivot.x,-pivot.y,-pivot.z);}
   if(group.name.startsWith("track_")){
    double displacement=((wheels*.21)% .31+.31)% .31;
    m.translate(0,0,group.name.startsWith("track_top_")?-displacement:displacement);
   }
   if(group.name.startsWith("wheel_")){m.translate(pivot.x,pivot.y,pivot.z);
    if(kind!=IndustrialMachines.Kind.TANK&&kind!=IndustrialMachines.Kind.FIGHTER&&pivot.z>0)m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-steer*26));
    m.multiply(RotationAxis.POSITIVE_X.rotation(wheels));m.translate(-pivot.x,-pivot.y,-pivot.z);}
   group.mesh.submit(m,q,light);m.pop();}
 }
 @Override public void render(State s,MatrixStack m,OrderedRenderCommandQueue q,CameraRenderState c){m.push();m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-s.yaw));if(s.kind==IndustrialMachines.Kind.FIGHTER)m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(s.pitch));submit(s.kind,s.doors,s.wheels,s.steer,Integer.MAX_VALUE,m,q,s.light);m.pop();super.render(s,m,q,c);}
}
