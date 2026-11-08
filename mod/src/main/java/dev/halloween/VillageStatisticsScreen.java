package dev.halloween;

import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/** Paged settlement register. Entries and actions are checked by the server. */
final class VillageStatisticsScreen extends Screen {
    private final List<String> pages;private List<String> visible=List.of();private int page,offset;private String query="",selectedId="",selectedText="";private TextFieldWidget search;
    VillageStatisticsScreen(String report){super(Text.literal("Статистика деревни"));pages=Arrays.asList(report.split("\f",-1));page=pages.size()>4?4:0;if(page==4){for(String line:pages.get(4).split("\n"))if(line.startsWith("v|")){var fields=line.split("\\|",3);if(fields.length==3){selectedId=fields[1];selectedText=fields[2];}break;}}}
    private void refresh(){clearAndInit();}
    @Override protected void init(){
        int w=Math.min(420,width-30),left=(width-w)/2,top=22,bottom=height-22;
        addDrawableChild(ButtonWidget.builder(Text.literal("◀"),b->{page=Math.floorMod(page-1,pages.size());offset=0;query="";selectedId="";refresh();}).dimensions(left+15,bottom-24,38,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▶"),b->{page=(page+1)%pages.size();offset=0;query="";selectedId="";refresh();}).dimensions(left+w-53,bottom-24,38,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("↑"),b->{offset=Math.max(0,offset-10);refresh();}).dimensions(left+w-47,top+43,27,18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("↓"),b->{offset=Math.min(Math.max(0,visible.size()-1),offset+10);refresh();}).dimensions(left+w-47,top+64,27,18).build());
        if(page>=2&&page<=3){search=new TextFieldWidget(textRenderer,left+18,top+40,w-104,18,Text.literal("Поиск"));search.setMaxLength(80);search.setText(query);addDrawableChild(search);
            addDrawableChild(ButtonWidget.builder(Text.literal("Найти"),b->{query=search.getText();offset=0;refresh();}).dimensions(left+w-81,top+40,48,18).build());}
        if(!selectedId.isEmpty()){
            addDrawableChild(ButtonWidget.builder(Text.literal("Растормошить"),b->{if(ClientPlayNetworking.canSend(VillageStatistics.Action.ID))ClientPlayNetworking.send(new VillageStatistics.Action("wake|"+selectedId));}).dimensions(left+20,top+105,150,20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Назад к списку"),b->{selectedId="";refresh();}).dimensions(left+20,top+130,150,20).build());return;
        }
        List<String> all=Arrays.asList(pages.get(page).split("\n"));String needle=query.toLowerCase(Locale.ROOT);
        visible=all.stream().filter(line->needle.isEmpty()||line.toLowerCase(Locale.ROOT).contains(needle)).toList();offset=Math.min(offset,Math.max(0,visible.size()-1));
        int firstY=top+(page>=2&&page<=3?68:44),capacity=Math.max(1,(bottom-firstY-30)/22);
        for(int i=offset;i<Math.min(visible.size(),offset+capacity);i++){
            String row=visible.get(i);int y=firstY+(i-offset)*22;
            if(row.startsWith("v|")||row.startsWith("h|")){String[] parts=row.split("\\|",3);if(parts.length<3)continue;String type=parts[0],id=parts[1],label=parts[2];
                String fitted=textRenderer.trimToWidth(label,w-115);
                addDrawableChild(ButtonWidget.builder(Text.literal((type.equals("h")?"Дом  ":"")+fitted),b->{if(type.equals("v")){selectedId=id;selectedText=label;refresh();}else if(ClientPlayNetworking.canSend(VillageStatistics.Action.ID))ClientPlayNetworking.send(new VillageStatistics.Action("home|"+id));}).dimensions(left+40,y,w-100,20).build());
            }
        }
    }
    @Override public boolean shouldPause(){return false;}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){offset=Math.max(0,Math.min(Math.max(0,visible.size()-1),offset-(int)vertical*3));refresh();return true;}
    @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta){
        draw.fill(0,0,width,height,0xbb0e171d);int w=Math.min(420,width-30),left=(width-w)/2,top=22,bottom=height-22;
        draw.fill(left-2,top-2,left+w+2,bottom+2,0xffa68448);draw.fill(left,top,left+w,bottom,0xff172e32);draw.fill(left+6,top+6,left+w-6,bottom-6,0xfff0e5c9);draw.fill(left+6,top+6,left+w-6,top+34,0xff244e4b);
        draw.drawTextWithShadow(textRenderer,Text.literal("§lРЕЕСТР ПОСЕЛЕНИЯ"),left+18,top+16,0xffffd37b);
        draw.drawCenteredTextWithShadow(textRenderer,Text.literal("Страница "+(page+1)+" / "+pages.size()),width/2,bottom-19,0xff626454);
        if(!selectedId.isEmpty()){draw.drawText(textRenderer,Text.literal("ЖИТЕЛЬ"),left+20,top+47,0xff754416,false);draw.drawText(textRenderer,Text.literal(textRenderer.trimToWidth(selectedText,w-40)),left+20,top+70,0xff4c493d,false);}
        else {int firstY=top+(page>=2&&page<=3?68:44),capacity=Math.max(1,(bottom-firstY-30)/22);
            draw.enableScissor(left+12,firstY,left+w-12,bottom-28);
            for(int i=offset;i<Math.min(visible.size(),offset+capacity);i++){String line=visible.get(i);int y=firstY+(i-offset)*22;if(line.startsWith("h|"))draw.drawItem(Items.STONE_BRICKS.getDefaultStack(),left+18,y+2);else if(!line.startsWith("v|"))draw.drawText(textRenderer,Text.literal(textRenderer.trimToWidth(line,w-80)),left+20,y+4,0xff4c493d,false);}
            draw.disableScissor();}
        super.render(draw,mouseX,mouseY,delta);
    }
}
