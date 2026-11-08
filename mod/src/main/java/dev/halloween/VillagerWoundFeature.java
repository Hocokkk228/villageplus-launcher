package dev.halloween;

import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.render.entity.state.VillagerEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Same geometry as the clothing overlays; wound stages follow synchronized health. */
public final class VillagerWoundFeature extends FeatureRenderer<VillagerEntityRenderState,VillagerResemblingModel> {
    private static final Identifier[] TEXTURES={null,texture(1),texture(2),texture(3)};
    private static Identifier texture(int stage){return Identifier.of("halloween","textures/entity/villager/wounds_"+stage+".png");}
    public VillagerWoundFeature(FeatureRendererContext<VillagerEntityRenderState,VillagerResemblingModel> context){super(context);}
    @Override public void render(MatrixStack matrices,OrderedRenderCommandQueue queue,int light,VillagerEntityRenderState state,float yaw,float pitch){
        int wounds=((VillagerArmorState)state).halloween$wounds();
        if(wounds<1||state.invisibleToPlayer)return;
        render(getContextModel(),TEXTURES[Math.min(3,wounds)],matrices,queue,light,state,0xffffffff,1);
    }
}
