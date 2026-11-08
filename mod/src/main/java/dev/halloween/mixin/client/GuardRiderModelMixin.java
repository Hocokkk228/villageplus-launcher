package dev.halloween.mixin.client;

import dev.halloween.GuardRiderState;
import dev.halloween.FightPose;
import dev.halloween.VillagerMotion;
import dev.halloween.VillagerGrip;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.render.entity.state.VillagerEntityRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerResemblingModel.class)
public abstract class GuardRiderModelMixin implements VillagerGrip {
    @Shadow @Final private ModelPart rightLeg;
    @Shadow @Final private ModelPart leftLeg;
    @Shadow @Final private ModelPart arms;
    @Shadow @Final private ModelPart head;
    @Unique private ModelPart halloween$rightArm,halloween$leftArm,halloween$body,halloween$root;
    @Unique private GuardRiderState.Weapon halloween$gripType = GuardRiderState.Weapon.EMPTY;
    @Unique private static final net.minecraft.client.render.entity.model.ZombieVillagerEntityModel<net.minecraft.client.render.entity.state.ZombieVillagerRenderState> halloween$zombieModel=
        new net.minecraft.client.render.entity.model.ZombieVillagerEntityModel<>(net.minecraft.client.render.entity.model.ZombieVillagerEntityModel.getTexturedModelData().createModel());
    @Unique private static final net.minecraft.client.render.entity.state.ZombieVillagerRenderState halloween$zombieState=new net.minecraft.client.render.entity.state.ZombieVillagerRenderState();
    @Inject(method="getModelData",at=@At("RETURN"))
    private static void halloween$armGeometry(CallbackInfoReturnable<ModelData> cir){
        var root=cir.getReturnValue().getRoot();
        for (boolean left : new boolean[]{false,true}) {
            float x=left?-1.25f:-2.75f;
            var arm=root.addChild(left?"halloween_left_arm":"halloween_right_arm", ModelPartBuilder.create()
                    .uv(22,20).mirrored(left).cuboid(x,-2,-2,4,5,4), ModelTransform.origin(left?5f:-5f,2,0));
            var elbow=arm.addChild("forearm",ModelPartBuilder.create()
                    .uv(22,20).mirrored(left).cuboid(x,0,-2,4,3,4),ModelTransform.origin(0,3,0));
            elbow.addChild("hand", ModelPartBuilder.create().uv(24,0).mirrored(left).cuboid(x,3,-2,4,4,4), ModelTransform.NONE);
        }
    }
    @Inject(method="<init>",at=@At("RETURN"))
    private void halloween$armParts(ModelPart root,CallbackInfo ci){
        halloween$root = root;
        if (root.hasChild("body")) halloween$body = root.getChild("body");
        if(root.hasChild("halloween_right_arm"))halloween$rightArm=root.getChild("halloween_right_arm");
        if(root.hasChild("halloween_left_arm"))halloween$leftArm=root.getChild("halloween_left_arm");
        if(halloween$rightArm!=null)halloween$mapArm(halloween$rightArm);
        if(halloween$leftArm!=null)halloween$mapArm(halloween$leftArm);
    }
    @Unique private static void halloween$mapArm(ModelPart arm) {
        arm.forEachCuboid(new MatrixStack(), (entry,path,index,cube)-> {
            boolean hand=path.endsWith("/hand");
            boolean forearm=path.endsWith("/forearm");
            for(int i=0;i<cube.sides.length;i++) {
                var face=cube.sides[i];var old=face.vertices();
                float minU=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY,minV=minU,maxV=maxU;
                for(var v:old){minU=Math.min(minU,v.u());maxU=Math.max(maxU,v.u());minV=Math.min(minV,v.v());maxV=Math.max(maxV,v.v());}
                // Body fabric on the upper arm; the opaque face pixels supply skin for every hand face.
                float u=hand?9.5f:30.5f,v=hand?8.5f:31.5f;
                boolean cap=Math.abs(face.direction().y())>.5f;
                float h=hand?2:cap?2:5;
                var mapped=new ModelPart.Vertex[4];
                for(int j=0;j<4;j++){
                    float horizontal=(old[j].u()-minU)/Math.max(.0001f,maxU-minU);
                    float vertical=cap?(old[j].v()-minV)/Math.max(.0001f,maxV-minV)
                            :hand?(old[j].y()-3)/4:(old[j].y()+(forearm?5:2))/8;
                    mapped[j]=old[j].remap((u+horizontal*3)/64,(v+vertical*h)/64);
                }
                cube.sides[i]=new ModelPart.Quad(mapped,face.direction());
            }
        });
    }
    @Inject(method="setAngles(Lnet/minecraft/client/render/entity/state/VillagerEntityRenderState;)V",at=@At("TAIL"))
    private void halloween$seatedPose(VillagerEntityRenderState state,CallbackInfo ci){
        var guard=(GuardRiderState)state;
        halloween$gripType = guard.halloween$weapon();
        if (halloween$body != null && halloween$body.hasChild("jacket")) {
            var armor = (dev.halloween.VillagerArmorState)state;
            halloween$body.getChild("jacket").visible = armor.halloween$armor(net.minecraft.entity.EquipmentSlot.LEGS).isEmpty();
        }
        if (halloween$rightArm != null && halloween$leftArm != null) {
            boolean active = guard.halloween$isGuard();
            arms.visible = !active;
            halloween$rightArm.visible = active;
            halloween$leftArm.visible = active;
            if (active && !guard.halloween$isMountedGuard()) halloween$groundMotion(state, guard);
        }
        if(guard.halloween$isMountedGuard()&&halloween$rightArm!=null&&halloween$leftArm!=null){
            var z=halloween$zombieState;z.hasVehicle=true;z.attacking=guard.halloween$swing()>0;z.handSwingProgress=guard.halloween$swing();
            z.limbSwingAnimationProgress=state.limbSwingAnimationProgress;z.limbSwingAmplitude=state.limbSwingAmplitude;z.age=state.age;
            z.relativeHeadYaw=state.relativeHeadYaw;z.pitch=state.pitch;z.mainArm=net.minecraft.util.Arm.RIGHT;
            z.rightHandItem=new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_SPEAR);
            z.rightArmPose=net.minecraft.client.render.entity.model.BipedEntityModel.ArmPose.SPEAR;
            z.leftArmPose=net.minecraft.client.render.entity.model.BipedEntityModel.ArmPose.EMPTY;
            halloween$zombieModel.setAngles(z);
            halloween$copyRotation(rightLeg,halloween$zombieModel.rightLeg);halloween$copyRotation(leftLeg,halloween$zombieModel.leftLeg);
            halloween$copyRotation(halloween$rightArm,halloween$zombieModel.rightArm);halloween$copyRotation(halloween$leftArm,halloween$zombieModel.leftArm);
        }
        if(guard.halloween$isSeatedPassenger()&&guard.halloween$pose()==dev.halloween.GuardRiderState.Pose.WORK&&halloween$rightArm!=null){halloween$rightArm.pitch=-1.05f;halloween$leftArm.pitch=-1.05f;halloween$rightArm.yaw=-.20f;halloween$leftArm.yaw=.20f;halloween$rightArm.roll=.08f;halloween$leftArm.roll=-.08f;}
        if(guard.halloween$isSeatedPassenger()){rightLeg.pitch=-1.4137167f;leftLeg.pitch=-1.4137167f;rightLeg.yaw=.20f;leftLeg.yaw=-.20f;rightLeg.roll=.04f;leftLeg.roll=-.04f;}
    }
    @Unique private void halloween$groundMotion(VillagerEntityRenderState state, GuardRiderState render) {
        var gait = VillagerMotion.gait(state.limbSwingAnimationProgress, state.limbSwingAmplitude, render.halloween$turn());
        var strike = VillagerMotion.strike(render.halloween$swing());
        float breath = (float)Math.sin(state.age * .105f + render.halloween$seed());
        float arm = gait.step() * gait.stride();
        float hurt = render.halloween$hurt();
        float lean = gait.forwardLean() - hurt * .18f;
        float twist = gait.twist();
        float roll = gait.turnLean() + hurt * .12f;
        float bob = gait.bob() + breath * .13f;
        float foot = gait.stride() * .65f;
        rightLeg.pitch = arm;
        leftLeg.pitch = -arm;
        rightLeg.originY -= Math.max(0, -gait.step()) * foot;
        leftLeg.originY -= Math.max(0, gait.step()) * foot;
        halloween$rightArm.setAngles(-.10f - arm * .84f + breath * .025f, -.09f, .09f);
        halloween$leftArm.setAngles(-.16f + arm * .77f - breath * .025f, .08f, -.10f);

        if(render.halloween$fightAction()!=FightPose.NONE){
            float age=render.halloween$fightFrame();
            int action=render.halloween$fightAction();
            boolean left=action==FightPose.LEFT;
            float side=left?-1:1;
            ModelPart attack=left?halloween$leftArm:halloween$rightArm;
            ModelPart cover=left?halloween$rightArm:halloween$leftArm;
            attack.setAngles(-.58f,-side*.24f,side*.16f);
            cover.setAngles(-.62f,side*.27f,-side*.16f);
            attack.getChild("forearm").pitch=-1.22f;
            cover.getChild("forearm").pitch=-1.34f;
            lean+=.13f;
            twist+=.14f;
            rightLeg.pitch=arm-.14f;
            leftLeg.pitch=-arm+.17f;
            rightLeg.yaw=-.14f;
            leftLeg.yaw=.20f;
            if(action==FightPose.LEFT||action==FightPose.RIGHT){
                float hit=halloween$pulse(age,2,5,13);
                float load=halloween$pulse(age,0,2,5);
                attack.pitch+=.34f*load-1.03f*hit;
                attack.yaw+=side*(-.35f*load+.42f*hit);
                attack.getChild("forearm").pitch+=1.13f*hit;
                twist+=side*(.44f*load-.66f*hit);
                lean+=.24f*hit;
                bob+=.45f*hit;
            }else if(action==FightPose.BLOCK){
                float guard=halloween$pulse(age,0,2,9);
                halloween$rightArm.pitch-=.42f*guard;
                halloween$leftArm.pitch-=.42f*guard;
                halloween$rightArm.yaw-=.34f*guard;
                halloween$leftArm.yaw+=.34f*guard;
                lean-=.17f*guard;
            }else if(action==FightPose.KICK){
                float raise=halloween$pulse(age,0,5,8);
                float stomp=halloween$pulse(age,5,8,20);
                rightLeg.pitch=-1.59f*raise-.74f*stomp;
                rightLeg.originY-=1.4f*raise;
                leftLeg.pitch=.25f*raise+.16f*stomp;
                halloween$rightArm.pitch+=.95f*raise+.44f*stomp;
                halloween$leftArm.pitch-=.23f*stomp;
                lean+=-.24f*raise+.37f*stomp;
                roll+=.20f*raise;
                twist-=.31f*stomp;
                bob+=.8f*stomp;
            }
        }else switch (render.halloween$pose()) {
            case STREET, GUARD -> {
                var weapon = render.halloween$weapon();
                boolean musket = weapon == GuardRiderState.Weapon.MUSKET;
                boolean revolver = weapon == GuardRiderState.Weapon.REVOLVER;
                if (musket || revolver || render.halloween$isCrossbow()) {
                    float swing = render.halloween$swing();
                    float recoil = (float)Math.sin(Math.PI * Math.min(swing / .42f, 1)) * (1 - swing * .5f);
                    float recovery = (float)Math.sin(Math.PI * Math.max(0, (swing - .36f) / .64f));
                    float aim = Math.max(-.35f, Math.min(.35f, state.pitch * .012f));
                    halloween$rightArm.setAngles(-1.49f + aim - recoil * (revolver ? .55f : .24f) + recovery * .13f,
                            revolver ? -.13f : -.26f, recoil * .05f);
                    halloween$leftArm.setAngles(revolver ? -.77f : -1.55f + aim - recoil * .15f + recovery * .42f,
                            revolver ? .20f : .37f, -.1f);
                    twist += revolver ? -.07f : -.14f;
                    lean += .07f - recoil * .12f;
                } else {
                    boolean left = render.halloween$leftStrike();
                    ModelPart attack = left ? halloween$leftArm : halloween$rightArm;
                    ModelPart cover = left ? halloween$rightArm : halloween$leftArm;
                    float side = left ? -1 : 1;
                    float weight = weapon == GuardRiderState.Weapon.EMPTY || left ? .76f : 1;
                    attack.setAngles(-.55f - side * arm * .34f + strike.windup() * .75f - strike.hit() * 1.37f * weight,
                            side * (-.2f - strike.windup() * .72f + strike.hit() * .96f),
                            side * (.13f + strike.windup() * .29f - strike.hit() * .19f));
                    cover.setAngles(-.78f + side * arm * .21f + strike.hit() * .3f, side * .28f, -side * .2f);
                    lean += .14f - strike.windup() * .09f + strike.hit() * .18f;
                    twist += side * (strike.windup() * .31f - strike.hit() * .43f);
                    roll += side * (.05f - strike.hit() * .13f);
                    bob += .5f * strike.windup() + .6f * strike.recover();
                }
            }
            case WORK -> {
                halloween$rightArm.pitch -= 1.05f * strike.windup() + .6f * strike.hit();
                halloween$rightArm.yaw -= .26f * strike.windup();
                halloween$leftArm.pitch -= .25f + .3f * strike.hit();
                lean += .09f + .26f * strike.hit();
                twist += .11f * strike.windup() - .12f * strike.hit();
            }
            case INTERACT -> {
                float reach=(float)Math.sin(Math.PI*render.halloween$swing());
                halloween$leftArm.setAngles(-.15f-reach*1.25f,.1f, -.04f);
                lean+=reach*.09f;
            }
            case SWIM -> {
                float paddle = (float)Math.sin(state.age * .31f + render.halloween$seed());
                halloween$rightArm.setAngles(-1.05f + paddle * .68f, -.22f, .29f);
                halloween$leftArm.setAngles(-1.05f - paddle * .68f, .22f, -.29f);
                rightLeg.pitch = paddle * .24f;
                leftLeg.pitch = -rightLeg.pitch;
                lean = .23f;
                twist *= .3f;
            }
            default -> {
                halloween$leftArm.pitch -= hurt * .22f;
                halloween$rightArm.pitch -= hurt * .25f;
            }
        }

        var torso = new org.joml.Quaternionf().rotationZYX(roll, twist, lean);
        if (halloween$body != null) halloween$followTorso(halloween$body, torso, bob, lean, twist, roll);
        halloween$followTorso(halloween$rightArm, torso, bob, lean, twist, roll);
        halloween$followTorso(halloween$leftArm, torso, bob, lean, twist, roll);
        // The head follows shoulder displacement while preserving most of the look direction.
        halloween$followTorso(head, torso, bob, lean * .37f, twist * .23f, roll * .55f);
    }

    @Unique private static float halloween$pulse(float age,float start,float peak,float end){
        float t=net.minecraft.util.math.MathHelper.clamp(age<peak?(age-start)/(peak-start):(end-age)/(end-peak),0,1);
        return t*t*(3-2*t);
    }

    @Unique private static void halloween$followTorso(ModelPart part, org.joml.Quaternionf rotation,
                                                       float bob, float pitch, float yaw, float roll) {
        var pivot = rotation.transform(new org.joml.Vector3f(part.originX, part.originY - 11f, part.originZ));
        part.setOrigin(pivot.x, pivot.y + 11f + bob, pivot.z);
        part.pitch += pitch;
        part.yaw += yaw;
        part.roll += roll;
    }

    public boolean halloween$grip(MatrixStack matrices, boolean left) {
        ModelPart arm = left ? halloween$leftArm : halloween$rightArm;
        if (arm == null || !arm.visible) return false;
        halloween$root.applyTransform(matrices);
        arm.applyTransform(matrices);
        arm.getChild("forearm").applyTransform(matrices);
        matrices.translate(0,-3/16f,0);
        float palm = left ? .0625f : -.0625f;
        if (!left && halloween$gripType == GuardRiderState.Weapon.MUSKET) {
            matrices.translate(palm, 1.068f, -.002f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
        } else if (!left && halloween$gripType == GuardRiderState.Weapon.REVOLVER) {
            matrices.translate(palm, .697f, -.134f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
            matrices.scale(.66f,.66f,.66f);
        } else if (!left && (halloween$gripType == GuardRiderState.Weapon.SABRE || halloween$gripType == GuardRiderState.Weapon.RAPIER)) {
            matrices.translate(palm, .59f, -.069f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-125));
        } else {
            // Vanilla item display transforms already include their own blade/tool rotation.
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            matrices.translate(-palm, .125f, -.625f);
        }
        return true;
    }

    @Unique private static void halloween$copyRotation(ModelPart into,ModelPart from){into.pitch=from.pitch;into.yaw=from.yaw;into.roll=from.roll;}
}
