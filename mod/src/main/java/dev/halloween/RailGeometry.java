package dev.halloween;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
/** Original vanilla-textured cuboids shared by rendering and physical collision. */
public final class RailGeometry {
 public record Part(BlockState state,Box box,boolean solid,boolean door,boolean hinged,String group,org.joml.Quaternionf rotation,Vec3d pivot,Vec3d hinge){}
 public static Box partBox(Part p,boolean open){if(!p.door||!open)return p.box;if(p.hinged){var hinge=p.hinge!=null?p.hinge:new Vec3d(p.box.minX,0,p.box.minZ);return worldBox(p.box.offset(hinge.negate()),hinge,90);}return p.box.offset(0,0,p.box.getCenter().z>0?-1.4:1.4);}
 private static final Map<Integer,List<Part>> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
 public static List<Part> parts(int kind){return CACHE.computeIfAbsent(kind,k->{
  try(var in=RailGeometry.class.getResourceAsStream("/data/halloween/rail_models/"+switch(k){case 0->"locomotive";case 1->"coach";case 2->"ship_brig";case 3->"ship_cutter";case 4->"sleeper_coach";default->throw new IllegalArgumentException("Unknown vehicle model "+k);}+".json")){
   if(in==null)throw new IOException("rail model missing");var json=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8));var result=new ArrayList<Part>();
   for(var element:json.getAsJsonArray()){var o=element.getAsJsonObject();var b=o.getAsJsonArray("box");var state=Registries.BLOCK.get(Identifier.of(o.get("block").getAsString())).getDefaultState();org.joml.Quaternionf rotation=null;Vec3d pivot=Vec3d.ZERO;if(o.has("rotation")){var r=o.getAsJsonArray("rotation");rotation=new org.joml.Quaternionf(r.get(0).getAsFloat(),r.get(1).getAsFloat(),r.get(2).getAsFloat(),r.get(3).getAsFloat());var v=o.getAsJsonArray("pivot");pivot=new Vec3d(v.get(0).getAsDouble(),v.get(1).getAsDouble(),v.get(2).getAsDouble());}result.add(new Part(state,new Box(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),b.get(3).getAsDouble(),b.get(4).getAsDouble(),b.get(5).getAsDouble()),o.get("solid").getAsBoolean(),o.has("door")&&o.get("door").getAsBoolean(),o.has("hinged")&&o.get("hinged").getAsBoolean(),o.has("group")?o.get("group").getAsString():"body",rotation,pivot,o.has("hinge")?new Vec3d(o.getAsJsonArray("hinge").get(0).getAsDouble(),o.getAsJsonArray("hinge").get(1).getAsDouble(),o.getAsJsonArray("hinge").get(2).getAsDouble()):null));}
   return List.copyOf(result);
  }catch(Exception e){throw new IllegalStateException("Cannot load original train model",e);}
 });}
 private static final Map<Integer,List<Box>> CELLS=new java.util.concurrent.ConcurrentHashMap<>();
 public static List<Box> solidCells(int kind){return solidCells(kind,false);}
 public static Box compartmentBox(Part p,int mask){if(p.group.startsWith("compartment_")){int i=Integer.parseInt(p.group.substring(12));if((mask&(1<<i))!=0)return p.box.offset(0,0,.98);}return p.box;}
 public static List<Box> solidCells(int kind,boolean open){return solidCells(kind,open,7);}
 public static List<Box> solidCells(int kind,boolean open,int compartments){return CELLS.computeIfAbsent(kind*32+(open?8:0)+compartments,k->{var cells=new ArrayList<Box>();for(var part:parts(kind))if(part.solid()){
   var b=part.group.startsWith("compartment_")?compartmentBox(part,compartments):partBox(part,open);int nx=Math.max(1,(int)Math.ceil((b.maxX-b.minX)*2)),nz=Math.max(1,(int)Math.ceil((b.maxZ-b.minZ)*2));
   for(int x=0;x<nx;x++)for(int z=0;z<nz;z++)cells.add(new Box(MathHelper.lerp(x/(double)nx,b.minX,b.maxX),b.minY,MathHelper.lerp(z/(double)nz,b.minZ,b.maxZ),MathHelper.lerp((x+1)/(double)nx,b.minX,b.maxX),b.maxY,MathHelper.lerp((z+1)/(double)nz,b.minZ,b.maxZ)));
  }return List.copyOf(cells);});}
 public static Vec3d rotate(Vec3d p,float yaw){double r=Math.toRadians(yaw),c=Math.cos(r),s=Math.sin(r);return new Vec3d(p.x*c-p.z*s,p.y,p.x*s+p.z*c);}
 public static Box worldBox(Box b,Vec3d origin,float yaw){double minX=Double.MAX_VALUE,minZ=minX,maxX=-minX,maxZ=-minX;
  for(double x:new double[]{b.minX,b.maxX})for(double z:new double[]{b.minZ,b.maxZ}){var v=rotate(new Vec3d(x,0,z),yaw);minX=Math.min(minX,v.x);maxX=Math.max(maxX,v.x);minZ=Math.min(minZ,v.z);maxZ=Math.max(maxZ,v.z);}return new Box(minX+origin.x,b.minY+origin.y,minZ+origin.z,maxX+origin.x,b.maxY+origin.y,maxZ+origin.z);
 }
 public static List<Vec3d> sleeperSeats(){var out=new ArrayList<Vec3d>();for(double c:new double[]{-3.15,0,3.15})for(int side:new int[]{-1,1})for(double x:new double[]{-1.02,-.12})out.add(new Vec3d(x,2.05,c+side*1.17));return List.copyOf(out);}
 public static double compartmentCenter(double z){return Math.clamp(Math.round(z/3.15),-1,1)*3.15;}
 public static List<Vec3d> seats(){var seats=new ArrayList<Vec3d>();for(int z=-4;z<=4;z+=2)for(int side:new int[]{-1,1})seats.add(new Vec3d(side*1.1,2.05,z));return List.copyOf(seats);}
}
