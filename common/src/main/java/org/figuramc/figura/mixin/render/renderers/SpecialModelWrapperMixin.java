package org.figuramc.figura.mixin.render.renderers;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpecialModelWrapper.class)
public class SpecialModelWrapperMixin<T> {
    @Shadow @Final private SpecialModelRenderer<T> specialRenderer;

    @Inject(method = "update", at = @At(value = "RETURN"))
    public void update(ItemStackRenderState itemStackRenderState, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, ClientLevel level, ItemOwner owner, int seed, CallbackInfo ci, @Local ItemStackRenderState.LayerRenderState layerRenderState) {
        if (!(specialRenderer instanceof PlayerHeadSpecialRenderer))
            return;
        if (displayContext != ItemDisplayContext.GUI)
            return;

        Avatar avatar = AvatarManager.getAvatarForItem(stack);
        if (avatar != null) {
            itemStackRenderState.setAnimated();
        }
    }
}
