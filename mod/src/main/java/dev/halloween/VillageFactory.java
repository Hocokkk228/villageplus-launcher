package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;

/** A separate industrial workshop, scheduled through the normal builders' queue. */
final class VillageFactory {
 static void register(){ServerTickEvents.END_WORLD_TICK.register(VillageFactory::tick);}
 static String id(long village){return "factory_v1:"+village;}
 static VillageProjects.Project project(ServerWorld w,long village){return VillageProjects.get(w).jobs.stream().filter(p->p.id.equals(id(village))).findFirst().orElse(null);}
 static BlockPos base(ServerWorld w,long village){var settlement=VillageEraData.get(w).settlements.get(village);if(settlement==null||!settlement.xp.containsKey("factory_x"))return null;return new BlockPos(settlement.xp.get("factory_x"),settlement.xp.get("factory_y"),settlement.xp.get("factory_z"));}
 static BlockPos entrance(ServerWorld w,long village){var p=project(w,village);var b=base(w,village);return p==null||!p.done||b==null?null:b.add(8,1,1);}
 static boolean supplyReady(ServerWorld w,long village){var p=base(w,village);if(p==null)return false;for(int x=p.getX()>>4;x<=(p.getX()+16)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+12)>>4;z++)if(!w.isChunkLoaded(ChunkPos.toLong(x,z)))return false;return true;}
 static void supplyGarden(ServerWorld w,BlockPos base,Map<BlockPos,BlockState> plan){
  for(int x=2;x<=14;x+=2){var soil=base.add(x,0,11);var water=base.add(x,0,10);var cane=soil.up();
   if(w.getBlockState(soil).hasBlockEntity()||w.getBlockState(water).hasBlockEntity())continue;
   var currentCane=plan.getOrDefault(cane,w.getBlockState(cane));var currentSoil=plan.getOrDefault(soil,w.getBlockState(soil));var currentWater=plan.getOrDefault(water,w.getBlockState(water));
   if(!currentCane.isAir()&&!currentCane.isOf(Blocks.SUGAR_CANE))continue;
   if(!currentSoil.isOf(Blocks.STONE_BRICKS)&&!currentSoil.isOf(Blocks.DIRT)&&!currentSoil.isAir())continue;
   if(!currentWater.isOf(Blocks.STONE_BRICKS)&&!currentWater.isOf(Blocks.WATER)&&!currentWater.isAir())continue;
   plan.put(soil,Blocks.DIRT.getDefaultState());plan.put(water,Blocks.WATER.getDefaultState());plan.put(cane,Blocks.SUGAR_CANE.getDefaultState());
  }
 }
 private static void ensureSupplyGarden(ServerWorld w,VillageEraData.Settlement s){String id="factory_cane_v1:"+s.village;var jobs=VillageProjects.get(w);if(jobs.exists(id))return;var plan=new LinkedHashMap<BlockPos,BlockState>();supplyGarden(w,base(w,s.village),plan);if(!plan.isEmpty())jobs.add(w,id,s.village,"factory_supplies",plan);}
 private static void tick(ServerWorld w){
  if(w.getPlayers().isEmpty()||w.getTime()%200!=0)return;var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
  for(var team:List.copyOf(memory.teams)){
   var era=VillageEraData.get(w).settlement(team);if(era.era<2||w.getPlayers().stream().noneMatch(p->p.getBlockPos().isWithinDistance(team.anchor,320)))continue;
   var existing=project(w,team.village);
   if(existing!=null){if(existing.done&&supplyReady(w,team.village))ensureSupplyGarden(w,era);continue;}
   int serial=era.xp.getOrDefault("factory_search_v1",0);
   for(int attempt=0;attempt<12;attempt++){
    int n=serial+attempt;double angle=n*2.399963229728653;int radius=55+Math.floorMod(n,14)*17;
    int x=team.anchor.getX()+(int)(Math.cos(angle)*radius),z=team.anchor.getZ()+(int)(Math.sin(angle)*radius),width=17,depth=13;
    if(memory.houses.stream().filter(VillageBuildingSites::reserves).anyMatch(h->h.min.getX()-3<=x+width&&h.max.getX()+3>=x&&h.min.getZ()-3<=z+depth&&h.max.getZ()+3>=z))continue;
    if(projects.jobs.stream().filter(p->!p.done&&VillageBuildingSites.building(p.kind)).anyMatch(p->p.min.getX()-3<=x+width&&p.max.getX()+3>=x&&p.min.getZ()-3<=z+depth&&p.max.getZ()+3>=z))continue;
    boolean loaded=true;for(int cx=x>>4;cx<=(x+width)>>4;cx++)for(int cz=z>>4;cz<=(z+depth)>>4;cz++)if(!VillageProjects.keepLoaded(w,new BlockPos(cx*16,64,cz*16)))loaded=false;
    if(!loaded){era.xp.put("factory_search_v1",n);VillageEraData.get(w).markDirty();return;}
    int y=drySite(w,x,z,width,depth);if(y==Integer.MIN_VALUE)continue;BlockPos base=new BlockPos(x,y-1,z);Map<BlockPos,BlockState> plan=new LinkedHashMap<>();boolean safe=true;
    for(int dx=0;dx<width&&safe;dx++)for(int dz=0;dz<depth&&safe;dz++){
     BlockPos floor=base.add(dx,0,dz);for(int h=0;h<=10;h++){var q=floor.up(h);var old=w.getBlockState(q);if(old.hasBlockEntity()||!old.isAir()&&!old.isReplaceable()&&!VillageProjects.terrain(old)&&!old.isIn(net.minecraft.registry.tag.BlockTags.LOGS)&&!old.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)){safe=false;break;}}
     if(!safe)break;for(int h=1;h<=10;h++)plan.put(floor.up(h),Blocks.AIR.getDefaultState());
     for(int down=1;down<=8&&!w.getBlockState(floor.down(down)).isSolidBlock(w,floor.down(down));down++)plan.put(floor.down(down),Blocks.STONE_BRICKS.getDefaultState());
     plan.put(floor,Blocks.STONE_BRICKS.getDefaultState());boolean wall=dx==0||dx==width-1||dz==0||dz==depth-1;
     if(wall)for(int h=1;h<=4;h++)if(!(dz==0&&dx>=6&&dx<=10&&h<=3))plan.put(floor.up(h),(h==3&&(dx+dz)%4==0?Blocks.GLASS:Blocks.BRICKS).getDefaultState());
     int roof=5+Math.min(dz,depth-1-dz)/2;plan.put(floor.up(roof),Blocks.DEEPSLATE_TILE_SLAB.getDefaultState());
    }
    if(!safe)continue;
    for(int dx:new int[]{3,7,11}){
     plan.put(base.add(dx,1,9),Blocks.BLAST_FURNACE.getDefaultState().with(AbstractFurnaceBlock.FACING,Direction.NORTH));
     plan.put(base.add(dx,1,7),Blocks.BARREL.getDefaultState());
     plan.put(base.add(dx,2,9),Blocks.BRICKS.getDefaultState());
     for(int h=3;h<=10;h++)plan.put(base.add(dx,h,9),Blocks.BRICKS.getDefaultState());
    }
    plan.put(base.add(13,1,3),Blocks.SMITHING_TABLE.getDefaultState());
    plan.put(base.add(13,1,5),Blocks.ANVIL.getDefaultState());
    plan.put(base.add(2,1,3),Blocks.GRINDSTONE.getDefaultState());
    for(int dx:new int[]{2,8,14}){plan.put(base.add(dx,4,5),Blocks.LANTERN.getDefaultState());plan.put(base.add(dx,3,5),Blocks.STONE_BRICKS.getDefaultState());}
    supplyGarden(w,base,plan);
    projects.add(w,id(team.village),team.village,"factory",plan);if(projects.exists(id(team.village))){era.xp.put("factory_x",x);era.xp.put("factory_y",y-1);era.xp.put("factory_z",z);}era.xp.put("factory_search_v1",n+1);VillageEraData.get(w).markDirty();return;
   }era.xp.put("factory_search_v1",serial+12);VillageEraData.get(w).markDirty();
  }
 }
 static int drySite(ServerWorld w,int x,int z,int width,int depth){int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
  for(int dx=0;dx<width;dx++)for(int dz=0;dz<depth;dz++){int top=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x+dx,z+dz);int limit=Math.max(w.getBottomY()+1,top-32);while(top>limit){var b=w.getBlockState(new BlockPos(x+dx,top-1,z+dz));if(b.isAir()||b.getFluidState().isEmpty()&&b.isReplaceable()||b.isIn(net.minecraft.registry.tag.BlockTags.LOGS)||b.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)){top--;continue;}break;}var surface=new BlockPos(x+dx,top-1,z+dz);if(!w.getFluidState(surface).isEmpty()||!w.getBlockState(surface).isSolidBlock(w,surface))return Integer.MIN_VALUE;low=Math.min(low,top);high=Math.max(high,top);}
  return high-low<=6?high:Integer.MIN_VALUE;
 }
 private VillageFactory(){}
}
