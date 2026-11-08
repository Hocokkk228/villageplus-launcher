package dev.halloween;

import java.util.*;
import net.minecraft.entity.passive.VillagerEntity;

/** Custom movement owns the brain only while its short renewable task lease is active. */
public final class VillageTasks {
    private static final Map<VillagerEntity,Long> TASKS=new WeakHashMap<>();
    private static final Map<VillagerEntity,Long> COURT=new WeakHashMap<>();
    static void holdCourt(VillagerEntity v){hold(v);COURT.put(v,v.getEntityWorld().getTime()+40);}
    public static boolean attendingCourt(VillagerEntity v){return COURT.getOrDefault(v,0L)>v.getEntityWorld().getTime();}
    static void hold(VillagerEntity v){TASKS.put(v,v.getEntityWorld().getTime()+40);}
    public static boolean ownsMovement(VillagerEntity v){
        if(VillageInjuries.panicking(v))return false;
        return v.getVehicle() instanceof IndustrialVehicleEntity||RailDriver.isDriver(v)||v.getVehicle() instanceof RailSeatEntity||VillageDefense.isGuard(v)||TASKS.getOrDefault(v,0L)>v.getEntityWorld().getTime();
    }
    static void reset(VillagerEntity v){TASKS.remove(v);COURT.remove(v);}
    private VillageTasks(){}
}
