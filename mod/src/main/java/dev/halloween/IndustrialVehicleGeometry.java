package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.util.math.*;
/** Original Soviet-era silhouettes. Closed solids, inset glazing and tiled atlas textures. */
final class IndustrialVehicleGeometry {
 record Part(String group,BlockState material,Box box,Vec3d pivot,org.joml.Quaternionf rotation,Vec3d origin,List<Vec3d> vertices){Part(String group,BlockState material,Box box,Vec3d pivot){this(group,material,box,pivot,null,Vec3d.ZERO,null);}Part(String group,BlockState material,Box box,Vec3d pivot,org.joml.Quaternionf rotation,Vec3d origin){this(group,material,box,pivot,rotation,origin,null);}}
 static Box bounds(Part part){var b=part.box;Box result=null;for(double x:new double[]{b.minX,b.maxX})for(double y:new double[]{b.minY,b.maxY})for(double z:new double[]{b.minZ,b.maxZ}){var v=new org.joml.Vector3f((float)x,(float)y,(float)z);if(part.rotation!=null)v.rotate(part.rotation);var q=new Vec3d(v.x,v.y,v.z).add(part.origin);var point=new Box(q,q);result=result==null?point:result.union(point);}return result;}
 static final Map<IndustrialMachines.Kind,List<Part>> CACHE=new EnumMap<>(IndustrialMachines.Kind.class);
 static List<Part> parts(IndustrialMachines.Kind kind){return CACHE.computeIfAbsent(kind,IndustrialVehicleGeometry::build);}
 static void box(List<Part> p,String group,Block b,double x0,double y0,double z0,double x1,double y1,double z1){p.add(new Part(group,b.getDefaultState(),new Box(x0,y0,z0,x1,y1,z1),Vec3d.ZERO));}
 static void wheel(List<Part> p,int id,double x,double y,double z,double radius,double width){var pivot=new Vec3d(x,y,z);String g="wheel_"+id;
  for(int n=0;n<16;n++){double a=n*Math.PI/8;var c=pivot.add(0,Math.sin(a)*radius*.78,Math.cos(a)*radius*.78);p.add(new Part(g,Blocks.BLACK_CONCRETE.getDefaultState(),new Box(c.add(-width/2,-radius*.22,-radius*.22),c.add(width/2,radius*.22,radius*.22)),pivot));}
  p.add(new Part(g,Blocks.SMOOTH_STONE.getDefaultState(),new Box(pivot.add(-width*.53,-radius*.35,-radius*.35),pivot.add(width*.53,radius*.35,radius*.35)),pivot));
  p.add(new Part(g,Blocks.POLISHED_BLACKSTONE.getDefaultState(),new Box(pivot.add(-width*.55,-.09,-.09),pivot.add(width*.55,.09,.09)),pivot));
 }
 static void lamp(List<Part> p,double x,double y,double z,boolean rear){box(p,"trim",rear?Blocks.RED_CONCRETE:Blocks.OCHRE_FROGLIGHT,x-.13,y-.1,z-.035,x+.13,y+.1,z+.035);}
 static void seat(List<Part> p,double x,double y,double z,Block material){box(p,"interior",material,x-.28,y-.14,z-.30,x+.28,y,z+.30);box(p,"interior",material,x-.28,y,z-.34,x+.28,y+.60,z-.25);box(p,"interior",Blocks.POLISHED_BLACKSTONE,x-.22,y-.3,z-.23,x+.22,y-.14,z+.23);}
 static List<Part> build(IndustrialMachines.Kind k){var p=new ArrayList<Part>();
  if(k==IndustrialMachines.Kind.FIGHTER){jet(p);return List.copyOf(p);}
  double w=k.width/2,l=k.length/2;boolean bus=k==IndustrialMachines.Kind.BUS,tank=k==IndustrialMachines.Kind.TANK,apc=k==IndustrialMachines.Kind.APC;
  Block body=tank||apc?Blocks.GREEN_TERRACOTTA:bus?Blocks.YELLOW_TERRACOTTA:Blocks.LIGHT_BLUE_TERRACOTTA;
  box(p,"frame",Blocks.POLISHED_BLACKSTONE,-w+.18,.38,-l+.12,w-.18,.58,l-.12);
  box(p,"engine",Blocks.DEEPSLATE_TILES,-.53,.58,l-1.15,.53,1.03,l-.35);
  if(!tank&&!apc)box(p,"body",body,-w,.58,-l,w,1.05,l);
  if(tank){
   // Hull and turret are shells; the crew does not clip through a solid opaque block.
   box(p,"body",body,-w,.58,-l,-w+.22,1.48,l);box(p,"body",body,w-.22,.58,-l,w,1.48,l);
   box(p,"body",body,-w,.58,-l,w,1.48,-l+.25);box(p,"body",body,-w,.58,l-.25,w,1.48,l);
   for(var roof:new Box[]{new Box(-w,1.38,-l,w,1.48,-1.15),new Box(-w,1.38,.85,w,1.48,l),new Box(-w,1.38,-1.15,-1,1.48,.85),new Box(1,1.38,-1.15,w,1.48,.85)})p.add(new Part("body",body.getDefaultState(),roof,Vec3d.ZERO));
   box(p,"turret",body,-1,1.48,-1.15,-.82,2.25,.85);box(p,"turret",body,.82,1.48,-1.15,1,2.25,.85);box(p,"turret",body,-1,1.48,-1.15,1,2.25,-.97);
   box(p,"turret",body,-1,1.48,.67,1,1.82,.85);box(p,"turret",body,-1,2.06,.67,1,2.25,.85);box(p,"glass",Blocks.BLACK_STAINED_GLASS,-.8,1.83,.69,.8,2.05,.73);
   box(p,"turret",body,-1,2.18,-1.15,1,2.28,.85);
   box(p,"turret",Blocks.POLISHED_DEEPSLATE,-.15,1.75,.85,.15,2,4.6);box(p,"turret",Blocks.BLACK_CONCRETE,-.11,1.79,4.59,.11,1.96,4.64);
   p.add(new Part("door_hatch",Blocks.IRON_BLOCK.getDefaultState(),new Box(-.55,2.29,-.5,.25,2.39,.15),new Vec3d(-.55,2.29,-.5)));
   for(int side:new int[]{-1,1}){double x=side*(w-.25);for(int j=0;j<6;j++)wheel(p,(side+1)*3+j,x,.62,-2.35+j*.94,.55,.42);for(int j=0;j<18;j++){double z=-2.65+j*.31;box(p,"track_bottom_"+side+"_"+j,Blocks.POLISHED_BLACKSTONE,x-.34,.12,z-.13,x+.34,.27,z+.13);box(p,"track_top_"+side+"_"+j,Blocks.POLISHED_BLACKSTONE,x-.34,1.03,z-.13,x+.34,1.17,z+.13);}
    box(p,"trim",body,side<0?-w-.10:w-.28,1.2,-l-.05,side<0?-w+.28:w+.10,1.35,l+.05);
    box(p,"trim",Blocks.IRON_BLOCK,side*.85-.36,1.49,-l+.15,side*.85+.36,1.83,-l+.75);
   }
  }else if(apc){
   box(p,"body",body,-w,.58,-l,w,1.05,l);
   for(int side:new int[]{-1,1}){double x=side*w;
    box(p,"body",body,x-.10,1.05,-l+.1,x+.10,2.4,-2.7);box(p,"body",body,x-.10,1.05,-1.3,x+.10,2.4,l-.35);
    door(p,side<0?"door_left":"door_right",body,new Box(x-.08,1.05,-2.7,x+.08,2.4,-1.3),new Vec3d(x,1.05,-1.3));
    for(int j=0;j<4;j++)wheel(p,(side+1)*2+j,side*(w-.05),.65,-2.5+j*1.65,.62,.38);
   }
   box(p,"body",body,-w+.1,1.05,-l,w-.1,2.4,-l+.15);box(p,"body",body,-w+.1,1.05,l-.35,w-.1,1.7,l-.15);box(p,"body",body,-w+.1,2.3,l-.35,w-.1,2.4,l-.15);
   box(p,"glass",Blocks.BLACK_STAINED_GLASS,-1,1.72,l-.32,1,2.28,l-.28);
   box(p,"body",body,-w+.1,2.4,-l+.1,w-.1,2.53,l-.35);
   box(p,"turret",Blocks.GREEN_TERRACOTTA,-.55,2.53,-.5,.55,3,.5);box(p,"turret",Blocks.POLISHED_DEEPSLATE,-.06,2.75,.45,.06,2.87,2.3);
  }else{
   double roof=bus?3.1:2.2,cabinFront=bus?l-.1:.95,cabinBack=bus?-l+.1:-1.3;
   box(p,"roof",bus?Blocks.SMOOTH_SANDSTONE:body,-w+.06,roof-.12,cabinBack,w-.06,roof,cabinFront);
   box(p,"body",body,-w,1.05,-l,w,1.24,cabinBack);
   box(p,"body",body,-w,1.05,cabinFront,w,1.24,l);
   box(p,"glass",Blocks.LIGHT_BLUE_STAINED_GLASS,-w+.12,1.25,cabinFront-.06,w-.12,roof-.16,cabinFront-.015);
   box(p,"glass",Blocks.LIGHT_BLUE_STAINED_GLASS,-w+.12,1.25,cabinBack+.015,w-.12,roof-.16,cabinBack+.06);
   for(int side:new int[]{-1,1}){
    double x=side*w;box(p,"pillars",bus?Blocks.SMOOTH_SANDSTONE:body,x-.055,1.05,cabinBack,x+.055,roof,cabinBack+.12);box(p,"pillars",bus?Blocks.SMOOTH_SANDSTONE:body,x-.055,1.05,cabinFront-.12,x+.055,roof,cabinFront);
    int windows=bus?7:2;double span=(cabinFront-cabinBack)/windows;
    for(int j=0;j<windows;j++){double z=cabinBack+j*span;boolean entrance=bus&&side==1&&(j==0||j==3||j==6);
     if(entrance){var pivot=new Vec3d(x,.62,z+.02);String group="door_bus_"+j;
      door(p,group,Blocks.SMOOTH_SANDSTONE,new Box(x-.05,.62,z+.02,x+.05,1.5,z+span-.02),pivot);
      door(p,group,Blocks.SMOOTH_SANDSTONE,new Box(x-.05,1.5,z+.02,x+.05,roof-.15,z+.1),pivot);
      door(p,group,Blocks.SMOOTH_SANDSTONE,new Box(x-.05,1.5,z+span-.1,x+.05,roof-.15,z+span-.02),pivot);
      door(p,group,Blocks.SMOOTH_SANDSTONE,new Box(x-.05,roof-.25,z+.1,x+.05,roof-.15,z+span-.1),pivot);
      p.add(new Part(group,Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState(),new Box(x-.025,1.52,z+.11,x+.025,roof-.26,z+span-.11),pivot));continue;}
     box(p,"pillars",bus?Blocks.SMOOTH_SANDSTONE:body,x-.04,1.05,z,x+.04,roof-.1,z+.07);
     var windowBox=new Box(x-.025,bus?1.52:1.16,z+.09,x+.025,roof-.16,z+span-.03);if(bus)p.add(new Part("glass",Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState(),windowBox,Vec3d.ZERO));else p.add(new Part("door_"+(side<0?"left":"right")+j,Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState(),windowBox,new Vec3d(x,.66,z+span-.03)));
     if(bus)box(p,"body",body,x-.06,.6,z+.07,x+.06,1.5,z+span-.02);
     else door(p,"door_"+(side<0?"left":"right")+j,body,new Box(x-.04,.66,z+.08,x+.04,1.15,z+span-.03),new Vec3d(x,.66,z+span-.03));
    }
    if(bus){for(double z:new double[]{-3.05,2.95})wheel(p,(side<0?0:2)+(z<0?0:1),side*(w-.04),.60,z,.57,.34);}else for(double z:new double[]{-1.95,1.9})wheel(p,(side<0?0:2)+(z<0?0:1),side*(w-.04),.43,z,.4,.25);
    box(p,"trim",Blocks.IRON_BLOCK,x-.025,.95,-l+.1,x+.025,1.01,l-.1);
    box(p,"trim",Blocks.POLISHED_BLACKSTONE,x-.19,1.5,cabinFront-.38,x+.19,1.64,cabinFront-.23);
   }
   for(int i=0;i<k.seats;i++){var s=k.seat(i);seat(p,s.x,s.y,s.z,bus?Blocks.BROWN_TERRACOTTA:Blocks.BLACK_WOOL);}
   box(p,"interior",Blocks.POLISHED_BLACKSTONE,-w+.12,bus?1.35:1.05,cabinFront-.40,w-.12,bus?1.5:1.19,cabinFront-.15);
   var driver=k.seat(0);var wheelCenter=driver.add(0,.5,.67);
   for(int n=0;n<16;n++){double a=n*Math.PI/8;var c=wheelCenter.add(Math.cos(a)*.25,Math.sin(a)*.25,0);p.add(new Part("steering",Blocks.POLISHED_BLACKSTONE.getDefaultState(),new Box(c.add(-.045,-.045,-.035),c.add(.045,.045,.035)),wheelCenter));}
   p.add(new Part("steering",Blocks.POLISHED_BLACKSTONE.getDefaultState(),new Box(wheelCenter.add(-.24,-.025,-.025),wheelCenter.add(.24,.025,.025)),wheelCenter));
   // Radiator grille, bumpers, number plate and separate lamps keep the silhouette readable at distance.
   box(p,"trim",Blocks.IRON_BLOCK,-w-.06,.56,l-.06,w+.06,.68,l+.08);box(p,"trim",Blocks.IRON_BLOCK,-w-.06,.56,-l-.08,w+.06,.68,-l+.06);
   for(int i=-2;i<=2;i++)box(p,"trim",Blocks.POLISHED_BLACKSTONE,i*.16-.025,.78,l+.008,i*.16+.025,.96,l+.034);
   box(p,"trim",Blocks.WHITE_CONCRETE,-.29,.69,l+.04,.29,.77,l+.055);
   if(bus){box(p,"trim",Blocks.BLACK_CONCRETE,-.85,2.64,l-.08,.85,2.97,l-.06);box(p,"trim",Blocks.SEA_LANTERN,-.74,2.72,l-.055,.74,2.82,l-.045);}
  }
  for(int side:new int[]{-1,1}){lamp(p,side*(w-.3),1.02,l+.02,false);lamp(p,side*(w-.3),.92,-l-.02,true);}
  return List.copyOf(p);
 }
 static void door(List<Part> p,String group,Block b,Box box,Vec3d pivot){p.add(new Part(group,b.getDefaultState(),box,pivot));}
 static void ring(List<Part> p,String group,Block material,double radius,double y,double z0,double z1){for(int n=0;n<16;n++){float angle=(float)(n*Math.PI/8);if(group.equals("body")&&z0>=.99&&z0<2.6&&Math.sin(angle)>.3)continue;p.add(new Part(group,material.getDefaultState(),new Box(radius*.80,-radius*.202,z0,radius,radius*.202,z1),Vec3d.ZERO,new org.joml.Quaternionf().rotationZ(angle),new Vec3d(0,y,0)));}}
 static void jet(List<Part> p){
  box(p,"frame",Blocks.IRON_BLOCK,-.3,.9,-5.1,.3,1.15,5.2);
  for(int i=0;i<10;i++){double z=-4.6+i*.8;ring(p,"body",Blocks.LIGHT_GRAY_CONCRETE,.67,1.55,z,z+.81);}
  for(int i=0;i<12;i++){double z=3.4+i*.26;ring(p,"body",Blocks.LIGHT_GRAY_CONCRETE,.67*(1-i/13.),1.55,z,z+.27);}
  for(int i=0;i<5;i++)ring(p,"body",Blocks.LIGHT_GRAY_CONCRETE,.5+i*.034,1.55,-6+i*.28,-5.71+i*.28);
  ring(p,"engine",Blocks.POLISHED_DEEPSLATE,.51,1.55,-6.12,-5.99);ring(p,"engine",Blocks.BLACK_CONCRETE,.36,1.55,-6.15,-6.11);
  // Raised glazing and narrow frames, with enough headroom for the actual seated pilot.
  box(p,"door_canopy",Blocks.LIGHT_BLUE_STAINED_GLASS,-.43,1.94,1,.43,2.72,2.55);
  box(p,"door_canopy",Blocks.LIGHT_BLUE_STAINED_GLASS,-.34,2.1,2.55,.34,2.48,2.92);
  box(p,"door_canopy",Blocks.LIGHT_GRAY_CONCRETE,-.46,1.90,.95,.46,2.0,2.93);
  for(double z:new double[]{1,2.5})box(p,"door_canopy",Blocks.IRON_BLOCK,-.45,2.68,z,.45,2.75,z+.04);
  for(int side:new int[]{-1,1}){
   double root=side*.6,tip=side*4.8;var vertices=List.of(new Vec3d(root,1.35,2.15),new Vec3d(tip,1.47,-1.42),new Vec3d(tip,1.47,-2.52),new Vec3d(root,1.35,-2.1),new Vec3d(root,1.48,2.15),new Vec3d(tip,1.60,-1.42),new Vec3d(tip,1.60,-2.52),new Vec3d(root,1.48,-2.1));
   p.add(new Part("wings",Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(),new Box(Math.min(root,tip),1.35,-2.52,Math.max(root,tip),1.60,2.15),Vec3d.ZERO,null,Vec3d.ZERO,vertices));
  }
  var fin=List.of(new Vec3d(-.075,2,-5.5),new Vec3d(-.075,4.09,-5.06),
      new Vec3d(-.075,4.09,-4.58),new Vec3d(-.075,2,-3.7),
      new Vec3d(.075,2,-5.5),new Vec3d(.075,4.09,-5.06),
      new Vec3d(.075,4.09,-4.58),new Vec3d(.075,2,-3.7));
  p.add(new Part("tail",Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(),new Box(-.075,2,-5.5,.075,4.09,-3.7),
      Vec3d.ZERO,null,Vec3d.ZERO,fin));
  for(int side:new int[]{-1,1}){
   double root=side*.45,tip=side*2;var vertices=List.of(new Vec3d(root,1.60,-3.5),new Vec3d(tip,1.60,-4.5),new Vec3d(tip,1.60,-5.53),new Vec3d(root,1.60,-5.35),new Vec3d(root,1.72,-3.5),new Vec3d(tip,1.72,-4.5),new Vec3d(tip,1.72,-5.53),new Vec3d(root,1.72,-5.35));
   p.add(new Part("tail",Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(),new Box(Math.min(root,tip),1.6,-5.53,Math.max(root,tip),1.72,-3.5),Vec3d.ZERO,null,Vec3d.ZERO,vertices));
  }
  box(p,"insignia",Blocks.RED_CONCRETE,-.08,3.15,-4.93,.08,3.45,-4.0);
  for(int side:new int[]{-1,1}){double x=side*3;box(p,"insignia",Blocks.RED_CONCRETE,x-.26,1.58,-.8,x+.26,1.60,-.28);box(p,"insignia",Blocks.RED_CONCRETE,x-.42,1.58,-.65,x+.42,1.60,-.43);}
  for(int i=0;i<3;i++){double x=i==0?0:i==1?-.95:.95,z=i==0?3.2:-1.7;box(p,"gear",Blocks.IRON_BLOCK,x-.05,.35,z-.05,x+.05,1.1,z+.05);wheel(p,i,x,.31,z,.3,.18);}
  seat(p,0,1.25,1.9,Blocks.BLACK_WOOL);
  for(int i=0;i<p.size();i++){
   var part=p.get(i);
   if(part.group.equals("door_canopy"))p.set(i,new Part(part.group,part.material,part.box,new Vec3d(0,1.90,.95)));
  }
 }
 private IndustrialVehicleGeometry(){}
}
