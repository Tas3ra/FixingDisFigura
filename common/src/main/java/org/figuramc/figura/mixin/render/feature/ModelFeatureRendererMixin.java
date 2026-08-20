package org.figuramc.figura.mixin.render.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.figuramc.figura.ducks.FiguraSubmitCallBackExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// this method runs callbacks for Models before and after rendering, as well as before animation setup if they exist
@Mixin(ModelFeatureRenderer.class)
public class ModelFeatureRendererMixin {
    @Shadow
    @Final
    private PoseStack poseStack;

    @Unique
    private final Map<SubmitNodeStorage.ModelSubmit<?>, Boolean> figura$renderVanilla = new IdentityHashMap<>();

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer;renderBatch(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/OutlineBufferSource;Ljava/util/Map;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V"), index = 2)
    private Map<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> figura$snapshotModelSubmits(Map<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> modelSubmits) {
        Map<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> entry : modelSubmits.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return snapshot;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer;renderTranslucents(Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/OutlineBufferSource;Ljava/util/List;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V"), index = 2)
    private List<SubmitNodeStorage.TranslucentModelSubmit<?>> figura$snapshotTranslucentModelSubmits(List<SubmitNodeStorage.TranslucentModelSubmit<?>> translucentModelSubmits) {
        return new ArrayList<>(translucentModelSubmits);
    }

    @WrapOperation(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"))
    private <S> void figura$setupAnim(Model<?> model, Object object, Operation<Void> original,
                                      SubmitNodeStorage.ModelSubmit<S> modelSubmit, RenderType renderType, VertexConsumer vertexConsumer,
                                      OutlineBufferSource outlineBufferSource, MultiBufferSource.BufferSource bufferSource) {
        FiguraSubmitCallBackExtension callBackExtension = (FiguraSubmitCallBackExtension) (Object) modelSubmit;
        if (!callBackExtension.figura$getPreventAnimSetup()) {
            original.call(model, object);
        }
    }


    @WrapOperation(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 0))
    private <S> void figura$renderModel(Model<?> model, PoseStack renderPoseStack, VertexConsumer renderVertexConsumer, int light, int overlay, int color,
                                        Operation<Void> original, SubmitNodeStorage.ModelSubmit<S> modelSubmit, RenderType renderType, VertexConsumer vertexConsumer,
                                        OutlineBufferSource outlineBufferSource, MultiBufferSource.BufferSource bufferSource) {
        FiguraSubmitCallBackExtension callBackExtension = (FiguraSubmitCallBackExtension) (Object) modelSubmit;

        boolean renderVanilla = true;
        for (var callback : new ArrayList<>(callBackExtension.figura$getPreRenderingCallbacks())) {
            if (!callback.apply(bufferSource, poseStack)) {
                renderVanilla = false;
            }
        }
        callBackExtension.figura$getPreRenderingCallbacks().clear();
        figura$renderVanilla.put(modelSubmit, renderVanilla);

        if (renderVanilla) {
            original.call(model, renderPoseStack, renderVertexConsumer, light, overlay, color);
        }
    }

    @WrapOperation(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 1))
    private <S> void figura$renderModelOutline(Model<?> model, PoseStack renderPoseStack, VertexConsumer renderVertexConsumer, int light, int overlay, int color,
                                               Operation<Void> original, SubmitNodeStorage.ModelSubmit<S> modelSubmit, RenderType renderType, VertexConsumer vertexConsumer,
                                               OutlineBufferSource outlineBufferSource, MultiBufferSource.BufferSource bufferSource) {
        if (figura$renderVanilla.getOrDefault(modelSubmit, true)) {
            original.call(model, renderPoseStack, renderVertexConsumer, light, overlay, color);
        }
    }

    @WrapOperation(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 2))
    private <S> void figura$renderModelCrumbling(Model<?> model, PoseStack renderPoseStack, VertexConsumer renderVertexConsumer, int light, int overlay, int color,
                                                 Operation<Void> original, SubmitNodeStorage.ModelSubmit<S> modelSubmit, RenderType renderType, VertexConsumer vertexConsumer,
                                                 OutlineBufferSource outlineBufferSource, MultiBufferSource.BufferSource bufferSource) {
        if (figura$renderVanilla.getOrDefault(modelSubmit, true)) {
            original.call(model, renderPoseStack, renderVertexConsumer, light, overlay, color);
        }
    }

    @Inject(method = "renderModel", at = @At("RETURN"))
    private <S> void figura$afterRenderModel(SubmitNodeStorage.ModelSubmit<S> modelSubmit, RenderType renderType, VertexConsumer vertexConsumer,
                                             OutlineBufferSource outlineBufferSource, MultiBufferSource.BufferSource bufferSource, CallbackInfo ci) {
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
