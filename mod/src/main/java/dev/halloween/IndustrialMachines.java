package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Shared catalogue for the production line, physical intermediate parts and finished vehicles. */
public final class IndustrialMachines {
 public enum Kind {
  SEDAN("sedan","Легковой автомобиль",2.35,5.8,2.3,4,.55),
  BUS("city_bus","Городской автобус",2.65,9.8,3.25,15,.39),
  TANK("battle_tank","Средний танк",3.45,6.8,2.9,3,.30),
  APC("armored_carrier","Бронетранспортёр",2.95,7.2,3.1,8,.45),
  FIGHTER("jet_fighter","Реактивный истребитель",9.6,14,4.3,1,1.65);
  public final String id,title;public final double width,length,height,maxSpeed;public final int seats;
  Kind(String id,String title,double width,double length,double height,int seats,double speed){this.id=id;this.title=title;this.width=width;this.length=length;this.height=height;this.seats=seats;maxSpeed=speed;}
  public Box bounds(){return new Box(-width/2,0,-length/2,width/2,height,length/2);}
  public Vec3d seat(int index){
   if(this==FIGHTER)return new Vec3d(0,1.25,2.2);
   if(this==BUS){if(index==0)return new Vec3d(.72,1.25,3.7);return new Vec3d((index%2==0?-.82:.82),1.25,2.6-((index-1)/2)*.95);}
   if(this==TANK)return new Vec3d(index==0?-.48:index==1?.48:0,.85,index<2?.3:-.65);
   if(this==APC)return index==0?new Vec3d(.65,1,1.9):new Vec3d(index%2==0?-.85:.85,1.1,.8-((index-1)/2)*.75);
   return new Vec3d(index%2==0?.5:-.5,.72,index<2?.6:-.75);
  }
 }
 public record PartRecipe(String part,Map<Item,Integer> inputs,int work){public PartRecipe{inputs=Map.copyOf(inputs);}}
 public static final Map<String,Item> PARTS=new LinkedHashMap<>();
 public static final Map<Kind,Item> PRODUCTS=new EnumMap<>(Kind.class);
 public static final List<PartRecipe> RECIPES=new ArrayList<>();
 public static EntityType<IndustrialVehicleEntity> VEHICLE;
 static Identifier id(String name){return Identifier.of("halloween",name);}
 static Item part(String name){var key=RegistryKey.of(RegistryKeys.ITEM,id(name));return Registry.register(Registries.ITEM,key,new Item(new Item.Settings().registryKey(key)));}
 static void register(){
  for(String name:List.of("machine_frame","combustion_engine","gearbox","road_wheel","vehicle_axle","vehicle_door","armor_plate","turret_assembly","track_link","wing_panel","jet_engine","cockpit_glass","circuit_board","picture_tube","loudspeaker","appliance_cabinet"))PARTS.put(name,part(name));
  var key=RegistryKey.of(RegistryKeys.ENTITY_TYPE,id("industrial_vehicle"));
  VEHICLE=Registry.register(Registries.ENTITY_TYPE,key,EntityType.Builder.<IndustrialVehicleEntity>create(IndustrialVehicleEntity::new,SpawnGroup.MISC).dimensions(2.05f,1.9f).maxTrackingRange(12).trackingTickInterval(1).build(key));
  for(var kind:Kind.values()){
   var itemKey=RegistryKey.of(RegistryKeys.ITEM,id(kind.id));
   PRODUCTS.put(kind,Registry.register(Registries.ITEM,itemKey,new Item(new Item.Settings().registryKey(itemKey).maxCount(1)){
    @Override public ActionResult useOnBlock(ItemUsageContext context){if(!(context.getWorld() instanceof ServerWorld world))return ActionResult.SUCCESS;
     var at=Vec3d.ofBottomCenter(context.getBlockPos().offset(context.getSide()));var vehicle=spawn(world,kind,at,context.getPlayerYaw());
     if(vehicle==null)return ActionResult.FAIL;if(context.getPlayer()==null||!context.getPlayer().isCreative())context.getStack().decrement(1);return ActionResult.SUCCESS;
    }
   }));
  }
  recipe("appliance_cabinet",35,Items.OAK_PLANKS,8);
  recipe("machine_frame",80,Items.IRON_INGOT,12,Items.COPPER_INGOT,2);
  recipe("combustion_engine",100,Items.IRON_INGOT,8,Items.COPPER_INGOT,4,Items.REDSTONE,2);
  recipe("gearbox",60,Items.IRON_INGOT,5,Items.COPPER_INGOT,2);
  recipe("road_wheel",35,Items.IRON_INGOT,2,Items.LEATHER,2,Items.COAL,1);
  recipe("vehicle_axle",45,Items.IRON_INGOT,4);
  recipe("vehicle_door",40,Items.IRON_INGOT,3,Items.GLASS_PANE,2);
  recipe("armor_plate",70,Items.IRON_INGOT,8,Items.COAL,2);
  recipe("turret_assembly",120,Items.IRON_INGOT,16,Items.COPPER_INGOT,4);
  recipe("track_link",25,Items.IRON_INGOT,2);
  recipe("wing_panel",70,Items.IRON_INGOT,6,Items.COPPER_INGOT,2);
  recipe("jet_engine",160,Items.IRON_INGOT,16,Items.COPPER_INGOT,8,Items.REDSTONE,4);
  recipe("cockpit_glass",50,Items.GLASS,6,Items.IRON_INGOT,2);
  recipe("circuit_board",40,Items.COPPER_INGOT,2,Items.REDSTONE,3,Items.GLASS,1);
  recipe("picture_tube",70,Items.GLASS,4,Items.COPPER_INGOT,2,Items.REDSTONE,2);
  recipe("loudspeaker",30,Items.COPPER_INGOT,1,Items.PAPER,2,Items.IRON_INGOT,1);
  ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e->{PARTS.values().forEach(e::add);PRODUCTS.values().forEach(e::add);});
  IndustrialVehicleControls.register();IndustrialTraffic.register();
 }
 static void recipe(String name,int work,Object... costs){var map=new LinkedHashMap<Item,Integer>();for(int i=0;i<costs.length;i+=2)map.put((Item)costs[i],(Integer)costs[i+1]);RECIPES.add(new PartRecipe(name,map,work));}
 static Map<String,Integer> bill(Kind kind){
  var b=new LinkedHashMap<String,Integer>();b.put("machine_frame",1);b.put("vehicle_axle",kind==Kind.APC?4:kind==Kind.BUS?3:2);b.put("gearbox",1);
  b.put(kind==Kind.FIGHTER?"jet_engine":"combustion_engine",kind==Kind.TANK?2:1);
  if(kind==Kind.FIGHTER){b.put("wing_panel",6);b.put("cockpit_glass",1);b.put("road_wheel",3);b.put("circuit_board",4);}
  else if(kind==Kind.TANK){b.put("track_link",32);b.put("armor_plate",12);b.put("turret_assembly",1);}
  else{b.put("road_wheel",kind==Kind.APC?8:kind==Kind.BUS?6:4);b.put("vehicle_door",kind==Kind.BUS?3:4);if(kind==Kind.APC)b.put("armor_plate",8);}
  return b;
 }
 static IndustrialVehicleEntity spawn(ServerWorld world,Kind kind,Vec3d at,float yaw){
  var v=VEHICLE.create(world,SpawnReason.EVENT);if(v==null)return null;v.configure(kind,at,yaw);
  if(!world.isSpaceEmpty(v,v.getBoundingBox())||!world.getOtherEntities(v,v.getBoundingBox()).isEmpty())return null;
  return world.spawnEntity(v)?v:null;
 }
 private IndustrialMachines(){}
}
