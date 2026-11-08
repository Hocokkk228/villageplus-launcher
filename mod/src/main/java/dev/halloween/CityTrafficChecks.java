package dev.halloween;

import java.util.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

final class CityTrafficChecks {
    private static CityStreets.Work work;
    private static CityStreets.Street street;
    private static IndustrialVehicleEntity bus;
    private static VillagerEntity rider;
    private static Vec3d departure;
    private static int age,deliveryAge;
    private static IndustrialPlants.Plant plant;
    private static VillageMemory.Team team;
    private static MachineAssemblyEntity assembly;
    private static VillagerEntity courier;
    private static Vec3d pickup;
    private static boolean sawCargo;
    private static boolean boarded;

    static boolean tick(ServerWorld world){
        if(work==null){
            var start=new BlockPos(17,221,68);
            var end=new BlockPos(109,221,68);
            work=new CityStreets.Work("road_test",9240,start,end);
            var obstacle=new LinkedHashMap<BlockPos,net.minecraft.block.BlockState>();
            for(var p:BlockPos.iterate(new BlockPos(53,221,63),new BlockPos(66,223,72))){
                var at=p.toImmutable();
                world.setBlockState(at,Blocks.BRICKS.getDefaultState());
                obstacle.put(at,Blocks.BRICKS.getDefaultState());
            }
            VillageProjects.get(world).add(world,"road_test_protected",9240,"factory",obstacle);
        }
        if(street==null){
            CityStreets.advance(world,work);
            require(!work.failed,"survey avoids protected factory");
            if(work.remaining==null||work.remaining.hasNext()||work.waitingColumn!=null)return false;
            require(work.plan.keySet().stream().noneMatch(p->p.getX()>=53&&p.getX()<=66&&p.getZ()>=63&&p.getZ()<=72),
                    "road and pavement do not erase factory blocks");
            for(var entry:work.plan.entrySet())world.setBlockState(entry.getKey(),entry.getValue());
            var base=work.start.add(-18,-1,22);
            plant=new IndustrialPlants.Plant(work.village,0,base,"",List.of());
            team=new VillageMemory.Team(work.village,work.start,0);
            var id=CityStreets.id(plant);
            street=new CityStreets.Street(id,work.village,work.path);
            CityStreets.get(world).streets.removeIf(s->s.id.equals(id));
            CityStreets.get(world).streets.add(street);
            var projects=VillageProjects.get(world);
            var point=work.start.down();
            projects.add(world,id,work.village,"city_street",Map.of(point,world.getBlockState(point)));
            projects.retire(projects.jobs.stream().filter(j->j.id.equals(id)).findFirst().orElseThrow());
            departure=street.loop().center(0);
            world.setTimeOfDay(3000);
            world.setBlockState(plant.line(),MachineAssembly.BLOCK.getDefaultState());
            assembly=(MachineAssemblyEntity)world.getBlockEntity(plant.line());
            courier=spawn(world,Vec3d.ofBottomCenter(plant.line().west(5)));
            RailWorks.add(assembly,IndustrialMachines.PRODUCTS.get(IndustrialMachines.Kind.BUS),1);
            IndustrialMachineChecks.log("PASS road survey, surfaces, and protected factory");
        }
        if(bus==null){
            if(world.getTime()%5==0)IndustrialDeliveries.tick(world,team,plant,assembly);
            var item=IndustrialMachines.PRODUCTS.get(IndustrialMachines.Kind.BUS);
            if(courier.getInventory().count(item)==1){
                sawCargo=true;
                if(pickup==null)pickup=courier.getEntityPos();
            }
            if(courier.getVehicle() instanceof IndustrialVehicleEntity vehicle){
                bus=vehicle;
                require(sawCargo&&pickup.distanceTo(courier.getEntityPos())>20,"courier physically carries the manufactured bus from the bench");
                require(assembly.count(item)==0&&courier.getInventory().count(item)==0,"output is consumed once on deployment");
                var position=departure.add(RailGeometry.rotate(new Vec3d(bus.kind().width/2+1,0,1.6),bus.getYaw()));
                rider=spawn(world,position);
                IndustrialMachineChecks.log("PASS delivered real assembled item on foot; courier became the bus driver");
            }else if(++deliveryAge>1600)throw new AssertionError("delivery stalled: "+courier.getEntityPos()
                    +" pickup="+pickup+" approach="+BlockPos.ofFloored(street.loop().at(0,2.2))
                    +" cargo="+courier.getInventory().count(item));
            return false;
        }
        age++;
        boarded|=rider.getVehicle()==bus;
        if(!boarded&&age>110)throw new AssertionError("villager did not board through the bus door");
        if(bus.visits>=3){
            require(boarded&&!rider.hasVehicle(),"resident boarded and alighted at the far station");
            require(rider.squaredDistanceTo(departure)>40*40,"resident travelled to a different stop");
            require(bus.squaredDistanceTo(departure)<16*16,"bus returned to the first stop");
            IndustrialMachineChecks.log("PASS bus completes both stops, actual villager boards, rides and disembarks");
            return true;
        }
        if(age>2400)throw new AssertionError("bus stalled at "+bus.getEntityPos()+" speed="+bus.speed+" visits="+bus.visits
                +" goal="+street.loop().center(street.stops().get(bus.nextStop))+" yaw="+bus.getYaw()
                +" closest="+street.loop().center(street.loop().closest(bus.getEntityPos()))+" rider="+rider.getEntityPos());
        return false;
    }

    private static VillagerEntity spawn(ServerWorld world,Vec3d at){
        var villager=EntityType.VILLAGER.create(world,SpawnReason.EVENT);
        villager.setPosition(at);
        world.spawnEntity(villager);
        VillageTasks.hold(villager);
        return villager;
    }

    private static void require(boolean ok,String why){IndustrialMachineChecks.require(ok,why);}
    private CityTrafficChecks(){}
}
