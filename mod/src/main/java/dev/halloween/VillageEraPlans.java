package dev.halloween;

import com.google.gson.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;

/** Uses vanilla house geometry, preserving doors, beds, stairs and roof orientation. */
final class VillageEraPlans {
    record Template(Vec3i size,List<VillageMemory.BlockRecord> blocks){}
    private static final Map<String,Template> TEMPLATES=new HashMap<>();
    private static final Map<VillageMemory.Team,Integer> TREASURY_SEARCH=new WeakHashMap<>();
    private static final Map<VillageMemory.Team,Integer> FARM_SEARCH=new WeakHashMap<>();
    private static final Map<VillageMemory.Team,Integer> HOME_SEARCH=new WeakHashMap<>();
    private static final Map<ServerWorld,Integer> DOOR_CURSOR=new WeakHashMap<>();
    @SuppressWarnings({"rawtypes","unchecked"})
    private static BlockState copy(BlockState source,Block target){
        BlockState out=target.getDefaultState();for(var e:source.getEntries().entrySet()){Property p=e.getKey();if(out.contains(p))out=out.with(p,(Comparable)e.getValue());}return out;
    }
    static BlockState stone(BlockState old){
        Block b=old.getBlock();
        if(old.isIn(BlockTags.LOGS))return Blocks.POLISHED_ANDESITE.getDefaultState();
        if(old.isIn(BlockTags.PLANKS))return Blocks.BRICKS.getDefaultState();
        if(Registries.BLOCK.getId(b).getPath().contains("terracotta"))return Blocks.TUFF_BRICKS.getDefaultState();
        String name=Registries.BLOCK.getId(b).getPath();
        boolean wood=List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","pale_oak","bamboo","crimson","warped").stream().anyMatch(n->name.startsWith(n+"_")||name.startsWith("stripped_"+n));
        if(wood&&b instanceof StairsBlock)return copy(old,Blocks.STONE_BRICK_STAIRS);
        if(wood&&b instanceof SlabBlock)return copy(old,Blocks.BRICK_SLAB);
        if(wood&&b instanceof FenceBlock)return Blocks.STONE_BRICK_WALL.getDefaultState();
        if(b instanceof WallTorchBlock)return old;
        if(b instanceof TorchBlock)return Blocks.LANTERN.getDefaultState();
        return old;
    }
    static Template template(ServerWorld w,String name){
        if(TEMPLATES.containsKey(name))return TEMPLATES.get(name);
        try(var reader=w.getServer().getResourceManager().getResource(Identifier.of("halloween","era_templates/"+name+".json")).orElseThrow().getReader()){
            var json=JsonParser.parseReader(reader).getAsJsonObject();var size=json.getAsJsonArray("size");List<BlockState> palette=new ArrayList<>();
            for(var entry:json.getAsJsonArray("palette")){
                var o=entry.getAsJsonObject();var state=Registries.BLOCK.get(Identifier.of(o.get("name").getAsString())).getDefaultState();
                for(var e:o.getAsJsonObject("properties").entrySet())state=property(state,e.getKey(),e.getValue().getAsString());palette.add(state);
            }
            List<VillageMemory.BlockRecord> blocks=new ArrayList<>();
            for(var entry:json.getAsJsonArray("blocks")){var a=entry.getAsJsonArray();var state=palette.get(a.get(3).getAsInt());if(state.isOf(Blocks.JIGSAW)||state.isOf(Blocks.STRUCTURE_VOID))continue;blocks.add(new VillageMemory.BlockRecord(new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()),state));}
            var t=new Template(new Vec3i(size.get(0).getAsInt(),size.get(1).getAsInt(),size.get(2).getAsInt()),blocks);TEMPLATES.put(name,t);return t;
        }catch(Exception e){org.slf4j.LoggerFactory.getLogger("halloween-eras").error("Cannot read vanilla house palette {}",name,e);return null;}
    }
    private static <T extends Comparable<T>> BlockState property(BlockState state,Property<T> p,String value){return p.parse(value).map(v->state.with(p,v)).orElse(state);}
    private static BlockState property(BlockState state,String name,String value){var p=state.getBlock().getStateManager().getProperty(name);return p==null?state:property(state,p,value);}
    private static boolean load(ServerWorld w,int x0,int z0,int x1,int z1){boolean ready=true;for(int x=x0>>4;x<=x1>>4;x++)for(int z=z0>>4;z<=z1>>4;z++)if(!VillageProjects.keepLoaded(w,new BlockPos(x*16,64,z*16)))ready=false;return ready;}
    static BlockPos vaultPos(VillageEraData.Vault vault,int depth,int side,int y){var forward=Direction.fromHorizontalQuarterTurns(vault.facing);return vault.origin.offset(forward,depth).offset(forward.rotateYClockwise(),side).up(y);}
    static void treasury(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s){
        var projects=VillageProjects.get(w);String id="era_treasury_v36:"+team.village;if(projects.exists(id))return;
        var legacy=projects.jobs.stream().filter(p->p.id.equals("era_treasury:"+team.village)).findFirst().orElse(null);
        if(legacy!=null){
            if(legacy.done&&s.xp.getOrDefault("treasury_replan_v36",0)==0||!legacy.done&&legacy.index>0)return;
            if(!legacy.done){projects.retire(legacy);s.vault=null;s.xp.put("treasury_replan_v36",1);VillageEraData.get(w).markDirty();}
        }
        if(VillageRelocations.get(w).moves.stream().anyMatch(m->m.village==team.village&&m.phase<3))return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        var castle=memory.houses.stream().filter(h->h.recordKey.contains("/royal_castle:")&&VillageEras.team(w,h.min)==team).toList();
        if(castle.isEmpty())return;
        int minX=castle.stream().mapToInt(h->h.min.getX()).min().orElseThrow(),maxX=castle.stream().mapToInt(h->h.max.getX()).max().orElseThrow();
        int minZ=castle.stream().mapToInt(h->h.min.getZ()).min().orElseThrow(),maxZ=castle.stream().mapToInt(h->h.max.getZ()).max().orElseThrow(),y=castle.getFirst().min.getY()+1;
        int[] distances={0,24,48,72,96,120,144,168,192};
        Direction[] directions={Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
        int candidates=distances.length*directions.length;
        int start=Math.floorMod(TREASURY_SEARCH.getOrDefault(team,0),candidates);
        for(int attempt=0;attempt<candidates;attempt++){
            int candidate=(start+attempt)%candidates;
            int offset=distances[candidate/4];Direction facing=directions[candidate%4];
            // Keep the same candidate while its chunks load, then advance if it is obstructed.
            TREASURY_SEARCH.put(team,candidate);
            BlockPos edge=switch(facing){case WEST->new BlockPos(minX+2,y,(minZ+maxZ)/2);case EAST->new BlockPos(maxX-2,y,(minZ+maxZ)/2);case NORTH->new BlockPos((minX+maxX)/2,y,minZ+2);default->new BlockPos((minX+maxX)/2,y,maxZ-2);};
            BlockPos origin=edge.offset(facing,offset+6);
            if(offset>0){if(!VillageProjects.keepLoaded(w,origin))return;origin=origin.withY(w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,origin.getX(),origin.getZ()));}
            int siteY=origin.getY();
            var vault=new VillageEraData.Vault(origin,facing.getHorizontalQuarterTurns(),"",-1,false);
            BlockPos far=vaultPos(vault,18,9,0),near=vaultPos(vault,0,-9,0);int x0=Math.min(far.getX(),near.getX()),x1=Math.max(far.getX(),near.getX()),z0=Math.min(far.getZ(),near.getZ()),z1=Math.max(far.getZ(),near.getZ());
            if(!load(w,x0,z0,x1,z1))return;
            var obstacles=memory.houses.stream().filter(h->h.kind.equals("house")&&!VillageRelocations.alreadyMoved(VillageRelocations.get(w),h)).filter(h->{var b=VillageRelocations.bounds(h);return b[0].getX()<=x1&&b[1].getX()>=x0&&b[0].getZ()<=z1&&b[1].getZ()>=z0;}).toList();
            Map<String,VillageMemory.House> unique=new LinkedHashMap<>();for(var h:obstacles){var b=VillageRelocations.bounds(h);unique.putIfAbsent(b[0].asLong()+":"+b[1].asLong(),h);}
            if(!unique.isEmpty()){
                if(unique.size()>2)continue;
                // Preflight the whole site: moving houses must actually make this location buildable.
                boolean viable=true;
                for(int depth=0;depth<=18&&viable;depth++)for(int side=-9;side<=9&&viable;side++)for(int height=0;height<=13;height++){
                    var pos=vaultPos(vault,depth,side,height);var old=w.getBlockState(pos);
                    boolean inHouse=unique.values().stream().anyMatch(h->{var b=VillageRelocations.bounds(h);return pos.getX()>=b[0].getX()&&pos.getX()<=b[1].getX()&&pos.getY()>=b[0].getY()&&pos.getY()<=b[1].getY()&&pos.getZ()>=b[0].getZ()&&pos.getZ()<=b[1].getZ();});
                    if(!inHouse&&(old.hasBlockEntity()||!old.isAir()&&!old.isReplaceable()&&!VillageProjects.terrain(old)&&!old.isIn(BlockTags.LOGS)&&!old.isIn(BlockTags.LEAVES))){viable=false;break;}
                }
                if(!viable)continue;
                for(var h:unique.values())if(!VillageRelocations.request(w,team,h))return;
                return;
            }
            Map<BlockPos,BlockState> plan=new LinkedHashMap<>();boolean safe=true;
            for(int d=0;d<=18;d++)for(int side=-9;side<=9;side++){
                BlockPos floor=vaultPos(vault,d,side,-1);int top=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,floor.getX(),floor.getZ());if(Math.abs(top-siteY)>9){safe=false;break;}
                for(int depth=1;depth<=12&&!w.getBlockState(floor.down(depth)).isSolidBlock(w,floor.down(depth));depth++)plan.put(floor.down(depth),Blocks.STONE_BRICKS.getDefaultState());
                plan.put(floor,Blocks.POLISHED_ANDESITE.getDefaultState());
                for(int h=0;h<=13;h++){BlockPos p=vaultPos(vault,d,side,h);var old=w.getBlockState(p);if(!old.isAir()&&!old.isReplaceable()&&!VillageProjects.terrain(old)&&!old.isIn(BlockTags.LOGS)&&!old.isIn(BlockTags.LEAVES)){safe=false;break;}plan.put(p,Blocks.AIR.getDefaultState());}
                if(d==18||Math.abs(side)==9){for(int h=0;h<5;h++)plan.put(vaultPos(vault,d,side,h),Blocks.STONE_BRICKS.getDefaultState());plan.put(vaultPos(vault,d,side,5),((d+side)%2==0?Blocks.STONE_BRICK_WALL:Blocks.STONE_BRICK_SLAB).getDefaultState());}
                boolean room=d>=5&&d<=15&&Math.abs(side)<=6;
                if(room){if(d==5||d==15||Math.abs(side)==6)for(int h=0;h<5;h++)plan.put(vaultPos(vault,d,side,h),((h==2&&d%3==0)?Blocks.GLASS_PANE:Blocks.STONE_BRICKS).getDefaultState());}
            }
            if(!safe)continue;
            var design=template(w,"stone_treasury");if(design==null)return;
            var rotation=switch(facing){case SOUTH->net.minecraft.util.BlockRotation.NONE;case WEST->net.minecraft.util.BlockRotation.CLOCKWISE_90;case NORTH->net.minecraft.util.BlockRotation.CLOCKWISE_180;default->net.minecraft.util.BlockRotation.COUNTERCLOCKWISE_90;};
            for(var record:design.blocks()){
                var pos=record.pos();plan.put(vaultPos(vault,pos.getZ(),9-pos.getX(),pos.getY()-1),record.state().rotate(rotation));
            }
            projects.add(w,id,team.village,"treasury",plan);
            if(projects.exists(id)){s.vault=vault;VillageEraData.get(w).markDirty();return;}
        }
    }
    static void repairEntrances(ServerWorld w){
        if(w.getPlayers().isEmpty()||w.getTime()%100!=0)return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
        // Reading a chunk may discover more structures; never iterate the live capture list.
        var houses=List.copyOf(memory.houses);if(houses.isEmpty())return;
        int first=Math.floorMod(DOOR_CURSOR.getOrDefault(w,0),houses.size()),limit=Math.min(32,houses.size());
        DOOR_CURSOR.put(w,(first+limit)%houses.size());
        for(int scan=0;scan<limit;scan++){
            var h=houses.get((first+scan)%houses.size());
            if(!h.kind.equals("house")||w.getPlayers().stream().noneMatch(p->p.getBlockPos().isWithinDistance(h.min,160)))continue;
            if(SteamHouseAccess.queue(w,h))return;
            String id="door_clearance_v1:"+h.min.asLong();if(projects.exists(id))continue;Map<BlockPos,BlockState> plan=new LinkedHashMap<>();
            for(var r:h.blueprint)if(r.state().getBlock() instanceof DoorBlock&&r.state().get(DoorBlock.HALF)==DoubleBlockHalf.LOWER)for(Direction d:new Direction[]{r.state().get(DoorBlock.FACING),r.state().get(DoorBlock.FACING).getOpposite()})for(int y=0;y<=1;y++){
                BlockPos p=r.pos().offset(d).up(y);if(!w.isChunkLoaded(new ChunkPos(p).toLong())||VillageProjects.pending(w,p))continue;if(w.getBlockState(p).isOf(Blocks.STONE_BRICK_WALL)&&w.getBlockState(p.up()).isOf(Blocks.LANTERN)){plan.put(p,Blocks.AIR.getDefaultState());plan.put(p.up(),Blocks.AIR.getDefaultState());}
            }
            if(!plan.isEmpty()){var team=VillageEras.team(w,h.min);if(team!=null){projects.add(w,id,team.village,"dungeon",plan);return;}}
        }
    }
    static void transition(ServerWorld w,VillageMemory.Team t,VillageEraData.Settlement s){
        VillageArchitectureMigration.tick(w,t,s);
        plantation(w,t,s);replaceGardens(w,t,s);
    }
    private static void plantation(ServerWorld w,VillageMemory.Team t,VillageEraData.Settlement s){
        var projects=VillageProjects.get(w);String id="era_plantation_v2:"+t.village;
        for(var legacy:List.copyOf(projects.jobs))if(legacy.village==t.village&&legacy.kind.equals("plantation")&&!legacy.done&&!legacy.id.equals(id)){
            boolean flooded=legacy.plan.stream().anyMatch(c->!c.before().getFluidState().isEmpty()&&c.after().isOf(Blocks.DIRT));
            if(!flooded)return;
            projects.retire(legacy);s.plantation=null;VillageEraData.get(w).markDirty();
        }
        if(projects.exists(id))return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        int extent=(int)Math.ceil(memory.houses.stream().filter(h->h.kind.equals("house")&&VillageEras.team(w,h.min)==t).mapToDouble(h->Math.sqrt(VillageEras.horizontal(t.anchor,h.min))).max().orElse(65))+18;
        for(int probe=0;probe<2;probe++){
            int attempt=Math.floorMod(FARM_SEARCH.merge(t,1,Integer::sum),512);
            double angle=attempt*2.399963229728653;int searchRadius=Math.min(360,Math.max(100,extent)+(attempt%12)*16);
            BlockPos base=t.anchor.add((int)(Math.cos(angle)*searchRadius),0,(int)(Math.sin(angle)*searchRadius));
            int x=base.getX(),z=base.getZ();if(!load(w,x-1,z-1,x+25,z+50)){FARM_SEARCH.merge(t,-1,Integer::sum);return;}int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z)-1;BlockPos origin=new BlockPos(x,y,z);
            boolean collision=memory.houses.stream().anyMatch(h->h.min.getX()<=x+27&&h.max.getX()>=x-3&&h.min.getZ()<=z+52&&h.max.getZ()>=z-3);if(collision)continue;
            Map<BlockPos,BlockState> plan=new LinkedHashMap<>();boolean good=true;
            for(int dx=-1;dx<=25&&good;dx++)for(int dz=-1;dz<=50&&good;dz++){
                BlockPos p=origin.add(dx,0,dz);int top=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());if(Math.abs(top-(y+1))>5||!w.getFluidState(new BlockPos(p.getX(),top-1,p.getZ())).isEmpty()){good=false;break;}
                for(int depth=1;depth<=24;depth++){var under=p.down(depth);if(w.getBlockState(under).isSolidBlock(w,under))break;plan.put(under,Blocks.DIRT.getDefaultState());}
                for(int h=1;h<=Math.max(7,top-y+5);h++){BlockPos q=p.up(h);var old=w.getBlockState(q);if(!old.isAir()&&!old.isReplaceable()&&!VillageProjects.terrain(old)&&!old.isIn(BlockTags.LOGS)&&!old.isIn(BlockTags.LEAVES)){good=false;break;}plan.put(q,Blocks.AIR.getDefaultState());}
                boolean border=dx==-1||dx==25||dz==-1||dz==50;
                if(border){plan.put(p,((dx+dz)%8==0?Blocks.STONE_BRICKS:Blocks.DIRT_PATH).getDefaultState());if((dx+dz)%8==0){plan.put(p.up(),Blocks.STONE_BRICK_WALL.getDefaultState());plan.put(p.up(2),Blocks.LANTERN.getDefaultState());}continue;}
                boolean water=dx%9==4;
                plan.put(p,water?Blocks.WATER.getDefaultState():Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE,7));
                if(water){if(dz%5==0)plan.put(p,plantationCrossing());}
                else plan.put(p.up(),(dx<8?Blocks.WHEAT:dx<17?Blocks.CARROTS:Blocks.POTATOES).getDefaultState());
            }
            if(!good)continue;
            for(int i=0;i<10;i++)plan.put(plantationComposter(origin,i),Blocks.COMPOSTER.getDefaultState());
            projects.add(w,id,t.village,"plantation",plan);if(projects.exists(id)){s.plantation=origin;VillageEraData.get(w).markDirty();return;}
        }
    }
    static BlockState plantationCrossing(){
        return Blocks.SPRUCE_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.TOP).with(SlabBlock.WATERLOGGED,true);
    }

    static void repairPlantationCrossings(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement settlement){
        var origin=settlement.plantation;
        if(origin==null)return;
        var projects=VillageProjects.get(w);
        String footingId="plantation_footings_v1:"+team.village;
        if(!projects.exists(footingId)){
            var footings=new LinkedHashMap<BlockPos,BlockState>();
            for(int dx=-1;dx<=25;dx++)for(int dz=-1;dz<=50;dz++){
                if(!(dx==-1||dx==25||dz==-1||dz==50)||(dx+dz)%8!=0)continue;
                var p=origin.add(dx,0,dz);if(!w.isChunkLoaded(p)){VillageProjects.keepLoaded(w,p);return;}
                var actual=w.getBlockState(p);var expected=projects.expected.get(p);
                if(expected==null||!(expected.isOf(Blocks.DIRT_PATH)||expected.isOf(Blocks.STONE_BRICKS))||expected.isOf(Blocks.STONE_BRICKS)&&VillageProjects.pending(w,p))continue;
                if(!actual.isOf(Blocks.STONE_BRICKS)&&(actual.isAir()||actual.isOf(Blocks.ICE)||VillageProjects.terrain(actual)))footings.put(p,Blocks.STONE_BRICKS.getDefaultState());
            }
            for(var job:projects.jobs)if(job.village==team.village&&job.kind.equals("plantation")&&!job.done){
                for(int i=0;i<job.plan.size();i++){
                    var cell=job.plan.get(i);var desired=footings.get(cell.pos());if(desired==null||!cell.after().isOf(Blocks.DIRT_PATH))continue;
                    var correction=new VillageCivilWorks.Change(cell.pos(),w.getBlockState(cell.pos()),desired);
                    if(i>=job.index)job.plan.set(i,correction);
                    else {job.plan.add(correction);projects.unfinished.merge(cell.pos(),1,Integer::sum);}
                    projects.expected.put(cell.pos(),desired);footings.remove(cell.pos());projects.markDirty();
                }
            }
            if(!footings.isEmpty())projects.add(w,footingId,team.village,"factory_supplies",footings);
        }
        String id="plantation_crossings:"+team.village;
        if(projects.exists(id)||!projects.complete(team.village,"plantation"))return;
        Map<BlockPos,BlockState> changes=new LinkedHashMap<>();
        for(int x=4;x<25;x+=9)for(int z=0;z<50;z+=5){
            var water=origin.add(x,0,z);
            if(!w.isChunkLoaded(water))return;
            var slab=w.getBlockState(water.up());
            if(w.getBlockState(water).isOf(Blocks.WATER)&&slab.isOf(Blocks.SPRUCE_SLAB)&&slab.get(SlabBlock.TYPE)==SlabType.TOP){
                changes.put(water.up(),Blocks.AIR.getDefaultState());
                changes.put(water,plantationCrossing());
            }
        }
        projects.add(w,id,team.village,"factory_supplies",changes);
    }

    static BlockPos plantationComposter(BlockPos origin,int slot){return origin.add(new int[]{2,7,12,16,21}[slot%5],1,slot<5?-1:50);}
    static void rebalanceComposters(ServerWorld w,VillageMemory.Team team,VillageEraData.Settlement s){
        if(s.plantation==null)return;var projects=VillageProjects.get(w);
        if(!projects.complete(team.village,"plantation"))return;
        String id="plantation_composters_v2:"+team.village;if(projects.exists(id))return;
        Set<BlockPos> desired=new HashSet<>();for(int i=0;i<10;i++)desired.add(plantationComposter(s.plantation,i));
        Map<BlockPos,BlockState> plan=new LinkedHashMap<>();
        for(int i=0;i<10;i++){
            var old=s.plantation.add(i*2+2,1,-1);var target=plantationComposter(s.plantation,i);
            if(!w.isChunkLoaded(new ChunkPos(old).toLong())||!w.isChunkLoaded(new ChunkPos(target).toLong())){VillageProjects.keepLoaded(w,old);VillageProjects.keepLoaded(w,target);return;}
            var existing=w.getBlockState(target);if(!existing.isAir()&&!existing.isReplaceable()&&!existing.isOf(Blocks.COMPOSTER))return;
            if(!desired.contains(old)&&w.getBlockState(old).isOf(Blocks.COMPOSTER))plan.put(old,Blocks.AIR.getDefaultState());
            plan.put(target,Blocks.COMPOSTER.getDefaultState());
        }
        projects.add(w,id,team.village,"plantation",plan);
    }
    static void replaceGardens(ServerWorld w,VillageMemory.Team t,VillageEraData.Settlement s){
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);int count=0;
        for(var farm:List.copyOf(memory.houses)){
            if(farm.recordKey.startsWith("era_")||VillageEras.team(w,farm.min)!=t)continue;
            var soil=farm.blueprint.stream().filter(r->r.state().isOf(Blocks.FARMLAND)).toList();
            // Already partially replaced gardens retain their old conversion project footprint.
            var previous=projects.jobs.stream().filter(p->p.kind.equals("farm_plaza")&&p.village==t.village&&p.id.equals("era_garden_plaza:"+t.village+":"+farm.min.asLong())).findFirst().orElse(null);
            if(soil.isEmpty()&&previous==null)continue;
            String id="era_garden_v2:"+t.village+":"+farm.min.asLong();if(projects.exists(id))continue;
            int y=soil.isEmpty()?previous.min.getY():soil.stream().mapToInt(r->r.pos().getY()).min().orElse(farm.min.getY());
            BlockPos low=farm.min,high=farm.max;
            if(farm.recordKey.startsWith("structure:"))try{String[] parts=farm.recordKey.split(":");low=BlockPos.fromLong(Long.parseLong(parts[parts.length-3]));high=BlockPos.fromLong(Long.parseLong(parts[parts.length-2]));}catch(RuntimeException ignored){}
            final int x0=low.getX(),x1=high.getX(),z0=low.getZ(),z1=high.getZ();
            // Chunk-fragment records describing the same farm must not make competing projects.
            boolean overlap=projects.jobs.stream().anyMatch(p->p.village==t.village&&p.id.startsWith("era_garden_v2:")&&p.min.getX()<=x1&&p.max.getX()>=x0&&p.min.getZ()<=z1&&p.max.getZ()>=z0);
            if(overlap)continue;
            if(x1-x0>24||z1-z0>24)continue;
            if(!load(w,x0,z0,x1,z1))return;Map<BlockPos,BlockState> plan=new LinkedHashMap<>();boolean safe=true;
            Map<BlockPos,BlockState> originals=new HashMap<>();for(var building:memory.houses)if(building.min.getX()<=x1&&building.max.getX()>=x0&&building.min.getZ()<=z1&&building.max.getZ()>=z0)for(var record:building.blueprint)originals.put(record.pos(),record.state());
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
                for(int h=0;h<=5;h++){
                    BlockPos q=new BlockPos(x,y+h,z);var old=w.getBlockState(q);
                    boolean recorded=originals.containsKey(q)&&originals.get(q).isOf(old.getBlock());
                    boolean oldPlaza=projects.expected.containsKey(q)&&projects.expected.get(q).isOf(old.getBlock());
                    if(old.hasBlockEntity()||!old.isAir()&&!VillageProjects.terrain(old)&&!old.isOf(Blocks.WATER)&&!old.isOf(Blocks.COMPOSTER)&&!recorded&&!oldPlaza){safe=false;break;}
                    plan.put(q,h==0?Blocks.POLISHED_ANDESITE.getDefaultState():Blocks.AIR.getDefaultState());
                }
            }
            if(!safe)continue;
            int cx=(x0+x1)/2,cz=(z0+z1)/2;
            if(x1-x0>=4&&z1-z0>=4&&Math.floorMod(farm.min.asLong(),2)==0){
                // A complete well, with water, a curb, pillars and a roof.
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)plan.put(new BlockPos(cx+dx,y+1,cz+dz),(dx==0&&dz==0?Blocks.WATER:Blocks.STONE_BRICKS).getDefaultState());
                for(int dx:new int[]{-1,1})for(int dz:new int[]{-1,1})for(int h=2;h<=3;h++)plan.put(new BlockPos(cx+dx,y+h,cz+dz),Blocks.STONE_BRICK_WALL.getDefaultState());
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(cx+dx>=x0&&cx+dx<=x1&&cz+dz>=z0&&cz+dz<=z1)plan.put(new BlockPos(cx+dx,y+4,cz+dz),Blocks.STONE_BRICK_SLAB.getDefaultState());
            }else{
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(cx+dx>=x0&&cx+dx<=x1&&cz+dz>=z0&&cz+dz<=z1)plan.put(new BlockPos(cx+dx,y+1,cz+dz),Blocks.CHISELED_STONE_BRICKS.getDefaultState());
                plan.put(new BlockPos(cx,y+2,cz),VillageStatue.ROYAL.getDefaultState());
                plan.put(new BlockPos(cx,y+4,cz),VillageStatue.ROYAL_TOP.getDefaultState());
            }
            // Supersede every overlapping old patch, including the water channel and timber border.
            projects.retireGardens(t.village,x0,z0,x1,z1);
            projects.add(w,id,t.village,"farm_plaza",plan);if(projects.exists(id)&&++count>=1)break;
        }
        memory.farms.removeIf(f->projects.jobs.stream().anyMatch(j->j.village==t.village&&j.kind.equals("farm_plaza")&&j.plan.stream().anyMatch(c->c.pos().equals(f.pos()))));memory.markDirty();
    }
    static int levelSite(ServerWorld w,int x,int z,int width,int depth,int maxSlope){
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(int dx=0;dx<width;dx++)for(int dz=0;dz<depth;dz++){
            int top=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x+dx,z+dz),limit=Math.max(w.getBottomY()+1,top-40);
            while(top>limit){var b=w.getBlockState(new BlockPos(x+dx,top-1,z+dz));if(b.isAir()||b.getFluidState().isEmpty()&&b.isReplaceable()||b.isIn(net.minecraft.registry.tag.BlockTags.LOGS)||b.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)){top--;continue;}break;}
            var ground=new BlockPos(x+dx,top-1,z+dz);
            if(!w.getFluidState(ground).isEmpty()||!w.getBlockState(ground).isSolidBlock(w,ground)){if(Boolean.getBoolean("halloween.recoveryTest")&&width>60)org.slf4j.LoggerFactory.getLogger("halloween-recovery").info("Station ground rejected at {}: {}",ground,w.getBlockState(ground));return Integer.MIN_VALUE;}
            low=Math.min(low,top);high=Math.max(high,top);
        }
        if(Boolean.getBoolean("halloween.recoveryTest")&&width>60&&high-low>maxSlope)org.slf4j.LoggerFactory.getLogger("halloween-recovery").info("Station terrain relief {} exceeds {}",high-low,maxSlope);
        return high-low<=maxSlope?high:Integer.MIN_VALUE;
    }
    static void expansion(ServerWorld w,VillageMemory.Team t,VillageEraData.Settlement s){
        if(s.era>=VillageEpoch.INDUSTRIAL||VillageIndustrial.pending(s))return;
        var projects=VillageProjects.get(w);long elapsed=w.getTime()-s.eraAt;
        if(s.era==2&&s.xp.getOrDefault(VillageSteam.PHASE,0)>0)return;
        int target=Math.min(s.baseHouses,(int)Math.max(1,(elapsed+12000)*s.baseHouses/48000));
        if(elapsed>=24000)target=Math.max(target,Math.min(20,s.baseHouses));if(elapsed>=48000)target=s.baseHouses;
        int existing=(int)projects.jobs.stream().filter(p->p.village==t.village&&p.kind.equals("house")).count();
        long pending=projects.jobs.stream().filter(p->p.village==t.village&&p.kind.equals("house")&&!p.done).count();if(existing>=target||pending>=8)return;
        String[] homes={"stone_home_courtyard","stone_home_tower","stone_home_gable","stone_home_villa"};boolean small=Math.floorMod(existing,10)<7;String name=small?"plains_small_house_"+(1+Math.floorMod(existing,8)):homes[Math.floorMod(existing/10*3+Math.floorMod(existing,10)-7,homes.length)];if(s.era>=3){small=false;name=existing%2==0?"steam_townhouse":"steam_villa";}
        Template template=template(w,name);if(template==null)return;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(int attempt=0;attempt<24;attempt++){
            int serial=existing*23+HOME_SEARCH.getOrDefault(t,0)+attempt;double angle=serial*2.399963229728653;int radius=24+Math.floorMod(serial,10)*9;
            int x=t.anchor.getX()+(int)(Math.cos(angle)*radius),z=t.anchor.getZ()+(int)(Math.sin(angle)*radius),sx=template.size.getX(),sz=template.size.getZ();
            int x1=x+sx-1,z1=z+sz-1;
            if(memory.houses.stream().filter(VillageBuildingSites::reserves).anyMatch(h->h.min.getX()-2<=x1&&h.max.getX()+2>=x&&h.min.getZ()-2<=z1&&h.max.getZ()+2>=z))continue;
            if(projects.jobs.stream().filter(p->VillageBuildingSites.building(p.kind)).anyMatch(p->p.min.getX()-4<=x1&&p.max.getX()+4>=x&&p.min.getZ()-4<=z1&&p.max.getZ()+4>=z))continue;
            if(!load(w,x-2,z-2,x1+2,z1+2)){HOME_SEARCH.put(t,HOME_SEARCH.getOrDefault(t,0)+attempt);return;}int y=levelSite(w,x,z,sx,sz,4);if(y==Integer.MIN_VALUE)continue;BlockPos origin=new BlockPos(x,y,z);
            if(!w.getWorldBorder().contains(origin)||!w.getWorldBorder().contains(origin.add(sx,template.size.getY(),sz)))continue;
            boolean good=true;Map<BlockPos,BlockState> plan=new LinkedHashMap<>();
            for(int dx=0;dx<sx&&good;dx++)for(int dz=0;dz<sz&&good;dz++){
                BlockPos p=origin.add(dx,-1,dz);int top=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ());if(Math.abs(top-y)>4){good=false;break;}
                for(int h=0;h<template.size.getY()+2;h++){
                    var q=p.up(h+1);var old=w.getBlockState(q);if(!old.isAir()&&!old.isReplaceable()&&!VillageProjects.terrain(old)&&!old.isIn(BlockTags.LOGS)&&!old.isIn(BlockTags.LEAVES)){good=false;break;}plan.put(q,Blocks.AIR.getDefaultState());
                }
                for(int depth=0;depth<=8;depth++){var q=p.down(depth);if(w.getBlockState(q).isSolidBlock(w,q))break;plan.put(q,Blocks.STONE_BRICKS.getDefaultState());}
            }
            if(!good)continue;
            for(var r:template.blocks){BlockState state=small?stone(r.state()):r.state();plan.put(origin.add(r.pos()),state);}
            String id=(s.era>=3?"gothic_expansion:":"era_house:")+t.village+":"+existing;projects.add(w,id,t.village,"house",plan);HOME_SEARCH.put(t,HOME_SEARCH.getOrDefault(t,0)+attempt+1);return;
        }
        HOME_SEARCH.merge(t,24,Integer::sum);
    }
    static void road(ServerWorld w,VillageMemory.Team team,BlockPos from,String suffix){
        Map<BlockPos,BlockState> plan=new LinkedHashMap<>();int dx=team.anchor.getX()-from.getX(),dz=team.anchor.getZ()-from.getZ(),steps=Math.max(Math.abs(dx),Math.abs(dz));
        for(int i=0;i<=steps;i++){
            int x=from.getX()+(int)Math.round(dx*(double)i/Math.max(1,steps)),z=from.getZ()+(int)Math.round(dz*(double)i/Math.max(1,steps));if(!w.isChunkLoaded(new ChunkPos(new BlockPos(x,0,z)).toLong()))continue;
            int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
            for(int side=-1;side<=1;side++){BlockPos p=new BlockPos(x+(Math.abs(dx)<Math.abs(dz)?side:0),y,z+(Math.abs(dx)>=Math.abs(dz)?side:0));var old=w.getBlockState(p);if(old.isIn(BlockTags.DIRT))plan.put(p,Blocks.DIRT_PATH.getDefaultState());}
        }
        VillageProjects.get(w).add(w,"era_road:"+team.village+":"+suffix,team.village,"road",plan);
    }
    private VillageEraPlans(){}
}

