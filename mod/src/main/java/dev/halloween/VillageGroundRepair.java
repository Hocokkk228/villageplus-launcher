package dev.halloween;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Repairs old hollow plateaus without unloading players or filling their dungeon. */
final class VillageGroundRepair extends PersistentState {
    private static final class Zone {
        static final Codec<Zone> CODEC=RecordCodecBuilder.create(i->i.group(BlockPos.CODEC.fieldOf("min").forGetter(z->z.min),BlockPos.CODEC.fieldOf("max").forGetter(z->z.max),Codec.INT.fieldOf("floor").forGetter(z->z.floor),Codec.LONG.listOf().fieldOf("finished").forGetter(z->new ArrayList<>(z.finished))).apply(i,Zone::new));
        final BlockPos min,max;final int floor;final Set<Long> finished;
        Zone(BlockPos min,BlockPos max,int floor,List<Long> finished){this.min=min;this.max=max;this.floor=floor;this.finished=new HashSet<>(finished);}
    }
    private static final Codec<VillageGroundRepair> CODEC=Zone.CODEC.listOf().xmap(VillageGroundRepair::new,s->s.zones);
    private static final PersistentStateType<VillageGroundRepair> TYPE=new PersistentStateType<>("halloween_ground_repair_v1",()->new VillageGroundRepair(List.of()),CODEC,null);
    private final List<Zone> zones;private final ArrayDeque<Tile> queue=new ArrayDeque<>();private final Set<String> queued=new HashSet<>();
    private static final class Tile {final Zone zone;final ChunkPos chunk;final String key;int column,y=Integer.MIN_VALUE,target,solid;Tile(Zone z,ChunkPos c){zone=z;chunk=c;key=z.min.asLong()+":"+c.toLong();}}
    private VillageGroundRepair(List<Zone> zones){this.zones=new ArrayList<>(zones);}
    static void register(){ServerTickEvents.END_WORLD_TICK.register(VillageGroundRepair::tick);}
    private static void tick(ServerWorld w){
        if(w.getPlayers().isEmpty())return;var state=w.getPersistentStateManager().getOrCreate(TYPE);var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        if(w.getTime()%200==0){
            for(var castle:List.copyOf(memory.houses))if(castle.recordKey.contains("/royal_castle:")&&w.getPlayers().stream().anyMatch(p->p.getBlockPos().isWithinDistance(castle.min,256))){
                VillageWorldgen.remember(w.getSeed(),new BlockBox(castle.min.getX(),castle.min.getY(),castle.min.getZ(),castle.max.getX(),castle.max.getY(),castle.max.getZ()));
                if(state.zones.stream().anyMatch(z->z.floor==castle.min.getY()&&castle.min.getX()>=z.min.getX()&&castle.min.getX()<=z.max.getX()&&castle.min.getZ()>=z.min.getZ()&&castle.min.getZ()<=z.max.getZ()))continue;
                var homes=memory.houses.stream().filter(h->h.min.isWithinDistance(castle.min,160)&&!h.recordKey.contains("/dungeon")&&(h.kind.equals("house")||h.recordKey.contains("/royal_castle:"))).toList();
                int x0=homes.stream().mapToInt(h->h.min.getX()).min().orElse(castle.min.getX()),z0=homes.stream().mapToInt(h->h.min.getZ()).min().orElse(castle.min.getZ()),x1=homes.stream().mapToInt(h->h.max.getX()).max().orElse(castle.max.getX()),z1=homes.stream().mapToInt(h->h.max.getZ()).max().orElse(castle.max.getZ());
                state.zones.add(new Zone(new BlockPos(x0,castle.min.getY(),z0),new BlockPos(x1,castle.min.getY(),z1),castle.min.getY(),List.of()));state.markDirty();
            }
            for(var zone:state.zones)for(int x=(zone.min.getX()-128)>>4;x<=(zone.max.getX()+128)>>4;x++)for(int z=(zone.min.getZ()-128)>>4;z<=(zone.max.getZ()+128)>>4;z++){
                var chunk=new ChunkPos(x,z);String key=zone.min.asLong()+":"+chunk.toLong();if(w.isChunkLoaded(chunk.toLong())&&!zone.finished.contains(chunk.toLong())&&state.queued.add(key))state.queue.add(new Tile(zone,chunk));
            }
        }
        for(var player:w.getPlayers())for(var jail:w.getEntitiesByClass(net.minecraft.entity.MarkerEntity.class,new Box(player.getBlockPos()).expand(256),m->m.getCommandTags().contains("halloween_jail")))VillagePrisons.prepare(w,jail);
        int reads=1024,changes=192;long until=System.nanoTime()+2_000_000;var protectedRooms=VillagePrisons.protectedBoxes(w);
        for(var h:memory.houses)if(h.recordKey.contains("/dungeon"))protectedRooms.add(new BlockBox(h.min.getX()-1,h.min.getY()-1,h.min.getZ()-1,h.max.getX()+1,h.max.getY()+1,h.max.getZ()+1));
        while(!state.queue.isEmpty()&&reads-->0&&changes>0&&System.nanoTime()<until){
            Tile t=state.queue.peek();if(!w.isChunkLoaded(t.chunk.toLong())||t.column>=256){state.queue.remove();state.queued.remove(t.key);if(t.column>=256){t.zone.finished.add(t.chunk.toLong());state.markDirty();}continue;}
            int x=t.chunk.getStartX()+(t.column&15),z=t.chunk.getStartZ()+(t.column>>4);
            if(t.y==Integer.MIN_VALUE){int d=Math.max(0,Math.max(Math.max(t.zone.min.getX()-x,x-t.zone.max.getX()),Math.max(t.zone.min.getZ()-z,z-t.zone.max.getZ())));t.target=t.zone.floor-(d+1)/2;t.y=t.target;t.solid=0;}
            if(t.y<Math.max(w.getBottomY(),t.target-96)||t.solid>=6){t.column++;t.y=Integer.MIN_VALUE;continue;}
            BlockPos p=new BlockPos(x,t.y--,z);if(protectedRooms.stream().anyMatch(b->b.contains(p))){t.solid=0;continue;}
            var old=w.getBlockState(p);
            if(old.isAir()||old.getBlock() instanceof FluidBlock){
                t.solid=0;if(!w.getOtherEntities(null,new Box(p),e->e instanceof LivingEntity&&e.isAlive()&&!e.isSpectator()).isEmpty())continue;
                w.setBlockState(p,(p.getY()==t.target?Blocks.GRASS_BLOCK:p.getY()>=t.target-3?Blocks.DIRT:Blocks.STONE).getDefaultState(),Block.NOTIFY_LISTENERS);changes--;
            }else if(old.isSolidBlock(w,p)&&!old.hasBlockEntity())t.solid++;else t.solid=0;
        }
    }
}
