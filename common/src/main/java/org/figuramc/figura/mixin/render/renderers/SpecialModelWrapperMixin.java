package org.figuramc.figura.mixin.render.renderers;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Supplier;

@Mixin(SpecialModelWrapper.class)
public class SpecialModelWrapperMixin<T> {
    @Shadow @Final private SpecialModelRenderer<T> specialRenderer;

    @Unique
    private static final Vector3fc[] FIGURA_HEAD_GUI_EXTENTS = new Vector3fc[] {
            new Vector3f(-0.75f, -0.75f, -0.75f),
            new Vector3f(0.75f, 0.75f, 0.75f)
    };
    @Unique
    private static final Supplier<Vector3fc[]> FIGURA_HEAD_GUI_EXTENTS_SUPPLIER = () -> FIGURA_HEAD_GUI_EXTENTS;

    @Inject(method = "update", at = @At(value = "RETURN"))
    public void update(CallbackInfo ci, @Local(argsOnly = true) ItemStackRenderState itemStackRenderState, @Local(argsOnly = true)ItemStack stack, @Local ItemStackRenderState.LayerRenderState layerRenderState) {
        if (!(specialRenderer instanceof PlayerHeadSpecialRenderer))
            return;

        Avatar avatar = AvatarManager.getAvatarForItem(stack);
        if (avatar != null) {
            itemStackRenderState.setAnimated();
            itemStackRenderState.setOversizedInGui(true);
            layerRenderState.setExtents(FIGURA_HEAD_GUI_EXTENTS_SUPPLIER);
        }
    }
}
