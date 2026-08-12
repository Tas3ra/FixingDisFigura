package org.figuramc.figura.utils.neoforge;

import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.figuramc.figura.mixin.neoforge.ElytraLayerAccessor;

public class RenderUtilsImpl {
    public static Identifier getPlayerSkinTexture(WingsLayer<?, ?> wingsLayer, HumanoidRenderState humanoidRenderState) {
        return ElytraLayerAccessor.invoke$getPlayerElytraTexture(humanoidRenderState);
    }
}
