package dev.halloween;

import net.minecraft.item.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;

final class VillageFightItem extends Item {
    VillageFightItem(Settings settings){super(settings);}
    @Override public ActionResult use(World world,PlayerEntity player,Hand hand){
        if(world instanceof ServerWorld server){
            if(!VillageArena.startNear(server,player))player.sendMessage(Text.translatable("message.halloween.fight_unavailable"),true);
        }
        return ActionResult.SUCCESS;
    }
}
