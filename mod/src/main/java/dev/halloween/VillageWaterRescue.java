package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Swimming owns movement until a reachable, supported landing is reached. It never edits terrain. */
final class VillageWaterRescue {
    private static final class Route {
        final List<Vec3d> points;final long until;int index;
        Route(List<Vec3d> points,long until){this.points=points;this.until=until;}
    }
    private static final Map<VillagerEntity,Route> ROUTES=new WeakHashMap<>();
    private static final class SearchBudget {long tick=Long.MIN_VALUE;boolean used;}
    private static final Map<ServerWorld,SearchBudget> SEARCHES=new WeakHashMap<>();
    private static boolean takeSearch(ServerWorld w){
        var budget=SEARCHES.computeIfAbsent(w,k->new SearchBudget());
        if(budget.tick!=w.getTime()){budget.tick=w.getTime();budget.used=false;}
        if(budget.used)return false;budget.used=true;return true;
    }
    private static boolean loaded(ServerWorld w,BlockPos p){return w.isChunkLoaded(new ChunkPos(p).toLong());}
    private static boolean fits(ServerWorld w,VillagerEntity v,Vec3d p){return w.isSpaceEmpty(v,v.getBoundingBox().offset(p.subtract(v.getEntityPos())));}
    private static Vec3d landing(ServerWorld w,VillagerEntity v,BlockPos p){
        if(!loaded(w,p)||!w.getFluidState(p).isEmpty()||!w.getFluidState(p.down()).isEmpty())return null;
        var shape=w.getBlockState(p).getCollisionShape(w,p);var below=w.getBlockState(p.down()).getCollisionShape(w,p.down());
        if(shape.isEmpty()&&below.isEmpty())return null;
        double height=shape.isEmpty()?-1+below.getMax(Direction.Axis.Y):shape.getMax(Direction.Axis.Y);
        if(height>1)return null;var goal=Vec3d.ofBottomCenter(p).add(0,height,0);
        return fits(w,v,goal)?goal:null;
    }
    /** Breadth-first water route: a near bank behind a wall must not attract swimmers through that wall. */
    private static Route findRoute(ServerWorld w,VillagerEntity v){
        BlockPos start=v.getBlockPos();if(!w.getFluidState(start).isIn(FluidTags.WATER)&&w.getFluidState(start.down()).isIn(FluidTags.WATER))start=start.down();
        var queue=new ArrayDeque<BlockPos>();var previous=new HashMap<BlockPos,BlockPos>();queue.add(start);previous.put(start,start);
        while(!queue.isEmpty()&&previous.size()<4096){
            var node=queue.removeFirst();
            for(var direction:Direction.Type.HORIZONTAL)for(int rise=0;rise<=1;rise++){
                var goal=landing(w,v,node.offset(direction).up(rise));if(goal==null)continue;
                var points=new ArrayList<Vec3d>();BlockPos cursor=node;
                while(!cursor.equals(start)){points.add(Vec3d.ofBottomCenter(cursor).add(0,.1,0));cursor=previous.get(cursor);}
                Collections.reverse(points);points.add(goal);return new Route(points,w.getTime()+160);
            }
            for(var direction:List.of(Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.DOWN)){
                var next=node.offset(direction);
                if(previous.containsKey(next)||Math.abs(next.getX()-start.getX())>24||Math.abs(next.getZ()-start.getZ())>24||next.getY()<start.getY()-1||next.getY()>start.getY()+8||!loaded(w,next)||!w.getFluidState(next).isIn(FluidTags.WATER)||!fits(w,v,Vec3d.ofBottomCenter(next).add(0,.1,0)))continue;
                previous.put(next,node);queue.addLast(next);
            }
        }
        return new Route(List.of(),w.getTime()+40);
    }
    static void swimToShore(ServerWorld w,VillagerEntity v){
        if(!v.isTouchingWater()||v.hasVehicle())return;
        VillageTasks.hold(v);v.getNavigation().stop();v.getBrain().forget(MemoryModuleType.WALK_TARGET);v.setSneaking(false);
        var route=ROUTES.get(v);
        if((route==null||route.until<=w.getTime())&&takeSearch(w)){route=findRoute(w,v);ROUTES.put(v,route);}
        if(route==null)route=new Route(List.of(),w.getTime());
        while(route.index<route.points.size()-1){
            var point=route.points.get(route.index);double dx=point.x-v.getX(),dz=point.z-v.getZ();
            if(dx*dx+dz*dz>.35||(v.isSubmergedInWater()&&Math.abs(point.y-v.getY())>.7))break;
            route.index++;
        }
        var velocity=v.getVelocity();double vx=0,vz=0,vertical=v.isSubmergedInWater()?.22:Math.max(.04,velocity.y);
        if(route.index<route.points.size()){
            var goal=route.points.get(route.index);double dx=goal.x-v.getX(),dz=goal.z-v.getZ(),distance=Math.sqrt(dx*dx+dz*dz);
            if(distance>.08){vx=dx/distance*.16;vz=dz/distance*.16;}
            boolean last=route.index==route.points.size()-1;
            if(!last)vertical=v.isSubmergedInWater()?Math.clamp((goal.y-v.getY())*.35,-.15,.22):Math.max(.04,velocity.y);
            else if(goal.y>v.getY()+.15)vertical=distance<1.6?.42:.22;
            if(v.horizontalCollision&&goal.y>=v.getY()-.1&&fits(w,v,v.getEntityPos().add(vx,1,vz)))vertical=.42;
            v.getLookControl().lookAt(goal.x,goal.y+1,goal.z);
        }
        v.setVelocity(vx,Math.min(.42,vertical),vz);v.velocityDirty=true;
    }
    static void register(){ServerTickEvents.END_WORLD_TICK.register(w->{
        if(!w.getRegistryKey().equals(World.OVERWORLD))return;
        for(var entity:w.iterateEntities())if(entity instanceof VillagerEntity v&&v.isAlive()&&!v.isAiDisabled()&&v.isTouchingWater())swimToShore(w,v);
    });}
    static String describe(VillagerEntity v){var r=ROUTES.get(v);return r==null?"none":r.index+"/"+r.points.size()+" next="+(r.index<r.points.size()?r.points.get(r.index):"none")+" end="+(r.points.isEmpty()?"none":r.points.getLast());}
    static void rescueAt(ServerWorld w,VillagerEntity builder,VillagerEntity victim){swimToShore(w,victim);}
    private VillageWaterRescue(){}
}
