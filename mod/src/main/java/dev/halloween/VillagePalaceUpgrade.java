package dev.halloween;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Survey only changed, village-owned blocks in small batches. Existing worlds get builder jobs too. */
final class VillagePalaceUpgrade {
    private static final class Survey {
        final BlockPos base;
        final VillageEraPlans.Template template;
        final Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
        int cursor;
        Survey(BlockPos base,VillageEraPlans.Template template){this.base=base;this.template=template;}
    }
    private static final Map<ServerWorld,Map<Long,Survey>> SURVEYS=new WeakHashMap<>();
    static String id(long village){return "cathedral_palace_v5:"+village;}
    static void tick(ServerWorld world,VillageMemory.Team team,VillageEraData.Settlement settlement){
        if(!VillageSteam.infrastructureStarted(settlement))return;
        var projects=VillageProjects.get(world);
        if(projects.exists(VillageSpas.id(team.village)))return;
        if(projects.jobs.stream().noneMatch(p->p.id.equals(VillageCathedral.id(team.village))&&p.done))return;
        for(var old:projects.jobs)if(old.village==team.village&&!old.done
                &&(old.id.startsWith("cathedral_palace_v3:")||old.id.startsWith("cathedral_palace_v4:")||old.id.startsWith("cathedral_finish_v2:")))projects.retire(old);
        var palace=projects.jobs.stream().filter(p->p.id.equals(id(team.village))).findFirst().orElse(null);
        var memory=world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        var castle=memory.houses.stream().filter(h->h.recordKey.contains("/royal_castle:")&&VillageEras.team(world,h.min)==team).findFirst().orElse(null);
        if(castle==null)return;
        var base=VillageRelocations.bounds(castle)[0];
        if(settlement.xp.getOrDefault("palace_v5_checked",0)>0){moveSquare(world,team,settlement,base);return;}
        if(palace!=null){if(palace.done){moveRoyalBed(world,team,base.add(48,8,64));moveSquare(world,team,settlement,base);}return;}
        var surveys=SURVEYS.computeIfAbsent(world,k->new HashMap<>());
        var survey=surveys.get(team.village);
        if(survey==null){var template=VillageEraPlans.template(world,"cologne_cathedral");if(template==null)return;survey=new Survey(base,template);surveys.put(team.village,survey);}
        long deadline=System.nanoTime()+1_500_000;
        for(int budget=0;budget<2048&&survey.cursor<survey.template.blocks().size()&&System.nanoTime()<deadline;budget++){
            var r=survey.template.blocks().get(survey.cursor);var p=survey.base.add(r.pos());
            if(!VillageProjects.keepLoaded(world,p))return;
            survey.cursor++;
            if(VillagePrisons.managed(world,p))continue;
            var actual=world.getBlockState(p);
            if(VillageConnections.matches(world,p,r.state(),actual)||actual.hasBlockEntity())continue;
            var owned=projects.expected.get(p);
            if(actual.isAir()||actual.isReplaceable()||VillageProjects.terrain(actual)||owned!=null&&owned.isOf(actual.getBlock()))
                survey.blocks.put(p,r.state());
        }
        if(survey.cursor==survey.template.blocks().size()){
            if(!survey.blocks.isEmpty())projects.add(world,id(team.village),team.village,"cathedral",survey.blocks);
            else {moveRoyalBed(world,team,base.add(48,8,64));moveSquare(world,team,settlement,base);settlement.xp.put("palace_v5_checked",1);VillageEraData.get(world).markDirty();}
            if(projects.exists(id(team.village))||survey.blocks.isEmpty())surveys.remove(team.village);
        }
    }
    static boolean ready(ServerWorld world,VillageMemory.Team team,VillageEraData.Settlement settlement){
        var projects=VillageProjects.get(world);
        if(projects.exists(VillageSpas.id(team.village))||!projects.exists(VillageCathedral.id(team.village)))return true;
        tick(world,team,settlement);
        return settlement.xp.getOrDefault("palace_v5_checked",0)>0
                ||projects.jobs.stream().anyMatch(p->p.id.equals(id(team.village))&&p.done);
    }

    private static void moveSquare(ServerWorld world,VillageMemory.Team team,VillageEraData.Settlement settlement,BlockPos base){
        if(settlement.xp.getOrDefault("palace_square_v3",0)>0)return;
        var squares=world.getEntitiesByClass(MarkerEntity.class,new Box(team.anchor).expand(160),m->m.getCommandTags().contains("halloween_square")&&VillageEras.team(world,m.getBlockPos())==team);
        if(squares.stream().anyMatch(m->m.getCommandTags().stream().anyMatch(t->t.startsWith("halloween_active_court_"))))return;
        var bellPosition=base.add(24,1,-8);var squarePosition=base.add(32,1,-4);
        var projects=VillageProjects.get(world);String id="palace_square_bell_v3:"+team.village;
        if(!projects.exists(id)){projects.add(world,id,team.village,"factory_supplies",Map.of(bellPosition,Blocks.BELL.getDefaultState()));return;}
        if(projects.jobs.stream().noneMatch(p->p.id.equals(id)&&p.done))return;
        for(var square:squares){VillageTrials.removeCage(world,VillageTrials.cagePosition(world,square));square.setPosition(Vec3d.ofBottomCenter(squarePosition));}
        for(var bell:world.getEntitiesByClass(MarkerEntity.class,new Box(team.anchor).expand(160),m->m.getCommandTags().contains("halloween_bell")&&VillageEras.team(world,m.getBlockPos())==team))bell.setPosition(Vec3d.ofBottomCenter(bellPosition));
        settlement.xp.put("palace_square_v3",1);VillageEraData.get(world).markDirty();
    }
    private static void moveRoyalBed(ServerWorld world,VillageMemory.Team team,BlockPos head){
        if(!(world.getBlockState(head).getBlock() instanceof BedBlock))return;
        var markers=world.getEntitiesByClass(MarkerEntity.class,new Box(team.anchor).expand(192),m->m.getCommandTags().contains("halloween_royal_bed")&&VillageEras.team(world,m.getBlockPos())==team);
        var removed=new LinkedHashMap<BlockPos,BlockState>();
        for(var marker:markers){var old=marker.getBlockPos();if(old.equals(head)||!world.isChunkLoaded(old))continue;
            var block=world.getBlockState(old);if(!(block.getBlock() instanceof BedBlock))continue;
            var facing=block.get(BedBlock.FACING);var other=old.offset(block.get(BedBlock.PART)==BedPart.HEAD?facing.getOpposite():facing);
            for(var p:List.of(old,other))if(world.getBlockState(p).getBlock() instanceof BedBlock){world.removeBlock(p,false);removed.put(p,Blocks.AIR.getDefaultState());}
            floatingBedPlatform(world,team,old,removed);
        }
        if(!removed.isEmpty())VillageProjects.recordInfrastructure(world,"royal_bed_moved_v3:"+team.village,removed);
        VillageThirdRepairs.royalBed(world,team,head);
    }
    private static void floatingBedPlatform(ServerWorld w,VillageMemory.Team team,BlockPos old,Map<BlockPos,BlockState> removed){
        var state=VillageProjects.get(w);
        var floor=old.down();
        var material=w.getBlockState(floor);
        if(!material.isIn(net.minecraft.registry.tag.BlockTags.PLANKS))return;
        var queue=new ArrayDeque<BlockPos>();var platform=new HashSet<BlockPos>();
        queue.add(floor);platform.add(floor);
        while(!queue.isEmpty()){
            var p=queue.remove();
            if(platform.size()>30||!w.isChunkLoaded(p)||!w.isAir(p.down())||!w.isAir(p.up()))return;
            var expected=state.expected.get(p);
            if(expected==null||!expected.isOf(material.getBlock()))return;
            for(Direction d:Direction.Type.HORIZONTAL){
                var next=p.offset(d);
                if(!w.isChunkLoaded(next))return;
                var block=w.getBlockState(next);
                if(block.isOf(material.getBlock())){if(platform.add(next))queue.add(next);}
                else if(block.isSolidBlock(w,next))return;
            }
        }
        for(var p:platform){w.removeBlock(p,false);removed.put(p,Blocks.AIR.getDefaultState());}
    }

    private VillagePalaceUpgrade(){}
}
