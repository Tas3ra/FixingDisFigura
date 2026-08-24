package org.figuramc.figura.lua.api;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.model.rendering.texture.CustomFiguraRenderLayer;
import org.figuramc.figura.model.rendering.texture.FiguraRenderLayer;
import org.figuramc.figura.model.rendering.texture.FiguraRenderTypes;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;

@LuaWhitelist
@LuaTypeDoc(
        name = "LuaRenderTypesAPI",
        value = "render_types"
)
public class LuaRenderTypesAPI {
    private final Avatar owner;

    public LuaRenderTypesAPI(Avatar owner) {
        this.owner = owner;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = {String.class, LuaTable.class},
                            argumentNames = {"name", "options"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, String.class},
                            argumentNames = {"name", "base"}
                    )
            },
            aliases = {"new", "newType", "define"},
            value = "render_types.register"
    )
    public LuaRenderTypesAPI register(@LuaNotNil String name, Object options) {
        String base = "TRANSLUCENT";
        Boolean fullBright = null;
        Boolean offset = null;
        Boolean lineWidth = null;

        if (options instanceof LuaTable table) {
            base = stringOption(table, "base", base);
            fullBright = boolOption(table, "fullBright", boolOption(table, "fullbright", boolOption(table, "emissive", null)));
            offset = boolOption(table, "offset", null);
            lineWidth = boolOption(table, "lineWidth", boolOption(table, "line_width", null));
        } else if (options instanceof String s) {
            base = s;
        } else if (options != null) {
            throw new LuaError("Illegal argument to render_types.register(): expected table, string, or nil.");
        }

        owner.renderTypes.register(name, base, fullBright, offset, lineWidth);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "name"
            ),
            value = "render_types.remove"
    )
    public boolean remove(@LuaNotNil String name) {
        return owner.renderTypes.remove(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("render_types.clear")
    public LuaRenderTypesAPI clear() {
        for (CustomFiguraRenderLayer layer : owner.renderTypes.customTypes())
            owner.renderTypes.remove(layer.name());
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "name"
            ),
            value = "render_types.has"
    )
    public boolean has(@LuaNotNil String name) {
        return owner.renderTypes.resolve(name) != null;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "name"
            ),
            value = "render_types.get"
    )
    public LuaTable get(@LuaNotNil String name) {
        FiguraRenderLayer layer = owner.renderTypes.resolve(name);
        if (layer == null)
            return null;
        return toTable(layer);
    }

    @LuaWhitelist
    @LuaMethodDoc("render_types.list")
    public List<String> list() {
        List<String> names = new ArrayList<>();
        for (FiguraRenderTypes type : FiguraRenderTypes.values())
            names.add(type.name());
        for (CustomFiguraRenderLayer layer : owner.renderTypes.customTypes())
            names.add(layer.name());
        return names;
    }

    @LuaWhitelist
    @LuaMethodDoc("render_types.list_custom")
    public List<String> listCustom() {
        List<String> names = new ArrayList<>();
        for (CustomFiguraRenderLayer layer : owner.renderTypes.customTypes())
            names.add(layer.name());
        return names;
    }

    @LuaWhitelist
    @LuaMethodDoc("render_types.list_builtin")
    public List<String> listBuiltin() {
        List<String> names = new ArrayList<>();
        for (FiguraRenderTypes type : FiguraRenderTypes.values())
            names.add(type.name());
        return names;
    }

    private static LuaTable toTable(FiguraRenderLayer layer) {
        LuaTable table = new LuaTable();
        table.set("name", LuaValue.valueOf(layer.name()));
        table.set("base", LuaValue.valueOf(layer.baseType().name()));
        table.set("fullBright", LuaValue.valueOf(layer.isFullBright()));
        table.set("offset", LuaValue.valueOf(layer.isOffset()));
        table.set("lineWidth", LuaValue.valueOf(layer.needsLineWidth()));
        table.set("builtin", LuaValue.valueOf(layer instanceof FiguraRenderTypes));
        return table;
    }

    private static String stringOption(LuaTable table, String key, String fallback) {
        LuaValue value = table.get(key);
        return value.isnil() ? fallback : value.checkjstring();
    }

    private static Boolean boolOption(LuaTable table, String key, Boolean fallback) {
        LuaValue value = table.get(key);
        if (value.isnil())
            return fallback;
        return value.checkboolean();
    }
}
