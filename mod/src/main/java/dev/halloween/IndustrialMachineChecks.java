package dev.halloween;
import java.util.*;
import java.nio.file.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Opt-in ticking tests, independent of the epoch soak and the player's worlds. */
final class IndustrialMachineChecks {
 static int ticks,product;static boolean started,done,reloaded;static MachineAssemblyEntity line;static final List<IndustrialVehicleEntity> vehicles=new ArrayList<>();static final List<Vec3d> starts=new ArrayList<>();
 static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
 static void register(){if(!Boolean.getBoolean("halloween.industrialMachinesTest"))return;
  ServerTickEvents.END_SERVER_TICK.register(server->{if(done||server.getTicks()<40)return;var w=server.getOverworld();try{
   if(!started){started=true;server.getTickManager().setTickRate(1000);for(int x=-2;x<=17;x++)for(int z=-3;z<=11;z++)w.setChunkForced(x,z,true);
    for(int x=-10;x<195;x++)for(int z=-12;z<140;z++)w.setBlockState(new BlockPos(x,220,z),Blocks.STONE.getDefaultState());
    for(var kind:IndustrialMachines.Kind.values()){
     var at=new Vec3d(kind.ordinal()*38+.5,221,.5);var v=IndustrialMachines.spawn(w,kind,at,0);require(v!=null,"vehicle spawns with physical clearance: "+kind);vehicles.add(v);starts.add(at);
     var driver=EntityType.VILLAGER.create(w,SpawnReason.EVENT);driver.setAiDisabled(true);driver.setPosition(at);w.spawnEntity(driver);require(v.board(driver,true),"driver has a reserved seat");
     if(kind.seats>1){var passenger=EntityType.VILLAGER.create(w,SpawnReason.EVENT);passenger.setAiDisabled(true);passenger.setPosition(at);w.spawnEntity(passenger);require(v.board(passenger,false),"separate passenger seat");require(!v.isDriver(passenger),"passenger cannot become driver simply by boarding");}
     v.doors(false);for(var part:IndustrialVehicleGeometry.parts(kind))require(part.box().getLengthX()>0&&part.box().getLengthY()>0&&part.box().getLengthZ()>0,"closed, nondegenerate model solids");
    }
    var pos=new BlockPos(205,221,0);w.setBlockState(pos,MachineAssembly.BLOCK.getDefaultState());line=(MachineAssemblyEntity)w.getBlockEntity(pos);supply(line,0);export();verifyWorkshop(w);log("PASS all five registered vehicle models, independent seats, workshop staircase, assembly access and supported lamps");
   }
   ticks++;
   if(ticks<=120)for(int i=0;i<vehicles.size();i++){var v=vehicles.get(i);v.input=1;v.brake=false;v.lastControl=w.getTime();for(var p:v.getPassengerList()){
    require(p.isAlive(),"vehicle does not injure its occupants");var expected=v.getEntityPos().add(RailGeometry.rotate(v.kind().seat(v.seat(p)).add(0,-.8,0),v.getYaw()));require(p.getEntityPos().squaredDistanceTo(expected)<.001,"physical seat transform: "+v.kind()+" actual="+p.getEntityPos()+" expected="+expected);require(v.renderedPassengerPos(p,1).squaredDistanceTo(expected)<.001,"render and physical seat transform match");}}
   if(ticks==125){for(int i=0;i<vehicles.size();i++){var v=vehicles.get(i);require(v.getEntityPos().distanceTo(starts.get(i))>3,"vehicle really moved: "+v.kind());v.input=0;v.brake=true;v.lastControl=w.getTime();}log("PASS physical driving for all five vehicles and stable passenger attachments");}
   if(product<7){line.work(200);
    if(!reloaded&&line.stage>=2){var saved=line.createNbtWithIdentifyingData(w.getRegistryManager());int stage=line.stage,progress=line.progress;var restored=BlockEntity.createFromNbt(line.getPos(),line.getCachedState(),saved,w.getRegistryManager());require(restored instanceof MachineAssemblyEntity,"assembly survives NBT restart");w.getWorldChunk(line.getPos()).setBlockEntity(restored);line=(MachineAssemblyEntity)restored;require(line.stage==stage&&line.progress==progress,"completed stages survive restart");reloaded=true;}
    if(line.completed>product){var output=product<5?IndustrialMachines.PRODUCTS.get(IndustrialMachines.Kind.values()[product]):product==5?IndustrialElectronics.TELEVISION.asItem():IndustrialElectronics.RADIO.asItem();require(line.count(output)==1,"exactly one finished product "+product);for(var stack:line.getHeldStacks())require(stack.isEmpty()||stack.isOf(output),"all exact raw materials consumed, no free intermediate or phantom stock: "+stack);log("PASS product "+product+": exact material bill, staged fabrication, restart and single output");product++;if(product<7){line.clear();line.product=product;supply(line,product);reloaded=false;}}
   }
   if(product==7&&ticks>160&&CityTrafficChecks.tick(w)){verifyOutputBackpressure(w);done=true;log("ALL INDUSTRIAL MACHINE CHECKS PASSED");server.stop(false);}
   if(ticks>4000)throw new AssertionError("production timed out: "+line.status());
  }catch(Throwable e){done=true;org.slf4j.LoggerFactory.getLogger("industrial-machines-test").error("FAIL",e);server.stop(false);}});
 }
 static void supply(MachineAssemblyEntity line,int product){line.product=product;var raw=new LinkedHashMap<Item,Integer>();for(var component:line.bill()){var recipe=IndustrialMachines.RECIPES.stream().filter(r->r.part().equals(component.getKey())).findFirst().orElseThrow();for(var cost:recipe.inputs().entrySet())raw.merge(cost.getKey(),cost.getValue()*component.getValue(),Integer::sum);}for(var cost:raw.entrySet())require(RailWorks.add(line,cost.getKey(),cost.getValue()),"raw bill fits warehouse");}
 static void verifyOutputBackpressure(ServerWorld w){line.clear();line.product=0;line.stage=line.bill().size();line.progress=0;for(int i=0;i<line.size();i++)line.setStack(i,new ItemStack(Items.COBBLESTONE,64));line.lastWork=Long.MIN_VALUE;require(!line.work(10)&&line.stage==line.bill().size(),"full output retains installed components");line.setStack(0,ItemStack.EMPTY);line.lastWork=Long.MIN_VALUE;require(line.work(10),"output commits when a slot opens");int count=line.count(IndustrialMachines.PRODUCTS.get(line.kind()));line.lastWork=Long.MIN_VALUE;line.work(10);require(line.count(IndustrialMachines.PRODUCTS.get(line.kind()))==count,"output cannot duplicate after commit");log("PASS full-output backpressure and no duplicate products");}
 static void verifyWorkshop(ServerWorld w){var base=new BlockPos(225,220,60);var plan=IndustrialPlantPlans.build(base,2);for(var e:plan.entrySet())w.setBlockState(e.getKey(),e.getValue(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
  var walker=new VillagerEntity(EntityType.VILLAGER,w);walker.setPosition(Vec3d.ofBottomCenter(base.add(3,1,2)));walker.setOnGround(true);SpasInteriors.navigation(walker);
  for(var target:List.of(base.add(3,6,20),base.add(18,1,22))){var path=walker.getNavigation().findPathTo(target,0);require(path!=null&&path.reachesTarget(),"factory interior path reaches "+target.subtract(base));}
  for(var e:plan.entrySet())if(e.getValue().getBlock() instanceof LanternBlock&&e.getValue().get(LanternBlock.HANGING))require(e.getValue().canPlaceAt(w,e.getKey()),"factory hanging lamp has support at "+e.getKey());
 }
 static void export()throws Exception{var root=new com.google.gson.JsonObject();for(var kind:IndustrialMachines.Kind.values()){var list=new com.google.gson.JsonArray();for(var p:IndustrialVehicleGeometry.parts(kind)){var o=new com.google.gson.JsonObject();o.addProperty("group",p.group());o.addProperty("block",net.minecraft.registry.Registries.BLOCK.getId(p.material().getBlock()).toString());var bounds=IndustrialVehicleGeometry.bounds(p);var b=new com.google.gson.JsonArray();for(double value:new double[]{bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ})b.add(value);o.add("box",b);var pivot=new com.google.gson.JsonArray();for(double value:new double[]{p.pivot().x,p.pivot().y,p.pivot().z})pivot.add(value);o.add("pivot",pivot);list.add(o);}root.add(kind.id,list);}var path=Path.of("industrial-vehicles.json");Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));}
 static void log(String s){org.slf4j.LoggerFactory.getLogger("industrial-machines-test").info(s);}
 private IndustrialMachineChecks(){}
}
