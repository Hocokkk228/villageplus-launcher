package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.feature.TreeConfiguredFeatures;
/** A green northern taiga corridor: tall native pines, staggered forest edges and a clear rail cutting. */
final class RailForest {
 static final Map<ServerWorld,Set<Long>> WARMED=new WeakHashMap<>();
 static void warm(ServerWorld w,ChunkPos cp){if(!WARMED.computeIfAbsent(w,key->new HashSet<>()).add(cp.toLong()))return;var chunk=w.getChunk(cp.x,cp.z);var taiga=w.getRegistryManager().getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.OLD_GROWTH_PINE_TAIGA);chunk.populateBiomes((x,y,z,s)->taiga,w.getChunkManager().getNoiseConfig().getMultiNoiseSampler());chunk.markNeedsSaving();
  for(int x=cp.getStartX();x<=cp.getEndX();x++)for(int z=cp.getStartZ();z<=cp.getEndZ();z++){int top=w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,x,z);for(int y=top;y>=Math.max(w.getSeaLevel()-2,top-38);y--){var p=new BlockPos(x,y,z);var s=w.getBlockState(p);if(s.contains(net.minecraft.state.property.Properties.SNOWY)&&s.get(net.minecraft.state.property.Properties.SNOWY))w.setBlockState(p,s.with(net.minecraft.state.property.Properties.SNOWY,false),Block.NOTIFY_LISTENERS);else if(s.isOf(Blocks.SNOW)||s.isOf(Blocks.POWDER_SNOW))w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);else if(s.isOf(Blocks.SNOW_BLOCK))w.setBlockState(p,Blocks.GRASS_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS);else if(s.isOf(Blocks.ICE)||s.isOf(Blocks.FROSTED_ICE))w.setBlockState(p,Blocks.WATER.getDefaultState(),Block.NOTIFY_LISTENERS);}}
 }
 static void edge(ServerWorld w,RailPath path,double d){var center=path.center(d);var cp=new ChunkPos(BlockPos.ofFloored(center));for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)warm(w,new ChunkPos(cp.x+dx,cp.z+dz));
  var network=RailwayNetwork.get(w);if(network.stations.stream().anyMatch(s->s.center(32).squaredDistanceTo(center)<110*110))return;
  var random=net.minecraft.util.math.random.Random.create(w.getSeed()^Double.doubleToLongBits(d));
  for(int side:new int[]{-1,1})for(int row=0;row<2;row++){
   var q=path.at(d+row*8,side*(22+row*24+random.nextInt(7)));int x=(int)Math.floor(q.x),z=(int)Math.floor(q.z);int y=RailTerrain.ground(w,x,z);var root=new BlockPos(x,y,z);if(y<=w.getSeaLevel()||!w.getFluidState(root.down()).isEmpty()||!w.getBlockState(root.down()).isIn(net.minecraft.registry.tag.BlockTags.DIRT))continue;
   if(w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses.stream().anyMatch(h->root.getX()>h.min.getX()-8&&root.getX()<h.max.getX()+8&&root.getZ()>h.min.getZ()-8&&root.getZ()<h.max.getZ()+8))continue;
   var feature=w.getRegistryManager().getOrThrow(RegistryKeys.CONFIGURED_FEATURE).getOrThrow(row==0?TreeConfiguredFeatures.MEGA_PINE:TreeConfiguredFeatures.MEGA_SPRUCE).value();
   feature.generate(w,w.getChunkManager().getChunkGenerator(),random,root);
  }
 }
}
