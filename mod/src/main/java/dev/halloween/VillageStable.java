package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;

/** A separate stone stable, scheduled through the normal builders' queue. */
final class VillageStable {
 static void register(){ServerTickEvents.END_WORLD_TICK.register(VillageStable::tick);}
 static String id(long village){return "stable_v1:"+village;}
 static VillageProjects.Project project(ServerWorld w,long village){return VillageProjects.get(w).jobs.stream().filter(p->p.id.equals(id(village))).findFirst().orElse(null);}
 static BlockPos base(ServerWorld w,long village){var settlement=VillageEraData.get(w).settlements.get(village);if(settlement==null||!settlement.xp.containsKey("stable_x"))return null;return new BlockPos(settlement.xp.get("stable_x"),settlement.xp.get("stable_y"),settlement.xp.get("stable_z"));}
 static BlockPos entrance(ServerWorld w,long village){var p=project(w,village);var b=base(w,village);return p==null||!p.done||b==null?null:b.add(8,1,1);}
 private static void tick(ServerWorld w){
  if(w.getPlayers().isEmpty()||w.getTime()%200!=0)return;var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
  for(var team:List.copyOf(memory.teams)){
   var era=VillageEraData.get(w).settlement(team);if(era.era<2||w.getPlayers().stream().noneMatch(p->p.getBlockPos().isWithinDistance(team.anchor,320)))continue;
   var existing=project(w,team.village);
   if(existing!=null){if(existing.done&&era.xp.getOrDefault("stable_horses_v1",0)==0){
     String imported="halloween_stable_import_"+team.village;var stable=base(w,team.village);if(stable==null)continue;var horses=w.getEntitiesByClass(HorseEntity.class,new Box(team.anchor).expand(320,96,320),h->h.isAlive()&&h.isTame()&&!h.hasPassengers()&&!h.getCommandTags().contains(imported));int slot=0;boolean complete=true;
     for(var horse:horses){boolean moved=false;for(int i=0;i<35;i++){int n=(slot+i)%35;BlockPos q=stable.add(2+(n%7)*2,1,2+(n/7)*2);
      Vec3d destination=Vec3d.ofBottomCenter(q);if(!w.isChunkLoaded(new ChunkPos(q).toLong())||!w.isSpaceEmpty(horse,horse.getBoundingBox().offset(destination.subtract(horse.getEntityPos())))||!w.getOtherEntities(horse,new Box(q).expand(.6,0,.6),e->e.isAlive()).isEmpty())continue;
      horse.requestTeleport(destination.x,destination.y,destination.z);horse.addCommandTag(imported);slot=n+1;moved=true;break;
     }if(!moved){complete=false;break;}}
     if(complete){era.xp.put("stable_horses_v1",1);VillageEraData.get(w).markDirty();}}continue;}
   int serial=era.xp.getOrDefault("stable_search_v1",0);
   for(int attempt=0;attempt<12;attempt++){
    int n=serial+attempt;double angle=n*2.399963229728653;int radius=55+Math.floorMod(n,6)*18;
    int x=team.anchor.getX()+(int)(Math.cos(angle)*radius),z=team.anchor.getZ()+(int)(Math.sin(angle)*radius),width=17,depth=13;
    if(memory.houses.stream().filter(h->!h.recordKey.startsWith("era_road:")&&!h.recordKey.startsWith("road_network_")&&!h.recordKey.startsWith("road_lights_")).anyMatch(h->h.min.getX()-3<=x+width&&h.max.getX()+3>=x&&h.min.getZ()-3<=z+depth&&h.max.getZ()+3>=z))continue;
    if(projects.jobs.stream().filter(p->!p.kind.equals("road")).anyMatch(p->p.min.getX()-3<=x+width&&p.max.getX()+3>=x&&p.min.getZ()-3<=z+depth&&p.max.getZ()+3>=z))continue;
    boolean loaded=true;for(int cx=x>>4;cx<=(x+width)>>4;cx++)for(int cz=z>>4;cz<=(z+depth)>>4;cz++)if(!VillageProjects.keepLoaded(w,new BlockPos(cx*16,64,cz*16)))loaded=false;
    if(!loaded){era.xp.put("stable_search_v1",n);VillageEraData.get(w).markDirty();return;}
    int y=VillageEraPlans.levelSite(w,x,z,width,depth,2);if(y==Integer.MIN_VALUE)continue;BlockPos base=new BlockPos(x,y-1,z);Map<BlockPos,BlockState> plan=new LinkedHashMap<>();boolean safe=true;
    for(int dx=0;dx<width&&safe;dx++)for(int dz=0;dz<depth&&safe;dz++){
     BlockPos floor=base.add(dx,0,dz);for(int h=0;h<=8;h++){var q=floor.up(h);var old=w.getBlockState(q);if(old.hasBlockEntity()||!old.isAir()&&!old.isReplaceable()&&!VillageProjects.terrain(old)){safe=false;break;}}
     if(!safe)break;for(int h=1;h<=7;h++)plan.put(floor.up(h),Blocks.AIR.getDefaultState());
     for(int down=1;down<=8&&!w.getBlockState(floor.down(down)).isSolidBlock(w,floor.down(down));down++)plan.put(floor.down(down),Blocks.STONE_BRICKS.getDefaultState());
     plan.put(floor,Blocks.STONE_BRICKS.getDefaultState());boolean wall=dx==0||dx==width-1||dz==0||dz==depth-1;
     if(wall)for(int h=1;h<=4;h++)if(!(dz==0&&dx>=6&&dx<=10&&h<=3))plan.put(floor.up(h),(h==3&&(dx+dz)%4==0?Blocks.IRON_BARS:Blocks.STONE_BRICKS).getDefaultState());
     int roof=5+Math.min(dz,depth-1-dz)/2;plan.put(floor.up(roof),Blocks.DEEPSLATE_TILE_SLAB.getDefaultState());
    }
    if(!safe)continue;
    for(int dx:new int[]{3,7,11,15})for(int dz=3;dz<10;dz++)if(dz!=6)plan.put(base.add(dx,1,dz),Blocks.COBBLESTONE_WALL.getDefaultState());
    for(int dx:new int[]{2,8,14}){plan.put(base.add(dx,4,5),Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING,false));plan.put(base.add(dx,3,5),Blocks.STONE_BRICKS.getDefaultState());}
    projects.add(w,id(team.village),team.village,"stable",plan);if(projects.exists(id(team.village))){era.xp.put("stable_x",x);era.xp.put("stable_y",y-1);era.xp.put("stable_z",z);}era.xp.put("stable_search_v1",n+1);VillageEraData.get(w).markDirty();return;
   }era.xp.put("stable_search_v1",serial+12);VillageEraData.get(w).markDirty();
  }
 }
 private VillageStable(){}
}
