package com.wildfire.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.client.render.BodyMeshRenderer;
import com.wildfire.client.render.BodyRenderContext;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements BodyRenderContext.Cubes {
    @Shadow @Final private List<ModelPart.Cube> cubes;
    public List<ModelPart.Cube> extended$cubes() { return cubes; }
    @Inject(method="compile",at=@At("HEAD"),cancellable=true)
    private void extended$compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color, CallbackInfo ci) {
        BodyRenderContext context=BodyRenderContext.CURRENT.get();
        if(context==null) return;
        var part=context.part((ModelPart)(Object)this);
        if(part==null) return;
        for(var cube:cubes) {
            if(BodyMeshRenderer.compatible(cube,part)) BodyMeshRenderer.render(cube,part,context,pose,consumer,light,overlay,color);
            else cube.compile(pose,consumer,light,overlay,color);
        }
        ci.cancel();
    }
}
