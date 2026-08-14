package org.figuramc.figura.mixin.render.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.ducks.FiguraItemStackRenderStateExtension;
import org.figuramc.figura.ducks.SkullBlockRendererAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class ItemStackRenderState$LayerRenderStateMixin {
    @Shadow
    @Final
    ItemStackRenderState field_55345;

    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/special/SpecialModelRenderer;submit(Ljava/lang/Object;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V"))
    private void figura$setCurrentItem(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, int overlay, int outlineColor, CallbackInfo ci) {
        ItemStack stack = ((FiguraItemStackRenderStateExtension)this.field_55345).figura$getItemStack();
        if (stack != null)
            SkullBlockRendererAccessor.setItem(stack);
    }
}
