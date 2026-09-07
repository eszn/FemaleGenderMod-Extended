package com.wildfire.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wildfire.client.render.BodyRenderContext;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerRenderMixin {
    @Shadow protected EntityModel<?> model;
    @WrapMethod(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void extended$render(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original) {
        BodyRenderContext previous=BodyRenderContext.CURRENT.get();
        BodyRenderContext.CURRENT.set(BodyRenderContext.eligible(entity,model) ? new BodyRenderContext(entity,model,partial) : null);
        try { original.call(entity,yaw,partial,pose,buffers,light); }
        finally { if(previous==null) BodyRenderContext.CURRENT.remove(); else BodyRenderContext.CURRENT.set(previous); }
    }
}
