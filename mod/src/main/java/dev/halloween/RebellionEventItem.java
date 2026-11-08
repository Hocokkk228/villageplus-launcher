package dev.halloween;

import net.minecraft.item.Item;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;

final class RebellionEventItem extends Item {
    RebellionEventItem(Settings settings){super(settings);}
    @Override public ActionResult use(World world,PlayerEntity player,Hand hand){
        if(world instanceof ServerWorld server&&player instanceof ServerPlayerEntity sp&&!VillageRebellion.startNear(server,sp))
            player.sendMessage(Text.translatable("message.halloween.rebellion_unavailable"),true);
        return ActionResult.SUCCESS;
    }
}
