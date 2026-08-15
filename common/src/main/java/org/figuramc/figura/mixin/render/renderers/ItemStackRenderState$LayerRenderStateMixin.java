package org.figuramc.figura.mixin.render.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.ducks.FiguraItemStackRenderStateExtension;
import org.figuramc.figura.ducks.SkullBlockRendererAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class ItemStackRenderState$LayerRenderStateMixin {
    @Shadow
    @Final
    ItemStackRenderState field_55345;

    @Unique
    private ItemStack figura$previousSkullItem;
    @Unique
    private Entity figura$previousSkullEntity;
    @Unique
    private SkullBlockRendererAccessor.SkullRenderMode figura$previousSkullMode;
    @Unique
    private boolean figura$hasScopedSkullContext;
    @Unique
    private boolean figura$scopedSkullContextIsGui;

    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/special/SpecialModelRenderer;submit(Ljava/lang/Object;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V"))
    private void figura$setCurrentItem(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, int overlay, int outlineColor, CallbackInfo ci) {
        figura$previousSkullItem = SkullBlockRendererAccessor.getItem();
        figura$previousSkullEntity = SkullBlockRendererAccessor.getEntity();
        figura$previousSkullMode = SkullBlockRendererAccessor.getRenderMode();
        figura$hasScopedSkullContext = true;

        FiguraItemStackRenderStateExtension renderState = (FiguraItemStackRenderStateExtension)this.field_55345;
        figura$scopedSkullContextIsGui = renderState.figura$getDisplayContext() == ItemDisplayContext.GUI;
        ItemStack stack = renderState.figura$getItemStack();
        if (stack != null)
            SkullBlockRendererAccessor.setItem(stack);
        if (SkullBlockRendererAccessor.getRenderMode() == SkullBlockRendererAccessor.SkullRenderMode.OTHER && figura$scopedSkullContextIsGui)
            SkullBlockRendererAccessor.setRenderMode(SkullBlockRendererAccessor.SkullRenderMode.GUI);
    }

    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/special/SpecialModelRenderer;submit(Ljava/lang/Object;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V", shift = At.Shift.AFTER))
    private void figura$restoreCurrentItem(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, int overlay, int outlineColor, CallbackInfo ci) {
        if (!figura$hasScopedSkullContext)
            return;

        SkullBlockRendererAccessor.clear();
        if (!figura$scopedSkullContextIsGui && figura$previousSkullItem != null)
            SkullBlockRendererAccessor.setItem(figura$previousSkullItem);
        if (!figura$scopedSkullContextIsGui && figura$previousSkullEntity != null)
            SkullBlockRendererAccessor.setEntity(figura$previousSkullEntity);
        if (!figura$scopedSkullContextIsGui && figura$previousSkullMode != null)
            SkullBlockRendererAccessor.setRenderMode(figura$previousSkullMode);

        figura$previousSkullItem = null;
        figura$previousSkullEntity = null;
        figura$previousSkullMode = null;
        figura$hasScopedSkullContext = false;
        figura$scopedSkullContextIsGui = false;
    }
}
