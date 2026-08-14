package org.figuramc.figura.mixin.render.feature;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.feature.ModelPartFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.figuramc.figura.ducks.FiguraSubmitCallBackExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// this method runs callbacks for Models before and after rendering, as well as before animation setup if they exist
@Mixin(ModelPartFeatureRenderer.class)
public class ModelPartFeatureRendererMixin {
    @Shadow
    @Final
    private PoseStack poseStack;

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;"))
    private Set<Map.Entry<RenderType, List<SubmitNodeStorage.ModelPartSubmit>>> figura$snapshotModelPartSubmits(Map<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> modelPartSubmits) {
        Map<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> entry : modelPartSubmits.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return snapshot.entrySet();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 0), cancellable = true)
    private <S> void figura$preRender(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource,
                                      OutlineBufferSource outlineBufferSource, MultiBufferSource.BufferSource crumblingBufferSource, CallbackInfo ci, @Local SubmitNodeStorage.ModelPartSubmit modelSubmit) {
        FiguraSubmitCallBackExtension callBackExtension = (FiguraSubmitCallBackExtension) (Object) modelSubmit;

        for (var callback : new ArrayList<>(callBackExtension.figura$getPreRenderingCallbacks())) {
             if (!callback.apply(bufferSource, poseStack)) {
                 ci.cancel();
             }
        }

        if (ci.isCancelled()) {
            for (var callback : new ArrayList<>(callBackExtension.figura$getPostRenderingCallbacks()))
                callback.run();

            callBackExtension.figura$getPostRenderingCallbacks().clear();
        }
        callBackExtension.figura$getPreRenderingCallbacks().clear();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 0, shift = At.Shift.AFTER))
    private <S> void figura$postRender(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource,
                                       OutlineBufferSource outlineBufferSource, MultiBufferSource.BufferSource crumblingBufferSource, CallbackInfo ci, @Local SubmitNodeStorage.ModelPartSubmit modelSubmit) {
        FiguraSubmitCallBackExtension callBackExtension = (FiguraSubmitCallBackExtension) (Object) modelSubmit;
        for (var callback : new ArrayList<>(callBackExtension.figura$getPostRenderingCallbacks()))
             callback.run();
        callBackExtension.figura$getPostRenderingCallbacks().clear();
    }
}
