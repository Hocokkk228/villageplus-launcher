package dev.halloween;

import java.util.*;
import net.minecraft.block.BedBlock;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

final class IndustrialDeliveries {
    private static final String TAG="halloween_machine_courier_";
    private static final Map<IndustrialPlants.Plant,Destination> DESTINATIONS=new WeakHashMap<>();
    private static final Map<IndustrialPlants.Plant,Long> RETRY=new WeakHashMap<>();

    private record Destination(Vec3d place,BlockPos approach,float yaw,Direction facing){}

    static void tick(ServerWorld world,VillageMemory.Team team,IndustrialPlants.Plant plant,MachineAssemblyEntity line){
        if(!VillageBuilders.workHours(world))return;
        if(plant.courier.isEmpty()){
            if(RETRY.getOrDefault(plant,0L)>world.getTime())return;
            RETRY.put(plant,world.getTime()+40);
            assign(world,team,plant,line);
            return;
        }
        var entity=world.getEntity(UUID.fromString(plant.courier));
        if(!(entity instanceof VillagerEntity courier)||!courier.isAlive()||courier.hasVehicle()||VillageTrials.busy(courier))return;
        var item=Registries.ITEM.get(Identifier.of(plant.shipment));
        if(item==net.minecraft.item.Items.AIR){release(world,plant,courier);return;}
        var kind=kind(item);
        VillageTasks.hold(courier);
        if(courier.isSleeping())courier.wakeUp();
        if(!plant.loadedCourier){
            if(line.count(item)==0){release(world,plant,courier);return;}
            if(courier.squaredDistanceTo(Vec3d.ofCenter(plant.line()))>9){
                IndustrialNavigation.move(world,courier,plant.line().west());
                return;
            }
            var inventory=courier.getInventory();
            if(RailWorks.free(inventory,item)<1)return;
            RailWorks.take(line,item,1);
            RailWorks.add(inventory,item,1);
            plant.loadedCourier=true;
            IndustrialPlants.get(world).markDirty();
        }
        if(courier.getInventory().count(item)==0){release(world,plant,courier);return;}
        var destination=DESTINATIONS.get(plant);
        if(destination==null){
            if(RETRY.getOrDefault(plant,0L)>world.getTime())return;
            RETRY.put(plant,world.getTime()+80);
            destination=destination(world,team,plant,item,kind);
            if(destination==null)return;
            DESTINATIONS.put(plant,destination);
        }
        boolean obstructing=kind!=null&&RailGeometry.worldBox(kind.bounds(),destination.place,destination.yaw)
                .intersects(courier.getBoundingBox());
        if(obstructing||courier.squaredDistanceTo(Vec3d.ofBottomCenter(destination.approach))>6.25){
            IndustrialNavigation.exact(world,courier,destination.approach);
            return;
        }

        if(kind==null){
            var place=BlockPos.ofFloored(destination.place);
            if(!applianceSpot(world,place)){DESTINATIONS.remove(plant);return;}
            var block=item==IndustrialElectronics.TELEVISION.asItem()?IndustrialElectronics.TELEVISION:IndustrialElectronics.RADIO;
            var state=block.getDefaultState().with(IndustrialElectronics.Appliance.FACING,destination.facing);
            if(!world.setBlockState(place,state))return;
            VillageProjects.recordInfrastructure(world,"appliance:"+place.asLong(),Map.of(place,state));
        }else{
            var vehicle=IndustrialMachines.spawn(world,kind,destination.place,destination.yaw);
            if(vehicle==null){DESTINATIONS.remove(plant);return;}
            if(civilian(kind)){
                var street=CityStreets.ready(world,plant);
                if(street==null||!vehicle.board(courier,true)){vehicle.discard();return;}
                courier.setVillagerData(courier.getVillagerData().withProfession(IndustrialTraffic.DRIVER));
                courier.setExperience(Math.max(1,courier.getExperience()));
                vehicle.streetId=street.id;
                vehicle.streetDistance=0;
                vehicle.dwellUntil=world.getTime()+100;
            }
            plant.vehicles.add(vehicle.getUuidAsString());
            vehicle.addCommandTag("halloween_city_vehicle_"+plant.village);
        }
        RailWorks.take(courier.getInventory(),item,1);
        courier.swingHand(Hand.MAIN_HAND);
        release(world,plant,courier);
    }

    private static void assign(ServerWorld world,VillageMemory.Team team,IndustrialPlants.Plant plant,MachineAssemblyEntity line){
        for(var item:output(plant.kind)){
            if(line.count(item)==0)continue;
            var kind=kind(item);
            if(kind!=null&&plant.vehicles.size()>=(civilian(kind)?6:3))continue;
            var destination=destination(world,team,plant,item,kind);
            if(destination==null)continue;
            var courier=world.getEntitiesByClass(VillagerEntity.class,new Box(plant.base).expand(136,32,136),
                    v->IndustrialTraffic.available(v)&&!VillageInjuries.panicking(v)&&!VillageFights.owns(world,v)
                      &&!v.getCommandTags().contains("halloween_bandit")
                      &&Set.of("none","nitwit","motor_driver").contains(VillageEras.profession(v))
                      &&RailWorks.free(v.getInventory(),item)>0).stream()
                    .min(Comparator.comparingDouble(v->v.squaredDistanceTo(Vec3d.ofCenter(plant.line())))).orElse(null);
            if(courier==null)continue;
            plant.courier=courier.getUuidAsString();
            plant.shipment=Registries.ITEM.getId(item).toString();
            courier.addCommandTag(TAG+plant.line().asLong());
            DESTINATIONS.put(plant,destination);
            IndustrialPlants.get(world).markDirty();
            return;
        }
    }

    private static List<Item> output(int plant){
        return switch(plant){
            case 0 -> List.of(IndustrialMachines.PRODUCTS.get(IndustrialMachines.Kind.SEDAN),IndustrialMachines.PRODUCTS.get(IndustrialMachines.Kind.BUS));
            case 1 -> List.of(IndustrialMachines.PRODUCTS.get(IndustrialMachines.Kind.TANK),IndustrialMachines.PRODUCTS.get(IndustrialMachines.Kind.APC));
            case 2 -> List.of(IndustrialMachines.PRODUCTS.get(IndustrialMachines.Kind.FIGHTER));
            default -> List.of(IndustrialElectronics.TELEVISION.asItem(),IndustrialElectronics.RADIO.asItem());
        };
    }

    private static IndustrialMachines.Kind kind(Item item){
        return IndustrialMachines.PRODUCTS.entrySet().stream().filter(e->e.getValue()==item).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private static boolean civilian(IndustrialMachines.Kind kind){return kind==IndustrialMachines.Kind.SEDAN||kind==IndustrialMachines.Kind.BUS;}

    private static Destination destination(ServerWorld world,VillageMemory.Team team,IndustrialPlants.Plant plant,Item item,IndustrialMachines.Kind kind){
        if(kind!=null){
            if(civilian(kind)){
                var street=CityStreets.ready(world,plant);
                if(street==null)return null;
                var path=street.loop();
                var place=path.center(0);
                if(!vehicleSpace(world,kind,place,path.yaw(0)))return null;
                var side=BlockPos.ofFloored(path.at(0,kind.width/2+1.6));
                return new Destination(place,side,path.yaw(0),Direction.NORTH);
            }
            for(int x:kind==IndustrialMachines.Kind.FIGHTER?new int[]{18}:new int[]{8,18,28}){
                var place=Vec3d.ofBottomCenter(plant.base.add(x,1,-8));
                if(vehicleSpace(world,kind,place,180))
                    return new Destination(place,BlockPos.ofFloored(place).east((int)Math.ceil(kind.width/2)+1),180,Direction.NORTH);
            }
            return null;
        }

        var houses=world.getPersistentStateManager().getOrCreate(VillageMemory.TYPE).houses;
        var block=item==IndustrialElectronics.TELEVISION.asItem()?IndustrialElectronics.TELEVISION:IndustrialElectronics.RADIO;
        for(var house:houses){
            if(!VillageEras.residential(house)||!world.isChunkLoaded(house.min)||!house.min.isWithinDistance(plant.base,240)
                    ||VillageEras.team(world,house.min)!=team||VillageTerrain.removesHouse(world,house)
                    ||house.blueprint.stream().anyMatch(r->r.state().isOf(block)))continue;
            for(var entry:house.blueprint){
                if(!(entry.state().getBlock() instanceof BedBlock)||!world.getBlockState(entry.pos()).isOf(entry.state().getBlock()))continue;
                for(var side:Direction.Type.HORIZONTAL){
                    var position=entry.pos().offset(side,2);
                    var approach=position.offset(side.getOpposite());
                    if(position.getX()<=house.min.getX()||position.getX()>=house.max.getX()
                            ||position.getZ()<=house.min.getZ()||position.getZ()>=house.max.getZ()
                            ||!applianceSpot(world,position)||!world.getBlockState(approach).isAir()
                            ||!world.getBlockState(approach.up()).isAir())continue;
                    return new Destination(Vec3d.ofBottomCenter(position),approach,0,side.getOpposite());
                }
            }
        }
        return null;
    }

    private static boolean applianceSpot(ServerWorld world,BlockPos position){
        return world.isChunkLoaded(position)&&world.getBlockState(position.down()).isSolidBlock(world,position.down())
                &&world.getBlockState(position).isAir()&&world.getBlockState(position.up()).isAir()
                &&world.getOtherEntities(null,new Box(position)).isEmpty();
    }

    private static boolean vehicleSpace(ServerWorld world,IndustrialMachines.Kind kind,Vec3d place,float yaw){
        var pos=BlockPos.ofFloored(place);
        if(!world.isChunkLoaded(pos))return false;
        var box=RailGeometry.worldBox(kind.bounds(),place,yaw);
        return world.isBlockSpaceEmpty(null,box)&&world.getOtherEntities(null,box,e->e instanceof IndustrialVehicleEntity).isEmpty();
    }

    private static void release(ServerWorld world,IndustrialPlants.Plant plant,VillagerEntity courier){
        courier.removeCommandTag(TAG+plant.line().asLong());
        if(!courier.hasVehicle())courier.getNavigation().stop();
        plant.courier="";
        plant.shipment="";
        plant.loadedCourier=false;
        DESTINATIONS.remove(plant);
        IndustrialPlants.get(world).markDirty();
    }

    static void onDeath(ServerWorld world,VillagerEntity villager){
        for(var plant:IndustrialPlants.get(world).plants){
            if(!plant.courier.equals(villager.getUuidAsString()))continue;
            if(plant.loadedCourier){
                var item=Registries.ITEM.get(Identifier.of(plant.shipment));
                if(RailWorks.take(villager.getInventory(),item,1))villager.dropStack(world,new ItemStack(item));
            }
            release(world,plant,villager);
        }
    }

    private IndustrialDeliveries(){}
}
