package dev.halloween;

import net.minecraft.item.Item;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** Creative test controls for the ordinary court flow. */
final class TrialEventItem extends Item {
    private static final String PREFIX="halloween_test_trial_mode_";
    private static final String[] MODES={"attack","theft","murder","random"};
    private static final String[] NAMES={"нападение", "ограбление", "убийство", "случайное наказание"};
    private final boolean execution;
    TrialEventItem(Settings settings,boolean execution){super(settings);this.execution=execution;}
    @Override public ActionResult use(World world,PlayerEntity player,Hand hand){
        if(!(world instanceof ServerWorld w)||!(player instanceof ServerPlayerEntity p))return ActionResult.SUCCESS;
        int mode=0;for(String tag:p.getCommandTags())if(tag.startsWith(PREFIX))try{mode=Integer.parseInt(tag.substring(PREFIX.length()));}catch(NumberFormatException ignored){}
        mode=Math.floorMod(mode,MODES.length);
        if(p.isSneaking()&&!execution){
            for(String tag:java.util.List.copyOf(p.getCommandTags()))if(tag.startsWith(PREFIX))p.removeCommandTag(tag);
            mode=(mode+1)%MODES.length;p.addCommandTag(PREFIX+mode);p.sendMessage(Text.literal("§eТест суда: "+NAMES[mode]),true);return ActionResult.SUCCESS;
        }
        String sentence=execution?"execution":MODES[mode];
        if(sentence.equals("random"))sentence=new String[]{"attack","theft","murder","execution"}[w.random.nextInt(4)];
        if(!VillageTrials.startTest(w,p,sentence))p.sendMessage(Text.literal("§cНужны свободный взрослый житель, охранник и площадь поблизости."),true);
        return ActionResult.SUCCESS;
    }
}
