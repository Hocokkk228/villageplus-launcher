package dev.halloween;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Persisted copy, container handover, resident reassignment, and demolition. */
final class VillageRelocations extends PersistentState {
    record Cell(BlockPos pos,BlockState state,NbtCompound data){
        static final Codec<Cell> CODEC=RecordCodecBuilder.create(i->i.group(BlockPos.CODEC.fieldOf("pos").forGetter(Cell::pos),BlockState.CODEC.fieldOf("state").forGetter(Cell::state),NbtCompound.CODEC.fieldOf("data").forGetter(Cell::data)).apply(i,Cell::new));
    }
    static final class Move {
        static final Codec<Move> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("id").forGetter(m->m.id),Codec.LONG.fieldOf("village").forGetter(m->m.village),BlockPos.CODEC.fieldOf("min").forGetter(m->m.min),BlockPos.CODEC.fieldOf("max").forGetter(m->m.max),BlockPos.CODEC.fieldOf("destination").forGetter(m->m.destination),Cell.CODEC.listOf().fieldOf("cells").forGetter(m->m.cells),Codec.INT.fieldOf("phase").forGetter(m->m.phase),Codec.INT.fieldOf("cursor").forGetter(m->m.cursor),Codec.STRING.optionalFieldOf("status","").forGetter(m->m.status)).apply(i,Move::new));
        final String id;final long village;final BlockPos min,max,destination;final List<Cell> cells;int phase,cursor;String status;
        Move(String id,long village,BlockPos min,BlockPos max,BlockPos destination,List<Cell> cells,int phase,int cursor,String status){this.id=id;this.village=village;this.min=min;this.max=max;this.destination=destination;this.cells=new ArrayList<>(cells);this.phase=phase;this.cursor=cursor;this.status=status;}
        BlockPos translated(BlockPos p){return p.subtract(min).add(destination);}
        boolean contains(BlockPos p){return p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();}
    }
    private static final Codec<VillageRelocations> CODEC=Move.CODEC.listOf().xmap(VillageRelocations::new,s->s.moves);
    private static final PersistentStateType<VillageRelocations> TYPE=new PersistentStateType<>("halloween_house_relocations",()->new VillageRelocations(List.of()),CODEC,null);
    final List<Move> moves;
    private static final Map<Long,Integer> SEARCH=new HashMap<>();
    VillageRelocations(List<Move> moves){this.moves=new ArrayList<>(moves);}
    static VillageRelocations get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(TYPE);}
    static BlockPos[] bounds(VillageMemory.House h){
        if(h.recordKey.startsWith("structure:"))try{var a=h.recordKey.split(":");return new BlockPos[]{BlockPos.fromLong(Long.parseLong(a[a.length-3])),BlockPos.fromLong(Long.parseLong(a[a.length-2]))};}catch(RuntimeException ignored){}
        return new BlockPos[]{h.min,h.max};
    }
    static boolean alreadyMoved(VillageRelocations state,VillageMemory.House house){
        var bounds=bounds(house);
        return state.moves.stream().anyMatch(m->m.phase>=3&&m.min.equals(bounds[0])&&m.max.equals(bounds[1]));
    }
    static boolean request(ServerWorld w,VillageMemory.Team team,VillageMemory.House house){
        var state=get(w);var bounds=bounds(house);BlockPos min=bounds[0],max=bounds[1];
        if(state.moves.stream().anyMatch(m->m.min.equals(min)&&m.max.equals(max)))return true;
        int width=max.getX()-min.getX()+1,height=max.getY()-min.getY()+1,depth=max.getZ()-min.getZ()+1;
        if(width<1||height<1||depth<1||(long)width*height*depth>32768)return false;
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
        for(int probe=0;probe<4;probe++){
            int attempt=SEARCH.merge(team.village,1,Integer::sum);double angle=attempt*2.399963229728653;int radius=85+(attempt%9)*20;
            int x=team.anchor.getX()+(int)(Math.cos(angle)*radius),z=team.anchor.getZ()+(int)(Math.sin(angle)*radius);
            if(memory.houses.stream().filter(h->!h.recordKey.startsWith("era_road:")&&!h.recordKey.startsWith("road_network_")).anyMatch(h->h.min.getX()-5<=x+width&&h.max.getX()+5>=x&&h.min.getZ()-5<=z+depth&&h.max.getZ()+5>=z))continue;
            if(projects.jobs.stream().filter(p->!p.kind.equals("road")).anyMatch(p->p.min.getX()-4<=x+width&&p.max.getX()+4>=x&&p.min.getZ()-4<=z+depth&&p.max.getZ()+4>=z))continue;
            if(state.moves.stream().anyMatch(m->m.destination.getX()-4<=x+width&&m.destination.getX()+(m.max.getX()-m.min.getX())+4>=x&&m.destination.getZ()-4<=z+depth&&m.destination.getZ()+(m.max.getZ()-m.min.getZ())+4>=z))continue;
            boolean loaded=true;for(int cx=x>>4;cx<=(x+width)>>4;cx++)for(int cz=z>>4;cz<=(z+depth)>>4;cz++)if(!VillageProjects.keepLoaded(w,new BlockPos(cx*16,64,cz*16)))loaded=false;
            for(int cx=min.getX()>>4;cx<=max.getX()>>4;cx++)for(int cz=min.getZ()>>4;cz<=max.getZ()>>4;cz++)if(!VillageProjects.keepLoaded(w,new BlockPos(cx*16,64,cz*16)))loaded=false;
            if(!loaded){SEARCH.merge(team.village,-1,Integer::sum);return false;}
            int y=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z)-1;var destination=new BlockPos(x,y,z);boolean safe=w.getWorldBorder().contains(destination)&&w.getWorldBorder().contains(destination.add(width,height,depth));
            for(int dx=0;dx<width&&safe;dx++)for(int dz=0;dz<depth&&safe;dz++){
                if(!w.getFluidState(new BlockPos(x+dx,w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x+dx,z+dz)-1,z+dz)).isEmpty()){safe=false;break;}
                if(Math.abs(w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x+dx,z+dz)-(y+1))>3){safe=false;break;}
                for(int dy=0;dy<height;dy++){var old=w.getBlockState(destination.add(dx,dy,dz));if(old.hasBlockEntity()||!old.isAir()&&!old.isReplaceable()&&!VillageCivilWorks.natural(old)){safe=false;break;}}
            }
            if(!safe)continue;
            List<Cell> cells=new ArrayList<>();for(BlockPos p:BlockPos.iterate(min,max)){var be=w.getBlockEntity(p);cells.add(new Cell(p.toImmutable(),w.getBlockState(p),be==null?new NbtCompound():be.createNbt(w.getRegistryManager())));}
            var move=new Move("relocate:"+team.village+":"+min.asLong(),team.village,min,max,destination,cells,0,0,"");state.moves.add(move);state.markDirty();return true;
        }
        return false;
    }
    static boolean sourceCompatible(BlockState before,BlockState now){
        if(now.isAir()||now.isOf(before.getBlock()))return true;
        // Grass spreading, trampling and road maintenance are normal changes, not a modified house.
        boolean oldSoil=before.isIn(net.minecraft.registry.tag.BlockTags.DIRT)||before.isOf(Blocks.FARMLAND)||before.isOf(Blocks.DIRT_PATH);
        boolean newSoil=now.isIn(net.minecraft.registry.tag.BlockTags.DIRT)||now.isOf(Blocks.FARMLAND)||now.isOf(Blocks.DIRT_PATH);
        var stone=VillageEraPlans.stone(before);
        return oldSoil&&newSoil||before.isOf(Blocks.WATER)&&newSoil
            ||!stone.equals(before)&&now.isOf(stone.getBlock())
            ||(before.isAir()||before.isReplaceable())&&now.isReplaceable()&&!now.hasBlockEntity();
    }
    static boolean mayRemove(ServerWorld w,BlockPos p){
        for(var m:get(w).moves)if(m.phase==2&&m.contains(p)){
            var be=w.getBlockEntity(p);if(be==null)return true;
            return be instanceof Inventory inv&&inv.isEmpty()||!(be instanceof Inventory)&&m.cells.stream().anyMatch(c->c.pos().equals(p)&&!c.data().isEmpty());
        }return false;
    }
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillageRelocations::tick);}
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty()||w.getTime()%20!=0)return;advance(w);
    }
    static void advance(ServerWorld w){var state=get(w);var projects=VillageProjects.get(w);var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(var m:state.moves){
            if(m.phase>=3||!Boolean.getBoolean("halloween.regression036")&&w.getPlayers().stream().noneMatch(p->p.getBlockPos().isWithinDistance(m.min,384)))continue;
            if(m.phase==0){
                if(projects.exists(m.id+":copy")){var job=projects.jobs.stream().filter(p->p.id.equals(m.id+":copy")).findFirst().orElseThrow();if(job.done){m.phase=1;state.markDirty();}continue;}
                Map<BlockPos,BlockState> plan=new LinkedHashMap<>();boolean loaded=true;
                for(var c:m.cells){var pos=m.translated(c.pos());if(!VillageProjects.keepLoaded(w,pos)){loaded=false;continue;}plan.put(pos,c.state());}
                if(!loaded)continue;
                for(int x=m.min.getX();x<=m.max.getX();x++)for(int z=m.min.getZ();z<=m.max.getZ();z++)for(int down=1;down<=12;down++){
                    var pos=m.translated(new BlockPos(x,m.min.getY(),z)).down(down);if(w.getBlockState(pos).isSolidBlock(w,pos))break;plan.put(pos,Blocks.STONE_BRICKS.getDefaultState());
                }
                projects.add(w,m.id+":copy",m.village,"relocation_copy",plan);m.status="Строится копия дома";state.markDirty();continue;
            }
            if(m.phase==1){
                if(m.cursor<m.cells.size()){
                    // One container per tick; its latest contents are handed over without an item drop.
                    int limit=Math.min(m.cells.size(),m.cursor+256);
                    for(;m.cursor<limit;m.cursor++){
                        var c=m.cells.get(m.cursor);if(c.data().isEmpty())continue;
                        var src=c.pos();var dst=m.translated(src);if(!VillageProjects.keepLoaded(w,src)||!VillageProjects.keepLoaded(w,dst))break;
                        var source=w.getBlockEntity(src);var target=w.getBlockEntity(dst);
                        if(source==null||target==null||source.getType()!=target.getType()){m.status="Ожидание контейнера "+src.toShortString();break;}
                        if(source instanceof Inventory from&&target instanceof Inventory to){
                            if(from.isEmpty()&&!to.isEmpty())continue; // Resuming a completed handover.
                            if(!to.isEmpty()){m.status="Новый контейнер занят "+dst.toShortString();break;}
                        }
                        var latest=source.createNbt(w.getRegistryManager());m.cells.set(m.cursor,new Cell(src,c.state(),latest));state.markDirty();
                        target.read(NbtReadView.create(ErrorReporter.EMPTY,w.getRegistryManager(),latest.copy()));target.markDirty();
                        if(source instanceof Inventory inv){inv.clear();source.markDirty();}
                    }
                    state.markDirty();continue;
                }
                m.phase=2;m.status="Перенос жителей и разбор старого дома";state.markDirty();
                var delta=m.destination.subtract(m.min);
                for(var person:w.getEntitiesByClass(VillagerEntity.class,new Box(m.min).expand(384,96,384),v->v.isAlive())){
                    for(var key:List.of(MemoryModuleType.HOME,MemoryModuleType.JOB_SITE)){
                        var site=person.getBrain().getOptionalRegisteredMemory(key);if(site.isPresent()&&site.get().dimension().equals(w.getRegistryKey())&&m.contains(site.get().pos()))person.getBrain().remember(key,GlobalPos.create(w.getRegistryKey(),site.get().pos().add(delta)));
                    }
                }
                for(var job:List.copyOf(projects.jobs))if(!job.done&&!job.id.startsWith(m.id)&&job.plan.stream().anyMatch(c->m.contains(c.pos())))projects.retire(job);
                memory.houses.removeIf(h->m.contains(h.min)&&m.contains(h.max));
                var records=m.cells.stream().map(c->new VillageMemory.BlockRecord(m.translated(c.pos()),c.state())).toList();
                memory.houses.removeIf(h->h.recordKey.equals(m.id+":copy"));
                memory.houses.add(new VillageMemory.House(m.destination,m.translated(m.max),records,w.getTime(),List.of(),0,true,List.of(),List.of(),List.of(),"house",m.id+":home",List.of()));memory.markDirty();VillageWorkplaceIndex.invalidate(w);
            }
            if(m.phase==2){
                if(projects.exists(m.id+":clear")){var job=projects.jobs.stream().filter(p->p.id.equals(m.id+":clear")).findFirst().orElseThrow();if(job.done){m.phase=3;m.status="Дом перенесён";state.markDirty();}continue;}
                // Conversion and road crews may have finished source work while its copy was built.
                // Bring those recognised changes across before demolition, including saves already in phase 2.
                var refresh=projects.jobs.stream().filter(p->p.id.equals(m.id+":refresh")).findFirst().orElse(null);
                if(refresh!=null&&!refresh.done){m.status="Обновляется копия после перестройки";continue;}
                if(refresh==null){
                    Map<BlockPos,BlockState> updates=new LinkedHashMap<>();List<Cell> revised=new ArrayList<>(m.cells);boolean loaded=true;
                    for(int i=0;i<m.cells.size();i++){
                        var c=m.cells.get(i);if(!VillageProjects.keepLoaded(w,c.pos())||!VillageProjects.keepLoaded(w,m.translated(c.pos()))){loaded=false;break;}
                        var now=w.getBlockState(c.pos());
                        if(!now.equals(c.state())&&!now.isAir()&&!now.hasBlockEntity()&&sourceCompatible(c.state(),now)){
                            updates.put(m.translated(c.pos()),now);revised.set(i,new Cell(c.pos(),now,c.data()));
                        }
                    }
                    if(!loaded)continue;
                    if(!updates.isEmpty()){
                        projects.add(w,m.id+":refresh",m.village,"relocation_copy",updates);
                        if(projects.exists(m.id+":refresh")){m.cells.clear();m.cells.addAll(revised);m.status="Обновляется копия после перестройки";state.markDirty();}
                        continue;
                    }
                }
                Map<BlockPos,BlockState> plan=new LinkedHashMap<>();boolean ready=true;
                var protectedVolumes=VillageBuildingSites.protectedVolumes(w,m.min,m.max,m.id);
                for(var c:m.cells){var pos=c.pos();if(VillageBuildingSites.protectedAt(protectedVolumes,pos))continue;if(!VillageProjects.keepLoaded(w,pos)){ready=false;break;}var now=w.getBlockState(pos);
                    if(!sourceCompatible(c.state(),now)){m.status="Старый дом изменён "+pos.toShortString();ready=false;break;}
                    if(now.hasBlockEntity()&&!mayRemove(w,pos)){m.status="В старый контейнер добавлены вещи "+pos.toShortString();ready=false;break;}
                    plan.put(pos,pos.getY()==m.min.getY()?Blocks.GRASS_BLOCK.getDefaultState():Blocks.AIR.getDefaultState());
                }
                if(ready)projects.add(w,m.id+":clear",m.village,"relocation_clear",plan);
            }
        }
    }
}
