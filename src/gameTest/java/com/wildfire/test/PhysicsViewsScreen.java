package com.wildfire.test;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.wildfire.main.*;
import com.wildfire.main.entitydata.BodySettings;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import org.joml.Quaternionf;

/** A real Minecraft player renderer and the production physics, driven by repeatable motion samples. */
final class PhysicsViewsScreen extends Screen {
    private final RemotePlayer actor;
    private final int mode;
    private ResourceLocation gray;
    private int step=-1;
    private double peakBreast,peakButt,walkBreast,walkButt;
    static final int LENGTH=160;
    PhysicsViewsScreen(int mode) {
        super(Component.literal("Jenny curves + rounded body | "+new String[]{"No armor","Leather","Diamond"}[mode]));
        this.mode=mode;
        var mc=Minecraft.getInstance(); var skin=mc.player.getSkin();
        actor=new RemotePlayer(mc.level,new GameProfile(UUID.nameUUIDFromBytes(("physics-gif-"+mode).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"PhysicsPreview")) {
            @Override public PlayerSkin getSkin() { return skin; }
        };
        var config=WildfireGender.getOrAddPlayerById(actor.getUUID());
        config.updateGender(Gender.FEMALE); config.updateBustSize(.8f);
        config.updateBodySettings(new BodySettings(.5f,.5f,.5f,.5f,BodySettings.BreastShape.NATURAL,true,.5f));
        if(mode>0) {
            actor.setItemSlot(EquipmentSlot.CHEST,new ItemStack(mode==1?Items.LEATHER_CHESTPLATE:Items.DIAMOND_CHESTPLATE));
            actor.setItemSlot(EquipmentSlot.LEGS,new ItemStack(mode==1?Items.LEATHER_LEGGINGS:Items.DIAMOND_LEGGINGS));
        }
    }
    boolean advance() throws Exception {
        if(++step>=LENGTH) {
            if(mode==0 && (peakBreast<.02 || peakButt<.005)) throw new IllegalStateException("Motion sequence failed to excite body physics");
            if(mode==0 && (walkBreast<.15 || walkButt<.08)) throw new IllegalStateException("Walking response is too small to assess visually");
            if(mode==2 && (peakBreast>.00001 || peakButt>.00001)) throw new IllegalStateException("Rigid armor moved");
            var config=WildfireGender.getOrAddPlayerById(actor.getUUID());
            if(Math.abs(config.getLeftBreastPhysics().getPositionY())>.005 || Math.abs(config.getBodyPhysics().vertical(true,1))>.005)
                throw new IllegalStateException("Physics did not settle after landing");
            Files.writeString(Path.of("verification","physics-"+mode+".txt"),"PASS production physics; peak breast="+peakBreast+"; peak butt="+peakButt+"; walking breast="+walkBreast+"; walking butt="+walkButt+"; settled=true\n");
            return false;
        }
        actor.tickCount++;
        boolean walking=step>=20 && step<70;
        double x=step<20?0:step<70?(step-20)*.08:4;
        double y=step>=95 && step<=113?Math.max(0,.028*(step-95)*(113-step)):0;
        float yaw=step<70?0:step<90?(step-70)*4.5f:90;
        actor.setPos(x,81+y,0); actor.xo=x; actor.yo=81+y; actor.zo=0;
        actor.yBodyRot=actor.yBodyRotO=yaw; actor.setYRot(yaw); actor.yRotO=yaw;
        actor.yHeadRot=actor.yHeadRotO=yaw; actor.setOnGround(y==0);
        actor.walkAnimation.update(walking?.65f:0,1);
        var config=WildfireGender.getOrAddPlayerById(actor.getUUID()); config.tickBreastPhysics(actor);
        peakBreast=Math.max(peakBreast,Math.abs(config.getLeftBreastPhysics().getPositionY()/2));
        peakButt=Math.max(peakButt,Math.abs(config.getBodyPhysics().vertical(true,1)));
        if(walking) { walkBreast=Math.max(walkBreast,Math.abs(config.getLeftBreastPhysics().getPositionY()/2)); walkButt=Math.max(walkButt,Math.abs(config.getBodyPhysics().vertical(true,1))); }
        return true;
    }
    String frameName() { return String.format("physics-%d/%03d.png",mode,step); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { if(gray!=null) minecraft.getTextureManager().release(gray); }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
        graphics.fill(0,0,width,height,0xFF18202A);
        graphics.drawCenteredString(font,title,width/2,16,0xFFF1F5F9);
        String phase=step<20?"Rest":step<70?"Walking":step<95?"Turning":step<114?"Jump / landing":"Settling";
        graphics.drawCenteredString(font,phase,width/2,34,0xFFABC8D7);
        if(gray==null) {
            var image=new NativeImage(1,1,false); image.setPixelRGBA(0,0,0xFFD4D4D4);
            gray=minecraft.getTextureManager().register("physics-neutral",new DynamicTexture(image));
        }
        for(int view=0;view<4;view++) {
            int center=width*(view*2+1)/8;
            graphics.drawCenteredString(font,new String[]{"Skin front","Skin rear","Surface front","Surface rear"}[view],center,58,0xFF9AB7C7);
            graphics.enableScissor(view*width/4,73,(view+1)*width/4,height-37);
            float turn=step>=70 && step<90?(step-70)*.018f:step>=90?.36f:0;
            var rotation=new Quaternionf().rotateZ((float)Math.PI).rotateY((view%2==0?2.65f:.40f)+turn);
            graphics.pose().pushPose(); graphics.pose().translate(center,height/2+14,50);
            graphics.pose().scale(108,108,-108); graphics.pose().translate(0,actor.getBbHeight()/2,0); graphics.pose().mulPose(rotation);
            Lighting.setupForEntityInInventory();
            var dispatcher=minecraft.getEntityRenderDispatcher(); dispatcher.setRenderShadow(false);
            MultiBufferSource buffers=view<2?graphics.bufferSource():type->graphics.bufferSource().getBuffer(RenderType.entitySolid(gray));
            // The viewport stays centered while the trajectory drives the real acceleration inputs.
            RenderSystem.runAsFancy(()->dispatcher.render(actor,0,0,0,0,1,graphics.pose(),buffers,15728880));
            graphics.flush(); dispatcher.setRenderShadow(true); graphics.pose().popPose(); Lighting.setupFor3DItems(); graphics.disableScissor();
        }
        graphics.drawCenteredString(font,"Real game renderer | scripted movement | 1x speed",width/2,height-24,0xFF9AB7C7);
    }
}
