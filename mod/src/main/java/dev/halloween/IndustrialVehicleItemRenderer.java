package dev.halloween;
import com.mojang.serialization.*;
import java.util.function.Consumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.joml.Vector3f;
import org.joml.Vector3fc;
/** The inventory/held miniature uses the very same closed meshes and smooth wing polygons as the entity. */
final class IndustrialVehicleItemRenderer implements SimpleSpecialModelRenderer {
 record Unbaked(int kind) implements SpecialModelRenderer.Unbaked {
  static final MapCodec<Unbaked> CODEC=Codec.intRange(0,4).fieldOf("kind").xmap(Unbaked::new,Unbaked::kind);
  @Override public MapCodec<? extends SpecialModelRenderer.Unbaked> getCodec(){return CODEC;}
  @Override public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakeContext context){return new IndustrialVehicleItemRenderer(IndustrialMachines.Kind.values()[kind]);}
 }
 final IndustrialMachines.Kind kind;final Box bounds;final float scale;
 IndustrialVehicleItemRenderer(IndustrialMachines.Kind kind){this.kind=kind;Box b=null;for(var p:IndustrialVehicleGeometry.parts(kind)){var next=IndustrialVehicleGeometry.bounds(p);b=b==null?next:b.union(next);}bounds=b;scale=(float)(.88/Math.max(b.getLengthX(),Math.max(b.getLengthY(),b.getLengthZ())));}
 static void register(){SpecialModelTypes.ID_MAPPER.put(Identifier.of("halloween","industrial_vehicle_item"),Unbaked.CODEC);}
 @Override public void render(ItemDisplayContext context,MatrixStack matrices,OrderedRenderCommandQueue queue,int light,int overlay,boolean glint,int outline){matrices.push();matrices.translate(.5,.08,.5);matrices.scale(scale,scale,scale);matrices.translate(-(bounds.minX+bounds.maxX)/2,-bounds.minY,-(bounds.minZ+bounds.maxZ)/2);IndustrialVehicleRenderer.submit(kind,0,0,Integer.MAX_VALUE,matrices,queue,light);matrices.pop();}
 @Override public void collectVertices(Consumer<Vector3fc> consumer){for(double x:new double[]{bounds.minX,bounds.maxX})for(double y:new double[]{bounds.minY,bounds.maxY})for(double z:new double[]{bounds.minZ,bounds.maxZ})consumer.accept(new Vector3f((float)(.5+(x-(bounds.minX+bounds.maxX)/2)*scale),(float)(.08+(y-bounds.minY)*scale),(float)(.5+(z-(bounds.minZ+bounds.maxZ)/2)*scale)));}
}
