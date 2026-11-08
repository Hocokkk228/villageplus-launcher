package dev.halloween;

import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.render.entity.state.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;

/** Opt-in checks run inside the actual client, with mixins applied. */
final class PlayerWeaponMotionChecks {
    static void verify() {
        try {
            Class.forName("net.minecraft.client.render.entity.PlayerEntityRenderer");
            Class.forName("net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer");
        } catch (ClassNotFoundException e) { throw new AssertionError(e); }
        for (boolean slim : new boolean[]{false, true}) {
            var root = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, slim), 64, 64).createModel();
            var model = new PlayerEntityModel(root, slim);
            var armor = new BipedEntityModel<PlayerEntityRenderState>(TexturedModelData.of(BipedEntityModel.getModelData(new Dilation(.5f), 0),64,32).createModel());
            var state = new PlayerEntityRenderState();
            state.age = 20;
            for (Arm hand : Arm.values()) {
                state.mainArm = state.preferredArm = hand;
                for (var item : new net.minecraft.item.Item[]{VillageItems.MUSKET, VillageItems.REVOLVER, VillageItems.RAPIER, VillageItems.SABRE}) {
                    state.rightHandItem = hand == Arm.RIGHT ? new ItemStack(item) : ItemStack.EMPTY;
                    state.leftHandItem = hand == Arm.LEFT ? new ItemStack(item) : ItemStack.EMPTY;
                    state.isUsingItem = true;
                    state.itemUseTime = 8;
                    state.handSwingProgress = 0;
                    ((PlayerWeaponState)state).halloween$recoil(0);
                    model.setAngles(state);
                    var main = hand == Arm.RIGHT ? model.rightArm : model.leftArm;
                    float heldPitch = main.pitch;
                    if (PlayerWeaponMotion.firearm(PlayerWeaponMotion.weapon(new ItemStack(item)))) {
                        ((PlayerWeaponState)state).halloween$recoil(1);
                        model.setAngles(state);
                        if (main.pitch >= heldPitch - .1f) throw new AssertionError("Missing recoil: " + item + " " + hand);
                    } else {
                        state.handSwingProgress = .48f;
                        model.setAngles(state);
                        if (Math.abs(main.pitch - heldPitch) < .2f) throw new AssertionError("Missing blade strike: " + item + " " + hand);
                    }
                    armor.setAngles(state);
                    var armored = hand == Arm.RIGHT ? armor.rightArm : armor.leftArm;
                    var sleeve = hand == Arm.RIGHT ? model.rightSleeve : model.leftSleeve;
                    if (Math.abs(main.pitch - armored.pitch) > .0001f || Math.abs(main.yaw - armored.yaw) > .0001f
                            || Math.abs(sleeve.pitch) > .0001f || Math.abs(sleeve.yaw) > .0001f
                            || !main.hasChild(hand == Arm.RIGHT ? "right_sleeve" : "left_sleeve"))
                        throw new AssertionError("Player, armor and sleeve desync: " + item + " " + hand
                                + " main=" + main.getTransform() + " armor=" + armored.getTransform() + " sleeve=" + sleeve.getTransform());
                    var previous = main.getTransform();
                    model.setAngles(state);
                    if (!main.getTransform().equals(previous)) throw new AssertionError("Player pose accumulates offsets");
                }
            }
            state.mainArm = state.preferredArm = Arm.RIGHT;
            state.rightHandItem = new ItemStack(Items.STICK);
            state.leftHandItem = ItemStack.EMPTY;
            state.handSwingProgress = 0;
            state.isUsingItem = false;
            ((PlayerWeaponState)state).halloween$recoil(1);
            model.setAngles(state);
            if (model.rightArm.pitch < -.3f) throw new AssertionError("Gun pose leaked to vanilla item");
        }
    }
    private PlayerWeaponMotionChecks() {}
}
