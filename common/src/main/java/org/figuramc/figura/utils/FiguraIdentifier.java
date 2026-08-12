package org.figuramc.figura.utils;

import net.minecraft.util.Util;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.Identifier;
import org.figuramc.figura.FiguraMod;

public class FiguraIdentifier extends Identifier {

    public FiguraIdentifier(String string) {
        super(FiguraMod.MOD_ID, string);
    }

    public static String formatPath(String path) {
        return Util.sanitizeName(path, Identifier::validPathChar);
    }
}
