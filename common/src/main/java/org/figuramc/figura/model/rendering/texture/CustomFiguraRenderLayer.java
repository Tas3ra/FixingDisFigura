package org.figuramc.figura.model.rendering.texture;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public class CustomFiguraRenderLayer implements FiguraRenderLayer {
    private final String name;
    private final FiguraRenderTypes baseType;
    private final Boolean fullBright;
    private final Boolean offset;
    private final Boolean lineWidth;

    public CustomFiguraRenderLayer(String name, FiguraRenderTypes baseType, Boolean fullBright, Boolean offset, Boolean lineWidth) {
        this.name = name;
        this.baseType = baseType == null ? FiguraRenderTypes.TRANSLUCENT : baseType;
        this.fullBright = fullBright;
        this.offset = offset;
        this.lineWidth = lineWidth;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public FiguraRenderTypes baseType() {
        return baseType;
    }

    @Override
    public boolean isOffset() {
        return offset != null ? offset : baseType.isOffset();
    }

    @Override
    public boolean isFullBright() {
        return fullBright != null ? fullBright : baseType.isFullBright();
    }

    @Override
    public boolean needsLineWidth() {
        return lineWidth != null ? lineWidth : baseType.needsLineWidth();
    }

    @Override
    public RenderType get(Identifier id) {
        return baseType.get(id);
    }
}
