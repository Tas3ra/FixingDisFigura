package org.figuramc.figura.lua.api.popup;

import net.minecraft.util.Mth;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@LuaWhitelist
@LuaTypeDoc(
        name = "PopupInput",
        value = "popup_input"
)
public class PopupInput {

    public enum Type {
        TOGGLE,
        SLIDER,
        BUTTON
    }

    public enum Target {
        ANY,
        PLAYER,
        HEAD,
        ENTITY,
        HUD,
        WORLD;

        public static Target byName(Object value) {
            if (!(value instanceof String string) || string.isBlank())
                return PLAYER;

            return switch (string.trim().toLowerCase(Locale.US)) {
                case "all", "any", "*" -> ANY;
                case "head", "heads", "skull", "skulls", "profile", "item" -> HEAD;
                case "entity", "entities" -> ENTITY;
                case "hud", "gui", "screen" -> HUD;
                case "world", "block" -> WORLD;
                default -> PLAYER;
            };
        }
    }

    private final PopupAPI owner;
    private final String id;
    private final Type type;
    private final Object defaultValue;
    private String title;
    private Target target = Target.PLAYER;
    private boolean toggleValue;
    private double sliderValue;
    private double min;
    private double max;
    private double step;
    private LuaFunction callback;
    private boolean synced;
    private boolean contextual;
    private String headNameFilter;
    private final Map<String, Object> contextualValues = new HashMap<>();

    PopupInput(PopupAPI owner, String id, String title, boolean defaultValue, LuaFunction callback) {
        this.owner = owner;
        this.id = id;
        this.title = cleanTitle(title, id);
        this.type = Type.TOGGLE;
        this.defaultValue = defaultValue;
        this.toggleValue = defaultValue;
        this.callback = callback;
    }

    PopupInput(PopupAPI owner, String id, String title, LuaFunction callback) {
        this.owner = owner;
        this.id = id;
        this.title = cleanTitle(title, id);
        this.type = Type.BUTTON;
        this.defaultValue = false;
        this.callback = callback;
    }

    PopupInput(PopupAPI owner, String id, String title, double defaultValue, double min, double max, double step, LuaFunction callback) {
        this.owner = owner;
        this.id = id;
        this.title = cleanTitle(title, id);
        this.type = Type.SLIDER;
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        this.step = sanitizeStep(step, this.min, this.max);
        this.defaultValue = normalizeSlider(defaultValue);
        this.sliderValue = (double) this.defaultValue;
        this.callback = callback;
    }

    static String cleanTitle(String title, String fallback) {
        String clean = title == null || title.isBlank() ? fallback : title.trim();
        return clean.length() > PopupAPI.MAX_TITLE_LENGTH ? clean.substring(0, PopupAPI.MAX_TITLE_LENGTH) : clean;
    }

    static double sanitizeStep(double step, double min, double max) {
        double range = Math.abs(max - min);
        if (!Double.isFinite(step) || step <= 0d)
            return range <= 0d ? 1d : range / 20d;
        if (range > 0d && range / step > PopupAPI.MAX_SLIDER_STEPS)
            return range / PopupAPI.MAX_SLIDER_STEPS;
        return step;
    }

    void loadStoredValue(Object value) {
        if (type == Type.TOGGLE && value instanceof Boolean bool)
            toggleValue = bool;
        else if (type == Type.SLIDER && value instanceof Number number)
            sliderValue = normalizeSlider(number.doubleValue());
    }

    private double normalizeSlider(double value) {
        if (!Double.isFinite(value))
            value = min;

        double clamped = Mth.clamp(value, min, max);
        if (step <= 0d || max == min)
            return clamped;

        double stepped = min + Math.round((clamped - min) / step) * step;
        return Mth.clamp(stepped, min, max);
    }

    public boolean setUserBoolean(boolean value) {
        ensureType(Type.TOGGLE);
        String context = contextKey();
        boolean oldValue = context == null ? toggleValue : contextBoolean(context);
        if (oldValue == value)
            return false;

        if (context == null)
            toggleValue = value;
        else
            contextualValues.put(context, value);
        owner.inputChanged(this);
        return true;
    }

    public boolean setUserNumber(double value) {
        return setUserNumber(value, true);
    }

    public boolean setUserNumber(double value, boolean save) {
        ensureType(Type.SLIDER);
        double normalized = normalizeSlider(value);
        String context = contextKey();
        double oldValue = context == null ? sliderValue : contextNumber(context);
        if (Double.compare(oldValue, normalized) == 0)
            return false;

        if (context == null)
            sliderValue = normalized;
        else
            contextualValues.put(context, normalized);
        owner.inputChanged(this, save);
        return true;
    }

    public void nudge(double direction) {
        ensureType(Type.SLIDER);
        setUserNumber(sliderValue + Math.signum(direction) * step);
    }

    @LuaWhitelist
    @LuaMethodDoc("popup_input.press")
    public void press() {
        ensureType(Type.BUTTON);
        owner.inputChanged(this, false);
    }

    boolean applySyncedBoolean(boolean value) {
        if (type != Type.TOGGLE)
            return false;
        String context = contextKey();
        boolean oldValue = context == null ? toggleValue : contextBoolean(context);
        if (oldValue == value)
            return true;

        if (context == null)
            toggleValue = value;
        else
            contextualValues.put(context, value);
        owner.inputChanged(this, false, false);
        return true;
    }

    boolean applySyncedNumber(double value) {
        if (type != Type.SLIDER)
            return false;

        double normalized = normalizeSlider(value);
        String context = contextKey();
        double oldValue = context == null ? sliderValue : contextNumber(context);
        if (Double.compare(oldValue, normalized) == 0)
            return true;

        if (context == null)
            sliderValue = normalized;
        else
            contextualValues.put(context, normalized);
        owner.inputChanged(this, false, false);
        return true;
    }

    boolean applySyncedPress() {
        if (type != Type.BUTTON)
            return false;

        owner.inputChanged(this, false, false);
        return true;
    }

    public void persist() {
        owner.persist(this);
    }

    private void ensureType(Type type) {
        if (this.type != type)
            throw new LuaError("Popup input \"" + id + "\" is a " + this.type.name().toLowerCase(Locale.US));
    }

    public double getProgress() {
        Object value = getValue();
        double number = value instanceof Number num ? num.doubleValue() : sliderValue;
        return max == min ? 0d : Mth.clamp((number - min) / (max - min), 0d, 1d);
    }

    public String getDisplayValue() {
        if (type == Type.BUTTON)
            return "Run";
        if (type == Type.TOGGLE)
            return Boolean.TRUE.equals(getValue()) ? "On" : "Off";

        Object current = getValue();
        double number = current instanceof Number num ? num.doubleValue() : sliderValue;
        String value = String.format(Locale.ROOT, "%.3f", number);
        while (value.contains(".") && value.endsWith("0"))
            value = value.substring(0, value.length() - 1);
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }

    @LuaWhitelist
    @LuaMethodDoc("popup_input.get_id")
    public String getId() {
        return id;
    }

    @LuaWhitelist
    @LuaMethodDoc("popup_input.get_type")
    public String getType() {
        return type.name().toLowerCase(Locale.US);
    }

    public Type getInputType() {
        return type;
    }

    @LuaWhitelist
    @LuaMethodDoc("popup_input.get_target")
    public String getTarget() {
        return target.name().toLowerCase(Locale.US);
    }

    public Target getTargetType() {
        return target;
    }

    public boolean appliesTo(Target activeTarget) {
        return target == Target.ANY || activeTarget == Target.ANY || target == activeTarget;
    }

    boolean matchesHeadName(String headName) {
        return target != Target.HEAD || headNameFilter == null || headNameFilter.equals(normalizeHeadName(headName));
    }

    @LuaWhitelist
    @LuaMethodDoc("popup_input.set_target")
    public PopupInput setTarget(@LuaNotNil String target) {
        this.target = Target.byName(target);
        this.contextual = this.target == Target.HEAD;
        if (this.contextual)
            setSynced(true);
        return this;
    }

    @LuaWhitelist
    public PopupInput set_target(@LuaNotNil String target) {
        return setTarget(target);
    }

    @LuaWhitelist
    public PopupInput target(@LuaNotNil String target) {
        return setTarget(target);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Boolean.class,
                    argumentNames = "synced"
            ),
            aliases = "synced",
            value = "popup_input.set_synced"
    )
    public PopupInput setSynced(Boolean synced) {
        this.synced = this.target == Target.HEAD || Boolean.TRUE.equals(synced);
        if (this.synced)
            owner.syncEnabled(this);
        return this;
    }

    @LuaWhitelist
    public PopupInput set_synced(Boolean synced) {
        return setSynced(synced);
    }

    @LuaWhitelist
    public PopupInput synced(Boolean synced) {
        return setSynced(synced);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "name"
            ),
            aliases = "headName",
            value = "popup_input.set_head_name"
    )
    public PopupInput setHeadName(String name) {
        this.headNameFilter = normalizeHeadName(name);
        return setTarget("head");
    }

    @LuaWhitelist
    public PopupInput set_head_name(String name) {
        return setHeadName(name);
    }

    @LuaWhitelist
    public PopupInput headName(String name) {
        return setHeadName(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("popup_input.get_head_name")
    public String getHeadName() {
        return headNameFilter;
    }

    @LuaWhitelist
    @LuaMethodDoc("popup_input.is_synced")
    public boolean isSynced() {
        return synced;
    }

    public boolean isContextual() {
        return contextual;
    }

    @LuaWhitelist
    @LuaMethodDoc("popup_input.get_title")
    public String getTitle() {
        return title;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "title"
            ),
            aliases = "title",
            value = "popup_input.set_title"
    )
    public PopupInput setTitle(String title) {
        this.title = cleanTitle(title, id);
        return this;
    }

    @LuaWhitelist
    public PopupInput title(String title) {
        return setTitle(title);
    }

    @LuaWhitelist
    @LuaMethodDoc("popup_input.get_value")
    public Object getValue() {
        String context = contextKey();
        if (context != null && contextualValues.containsKey(context))
            return contextualValues.get(context);

        return switch (type) {
            case TOGGLE -> toggleValue;
            case SLIDER -> sliderValue;
            case BUTTON -> true;
        };
    }

    @LuaWhitelist
    @LuaMethodDoc("popup_input.get_default")
    public Object getDefault() {
        return defaultValue;
    }

    @LuaWhitelist
    @LuaMethodDoc("popup_input.get_min")
    public Double getMin() {
        return type == Type.SLIDER ? min : null;
    }

    @LuaWhitelist
    @LuaMethodDoc("popup_input.get_max")
    public Double getMax() {
        return type == Type.SLIDER ? max : null;
    }

    @LuaWhitelist
    @LuaMethodDoc("popup_input.get_step")
    public Double getStep() {
        return type == Type.SLIDER ? step : null;
    }

    private String contextKey() {
        String context = PopupAPI.getActiveContext();
        return contextual && context != null && !context.isBlank() ? context : null;
    }

    private boolean contextBoolean(String context) {
        Object value = contextualValues.get(context);
        return value instanceof Boolean bool ? bool : toggleValue;
    }

    private double contextNumber(String context) {
        Object value = contextualValues.get(context);
        return value instanceof Number number ? number.doubleValue() : sliderValue;
    }

    private static String normalizeHeadName(String name) {
        if (name == null)
            return null;

        String clean = name.trim();
        if (clean.isEmpty())
            return null;

        while (clean.length() >= 2 && clean.startsWith("\"") && clean.endsWith("\""))
            clean = clean.substring(1, clean.length() - 1).trim();

        return clean.isEmpty() ? null : clean.toLowerCase(Locale.US);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = LuaFunction.class,
                    argumentNames = "function"
            ),
            aliases = "onChange",
            value = "popup_input.set_on_change"
    )
    public PopupInput setOnChange(LuaFunction function) {
        this.callback = function;
        return this;
    }

    @LuaWhitelist
    public PopupInput onChange(LuaFunction function) {
        return setOnChange(function);
    }

    LuaFunction getCallback() {
        return callback;
    }

    @LuaWhitelist
    public Object __index(String key) {
        return switch (key) {
            case "id" -> getId();
            case "title" -> getTitle();
            case "type" -> getType();
            case "target" -> getTarget();
            case "headName" -> getHeadName();
            case "value" -> getValue();
            case "default" -> getDefault();
            case "min" -> getMin();
            case "max" -> getMax();
            case "step" -> getStep();
            case "synced" -> isSynced();
            case "onChange" -> callback;
            default -> null;
        };
    }

    @LuaWhitelist
    public void __newindex(@LuaNotNil String key, Object value) {
        switch (key) {
            case "title" -> setTitle(value instanceof String string ? string : null);
            case "headName" -> setHeadName(value instanceof String string ? string : null);
            case "synced" -> setSynced(value instanceof Boolean bool && bool);
            case "onChange" -> setOnChange(value instanceof LuaFunction function ? function : null);
            default -> throw new LuaError("Cannot assign value on key \"" + key + "\"");
        }
    }

    @Override
    public String toString() {
        return "PopupInput(" + id + ")";
    }
}
