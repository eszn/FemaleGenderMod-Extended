package com.wildfire.client.render;

import com.wildfire.main.Gender;
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
    private final Map<ModelPart, BodyDeformation.Part> parts = new IdentityHashMap<>();
    public BodyRenderContext(LivingEntity entity, EntityModel<?> model, float partialTick) {
        this.entity = entity; this.partialTick = partialTick; this.config = EntityConfig.getEntity(entity);
        register(model);
    }
    public static boolean eligible(LivingEntity entity, EntityModel<?> model) {
        return entity instanceof Player && model instanceof PlayerModel<?> && !entity.isSpectator()
                && !GeneralClientConfig.INSTANCE.disableRendering.get()
                && EntityConfig.getEntity(entity).getGender() == Gender.FEMALE
                && EntityConfig.getEntity(entity).getBodySettings().hasBodyShape();
    }
    public void register(Object model) {
        if (model instanceof HumanoidModel<?> humanoid) {
            parts.put(humanoid.body, BodyDeformation.Part.TORSO);
            parts.put(humanoid.leftLeg, BodyDeformation.Part.LEFT_LEG);
            parts.put(humanoid.rightLeg, BodyDeformation.Part.RIGHT_LEG);
            if (model instanceof PlayerModel<?> player) {
                parts.put(player.jacket, BodyDeformation.Part.TORSO);
                parts.put(player.leftPants, BodyDeformation.Part.LEFT_LEG);
                parts.put(player.rightPants, BodyDeformation.Part.RIGHT_LEG);
            }
        }
    }
    public BodyDeformation.Part part(ModelPart part) { return parts.get(part); }
}
