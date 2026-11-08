package dev.halloween;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.render.entity.state.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;

/** Developer-only export of actual model geometry and evaluated animation poses. */
final class PlayerWeaponPreviewExport {
    static void write() throws Exception {
        var output = new JsonObject();
        for (boolean slim : new boolean[]{false, true}) {
            var root = TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, slim),64,64).createModel();
            var model = new PlayerEntityModel(root,slim);
            var data = new JsonObject();var body=new JsonArray();
            collect(root,"root",null,body);data.add("body",body);
            var armor = new JsonArray();
            for(String type:new String[]{"helmet","chest","leggings","boots"}){
                var part=TexturedModelData.of(BipedEntityModel.getModelData(new Dilation(type.equals("leggings")?.25f:.5f),0),64,32).createModel();
                for(String node:switch(type){case "helmet"->new String[]{"head"};case "chest"->new String[]{"body","right_arm","left_arm"};default->new String[]{"right_leg","left_leg"};})
                    collect(part.getChild(node),type+":"+node,null,armor);
            }
            data.add("armor",armor);var clips=new JsonArray();
            for(String name:new String[]{"idle","walk","run","strike","rapier","musket","revolver"}){
                var clip=new JsonObject();clip.addProperty("name",name);clip.addProperty("ticks",name.equals("idle")?80:name.equals("walk")?24:name.equals("run")?16:12);
                var frames=new JsonArray();
                for(int frame=0;frame<=40;frame++){
                    float t=frame/40f;
                    var s=new PlayerEntityRenderState();s.age=t*clip.get("ticks").getAsFloat();s.mainArm=s.preferredArm=Arm.RIGHT;
                    s.hatVisible=s.jacketVisible=s.leftPantsLegVisible=s.rightPantsLegVisible=s.leftSleeveVisible=s.rightSleeveVisible=true;
                    s.rightHandItem=switch(name){case "musket"->new ItemStack(VillageItems.MUSKET);case "revolver"->new ItemStack(VillageItems.REVOLVER);case "rapier"->new ItemStack(VillageItems.RAPIER);case "strike"->new ItemStack(VillageItems.SABRE);default->ItemStack.EMPTY;};
                    if(name.equals("walk")||name.equals("run")){s.limbSwingAmplitude=name.equals("run")?1:.55f;s.limbSwingAnimationProgress=t*(float)Math.PI*2/.6662f;}
                    if(name.equals("strike")||name.equals("rapier"))s.handSwingProgress=t==1?0:t;
                    if(name.equals("musket")||name.equals("revolver")){
                        s.isUsingItem=true;s.itemUseTime=8;float elapsed=t*12;
                        ((PlayerWeaponState)s).halloween$recoil((float)(Math.exp(-elapsed*.44)*Math.sin(Math.min(elapsed/1.2,1)*Math.PI*.5)));
                    }
                    model.setAngles(s);var pose=new JsonObject();pose(root,"root",pose);frames.add(pose);
                }
                clip.add("frames",frames);clips.add(clip);
            }
            data.add("clips",clips);output.add(slim?"slim":"normal",data);
        }
        var villagerRoot=TexturedModelData.of(VillagerResemblingModel.getModelData(),64,64).createModel();
        new VillagerResemblingModel(villagerRoot);
        var arms=new JsonArray();
        for(String side:new String[]{"right","left"})collect(villagerRoot.getChild("halloween_"+side+"_arm"),"root/halloween_"+side+"_arm","root",arms);
        output.add("villagerArms",arms);
        Files.writeString(Path.of("player-preview.json"),new Gson().toJson(output));
    }
    @SuppressWarnings("unchecked") private static Map<String,ModelPart> children(ModelPart p)throws Exception{
        var f=ModelPart.class.getDeclaredField("children");f.setAccessible(true);return (Map<String,ModelPart>)f.get(p);
    }
    @SuppressWarnings("unchecked") private static void collect(ModelPart p,String name,String parent,JsonArray out)throws Exception{
        var row=new JsonObject();row.addProperty("part",name);if(parent!=null)row.addProperty("parent",parent);else row.add("parent",JsonNull.INSTANCE);
        row.add("transform",transform(p));var q=new JsonArray();var field=ModelPart.class.getDeclaredField("cuboids");field.setAccessible(true);
        for(var cube:(List<ModelPart.Cuboid>)field.get(p))for(var face:cube.sides)for(var v:face.vertices())for(float f:new float[]{v.x(),v.y(),v.z(),v.u(),v.v()})q.add(round(f));
        row.add("quads",q);out.add(row);
        for(var entry:children(p).entrySet())collect(entry.getValue(),name+"/"+entry.getKey(),name,out);
    }
    private static void pose(ModelPart p,String name,JsonObject out)throws Exception{
        out.add(name,transform(p));for(var e:children(p).entrySet())pose(e.getValue(),name+"/"+e.getKey(),out);
    }
    private static JsonArray transform(ModelPart p){var a=new JsonArray();for(float f:new float[]{p.originX,p.originY,p.originZ,p.pitch,p.yaw,p.roll,p.xScale,p.yScale,p.zScale,p.visible?1:0})a.add(round(f));return a;}
    private static double round(float f){return Math.round(f*10000d)/10000d;}
}
