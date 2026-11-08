package dev.halloween;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

final class ReleaseBlock extends Block {
    ReleaseBlock(Settings s){super(s);}
    static void register(){
        var id=Identifier.of("halloween","early_release");var key=RegistryKey.of(RegistryKeys.BLOCK,id);
        var block=Registry.register(Registries.BLOCK,key,new ReleaseBlock(AbstractBlock.Settings.create().registryKey(key).strength(2f)));
        var item=Registry.register(Registries.ITEM,id,new BlockItem(block,new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM,id)).useBlockPrefixedTranslationKey()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e->e.add(item));
    }
    @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){
        if(world instanceof ServerWorld w)player.sendMessage(Text.literal(VillageTrials.requestRelease(w,pos)?"§aОхрана отправилась освобождать заключённого.":"§cЗаключённый поблизости не найден."),true);
        return ActionResult.SUCCESS;
    }
}
