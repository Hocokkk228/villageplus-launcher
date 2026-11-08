package dev.halloween;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
/** Same-site market upgrades preserve their real barrel inventories. */
final class VillageMarkets {
 static String id(long village,int era){return "market_v1:"+village+":"+era;}
 static final String X="market_x",Y="market_y",Z="market_z",STAGE="market_stage";
 static BlockPos origin(VillageEraData.Settlement s){return new BlockPos(s.xp.get(X),s.xp.get(Y),s.xp.get(Z));}
 static void register(){ServerTickEvents.END_WORLD_TICK.register(w->{if(w.getPlayers().isEmpty()||w.getTime()%100!=0)return;var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
  for(var team:memory.teams){if(!WorldSettlements.loadedNearby(w,team.anchor))continue;var s=VillageEraData.get(w).settlement(team);advance(w,team,s);int stage=s.xp.getOrDefault(STAGE,0);if(!VillageBuilders.workHours(w))continue;
   var shop=VillageIndustrial.shop(w,s);
   if(shop!=null)tradeDay(w,team,shop);
   if(stage>0&&VillageProjects.get(w).complete(team.village,"market"))tradeDay(w,team,origin(s));}
 });}
 static void advance(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s){
  var jobs=VillageProjects.get(w);int stage=s.xp.getOrDefault(STAGE,0),target=Math.min(3,s.era);if(stage>=target)return;
  if(stage>0&&!jobs.jobs.stream().anyMatch(p->p.id.equals(id(team.village,stage))&&p.done))return;
  if(!s.xp.containsKey(X)){int cursor=s.xp.getOrDefault("market_search",0);
   for(int n=cursor;n<cursor+4;n++){double angle=n*2.399963229728653;int radius=40+n%10*10,x=team.anchor.getX()+(int)(Math.cos(angle)*radius),z=team.anchor.getZ()+(int)(Math.sin(angle)*radius);
    if(memoryOverlap(w,x,z)||jobs.jobs.stream().filter(p->!p.kind.equals("road")&&!p.kind.equals("stone_road")).anyMatch(p->p.min.getX()<=x+14&&p.max.getX()>=x&&p.min.getZ()<=z+14&&p.max.getZ()>=z))continue;
    boolean loaded=true;for(int cx=x>>4;cx<=(x+14)>>4;cx++)for(int cz=z>>4;cz<=(z+14)>>4;cz++)if(!w.isChunkLoaded(ChunkPos.toLong(cx,cz)))loaded=false;if(!loaded)continue;
    int top=VillageEraPlans.levelSite(w,x,z,15,15,2);if(top==Integer.MIN_VALUE)continue;var base=new BlockPos(x,top-1,z);if(!safe(w,base))continue;
    s.xp.put(X,x);s.xp.put(Y,base.getY());s.xp.put(Z,z);break;
   }s.xp.put("market_search",cursor+4);VillageEraData.get(w).markDirty();if(!s.xp.containsKey(X))return;
  }
  var base=origin(s);for(int cx=base.getX()>>4;cx<=(base.getX()+14)>>4;cx++)for(int cz=base.getZ()>>4;cz<=(base.getZ()+14)>>4;cz++)if(!w.isChunkLoaded(ChunkPos.toLong(cx,cz)))return;
  var blocks=plan(base,target);for(var e:blocks.entrySet())if(w.getBlockState(e.getKey()).hasBlockEntity()&&!w.getBlockState(e.getKey()).isOf(e.getValue().getBlock()))return;
  jobs.add(w,id(team.village,target),team.village,"market",blocks);if(jobs.exists(id(team.village,target))){s.xp.put(STAGE,target);VillageEraData.get(w).markDirty();}
 }
 static boolean memoryOverlap(ServerWorld w,int x,int z){return w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses.stream().filter(h->!h.kind.equals("infrastructure")).anyMatch(h->h.min.getX()-2<=x+14&&h.max.getX()+2>=x&&h.min.getZ()-2<=z+14&&h.max.getZ()+2>=z);}
 static boolean safe(ServerWorld w,BlockPos base){for(var p:BlockPos.iterate(base,base.add(14,8,14))){var b=w.getBlockState(p);if(!w.getWorldBorder().contains(p)||!w.getFluidState(p).isEmpty()||b.hasBlockEntity()||!b.isAir()&&!b.isReplaceable()&&!VillageProjects.terrain(b)&&!b.isIn(net.minecraft.registry.tag.BlockTags.LOGS)&&!b.isIn(net.minecraft.registry.tag.BlockTags.LEAVES))return false;}return true;}
 static Map<BlockPos,BlockState> plan(BlockPos base,int era){Map<BlockPos,BlockState> out=new LinkedHashMap<>();
  for(int x=0;x<15;x++)for(int z=0;z<15;z++){out.put(base.add(x,0,z),(era==1?Blocks.SPRUCE_PLANKS:Blocks.STONE_BRICKS).getDefaultState());for(int y=1;y<=8;y++)out.put(base.add(x,y,z),Blocks.AIR.getDefaultState());}
  if(era<3){for(int sx:new int[]{2,9})for(int sz:new int[]{2,9}){
   for(int x=0;x<4;x++)for(int z=0;z<4;z++)out.put(base.add(sx+x,4,sz+z),((x%2==0)?(sz==2?Blocks.RED_WOOL:Blocks.GREEN_WOOL):Blocks.WHITE_WOOL).getDefaultState());
   for(int dx:new int[]{0,3})for(int dz:new int[]{0,3})for(int y=1;y<=3;y++)out.put(base.add(sx+dx,y,sz+dz),(era==1?Blocks.SPRUCE_FENCE:Blocks.STONE_BRICK_WALL).getDefaultState());
   for(int x=0;x<4;x++)out.put(base.add(sx+x,1,sz),(era==1?Blocks.SPRUCE_SLAB:Blocks.SMOOTH_STONE_SLAB).getDefaultState().with(SlabBlock.TYPE,SlabType.TOP));
   out.put(base.add(sx+1,1,sz+2),Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING,Direction.UP));
  }for(int x:new int[]{0,14})for(int z:new int[]{0,14}){for(int y=1;y<=3;y++)out.put(base.add(x,y,z),(era==1?Blocks.SPRUCE_FENCE:Blocks.STONE_BRICK_WALL).getDefaultState());out.put(base.add(x,4,z),Blocks.LANTERN.getDefaultState());}}
  else{
   for(int y=1;y<=5;y++)for(int x=1;x<=13;x++)for(int z=1;z<=13;z++)if(x==1||x==13||z==1||z==13)out.put(base.add(x,y,z),((y==2||y==3)&&(x%4!=1&&z%4!=1)?Blocks.GLASS:Blocks.BRICKS).getDefaultState());
   for(int x=0;x<15;x++)for(int z=0;z<15;z++)out.put(base.add(x,6,z),Blocks.DARK_OAK_SLAB.getDefaultState());
   for(int x:new int[]{6,7})for(int y=1;y<=2;y++)out.put(base.add(x,y,1),Blocks.DARK_OAK_DOOR.getDefaultState().with(DoorBlock.FACING,Direction.SOUTH).with(DoorBlock.HALF,y==1?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER).with(DoorBlock.HINGE,x==6?DoorHinge.LEFT:DoorHinge.RIGHT));
   for(int x=3;x<=11;x++)out.put(base.add(x,1,7),Blocks.POLISHED_ANDESITE.getDefaultState());
   for(int sx:new int[]{2,9})for(int sz:new int[]{2,9})out.put(base.add(sx+1,1,sz+2),Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING,Direction.UP));
   for(int x:new int[]{4,10})for(int z:new int[]{4,10})out.put(base.add(x,5,z),Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING,true));
   for(int x:new int[]{2,12})for(int z=3;z<=11;z++)out.put(base.add(x,1,z),Blocks.BOOKSHELF.getDefaultState());
  }return out;
 }
 static void tradeDay(ServerWorld w,VillageMemory.Team team,BlockPos base){var traders=w.getEntitiesByClass(VillagerEntity.class,new Box(team.anchor).expand(192,64,192),v->v.isAlive()&&v.getCustomer()==null&&!v.isBaby()&&!v.isSleeping()&&!VillageTasks.ownsMovement(v)&&!VillageTrials.busy(v)&&VillageEras.belongs(w,v,team.village)&&Set.of("farmer","fisherman","butcher","shepherd","leatherworker","librarian","cartographer").stream().anyMatch(role->v.getVillagerData().profession().matchesKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.VILLAGER_PROFESSION,net.minecraft.util.Identifier.ofVanilla(role)))));
  traders.sort(Comparator.comparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(base))));for(int i=0;i<Math.min(4,traders.size());i++){var v=traders.get(i);int sx=i%2==0?2:9,sz=i<2?2:9;var post=base.add(sx+1,1,sz+1);VillageTasks.hold(v);if(v.squaredDistanceTo(Vec3d.ofBottomCenter(post))>4){VillageProjects.approach(w,v,post);continue;}
   var p=base.add(sx+1,1,sz+2);if(!(w.getBlockEntity(p) instanceof Inventory stock))continue;for(int slot=0;slot<v.getInventory().size();slot++){var stack=v.getInventory().getStack(slot);if(stack.isEmpty())continue;for(int dst=0;dst<stock.size();dst++)if(stock.getStack(dst).isEmpty()){stock.setStack(dst,stack.copy());stack.setCount(0);stock.markDirty();v.getInventory().markDirty();break;}break;}
  }
 }
}
