package dev.halloween;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;

/** Poses operate on the same biped bones used by skin layers and armor. */
public final class PlayerWeaponMotion {
    public enum Weapon { NONE, MUSKET, REVOLVER, RAPIER, SABRE }
    private static final Map<Entity, Long> SHOTS = new WeakHashMap<>();

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(WeaponShotNetwork.Shot.ID, (packet, context) ->
            context.client().execute(() -> {
                if (context.client().world == null) return;
                for (var entity : context.client().world.getPlayers()) {
                    if (entity.getUuid().equals(packet.shooter())) {
                        SHOTS.put(entity, context.client().world.getTime());
                        break;
                    }
                }
            }));
    }

    public static float recoil(Entity player, float delta) {
        Long fired = SHOTS.get(player);
        if (fired == null) return 0;
        float elapsed = player.getEntityWorld().getTime() - fired + delta;
        if (elapsed < 0 || elapsed > 9) { SHOTS.remove(player); return 0; }
        return (float)(Math.exp(-elapsed * .44) * Math.sin(Math.min(elapsed / 1.2, 1) * Math.PI * .5));
    }

    public static Weapon weapon(ItemStack stack) {
        if (stack.isOf(VillageItems.MUSKET)) return Weapon.MUSKET;
        if (stack.isOf(VillageItems.REVOLVER)) return Weapon.REVOLVER;
        if (stack.isOf(VillageItems.RAPIER)) return Weapon.RAPIER;
        if (stack.isOf(VillageItems.SABRE)) return Weapon.SABRE;
        return Weapon.NONE;
    }

    public static boolean firearm(Weapon weapon) { return weapon == Weapon.MUSKET || weapon == Weapon.REVOLVER; }

    public static void apply(BipedEntityModel<?> model, BipedEntityRenderState state) {
        if (!(state instanceof PlayerEntityRenderState) || state.isSwimming || state.isGliding
                || state.sleepingDirection != null) return;
        var client = net.minecraft.client.MinecraftClient.getInstance();
        Entity entity = client.world == null ? null : client.world.getEntityById(((PlayerEntityRenderState)state).id);
        if (entity != null && entity.getVehicle() instanceof RailSeatEntity seat && seat.controls()
                && seat.train() != null && seat.train().isShip()) return;
        boolean left = state.mainArm == Arm.LEFT;
        boolean offhandEmpty = (left ? state.rightHandItem : state.leftHandItem).isEmpty();
        Weapon weapon = weapon(left ? state.leftHandItem : state.rightHandItem);
        if (weapon == Weapon.NONE) return;
        ModelPart main = left ? model.leftArm : model.rightArm;
        ModelPart off = left ? model.rightArm : model.leftArm;
        float side = left ? -1 : 1;
        float pitch = MathHelper.clamp(state.pitch * MathHelper.RADIANS_PER_DEGREE, -.9f, .9f);
        if (firearm(weapon)) {
            float aim = state.isUsingItem ? MathHelper.clamp(state.itemUseTime / 5f, 0, 1) : 0;
            float kick = ((PlayerWeaponState)state).halloween$recoil();
            boolean longGun = weapon == Weapon.MUSKET;
            main.pitch = -1.34f - aim * .23f + pitch * (.55f + aim * .45f) - kick * (longGun ? .20f : .48f);
            main.yaw = side * (longGun ? -.22f : -.10f);
            main.roll = side * .025f;
            if (longGun && offhandEmpty) {
                off.pitch = -1.42f - aim * .15f + pitch * (.55f + aim * .45f) - kick * .13f;
                off.yaw = side * .48f;
                off.roll = side * -.10f;
            }
            model.body.yaw = side * (longGun ? -.10f : -.035f);
            model.body.pitch -= kick * .06f;
        } else if (state.handSwingProgress > 0 && state.preferredArm == state.mainArm) {
            var strike = VillagerMotion.strike(state.handSwingProgress);
            boolean thrust = weapon == Weapon.RAPIER;
            model.body.yaw = side * (strike.windup() * .24f - strike.hit() * .36f);
            main.pitch = -.30f - strike.windup() * .90f - strike.hit() * (thrust ? 1.12f : .65f);
            main.yaw = side * (-.12f - strike.windup() * (thrust ? .25f : .72f)
                    + strike.hit() * (thrust ? .12f : 1.03f));
            main.roll = side * (strike.windup() * .28f - strike.hit() * .23f);
            if (offhandEmpty) {
                off.pitch = -.55f + strike.hit() * .20f;
                off.yaw = side * .25f;
                off.roll = side * -.14f;
            }
        }
    }

    private PlayerWeaponMotion() {}
}
