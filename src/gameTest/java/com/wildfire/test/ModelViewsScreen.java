package com.wildfire.test;

import java.util.UUID;
import com.wildfire.main.WildfireHelper;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.entitydata.BodySettings;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Rendered by Minecraft into its own framebuffer; no desktop screenshots or input. */
final class ModelViewsScreen extends Screen {
    private final UUID player;
    private final boolean neutral;
    private final BodySettings.BreastShape shape;
    private final boolean closeup;
    private final int sizing;
    private ResourceLocation material;
    private boolean baseOnly;
    static ModelViewsScreen base(UUID player) {
        var screen=new ModelViewsScreen(player,true,BodySettings.BreastShape.NATURAL,true); screen.baseOnly=true; return screen;
    }
    boolean baseOnly() { return baseOnly; }
    ModelViewsScreen(UUID player) { this(player,false,BodySettings.BreastShape.NATURAL); }
    ModelViewsScreen(UUID player,boolean neutral,BodySettings.BreastShape shape) {
        this(player,neutral,shape,false);
    }
    ModelViewsScreen(UUID player,boolean neutral,BodySettings.BreastShape shape,boolean closeup) {
        this(player,neutral,shape,closeup,0);
    }
    static ModelViewsScreen sizes(UUID player,boolean rear) {
        return new ModelViewsScreen(player,true,BodySettings.BreastShape.NATURAL,false,rear?2:1);
    }
    private ModelViewsScreen(UUID player,boolean neutral,BodySettings.BreastShape shape,boolean closeup,int sizing) {
        super(Component.literal("Female Gender Extended | "+com.wildfire.client.gui.screen.WildfireBodySettingsScreen.label(shape)+" | "+(sizing>0?"size comparison":closeup?"shading close-up":neutral?"neutral material":"skin / armor")));
        this.player=player; this.neutral=neutral; this.shape=shape; this.closeup=closeup;
        this.sizing=sizing;
    }
    @Override public void removed() { if(material!=null) minecraft.getTextureManager().release(material); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
        graphics.fill(0,0,width,height,0xFF18202A);
        graphics.drawCenteredString(font,baseOnly?Component.literal("Base surface only | layers and arms hidden for diagnosis"):title,width/2,22,0xFFF1F5F9);
        var entity=minecraft.level.getPlayerByUUID(player);
        if(entity==null) return;
        var config=WildfireGender.getOrAddPlayerById(player); var saved=config.getBodySettings(); float savedBust=config.getBustSize();
        var armor=WildfireHelper.getArmorConfig(entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
        config.updateBodySettings(new BodySettings(saved.hips(),saved.thighs(),saved.buttocks(),saved.waist(),shape,saved.physics(),saved.motion()));
        try {
            int views=sizing>0?3:4;
            for(int view=0;view<views;view++) {
                if(sizing>0) {
                    config.updateBustSize(new float[]{.3f,.75f,1.2f}[view]);
                    float amount=new float[]{.2f,.55f,1}[view];
                    config.updateBodySettings(new BodySettings(amount,amount,amount,amount*.65f,shape,false,0));
                    config.getLeftBreastPhysics().update(entity,armor); config.getLeftBreastPhysics().update(entity,armor);
                    config.getRightBreastPhysics().update(entity,armor); config.getRightBreastPhysics().update(entity,armor);
                }
                int center=width*(view*2+1)/(views*2);
                graphics.drawCenteredString(font,(sizing>0?new String[]{"Small","Medium","Maximum"}:new String[]{"Front","Side","Rear","Rear 3/4"})[view],center,56,0xFF9AB7C7);
                var rotation=new Quaternionf().rotateZ((float)Math.PI).rotateY((sizing>0?(sizing==1?.55f:1.5f):new float[]{0,1,2,1.5f}[view])*(float)Math.PI/2);
                if(closeup) graphics.enableScissor(view*width/4+4,72,(view+1)*width/4-4,height-50);
                int scale=closeup?190:sizing>0?110:95;
                WildfireHelper.withEntityAngles(entity,180,180,0,p->{
                    if(!neutral) InventoryScreen.renderEntityInInventory(graphics,center,height/2+8,95,
                            new Vector3f(0,p.getBbHeight()/2,0),rotation,null,p);
                    else {
                        if(material==null) {
                            var image=new NativeImage(1,1,false); image.setPixelRGBA(0,0,0xFFD4D4D4);
                            material=minecraft.getTextureManager().register("body-verification",new DynamicTexture(image));
                        }
                        graphics.pose().pushPose();
                        graphics.pose().translate(center,height/2+8,50);
                        graphics.pose().scale(scale,scale,-scale); graphics.pose().translate(0,p.getBbHeight()/2,0); graphics.pose().mulPose(rotation);
                        Lighting.setupForEntityInInventory();
                        var dispatcher=minecraft.getEntityRenderDispatcher(); dispatcher.setRenderShadow(false);
                        MultiBufferSource buffers=type->graphics.bufferSource().getBuffer(RenderType.entitySolid(material));
                        RenderSystem.runAsFancy(()->dispatcher.render(p,0,0,0,0,1,graphics.pose(),buffers,15728880));
                        graphics.flush(); dispatcher.setRenderShadow(true); graphics.pose().popPose(); Lighting.setupFor3DItems();
                    }
                });
                if(closeup) graphics.disableScissor();
            }
        } finally {
            config.updateBodySettings(saved);
            if(sizing>0) {
                config.updateBustSize(savedBust);
                config.getLeftBreastPhysics().update(entity,armor); config.getLeftBreastPhysics().update(entity,armor);
                config.getRightBreastPhysics().update(entity,armor); config.getRightBreastPhysics().update(entity,armor);
            }
        }
        graphics.drawCenteredString(font,Component.literal("Standard skin, skeleton and armor paths"),width/2,height-34,0xFF9AB7C7);
    }
}
