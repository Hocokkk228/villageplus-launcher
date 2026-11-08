package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Thirty private rooms, a three-block corridor and a real staircase. Repairs are budgeted. */
final class VillagePrisons {
    private static final String CELL="halloween_prison_cell_",ANCHOR="halloween_prison_anchor_",VERSION="halloween_prison_30_v3";
    private static final Map<MarkerEntity,Layout> LAYOUTS=new WeakHashMap<>();
    private static final Map<Entity,Layout> RESETS=new WeakHashMap<>();
    private static final class Layout {
        final MarkerEntity jail;final BlockPos a;final Direction f,s;final int rise;final List<Map.Entry<BlockPos,BlockState>> plan;int index;final ArrayDeque<Map.Entry<BlockPos,BlockState>> deferred=new ArrayDeque<>();
        Layout(ServerWorld w,MarkerEntity jail){
            this.jail=jail;a=jail.getBlockPos();BlockPos top=VillageTrials.jailEntrance(w,a);
            Direction up=top==null?Direction.NORTH:Direction.getFacing(top.getX()-a.getX(),0,top.getZ()-a.getZ());if(up.getAxis()==Direction.Axis.Y)up=Direction.NORTH;
            int saved=number(jail,"halloween_prison_facing_",-1);f=saved<0?up.getOpposite():Direction.fromHorizontalQuarterTurns(saved);s=f.rotateYClockwise();jail.addCommandTag("halloween_prison_facing_"+f.getHorizontalQuarterTurns());Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
            for(int y=-1;y<=3;y++)for(int z=-1;z<=77;z++)for(int x=-7;x<=7;x++)blocks.put(pos(x,z,y),state(x,z,y));
            var gates=w.getEntitiesByClass(MarkerEntity.class,new Box(a).expand(160,40,160),m->m.getCommandTags().contains("halloween_gate_left")||m.getCommandTags().contains("halloween_gate_right"));
            int floor=gates.stream().min(Comparator.comparingDouble(m->m.squaredDistanceTo(jail))).map(Entity::getBlockY).orElse(a.getY()+7);
            int oldRise=number(jail,"halloween_prison_rise_",7);
            rise=Math.clamp(floor-a.getY(),2,16);
            for(String tag:List.copyOf(jail.getCommandTags()))if(tag.startsWith("halloween_prison_rise_"))jail.removeCommandTag(tag);
            jail.addCommandTag("halloween_prison_rise_"+rise);
            // The dungeon entrance is a walkable stairwell, never a ladder shaft.
            for(BlockPos pos:BlockPos.iterate(a.add(-5,0,-rise-3),a.add(5,Math.max(rise,oldRise)+2,rise+3)))
                if(w.isChunkLoaded(new ChunkPos(pos).toLong())&&w.getBlockState(pos).isOf(Blocks.LADDER))blocks.put(pos.toImmutable(),Blocks.AIR.getDefaultState());
            // Remove only the obsolete generated stair blocks above the true castle floor.
            for(int n=rise+1;n<=oldRise;n++)for(int lane=-1;lane<=1;lane++){
                var foot=a.offset(f,-n).offset(s,lane).up(n);var pos=foot.down();var old=w.getBlockState(pos);
                if(old.isOf(Blocks.STONE_BRICK_STAIRS)&&old.get(StairsBlock.FACING)==f.getOpposite())blocks.put(pos,Blocks.AIR.getDefaultState());
            }
            for(int n=0;n<=rise;n++)for(int lane=-1;lane<=1;lane++){
                BlockPos foot=a.offset(f,-n).offset(s,lane).up(n);
                blocks.put(foot.down(),n==0?Blocks.STONE_BRICKS.getDefaultState():Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING,f.getOpposite()));
                for(int h=0;h<3;h++)blocks.put(foot.up(h),Blocks.AIR.getDefaultState());
            }
            for(int n=0;n<=rise;n++)for(int lane:new int[]{-2,2}){
                var foot=a.offset(f,-n).offset(s,lane).up(n);
                for(int h=-1;h<=2;h++)blocks.put(foot.up(h),Blocks.STONE_BRICKS.getDefaultState());
                if(n%4==0){blocks.put(foot.up(2),Blocks.CHISELED_STONE_BRICKS.getDefaultState());blocks.put(foot.offset(s,-Integer.signum(lane)).up(2),Blocks.LANTERN.getDefaultState());}
            }
            var landing=a.offset(f,-rise).up(rise);
            for(int lane=-2;lane<=2;lane++)blocks.put(landing.offset(s,lane).up(3),Blocks.CHISELED_STONE_BRICKS.getDefaultState());
            plan=new ArrayList<>(blocks.entrySet());
        }
        BlockPos pos(int x,int z,int y){return a.offset(s,x).offset(f,z).up(y);}
        BlockPos center(int slot){return pos(slot<15?-4:4,3+slot%15*5,0);}
        int slot(BlockPos cell){for(int i=0;i<30;i++)if(center(i).equals(cell))return i;return -1;}
        BlockState state(int x,int z,int y){
            if(y==-1)return ((x+z)%7==0?Blocks.CRACKED_STONE_BRICKS:Blocks.STONE_BRICKS).getDefaultState();
            if(y==3)return Blocks.STONE_BRICKS.getDefaultState();
            boolean wall=Math.abs(x)==7||z==77||z==-1&&Math.abs(x)>1||Math.abs(x)>2&&Math.floorMod(z-1,5)==0;
            if(wall)return (Math.floorMod(x*13+z*7+y,19)==0?Blocks.MOSSY_STONE_BRICKS:Blocks.STONE_BRICKS).getDefaultState();
            if(Math.abs(x)==2)return Blocks.IRON_BARS.getDefaultState().with(PaneBlock.NORTH,f.getAxis()==Direction.Axis.Z).with(PaneBlock.SOUTH,f.getAxis()==Direction.Axis.Z).with(PaneBlock.EAST,f.getAxis()==Direction.Axis.X).with(PaneBlock.WEST,f.getAxis()==Direction.Axis.X);
            if(x==0&&y==2&&Math.floorMod(z-3,5)==0)return Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING,true);
            if(Math.abs(x)==5&&y==0){
                if(Math.floorMod(z-3,5)==0)return Blocks.WHITE_BED.getDefaultState().with(BedBlock.FACING,f).with(BedBlock.PART,BedPart.FOOT);
                if(Math.floorMod(z-4,5)==0)return Blocks.WHITE_BED.getDefaultState().with(BedBlock.FACING,f).with(BedBlock.PART,BedPart.HEAD);
                if(Math.floorMod(z-2,5)==0)return Blocks.CHEST.getDefaultState().with(ChestBlock.FACING,x<0?s:s.getOpposite());
            }
            return Blocks.AIR.getDefaultState();
        }
    }
    private static int number(Entity e,String prefix,int fallback){for(String t:e.getCommandTags())if(t.startsWith(prefix))try{return Integer.parseInt(t.substring(prefix.length()));}catch(NumberFormatException ignored){}return fallback;}
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillagePrisons::tick);}
    static BlockPos assigned(Entity e){return tag(e,CELL);}
    private static BlockPos tag(Entity e,String prefix){for(String t:e.getCommandTags())if(t.startsWith(prefix))try{return BlockPos.fromLong(Long.parseLong(t.substring(prefix.length())));}catch(NumberFormatException ignored){}return null;}
    private static Layout layout(ServerWorld w,Entity e){BlockPos a=tag(e,ANCHOR);if(a==null)return null;for(var l:LAYOUTS.values())if(l.jail.getEntityWorld()==w&&l.a.equals(a))return l;return null;}
    static void prepare(ServerWorld w,MarkerEntity jail){LAYOUTS.computeIfAbsent(jail,k->new Layout(w,k));}
    static boolean ready(ServerWorld w,MarkerEntity jail){prepare(w,jail);return jail.getCommandTags().contains(VERSION);}
    static BlockPos cellFor(ServerWorld w,MarkerEntity jail,Entity prisoner){
        prepare(w,jail);var l=LAYOUTS.get(jail);BlockPos old=assigned(prisoner);if(old!=null&&l.slot(old)>=0&&(jail.getCommandTags().contains(VERSION)||usable(w,l,l.slot(old))))return old;
        if(old!=null)open(w,prisoner);
        int slot=-1;
        List<Integer> slots=new ArrayList<>();for(int i=0;i<30;i++)slots.add(i);slots.sort(Comparator.comparingDouble(i->l.center(i).getSquaredDistance(l.a)));
        for(int i:slots){if(!jail.getCommandTags().contains(VERSION)&&!usable(w,l,i))continue;String prefix="halloween_room_"+i+"_";String own=prefix+prisoner.getUuidAsString();if(jail.getCommandTags().contains(own)){slot=i;break;}if(jail.getCommandTags().stream().noneMatch(t->t.startsWith(prefix))){slot=i;break;}}
        if(slot<0)return null;BlockPos c=l.center(slot);prisoner.addCommandTag(CELL+c.asLong());prisoner.addCommandTag(ANCHOR+l.a.asLong());jail.addCommandTag("halloween_room_"+slot+"_"+prisoner.getUuidAsString());gate(w,l,slot,false);return c;
    }
    // An occupied far room must not prevent escorting a prisoner into a completed near room.
    private static boolean usable(ServerWorld w,Layout l,int slot){int center=3+slot%15*5;for(int x=slot<15?-7:2;x<=(slot<15?-2:7);x++)for(int z=center-2;z<=center+2;z++)for(int y=-1;y<=3;y++){var p=l.pos(x,z,y);if(!w.isChunkLoaded(new ChunkPos(p).toLong()))return false;if(x==(slot<15?-2:2)&&z==center&&(y==0||y==1))continue;var expected=l.state(x,z,y);if(!w.getBlockState(p).isOf(expected.getBlock()))return false;}for(int n=l.rise;n>=0;n--){var p=l.a.offset(l.f,-n).up(n);if(w.getBlockState(p.down()).getCollisionShape(w,p.down()).isEmpty()||!w.getBlockState(p).getCollisionShape(w,p).isEmpty()||!w.getBlockState(p.up()).getCollisionShape(w,p.up()).isEmpty())return false;}for(int z=0;z<=center;z++){var p=l.pos(0,z,0);if(w.getBlockState(p.down()).getCollisionShape(w,p.down()).isEmpty()||!w.getBlockState(p).getCollisionShape(w,p).isEmpty()||!w.getBlockState(p.up()).getCollisionShape(w,p.up()).isEmpty())return false;}return true;}
    static BlockPos roomPost(ServerWorld w,Entity e){var l=layout(w,e);var c=assigned(e);if(l==null||c==null)return c;int slot=l.slot(c);return slot<0?l.a:l.pos(0,3+slot%15*5,0);}
    static BlockPos doorPost(ServerWorld w,Entity e){var l=layout(w,e);var c=assigned(e);if(l==null||c==null)return c;int slot=l.slot(c);return slot<0?l.a:l.pos(slot<15?-1:1,3+slot%15*5,0);}
    static List<BlockPos> route(ServerWorld w,MarkerEntity jail,Entity prisoner){
        prepare(w,jail);var l=LAYOUTS.get(jail);if(!jail.getCommandTags().contains(VERSION)&&(assigned(prisoner)==null||l.slot(assigned(prisoner))<0||!usable(w,l,l.slot(assigned(prisoner)))))return List.of();List<BlockPos> route=new ArrayList<>();
        for(int n=l.rise;n>=0;n--)route.add(l.a.offset(l.f,-n).up(n));
        BlockPos c=assigned(prisoner);if(c!=null){int slot=l.slot(c);if(slot>=0){for(int z=3;z<=3+slot%15*5;z+=5)route.add(l.pos(0,z,0));}}
        return route;
    }
    static List<BlockPos> escortRoute(ServerWorld w,MarkerEntity jail,Entity prisoner){
        // Every stair is a waypoint; the caller replaces the final corridor post with the cell door.
        return route(w,jail,prisoner);
    }
    static boolean seal(ServerWorld w,Entity e){var l=layout(w,e);BlockPos c=assigned(e);if(l==null||c==null)return false;int slot=l.slot(c);if(slot<0)return false;gate(w,l,slot,true);return true;}
    private static void gate(ServerWorld w,Layout l,int slot,boolean close){int x=slot<15?-2:2,z=3+slot%15*5;for(int y=0;y<2;y++){BlockPos p=l.pos(x,z,y);if(w.isChunkLoaded(new ChunkPos(p).toLong()))w.setBlockState(p,(close?Blocks.IRON_BARS:Blocks.AIR).getDefaultState(),Block.NOTIFY_ALL);}}
    static boolean escaped(Entity e){BlockPos c=assigned(e);return c!=null&&(Math.abs(e.getY()-c.getY())>2.5||e.squaredDistanceTo(Vec3d.ofCenter(c))>25);}
    static void removeCivilianBedPois(ServerWorld w){
        for(var layout:LAYOUTS.values())if(layout.jail.getEntityWorld()==w)for(int slot=0;slot<30;slot++){
            var p=layout.pos(slot<15?-5:5,4+slot%15*5,0);
            if(w.isChunkLoaded(new ChunkPos(p).toLong())&&w.getPointOfInterestStorage().getType(p).isPresent())w.getPointOfInterestStorage().remove(p);
        }
    }
    static void provision(ServerWorld w,Entity e){var l=layout(w,e);var c=assigned(e);if(l==null||c==null)return;int slot=l.slot(c);if(slot<0)return;BlockPos p=l.pos(slot<15?-5:5,2+slot%15*5,0);if(w.getBlockEntity(p) instanceof ChestBlockEntity chest&&chest.isEmpty())chest.setStack(0,new ItemStack(Items.STONE_SHOVEL));}
    static void open(ServerWorld w,Entity e){
        var l=layout(w,e);BlockPos c=assigned(e);
        if(l!=null&&c!=null){int slot=l.slot(c);if(slot>=0){gate(w,l,slot,false);if(!e.isSpectator()&&e.squaredDistanceTo(Vec3d.ofCenter(c))<25){BlockPos exit=roomPost(w,e);e.requestTeleport(exit.getX()+.5,exit.getY(),exit.getZ()+.5);}RESETS.put(e,l);l.jail.removeCommandTag("halloween_room_"+slot+"_"+e.getUuidAsString());e.addCommandTag("halloween_reset_room_"+slot);}}
        for(String t:List.copyOf(e.getCommandTags()))if(t.startsWith(CELL)||t.startsWith(ANCHOR)||t.equals("halloween_prison_gate_pending"))e.removeCommandTag(t);
    }
    static List<BlockBox> protectedBoxes(ServerWorld w){List<BlockBox> boxes=new ArrayList<>();for(var l:LAYOUTS.values())if(l.jail.getEntityWorld()==w){
        BlockPos p=l.pos(-8,-1,-1),q=l.pos(8,79,3);boxes.add(new BlockBox(Math.min(p.getX(),q.getX()),p.getY(),Math.min(p.getZ(),q.getZ()),Math.max(p.getX(),q.getX()),q.getY(),Math.max(p.getZ(),q.getZ())));
        for(int n=0;n<=l.rise;n++){var foot=l.a.offset(l.f,-n).up(n);boxes.add(new BlockBox(foot.getX()-2,foot.getY()-1,foot.getZ()-2,foot.getX()+2,foot.getY()+2,foot.getZ()+2));}
    }return boxes;}
    static void reopenBlockedStairs(ServerWorld w,MarkerEntity jail){
        prepare(w,jail);var l=LAYOUTS.get(jail);if(l==null||!jail.getCommandTags().contains(VERSION))return;
        for(int n=0;n<=l.rise;n++)for(int lane=-1;lane<=1;lane++)for(int h=0;h<3;h++){
            var p=l.a.offset(l.f,-n).offset(l.s,lane).up(n+h);if(!w.isChunkLoaded(new ChunkPos(p).toLong()))continue;
            var old=w.getBlockState(p);if(!old.hasBlockEntity()&&(old.isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_OVERWORLD)||old.isIn(net.minecraft.registry.tag.BlockTags.DIRT)))w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        }
    }
    static boolean managed(ServerWorld w,BlockPos p){for(var l:LAYOUTS.values())if(l.jail.getEntityWorld()==w){for(int n=0;n<=l.rise;n++){var foot=l.a.offset(l.f,-n).up(n);if(Math.abs(p.getX()-foot.getX())<=2&&Math.abs(p.getZ()-foot.getZ())<=2&&p.getY()>=foot.getY()-1&&p.getY()<=foot.getY()+2)return true;}var d=p.subtract(l.a);int x=d.getX()*l.s.getOffsetX()+d.getZ()*l.s.getOffsetZ(),z=d.getX()*l.f.getOffsetX()+d.getZ()*l.f.getOffsetZ();if(Math.abs(x)<=7&&z>=-1&&z<=77&&d.getY()>=-1&&d.getY()<=3)return true;}return false;}
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty())return;
        if(w.getTime()%100==0)for(var player:w.getPlayers())for(var jail:w.getEntitiesByClass(MarkerEntity.class,new Box(player.getBlockPos()).expand(192),m->m.getCommandTags().contains("halloween_jail"))){prepare(w,jail);reopenBlockedStairs(w,jail);}
        int budget=192;long until=System.nanoTime()+2_000_000;
        for(var l:new ArrayList<>(LAYOUTS.values())){
            if(l.jail.getEntityWorld()!=w||!l.jail.isAlive()||l.jail.getCommandTags().contains(VERSION))continue;
            if(l.index==0){for(var e:w.iterateEntities())if((e.getCommandTags().contains("halloween_prisoner")||e.getCommandTags().contains("halloween_player_jailed"))&&e.squaredDistanceTo(Vec3d.ofCenter(l.a))<144){w.setBlockState(l.a.down(),Blocks.STONE_BRICKS.getDefaultState(),Block.NOTIFY_LISTENERS);w.setBlockState(l.a,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);w.setBlockState(l.a.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);e.requestTeleport(l.a.getX()+.5,l.a.getY(),l.a.getZ()+.5);}}
            while(l.index<l.plan.size()&&budget>0&&System.nanoTime()<until){var entry=l.plan.get(l.index);var p=entry.getKey();if(!VillageProjects.keepLoaded(w,p))break;
                BlockState old=w.getBlockState(p);if(!old.isOf(entry.getValue().getBlock())){
                    if(old.hasBlockEntity()&&w.getBlockEntity(p) instanceof net.minecraft.inventory.Inventory inv)net.minecraft.util.ItemScatterer.spawn(w,p,inv);
                    if(!entry.getValue().isAir()&&!w.getOtherEntities(null,new Box(p),e->e instanceof LivingEntity&&e.isAlive()&&!e.isSpectator()).isEmpty()){l.deferred.addLast(entry);l.index++;continue;}
                    w.setBlockState(p,entry.getValue(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);budget--;
                }l.index++;
            }
            int retries=Math.min(96,l.deferred.size());while(retries-->0&&budget>0&&System.nanoTime()<until){var entry=l.deferred.removeFirst();var p=entry.getKey();if(!w.isChunkLoaded(new ChunkPos(p).toLong())||!entry.getValue().isAir()&&!w.getOtherEntities(null,new Box(p),e->e instanceof LivingEntity&&e.isAlive()&&!e.isSpectator()).isEmpty()){l.deferred.addLast(entry);continue;}if(!w.getBlockState(p).equals(entry.getValue())){w.setBlockState(p,entry.getValue(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);budget--;}}
            if(l.index>=l.plan.size()&&l.deferred.isEmpty()){l.jail.addCommandTag(VERSION);Map<BlockPos,BlockState> records=new LinkedHashMap<>();for(var entry:l.plan)records.put(entry.getKey(),entry.getValue());VillageProjects.recordInfrastructure(w,"prison_30:"+l.jail.getUuidAsString(),records);}
        }
        if(w.getTime()%20!=0)return;
        for(var e:w.iterateEntities())if(e.getCommandTags().contains("halloween_prisoner")||e.getCommandTags().contains("halloween_player_jailed")){
            var l=layout(w,e);if(l==null||!l.jail.getCommandTags().contains(VERSION)){var jail=w.getEntitiesByClass(MarkerEntity.class,new Box(e.getBlockPos()).expand(160),m->m.getCommandTags().contains("halloween_jail")).stream().findFirst().orElse(null);if(jail!=null)prepare(w,jail);continue;}
            var c=assigned(e);if(c==null||l.slot(c)<0){for(String t:List.copyOf(e.getCommandTags()))if(t.startsWith(CELL))e.removeCommandTag(t);c=cellFor(w,l.jail,e);if(c!=null){e.requestTeleport(c.getX()+.5,c.getY(),c.getZ()+.5);seal(w,e);provision(w,e);}}
            if(e instanceof VillagerEntity v){v.setAiDisabled(false);VillageTasks.hold(v);}
        }
        for(var entry:new ArrayList<>(RESETS.entrySet())){var e=entry.getKey();var l=entry.getValue();for(String t:List.copyOf(e.getCommandTags()))if(t.startsWith("halloween_reset_room_")){
            int slot=Integer.parseInt(t.substring("halloween_reset_room_".length()));int sign=slot<15?-1:1,zc=3+slot%15*5;boolean blocked=false;
            for(int x=2;x<=7;x++)for(int z=zc-2;z<=zc+3;z++)for(int y=-1;y<=3;y++){BlockPos p=l.pos(x*sign,z,y);if(!w.isChunkLoaded(new ChunkPos(p).toLong())){blocked=true;continue;}var desired=l.state(x*sign,z,y);if(!desired.isAir()&&!w.getOtherEntities(null,new Box(p),v->v instanceof LivingEntity&&v.isAlive()&&!v.isSpectator()).isEmpty()){blocked=true;continue;}if(w.getBlockState(p).hasBlockEntity()&&w.getBlockEntity(p) instanceof net.minecraft.inventory.Inventory inv){net.minecraft.util.ItemScatterer.spawn(w,p,inv);inv.clear();}w.setBlockState(p,desired,Block.NOTIFY_ALL);}
            if(!blocked){e.removeCommandTag(t);RESETS.remove(e);}
        }}
    }
    private VillagePrisons(){}
}
