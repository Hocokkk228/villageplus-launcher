package dev.halloween;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

final class SpasInteriors {
    private static final BlockPos DESK = new BlockPos(49, 15, 49);
    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

    static void navigation(net.minecraft.entity.passive.VillagerEntity villager) {
        villager.getNavigation().setMaxFollowRange(192);
        villager.getNavigation().setCanOpenDoors(true);
    }

    static String id(long village) { return "industrial_spas_interiors:" + village; }

    static BlockPos office(ServerWorld world, long village) {
        var job = VillageProjects.get(world).jobs.stream()
                .filter(p -> p.id.equals(id(village)) && p.done).findFirst().orElse(null);
        if (job == null) return null;
        // The desk is used as a stable reference even in an existing renovated shell.
        return job.plan.stream().filter(c -> c.after().isOf(Blocks.LECTERN)).findFirst()
                .map(c -> c.pos().east(2)).orElse(null);
    }

    static boolean advance(ServerWorld world, VillageMemory.Team team, BlockPos base) {
        var projects = VillageProjects.get(world);
        var existing = projects.jobs.stream().filter(p -> p.id.equals(id(team.village))).findFirst().orElse(null);
        if (existing != null) return existing.done;
        for (int cx = (base.getX()+10)>>4; cx <= (base.getX()+54)>>4; cx++) {
            for (int cz = (base.getZ()+24)>>4; cz <= (base.getZ()+70)>>4; cz++) {
                if (!VillageProjects.keepLoaded(world,new BlockPos(cx<<4,base.getY(),cz<<4))) return false;
            }
        }
        var interior = new SpasInteriors();
        interior.plan();
        var changes = new LinkedHashMap<BlockPos, BlockState>();
        for (var entry : interior.blocks.entrySet()) {
            var local = entry.getKey();
            var pos = base.add(local);
            if (SpasArchitecture.protectedWing(local.getX(),local.getY(),local.getZ()) || VillagePrisons.managed(world,pos)) continue;
            var old = world.getBlockState(pos);
            if (old.hasBlockEntity() || VillageConnections.matches(world,pos,entry.getValue(),old)) continue;
            var expected = projects.expected.get(pos);
            if (old.isAir() || old.isReplaceable() || expected != null && old.isOf(expected.getBlock()))
                changes.put(pos,entry.getValue());
        }
        if (changes.isEmpty()) return true;
        projects.add(world,id(team.village),team.village,"cathedral",changes);
        return false;
    }

    private void plan() {
        for (int y : new int[]{0,7,14,21}) for (int x=10; x<=54; x++) for (int z=24; z<=70; z++) {
            boolean atrium = x>=28 && x<=37 && z>=38 && z<=58;
            if (y>0 && atrium) continue;
            boolean border = x==10 || x==54 || z==24 || z==70 || x==27 || x==38 && z>=37 && z<=59;
            put(x,y,z,border?Blocks.POLISHED_GRANITE:(x+z)%11==0?Blocks.POLISHED_DIORITE:Blocks.SMOOTH_STONE);
        }
        for (int y : new int[]{7,14,21}) {
            for (int z=38;z<=58;z++) for (int x : new int[]{27,38})
                put(x,y+1,z,Blocks.IRON_BARS);
            for (int x=28;x<=37;x++) for (int z : new int[]{37,59}) put(x,y+1,z,Blocks.IRON_BARS);
        }
        for (int floor=0;floor<3;floor++) {
            boolean south = floor%2==0;
            int lane = south?17:21;
            Direction facing=south?Direction.SOUTH:Direction.NORTH;
            var stair=Blocks.POLISHED_ANDESITE_STAIRS.getDefaultState().with(StairsBlock.FACING,facing);
            for (int x=lane;x<lane+3;x++) for (int i=0;i<7;i++) {
                int y=floor*7+1+i,z=south?31+i:37-i;
                blocks.put(new BlockPos(x,y,z),stair);
                for (int h=1;h<=3;h++) put(x,y+h,z,Blocks.AIR);
                for (int h=1;h<=i;h++) put(x,y-h,z,Blocks.STONE_BRICKS);
            }
        }
        for (int y : new int[]{0,7,14,21}) {
            for (int x : new int[]{16,20,24}) for (int z : new int[]{30,34,38})
                put(x,y,z,Blocks.SEA_LANTERN);
            for (int z : new int[]{33,46,56,64}) for (int x : new int[]{25,41}) {
                blocks.put(new BlockPos(x,y+6,z),Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING,true));
            }
            for (int z=26;z<=69;z++) if (y==0||z<37||z>59) for(int x=31;x<=34;x++) put(x,y+1,z,Blocks.RED_CARPET);
            for (int x : new int[]{12,52}) for (int z : new int[]{29,43,55,66}) {
                put(x,y+1,z,Blocks.GRANITE_WALL);
                put(x,y+2,z,Blocks.LANTERN);
            }
        }
        for (int z=40;z<=57;z+=3) for (int x : new int[]{22,23,24,41,42,43})
            blocks.put(new BlockPos(x,1,z),Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING,Direction.NORTH));
        for (int x=40;x<=53;x++) for (int z=43;z<=55;z++) {
            if (x!=40&&x!=53&&z!=43&&z!=55) {
                if (x>=44&&x<=51&&z>=46&&z<=53) put(x,15,z,Blocks.RED_CARPET);
                continue;
            }
            for (int y=15;y<=19;y++) {
                boolean door=x==40&&z==49&&y<=17;
                boolean glass=x==53&&z%3==0&&(y==17||y==18);
                put(x,y,z,door?Blocks.AIR:glass?Blocks.BROWN_STAINED_GLASS:y==15||y==19?Blocks.DARK_OAK_PLANKS:Blocks.SMOOTH_SANDSTONE);
            }
        }
        for (int x=47;x<=50;x++) put(x,15,49,Blocks.DARK_OAK_PLANKS);
        blocks.put(DESK,Blocks.LECTERN.getDefaultState().with(net.minecraft.block.LecternBlock.FACING,Direction.EAST));
        blocks.put(DESK.east(3),Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING,Direction.WEST));
        for (int x=42;x<=50;x++) for (int y=15;y<=17;y++) put(x,y,54,Blocks.BOOKSHELF);
        for (int x : new int[]{43,50}) for (int z : new int[]{46,52})
            blocks.put(new BlockPos(x,20,z),Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING,true));
        for (int x=41;x<=46;x++) put(x,15,47,Blocks.DARK_OAK_SLAB);
        for (int x : new int[]{42,45}) for (int z : new int[]{46,48})
            blocks.put(new BlockPos(x,15,z),Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING,z==46?Direction.SOUTH:Direction.NORTH));
    }

    private void put(int x,int y,int z,Block block) {
        blocks.put(new BlockPos(x,y,z),block.getDefaultState());
    }
}
