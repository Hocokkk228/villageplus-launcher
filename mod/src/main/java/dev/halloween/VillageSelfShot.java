package dev.halloween;
import java.util.*;
import net.minecraft.server.network.ServerPlayerEntity;
/** Explicit in-game gesture: five seconds crouched with the same gun, then stand and use within five seconds. */
final class VillageSelfShot {
 static final int CHARGE=100;
 private static final class State {net.minecraft.item.ItemStack gun;int ticks;long lastCrouch;}
 private static final Map<ServerPlayerEntity,State> STATES=new WeakHashMap<>();
 static void register(){net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{
  for(var p:server.getPlayerManager().getPlayerList())tickPlayer(p);
 });}
 static void tickPlayer(ServerPlayerEntity p){
   if(!p.isAlive()||p.isCreative()||p.isSpectator()||!(p.getMainHandStack().getItem() instanceof MusketItem)){STATES.remove(p);return;}
   var s=STATES.computeIfAbsent(p,k->new State());if(s.gun!=p.getMainHandStack()){s.gun=p.getMainHandStack();s.ticks=0;}
   if(p.isSneaking()){s.ticks=Math.min(CHARGE,s.ticks+1);s.lastCrouch=p.getEntityWorld().getTime();}
   else if(s.ticks<CHARGE||p.getEntityWorld().getTime()-s.lastCrouch>100)s.ticks=0;
 }
 static boolean tryUse(ServerPlayerEntity p){
  var s=STATES.get(p);if(s==null||s.ticks<CHARGE||s.gun!=p.getMainHandStack()||p.isSneaking()||p.getEntityWorld().getTime()-s.lastCrouch>100)return false;
  if(p.getItemCooldownManager().isCoolingDown(s.gun))return false;
  for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(VillageItems.MUSKET_CARTRIDGE)){
   p.getInventory().getStack(i).decrement(1);STATES.remove(p);
   p.getEntityWorld().playSound(null,p.getBlockPos(),net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),net.minecraft.sound.SoundCategory.PLAYERS,1,1.6f);
   p.setHealth(0);p.onDeath(p.getDamageSources().genericKill());return true;
  }
  return false;
 }
}
