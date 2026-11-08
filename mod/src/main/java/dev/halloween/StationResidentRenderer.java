package dev.halloween;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.VillagerEntityRenderState;
import net.minecraft.entity.passive.VillagerEntity;
public final class StationResidentRenderer extends VillagerEntityRenderer {
 public StationResidentRenderer(EntityRendererFactory.Context context){super(context);}
 @Override public void updateRenderState(VillagerEntity villager,VillagerEntityRenderState state,float delta){
  super.updateRenderState(villager,state,delta);
  state.pose=net.minecraft.entity.EntityPose.SLEEPING;state.sleepingDirection=net.minecraft.util.math.Direction.fromHorizontalDegrees(villager.getYaw()+90);state.limbSwingAmplitude=0;
 }
}
