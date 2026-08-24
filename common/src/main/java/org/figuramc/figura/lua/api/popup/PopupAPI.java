package org.figuramc.figura.lua.api.popup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.backend2.NetworkStuff;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.utils.IOUtils;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

@LuaWhitelist
@LuaTypeDoc(
        name = "PopupAPI",
        value = "popup"
)
public class PopupAPI {

    public static final int MAX_CONTROLS = 16;
    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_TITLE_LENGTH = 64;
    public static final int MAX_SLIDER_STEPS = 1000;
    public static final int SYNC_PING_ID = 0x46475055;
    private static final int SYNC_VERSION = 1;
    private static final int SYNC_TOGGLE = 0;
    private static final int SYNC_SLIDER = 1;
    private static final int SYNC_BUTTON = 2;
    private static final ThreadLocal<String> ACTIVE_CONTEXT = new ThreadLocal<>();

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final ReentrantLock STORE_LOCK = new ReentrantLock();
    private static JsonObject storedValues = new JsonObject();
    private static boolean loaded;

    private final Avatar owner;
    private final Map<String, PopupInput> inputs = new LinkedHashMap<>();
    private final Map<String, SyncedValue> pendingSync = new LinkedHashMap<>();

    private static class SyncedValue {
        final int type;
        final String context;
        final boolean boolValue;
        final double numberValue;

        SyncedValue(int type, String context, boolean boolValue, double numberValue) {
            this.type = type;
            this.context = context;
            this.boolValue = boolValue;
            this.numberValue = numberValue;
        }
    }

    public PopupAPI(Avatar owner) {
        this.owner = owner;
    }

    public static List<PopupInput> getInputs(Avatar avatar) {
        return getInputs(avatar, PopupInput.Target.ANY);
    }

    public static List<PopupInput> getInputs(Avatar avatar, PopupInput.Target target) {
        return getInputs(avatar, target, null);
    }

    public static List<PopupInput> getInputs(Avatar avatar, PopupInput.Target target, String headName) {
        if (avatar == null || avatar.luaRuntime == null || avatar.luaRuntime.popup == null)
            return List.of();
        return avatar.luaRuntime.popup.getInputList(target, headName);
    }

    public static boolean handleSync(Avatar avatar, byte[] data) {
        if (avatar == null || avatar.luaRuntime == null || avatar.luaRuntime.popup == null)
            return true;

        avatar.luaRuntime.popup.receiveSync(data);
        return true;
    }

    public static String getActiveContext() {
        return ACTIVE_CONTEXT.get();
    }

    public static void pushContext(String context) {
        ACTIVE_CONTEXT.set(context);
    }

    public static void popContext() {
        ACTIVE_CONTEXT.remove();
    }

    boolean canPublishSync() {
        return owner.isHost;
    }

    void syncEnabled(PopupInput input) {
        applyPendingSync(input);
        if (input.getInputType() != PopupInput.Type.BUTTON && (!input.isContextual() || getActiveContext() != null))
            syncNow(input);
    }

    private void syncNow(PopupInput input) {
        if (!input.isSynced() || !canPublishSync())
            return;

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(SYNC_VERSION);
            out.writeUTF(input.getId());
            out.writeUTF(input.isContextual() ? getActiveContext() == null ? "" : getActiveContext() : "");

            switch (input.getInputType()) {
                case TOGGLE -> {
                    out.writeByte(SYNC_TOGGLE);
                    out.writeBoolean(Boolean.TRUE.equals(input.getValue()));
                }
                case SLIDER -> {
                    out.writeByte(SYNC_SLIDER);
                    Object value = input.getValue();
                    out.writeDouble(value instanceof Number number ? number.doubleValue() : 0d);
                }
                case BUTTON -> out.writeByte(SYNC_BUTTON);
            }

            NetworkStuff.sendPing(SYNC_PING_ID, input.getInputType() != PopupInput.Type.BUTTON, bytes.toByteArray());
        } catch (Exception e) {
            FiguraMod.LOGGER.warn("Failed to sync popup input \"{}\"", input.getId(), e);
        }
    }

    private void receiveSync(byte[] data) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            if (in.readUnsignedByte() != SYNC_VERSION)
                return;

            String id = normalizeId(in.readUTF());
            String context = in.readUTF();
            if (context.isBlank())
                context = null;
            int type = in.readUnsignedByte();
            boolean boolValue = false;
            double numberValue = 0d;

            if (type == SYNC_TOGGLE)
                boolValue = in.readBoolean();
            else if (type == SYNC_SLIDER)
                numberValue = in.readDouble();
            else if (type != SYNC_BUTTON)
                return;

            PopupInput input = inputs.get(id);
            SyncedValue value = new SyncedValue(type, context, boolValue, numberValue);
            if (input == null || !input.isSynced()) {
                pendingSync.put(id, value);
                return;
            }

            applySync(input, value);
        } catch (Exception e) {
            FiguraMod.LOGGER.warn("Failed to receive popup sync for {}", owner.owner, e);
        }
    }

    private void applyPendingSync(PopupInput input) {
        SyncedValue value = pendingSync.remove(input.getId());
        if (value != null)
            applySync(input, value);
    }

    private void applySync(PopupInput input, SyncedValue value) {
        pushContext(value.context);
        try {
            if (value.type == SYNC_TOGGLE)
                input.applySyncedBoolean(value.boolValue);
            else if (value.type == SYNC_SLIDER)
                input.applySyncedNumber(value.numberValue);
            else if (value.type == SYNC_BUTTON)
                input.applySyncedPress();
        } finally {
            popContext();
        }
    }

    private static Path getPath() {
        return IOUtils.getOrCreateDir(FiguraMod.getFiguraDirectory(), "config").resolve("popup_inputs.json");
    }

    private static void ensureLoaded() {
        STORE_LOCK.lock();
        try {
            if (loaded)
                return;

            Path path = getPath();
            if (Files.exists(path)) {
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonElement element = GSON.fromJson(reader, JsonElement.class);
                    if (element != null && element.isJsonObject())
                        storedValues = element.getAsJsonObject();
                } catch (Exception e) {
                    FiguraMod.LOGGER.warn("Failed to load popup input data", e);
                    storedValues = new JsonObject();
                }
            }

            loaded = true;
        } finally {
            STORE_LOCK.unlock();
        }
    }

    private static void write() {
        Path path = getPath();
        try (OutputStream fs = Files.newOutputStream(path)) {
            fs.write(GSON.toJson(storedValues).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FiguraMod.LOGGER.warn("Failed to save popup input data", e);
        }
    }

    private String storeKey() {
        String avatarKey = owner.id == null || owner.id.isBlank() ? owner.name : owner.id;
        if (avatarKey == null || avatarKey.isBlank())
            avatarKey = "avatar";
        return owner.owner + "/" + avatarKey;
    }

    private Object readStored(String id) {
        ensureLoaded();
        STORE_LOCK.lock();
        try {
            JsonElement groupElement = storedValues.get(storeKey());
            if (groupElement == null || !groupElement.isJsonObject())
                return null;

            JsonElement valueElement = groupElement.getAsJsonObject().get(id);
            if (valueElement == null || !valueElement.isJsonPrimitive())
                return null;

            if (valueElement.getAsJsonPrimitive().isBoolean())
                return valueElement.getAsBoolean();
            if (valueElement.getAsJsonPrimitive().isNumber())
                return valueElement.getAsDouble();
            return null;
        } finally {
            STORE_LOCK.unlock();
        }
    }

    private void saveStored(PopupInput input) {
        if (input.isContextual())
            return;

        ensureLoaded();
        STORE_LOCK.lock();
        try {
            String key = storeKey();
            JsonElement groupElement = storedValues.get(key);
            JsonObject group;
            if (groupElement != null && groupElement.isJsonObject()) {
                group = groupElement.getAsJsonObject();
            } else {
                group = new JsonObject();
                storedValues.add(key, group);
            }

            Object value = input.getValue();
            if (value instanceof Boolean bool)
                group.addProperty(input.getId(), bool);
            else if (value instanceof Number number)
                group.addProperty(input.getId(), number.doubleValue());
            write();
        } finally {
            STORE_LOCK.unlock();
        }
    }

    private static String normalizeId(String id) {
        if (id == null || id.isBlank())
            throw new LuaError("Popup input id cannot be empty");

        String clean = id.trim();
        if (clean.length() > MAX_ID_LENGTH)
            throw new LuaError("Popup input id cannot be longer than " + MAX_ID_LENGTH + " characters");

        return clean;
    }

    private void checkRoom(String id) {
        if (!inputs.containsKey(id) && inputs.size() >= MAX_CONTROLS)
            throw new LuaError("An avatar can only register " + MAX_CONTROLS + " popup inputs");
    }

    private static LuaFunction asFunction(Object value) {
        return value instanceof LuaFunction function ? function : null;
    }

    private static double optionalNumber(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    void inputChanged(PopupInput input) {
        inputChanged(input, true);
    }

    void inputChanged(PopupInput input, boolean save) {
        inputChanged(input, save, true);
    }

    void inputChanged(PopupInput input, boolean save, boolean sync) {
        if (save)
            saveStored(input);
        if (sync)
            syncNow(input);
        LuaFunction callback = input.getCallback();
        if (callback != null && owner.luaRuntime != null)
            owner.run(callback, owner.tick, input.getValue(), input);
    }

    void persist(PopupInput input) {
        saveStored(input);
    }

    private List<PopupInput> getInputList(PopupInput.Target target, String headName) {
        return inputs.values().stream()
                .filter(input -> input.appliesTo(target))
                .filter(input -> input.matchesHeadName(headName))
                .toList();
    }

    public void addConfiguredInputs(ListTag controls) {
        if (controls == null)
            return;

        for (Tag tag : controls) {
            if (!(tag instanceof CompoundTag control))
                continue;

            String id = control.getStringOr("id", "").trim();
            if (id.isEmpty())
                continue;

            try {
                String type = control.getStringOr("type", "toggle").toLowerCase(Locale.US);
                String title = control.getStringOr("title", id);
                PopupInput input;
                if ("button".equals(type)) {
                    input = addButton(id, title, null);
                } else if ("slider".equals(type)) {
                    input = addSlider(
                            id,
                            title,
                            parseDouble(control.getStringOr("default", "0"), 0d),
                            parseDouble(control.getStringOr("min", "0"), 0d),
                            parseDouble(control.getStringOr("max", "1"), 1d),
                            parseDouble(control.getStringOr("step", "0"), 0d),
                            null
                    );
                } else {
                    input = addToggle(id, title, parseBoolean(control.getStringOr("default", "false")), null);
                }
                input.setTarget(control.getStringOr("target", "player"));
                if (control.contains("headName"))
                    input.setHeadName(control.getStringOr("headName", ""));
                else if (control.contains("head_name"))
                    input.setHeadName(control.getStringOr("head_name", ""));
                input.setSynced(parseBoolean(control.getStringOr("synced", "false")));
            } catch (LuaError e) {
                FiguraMod.LOGGER.warn("Failed to add configured popup input \"{}\"", id, e);
            }
        }
    }

    private static boolean parseBoolean(String value) {
        if (value == null)
            return false;

        return switch (value.trim().toLowerCase(Locale.US)) {
            case "true", "1", "yes", "on" -> true;
            default -> false;
        };
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null)
            return fallback;

        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = {String.class, Boolean.class},
                            argumentNames = {"id", "defaultValue"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, Boolean.class, LuaFunction.class},
                            argumentNames = {"id", "defaultValue", "onChange"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, String.class, Boolean.class},
                            argumentNames = {"id", "title", "defaultValue"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, String.class, Boolean.class, LuaFunction.class},
                            argumentNames = {"id", "title", "defaultValue", "onChange"}
                    )
            },
            aliases = "toggle",
            value = "popup.add_toggle"
    )
    public PopupInput addToggle(@LuaNotNil String id, Object titleOrDefault, Object defaultOrCallback, Object callback) {
        id = normalizeId(id);
        checkRoom(id);

        String title = null;
        boolean defaultValue = false;
        LuaFunction function = null;

        if (titleOrDefault instanceof String string) {
            title = string;
            if (defaultOrCallback instanceof Boolean bool)
                defaultValue = bool;
            else
                function = asFunction(defaultOrCallback);
            if (function == null)
                function = asFunction(callback);
        } else {
            if (titleOrDefault instanceof Boolean bool)
                defaultValue = bool;
            function = asFunction(defaultOrCallback);
            if (function == null)
                function = asFunction(callback);
        }

        PopupInput input = new PopupInput(this, id, title, defaultValue, function);
        input.loadStoredValue(readStored(id));
        inputs.put(id, input);
        return input;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = String.class,
                            argumentNames = "id"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, LuaFunction.class},
                            argumentNames = {"id", "onPress"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, String.class},
                            argumentNames = {"id", "title"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, String.class, LuaFunction.class},
                            argumentNames = {"id", "title", "onPress"}
                    )
            },
            aliases = "button",
            value = "popup.add_button"
    )
    public PopupInput addButton(@LuaNotNil String id, Object titleOrCallback, Object callback) {
        id = normalizeId(id);
        checkRoom(id);

        String title = null;
        LuaFunction function = null;

        if (titleOrCallback instanceof String string) {
            title = string;
            function = asFunction(callback);
        } else {
            function = asFunction(titleOrCallback);
            if (function == null)
                function = asFunction(callback);
        }

        PopupInput input = new PopupInput(this, id, title, function);
        inputs.put(id, input);
        return input;
    }

    @LuaWhitelist
    public PopupInput button(@LuaNotNil String id, Object titleOrCallback, Object callback) {
        return addButton(id, titleOrCallback, callback);
    }

    @LuaWhitelist
    public PopupInput toggle(@LuaNotNil String id, Object titleOrDefault, Object defaultOrCallback, Object callback) {
        return addToggle(id, titleOrDefault, defaultOrCallback, callback);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = {String.class, Double.class, Double.class, Double.class},
                            argumentNames = {"id", "defaultValue", "min", "max"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, Double.class, Double.class, Double.class, Double.class},
                            argumentNames = {"id", "defaultValue", "min", "max", "step"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, Double.class, Double.class, Double.class, Double.class, LuaFunction.class},
                            argumentNames = {"id", "defaultValue", "min", "max", "step", "onChange"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, String.class, Double.class, Double.class, Double.class},
                            argumentNames = {"id", "title", "defaultValue", "min", "max"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, String.class, Double.class, Double.class, Double.class, Double.class},
                            argumentNames = {"id", "title", "defaultValue", "min", "max", "step"}
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {String.class, String.class, Double.class, Double.class, Double.class, Double.class, LuaFunction.class},
                            argumentNames = {"id", "title", "defaultValue", "min", "max", "step", "onChange"}
                    )
            },
            aliases = "slider",
            value = "popup.add_slider"
    )
    public PopupInput addSlider(@LuaNotNil String id, Object titleOrDefault, Object defaultOrMin, Object minOrMax, Object maxOrStep, Object stepOrCallback, Object callback) {
        id = normalizeId(id);
        checkRoom(id);

        String title = null;
        double defaultNumber;
        double minValue;
        double maxValue;
        double stepValue;
        LuaFunction function;

        if (titleOrDefault instanceof String string) {
            title = string;
            defaultNumber = optionalNumber(defaultOrMin, 0d);
            minValue = optionalNumber(minOrMax, 0d);
            maxValue = optionalNumber(maxOrStep, 1d);
            stepValue = optionalNumber(stepOrCallback, 0d);
            function = asFunction(callback);
            if (function == null)
                function = asFunction(stepOrCallback);
        } else {
            defaultNumber = optionalNumber(titleOrDefault, 0d);
            minValue = optionalNumber(defaultOrMin, 0d);
            maxValue = optionalNumber(minOrMax, 1d);
            stepValue = optionalNumber(maxOrStep, 0d);
            function = asFunction(stepOrCallback);
            if (function == null)
                function = asFunction(callback);
        }

        PopupInput input = new PopupInput(this, id, title, defaultNumber, minValue, maxValue, stepValue, function);
        input.loadStoredValue(readStored(id));
        inputs.put(id, input);
        return input;
    }

    @LuaWhitelist
    public PopupInput slider(@LuaNotNil String id, Object titleOrDefault, Object defaultOrMin, Object minOrMax, Object maxOrStep, Object stepOrCallback, Object callback) {
        return addSlider(id, titleOrDefault, defaultOrMin, minOrMax, maxOrStep, stepOrCallback, callback);
    }

    @LuaWhitelist
    @LuaMethodDoc("popup.clear")
    public PopupAPI clear() {
        inputs.clear();
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "id"
            ),
            value = "popup.remove"
    )
    public PopupAPI remove(@LuaNotNil String id) {
        inputs.remove(normalizeId(id));
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "id",
                    returnType = PopupInput.class
            ),
            value = "popup.get_input"
    )
    public PopupInput getInput(@LuaNotNil String id) {
        return inputs.get(normalizeId(id));
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "id"
            ),
            value = "popup.get"
    )
    public Object get(@LuaNotNil String id) {
        PopupInput input = getInput(id);
        return input == null ? LuaValue.NIL : input.getValue();
    }

    @LuaWhitelist
    @LuaMethodDoc("popup.get_inputs")
    public LuaTable getInputs() {
        LuaTable table = new LuaTable();
        int i = 1;
        for (PopupInput input : inputs.values())
            table.set(i++, owner.luaRuntime.typeManager.javaToLua(input).arg1());
        return table;
    }

    @LuaWhitelist
    public PopupInput __index(@LuaNotNil String id) {
        return getInput(id);
    }

    @Override
    public String toString() {
        return "PopupAPI";
    }
}
