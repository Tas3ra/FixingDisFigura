package org.figuramc.figura.gui.neoforge;


import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import org.figuramc.figura.gui.FiguraGui;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class GuiUnderlay implements LayeredDraw.Layer {
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        FiguraGui.onRender(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false), new CallbackInfo("dummy", true));
    }
}