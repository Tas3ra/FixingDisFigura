package org.figuramc.figura.model.rendering.texture;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public interface FiguraRenderLayer {
    String name();

    FiguraRenderTypes baseType();

    boolean isOffset();

    boolean isFullBright();

    boolean needsLineWidth();

    RenderType get(Identifier id);
}
