package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.*;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import net.minecraft.client.render.OverlayTexture;
public final class RailRenderers {
 public static volatile boolean legacyBenchmark;
 public static final java.util.Set<Integer> observedVehicles=java.util.concurrent.ConcurrentHashMap.newKeySet();
 private static final Map<Integer,RailMesh> VEHICLES=new HashMap<>(),INTERIORS=new HashMap<>();
 public static void clearMeshes(){IndustrialVehicleRenderer.clear();VEHICLES.clear();INTERIORS.clear();TRACKS.clear();}
 public static class VehicleState extends EntityRenderState {int id,kind;float yaw;boolean doors;int compartments;}
 public static class Vehicle extends EntityRenderer<RailVehicleEntity,VehicleState>{
  public Vehicle(EntityRendererFactory.Context context){super(context);shadowRadius=1.7f;}
  // Long articulated bodies must not be occlusion-culled by a point near their pivot.
  @Override protected boolean canBeCulled(RailVehicleEntity entity){return false;}
  @Override public boolean shouldRender(RailVehicleEntity e,net.minecraft.client.render.Frustum frustum,double x,double y,double z){return e.shouldRender(e.squaredDistanceTo(x,y,z))&&frustum.isVisible(getBoundingBox(e).expand(.5));}
  @Override protected Box getBoundingBox(RailVehicleEntity entity){return RailGeometry.worldBox(new Box(-2.2,-.2,-8.5,2.2,entity.isSleeper()?6.8:6.2,8.5),entity.getEntityPos(),entity.getYaw());}
  @Override public VehicleState createRenderState(){return new VehicleState();}
  @Override public void updateRenderState(RailVehicleEntity e,VehicleState s,float delta){super.updateRenderState(e,s,delta);s.id=e.getId();s.kind=e.modelKind();s.compartments=e.compartments();s.doors=e.doorsOpen();s.yaw=e.getLerpedYaw(delta);}
  @Override public void render(VehicleState s,MatrixStack matrices,OrderedRenderCommandQueue queue,CameraRenderState camera){
   if(Boolean.getBoolean("halloween.railArrivalTest"))observedVehicles.add(s.id);
   matrices.push();matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-s.yaw));
   if(legacyBenchmark){for(var part:RailGeometry.parts(s.kind))cube(matrices,queue,part.state(),RailGeometry.partBox(part,s.doors),s.light);}else VEHICLES.computeIfAbsent(s.kind*32+(s.doors?8:0)+s.compartments,key->{var pieces=new ArrayList<RailMesh.Piece>();for(var part:RailGeometry.parts(s.kind))if(s.kind!=4||!warm(part))pieces.add(RailMesh.box(part.state(),part.group().startsWith("compartment_")?RailGeometry.compartmentBox(part,s.compartments):RailGeometry.partBox(part,s.doors)));return new RailMesh(pieces,true);}).submit(matrices,queue,s.light);
   if(s.kind==4)INTERIORS.computeIfAbsent(s.kind*32+(s.doors?8:0)+s.compartments,key->{var pieces=new ArrayList<RailMesh.Piece>();for(var part:RailGeometry.parts(s.kind))if(warm(part))pieces.add(RailMesh.box(part.state(),part.group().startsWith("compartment_")?RailGeometry.compartmentBox(part,s.compartments):RailGeometry.partBox(part,s.doors)));return new RailMesh(pieces,true);}).submit(matrices,queue,net.minecraft.client.render.LightmapTextureManager.applyEmission(s.light,13));
   matrices.pop();super.render(s,matrices,queue,camera);
  }
 }
 static boolean warm(RailGeometry.Part p){return p.group().equals("interior")||p.group().startsWith("compartment_");}
 static void cube(MatrixStack matrices,OrderedRenderCommandQueue queue,BlockState state,Box b,int light){matrices.push();matrices.translate(b.minX,b.minY,b.minZ);matrices.scale((float)(b.maxX-b.minX),(float)(b.maxY-b.minY),(float)(b.maxZ-b.minZ));queue.submitBlock(matrices,state,light,OverlayTexture.DEFAULT_UV,0);matrices.pop();}
 private static final Map<List<Vec3d>,RailMesh> TRACKS=new LinkedHashMap<>(){protected boolean removeEldestEntry(Map.Entry<List<Vec3d>,RailMesh> e){return size()>1024;}};
 public static class TrackState extends BlockEntityRenderState {List<Vec3d> points=List.of();RailMesh mesh;}
 public static class Track implements BlockEntityRenderer<RailTrackEntity,TrackState>{
  public Track(BlockEntityRendererFactory.Context c){}
  @Override public TrackState createRenderState(){return new TrackState();}
  @Override public void updateRenderState(RailTrackEntity e,TrackState s,float delta,Vec3d camera,ModelCommandRenderer.CrumblingOverlayCommand overlay){BlockEntityRenderer.super.updateRenderState(e,s,delta,camera,overlay);s.points=e.points;s.mesh=TRACKS.computeIfAbsent(s.points,RailRenderers::trackMesh);}
  @Override public boolean rendersOutsideBoundingBox(){return true;}
  @Override public int getRenderDistance(){return 128;}
  @Override public void render(TrackState s,MatrixStack matrices,OrderedRenderCommandQueue queue,CameraRenderState camera){if(legacyBenchmark){for(int i=0;i<s.points.size()-1;i++){var a=s.points.get(i);var b=s.points.get(i+1);var d=b.subtract(a);float yaw=(float)Math.toDegrees(Math.atan2(d.x,d.z));matrices.push();matrices.translate(a.x,a.y,a.z);matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));if(i%2==0)cube(matrices,queue,Blocks.IRON_BLOCK.getDefaultState(),new Box(-1.3,0,-.13,1.3,.10,.13),s.lightmapCoordinates);for(double x:new double[]{-.75,.75}){cube(matrices,queue,Blocks.IRON_BLOCK.getDefaultState(),new Box(x-.055,.10,0,x+.055,.22,d.length()+.025),s.lightmapCoordinates);cube(matrices,queue,Blocks.BLACKSTONE.getDefaultState(),new Box(x-.10,.08,0,x+.10,.11,d.length()+.025),s.lightmapCoordinates);}matrices.pop();}}else if(s.mesh!=null)s.mesh.submit(matrices,queue,s.lightmapCoordinates);}
 }
 static RailMesh trackMesh(List<Vec3d> points){var pieces=new ArrayList<RailMesh.Piece>();
  for(int i=0;i<points.size()-1;i++){var a=points.get(i);var b=points.get(i+1);var d=b.subtract(a);float yaw=(float)-Math.toDegrees(Math.atan2(d.x,d.z));
   if(i%2==0)pieces.add(new RailMesh.Piece(Blocks.IRON_BLOCK.getDefaultState(),new Box(-1.3,0,-.13,1.3,.10,.13),yaw,a));
   for(double x:new double[]{-.75,.75}){pieces.add(new RailMesh.Piece(Blocks.IRON_BLOCK.getDefaultState(),new Box(x-.055,.10,0,x+.055,.22,d.length()+.025),yaw,a));pieces.add(new RailMesh.Piece(Blocks.BLACKSTONE.getDefaultState(),new Box(x-.10,.08,0,x+.10,.11,d.length()+.025),yaw,a));}
  }return new RailMesh(pieces);
 }
}
