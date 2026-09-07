package com.wildfire.mixin;

import com.wildfire.client.render.BodyRenderContext;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method="setupAnim",at=@At("RETURN"))
    private void extended$armClearance(LivingEntity entity,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci) {
        var context=BodyRenderContext.CURRENT.get();
        if(context==null || context.entity!=entity || entity.isUsingItem() || entity.swinging || entity.isSleeping()
                || entity.getPose()==Pose.SWIMMING || entity.getPose()==Pose.FALL_FLYING) return;
        var model=(PlayerModel<?>)(Object)this;
        var shape=context.config.getBodySettings();
        float angle=Math.max(context.breasts?context.bust*.24f:0,shape.hips()*.06f+shape.buttocks()*.1f);
        // Only the ordinary relaxed pose needs extra clearance. Use/item and special animation poses keep control.
        if(model.leftArmPose==HumanoidModel.ArmPose.EMPTY) model.leftArm.zRot=Math.min(model.leftArm.zRot,-angle);
        if(model.rightArmPose==HumanoidModel.ArmPose.EMPTY) model.rightArm.zRot=Math.max(model.rightArm.zRot,angle);
        model.leftSleeve.copyFrom(model.leftArm); model.rightSleeve.copyFrom(model.rightArm);
    }
}
