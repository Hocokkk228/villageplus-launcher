package dev.halloween;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.model.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
/** Immutable, atlas-textured geometry. Models are sampled once, never once per cuboid per frame. */
final class RailMesh {
 record Vertex(float x,float y,float z,float u,float v,float nx,float ny,float nz,int emission){}
 record Piece(BlockState state,Box box,float yaw,Vec3d origin,org.joml.Quaternionf rotation,List<Vec3d> corners){Piece(BlockState state,Box box,float yaw,Vec3d origin){this(state,box,yaw,origin,null,null);}Piece(BlockState state,Box box,float yaw,Vec3d origin,org.joml.Quaternionf rotation){this(state,box,yaw,origin,rotation,null);}}
 final Vertex[] opaque,glass;
 RailMesh(List<Piece> pieces){this(pieces,false);}
 RailMesh(List<Piece> pieces,boolean tiled){var solid=new ArrayList<Vertex>();var transparent=new ArrayList<Vertex>();var models=MinecraftClient.getInstance().getBlockRenderManager().getModels();
  var random=net.minecraft.util.math.random.Random.create(0);
  for(int index=0;index<pieces.size();index++){var piece=pieces.get(index);var state=piece.state;var box=piece.box;boolean translucent=isGlass(state);var target=translucent?transparent:solid;
   if(piece.corners!=null){prism(target,piece,models.getModel(state).getParts(random));continue;}
   for(var part:models.getModel(state).getParts(random)){
    var quads=new ArrayList<BakedQuad>();for(var direction:Direction.values())quads.addAll(part.getQuads(direction));quads.addAll(part.getQuads(null));
    for(var quad:quads){var direction=quad.face();var normal=rotate(piece,new Vec3d(direction.getOffsetX(),direction.getOffsetY(),direction.getOffsetZ()));
     if(tiled){tiledFace(target,pieces,index,piece,quad,normal,translucent);continue;}
     for(int i=0;i<4;i++){var p=quad.getPosition(i);var point=rotate(piece,new Vec3d(box.minX+p.x()*box.getLengthX(),box.minY+p.y()*box.getLengthY(),box.minZ+p.z()*box.getLengthZ())).add(piece.origin);long uv=quad.getTexcoords(i);target.add(new Vertex((float)point.x,(float)point.y,(float)point.z,net.minecraft.client.util.math.Vector2f.getX(uv),net.minecraft.client.util.math.Vector2f.getY(uv),(float)normal.x,(float)normal.y,(float)normal.z,quad.lightEmission()));}
    }
   }
  }opaque=solid.toArray(Vertex[]::new);glass=transparent.toArray(Vertex[]::new);
 }
 record UVPoint(double u,double v){}
 static List<UVPoint> clip(List<UVPoint> input,int axis,double edge,boolean keepGreater){var out=new ArrayList<UVPoint>();if(input.isEmpty())return out;var a=input.getLast();double av=(axis==0?a.u:a.v)-edge;
  for(var b:input){double bv=(axis==0?b.u:b.v)-edge;boolean ai=keepGreater?av>=-1e-8:av<=1e-8,bi=keepGreater?bv>=-1e-8:bv<=1e-8;if(ai!=bi){double t=av/(av-bv);out.add(new UVPoint(a.u+(b.u-a.u)*t,a.v+(b.v-a.v)*t));}if(bi)out.add(b);a=b;av=bv;}return out;
 }
 static void prism(List<Vertex> out,Piece piece,List<BlockModelPart> models){
  var corners=piece.corners;var center=corners.stream().reduce(Vec3d.ZERO,Vec3d::add).multiply(1./corners.size());
  int[][] faces={{0,3,2,1},{4,5,6,7},{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7}};
  for(var face:faces){var points=new ArrayList<Vec3d>();for(int i:face)points.add(corners.get(i));var origin=points.getFirst();var u=points.get(1).subtract(origin).normalize();var normal=u.crossProduct(points.get(2).subtract(origin)).normalize();var middle=points.stream().reduce(Vec3d.ZERO,Vec3d::add).multiply(.25);
   if(normal.dotProduct(middle.subtract(center))<0){Collections.reverse(points);origin=points.getFirst();u=points.get(1).subtract(origin).normalize();normal=u.crossProduct(points.get(2).subtract(origin)).normalize();}
   var v=normal.crossProduct(u).normalize();var polygon=new ArrayList<UVPoint>();for(var p:points){var d=p.subtract(origin);polygon.add(new UVPoint(d.dotProduct(u),d.dotProduct(v)));}
   var direction=Direction.getFacing(normal.x,normal.y,normal.z);BakedQuad texture=null;
   for(var model:models){var quads=model.getQuads(direction);if(!quads.isEmpty()){texture=quads.getFirst();break;}for(var q:model.getQuads(null))if(q.face()==direction){texture=q;break;}if(texture!=null)break;}
   if(texture==null)continue;float minU=Float.POSITIVE_INFINITY,minV=minU,maxU=-minU,maxV=-minU;
   for(int i=0;i<4;i++){long uv=texture.getTexcoords(i);float tu=net.minecraft.client.util.math.Vector2f.getX(uv),tv=net.minecraft.client.util.math.Vector2f.getY(uv);minU=Math.min(minU,tu);maxU=Math.max(maxU,tu);minV=Math.min(minV,tv);maxV=Math.max(maxV,tv);}
   double loU=polygon.stream().mapToDouble(UVPoint::u).min().orElse(0),hiU=polygon.stream().mapToDouble(UVPoint::u).max().orElse(0),loV=polygon.stream().mapToDouble(UVPoint::v).min().orElse(0),hiV=polygon.stream().mapToDouble(UVPoint::v).max().orElse(0);
   var transformedNormal=rotate(piece,normal);
   for(double su=Math.floor(loU);su<hiU-1e-7;su++)for(double sv=Math.floor(loV);sv<hiV-1e-7;sv++){
    var clipped=clip(clip(clip(clip(polygon,0,su,true),0,su+1,false),1,sv,true),1,sv+1,false);if(clipped.size()<3)continue;
    for(int triangle=1;triangle<clipped.size()-1;triangle++)for(int index:new int[]{0,triangle,triangle+1,triangle+1}){var t=clipped.get(index);var p=rotate(piece,origin.add(u.multiply(t.u)).add(v.multiply(t.v))).add(piece.origin);out.add(new Vertex((float)p.x,(float)p.y,(float)p.z,minU+(maxU-minU)*(float)(t.u-su),minV+(maxV-minV)*(float)(t.v-sv),(float)transformedNormal.x,(float)transformedNormal.y,(float)transformedNormal.z,texture.lightEmission()));}
   }
  }
 }
 static boolean isGlass(BlockState state){return state.getBlock() instanceof net.minecraft.block.StainedGlassBlock||state.isOf(net.minecraft.block.Blocks.GLASS);}
 record Rect(double u0,double v0,double u1,double v1){}
 // Subtract covered rectangles before tessellation: touching hull bands and decorative
 // trim get one visible surface, including partial overlap, rather than coplanar quads.
 static List<Rect> subtract(List<Rect> source,Rect cut){var out=new ArrayList<Rect>();for(var r:source){double u0=Math.max(r.u0,cut.u0),v0=Math.max(r.v0,cut.v0),u1=Math.min(r.u1,cut.u1),v1=Math.min(r.v1,cut.v1);if(u1-u0<1e-6||v1-v0<1e-6){out.add(r);continue;}if(u0>r.u0+1e-6)out.add(new Rect(r.u0,r.v0,u0,r.v1));if(u1<r.u1-1e-6)out.add(new Rect(u1,r.v0,r.u1,r.v1));if(v0>r.v0+1e-6)out.add(new Rect(u0,r.v0,u1,v0));if(v1<r.v1-1e-6)out.add(new Rect(u0,v1,u1,r.v1));}return out;}
 static Vec3d localPoint(Piece piece,BakedQuad quad,int i){var p=quad.getPosition(i);var b=piece.box;return new Vec3d(b.minX+p.x()*b.getLengthX(),b.minY+p.y()*b.getLengthY(),b.minZ+p.z()*b.getLengthZ());}
 static Vec3d modelPoint(BakedQuad q,int i){var p=q.getPosition(i);return new Vec3d(p.x(),p.y(),p.z());}
 static void tiledFace(List<Vertex> out,List<Piece> pieces,int index,Piece piece,BakedQuad quad,Vec3d normal,boolean glass){var p0=localPoint(piece,quad,0);var eu=localPoint(piece,quad,1).subtract(p0);var ev=localPoint(piece,quad,3).subtract(p0);double width=eu.length(),height=ev.length();if(width<1e-6||height<1e-6)return;var u=eu.multiply(1/width);var v=ev.multiply(1/height);var visible=new ArrayList<Rect>();visible.add(new Rect(0,0,width,height));
  if(!glass&&piece.rotation==null&&piece.yaw==0&&piece.origin.equals(Vec3d.ZERO)){var direction=quad.face();var axis=direction.getAxis();double plane=p0.getComponentAlongAxis(axis);boolean positive=direction.getDirection()==Direction.AxisDirection.POSITIVE;
   for(int j=0;j<pieces.size()&&!visible.isEmpty();j++){if(index==j)continue;var other=pieces.get(j);if(other.corners!=null||other.rotation!=null||other.yaw!=0||!other.origin.equals(Vec3d.ZERO)||isGlass(other.state))continue;var b=other.box;double lo=b.getMin(axis),hi=b.getMax(axis);boolean covers=positive?(hi>plane+1e-6&&lo<=plane+1e-6||Math.abs(hi-plane)<1e-6&&lo<plane-1e-6&&j>index):(lo<plane-1e-6&&hi>=plane-1e-6||Math.abs(lo-plane)<1e-6&&hi>plane+1e-6&&j>index);if(!covers)continue;
    double u0=Double.POSITIVE_INFINITY,v0=u0,u1=-u0,v1=-u0;for(double x:new double[]{b.minX,b.maxX})for(double y:new double[]{b.minY,b.maxY})for(double z:new double[]{b.minZ,b.maxZ}){var d=new Vec3d(x,y,z).subtract(p0);double du=d.dotProduct(u),dv=d.dotProduct(v);u0=Math.min(u0,du);u1=Math.max(u1,du);v0=Math.min(v0,dv);v1=Math.max(v1,dv);}visible=new ArrayList<>(subtract(visible,new Rect(u0,v0,u1,v1)));
   }
  }
  double stepU=Math.max(.0625,modelPoint(quad,1).distanceTo(modelPoint(quad,0))),stepV=Math.max(.0625,modelPoint(quad,3).distanceTo(modelPoint(quad,0)));long uv0=quad.getTexcoords(0),uv1=quad.getTexcoords(1),uv3=quad.getTexcoords(3);float baseU=net.minecraft.client.util.math.Vector2f.getX(uv0),baseV=net.minecraft.client.util.math.Vector2f.getY(uv0);float duU=net.minecraft.client.util.math.Vector2f.getX(uv1)-baseU,duV=net.minecraft.client.util.math.Vector2f.getY(uv1)-baseV,dvU=net.minecraft.client.util.math.Vector2f.getX(uv3)-baseU,dvV=net.minecraft.client.util.math.Vector2f.getY(uv3)-baseV;
  for(var r:visible)for(double a=Math.floor(r.u0/stepU)*stepU;a<r.u1-1e-6;a+=stepU)for(double b=Math.floor(r.v0/stepV)*stepV;b<r.v1-1e-6;b+=stepV){double x0=Math.max(a,r.u0),x1=Math.min(a+stepU,r.u1),y0=Math.max(b,r.v0),y1=Math.min(b+stepV,r.v1);double[][] corners={{x0,y0},{x1,y0},{x1,y1},{x0,y1}};for(var c:corners){var point=rotate(piece,p0.add(u.multiply(c[0])).add(v.multiply(c[1]))).add(piece.origin);float fu=(float)((c[0]-a)/stepU),fv=(float)((c[1]-b)/stepV);out.add(new Vertex((float)point.x,(float)point.y,(float)point.z,baseU+duU*fu+dvU*fv,baseV+duV*fu+dvV*fv,(float)normal.x,(float)normal.y,(float)normal.z,quad.lightEmission()));}}
 }
 static void verifyTiling(){var state=net.minecraft.block.Blocks.OAK_PLANKS.getDefaultState();var union=new RailMesh(List.of(box(state,new Box(0,0,0,1,1,1)),box(state,new Box(.5,0,0,1.5,1,1))),true);double area=0;for(int i=0;i<union.opaque.length;i+=4){var a=union.opaque[i];var b=union.opaque[i+1];var d=union.opaque[i+3];area+=new Vec3d(b.x-a.x,b.y-a.y,b.z-a.z).crossProduct(new Vec3d(d.x-a.x,d.y-a.y,d.z-a.z)).length();}if(Math.abs(area-8)>.0001)throw new AssertionError("overlapping boxes must have exactly 8 square blocks of exposed surface, got "+area);
  var unit=new RailMesh(List.of(box(state,new Box(0,0,0,1,1,1))));var a=unit.opaque[0];var b=unit.opaque[1];double density=Math.hypot(b.u-a.u,b.v-a.v);var tiled=new RailMesh(List.of(box(state,new Box(0,0,0,2.5,1,1))),true);for(int i=0;i<tiled.opaque.length;i+=4)for(int j:new int[]{1,3}){a=tiled.opaque[i];b=tiled.opaque[i+j];double length=new Vec3d(b.x-a.x,b.y-a.y,b.z-a.z).length();if(length>1.0001||Math.abs(Math.hypot(b.u-a.u,b.v-a.v)/length-density)>.00001)throw new AssertionError("texture density changed on tiled face");}
  org.slf4j.LoggerFactory.getLogger("naval-mesh-test").info("PASS overlapping surfaces have exact union area; clipped texture tiles preserve vanilla block texel density");
 }
 static Piece box(BlockState s,Box b){return new Piece(s,b,0,Vec3d.ZERO);}
 static Piece part(RailGeometry.Part p,boolean open){return new Piece(p.state(),RailGeometry.partBox(p,open),0,p.pivot(),p.rotation());}
 private static Vec3d rotate(Piece piece,Vec3d point){if(piece.rotation==null)return RailGeometry.rotate(point,piece.yaw);var v=piece.rotation.transform(new org.joml.Vector3f((float)point.x,(float)point.y,(float)point.z));return new Vec3d(v.x,v.y,v.z);}
 void submit(MatrixStack matrices,OrderedRenderCommandQueue queue,int light){
  if(opaque.length>0)queue.submitCustom(matrices,RenderLayers.entitySolid(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE),(entry,buffer)->draw(opaque,entry,buffer,light));
  if(glass.length>0)queue.submitCustom(matrices,RenderLayers.entityTranslucent(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE),(entry,buffer)->draw(glass,entry,buffer,light));
 }
 static void draw(Vertex[] vertices,MatrixStack.Entry entry,VertexConsumer buffer,int light){var m=entry.getPositionMatrix();var n=entry.getNormalMatrix();for(var v:vertices){int illumination=v.emission>0?LightmapTextureManager.applyEmission(light,v.emission):light;
  float x=m.m00()*v.x+m.m10()*v.y+m.m20()*v.z+m.m30(),y=m.m01()*v.x+m.m11()*v.y+m.m21()*v.z+m.m31(),z=m.m02()*v.x+m.m12()*v.y+m.m22()*v.z+m.m32();
  float nx=n.m00()*v.nx+n.m10()*v.ny+n.m20()*v.nz,ny=n.m01()*v.nx+n.m11()*v.ny+n.m21()*v.nz,nz=n.m02()*v.nx+n.m12()*v.ny+n.m22()*v.nz;
  buffer.vertex(x,y,z).color(-1).texture(v.u,v.v).overlay(OverlayTexture.DEFAULT_UV).light(illumination).normal(nx,ny,nz);
 }}
}
