package dev.halloween;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.*;
/** A walkable brick workshop with an office floor, gantries, stockrooms and a paved loading court. */
final class IndustrialPlantPlans {
 static final int WIDTH=36,DEPTH=44;
 static LinkedHashMap<BlockPos,BlockState> build(BlockPos base,int kind){var p=new LinkedHashMap<BlockPos,BlockState>();
  for(int x=0;x<WIDTH;x++)for(int z=-32;z<DEPTH;z++){
   p.put(base.add(x,0,z),(z<0?Blocks.GRAY_CONCRETE:Blocks.POLISHED_ANDESITE).getDefaultState());
   for(int y=1;y<=12;y++)p.put(base.add(x,y,z),Blocks.AIR.getDefaultState());
   if(z<0){if(x==1||x==WIDTH-2)p.put(base.add(x,0,z),Blocks.SMOOTH_STONE.getDefaultState());if(x==18&&Math.floorMod(z,6)<3)p.put(base.add(x,0,z),Blocks.WHITE_CONCRETE.getDefaultState());continue;}
   if(x==0||x==WIDTH-1||z==0||z==DEPTH-1)for(int y=1;y<=9;y++){
    var block=y==1||y==5||y==9?Blocks.STONE_BRICKS:((x+z+y)%11==0?Blocks.GRANITE:Blocks.BRICKS);
    boolean window=(x==0||x==WIDTH-1)&&z%6>=1&&z%6<=4&&y>=6&&y<=8;
    p.put(base.add(x,y,z),(window?Blocks.LIGHT_BLUE_STAINED_GLASS:block).getDefaultState());
   }
   if(x==0||x==WIDTH-1)for(int y=1;y<=10;y++)if(z%6==0)p.put(base.add(x,y,z),Blocks.POLISHED_ANDESITE.getDefaultState());
   int roof=10+(x>=10&&x%10>=5?1:0);p.put(base.add(x,roof,z),Blocks.DEEPSLATE_TILES.getDefaultState());
   if(z>0&&z<DEPTH-1&&z%8==0&&x>8){p.put(base.add(x,9,z),net.minecraft.registry.Registries.BLOCK.get(net.minecraft.util.Identifier.ofVanilla("waxed_copper_chain")).getDefaultState());if(x%8==3)p.put(base.add(x,8,z),Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING,true));}
  }
  // Twelve-block-wide loading bay; no two-block lintel across the top of a bus or aircraft.
  for(int x=14;x<=26;x++)for(int y=1;y<=6;y++)p.put(base.add(x,y,0),Blocks.AIR.getDefaultState());
  for(int z=0;z<DEPTH;z++)for(int y=1;y<=9;y++)p.put(base.add(8,y,z),Blocks.BRICKS.getDefaultState());
  for(int z:new int[]{5,22,35})for(int x=0;x<=8;x++)p.put(base.add(x,5,z),Blocks.SPRUCE_PLANKS.getDefaultState());
  for(int x=1;x<8;x++)for(int z=1;z<DEPTH-1;z++)p.put(base.add(x,5,z),Blocks.SPRUCE_PLANKS.getDefaultState());
  for(int step=0;step<5;step++)for(int x=5;x<=6;x++){
   var at=base.add(x,step+1,33+step);p.put(at,Blocks.STONE_BRICK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.SOUTH));
   for(int y=1;y<=3;y++)p.put(at.up(y),Blocks.AIR.getDefaultState());
  }
  for(int x=5;x<=6;x++)for(int z=33;z<=36;z++)p.put(base.add(x,5,z),Blocks.AIR.getDefaultState());
  for(int z:new int[]{5,20,37})for(int y=1;y<=3;y++)p.put(base.add(8,y,z),Blocks.AIR.getDefaultState());
  for(int y=1;y<=3;y++)for(int x=3;x<=4;x++)p.put(base.add(x,y,0),Blocks.AIR.getDefaultState());
  for(int z:new int[]{7,10,13}){p.put(base.add(3,1,z),Blocks.BARREL.getDefaultState());p.put(base.add(5,1,z),Blocks.BARREL.getDefaultState());}
  p.put(base.add(3,1,17),Blocks.SMITHING_TABLE.getDefaultState());p.put(base.add(5,1,17),Blocks.BLAST_FURNACE.getDefaultState());
  for(int z:new int[]{4,14,25,40}){p.put(base.add(4,4,z),Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING,true));p.put(base.add(4,9,z),Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING,true));}
  for(int x:new int[]{3,6})for(int z:new int[]{6,16,26}){p.put(base.add(x,6,z),Blocks.SPRUCE_SLAB.getDefaultState());p.put(base.add(x,6,z+1),Blocks.OAK_STAIRS.getDefaultState());}
  p.put(base.add(2,6,40),Blocks.BOOKSHELF.getDefaultState());p.put(base.add(3,6,40),Blocks.CARTOGRAPHY_TABLE.getDefaultState());
  // Assembly lane and tangible workstations around the staged vehicle, clear of its final silhouette.
  for(int z=8;z<=35;z++)for(int x:new int[]{13,27})p.put(base.add(x,1,z),Blocks.IRON_BARS.getDefaultState());
  for(int z:new int[]{12,20,28})for(int x:new int[]{13,27})p.put(base.add(x,1,z),Blocks.AIR.getDefaultState());
  p.put(base.add(20,1,22),MachineAssembly.BLOCK.getDefaultState());
  p.put(base.add(11,1,15),Blocks.SMITHING_TABLE.getDefaultState());p.put(base.add(29,1,15),Blocks.ANVIL.getDefaultState());
  p.put(base.add(11,1,28),Blocks.BARREL.getDefaultState());p.put(base.add(29,1,28),Blocks.BARREL.getDefaultState());
  for(int x=10;x<=30;x++){p.put(base.add(x,7,22),Blocks.YELLOW_TERRACOTTA.getDefaultState());if(x%4==0)p.put(base.add(x,6,22),Blocks.IRON_BARS.getDefaultState());}
  for(int y=10;y<18;y++)for(int x=2;x<=4;x++)for(int z=DEPTH-6;z<=DEPTH-4;z++)p.put(base.add(x,y,z),(y%4==0?Blocks.STONE_BRICKS:Blocks.BRICKS).getDefaultState());
  for(int x:new int[]{11,23,33})for(int z=4;z<DEPTH;z+=8){for(int y=7;y<=9;y++)p.put(base.add(x,y,z),net.minecraft.registry.Registries.BLOCK.get(net.minecraft.util.Identifier.ofVanilla("waxed_copper_chain")).getDefaultState());p.put(base.add(x,6,z),Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING,true));}
  // Different signs and facade bands identify the four plants without bare featureless walls.
  for(int x=10;x<=30;x++)p.put(base.add(x,8,0),(kind==0?Blocks.BLUE_TERRACOTTA:kind==1?Blocks.GREEN_TERRACOTTA:kind==2?Blocks.LIGHT_BLUE_TERRACOTTA:Blocks.RED_TERRACOTTA).getDefaultState());
  return p;
 }
 private IndustrialPlantPlans(){}
}
