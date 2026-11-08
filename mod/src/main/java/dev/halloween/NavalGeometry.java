package dev.halloween;

import java.util.*;
import net.minecraft.util.math.*;

/** The ship's local reference frame is shared by model, collision, helm and passenger movement. */
public final class NavalGeometry {
 private static final Map<Integer,List<Box>> CELLS=new java.util.concurrent.ConcurrentHashMap<>();
 public static double sx(int kind){return kind==3?.78:1;}
 public static double sz(int kind){return kind==3?.70:1;}
 public static Vec3d local(int kind,double x,double y,double z){return new Vec3d(x*sx(kind),y,z*sz(kind));}
 public static Box bounds(int kind){return new Box(-8*sx(kind),-2.5,-15.6*sz(kind),8*sx(kind),kind==3?21:24,20*sz(kind));}
 public static Box hull(int kind){return new Box(-4.3*sx(kind),-2.3,-15*sz(kind),4.3*sx(kind),3.85,15.3*sz(kind));}
 public static Vec3d helm(int kind){return local(kind,0,6.55,-11.9);}
 public static Vec3d door(int kind){return local(kind,0,4.1,-7);}
 public static List<Box> barrelBoxes(int kind){var boxes=new ArrayList<Box>();for(double x:new double[]{-1.9,1.9})for(double z:new double[]{3.2,4.6,6}){boxes.add(new Box(local(kind,x-.48,.95,z-.48),local(kind,x+.48,1.95,z+.48)));if(z==4.6)boxes.add(new Box(local(kind,x-.45,1.95,z-.45),local(kind,x+.45,2.65,z+.45)));}return List.copyOf(boxes);}
 public static Vec3d cargo(int kind){return local(kind,0,1.5,4.5);}
 public static int cargoSize(int kind){return kind==3?27:54;}
 public static List<Vec3d> seats(int kind){var seats=new ArrayList<Vec3d>();for(double x:new double[]{-2.2,2.2})for(double z:new double[]{-4.9,1.8,5.5})seats.add(local(kind,x,3.55,z));return seats;}
 public static boolean inside(RailVehicleEntity ship,Vec3d p){if(!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||p.y<.38||p.y>8.8)return false;int kind=ship.kind();if(Math.abs(p.x)<=4.15*sx(kind)&&p.z>=-14.3*sz(kind)&&p.z<=15.2*sz(kind))return true;return ship.gangwayDeployed()&&Math.abs(p.x)<=7.8*sx(kind)&&p.z>=-6.5*sz(kind)&&p.z<=-4.3*sz(kind)&&p.y<=4.5;}
 public static List<Box> cells(int kind,boolean door,boolean gangway){return CELLS.computeIfAbsent(kind*4+(door?1:0)+(gangway?2:0),key->{var cells=new ArrayList<Box>();for(var part:RailGeometry.parts(kind)){
  if(!part.solid()||part.group().equals("gangway")&&!gangway)continue;var b=RailGeometry.partBox(part,door);int nx=Math.max(1,(int)Math.ceil(b.getLengthX()*2)),nz=Math.max(1,(int)Math.ceil(b.getLengthZ()*2));
  for(int x=0;x<nx;x++)for(int z=0;z<nz;z++)cells.add(new Box(MathHelper.lerp(x/(double)nx,b.minX,b.maxX),b.minY,MathHelper.lerp(z/(double)nz,b.minZ,b.maxZ),MathHelper.lerp((x+1)/(double)nx,b.minX,b.maxX),b.maxY,MathHelper.lerp((z+1)/(double)nz,b.minZ,b.maxZ)));
 }return List.copyOf(cells);});}
 public static boolean onFloor(RailVehicleEntity ship,Vec3d p){for(var b:cells(ship.kind(),ship.doorsOpen(),ship.gangwayDeployed()))if(Math.abs(p.y-b.maxY)<.085&&p.x>b.minX-.27&&p.x<b.maxX+.27&&p.z>b.minZ-.27&&p.z<b.maxZ+.27)return true;return false;}
 public static Vec3d dismount(int kind,Vec3d seat){if(seat.y>6)return local(kind,0,6,-9.2);return new Vec3d(Math.copySign(.9*sx(kind),seat.x),Math.max(.5,seat.y-.55),seat.z);}
 private NavalGeometry(){}
}
