package org.figuramc.figura.mixin.render.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.feature.ModelPartFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.figuramc.figura.ducks.FiguraSubmitCallBackExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.IdentityHashMap;
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

    @Unique
    private final Map<SubmitNodeStorage.ModelPartSubmit, Boolean> figura$renderVanilla = new IdentityHashMap<>();

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;"))
    private Set<Map.Entry<RenderType, List<SubmitNodeStorage.ModelPartSubmit>>> figura$snapshotModelPartSubmits(Map<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> modelPartSubmits) {
        Map<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> entry : modelPartSubmits.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return snapshot.entrySet();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 0))
    private void figura$renderModelPart(ModelPart modelPart, PoseStack renderPoseStack, VertexConsumer vertexConsumer, int light, int overlay, int color,
                                        Operation<Void> original, SubmitNodeCollection submitNodeCollection,
                                        MultiBufferSource.BufferSource bufferSource, OutlineBufferSource outlineBufferSource,
                                        MultiBufferSource.BufferSource crumblingBufferSource, @Local SubmitNodeStorage.ModelPartSubmit modelSubmit,
                                        @Local RenderType renderType) {
        FiguraSubmitCallBackExtension callBackExtension = (FiguraSubmitCallBackExtension) (Object) modelSubmit;

        boolean renderVanilla = true;
        for (var callback : new ArrayList<>(callBackExtension.figura$getPreRenderingCallbacks())) {
             if (!callback.apply(bufferSource, poseStack)) {
                 renderVanilla = false;
             }
        }
        callBackExtension.figura$getPreRenderingCallbacks().clear();
        figura$renderVanilla.put(modelSubmit, renderVanilla);

        boolean completed = false;
        try {
            if (renderVanilla) {
                original.call(modelPart, renderPoseStack, vertexConsumer, light, overlay, color);
            }
            completed = true;
        } finally {
            if (!completed || !figura$hasLaterModelPartPass(modelSubmit, renderType))
                figura$finishModelPart(modelSubmit);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 1))
    private void figura$renderModelPartOutline(ModelPart modelPart, PoseStack renderPoseStack, VertexConsumer vertexConsumer, int light, int overlay, int color,
                                               Operation<Void> original, SubmitNodeCollection submitNodeCollection,
                                               MultiBufferSource.BufferSource bufferSource, OutlineBufferSource outlineBufferSource,
                                               MultiBufferSource.BufferSource crumblingBufferSource, @Local SubmitNodeStorage.ModelPartSubmit modelSubmit) {
        try {
            if (figura$renderVanilla.getOrDefault(modelSubmit, true))
                original.call(modelPart, renderPoseStack, vertexConsumer, light, overlay, color);
        } finally {
            if (modelSubmit.crumblingOverlay() == null)
                figura$finishModelPart(modelSubmit);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 2))
    private void figura$renderModelPartCrumbling(ModelPart modelPart, PoseStack renderPoseStack, VertexConsumer vertexConsumer, int light, int overlay, int color,
                                                 Operation<Void> original, SubmitNodeCollection submitNodeCollection,
                                                 MultiBufferSource.BufferSource bufferSource, OutlineBufferSource outlineBufferSource,
                                                 MultiBufferSource.BufferSource crumblingBufferSource, @Local SubmitNodeStorage.ModelPartSubmit modelSubmit) {
        try {
            if (figura$renderVanilla.getOrDefault(modelSubmit, true))
                original.call(modelPart, renderPoseStack, vertexConsumer, light, overlay, color);
        } finally {
            figura$finishModelPart(modelSubmit);
        }
    }

    @Unique
    private boolean figura$hasLaterModelPartPass(SubmitNodeStorage.ModelPartSubmit modelSubmit, RenderType renderType) {
        boolean hasOutline = modelSubmit.outlineColor() != 0 && (renderType.isOutline() || renderType.outline().isPresent());
        return hasOutline || modelSubmit.crumblingOverlay() != null;
    }

    @Unique
    private void figura$finishModelPart(SubmitNodeStorage.ModelPartSubmit modelSubmit) {
        FiguraSubmitCallBackExtension callBackExtension = (FiguraSubmitCallBackExtension) (Object) modelSubmit;

        try {
            for (var callback : new ArrayList<>(callBackExtension.figura$getPostRenderingCallbacks()))
                callback.run();
        } finally {
            callBackExtension.figura$getPostRenderingCallbacks().clear();
            figura$renderVanilla.remove(modelSubmit);
        }
    }
}
