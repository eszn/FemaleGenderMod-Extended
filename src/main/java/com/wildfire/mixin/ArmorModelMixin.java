package com.wildfire.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.wildfire.client.render.BodyRenderContext;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorModelMixin {
    @ModifyExpressionValue(method="renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;getArmorModelHook(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/client/model/HumanoidModel;)Lnet/minecraft/client/model/Model;"))
    private Model extended$armor(Model model,@com.llamalad7.mixinextras.sugar.Local(argsOnly=true) net.minecraft.world.entity.EquipmentSlot slot) {
        BodyRenderContext context=BodyRenderContext.CURRENT.get();
        if(context!=null) context.register(model,slot);
        return model;
    }
}
