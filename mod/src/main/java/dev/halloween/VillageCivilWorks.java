package dev.halloween;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Saved public works. Explicit changes supersede older repair snapshots, including terrain removals. */
final class VillageCivilWorks {
    record Change(BlockPos pos,BlockState before,BlockState after){
        static final Codec<Change> CODEC=RecordCodecBuilder.create(i->i.group(BlockPos.CODEC.fieldOf("pos").forGetter(Change::pos),
            BlockState.CODEC.fieldOf("before").forGetter(Change::before),BlockState.CODEC.fieldOf("after").forGetter(Change::after)).apply(i,Change::new));
    }
    record Job(String id,BlockPos min,BlockPos max,List<Change> changes){
        static final Codec<Job> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("id").forGetter(Job::id),
            BlockPos.CODEC.fieldOf("min").forGetter(Job::min),BlockPos.CODEC.fieldOf("max").forGetter(Job::max),
            Change.CODEC.listOf().fieldOf("changes").forGetter(Job::changes)).apply(i,Job::new));
    }
    private static final class State extends PersistentState {
        static final Codec<State> CODEC=Job.CODEC.listOf().xmap(State::new,s->s.jobs);
        static final PersistentStateType<State> TYPE=new PersistentStateType<>("halloween_public_works",()->new State(List.of()),CODEC,null);
        final List<Job> jobs;final Map<BlockPos,Change> overrides=new HashMap<>();final Set<String> ids=new HashSet<>();
        State(List<Job> source){jobs=new ArrayList<>(source);for(Job j:jobs){ids.add(j.id());for(Change c:j.changes())overrides.put(c.pos(),c);}}
    }
    private static final Map<ServerWorld,State> CACHE=new WeakHashMap<>();
    private static State state(ServerWorld w){return CACHE.computeIfAbsent(w,k->w.getPersistentStateManager().getOrCreate(State.TYPE));}
    static boolean superseded(ServerWorld w,VillageMemory.BlockRecord r){Change c=state(w).overrides.get(r.pos());return c!=null&&!c.after().equals(r.state());}
    static boolean hasOverride(ServerWorld w,BlockPos p){return state(w).overrides.containsKey(p);}
    static boolean mayClear(ServerWorld w,BlockPos p,BlockState old){Change c=state(w).overrides.get(p);return c!=null&&c.after().isAir()&&c.before().equals(old)&&!old.isAir()&&!old.hasBlockEntity();}
    static boolean mayReplace(ServerWorld w,BlockPos p,BlockState old,BlockState wanted){
        Change c=state(w).overrides.get(p);return c!=null&&c.after().equals(wanted)&&c.before().equals(old)&&!old.isAir()&&!old.hasBlockEntity();
    }
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillageCivilWorks::tick);}
    static BlockPos royalStairFoot(ServerWorld w,BlockPos bed){
        var state=w.getBlockState(bed);Direction forward=state.getBlock() instanceof BedBlock?state.get(BedBlock.FACING):Direction.SOUTH;
        return bed.offset(forward.rotateYClockwise(),5).offset(forward,-8).down(7);
    }
    static void royalStairs(ServerWorld w,MarkerEntity bed){
        State state=state(w);String id="royal_stairs_v1:"+bed.getUuidAsString();if(state.ids.contains(id))return;
        BlockPos head=bed.getBlockPos();var bedState=w.getBlockState(head);if(!(bedState.getBlock() instanceof BedBlock))return;
        Direction forward=bedState.get(BedBlock.FACING),across=forward.rotateYCounterclockwise();BlockPos foot=royalStairFoot(w,head);
        Map<BlockPos,BlockState> target=new LinkedHashMap<>();
        for(int step=0;step<7;step++)for(int width=0;width<2;width++){
            BlockPos p=foot.offset(forward,step).offset(across,width).up(step);
            for(int y=foot.getY();y<p.getY();y++)target.put(new BlockPos(p.getX(),y,p.getZ()),Blocks.SPRUCE_PLANKS.getDefaultState());
            target.put(p,Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING,forward));
            target.put(p.up(),Blocks.AIR.getDefaultState());target.put(p.up(2),Blocks.AIR.getDefaultState());target.put(p.up(3),Blocks.AIR.getDefaultState());
        }
        BlockPos oldLadder=head.offset(forward.rotateYClockwise(),5).offset(forward,-5);
        for(int y=head.getY()-6;y<=head.getY();y++){
            BlockPos p=new BlockPos(oldLadder.getX(),y,oldLadder.getZ());if(w.getBlockState(p).isOf(Blocks.LADDER)&&!target.containsKey(p))target.put(p,Blocks.AIR.getDefaultState());
        }
        Map<BlockPos,Change> changes=new LinkedHashMap<>();
        for(var e:target.entrySet()){
            BlockPos p=e.getKey();if(!w.isChunkLoaded(new ChunkPos(p).toLong()))return;
            var old=w.getBlockState(p);if(old.equals(e.getValue()))continue;
            if(!old.isAir()&&!old.isOf(Blocks.LADDER)&&!old.isOf(Blocks.SPRUCE_PLANKS)&&!old.isOf(Blocks.DARK_OAK_PLANKS)&&!old.isOf(Blocks.SPRUCE_STAIRS))return;
            changes.put(p,new Change(p,old,e.getValue()));
        }
        add(w,w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE),state,id,changes,foot,head);
    }
    static boolean natural(BlockState s){return s.isIn(BlockTags.DIRT)||s.isIn(BlockTags.BASE_STONE_OVERWORLD)
        ||s.isOf(Blocks.SAND)||s.isOf(Blocks.RED_SAND)||s.isOf(Blocks.GRAVEL)||s.isOf(Blocks.SNOW_BLOCK)||s.isOf(Blocks.SNOW)||VillageExpansion.softPlant(s);}
    private static boolean editable(BlockState s){return s.isAir()||natural(s);}
    private static void tick(ServerWorld w){
        if(!w.getRegistryKey().equals(World.OVERWORLD)||w.getPlayers().isEmpty()||w.getTime()%200!=0)return;
        State s=state(w);VillageMemory memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(Job job:s.jobs)if(!job.changes().isEmpty()&&memory.houses.stream().noneMatch(h->h.recordKey.equals(job.id())))attach(memory,job,w.getTime());
        Set<UUID> seen=new HashSet<>();
        for(var player:w.getPlayers())for(var square:w.getEntitiesByClass(MarkerEntity.class,new Box(player.getBlockPos()).expand(128),m->m.getCommandTags().contains("halloween_square"))){
            if(!seen.add(square.getUuid()))continue;
            String id="plaza_access_v3:"+square.getUuidAsString();if(!s.ids.contains(id))planSquare(w,memory,s,square,id);
        }
        for(var team:memory.teams){
            if(w.getPlayers().stream().noneMatch(p->p.squaredDistanceTo(Vec3d.ofCenter(team.anchor))<160*160))continue;
            // Dungeon stairs are owned exclusively by VillagePrisons.
            planWall(w,memory,s,team);
        }
        for(var house:List.copyOf(memory.houses))if(house.kind.equals("house")&&w.getPlayers().stream().anyMatch(p->p.squaredDistanceTo(Vec3d.ofCenter(house.min))<96*96))
            removeOldDirtPillars(w,memory,s,house);
    }
    private static void removeOldDirtPillars(ServerWorld w,VillageMemory memory,State s,VillageMemory.House house){
        String id="old_builder_pillars_v1:"+house.min.asLong();if(s.ids.contains(id))return;
        Map<BlockPos,BlockState> expected=new HashMap<>();for(var r:house.blueprint)expected.put(r.pos(),r.state());
        Map<BlockPos,Change> changes=new LinkedHashMap<>();
        for(int x=house.min.getX()+1;x<house.max.getX();x++)for(int z=house.min.getZ()+1;z<house.max.getZ();z++){
            List<BlockPos> column=new ArrayList<>();
            for(int y=house.min.getY()+1;y<=house.max.getY();y++){
                BlockPos p=new BlockPos(x,y,z);if(!w.isChunkLoaded(new ChunkPos(p).toLong()))return;
                var old=w.getBlockState(p);var design=expected.get(p);
                if(old.isOf(Blocks.DIRT)&&(design==null||!natural(design)))column.add(p);else if(!column.isEmpty())break;
            }
            if(column.size()<3)continue;
            for(BlockPos p:column)changes.put(p,new Change(p,w.getBlockState(p),expected.getOrDefault(p,Blocks.AIR.getDefaultState())));
        }
        if(!changes.isEmpty())add(w,memory,s,id,changes,house.min,house.max);
    }
    private static void attach(VillageMemory memory,Job job,long time){
        var blocks=job.changes().stream().map(c->new VillageMemory.BlockRecord(c.pos(),c.after())).toList();
        memory.houses.add(new VillageMemory.House(job.min(),job.max(),blocks,time,List.of(),0,true,List.of(),List.of(),List.of(),"infrastructure",job.id(),List.of()));memory.markDirty();
    }
    private static void add(ServerWorld w,VillageMemory memory,State state,String id,Map<BlockPos,Change> changes,BlockPos low,BlockPos high){
        if(state.ids.contains(id))return;
        BlockPos min=low,max=high;
        for(BlockPos p:changes.keySet()){
            min=new BlockPos(Math.min(min.getX(),p.getX()),Math.min(min.getY(),p.getY()),Math.min(min.getZ(),p.getZ()));
            max=new BlockPos(Math.max(max.getX(),p.getX()),Math.max(max.getY(),p.getY()),Math.max(max.getZ(),p.getZ()));
        }
        Job job=new Job(id,min,max,List.copyOf(changes.values()));state.jobs.add(job);state.ids.add(id);state.overrides.putAll(changes);state.markDirty();
        if(!changes.isEmpty())attach(memory,job,w.getTime());
        if(id.startsWith("dungeon_")&&!changes.isEmpty()){
            var team=VillageEras.team(w,low);if(team!=null){Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();for(var c:changes.values())blocks.put(c.pos(),c.after());VillageProjects.get(w).add(w,id,team.village,"dungeon",blocks);}
        }
    }
    private static void change(ServerWorld w,Map<BlockPos,Change> out,BlockPos p,BlockState to){
        if(!w.isChunkLoaded(new ChunkPos(p).toLong())||!w.getWorldBorder().contains(p))return;
        BlockState old=w.getBlockState(p);if(!old.equals(to)&&editable(old))out.put(p.toImmutable(),new Change(p.toImmutable(),old,to));
    }
    private static boolean houseAbove(VillageMemory memory,BlockPos p,int floor){
        return memory.houses.stream().anyMatch(h->h.kind.equals("house")&&h.min.getY()>floor+1&&p.getX()>=h.min.getX()-1&&p.getX()<=h.max.getX()+1&&p.getZ()>=h.min.getZ()-1&&p.getZ()<=h.max.getZ()+1);
    }
    private static void planSquare(ServerWorld w,VillageMemory memory,State s,MarkerEntity square,String id){
        BlockPos centre=square.getBlockPos();int floor=centre.getY()-1;
        var podium=w.getEntitiesByClass(MarkerEntity.class,new Box(centre).expand(36),m->m.getCommandTags().contains("halloween_podium")).stream().findFirst().orElse(null);
        Direction forward=podium==null?Direction.SOUTH:Direction.getFacing(podium.getX()-square.getX(),0,podium.getZ()-square.getZ());
        if(forward.getAxis()==Direction.Axis.Y)forward=Direction.SOUTH;Direction side=forward.rotateYClockwise();
        Map<BlockPos,Change> changes=new LinkedHashMap<>();boolean obstructed=false,allLoaded=true;
        for(int a=-31;a<=31;a++)for(int b=-23;b<=19;b++){
            BlockPos column=centre.offset(side,a).offset(forward,b);if(!w.isChunkLoaded(new ChunkPos(column).toLong())){allLoaded=false;continue;}
            if(houseAbove(memory,column,floor))continue;
            for(int y=floor+1;y<=floor+32;y++){
                BlockPos p=new BlockPos(column.getX(),y,column.getZ());BlockState old=w.getBlockState(p);
                if(natural(old)){change(w,changes,p,Blocks.AIR.getDefaultState());obstructed=true;}
            }
        }
        if(!allLoaded)return; // Do not save a partial clearing while its far edge is unloaded.
        boolean stairs=false;
        for(Direction outward:new Direction[]{forward.getOpposite(),side,side.getOpposite()}){
            int edge=outward==forward.getOpposite()?22:30;
            for(int lateral:new int[]{0,10,-10,18,-18}){
                BlockPos start=new BlockPos(centre.getX(),floor,centre.getZ()).offset(outward,edge).offset(outward.rotateYClockwise(),lateral);
                for(int run=4;run<=28;run++){
                    BlockPos end=start.offset(outward,run);if(!w.isChunkLoaded(new ChunkPos(end).toLong()))continue;
                    int top=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,end.getX(),end.getZ());
                    int rise=top-(floor+1);
                    if(rise<3||rise>16||rise>run)continue;
                    Map<BlockPos,Change> stair=stairs(w,memory,start,outward,run,rise);if(stair==null)continue;
                    changes.putAll(stair);stairs=true;break;
                }
                if(stairs)break;
            }
            if(stairs)break;
        }
        if(!stairs&&!obstructed)return;
        add(w,memory,s,id,changes,centre,centre);
    }
    private static Map<BlockPos,Change> stairs(ServerWorld w,VillageMemory memory,BlockPos start,Direction up,int run,int rise){
        Map<BlockPos,Change> out=new LinkedHashMap<>();Direction across=up.rotateYClockwise();
        for(int i=0;i<=run;i++)for(int width=-2;width<=2;width++){
            int level=i*rise/run;
            BlockPos step=start.offset(up,i).offset(across,width).up(level);
            if(!w.isChunkLoaded(new ChunkPos(step).toLong())||houseAbove(memory,step,start.getY()))return null;
            for(int h=0;h<=3;h++)if(!editable(w.getBlockState(step.up(h))))return null;
            if(Math.abs(width)<=1){
                for(int h=1;h<=3;h++)change(w,out,step.up(h),Blocks.AIR.getDefaultState());
                boolean rising=i<run&&(i+1)*rise/run>level;
                change(w,out,step,rising?Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING,up):Blocks.POLISHED_ANDESITE.getDefaultState());
            }else{
                change(w,out,step,Blocks.POLISHED_TUFF.getDefaultState());change(w,out,step.up(),Blocks.STONE_BRICK_WALL.getDefaultState());
            }
            for(int depth=1;depth<=20;depth++){
                BlockPos p=step.down(depth);var old=w.getBlockState(p);if(old.isSolidBlock(w,p))break;
                if(!old.isAir()&&!VillageExpansion.softPlant(old))return null;
                change(w,out,p,(i%4==0?Blocks.TUFF_BRICKS:Blocks.STONE_BRICKS).getDefaultState());
            }
        }
        return out;
    }
    private static boolean dungeonChange(ServerWorld w,VillageMemory memory,Map<BlockPos,Change> out,BlockPos p,BlockState wanted){
        if(!w.isChunkLoaded(new ChunkPos(p).toLong())||!w.getWorldBorder().contains(p))return false;
        Change proposed=out.get(p);
        if(proposed!=null&&!proposed.after().equals(wanted))return false;
        BlockState old=w.getBlockState(p);if(old.equals(wanted))return true;
        if(old.hasBlockEntity())return false;
        boolean recorded=memory.houses.stream().filter(h->h.kind.equals("structure")).anyMatch(h->h.blueprint.stream().anyMatch(r->r.pos().equals(p)&&r.state().equals(old)));
        if(!old.isAir()&&!old.isOf(Blocks.LADDER)&&!old.isOf(Blocks.COBBLESTONE_STAIRS)&&!old.isOf(Blocks.COBBLESTONE)&&!old.isOf(Blocks.STONE_BRICKS)&&!old.isOf(Blocks.CRACKED_STONE_BRICKS)&&!old.isOf(Blocks.MOSSY_STONE_BRICKS)&&!natural(old)&&!recorded)return false;
        out.put(p.toImmutable(),new Change(p.toImmutable(),old,wanted));return true;
    }
    private static void planDungeonStairs(ServerWorld w,VillageMemory memory,State state,MarkerEntity jail){
        String id="dungeon_stairs_v4:"+jail.getUuidAsString();if(state.ids.contains(id))return;
        if(!generatedDungeonRoute(w,jail).isEmpty()||state.ids.contains("dungeon_clearance_v5:"+jail.getUuidAsString()))return;
        BlockPos cell=jail.getBlockPos(),top=VillageTrials.jailEntrance(w,cell);if(top==null)return;
        int rise=top.getY()-cell.getY();if(rise<2||rise>12)return;
        for(Direction out:Direction.Type.HORIZONTAL){
            int dx=top.getX()-cell.getX(),dz=top.getZ()-cell.getZ();
            if((dx!=0||dz!=0)&&dx*out.getOffsetX()+dz*out.getOffsetZ()<=0)continue;
            Map<BlockPos,Change> changes=new LinkedHashMap<>();boolean safe=true;
            // The stair descends outward from the top; a lower corridor returns to the cell.
            for(int step=0;step<rise&&safe;step++)for(int lane=-1;lane<=1&&safe;lane++){
                BlockPos base=top.offset(out,step).offset(out.rotateYClockwise(),lane).down(step+1);
                for(int h=1;h<=3&&safe;h++)safe=dungeonChange(w,memory,changes,base.up(h),Blocks.AIR.getDefaultState());
                if(safe)safe=dungeonChange(w,memory,changes,base,Blocks.COBBLESTONE_STAIRS.getDefaultState().with(StairsBlock.FACING,out.getOpposite()));
            }
            Direction side=out.rotateYClockwise();
            for(int step=0;step<=rise&&safe;step++){
                BlockPos base=new BlockPos(top.getX(),cell.getY()-1,top.getZ()).offset(out,step).offset(side,2);
                for(int h=1;h<=3&&safe;h++)safe=dungeonChange(w,memory,changes,base.up(h),Blocks.AIR.getDefaultState());
                if(safe)safe=dungeonChange(w,memory,changes,base,Blocks.STONE_BRICKS.getDefaultState());
            }
            // Join the lower corridor to the prison floor without leaving a gap.
            BlockPos join=new BlockPos(top.getX(),cell.getY()-1,top.getZ()).offset(side,2);
            while(safe&&(join.getX()!=cell.getX()||join.getZ()!=cell.getZ())){
                if(join.getX()!=cell.getX())join=join.add(Integer.signum(cell.getX()-join.getX()),0,0);
                else join=join.add(0,0,Integer.signum(cell.getZ()-join.getZ()));
                for(int h=1;h<=3&&safe;h++)safe=dungeonChange(w,memory,changes,join.up(h),Blocks.AIR.getDefaultState());
                if(safe)safe=dungeonChange(w,memory,changes,join,Blocks.STONE_BRICKS.getDefaultState());
            }
            if(!safe||changes.isEmpty())continue;
            add(w,memory,state,id,changes,cell,top);return;
        }
    }
    private static List<BlockPos> generatedDungeonRoute(ServerWorld w,MarkerEntity jail){
        BlockPos cell=jail.getBlockPos();List<BlockPos> stairs=new ArrayList<>();
        for(BlockPos p:BlockPos.iterate(cell.add(-12,-1,-12),cell.add(12,7,12))){
            if(w.isChunkLoaded(new ChunkPos(p).toLong())&&w.getBlockState(p).isOf(Blocks.COBBLESTONE_STAIRS))stairs.add(p.toImmutable());
        }
        if(stairs.size()<7)return List.of();
        stairs.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed().thenComparingDouble(p->p.getSquaredDistance(cell)));
        BlockPos top=stairs.getFirst();Direction up=w.getBlockState(top).get(StairsBlock.FACING),down=up.getOpposite(),side=up.rotateYClockwise();
        List<BlockPos> route=new ArrayList<>();BlockPos step=top;
        while(stairs.contains(step)){route.add(step.up());step=step.offset(down).down();}
        if(route.size()<5)return List.of();
        State saved=state(w);String id="dungeon_clearance_v5:"+jail.getUuidAsString();
        if(!saved.ids.contains(id)){
            Map<BlockPos,Change> changes=new LinkedHashMap<>();var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);boolean safe=true;
            // Three full blocks of headroom, including the opening through the castle floor.
            for(BlockPos foot:route)for(int lane=-1;lane<=1;lane++){
                BlockPos base=foot.down().offset(side,lane);
                for(int h=1;h<=3;h++)safe&=dungeonChange(w,memory,changes,base.up(h),Blocks.AIR.getDefaultState());
                safe&=dungeonChange(w,memory,changes,base,Blocks.COBBLESTONE_STAIRS.getDefaultState().with(StairsBlock.FACING,up));
            }
            // Bridge the two missing upper steps all the way to the castle walking surface.
            int missing=Math.max(0,7-(top.getY()-cell.getY()));
            for(int n=1;n<=missing;n++)for(int lane=-1;lane<=1;lane++){
                BlockPos base=top.offset(up,n).up(n).offset(side,lane);
                safe&=dungeonChange(w,memory,changes,base,Blocks.COBBLESTONE_STAIRS.getDefaultState().with(StairsBlock.FACING,up));
                for(int h=1;h<=3;h++)safe&=dungeonChange(w,memory,changes,base.up(h),Blocks.AIR.getDefaultState());
            }
            BlockPos upper=top.offset(up,missing).up(missing);
            for(int lane=-1;lane<=1;lane++)for(int h=0;h<3;h++)safe&=dungeonChange(w,memory,changes,upper.offset(up).up(1+h).offset(side,lane),Blocks.AIR.getDefaultState());
            for(BlockPos p:BlockPos.iterate(cell.add(-4,0,-4),cell.add(4,12,4)))if(w.getBlockState(p).isOf(Blocks.LADDER))safe&=dungeonChange(w,memory,changes,p,Blocks.AIR.getDefaultState());
            if(safe)add(w,memory,saved,id,changes,cell,top.up(3));
        }
        Job upgrade=saved.jobs.stream().filter(j->j.id().equals(id)).findFirst().orElse(null);
        if(upgrade==null||upgrade.changes().stream().anyMatch(c->!w.getBlockState(c.pos()).equals(c.after())))return List.of();
        // Follow the existing open lower corridor instead of jumping from the last step to the cell.
        BlockPos start=route.getLast();Map<BlockPos,BlockPos> previous=new HashMap<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(start);previous.put(start,start);
        while(!queue.isEmpty()&&!previous.containsKey(cell)){
            BlockPos p=queue.removeFirst();
            for(Direction d:Direction.Type.HORIZONTAL){BlockPos q=p.offset(d);
                if(previous.containsKey(q)||q.getManhattanDistance(cell)>28||!w.isChunkLoaded(new ChunkPos(q).toLong()))continue;
                if(!w.getBlockState(q).getCollisionShape(w,q).isEmpty()||!w.getBlockState(q.up()).getCollisionShape(w,q.up()).isEmpty()||w.getBlockState(q.down()).getCollisionShape(w,q.down()).isEmpty())continue;
                previous.put(q,p);queue.add(q);
            }
        }
        if(!previous.containsKey(cell))return List.of();
        List<BlockPos> corridor=new ArrayList<>();for(BlockPos p=cell;!p.equals(start);p=previous.get(p))corridor.add(p);
        Collections.reverse(corridor);route.addAll(corridor);return route;
    }
    private record RouteCache(long until,List<BlockPos> route){}
    private static final Map<MarkerEntity,RouteCache> ROUTES=new WeakHashMap<>();
    static List<BlockPos> dungeonRoute(ServerWorld w,MarkerEntity jail){
        var cached=ROUTES.get(jail);if(cached!=null&&cached.until()>w.getTime())return cached.route();
        List<BlockPos> route=computeDungeonRoute(w,jail);
        ROUTES.put(jail,new RouteCache(w.getTime()+(route.isEmpty()?40:100),route));return route;
    }
    private static List<BlockPos> computeDungeonRoute(ServerWorld w,MarkerEntity jail){
        List<BlockPos> generated=generatedDungeonRoute(w,jail);if(!generated.isEmpty())return generated;
        State state=state(w);String id="dungeon_stairs_v4:"+jail.getUuidAsString();
        Job job=state.jobs.stream().filter(j->j.id().equals(id)).findFirst().orElse(null);
        if(job==null){planDungeonStairs(w,w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE),state,jail);return List.of();}
        for(Change c:job.changes()){
            if(!w.isChunkLoaded(new ChunkPos(c.pos()).toLong()))return List.of();
            BlockState actual=w.getBlockState(c.pos());if(c.after().isAir()?!actual.isAir():!actual.isOf(c.after().getBlock()))return List.of();
        }
        List<BlockPos> steps=new ArrayList<>();List<BlockPos> corridor=new ArrayList<>();
        for(Change c:job.changes()){
            if(c.after().isOf(Blocks.COBBLESTONE_STAIRS))steps.add(c.pos().up());
            else if(c.after().isOf(Blocks.STONE_BRICKS))corridor.add(c.pos().up());
        }
        if(steps.isEmpty())return List.of();
        steps.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());
        List<BlockPos> lane=new ArrayList<>();
        for(BlockPos p:steps)if(lane.isEmpty()||p.getY()<lane.getLast().getY())lane.add(p);
        steps=lane;
        BlockPos end=steps.getLast();
        while(!corridor.isEmpty()){
            final BlockPos previous=end;
            BlockPos next=corridor.stream().min(Comparator.comparingDouble(p->p.getSquaredDistance(previous))).orElseThrow();
            corridor.remove(next);if(next.getSquaredDistance(previous)>4)break;steps.add(next);end=next;
        }
        steps.add(jail.getBlockPos());return steps;
    }
    private static void planWall(ServerWorld w,VillageMemory memory,State s,VillageMemory.Team team){
        String prefix="village_wall_v1:"+Long.toUnsignedString(team.village);Job layout=s.jobs.stream().filter(j->j.id().equals(prefix)).findFirst().orElse(null);
        if(layout==null){
            var buildings=memory.houses.stream().filter(h->!h.kind.equals("infrastructure")&&h.recordKey.indexOf("rebel_camp:")<0&&h.min.isWithinDistance(team.anchor,192)).toList();
            if(buildings.stream().filter(h->h.kind.equals("house")).count()<3)return;
            int minX=buildings.stream().mapToInt(h->h.min.getX()).min().orElse(team.anchor.getX())-10;
            int maxX=buildings.stream().mapToInt(h->h.max.getX()).max().orElse(team.anchor.getX())+10;
            int minZ=buildings.stream().mapToInt(h->h.min.getZ()).min().orElse(team.anchor.getZ())-10;
            int maxZ=buildings.stream().mapToInt(h->h.max.getZ()).max().orElse(team.anchor.getZ())+10;
            if(maxX-minX>384||maxZ-minZ>384)return;
            add(w,memory,s,prefix,new LinkedHashMap<>(),new BlockPos(minX,team.anchor.getY(),minZ),new BlockPos(maxX,team.anchor.getY(),maxZ));
            return;
        }
        for(int side=0;side<4;side++){
            int length=side<2?layout.max().getX()-layout.min().getX():layout.max().getZ()-layout.min().getZ();
            for(int first=0;first<=length;first+=8){
                String id=prefix+":"+side+":"+first;if(s.ids.contains(id))continue;
                Map<BlockPos,Change> changes=new LinkedHashMap<>();boolean loaded=true,usable=true;BlockPos anchor=null;
                for(int k=first;k<=Math.min(length,first+7);k++){
                    int x=side<2?layout.min().getX()+k:side==2?layout.min().getX():layout.max().getX();
                    int z=side>=2?layout.min().getZ()+k:side==0?layout.min().getZ():layout.max().getZ();
                    BlockPos probe=new BlockPos(x,team.anchor.getY(),z);if(!w.isChunkLoaded(new ChunkPos(probe).toLong())){loaded=false;break;}
                    int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);BlockPos base=new BlockPos(x,y,z);anchor=base;
                    if(!natural(w.getBlockState(base.down()))){usable=false;break;}
                    boolean opening=Math.abs(k-length/2)<=3;
                    for(int h=0;h<=4;h++)if(!editable(w.getBlockState(base.up(h)))){usable=false;break;}
                    if(!usable)break;
                    if(opening){
                        change(w,changes,base.down(),Blocks.POLISHED_ANDESITE.getDefaultState());
                        if(Math.abs(k-length/2)==3)for(int h=0;h<4;h++)change(w,changes,base.up(h),Blocks.CHISELED_STONE_BRICKS.getDefaultState());
                        change(w,changes,base.up(4),Blocks.STONE_BRICKS.getDefaultState());continue;
                    }
                    for(int h=0;h<3;h++)change(w,changes,base.up(h),(h==0?Blocks.POLISHED_TUFF:k%6==0?Blocks.CHISELED_STONE_BRICKS:(k+h)%11==0?Blocks.TUFF_BRICKS:Blocks.STONE_BRICKS).getDefaultState());
                    change(w,changes,base.up(3),k%2==0?Blocks.STONE_BRICK_WALL.getDefaultState():Blocks.STONE_BRICK_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.BOTTOM));
                    if(k%12==0)change(w,changes,base.up(4),Blocks.LANTERN.getDefaultState());
                }
                if(!loaded||!usable||anchor==null)continue;
                add(w,memory,s,id,changes,anchor,anchor);return; // Plan one short section per pass.
            }
        }
    }
    private VillageCivilWorks(){}
}
