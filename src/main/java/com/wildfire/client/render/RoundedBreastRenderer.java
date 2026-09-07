package com.wildfire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.api.IGenderArmor;
import com.wildfire.main.Gender;
import com.wildfire.main.WildfireHelper;
import com.wildfire.main.entitydata.EntityConfig;
import com.wildfire.physics.BodyDeformation;
import com.wildfire.physics.BreastSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Vector3f;

public final class RoundedBreastRenderer {
    public static void render(PoseStack stack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                              HumanoidModel<?> model, EntityConfig config, float partial, TextureAtlas trimAtlas) {
        if(!config.getGender().canHaveBreasts() || config.getBustSize()<.02) return;
        var chest=player.getItemBySlot(EquipmentSlot.CHEST);
        IGenderArmor armor=WildfireHelper.getArmorConfig(chest);
        if(armor.alwaysHidesBreasts() || (armor.coversBreasts() && !config.showBreastsInArmor())) return;
        // Specialized armor meshes own their silhouette. The standard humanoid path is supported below.
        if(!chest.isEmpty() && !(chest.getItem() instanceof ArmorItem)) return;
        Minecraft mc=Minecraft.getInstance();
        boolean translucent=player.isInvisible() && mc.player!=null && !player.isInvisibleTo(mc.player);
        boolean visible=!player.isInvisible() || translucent || mc.shouldEntityAppearGlowing(player);
        stack.pushPose();
        try {
            model.body.translateAndRotate(stack);
            if(visible) {
                RenderType type=translucent ? RenderType.itemEntityTranslucentCull(player.getSkin().texture())
                        : player.isInvisible() ? RenderType.outline(player.getSkin().texture()) : RenderType.entityTranslucent(player.getSkin().texture());
                int color=translucent ? 0x26FFFFFF : -1;
                draw(stack,buffers.getBuffer(type),light,LivingEntityRenderer.getOverlayCoords(player,0),color,config,partial,0,64,0);
                if(player.isModelPartShown(PlayerModelPart.JACKET))
                    draw(stack,buffers.getBuffer(type),light,LivingEntityRenderer.getOverlayCoords(player,0),color,config,partial,.26f,64,16);
            }
            if(armor.coversBreasts() && chest.getItem() instanceof ArmorItem item) {
                int dye=chest.is(ItemTags.DYEABLE) ? DyedItemColor.getOrDefault(chest,0xFFA06540) : -1;
                for(var layer:item.getMaterial().value().layers()) {
                    var texture=ClientHooks.getArmorTexture(player,chest,layer,false,EquipmentSlot.CHEST);
                    draw(stack,buffers.getBuffer(RenderType.armorCutoutNoCull(texture)),light,OverlayTexture.NO_OVERLAY,layer.dyeable()?dye:-1,config,partial,.55f,32,0);
                }
                var trim=chest.get(DataComponents.TRIM);
                if(trim!=null) {
                    var consumer=trimAtlas.getSprite(trim.outerTexture(item.getMaterial())).wrap(buffers.getBuffer(Sheets.armorTrimsSheet(trim.pattern().value().decal())));
                    draw(stack,consumer,light,OverlayTexture.NO_OVERLAY,-1,config,partial,.55f,32,0);
                }
                if(chest.hasFoil()) draw(stack,buffers.getBuffer(RenderType.armorEntityGlint()),light,OverlayTexture.NO_OVERLAY,-1,config,partial,.55f,32,0);
            }
        } finally { stack.popPose(); }
    }
    private static void draw(PoseStack stack, VertexConsumer consumer, int light, int overlay, int color,
                             EntityConfig config, float partial, float inflate, int textureHeight, int vOffset) {
        int steps=16;
        for(boolean left:new boolean[]{true,false}) {
            var physics=left || config.getBreasts().isUniboob() ? config.getLeftBreastPhysics() : config.getRightBreastPhysics();
            float size=physics.getBreastSize(partial);
            // Preview before the first tick uses the configured size.
            if(size==0) size=config.getBustSize();
            float dx=(physics.getPrePositionX()+(physics.getPositionX()-physics.getPrePositionX())*partial)/2;
            float dy=(physics.getPrePositionY()+(physics.getPositionY()-physics.getPrePositionY())*partial)/2;
            Vector3f[][] points=new Vector3f[steps+1][steps+1], normals=new Vector3f[steps+1][steps+1];
            for(int row=0;row<=steps;row++) for(int col=0;col<=steps;col++) {
                float u=col/(float)steps, v=row/(float)steps, epsilon=.0005f;
                points[row][col]=point(config,left,u,v,size,dx,dy,inflate);
                var du=point(config,left,Math.min(1,u+epsilon),v,size,dx,dy,inflate)
                        .sub(point(config,left,Math.max(0,u-epsilon),v,size,dx,dy,inflate));
                var dv=point(config,left,u,Math.min(1,v+epsilon),size,dx,dy,inflate)
                        .sub(point(config,left,u,Math.max(0,v-epsilon),size,dx,dy,inflate));
                normals[row][col]=dv.cross(du).normalize();
                stack.last().transformNormal(normals[row][col],normals[row][col]);
            }
            for(int row=0;row<steps;row++) for(int col=0;col<steps;col++) for(int corner=0;corner<4;corner++) {
                int column=col+(corner>=2?1:0), line=row+(corner==1 || corner==2?1:0);
                var p=points[line][column]; var n=normals[line][column];
                float u=column/(float)steps, v=line/(float)steps;
                consumer.addVertex(stack.last().pose(),p.x,p.y,p.z).setColor(color)
                        .setUv(((left?20:24)+u*4)/64,(22.1f+v*5.7f+vOffset)/textureHeight)
                        .setOverlay(overlay).setLight(light).setNormal(n.x,n.y,n.z);
            }
        }
    }
    private static Vector3f point(EntityConfig config,boolean left,float u,float v,float size,float dx,float dy,float inflate) {
        var settings=config.getBreasts();
        var p=BreastSurface.point(left,u,v,size,config.getBodySettings().shape(),dx,dy,inflate,
                settings.getXOffset(),settings.getYOffset(),settings.getZOffset(),settings.getCleavage());
        if(config.getGender()==Gender.FEMALE) p=BodyDeformation.deform(BodyDeformation.Part.TORSO,p.x(),p.y(),p.z(),config.getBodySettings(),0,0);
        return new Vector3f((float)p.x()/16,(float)p.y()/16,(float)p.z()/16);
    }
}
