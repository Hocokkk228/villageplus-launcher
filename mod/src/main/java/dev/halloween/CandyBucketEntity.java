package dev.halloween;

import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.storage.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

public final class CandyBucketEntity extends BlockEntity {
    int count;
    private int progress;
    public CandyBucketEntity(BlockPos p, BlockState s) { super(CandyBucketContent.BUCKET_ENTITY, p, s); }
    @Override protected void readData(ReadView view) {
        super.readData(view);
        count = Math.clamp(view.getInt("CandyCount", 0), 0, 64);
        progress = Math.clamp(view.getInt("Progress", 0), 0, 80);
    }
    @Override protected void writeData(WriteView view) {
        super.writeData(view); view.putInt("CandyCount", count); view.putInt("Progress", progress);
    }
    public static void tick(World world, BlockPos pos, BlockState state, CandyBucketEntity b) {
        if (!(world instanceof ServerWorld server)) return;
        double x=pos.getX()+.5, y=pos.getY(), z=pos.getZ()+.5;
        if (b.progress == 0) {
            if ((world.getTime() & 1) != 0) return;
            int before=b.count;
            // Only dropped candy physically inside/above the opening is accepted.
            for (ItemEntity entity : world.getEntitiesByClass(ItemEntity.class,
                    new Box(pos).contract(.20, 0, .20).stretch(0, .4, 0),
                    e -> e.isAlive() && e.getStack().isOf(CandyBucketContent.CANDY_CORN))) {
                int take = Math.min(64-b.count, entity.getStack().getCount());
                b.count += take;
                ItemStack remaining=entity.getStack().copy(); remaining.decrement(take);
                if (remaining.isEmpty()) entity.discard(); else entity.setStack(remaining);
                sound(server,pos,SoundEvents.ENTITY_ITEM_PICKUP,.55f,1.1f+b.count/128f);
                server.spawnParticles(ParticleTypes.HAPPY_VILLAGER,x,y+.65,z,3,.18,.1,.18,.01);
                if (b.count == 64) { b.progress=1; break; }
            }
            if (before==b.count) return;
        } else {
            b.progress++;
            int t=b.progress;
            if (t==2) sound(server,pos,SoundEvents.ENTITY_WITCH_CELEBRATE,.8f,1.25f);
            if (t>=18 && t<60 && t%4==0) {
                sound(server,pos,SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,.75f,.7f+t/60f);
                server.spawnParticles(ParticleTypes.WITCH,x,y+1.15,z,5,.35,.25,.35,.03);
            }
            if (t==60) {
                // Clear the paid candy before returning to idle; breaking after this cannot refund it.
                b.count=0;
                ItemEntity gift=new ItemEntity(server,x,y+1.45,z,reward(server));
                gift.setVelocity((world.random.nextDouble()-.5)*.24,.55,(world.random.nextDouble()-.5)*.24);
                gift.setPickupDelay(15); server.spawnEntity(gift);
                server.spawnParticles(ParticleTypes.EXPLOSION,x,y+1.2,z,1,0,0,0,0);
                server.spawnParticles(ParticleTypes.FIREWORK,x,y+1.2,z,35,.25,.25,.25,.12);
                sound(server,pos,SoundEvents.ENTITY_GENERIC_EXPLODE.value(),.65f,1.6f);
                sound(server,pos,SoundEvents.ENTITY_PLAYER_LEVELUP,.8f,1.2f);
            }
            if (t>=80) b.progress=0;
        }
        int t=b.progress;
        int motion=t==0?0:t<18?1+Math.min(3,t/5):t<60?5+(t%6):Math.min(15,11+(t-60)/4);
        int fill=b.count==0?0:Math.min(4,(b.count+15)/16);
        BlockState current=world.getBlockState(pos);
        if (current.get(CandyBucketBlock.FILL)!=fill || current.get(CandyBucketBlock.MOTION)!=motion)
            world.setBlockState(pos,current.with(CandyBucketBlock.FILL,fill).with(CandyBucketBlock.MOTION,motion),Block.NOTIFY_LISTENERS);
        b.markDirty();
    }
    private static ItemStack reward(ServerWorld w) {
        int roll=w.random.nextInt(100);
        if (roll<45) return new ItemStack(Items.DIRT,8+w.random.nextInt(25));
        if (roll<72) {
            Item[] pool={Items.OAK_LOG,Items.PUMPKIN,Items.GOLDEN_CARROT,Items.IRON_INGOT,Items.EMERALD};
            return new ItemStack(pool[w.random.nextInt(pool.length)],2+w.random.nextInt(7));
        }
        if (roll<90) {
            Item[] pool={Items.DIAMOND,Items.GOLD_INGOT,Items.GOLDEN_APPLE,Items.ENDER_PEARL};
            return new ItemStack(pool[w.random.nextInt(pool.length)],1+w.random.nextInt(3));
        }
        Item[] pool=roll<98?new Item[]{Items.DIAMOND_SWORD,Items.DIAMOND_PICKAXE,Items.DIAMOND_CHESTPLATE}:
                new Item[]{Items.NETHERITE_SWORD,Items.NETHERITE_PICKAXE,Items.NETHERITE_CHESTPLATE,Items.NETHERITE_HELMET};
        return EnchantmentHelper.enchant(w.random,new ItemStack(pool[w.random.nextInt(pool.length)]),30,w.getRegistryManager(),Optional.empty());
    }
    private static void sound(ServerWorld w,BlockPos p,SoundEvent s,float volume,float pitch) {
        w.playSound(null,p,s,SoundCategory.BLOCKS,volume,pitch);
    }
}
