package org.figuramc.figura.ducks;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.render.GuiRenderer;
import org.joml.Matrix4fc;

public interface GameRendererAccessor {

    double figura$getFov(Camera camera, float tickDelta, boolean changingFov);

    GuiRenderer figura$getGuiRenderer();

    Matrix4fc figura$getFiguraViewMatrix();
}
