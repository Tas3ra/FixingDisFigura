package org.figuramc.figura.mixin.render.renderers;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.ducks.PlayerHeadRenderInfoExtension;
import org.figuramc.figura.ducks.SkullBlockRendererAccessor;
import org.figuramc.figura.ducks.SkullBlockRendererHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerHeadSpecialRenderer.class)
public abstract class PlayerHeadSpecialRendererMixin {
    @ModifyReturnValue(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;", at = @At("TAIL"))
    public PlayerSkinRenderCache.RenderInfo setAvatar(PlayerSkinRenderCache.RenderInfo original, @Local(argsOnly = true) ItemStack itemStack) {
        if (original == null) {
            SkullBlockRendererHelper.clear();
            SkullBlockRendererAccessor.clear();
            return null;
        }

        ResolvableProfile profile = itemStack == null ? null : itemStack.get(DataComponents.PROFILE);
        Avatar avatar = AvatarManager.getAvatarForProfile(profile);
        if (avatar == null && original.gameProfile() != null && original.gameProfile().id() != null)
            avatar = AvatarManager.getAvatarForPlayer(original.gameProfile().id());
        PlayerHeadRenderInfoExtension extension = (PlayerHeadRenderInfoExtension)(Object)original;
        extension.figura$setAvatar(avatar);
        extension.figura$setItemStack(null);
        return original;
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V", at = @At("HEAD"))
    private void captureAvatar(PlayerSkinRenderCache.RenderInfo playerHeadRenderInfo, ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k, CallbackInfo ci) {
        ItemStack contextStack = SkullBlockRendererAccessor.getItem();
        Entity contextEntity = SkullBlockRendererAccessor.getEntity();
        SkullBlockRendererAccessor.SkullRenderMode contextMode = SkullBlockRendererAccessor.getRenderMode();

        SkullBlockRendererHelper.clear();
        SkullBlockRendererAccessor.clear();

        if (playerHeadRenderInfo == null) {
            SkullBlockRendererHelper.setAvatar(AvatarManager.getAvatarForItem(contextStack));
            figura$restoreRenderContext(contextStack, contextEntity, contextMode);
            return;
        }

        PlayerHeadRenderInfoExtension extension = (PlayerHeadRenderInfoExtension)(Object)playerHeadRenderInfo;
        ItemStack itemStack = contextStack != null ? contextStack : extension.figura$getItemStack();
        Avatar avatar = itemStack != null ? AvatarManager.getAvatarForItem(itemStack) : extension.figura$getAvatar();
        if (avatar == null && playerHeadRenderInfo.gameProfile() != null && playerHeadRenderInfo.gameProfile().id() != null)
            avatar = AvatarManager.getAvatarForPlayer(playerHeadRenderInfo.gameProfile().id());

        SkullBlockRendererHelper.setAvatar(avatar);
        figura$restoreRenderContext(itemStack, contextEntity, contextMode);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V", at = @At("RETURN"))
    private void clearAvatar(PlayerSkinRenderCache.RenderInfo playerHeadRenderInfo, ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k, CallbackInfo ci) {
        SkullBlockRendererHelper.clear();
        SkullBlockRendererAccessor.clear();
    }

    private static void figura$restoreRenderContext(ItemStack itemStack, Entity entity, SkullBlockRendererAccessor.SkullRenderMode mode) {
        if (itemStack != null)
            SkullBlockRendererAccessor.setItem(itemStack);
        if (entity != null)
            SkullBlockRendererAccessor.setEntity(entity);
        SkullBlockRendererAccessor.setRenderMode(mode);
    }
}
