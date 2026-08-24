package org.figuramc.figura.model.rendering.texture;

import org.luaj.vm2.LuaError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class LuaRenderTypeRegistry {
    public static final int MAX_CUSTOM_TYPES = 64;
    public static final int MAX_NAME_LENGTH = 64;

    private final Map<String, CustomFiguraRenderLayer> customTypes = new LinkedHashMap<>();

    public FiguraRenderLayer resolve(String name) {
        if (name == null)
            return null;

        String normalized = normalize(name);
        FiguraRenderTypes builtin = FiguraRenderTypes.byName(normalized);
        if (builtin != null)
            return builtin;
        return customTypes.get(normalized);
    }

    public CustomFiguraRenderLayer register(String name, String base, Boolean fullBright, Boolean offset, Boolean lineWidth) {
        String normalized = validateName(name);
        FiguraRenderTypes baseType = FiguraRenderTypes.byName(base == null ? "TRANSLUCENT" : base);
        if (baseType == null)
            throw new LuaError("Illegal base RenderType: \"" + base + "\".");

        if (FiguraRenderTypes.byName(normalized) != null)
            throw new LuaError("Cannot replace built-in RenderType: \"" + name + "\".");
        if (!customTypes.containsKey(normalized) && customTypes.size() >= MAX_CUSTOM_TYPES)
            throw new LuaError("Too many custom RenderTypes. Max is " + MAX_CUSTOM_TYPES + ".");

        CustomFiguraRenderLayer layer = new CustomFiguraRenderLayer(name, baseType, fullBright, offset, lineWidth);
        customTypes.put(normalized, layer);
        return layer;
    }

    public boolean remove(String name) {
        if (name == null)
            return false;
        return customTypes.remove(normalize(name)) != null;
    }

    public CustomFiguraRenderLayer getCustom(String name) {
        if (name == null)
            return null;
        return customTypes.get(normalize(name));
    }

    public Collection<CustomFiguraRenderLayer> customTypes() {
        return new ArrayList<>(customTypes.values());
    }

    public static String normalize(String name) {
        return name.trim().toUpperCase(Locale.US);
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank())
            throw new LuaError("RenderType name cannot be empty.");
        if (name.length() > MAX_NAME_LENGTH)
            throw new LuaError("RenderType name is too long. Max is " + MAX_NAME_LENGTH + " characters.");

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = c == '_' || c == '-' || c == ':' || c == '.' || Character.isLetterOrDigit(c);
            if (!allowed)
                throw new LuaError("RenderType name contains an illegal character: \"" + c + "\".");
        }

        return normalize(name);
    }
}
