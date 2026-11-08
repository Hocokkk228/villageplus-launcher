package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.*;
/** Actual villager movement and warehouse pickup, rather than injecting parts into a completed line. */
final class MachineFactoryFlowChecks {
 static boolean started,done;static int age;static MachineAssemblyEntity line;static VillageMemory.Team team;static VillageEraData.Settlement settlement;static VillagerEntity worker;static Vec3d last;static double walked;static final Set<Item> products=new HashSet<>();
 static void register(){if(!Boolean.getBoolean("halloween.machineFactoryFlowTest"))return;
  ServerTickEvents.END_SERVER_TICK.register(server->{if(done||server.getTicks()<40)return;var w=server.getOverworld();try{
   if(!started){started=true;server.getTickManager().setTickRate(1000);for(int x=-4;x<=4;x++)for(int z=-2;z<=8;z++)w.setChunkForced(x,z,true);for(int x=-45;x<=45;x++)for(int z=0;z<=100;z++)w.setBlockState(new BlockPos(x,220,z),Blocks.STONE.getDefaultState());
    var base=new BlockPos(0,220,40);for(var e:IndustrialPlantPlans.build(base,0).entrySet())w.setBlockState(e.getKey(),e.getValue(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
    team=new VillageMemory.Team(520551,base,0);w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).teams.add(team);settlement=VillageEraData.get(w).settlement(team);settlement.era=2;settlement.vault=new VillageEraData.Vault(new BlockPos(-20,221,50),0,"",-1,true);settlement.xp.put(VillageFastEras.FLAG,1);
    var stock=new ArrayList<Inventory>();for(int i=0;i<6;i++){var p=VillageEconomy.barrel(settlement,i);w.setBlockState(p,Blocks.BARREL.getDefaultState());stock.add((Inventory)w.getBlockEntity(p));}
    line=(MachineAssemblyEntity)w.getBlockEntity(base.add(20,1,22));var raw=new LinkedHashMap<Item,Integer>();for(int product=0;product<7;product++){line.product=product;for(var part:line.bill()){var recipe=IndustrialMachines.RECIPES.stream().filter(r->r.part().equals(part.getKey())).findFirst().orElseThrow();for(var e:recipe.inputs().entrySet())raw.merge(e.getKey(),e.getValue()*part.getValue(),Integer::sum);}}
    for(var entry:raw.entrySet()){int left=entry.getValue();for(var inv:stock){int n=Math.min(left,RailWorks.free(inv,entry.getKey()));RailWorks.add(inv,entry.getKey(),n);left-=n;}if(left!=0)throw new AssertionError("raw material fixture overflow");}line.product=0;
    worker=EntityType.VILLAGER.create(w,SpawnReason.EVENT);worker.setVillagerData(worker.getVillagerData().withProfession(Registries.VILLAGER_PROFESSION.getOrThrow(net.minecraft.village.VillagerProfession.TOOLSMITH)));worker.setPosition(Vec3d.ofBottomCenter(VillageEconomy.barrel(settlement,3).east(2)));w.spawnEntity(worker);last=worker.getEntityPos();
   }
   age++;w.setTimeOfDay(6000);walked+=last.distanceTo(worker.getEntityPos());last=worker.getEntityPos();
   int kind=line.completed<2?0:line.completed<4?1:line.completed<5?2:3;
   var plant=new IndustrialPlants.Plant(team.village,kind,new BlockPos(0,220,40),worker.getUuidAsString(),List.of());
   if(age%10==0)IndustrialPlants.operate(w,team,settlement,plant);
   for(int i=0;i<line.size();i++){var stack=line.getStack(i);if(IndustrialMachines.PRODUCTS.containsValue(stack.getItem())||stack.isOf(IndustrialElectronics.TELEVISION.asItem())||stack.isOf(IndustrialElectronics.RADIO.asItem()))products.add(stack.getItem());}
   if(products.size()==7){if(walked<60)throw new AssertionError("worker skipped actual delivery route");done=true;org.slf4j.LoggerFactory.getLogger("machine-factory-flow-test").info("PASS actual worker hauled warehouse stock, fabricated parts and assembled all seven products in {} ticks, walked {} blocks",age,Math.round(walked));server.stop(false);}
   if(age%1000==0)org.slf4j.LoggerFactory.getLogger("machine-factory-flow-test").info("tick={} products={} stage={} worker={} inventory={} path={}",age,products.size(),line.status(),worker.getBlockPos(),java.util.stream.IntStream.range(0,worker.getInventory().size()).mapToObj(i->worker.getInventory().getStack(i).toString()).toList(),worker.getNavigation().isFollowingPath());
   if(!worker.isAlive()||age>30000)throw new AssertionError("factory flow stalled: "+line.status()+" worker="+worker.getEntityPos());
  }catch(Throwable e){done=true;org.slf4j.LoggerFactory.getLogger("machine-factory-flow-test").error("FAIL",e);server.stop(false);}});
 }
 private MachineFactoryFlowChecks(){}
}
