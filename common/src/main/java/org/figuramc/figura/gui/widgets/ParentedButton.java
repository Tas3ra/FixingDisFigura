package org.figuramc.figura.gui.widgets;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ParentedButton extends Button {

    private final AbstractContainerElement parent;

    public ParentedButton(int x, int y, int width, int height, Component text, AbstractContainerElement parent, OnPress pressAction) {
        super(x, y, width, height, text, null, pressAction);
        this.parent = parent;
    }

    public ParentedButton(int x, int y, int width, int height, int u, int v, int regionSize, Identifier texture, int textureWidth, int textureHeight, Component tooltip, AbstractContainerElement parent, OnPress pressAction) {
        super(x, y, width, height, u, v, regionSize, texture, textureWidth, textureHeight, tooltip, pressAction);
        this.parent = parent;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.parent.isHovered() && super.isMouseOver(mouseX, mouseY);
    }
}
