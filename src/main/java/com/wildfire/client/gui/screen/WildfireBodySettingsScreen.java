package com.wildfire.client.gui.screen;

import com.wildfire.client.gui.WildfireSlider;
import com.wildfire.main.config.ClientConfiguration;
import com.wildfire.main.config.FloatConfigKey;
import com.wildfire.main.entitydata.BodySettings;
import com.wildfire.main.entitydata.PlayerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public final class WildfireBodySettingsScreen extends BaseWildfireScreen {
    private final List<WildfireSlider> sliders=new ArrayList<>();
    public WildfireBodySettingsScreen(Screen parent, UUID player) { super(Component.literal("Body shape & motion"),parent,player); }
    @Override public void init() {
        sliders.clear();
        PlayerConfig player=getPlayer();
        int x=width/2-10, y=Math.max(28,height/2-94);
        BodySettings s=player.getBodySettings();
        slider("Hip width",ClientConfiguration.HIPS,s.hips(),x,y,v->change(0,(float)v));
        slider("Thigh fullness",ClientConfiguration.THIGHS,s.thighs(),x,y+23,v->change(1,(float)v));
        slider("Buttock fullness",ClientConfiguration.BUTTOCKS,s.buttocks(),x,y+46,v->change(2,(float)v));
        slider("Waist taper",ClientConfiguration.WAIST,s.waist(),x,y+69,v->change(3,(float)v));
        slider("Secondary motion",ClientConfiguration.BODY_MOTION,s.motion(),x,y+92,v->change(4,(float)v));
        addRenderableWidget(Button.builder(Component.literal("Breast model: "+label(s.shape())),button->{
            BodySettings now=player.getBodySettings();
            var shape=BodySettings.BreastShape.values()[(now.shape().ordinal()+1)%3];
            player.updateBodySettings(new BodySettings(now.hips(),now.thighs(),now.buttocks(),now.waist(),shape,now.physics(),now.motion()));
            button.setMessage(Component.literal("Breast model: "+label(shape))); PlayerConfig.saveGenderInfo(player);
        }).bounds(x,y+115,170,20).build());
        addRenderableWidget(Button.builder(Component.literal("Body physics: "+(s.physics()?"On":"Off")),button->{
            BodySettings now=player.getBodySettings();
            player.updateBodySettings(new BodySettings(now.hips(),now.thighs(),now.buttocks(),now.waist(),now.shape(),!now.physics(),now.motion()));
            button.setMessage(Component.literal("Body physics: "+(!now.physics()?"On":"Off"))); PlayerConfig.saveGenderInfo(player);
        }).bounds(x,y+138,170,20).build());
        addRenderableWidget(Button.builder(Component.literal("Natural preset"),button->{
            player.updateBodySettings(BodySettings.DEFAULT); PlayerConfig.saveGenderInfo(player); rebuildWidgets();
        }).bounds(width/2-160,y+138,140,20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),button->onClose()).bounds(x,y+164,170,20).build());
    }
    private static String label(BodySettings.BreastShape shape) {
        return switch(shape) { case CLASSIC->"Classic"; case ROUNDED->"Rounded"; case NATURAL->"Natural"; };
    }
    private void slider(String name, FloatConfigKey key, float value, int x, int y, DoubleConsumer change) {
        var slider=addRenderableWidget(new WildfireSlider(x,y,170,20,key,value,v->change.accept(v),
                v->Component.literal(name+": "+Math.round(v*100)+"%"),v->PlayerConfig.saveGenderInfo(getPlayer())));
        slider.setArrowKeyStep(.01);
        slider.setTooltip(Tooltip.create(Component.literal("Cosmetic only. Standard armor follows the shape; rigid armor suppresses motion.")));
        sliders.add(slider);
    }
    private void change(int field,float value) {
        BodySettings s=getPlayer().getBodySettings();
        getPlayer().updateBodySettings(new BodySettings(field==0?value:s.hips(),field==1?value:s.thighs(),field==2?value:s.buttocks(),
                field==3?value:s.waist(),s.shape(),s.physics(),field==4?value:s.motion()));
    }
    private void saveSliders() { sliders.forEach(WildfireSlider::save); }
    @Override public boolean mouseReleased(double x,double y,int button) { saveSliders(); return super.mouseReleased(x,y,button); }
    @Override public void onClose() { saveSliders(); super.onClose(); }
    @Override public void removed() { saveSliders(); super.removed(); }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
        super.render(graphics,mouseX,mouseY,partial);
        int y=Math.max(28,height/2-94);
        graphics.drawCenteredString(font,title,width/2,y-19,0xFFFFFF);
        if(minecraft!=null && minecraft.level!=null) {
            var player=minecraft.level.getPlayerByUUID(playerUUID);
            if(player!=null) InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,width/2-158,y,width/2-22,y+133,55,.0625f,mouseX,mouseY,player);
        }
        graphics.drawCenteredString(font,Component.literal("Rigid armor holds its shape."),width/2,y+192,0xAAAAAA);
    }
}
