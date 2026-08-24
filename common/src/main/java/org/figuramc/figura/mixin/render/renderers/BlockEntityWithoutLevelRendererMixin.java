package org.figuramc.figura.mixin.render.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.ducks.PlayerHeadRenderInfoExtension;
import org.figuramc.figura.ducks.SkullBlockRendererAccessor;
import org.figuramc.figura.ducks.SkullBlockRendererHelper;
import org.figuramc.figura.ducks.SkullSpecialRendererExtension;
import org.figuramc.figura.gui.ViewerVisibilityManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerHeadSpecialRenderer.class)
public class BlockEntityWithoutLevelRendererMixin implements SkullSpecialRendererExtension {

    @Unique
    private ItemStack figura$stack;

    @Inject(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;", at = @At("HEAD"), require = 0)
    private void figura$saveTargetItem(ItemStack stack, CallbackInfoReturnable<PlayerSkinRenderCache.RenderInfo> cir) {
        figura$stack = stack;
        if (stack != null)
            SkullBlockRendererAccessor.setItem(stack);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/SkullBlockRenderer;submitSkull(Lnet/minecraft/core/Direction;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/model/object/skull/SkullModelBase;Lnet/minecraft/client/renderer/rendertype/RenderType;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"), require = 0)
    void setTargetItem(PlayerSkinRenderCache.RenderInfo renderInfo, ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k, CallbackInfo ci) {
        ItemStack itemStack = SkullBlockRendererAccessor.getItem();
        if (itemStack == null)
            itemStack = figura$stack;
        if (itemStack == null && renderInfo != null)
            itemStack = ((PlayerHeadRenderInfoExtension)(Object)renderInfo).figura$getItemStack();
        if (itemStack != null) {
            SkullBlockRendererAccessor.setItem(itemStack);
            Avatar itemAvatar = AvatarManager.getAvatarForItem(itemStack);
            if (itemAvatar != null && ViewerVisibilityManager.areCustomSkullsVisible(itemAvatar.owner))
                SkullBlockRendererHelper.setAvatar(itemAvatar);
        }
        if (SkullBlockRendererAccessor.getRenderMode() == SkullBlockRendererAccessor.SkullRenderMode.OTHER && itemDisplayContext == ItemDisplayContext.GUI)
            SkullBlockRendererAccessor.setRenderMode(SkullBlockRendererAccessor.SkullRenderMode.GUI);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V", at = @At("RETURN"), require = 0)
    void clearTargetItem(PlayerSkinRenderCache.RenderInfo renderInfo, ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k, CallbackInfo ci) {
        figura$stack = null;
        SkullBlockRendererAccessor.clear();
        SkullBlockRendererHelper.clear();
    }

    @Override
    public ItemStack figura$getItemStack() {
        ItemStack itemStack = SkullBlockRendererAccessor.getItem();
        return itemStack != null ? itemStack : figura$stack;
    }
}
