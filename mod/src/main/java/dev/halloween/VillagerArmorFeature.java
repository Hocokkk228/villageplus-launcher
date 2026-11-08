package dev.halloween;
import dev.halloween.VillagerArmorState;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.render.entity.state.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Player armor geometry with a villager-sized head and folded sleeves. */
public final class VillagerArmorFeature extends FeatureRenderer<VillagerEntityRenderState,VillagerResemblingModel> {
 private static final class ArmorPose extends BipedEntityRenderState {
  ModelTransform headPose,bodyPose,leftLegPose,rightLegPose,leftArmPose,rightArmPose,leftElbowPose,rightElbowPose;
  EquipmentSlot slot;
 }
 private static final class SyncedArmorModel extends BipedEntityModel<BipedEntityRenderState> {
  SyncedArmorModel(ModelPart root){super(root);}
  @Override public void setAngles(BipedEntityRenderState state){
   if(!(state instanceof ArmorPose p)){super.setAngles(state);return;}
   resetTransforms();head.setTransform(p.headPose);hat.setTransform(p.headPose);body.setTransform(p.bodyPose);
   leftLeg.setTransform(p.leftLegPose);rightLeg.setTransform(p.rightLegPose);leftArm.setTransform(p.leftArmPose);rightArm.setTransform(p.rightArmPose);
   if(leftArm.hasChild("forearm")){
    leftArm.getChild("forearm").setTransform(p.leftElbowPose==null?ModelTransform.origin(0,3,0):p.leftElbowPose);
    rightArm.getChild("forearm").setTransform(p.rightElbowPose==null?ModelTransform.origin(0,3,0):p.rightElbowPose);
   }
   setVisible(false);
   switch(p.slot){
    case HEAD->{head.visible=true;hat.visible=true;head.yScale*=1.23f;hat.yScale*=1.23f;}
    case CHEST->{body.visible=true;leftArm.visible=true;rightArm.visible=true;}
    case LEGS,FEET->{leftLeg.visible=true;rightLeg.visible=true;}
    default->{}
   }
  }
 }
 private final BipedEntityModel<BipedEntityRenderState> helmet,chest,legs,boots;
 public VillagerArmorFeature(FeatureRendererContext<VillagerEntityRenderState,VillagerResemblingModel> context){
  super(context);helmet=model(.8f);chest=model(.55f,.55f,1.65f);legs=model(.5f,.5f,1.65f);boots=model(.7f);
 }
 private static BipedEntityModel<BipedEntityRenderState> model(float dilation){return model(dilation,dilation,dilation);}
 private static BipedEntityModel<BipedEntityRenderState> model(float x,float y,float z){
  var data=BipedEntityModel.getModelData(new Dilation(x,y,z),0);
  var root=data.getRoot();
  var padding=new Dilation(x,y,z);
  for(boolean left:new boolean[]{false,true}){
   float armX=left?-1.25f:-2.75f;
   var arm=root.addChild(left?"left_arm":"right_arm",ModelPartBuilder.create().uv(40,16).mirrored(left)
       .cuboid(armX,-2,-2,4,5,4,padding),ModelTransform.origin(left?5:-5,2,0));
   arm.addChild("forearm",ModelPartBuilder.create().uv(40,21).mirrored(left)
       .cuboid(armX,0,-2,4,3,4,padding),ModelTransform.origin(0,3,0));
  }
  return new SyncedArmorModel(TexturedModelData.of(data,64,32).createModel());
 }
 static void verifyWalkingSync(){
  var root=TexturedModelData.of(VillagerResemblingModel.getModelData(),64,64).createModel();var villager=new VillagerResemblingModel(root);
  var state=new VillagerEntityRenderState();state.limbSwingAmplitude=1f;state.limbSwingAnimationProgress=1.2f;
  var motion = (GuardRiderState)state;
  motion.halloween$setGuard(true);
  motion.halloween$pose(GuardRiderState.Pose.CASUAL, .3f, 0, 1);
  var armor=model(.5f);var pose=new ArmorPose();pose.slot=EquipmentSlot.LEGS;
  for(float phase:new float[]{.2f,1.2f,2.2f}){
   state.limbSwingAnimationProgress=phase;villager.setAngles(state);
   pose.headPose=villager.getHead().getTransform();pose.bodyPose=root.getChild("body").getTransform();
   pose.leftLegPose=root.getChild("left_leg").getTransform();pose.rightLegPose=root.getChild("right_leg").getTransform();
   pose.leftArmPose=ModelTransform.NONE;pose.rightArmPose=ModelTransform.NONE;armor.setAngles(pose);
   if(Math.abs(armor.leftLeg.pitch-root.getChild("left_leg").pitch)>0.00001f||Math.abs(armor.rightLeg.pitch-root.getChild("right_leg").pitch)>0.00001f)throw new AssertionError("armor legs differ from villager animation");
  }
  if(Math.abs(root.getChild("right_leg").pitch)<.001f)throw new AssertionError("villager walking animation disabled");
  for(String side:new String[]{"right","left"}){
   var actualArm=root.getChild("halloween_"+side+"_arm");
   if(!actualArm.hasChild("forearm")||!actualArm.getChild("forearm").hasChild("hand"))throw new AssertionError("missing independent hand");
   int[] faces={0};actualArm.getChild("forearm").getChild("hand").forEachCuboid(new MatrixStack(),(entry,path,index,cube)->{
    faces[0]+=cube.sides.length;
    for(var face:cube.sides)for(var vertex:face.vertices())if(vertex.u()<9.5f/64||vertex.u()>12.5f/64||vertex.v()<8.5f/64||vertex.v()>10.5f/64)throw new AssertionError("hand samples sleeve/shoulder atlas");
   });
   if(faces[0]!=6)throw new AssertionError("hand has missing faces");
  }
  var body = root.getChild("body");
  var arm = root.getChild("halloween_right_arm");
  motion.halloween$pose(GuardRiderState.Pose.STREET, 0, 0, 1);
  motion.halloween$setSwing(.22f);
  villager.setAngles(state);
  float windup = body.yaw;
  var start = new MatrixStack();
  if (!((VillagerGrip)villager).halloween$grip(start, false)) throw new AssertionError("sword lost its grip during windup");
  motion.halloween$setSwing(.48f);
  villager.setAngles(state);
  var hit = new MatrixStack();
  if (!((VillagerGrip)villager).halloween$grip(hit, false)
      || start.peek().getPositionMatrix().equals(hit.peek().getPositionMatrix())
      || Math.abs(windup - body.yaw) < .35f) throw new AssertionError("strike does not turn the torso and weapon");
  var poseAfter = arm.getTransform();
  villager.setAngles(state);
  if (!poseAfter.equals(arm.getTransform())) throw new AssertionError("reused model accumulates pose offsets");
  var left = root.getChild("halloween_left_arm");
  float rightHit = arm.pitch, rightTurn = body.yaw;
  motion.halloween$combat(GuardRiderState.Weapon.BLADE, true);
  villager.setAngles(state);
  if (left.pitch >= arm.pitch || Math.abs(body.yaw - rightTurn) < .25f || arm.pitch <= rightHit)
      throw new AssertionError("offhand strike did not switch arm and torso");
  motion.halloween$combat(GuardRiderState.Weapon.MUSKET, false);
  motion.halloween$setSwing(0);
  villager.setAngles(state);
  float aim = arm.pitch;
  var firearm = new MatrixStack();
  ((VillagerGrip)villager).halloween$grip(firearm, false);
  motion.halloween$setSwing(.19f);
  villager.setAngles(state);
  var recoil = new MatrixStack();
  if (arm.pitch >= aim || !((VillagerGrip)villager).halloween$grip(recoil, false)
          || firearm.peek().getPositionMatrix().equals(recoil.peek().getPositionMatrix()))
      throw new AssertionError("musket recoil not synced with weapon grip");

  motion.halloween$setGuard(false);
  motion.halloween$setSeatedPassenger(true);
  motion.halloween$pose(GuardRiderState.Pose.CLASSIC, 0, 0, 1);
  villager.setAngles(state);
  if (arm.visible || root.getChild("right_leg").pitch > -1f || Math.abs(body.yaw) > .001f)
      throw new AssertionError("combat pose leaked into a seated passenger");
 }
 @Override public void render(MatrixStack matrices,OrderedRenderCommandQueue queue,int light,VillagerEntityRenderState state,float limbAngle,float limbDistance){
  if(state.baby||state.invisibleToPlayer)return;
  var actual=(VillagerArmorState)state;
  var villager=getContextModel();villager.setAngles(state);var root=villager.getRootPart();
  for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){
   var stack=actual.halloween$armor(slot);if(stack.isEmpty())continue;
   String id=Registries.ITEM.getId(stack.getItem()).getPath();String type=null;
   for(String material:new String[]{"leather","chainmail","iron","golden","diamond","netherite","copper"})if(id.startsWith(material+"_")){type=material.equals("golden")?"gold":material;break;}
   if(type==null||!(id.endsWith("_helmet")||id.endsWith("_chestplate")||id.endsWith("_leggings")||id.endsWith("_boots")))continue;
   boolean leggings=slot==EquipmentSlot.LEGS;var model=switch(slot){case HEAD->helmet;case CHEST->chest;case LEGS->legs;default->boots;};var pose=new ArmorPose();pose.slot=slot;
   pose.headPose=villager.getHead().getTransform();pose.bodyPose=root.getChild("body").getTransform();
   pose.leftLegPose=root.getChild("left_leg").getTransform();pose.rightLegPose=root.getChild("right_leg").getTransform();
   boolean active=root.hasChild("halloween_right_arm")&&root.getChild("halloween_right_arm").visible;
   pose.rightArmPose=active?root.getChild("halloween_right_arm").getTransform():ModelTransform.of(-5,3,-1,-.75f,0,0);
   pose.leftArmPose=active?root.getChild("halloween_left_arm").getTransform():ModelTransform.of(5,3,-1,-.75f,0,0);
   pose.rightElbowPose=active?root.getChild("halloween_right_arm").getChild("forearm").getTransform():null;
   pose.leftElbowPose=active?root.getChild("halloween_left_arm").getChild("forearm").getTransform():null;
   pose.age=state.age;model.setAngles(pose);
   Identifier texture=Identifier.of("minecraft","textures/entity/equipment/"+(leggings?"humanoid_leggings/":"humanoid/")+type+".png");
   int color=type.equals("leather")?0xff000000|net.minecraft.component.type.DyedColorComponent.getColor(stack,net.minecraft.component.type.DyedColorComponent.DEFAULT_COLOR):0xffffffff;
   render(model,texture,matrices,queue,light,pose,color,0);
   if(type.equals("leather"))render(model,Identifier.of("minecraft","textures/entity/equipment/"+(leggings?"humanoid_leggings/":"humanoid/")+"leather_overlay.png"),matrices,queue,light,pose,0xffffffff,1);
  }
 }
}

