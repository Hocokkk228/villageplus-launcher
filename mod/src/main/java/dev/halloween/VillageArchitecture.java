package dev.halloween;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** One source of furnished house geometry for new workshops and upgrades of old boxes. */
final class VillageArchitecture {
    static final List<String> ROLES=VillageEras.PROFESSIONS;
    static final String PREFIX="architecture_v1:";
    static boolean periodHome(VillageMemory.House h){return h.recordKey.startsWith("gothic_rebuild:")||h.recordKey.startsWith("gothic_expansion:")||h.recordKey.startsWith("gothic_infill_v2:")||h.recordKey.startsWith("steam_residence_");}
    static boolean eligible(VillageMemory.House h){return !periodHome(h)&&!h.recordKey.startsWith(PREFIX)&&(h.kind.equals("house")||h.recordKey.contains("/houses/")||h.recordKey.startsWith("era_workshop:"))&&!h.recordKey.contains("fighting_arena")&&(h.recordKey.startsWith("era_workshop:")||h.blueprint.stream().anyMatch(r->!VillageEraPlans.stone(r.state()).equals(r.state())));}
    static String role(String key){for(String role:ROLES)if(key.contains(":"+role+":")||key.contains("profession_"+role+"_"))return role;return "builder";}
    static Map<BlockPos,BlockState> plan(ServerWorld w,BlockPos base,String role,int variant){
        var t=VillageEraPlans.template(w,"profession_"+role+"_"+Math.floorMod(variant,3));if(t==null)return Map.of();Map<BlockPos,BlockState> plan=new LinkedHashMap<>();
        for(var r:t.blocks()){
            var pos=base.add(r.pos());var state=r.state();
            if(w.getBlockState(pos).hasBlockEntity())state=w.getBlockState(pos);plan.put(pos,state);
        }
        // A proper two-block door and level approach, not a door balanced over a void.
        int center=t.size().getX()/2;
        for(int z=-2;z<=0;z++){plan.put(base.add(center,0,z),Blocks.STONE_BRICKS.getDefaultState());for(int y=1;y<=3;y++)plan.put(base.add(center,y,z),Blocks.AIR.getDefaultState());}
        return plan;
    }
    static boolean conversionsPending(ServerWorld w,long village){return VillageProjects.get(w).jobs.stream().anyMatch(p->p.village==village&&p.kind.equals("conversion")&&!p.done);}
    static void transition(ServerWorld w,VillageMemory.Team t,VillageEraData.Settlement data){
        var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);var projects=VillageProjects.get(w);
        var houses=memory.houses.stream().filter(h->eligible(h)&&VillageEras.team(w,h.min)==t).sorted(Comparator.<VillageMemory.House>comparingDouble(h->VillageEras.horizontal(h.min,t.anchor)).reversed()).toList();
        var unfinished=projects.jobs.stream().filter(p->p.village==t.village&&!p.done&&p.id.startsWith("era_workshop:")).toList();
        if(data.xp.getOrDefault("architecture_v1",0)==0){
            data.xp.put("architecture_v1",1);data.conversionCount=houses.size()+unfinished.size();
            if(data.conversionCount>0)data.transitionAt=w.getTime();VillageEraData.get(w).markDirty();
            for(var old:List.copyOf(projects.jobs))if(old.village==t.village&&old.kind.equals("conversion")&&!old.id.startsWith(PREFIX))projects.retire(old);
        }
        int planned=0;
        for(var old:unfinished){
            String id=PREFIX+old.id;if(projects.exists(id))continue;
            BlockPos door=old.plan.stream().filter(c->c.after().getBlock() instanceof DoorBlock&&c.after().get(DoorBlock.HALF)==DoubleBlockHalf.LOWER).map(VillageCivilWorks.Change::pos).findFirst().orElse(old.min.up());
            BlockPos base=new BlockPos(old.min.getX(),door.getY()-1,old.min.getZ());var plan=plan(w,base,role(old.id),0);
            projects.add(w,id,t.village,"conversion",plan);if(projects.exists(id))projects.retire(old);if(++planned>=2)return;
        }
        for(var h:houses){
            String id=PREFIX+h.min.asLong()+":"+h.recordKey.hashCode();if(projects.exists(id))continue;Map<BlockPos,BlockState> changes=new LinkedHashMap<>();
            for(var r:h.blueprint){var desired=VillageEraPlans.stone(r.state());if(!desired.equals(r.state())||!w.getBlockState(r.pos()).isOf(desired.getBlock()))changes.put(r.pos(),desired);}
            if(h.recordKey.startsWith("era_workshop:")){
                BlockPos door=h.blueprint.stream().filter(r->r.state().getBlock() instanceof DoorBlock&&r.state().get(DoorBlock.HALF)==DoubleBlockHalf.LOWER).map(VillageMemory.BlockRecord::pos).findFirst().orElse(h.min.up());
                changes.putAll(plan(w,new BlockPos(h.min.getX(),door.getY()-1,h.min.getZ()),role(h.recordKey),0));
            }
            // Preserve existing doors, both storeys, paired blocks and interior inventories.
            for(var r:h.blueprint)if(r.state().getBlock() instanceof DoorBlock&&r.state().get(DoorBlock.HALF)==DoubleBlockHalf.LOWER){
                changes.put(r.pos(),r.state());var upper=h.blueprint.stream().filter(b->b.pos().equals(r.pos().up())).findFirst();upper.ifPresent(b->changes.put(b.pos(),b.state()));
                for(var dir:List.of(r.state().get(DoorBlock.FACING),r.state().get(DoorBlock.FACING).getOpposite())){
                    BlockPos q=r.pos().offset(dir);if(w.getBlockState(q).isAir()||w.getBlockState(q).isReplaceable()){if(w.getBlockState(q.down()).isAir())changes.put(q.down(),Blocks.STONE_BRICKS.getDefaultState());}
                }
            }
            projects.add(w,id,t.village,"conversion",changes);if(++planned>=2)return;
        }
    }
    static void ensureHomes(ServerWorld w,VillageMemory.Team team){if(conversionsPending(w,team.village))return;for(String role:ROLES){long count=VillageProjects.get(w).jobs.stream().filter(p->p.village==team.village&&p.kind.equals("workshop_"+role)).count();if(count<3){Block station=role.equals("builder")?net.minecraft.registry.Registries.BLOCK.get(net.minecraft.util.Identifier.of("halloween","builder_workbench")):VillageEconomy.workstation(role);VillageWorkplaces.plan(w,team,role,station);return;}}}
    private VillageArchitecture(){}
}
