package dev.halloween;

import net.minecraft.item.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.*;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** Single-shot black-powder weapon. Ammunition and cooldown are server authoritative. */
class MusketItem extends Item {
    private record Smoke(java.util.UUID shooter,long until){}
    private static final java.util.Map<ServerWorld,java.util.List<Smoke>> SMOKE=new java.util.WeakHashMap<>();
    static void tickSmoke(ServerWorld w){
        var list=SMOKE.get(w);if(list==null)return;
        list.removeIf(s->w.getTime()>=s.until());
        if(w.getTime()%4!=0)return;
        for(var smoke:list){var p=w.getPlayerByUuid(smoke.shooter());if(p==null||!(p.getMainHandStack().getItem() instanceof MusketItem))continue;
            var muzzle=p.getEyePos().add(p.getRotationVec(1).multiply(1.1));
            w.spawnParticles(ParticleTypes.SMOKE,muzzle.x,muzzle.y-.12,muzzle.z,2,.025,.025,.025,.01);
        }
    }
    static boolean fireGuard(ServerWorld w,net.minecraft.entity.passive.VillagerEntity guard,net.minecraft.entity.LivingEntity target,float damage){
        if(!(guard.getMainHandStack().getItem() instanceof MusketItem))return false;
        int ammo=-1;for(int i=0;i<guard.getInventory().size();i++)if(guard.getInventory().getStack(i).isOf(VillageItems.MUSKET_CARTRIDGE)){ammo=i;break;}
        if(ammo<0)return false;
        var from=guard.getEyePos();var end=target.getBoundingBox().getCenter();
        var hit=w.raycast(new net.minecraft.world.RaycastContext(from,end,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,guard));
        if(hit.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)return false;
        for(var e:w.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,new net.minecraft.util.math.Box(from,end).expand(.3),e->e!=guard&&e!=target&&e.isAlive()))if(e.getBoundingBox().raycast(from,end).isPresent())return false;
        guard.getInventory().getStack(ammo).decrement(1);target.damage(w,w.getDamageSources().mobAttack(guard),damage);guard.swingHand(Hand.MAIN_HAND);
        var muzzle=from.add(end.subtract(from).normalize());
        BlastEffects.firearm(w,muzzle,end.subtract(from));
        w.playSound(null,guard.getBlockPos(),SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.NEUTRAL,1.2f,1.6f);return true;
    }
    MusketItem(Settings settings){super(settings);}
    @Override public ActionResult use(World world,PlayerEntity player,Hand hand){if(hand==Hand.MAIN_HAND&&player instanceof net.minecraft.server.network.ServerPlayerEntity p&&VillageSelfShot.tryUse(p))return ActionResult.SUCCESS;player.setCurrentHand(hand);return ActionResult.CONSUME;}
    @Override public int getMaxUseTime(ItemStack stack,net.minecraft.entity.LivingEntity user){return 72000;}
    public ActionResult fire(World world,PlayerEntity player,Hand hand){
        ItemStack weapon=player.getStackInHand(hand);
        if(player.getItemCooldownManager().isCoolingDown(weapon))return ActionResult.FAIL;
        if(!(world instanceof ServerWorld server))return ActionResult.SUCCESS;
        int slot=-1;
        for(int i=0;i<player.getInventory().size();i++)if(player.getInventory().getStack(i).isOf(VillageItems.MUSKET_CARTRIDGE)){slot=i;break;}
        if(slot<0&&!player.isCreative())return ActionResult.FAIL;
        var start=player.getEyePos();var direction=player.getRotationVec(1);
        var end=start.add(direction.multiply(256));
        // Bound the ray to loaded terrain, without generating chunks at shooting range.
        for(int distance=1;distance<=256;distance++){
            var probe=net.minecraft.util.math.BlockPos.ofFloored(start.add(direction.multiply(distance)));
            if(!server.isChunkLoaded(new net.minecraft.util.math.ChunkPos(probe).toLong())){end=start.add(direction.multiply(distance-1));break;}
        }
        var cursor=start;
        for(int pane=0;pane<16;pane++){
            var hit=server.raycast(new net.minecraft.world.RaycastContext(cursor,end,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,player));
            if(hit.getType()==net.minecraft.util.hit.HitResult.Type.MISS)break;
            var state=server.getBlockState(hit.getBlockPos());
            if(state.isOf(net.minecraft.block.Blocks.GLASS)||state.isOf(net.minecraft.block.Blocks.TINTED_GLASS)||state.getBlock() instanceof net.minecraft.block.StainedGlassBlock||state.getBlock() instanceof net.minecraft.block.PaneBlock&&state.getBlock()!=net.minecraft.block.Blocks.IRON_BARS){
                server.breakBlock(hit.getBlockPos(),false,player);cursor=hit.getPos().add(direction.multiply(.02));
                if(pane==15)end=cursor;
            }else{end=hit.getPos();break;}
        }
        net.minecraft.entity.LivingEntity victim=null;double nearest=start.squaredDistanceTo(end);
        for(var entity:server.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,new net.minecraft.util.math.Box(start,end).expand(1),e->e!=player&&e.isAlive()&&!e.isSpectator())){
            var contact=entity.getBoundingBox().expand(.05).raycast(start,end);
            if(contact.isPresent()&&start.squaredDistanceTo(contact.get())<nearest){nearest=start.squaredDistanceTo(contact.get());victim=entity;}
        }
        if(victim!=null)victim.damage(server,server.getDamageSources().playerAttack(player),(float)(weapon.isOf(VillageItems.REVOLVER)?Math.max(6,22-Math.sqrt(nearest)*.12):Math.max(12,32-Math.sqrt(nearest)*.12)));
        if(!player.isCreative())player.getInventory().getStack(slot).decrement(1);
        int cooldown=70;
        if(weapon.isOf(VillageItems.REVOLVER)){
            var data=weapon.getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.DEFAULT).copyNbt();
            int chamber=(data.getInt("village_rounds").orElse(0)+1)%6;data.putInt("village_rounds",chamber);
            weapon.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(data));cooldown=chamber==0?60:10;
        }
        player.getItemCooldownManager().set(weapon,cooldown);
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity shooter) WeaponShotNetwork.send(shooter);
        SMOKE.computeIfAbsent(server,k->new java.util.ArrayList<>()).add(new Smoke(player.getUuid(),server.getTime()+60));
        var muzzle=player.getEyePos().add(player.getRotationVec(1).multiply(1.1));
        BlastEffects.firearm(server,muzzle,direction);
        server.playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.PLAYERS,1.5f,1.6f);
        return ActionResult.SUCCESS;
    }
}
