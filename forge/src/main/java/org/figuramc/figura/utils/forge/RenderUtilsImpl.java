package org.figuramc.figura.utils.forge;

import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.figuramc.figura.mixin.forge.ElytraLayerAccessor;

public class RenderUtilsImpl {
    public static Identifier getPlayerSkinTexture(WingsLayer<?, ?> wingsLayer, HumanoidRenderState humanoidRenderState) {
        return ((ElytraLayerAccessor)wingsLayer).invoke$getPlayerElytraTexture(humanoidRenderState);
    }
}
