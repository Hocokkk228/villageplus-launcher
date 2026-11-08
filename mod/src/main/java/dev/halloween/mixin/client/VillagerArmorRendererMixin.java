package dev.halloween.mixin.client;
import dev.halloween.VillagerArmorState;
import dev.halloween.VillagerArmorFeature;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.render.entity.state.VillagerEntityRenderState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(VillagerEntityRenderer.class)
public abstract class VillagerArmorRendererMixin extends LivingEntityRenderer<VillagerEntity,VillagerEntityRenderState,VillagerResemblingModel> {
 protected VillagerArmorRendererMixin(EntityRendererFactory.Context c,VillagerResemblingModel m,float f){super(c,m,f);}
 @Inject(method="<init>",at=@At("TAIL")) private void halloween$addArmor(EntityRendererFactory.Context context,CallbackInfo ci){this.addFeature(new VillagerArmorFeature(this));this.addFeature(new dev.halloween.VillagerWoundFeature(this));}
 @Inject(method="updateRenderState(Lnet/minecraft/entity/passive/VillagerEntity;Lnet/minecraft/client/render/entity/state/VillagerEntityRenderState;F)V",at=@At("TAIL"))
 private void halloween$copyArmor(VillagerEntity entity,VillagerEntityRenderState state,float delta,CallbackInfo ci){float fraction=entity.getHealth()/entity.getMaxHealth();int score=dev.halloween.FightPose.of(entity).halloween$injury();
 int cumulative=score>=8?3:score>=4?2:score>0?1:0;
 ((VillagerArmorState)state).halloween$wounds(Math.max(cumulative,fraction>.9f?0:fraction>.65f?1:fraction>.35f?2:3));for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET})((VillagerArmorState)state).halloween$setArmor(slot,entity.getEquippedStack(slot));}
}
