package org.figuramc.figura.mixin.render.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.figuramc.figura.ducks.FiguraSubmitCallBackExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemFeatureRenderer.class)
public class ItemFeatureRendererMixin {

    @Shadow
    @Final
    private PoseStack poseStack;

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollection;getItemSubmits()Ljava/util/List;"))
    private List<SubmitNodeStorage.ItemSubmit> figura$snapshotItemSubmits(SubmitNodeCollection submitNodeCollection,
                                                                          Operation<List<SubmitNodeStorage.ItemSubmit>> original) {
        return new ArrayList<>(original.call(submitNodeCollection));
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderItem(Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II[ILjava/util/List;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V", ordinal = 0))
    private void figura$renderItem(ItemDisplayContext displayContext, PoseStack renderPoseStack, MultiBufferSource multiBufferSource,
                                   int light, int overlay, int[] tintLayers, List<?> layers, RenderType renderType,
                                   ItemStackRenderState.FoilType foilType, Operation<Void> original,
                                   SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource,
                                   OutlineBufferSource outlineBufferSource, @Local SubmitNodeStorage.ItemSubmit itemSubmit) {
        FiguraSubmitCallBackExtension callBackExtension = (FiguraSubmitCallBackExtension) (Object) itemSubmit;

        boolean renderVanilla = true;
        for (var callback : new ArrayList<>(callBackExtension.figura$getPreRenderingCallbacks())) {
            if (!callback.apply(bufferSource, poseStack)) {
                renderVanilla = false;
            }
        }
        callBackExtension.figura$getPreRenderingCallbacks().clear();

        try {
            if (renderVanilla) {
                original.call(displayContext, renderPoseStack, multiBufferSource, light, overlay, tintLayers, layers, renderType, foilType);
            }
        } finally {
            for (var callback : new ArrayList<>(callBackExtension.figura$getPostRenderingCallbacks()))
                callback.run();

            callBackExtension.figura$getPostRenderingCallbacks().clear();
        }
    }
}
