package com.wildfire.client.render;

import com.wildfire.main.Gender;
import com.wildfire.main.WildfireHelper;
import com.wildfire.main.entitydata.BodySettings;
import com.wildfire.main.config.GeneralClientConfig;
import com.wildfire.main.entitydata.EntityConfig;
import com.wildfire.physics.BodyDeformation;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Render-call-scoped state. Every mixin restores this in finally, including nested entity renders. */
public final class BodyRenderContext {
    public static final ThreadLocal<BodyRenderContext> CURRENT = new ThreadLocal<>();
    public final EntityConfig config;
    public final float partialTick;
    public final LivingEntity entity;
    public final boolean breasts;
    public final float bust,leftX,leftY,rightX,rightY;
    private final Map<ModelPart, BodyDeformation.Part> parts = new IdentityHashMap<>();
    public interface Cubes { java.util.List<ModelPart.Cube> extended$cubes(); }
    public record Binding(HumanoidModel<?> model, boolean outer, net.minecraft.world.entity.EquipmentSlot armor) {}
    private final Map<ModelPart.Cube,Binding> bindings=new IdentityHashMap<>();
    public record TorsoPose(org.joml.Matrix4f position,org.joml.Matrix3f normal) {}
    public final Map<HumanoidModel<?>,TorsoPose> torsoPoses=new IdentityHashMap<>();
    public TorsoPose primaryTorsoPose;
    public BodyRenderContext(LivingEntity entity, EntityModel<?> model, float partialTick) {
        this.entity = entity; this.partialTick = partialTick; this.config = EntityConfig.getEntity(entity);
        var armor=WildfireHelper.getArmorConfig(entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
        breasts=config.getGender().canHaveBreasts() && config.getBodySettings().shape()!=BodySettings.BreastShape.CLASSIC
                && !armor.alwaysHidesBreasts() && (!armor.coversBreasts() || config.showBreastsInArmor());
        var l=config.getLeftBreastPhysics(); var r=config.getBreasts().isUniboob()?l:config.getRightBreastPhysics();
        bust=l.getBreastSize(partialTick)==0?config.getBustSize():l.getBreastSize(partialTick);
        leftX=(l.getPrePositionX()+(l.getPositionX()-l.getPrePositionX())*partialTick)/2;
        leftY=(l.getPrePositionY()+(l.getPositionY()-l.getPrePositionY())*partialTick)/2;
        rightX=(r.getPrePositionX()+(r.getPositionX()-r.getPrePositionX())*partialTick)/2;
        rightY=(r.getPrePositionY()+(r.getPositionY()-r.getPrePositionY())*partialTick)/2;
        register(model);
    }
    public static boolean eligible(LivingEntity entity, EntityModel<?> model) {
        var config=EntityConfig.getEntity(entity);
        return entity instanceof Player && model instanceof PlayerModel<?> && !entity.isSpectator()
                && !GeneralClientConfig.INSTANCE.disableRendering.get()
                && ((config.getGender()==Gender.FEMALE && config.getBodySettings().hasBodyShape())
                    || (config.getGender().canHaveBreasts() && config.getBustSize()>0 && config.getBodySettings().shape()!=BodySettings.BreastShape.CLASSIC));
    }
    public void register(Object model) {
        register(model,null);
    }
    public Binding binding(ModelPart.Cube cube) { return bindings.get(cube); }
    public void register(Object model,net.minecraft.world.entity.EquipmentSlot armor) {
        if (model instanceof HumanoidModel<?> humanoid) {
            parts.put(humanoid.body, BodyDeformation.Part.TORSO);
            parts.put(humanoid.leftLeg, BodyDeformation.Part.LEFT_LEG);
            parts.put(humanoid.rightLeg, BodyDeformation.Part.RIGHT_LEG);
            if (model instanceof PlayerModel<?> player) {
                parts.put(player.jacket, BodyDeformation.Part.TORSO);
                parts.put(player.leftPants, BodyDeformation.Part.LEFT_LEG);
                parts.put(player.rightPants, BodyDeformation.Part.RIGHT_LEG);
            }
            for(var part:new ModelPart[]{humanoid.body,humanoid.leftLeg,humanoid.rightLeg})
                if((Object)part instanceof Cubes access) for(var cube:access.extended$cubes()) bindings.put(cube,new Binding(humanoid,false,armor));
            if(model instanceof PlayerModel<?> player) for(var part:new ModelPart[]{player.jacket,player.leftPants,player.rightPants})
                if((Object)part instanceof Cubes access) for(var cube:access.extended$cubes()) bindings.put(cube,new Binding(player,true,null));
        }
    }
    public BodyDeformation.Part part(ModelPart part) { return parts.get(part); }
}
