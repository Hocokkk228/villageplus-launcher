package dev.halloween;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
/** Compact, scrollable world almanac with three tabs. */
final class WorldStatisticsScreen extends Screen {
 private final String[] pages;private int tab,offset;private List<String> rows=List.of();
 WorldStatisticsScreen(String text){super(Text.literal("Мировая статистика"));pages=text.split("\f",-1);}
 @Override public boolean shouldPause(){return false;}
 @Override protected void init(){int w=Math.min(620,width-24),x=(width-w)/2;String[] titles={"Развитие мира","Города","Розыск"};for(int i=0;i<3;i++){final int index=i;addDrawableChild(ButtonWidget.builder(Text.literal(titles[i]),b->{tab=index;offset=0;clearAndInit();}).dimensions(x+12+i*(w-24)/3,34,(w-30)/3,20).build());}rows=Arrays.asList(pages[Math.min(tab,pages.length-1)].split("\n"));}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){offset=Math.clamp(offset-(int)vertical*3,0,Math.max(0,rows.size()-(height-100)/13));return true;}
 @Override public void render(DrawContext d,int mx,int my,float delta){int w=Math.min(620,width-24),x=(width-w)/2;d.fill(0,0,width,height,0xc0101820);d.fill(x,12,x+w,height-14,0xff173b42);d.fill(x+3,58,x+w-3,height-17,0xffeee4c9);d.drawCenteredTextWithShadow(textRenderer,title,width/2,20,0xffffd782);d.enableScissor(x+10,64,x+w-10,height-24);int y=67;for(int i=offset;i<rows.size()&&y<height-28;i++){for(var line:textRenderer.wrapLines(Text.literal(rows.get(i)),w-32)){d.drawText(textRenderer,line,x+16,y,0xff383b3a,false);y+=13;}}d.disableScissor();super.render(d,mx,my,delta);}
}
