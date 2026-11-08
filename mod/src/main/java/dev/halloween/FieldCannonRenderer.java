package dev.halloween;
import java.util.*;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
/** Original wooden wheeled carriage and octagonal metal barrel. Cached atlas geometry. */
final class FieldCannonRenderer extends EntityRenderer<FieldCannonEntity,FieldCannonRenderer.State> {
 static RailMesh carriage,barrel;
 static class State extends EntityRenderState{float yaw,elevation,recoil;}
 FieldCannonRenderer(EntityRendererFactory.Context context){super(context);shadowRadius=1;}
 static void clear(){carriage=barrel=null;}
 static RailMesh makeCarriage(){var pieces=new ArrayList<RailMesh.Piece>();
  for(int side:new int[]{-1,1}){
   pieces.add(RailMesh.box(Blocks.DARK_OAK_PLANKS.getDefaultState(),new Box(side*.5-.1,.32,-1.45,side*.5+.1,.7,.7)));
   for(int i=0;i<16;i++){double a=i*Math.PI/8;var p=new Vec3d(side*.83,.62+Math.sin(a)*.51,Math.cos(a)*.51);pieces.add(RailMesh.box(Blocks.STRIPPED_DARK_OAK_WOOD.getDefaultState(),new Box(p.add(-.09,-.105,-.105),p.add(.09,.105,.105))));}
   pieces.add(RailMesh.box(Blocks.IRON_BLOCK.getDefaultState(),new Box(side*.83-.12,.48,-.14,side*.83+.12,.76,.14)));
   for(int i=0;i<8;i++){double a=i*Math.PI/4;for(int j=1;j<=3;j++){var p=new Vec3d(side*.83,.62+Math.sin(a)*j*.13,Math.cos(a)*j*.13);pieces.add(RailMesh.box(Blocks.OAK_PLANKS.getDefaultState(),new Box(p.add(-.05,-.065,-.065),p.add(.05,.065,.065))));}}
  }
  pieces.add(RailMesh.box(Blocks.IRON_BLOCK.getDefaultState(),new Box(-.9,.53,-.08,.9,.66,.08)));pieces.add(RailMesh.box(Blocks.DARK_OAK_PLANKS.getDefaultState(),new Box(-.6,.48,-.55,.6,.8,.58)));return new RailMesh(pieces);
 }
 static RailMesh makeBarrel(){var parts=new ArrayList<RailMesh.Piece>();for(var b:new Box[]{new Box(-.22,-.22,-.85,.22,.22,1.05),new Box(-.29,-.14,-.75,.29,.14,1),new Box(-.14,-.29,-.75,.14,.29,1)})parts.add(RailMesh.box(Blocks.POLISHED_DEEPSLATE.getDefaultState(),b));parts.add(RailMesh.box(Blocks.BLACK_CONCRETE.getDefaultState(),new Box(-.16,-.16,1.04,.16,.16,1.08)));parts.add(RailMesh.box(Blocks.GOLD_BLOCK.getDefaultState(),new Box(-.12,-.1,-1.0,.12,.1,-.84)));return new RailMesh(parts);}
 @Override public State createRenderState(){return new State();}
 @Override public void updateRenderState(FieldCannonEntity e,State s,float d){super.updateRenderState(e,s,d);s.yaw=e.getLerpedYaw(d);s.elevation=e.elevation();double elapsed=e.getEntityWorld().getTime()-e.shot()+d;s.recoil=elapsed<0||elapsed>18?0:(float)(.35*Math.exp(-elapsed/6)*Math.min(1,elapsed*2));}
 @Override public void render(State s,MatrixStack m,OrderedRenderCommandQueue q,CameraRenderState camera){if(carriage==null)carriage=makeCarriage();if(barrel==null)barrel=makeBarrel();m.push();m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-s.yaw));carriage.submit(m,q,s.light);m.translate(0,1.05,-s.recoil);m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-s.elevation));barrel.submit(m,q,s.light);m.pop();super.render(s,m,q,camera);}
}
