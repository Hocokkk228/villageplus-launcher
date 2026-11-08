package dev.halloween;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.*;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** A permanent court site. The old square marker still owns the case and the escort. */
final class VillageCourthouse {
    static Block BOARD;
    private static final int WIDTH = 23, DEPTH = 19;
    private static final String SEARCH = "courthouse_search", POSITION = "halloween_indoor_court_";
    private static final BlockPos CAGE = new BlockPos(11, 1, 10);

    static void register() {
        var id = Identifier.of("halloween", "courthouse_board");
        var key = RegistryKey.of(RegistryKeys.BLOCK, id);
        BOARD = Registry.register(Registries.BLOCK, key, new Block(AbstractBlock.Settings.create().registryKey(key)
                .strength(1.5f).nonOpaque().noCollision().sounds(net.minecraft.sound.BlockSoundGroup.METAL)));
        Registry.register(Registries.ITEM, id, new BlockItem(BOARD,
                new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, id)).useBlockPrefixedTranslationKey()));
    }

    static String id(long village) { return "industrial_courthouse:" + village; }

    static void advance(ServerWorld world, VillageMemory.Team team, VillageEraData.Settlement settlement) {
        var projects = VillageProjects.get(world);
        if (projects.exists(id(team.village))) return;
        int cursor = settlement.xp.getOrDefault(SEARCH, 0);
        int radius = 34 + Math.floorMod(cursor, 18) * 8;
        double angle = cursor * 2.399963229728653;
        int x = team.anchor.getX() + (int)(Math.cos(angle)*radius) - WIDTH/2;
        int z = team.anchor.getZ() + (int)(Math.sin(angle)*radius) - DEPTH/2;
        var memory = world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        boolean occupied = memory.houses.stream().filter(VillageBuildingSites::reserves).anyMatch(h -> overlap(h.min,h.max,x,z))
                || projects.jobs.stream().filter(p -> !p.done && VillageBuildingSites.building(p.kind))
                .anyMatch(p -> overlap(p.min,p.max,x,z));
        if (!occupied) {
            boolean ready = true;
            for (int cx=x>>4; cx<=(x+WIDTH-1)>>4; cx++) for (int cz=z>>4; cz<=(z+DEPTH-1)>>4; cz++)
                if (!VillageProjects.keepLoaded(world,new BlockPos(cx<<4,64,cz<<4))) ready=false;
            if (!ready) return;
        }
        settlement.xp.put(SEARCH,cursor+1);
        VillageEraData.get(world).markDirty();
        if (occupied) return;
        int y = VillageEraPlans.levelSite(world,x,z,WIDTH,DEPTH,4);
        if (y == Integer.MIN_VALUE || y+12>world.getTopYInclusive()) return;
        var base = new BlockPos(x,y-1,z);
        var blocks = plan(base);
        for (var pos : blocks.keySet()) {
            var old = world.getBlockState(pos);
            if (!world.getWorldBorder().contains(pos) || old.hasBlockEntity() || !old.getFluidState().isEmpty()
                    || !old.isAir() && !old.isReplaceable() && !VillageProjects.terrain(old)
                    && !old.isIn(net.minecraft.registry.tag.BlockTags.LOGS) && !old.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) return;
        }
        // Place supports only where the surveyed ground falls away.
        for (int dx=0; dx<WIDTH; dx++) for (int dz=0; dz<DEPTH; dz++) for (int depth=1; depth<=4; depth++) {
            var support = base.add(dx,-depth,dz);
            var old = world.getBlockState(support);
            if (old.isSolidBlock(world,support)) break;
            if (old.hasBlockEntity() || !old.getFluidState().isEmpty() || !old.isAir() && !old.isReplaceable()) return;
            blocks.put(support,Blocks.STONE_BRICKS.getDefaultState());
        }
        projects.add(world,id(team.village),team.village,"courthouse",blocks);
        if (projects.exists(id(team.village))) {
            settlement.xp.put("courthouse_x",x);
            settlement.xp.put("courthouse_y",base.getY());
            settlement.xp.put("courthouse_z",z);
            VillageEraData.get(world).markDirty();
        }
    }

    private static boolean overlap(BlockPos min, BlockPos max,int x,int z) {
        return min.getX()<=x+WIDTH && max.getX()>=x-1 && min.getZ()<=z+DEPTH && max.getZ()>=z-1;
    }

    static BlockPos hall(ServerWorld world, MarkerEntity square) {
        for (var tag : square.getCommandTags()) if (tag.startsWith(POSITION)) {
            try { return BlockPos.fromLong(Long.parseLong(tag.substring(POSITION.length()))); }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    static void selectHall(ServerWorld world, MarkerEntity square) {
        if (hall(world,square)!=null || square.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_active_court_"))) return;
        var settlement=VillageEras.settlement(world,square.getBlockPos());
        if (!VillageIndustrial.civic(settlement) || !settlement.xp.containsKey("courthouse_x")) return;
        if (VillageProjects.get(world).jobs.stream().noneMatch(p->p.id.equals(id(settlement.village))&&p.done)) return;
        var base = new BlockPos(settlement.xp.get("courthouse_x"),settlement.xp.get("courthouse_y"),settlement.xp.get("courthouse_z"));
        if (!world.isChunkLoaded(base.add(CAGE)) || !VillageTrials.cageReady(world,base.add(CAGE))) return;
        VillageTrials.removeCage(world,VillageTrials.cagePosition(world,square));
        square.addCommandTag(POSITION+base.asLong());
    }

    static boolean required(ServerWorld world, MarkerEntity square) {
        return VillageIndustrial.civic(VillageEras.settlement(world,square.getBlockPos()));
    }

    static boolean judgePresent(ServerWorld world, BlockPos hall) {
        var post=judge(hall);
        return !world.getEntitiesByClass(net.minecraft.entity.passive.VillagerEntity.class,new Box(post).expand(1.25),
                v->v.isAlive()&&v.getVillagerData().profession().matchesKey(RoyalProfession.DEPUTY_KEY)
                        && v.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofBottomCenter(post))<2.25).isEmpty();
    }

    static BlockPos cage(BlockPos base) { return base.add(CAGE); }
    static BlockPos judge(BlockPos base) { return base.add(11,2,16); }
    static BlockPos witness(BlockPos base,int slot) { return base.add(slot==0?5:17,1,12); }

    static boolean permanent(ServerWorld world, BlockPos cage) {
        return world.getEntitiesByClass(MarkerEntity.class,new Box(cage).expand(384,96,384),
                m->{var base=hall(world,m);return base!=null && cage.equals(cage(base));}).size()>0;
    }

    static Map<BlockPos,BlockState> plan(BlockPos base) {
        var plan = new LinkedHashMap<BlockPos,BlockState>();
        for (int x=0;x<WIDTH;x++) for (int z=0;z<DEPTH;z++) for (int y=0;y<=10;y++) {
            boolean shell = x==0||x==WIDTH-1||z==2||z==DEPTH-1;
            int bay=(x==0||x==WIDTH-1)?z:x;
            boolean glazing = y>=3&&y<=6&&bay%5>=2&&bay%5<=3;
            Block block = y==0 ? (x>=9&&x<=13&&z>=3&&z<=9?Blocks.POLISHED_GRANITE:Blocks.SMOOTH_STONE)
                    : z<2?Blocks.AIR:y==9?Blocks.DEEPSLATE_TILES:y==10?(shell?Blocks.POLISHED_ANDESITE:Blocks.AIR)
                    : !shell?Blocks.AIR:glazing?Blocks.GLASS:y==1||y==7?Blocks.POLISHED_ANDESITE
                    : Math.floorMod(x*13+z*11+y*7,17)==0?Blocks.TUFF_BRICKS:Blocks.BRICKS;
            plan.put(base.add(x,y,z),block.getDefaultState());
        }
        for (int x : new int[]{3,7,15,19}) for (int y=1;y<=6;y++)
            plan.put(base.add(x,y,1),(y==1||y==6?Blocks.POLISHED_ANDESITE:Blocks.SMOOTH_SANDSTONE).getDefaultState());
        for (int x=2;x<=20;x++) for (int z=0;z<=2;z++) plan.put(base.add(x,7,z),Blocks.POLISHED_ANDESITE.getDefaultState());
        for (int x=9;x<=13;x++) for (int y=8;y<=10-Math.abs(x-11);y++)
            plan.put(base.add(x,y,1),Blocks.DEEPSLATE_BRICKS.getDefaultState());
        IndustrialArchitecture.door(plan,base.add(10,1,2),Direction.NORTH);
        IndustrialArchitecture.door(plan,base.add(11,1,2),Direction.NORTH);
        IndustrialArchitecture.door(plan,base.add(12,1,2),Direction.NORTH);
        plan.put(base.add(11,6,1),BOARD.getDefaultState());
        for(int x=7;x<=15;x++)for(int z=14;z<=17;z++)plan.put(base.add(x,1,z),Blocks.POLISHED_ANDESITE.getDefaultState());
        for(int x=9;x<=13;x++)plan.put(base.add(x,2,14),Blocks.DARK_OAK_PLANKS.getDefaultState());
        for(int x:new int[]{7,8,14,15})
            plan.put(base.add(x,1,13),Blocks.POLISHED_ANDESITE_STAIRS.getDefaultState().with(StairsBlock.FACING,Direction.SOUTH));
        plan.put(base.add(11,2,17),Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING,Direction.NORTH));
        for(int x:new int[]{5,17}) {
            plan.put(base.add(x,1,11),Blocks.SPRUCE_FENCE.getDefaultState());
            plan.put(base.add(x,1,13),Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING,Direction.NORTH));
        }
        for(int x:new int[]{5,17})for(int z:new int[]{5,10,15})
            plan.put(base.add(x,8,z),Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING,true));
        var cage=base.add(CAGE);
        for(int y=0;y<=3;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            if(y<3&&x==0&&z==0||y<2&&x==0&&z==-1)continue;
            plan.put(cage.add(x,y,z),Blocks.IRON_BARS.getDefaultState());
        }
        return plan;
    }

    private VillageCourthouse() {}
}
