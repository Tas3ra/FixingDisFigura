package org.figuramc.figura.config.neoforge;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.gui.screens.ConfigScreen;

public class ModConfig {
    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> (
                (client, parent) -> new ConfigScreen(parent, FiguraMod.debugModeEnabled())));
    }
}
