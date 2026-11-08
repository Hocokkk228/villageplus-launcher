package dev.halloween;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.*;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
final class MachineAssemblyRenderer implements BlockEntityRenderer<MachineAssemblyEntity,MachineAssemblyRenderer.State> {
 static class State extends BlockEntityRenderState{IndustrialMachines.Kind kind;int product,stage,total;float progress;int facing;}
 MachineAssemblyRenderer(BlockEntityRendererFactory.Context c){}
 @Override public State createRenderState(){return new State();}
 @Override public void updateRenderState(MachineAssemblyEntity e,State s,float delta,Vec3d camera,ModelCommandRenderer.CrumblingOverlayCommand overlay){BlockEntityRenderer.super.updateRenderState(e,s,delta,camera,overlay);s.product=e.product;s.kind=e.kind();s.stage=e.stage();s.total=e.bill().size();s.progress=e.stageFraction();s.facing=e.getCachedState().get(MachineAssemblyBlock.FACING).getHorizontalQuarterTurns();}
 @Override public boolean rendersOutsideBoundingBox(){return true;}
 @Override public int getRenderDistance(){return 96;}
 @Override public void render(State s,MatrixStack m,OrderedRenderCommandQueue q,CameraRenderState c){if(s.stage==0&&s.progress==0)return;m.push();m.translate(.5,1.05,.5);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-s.facing*90));
  if(s.product>=5){m.scale(1.5f,1.5f,1.5f);m.translate(-.5,0,-.5);q.submitBlock(m,s.stage>=2?(s.product==5?IndustrialElectronics.TELEVISION:IndustrialElectronics.RADIO).getDefaultState():net.minecraft.block.Blocks.OAK_PLANKS.getDefaultState(),s.lightmapCoordinates,net.minecraft.client.render.OverlayTexture.DEFAULT_UV,0);m.pop();return;}
  var groups=IndustrialVehicleRenderer.meshes(s.kind);int visible=Math.min(groups.size(),Math.max(1,(int)Math.ceil(groups.size()*s.stage/(double)s.total)));
  IndustrialVehicleRenderer.submit(s.kind,0,0,visible,m,q,s.lightmapCoordinates);
  if(visible<groups.size()&&s.progress>0){var next=groups.get(visible);m.push();double ease=s.progress*s.progress*(3-2*s.progress);m.translate((1-ease)*(s.kind.width/2+2),Math.sin(ease*Math.PI)*1.4,0);next.mesh().submit(m,q,s.lightmapCoordinates);m.pop();}m.pop();
 }
}
