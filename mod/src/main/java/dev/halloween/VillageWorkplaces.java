package dev.halloween;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;

/** Profession-specific furnished homes with three silhouettes, never flat-roof boxes. */
final class VillageWorkplaces {
    static void plan(ServerWorld w,VillageMemory.Team team,String role,Block workstation){
        var projects=VillageProjects.get(w);if(VillageArchitecture.conversionsPending(w,team.village)||VillageArchitectureMigration.running(VillageEraData.get(w).settlement(team)))return;
        String kind="workshop_"+role;
        if(projects.jobs.stream().anyMatch(p->p.village==team.village&&p.kind.equals(kind)&&!p.done))return;
        if(projects.jobs.stream().filter(p->p.village==team.village&&p.kind.startsWith("workshop_")&&!p.done).count()>=2)return;
        int serial=(int)projects.jobs.stream().filter(p->p.village==team.village&&p.kind.equals(kind)).count();if(serial>=3)return;
        var template=VillageEraPlans.template(w,"profession_"+role+"_"+serial);if(template==null)return;
        int width=template.size().getX(),depth=template.size().getZ(),height=template.size().getY();var memory=w.getPersistentStateManager().getOrCreate(VillageMemory.TYPE);
        for(int attempt=0;attempt<24;attempt++){
            double angle=(attempt+serial*17+role.hashCode()%31)*2.399963229728653;int radius=45+(attempt%8)*18;
            int x=team.anchor.getX()+(int)(Math.cos(angle)*radius),z=team.anchor.getZ()+(int)(Math.sin(angle)*radius);
            if(memory.houses.stream().anyMatch(h->h.min.getX()-4<=x+width-1&&h.max.getX()+4>=x&&h.min.getZ()-4<=z+depth-1&&h.max.getZ()+4>=z))continue;
            if(projects.jobs.stream().anyMatch(p->p.min.getX()-4<=x+width-1&&p.max.getX()+4>=x&&p.min.getZ()-4<=z+depth-1&&p.max.getZ()+4>=z))continue;
            boolean loaded=true;for(int cx=x>>4;cx<=(x+width-1)>>4;cx++)for(int cz=(z-2)>>4;cz<=(z+depth-1)>>4;cz++)if(!VillageProjects.keepLoaded(w,new BlockPos(cx*16,64,cz*16)))loaded=false;if(!loaded)return;
            int top=VillageEraPlans.levelSite(w,x,z,width,depth,3);if(top==Integer.MIN_VALUE)continue;int y=top-1;BlockPos base=new BlockPos(x,y,z);boolean safe=true;Map<BlockPos,BlockState> plan=new LinkedHashMap<>();
            for(int dx=0;dx<width&&safe;dx++)for(int dz=0;dz<depth&&safe;dz++){
                var p=base.add(dx,0,dz);if(Math.abs(w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x+dx,z+dz)-(y+1))>3){safe=false;break;}
                for(int h=1;h<height;h++){var q=p.up(h);var old=w.getBlockState(q);if(!old.isAir()&&!old.isReplaceable()&&!VillageCivilWorks.natural(old)&&!old.isIn(BlockTags.LOGS)&&!old.isIn(BlockTags.LEAVES)){safe=false;break;}}
                for(int down=1;down<=8;down++){var q=p.down(down);if(w.getBlockState(q).isSolidBlock(w,q))break;plan.put(q,Blocks.STONE_BRICKS.getDefaultState());}
            }
            if(!safe)continue;plan.putAll(VillageArchitecture.plan(w,base,role,serial));String id="era_workshop:"+team.village+":"+role+":"+serial;projects.add(w,id,team.village,kind,plan);return;
        }
    }
    private VillageWorkplaces(){}
}
