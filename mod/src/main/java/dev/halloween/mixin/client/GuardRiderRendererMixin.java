package dev.halloween.mixin.client;

import dev.halloween.GuardRiderState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.VillagerEntityRenderer;
import net.minecraft.client.render.entity.state.VillagerEntityRenderState;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityRenderer.class)
public abstract class GuardRiderRendererMixin {
    @Inject(method="updateRenderState(Lnet/minecraft/entity/passive/VillagerEntity;Lnet/minecraft/client/render/entity/state/VillagerEntityRenderState;F)V",at=@At("TAIL"))
    private void halloween$riding(VillagerEntity villager,VillagerEntityRenderState state,float delta,CallbackInfo ci){
        var profession = net.minecraft.registry.Registries.VILLAGER_PROFESSION.getId(villager.getVillagerData().profession().value());
        boolean custom = profession != null && profession.getNamespace().equals("halloween");
        String role = custom ? profession.getPath() : "";
        boolean guard = villager.getVillagerData().profession().matchesKey(dev.halloween.GuardProfession.KEY)
                || villager.getVillagerData().profession().matchesKey(dev.halloween.GuardProfession.ROYAL_KEY);
        boolean worker = role.equals("lumberjack") || role.equals("miner") || role.equals("builder");
        boolean riding = villager.getVehicle() instanceof AbstractHorseEntity;
        boolean seated = villager.getVehicle() instanceof dev.halloween.RailSeatEntity || villager.getVehicle() instanceof dev.halloween.IndustrialVehicleEntity;
        var extra = (GuardRiderState)state;
        extra.halloween$setSeatedPassenger(seated);
        extra.halloween$setMountedGuard(riding);
        var combat=dev.halloween.FightPose.of(villager);
        int fight=state.deathTime>0?0:combat.halloween$fightPose();
        extra.halloween$fightAction(fight,combat.halloween$fightAge(delta));
        float swing = villager.getHandSwingProgress(delta);
        boolean fighting = villager.isAttacking();
        boolean street = fighting && !guard && !worker;
        var pose = GuardRiderState.Pose.CLASSIC;
        if (riding && guard) pose = GuardRiderState.Pose.GUARD;
        else if (!state.baby && state.deathTime <= 0 && !villager.hasVehicle() && !villager.isSleeping()) {
            if (state.touchingWater) pose = GuardRiderState.Pose.SWIM;
            else if (swing>0 && villager.preferredHand==net.minecraft.util.Hand.OFF_HAND && !fighting) pose=GuardRiderState.Pose.INTERACT;
            else if (villager.isSprinting()&&!guard) pose=GuardRiderState.Pose.CASUAL;
            else if (guard && (fighting || swing > 0)) pose = GuardRiderState.Pose.GUARD;
            else if (street) pose = GuardRiderState.Pose.STREET;
            else if ((worker || swing>0) && (swing > 0 || !villager.getMainHandStack().isEmpty())) pose = GuardRiderState.Pose.WORK;
            else if (villager.getCustomer() == null) pose = GuardRiderState.Pose.CASUAL;
        }
        if(fight!=0)pose=GuardRiderState.Pose.STREET;
        float turn = net.minecraft.util.math.MathHelper.clamp(
                net.minecraft.util.math.MathHelper.wrapDegrees(villager.bodyYaw - villager.lastBodyYaw) / 22f, -1, 1);
        float hurt = Math.max(0, (villager.hurtTime - delta) / 10f);
        if(villager.getVehicle() instanceof dev.halloween.IndustrialVehicleEntity machine&&machine.isDriver(villager))pose=GuardRiderState.Pose.WORK;
        extra.halloween$pose(pose, turn, hurt, (villager.getId() & 31) * .67f);
        extra.halloween$setGuard(pose != GuardRiderState.Pose.CLASSIC);
        var held = villager.getMainHandStack();
        var itemId = net.minecraft.registry.Registries.ITEM.getId(held.getItem());
        var weapon = held.isEmpty() ? GuardRiderState.Weapon.EMPTY
                : held.isOf(Items.CROSSBOW) ? GuardRiderState.Weapon.CROSSBOW
                : itemId.equals(net.minecraft.util.Identifier.of("halloween","musket")) ? GuardRiderState.Weapon.MUSKET
                : itemId.equals(net.minecraft.util.Identifier.of("halloween","revolver")) ? GuardRiderState.Weapon.REVOLVER
                : itemId.equals(net.minecraft.util.Identifier.of("halloween","sabre")) ? GuardRiderState.Weapon.SABRE
                : itemId.equals(net.minecraft.util.Identifier.of("halloween","rapier")) ? GuardRiderState.Weapon.RAPIER
                : worker ? GuardRiderState.Weapon.TOOL : GuardRiderState.Weapon.BLADE;
        if(fight!=0)weapon=GuardRiderState.Weapon.EMPTY;
        extra.halloween$combat(weapon, villager.preferredHand == net.minecraft.util.Hand.OFF_HAND);
        extra.halloween$setCrossbow(weapon == GuardRiderState.Weapon.CROSSBOW);
        extra.halloween$setSwing(swing);
        extra.halloween$offhand().clear();
        var itemModels = MinecraftClient.getInstance().getItemModelManager();
        if (pose == GuardRiderState.Pose.GUARD && !villager.getOffHandStack().isEmpty()) {
            itemModels.updateForLivingEntity(extra.halloween$offhand(), villager.getOffHandStack(),
                    ItemDisplayContext.THIRD_PERSON_LEFT_HAND, villager);
        }
        if (fight!=0 || guard && pose != GuardRiderState.Pose.GUARD) state.itemRenderState.clear();
        else if (pose != GuardRiderState.Pose.CLASSIC) {
            itemModels.updateForLivingEntity(state.itemRenderState, villager.getMainHandStack(),
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, villager);
        }
    }
}
