package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;

final class RoyalCoronation {
    private record Animation(UUID king,UUID crown,long started){}
    private static final Map<ServerWorld,List<Animation>> ACTIVE=new WeakHashMap<>();
    static void register(){ServerTickEvents.END_WORLD_TICK.register(RoyalCoronation::tick);}
    static boolean isAnimating(ServerWorld w,VillagerEntity king){return ACTIVE.getOrDefault(w,List.of()).stream().anyMatch(a->a.king().equals(king.getUuid()));}
    static void begin(ServerWorld w,VillagerEntity king){
        if (RoyalProfession.updateOffice(w, king)) return;
        ItemEntity item=new ItemEntity(w,king.getX(),king.getY()+5,king.getZ(),new ItemStack(RoyalProfession.CROWN));
        item.setNoGravity(true);item.setPickupDelayInfinite();item.setVelocity(0,0,0);w.spawnEntity(item);
        ACTIVE.computeIfAbsent(w,k->new ArrayList<>()).add(new Animation(king.getUuid(),item.getUuid(),w.getTime()));
        w.playSound(null,king.getBlockPos(),SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,SoundCategory.PLAYERS,1,1);
    }
    private static void tick(ServerWorld w){
        var list=ACTIVE.get(w);if(list==null)return;
        var it=list.iterator();while(it.hasNext()){
            var a=it.next();var item=w.getEntity(a.crown());var entity=w.getEntity(a.king());
            if(!(entity instanceof VillagerEntity king)||item==null||RoyalProfession.isPresident(king)){if(item!=null)item.discard();it.remove();continue;}
            king.getNavigation().stop();king.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);king.setVelocity(0,king.getVelocity().y,0);
            double progress=Math.min(1,(w.getTime()-a.started())/80.0);double eased=progress*progress*(3-2*progress);
            item.setPos(king.getX(),king.getY()+5-3.1*eased,king.getZ());item.setVelocity(0,0,0);
            if(progress>=1){item.discard();king.equipStack(EquipmentSlot.HEAD,new ItemStack(RoyalProfession.CROWN));it.remove();}
        }
    }
    private RoyalCoronation(){}
}
