package dev.halloween;

import java.util.Comparator;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

final class VillageBounties {
    private static final String PAID = "halloween_bounty_paid";

    static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(world instanceof ServerWorld server) || !(player instanceof ServerPlayerEntity p)
                    || hand != Hand.MAIN_HAND || !p.isSneaking() || !p.getMainHandStack().isEmpty()
                    || !world.getBlockState(hit.getBlockPos()).isOf(Blocks.BELL)
                    || VillageEras.team(server, hit.getBlockPos()) == null) return ActionResult.PASS;
            var notices = server.getEntitiesByClass(VillagerEntity.class, new Box(hit.getBlockPos()).expand(96, 20, 96),
                    VillageBounties::suspect);
            notices.sort(Comparator.comparingDouble(p::squaredDistanceTo));
            p.sendMessage(Text.literal(notices.isEmpty() ? "§7На доске розыска пусто." : "§6Разыскиваются живыми:"), false);
            for (var v : notices.stream().limit(6).toList()) {
                p.sendMessage(Text.literal("§e" + v.getName().getString() + " • замечен около "
                        + v.getBlockPos().toShortString() + " • " + reward(v) + " изумрудов"), false);
            }
            return ActionResult.SUCCESS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (!(world instanceof ServerWorld server) || !(player instanceof ServerPlayerEntity p)
                    || hand != Hand.MAIN_HAND || !p.isSneaking() || !p.getMainHandStack().isEmpty()
                    || !(target instanceof VillagerEntity v) || !suspect(v) || p.isCreative() || p.isSpectator()) return ActionResult.PASS;
            if (v.getHealth() > v.getMaxHealth() * .45f) {
                p.sendMessage(Text.literal("§7Подозреваемый ещё сопротивляется."), true);
                return ActionResult.SUCCESS;
            }
            var guard = server.getEntitiesByClass(VillagerEntity.class, v.getBoundingBox().expand(9, 4, 9),
                    g -> VillageDefense.isGuard(g) && !VillageDefense.escorting(g) && !g.hasVehicle() && g.canSee(v)
                            && !VillageCustody.busy(server, g, null))
                    .stream().min(Comparator.comparingDouble(v::squaredDistanceTo)).orElse(null);
            if (guard == null) {
                p.sendMessage(Text.literal("§7Рядом нет свободной стражи. Приведи подозреваемого к патрулю."), true);
                return ActionResult.SUCCESS;
            }
            capture(server, p, v, guard);
            return ActionResult.SUCCESS;
        });
    }

    static boolean suspect(VillagerEntity villager) {
        var tags = villager.getCommandTags();
        return villager.isAlive() && tags.contains("halloween_bandit") && tags.contains("halloween_wanted")
                && !tags.contains(PAID) && !tags.contains("halloween_surrender") && !tags.contains("halloween_prisoner")
                && !villager.isLeashed() && !villager.hasVehicle();
    }

    static boolean capture(ServerWorld world, ServerPlayerEntity player, VillagerEntity suspect, VillagerEntity guard) {
        if (!suspect(suspect) || suspect.getHealth() > suspect.getMaxHealth() * .45f
                || !VillageDefense.isGuard(guard) || VillageDefense.escorting(guard)
                || guard.squaredDistanceTo(suspect) > 81 || player.squaredDistanceTo(suspect) > 25) return false;
        suspect.getNavigation().stop();
        suspect.setAttacking(false);
        if (suspect.isSleeping()) suspect.wakeUp();
        suspect.addCommandTag("halloween_surrender");
        suspect.attachLeash(guard, true);
        guard.addCommandTag("halloween_guard_escorting");
        VillageTrials.arrested(world, suspect);
        if (!suspect.addCommandTag(PAID)) return false;
        int reward = reward(suspect);
        var stack = new ItemStack(Items.EMERALD, reward);
        player.getInventory().insertStack(stack);
        if (!stack.isEmpty()) player.dropItem(stack, false);
        player.sendMessage(Text.literal("§aСтража приняла подозреваемого. Награда: " + reward + " изумрудов."), false);
        return true;
    }

    private static int reward(VillagerEntity villager) {
        return villager.getCommandTags().contains("halloween_crime_murder") ? 12 : 6;
    }

    private VillageBounties() {}
}
