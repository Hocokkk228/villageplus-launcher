package dev.halloween;
import java.util.*;
import net.minecraft.util.math.*;
/** Shared arc-length sampling: track, carriage bodies and couplings follow the same curve. */
public final class RailPath {
 public final List<Vec3d> controls,points;public final double[] distances;public final double length;public final boolean closed;
 public RailPath(List<Vec3d> controls){
  if(controls.size()<4||controls.size()%4!=0)throw new IllegalArgumentException("cubic controls");this.controls=List.copyOf(controls);var out=new ArrayList<Vec3d>();
  for(int k=0;k<controls.size();k+=4){var a=controls.get(k);var b=controls.get(k+1);var c=controls.get(k+2);var d=controls.get(k+3);int steps=Math.max(8,(int)Math.ceil((a.distanceTo(b)+b.distanceTo(c)+c.distanceTo(d))*2));
   for(int i=k==0?0:1;i<=steps;i++){double t=i/(double)steps,u=1-t;out.add(a.multiply(u*u*u).add(b.multiply(3*u*u*t)).add(c.multiply(3*u*t*t)).add(d.multiply(t*t*t)));}
  }points=List.copyOf(out);distances=new double[points.size()];for(int i=1;i<points.size();i++)distances[i]=distances[i-1]+points.get(i).distanceTo(points.get(i-1));length=distances[distances.length-1];closed=points.getFirst().squaredDistanceTo(points.getLast())<.01;
 }
 public static List<Vec3d> straight(Vec3d a,Vec3d b){return List.of(a,a.lerp(b,1./3),a.lerp(b,2./3),b);}
 public Vec3d center(double distance){double d=closed?wrap(distance):Math.clamp(distance,0,length);int i=Arrays.binarySearch(distances,d);if(i>=0)return points.get(i);i=Math.clamp(-i-1,1,points.size()-1);double span=distances[i]-distances[i-1];return points.get(i-1).lerp(points.get(i),span<1e-8?0:(d-distances[i-1])/span);}
 public double wrap(double distance){return length==0?0:((distance%length)+length)%length;}
 public RailPath reversed(){var c=new ArrayList<>(controls);Collections.reverse(c);return new RailPath(c);}
 public double closest(Vec3d point){double best=Double.MAX_VALUE,at=0;for(int i=0;i<points.size();i++){double d=points.get(i).squaredDistanceTo(point);if(d<best){best=d;at=distances[i];}}return at;}
 public Vec3d tangent(double distance){var v=center(distance+.25).subtract(center(distance-.25));return v.lengthSquared()<1e-8?new Vec3d(0,0,1):v.normalize();}
 public Vec3d at(double distance,double lane){var t=tangent(distance);return center(distance).add(t.z*lane,0,-t.x*lane);}
 public float yaw(double distance){var t=tangent(distance);return (float)Math.toDegrees(Math.atan2(-t.x,t.z));}
}
