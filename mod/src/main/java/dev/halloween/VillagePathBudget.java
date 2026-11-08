package dev.halloween;

import java.util.*;
import net.minecraft.server.world.ServerWorld;

/** Bounds custom full path searches per world tick; callers retry on their next update. */
final class VillagePathBudget {
    private static final class Budget {long tick=Long.MIN_VALUE;int used,escorts,convoys,clearance;}
    private static final Map<ServerWorld,Budget> BUDGETS=new WeakHashMap<>();
    static boolean take(ServerWorld world){
        var b=BUDGETS.computeIfAbsent(world,w->new Budget());
        if(b.tick!=world.getTime()){b.tick=world.getTime();b.used=0;b.escorts=0;b.convoys=0;b.clearance=0;}
        return b.used++<8;
    }
    static boolean takeEscort(ServerWorld world){var b=BUDGETS.computeIfAbsent(world,w->new Budget());if(b.tick!=world.getTime()){b.tick=world.getTime();b.used=0;b.escorts=0;b.convoys=0;b.clearance=0;}return b.escorts++<12;}
    static boolean takeConvoy(ServerWorld world){var b=BUDGETS.computeIfAbsent(world,w->new Budget());if(b.tick!=world.getTime()){b.tick=world.getTime();b.used=0;b.escorts=0;b.convoys=0;b.clearance=0;}return b.convoys++<8;}
    static boolean takeClearance(ServerWorld world){var b=BUDGETS.computeIfAbsent(world,w->new Budget());if(b.tick!=world.getTime()){b.tick=world.getTime();b.used=0;b.escorts=0;b.convoys=0;b.clearance=0;}return b.clearance++<8;}
    private VillagePathBudget(){}
}
