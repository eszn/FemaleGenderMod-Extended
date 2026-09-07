package com.wildfire.test;

import com.mojang.blaze3d.vertex.*;
import com.wildfire.client.gui.screen.WildfireBodySettingsScreen;
import com.wildfire.client.render.BodyRenderContext;
import com.wildfire.main.Gender;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.config.GeneralClientConfig;
import com.wildfire.main.entitydata.BodySettings;
import com.wildfire.main.entitydata.PlayerConfig;
import com.wildfire.main.networking.BodySync;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid="wildfire_gender_test",value=Dist.CLIENT)
public final class ClientVerification {
    private static int ticks, syncedTicks, frames;
    private static boolean configured, sawRemote, sawLeave, sawReturn, rendered;
    private static String capture;
    private static boolean connecting;
    private static boolean reported;
    private static int gifMode;
    private static PhysicsViewsScreen physicsScreen;
    private static boolean gifDone;
    private static boolean enabled() { return "a".equals(System.getProperty("wildfire.verify")) || "b".equals(System.getProperty("wildfire.verify")); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if(!enabled()) return;
        var mc=Minecraft.getInstance(); ticks++;
        mc.options.pauseOnLostFocus=false; mc.options.framerateLimit().set(30); mc.options.renderDistance().set(4);
        mc.options.guiScale().set(2);
        mc.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;
        mc.getToasts().clear();
        // Hidden test window; no desktop input, focus or UI automation is used.
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(ticks>2400) throw new IllegalStateException("Multiplayer/render verification timed out");
        if(mc.player==null || mc.level==null) return;
        if(!configured) {
            System.out.println("BODY_VERIFY_GRAPHICS: "+org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
            var authored=com.wildfire.client.render.AuthoredModel.get();
            if(authored!=null) System.out.println("BODY_VERIFY_AUTHORED: "+authored.character());
            GeneralClientConfig.INSTANCE.firstTimeLoad.set(false);
            GeneralClientConfig.INSTANCE.cloudSync.set(false); GeneralClientConfig.INSTANCE.syncPlayerData.set(false);
            var config=WildfireGender.getOrAddPlayerById(mc.player.getUUID());
            config.updateGender(Gender.FEMALE); config.updateBustSize("a".equals(System.getProperty("wildfire.verify"))?1.2f:.9f);
            config.updateBodySettings(new BodySettings(.7f,.65f,.75f,.65f,BodySettings.BreastShape.NATURAL,true,.65f));
            PlayerConfig.saveGenderInfo(config);
            if(!mc.getConnection().hasChannel(BodySync.Serverbound.TYPE)) throw new IllegalStateException("Optional extension channel was not negotiated");
            configured=true; mc.setScreen(new WildfireBodySettingsScreen(null,mc.player.getUUID()));
        }
        var remote=mc.level.players().stream().filter(p->!p.getUUID().equals(mc.player.getUUID())).findFirst().orElse(null);
        if(remote!=null) {
            var config=WildfireGender.getPlayerById(remote.getUUID());
            if(config!=null && config.hasExtendedProfile) {
                float expected=remote.getGameProfile().getName().endsWith("A")?1.2f:.9f;
                if(config.getBustSize()!=expected || config.getBodySettings().buttocks()!=.75f) throw new IllegalStateException("Remote synchronized values changed");
                sawRemote=true; if(sawLeave) sawReturn=true;
                syncedTicks++;
                if(!rendered && syncedTicks>25) { renderChecks(mc,remote); rendered=true; capture="body-settings.png"; }
                if(syncedTicks==60) { mc.setScreen(new ModelViewsScreen(mc.player.getUUID())); capture="body-natural.png"; }
                if(syncedTicks==72) { mc.setScreen(new ModelViewsScreen(mc.player.getUUID(),true,BodySettings.BreastShape.NATURAL)); capture="body-neutral.png"; }
                if(syncedTicks==78) { mc.setScreen(new ModelViewsScreen(mc.player.getUUID(),true,BodySettings.BreastShape.NATURAL,true)); capture="shading-natural.png"; }
                if(syncedTicks==81) { mc.setScreen(ModelViewsScreen.base(mc.player.getUUID())); capture="base-surface.png"; }
                if(syncedTicks==84) { mc.setScreen(new ModelViewsScreen(mc.player.getUUID(),false,BodySettings.BreastShape.ROUNDED)); capture="body-rounded.png"; }
                if(syncedTicks==96) { mc.setScreen(new ModelViewsScreen(mc.player.getUUID(),true,BodySettings.BreastShape.ROUNDED)); capture="body-rounded-neutral.png"; }
                if(syncedTicks==102) { mc.setScreen(new ModelViewsScreen(mc.player.getUUID(),true,BodySettings.BreastShape.ROUNDED,true)); capture="shading-rounded.png"; }
                if(syncedTicks==108) mc.setScreen(new ModelViewsScreen(mc.player.getUUID()));
                if(syncedTicks==114) { mc.setScreen(ModelViewsScreen.sizes(mc.player.getUUID(),false)); capture="sizes-front.png"; }
                if(syncedTicks==120) { mc.setScreen(ModelViewsScreen.sizes(mc.player.getUUID(),true)); capture="sizes-rear.png"; }
                if(syncedTicks==126) mc.setScreen(new ModelViewsScreen(mc.player.getUUID()));
                if(syncedTicks==220) { renderChecks(mc,remote); capture="body-leather.png"; }
                if(syncedTicks==290) { renderChecks(mc,remote); capture="body-diamond.png"; }
            }
        } else if(sawRemote) sawLeave=true;
        if(!reported && syncedTicks>300 && rendered && sawReturn) {
            var report="Real two-client sync, tracking reentry, transformed player/armor render, finite mesh normals: PASS\n";
            Files.createDirectories(Path.of("verification")); Files.writeString(Path.of("verification/result.txt"),report);
            System.out.println("BODY_VERIFY_CLIENT: PASS "+System.getProperty("wildfire.verify")); reported=true;
        }
        if(reported && "a".equals(System.getProperty("wildfire.verify")) && !gifDone && capture==null) {
            if(physicsScreen==null) { physicsScreen=new PhysicsViewsScreen(gifMode); mc.setScreen(physicsScreen); }
            if(physicsScreen.advance()) capture=physicsScreen.frameName();
            else { physicsScreen=null; if(++gifMode==3) gifDone=true; }
        }
        if(reported && ticks>850 && (!"a".equals(System.getProperty("wildfire.verify")) || gifDone)) mc.stop();
    }
    private static void renderChecks(Minecraft mc,AbstractClientPlayer player) {
        PlayerRenderer renderer=(PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(player);
        var config=WildfireGender.getOrAddPlayerById(player.getUUID());
        var saved=config.getBodySettings();
        var counter=new Counter(); MultiBufferSource buffers=type->counter;
        // Run the actual render entry point so the installed mixins and all layers execute.
        config.updateBodySettings(BodySettings.NONE);
        renderer.render(player,0,.5f,new PoseStack(),buffers,15728880); int baseline=counter.vertices;
        counter.reset(); config.updateBodySettings(saved);
        renderer.render(player,0,.5f,new PoseStack(),buffers,15728880); int shaped=counter.vertices;
        long original=counter.fingerprint; counter.reset();
        renderer.render(player,0,.5f,new PoseStack(),buffers,15728880);
        if(counter.fingerprint!=original) throw new IllegalStateException("Cached mesh changed a resting player");
        config.updateBodySettings(new BodySettings(.2f,.2f,.2f,.2f,saved.shape(),saved.physics(),saved.motion())); counter.reset();
        renderer.render(player,0,.5f,new PoseStack(),buffers,15728880);
        if(counter.fingerprint==original) throw new IllegalStateException("Size changes did not invalidate cached geometry");
        config.updateBodySettings(saved); counter.reset();
        renderer.render(player,0,.5f,new PoseStack(),buffers,15728880);
        if(counter.fingerprint!=original) throw new IllegalStateException("Restoring a profile did not restore its geometry");
        if(shaped<baseline+2000) throw new IllegalStateException("Body tessellation mixin did not run: "+baseline+" / "+shaped);
        if(BodyRenderContext.CURRENT.get()!=null) throw new IllegalStateException("Render context leaked to the next player");
        // Every pose runs the same render path. Player attributes are restored afterwards.
        var oldPose=player.getPose();
        for(var pose:new net.minecraft.world.entity.Pose[]{net.minecraft.world.entity.Pose.CROUCHING,net.minecraft.world.entity.Pose.SWIMMING,net.minecraft.world.entity.Pose.FALL_FLYING}) {
            player.setPose(pose); renderer.render(player,0,.5f,new PoseStack(),buffers,15728880);
        }
        player.setPose(oldPose);
        System.out.println("BODY_VERIFY_RENDER: baseline="+baseline+" shaped="+shaped+" poses=4 finite/unit normals PASS");
    }
    @SubscribeEvent public static void isolateSurface(RenderLivingEvent.Pre<?,?> event) {
        if(!enabled() || !(Minecraft.getInstance().screen instanceof ModelViewsScreen screen) || !screen.baseOnly()) return;
        if(event.getRenderer().getModel() instanceof net.minecraft.client.model.PlayerModel<?> model) {
            model.jacket.visible=false; model.leftPants.visible=false; model.rightPants.visible=false;
            model.leftArm.visible=false; model.rightArm.visible=false; model.leftSleeve.visible=false; model.rightSleeve.visible=false;
        }
    }
    @SubscribeEvent public static void hidePreviewName(RenderNameTagEvent event) {
        if(enabled() && Minecraft.getInstance().screen instanceof PhysicsViewsScreen)
            event.setCanRender(net.neoforged.neoforge.common.util.TriState.FALSE);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) throws Exception {
        if(!enabled()) return;
        var mc=Minecraft.getInstance();
        if(!connecting && mc.player==null && mc.getOverlay()==null && ++frames>30) {
            connecting=true;
            System.out.println("BODY_VERIFY_CLIENT: connecting scripted client");
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(),mc,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25589"),
                    new net.minecraft.client.multiplayer.ServerData("Body Verification","127.0.0.1:25589",net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
        }
        if(capture==null) return;
        Files.createDirectories(Path.of("verification",capture).getParent());
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(Path.of("verification",capture)); }
        capture=null; frames++;
    }
    private static final class Counter implements VertexConsumer {
        int vertices;
        long fingerprint=1;
        void reset() { vertices=0; fingerprint=1; }
        @Override public VertexConsumer addVertex(float x,float y,float z) {
            if(!Float.isFinite(x+y+z))throw new IllegalStateException("Non-finite vertex"); vertices++;
            fingerprint=31*fingerprint+Float.floatToIntBits(x); fingerprint=31*fingerprint+Float.floatToIntBits(y); fingerprint=31*fingerprint+Float.floatToIntBits(z); return this;
        }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
        @Override public VertexConsumer setUv(float u,float v) { if(!Float.isFinite(u+v))throw new IllegalStateException("Non-finite UV"); return this; }
        @Override public VertexConsumer setUv1(int u,int v) { return this; }
        @Override public VertexConsumer setUv2(int u,int v) { return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { float length=x*x+y*y+z*z; if(!Float.isFinite(length) || Math.abs(length-1)>.025)throw new IllegalStateException("Invalid normal "+length); return this; }
    }
}
