package org.figuramc.figura.gui.screens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.local.LocalAvatarLoader;
import org.figuramc.figura.gui.FiguraToast;
import org.figuramc.figura.gui.widgets.AbstractContainerElement;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.Label;
import org.figuramc.figura.gui.widgets.ScrollBarWidget;
import org.figuramc.figura.gui.widgets.SwitchButton;
import org.figuramc.figura.gui.widgets.TextField;
import org.figuramc.figura.gui.widgets.FiguraWidget;
import org.figuramc.figura.lua.api.popup.PopupAPI;
import org.figuramc.figura.lua.api.popup.PopupInput;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.IOUtils;
import org.figuramc.figura.utils.TextUtils;
import org.figuramc.figura.utils.ui.UIHelper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AvatarConfigEditorScreen extends AbstractPanelScreen {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final int FIELD_HEIGHT = 20;
    private static final int SCRIPT_ROW_HEIGHT = 22;
    private static final int POPUP_ROW_HEIGHT = 120;

    private final List<SwitchButton> scriptButtons = new ArrayList<>();
    private final List<PopupControlSlot> popupControlSlots = new ArrayList<>();
    private final Set<String> selectedAutoScripts = new LinkedHashSet<>();
    private List<String> availableScripts = List.of();

    private TextField nameField;
    private TextField descriptionField;
    private TextField authorsField;
    private TextField versionField;
    private TextField colorField;
    private Label status;
    private Label scriptPageText;
    private Button previousScripts;
    private Button nextScripts;
    private AvatarConfigForm form;

    private JsonObject metadata = new JsonObject();
    private Path avatarFolder;
    private Path configPath;
    private String avatarName = "avatar";
    private int scriptPage;
    private int scriptRows;
    private boolean dirty;
    private boolean autoScriptsTouched;
    private boolean popupControlsTouched;

    public AvatarConfigEditorScreen(Screen parentScreen) {
        super(parentScreen, FiguraText.of("gui.panels.title.avatar_config"));
    }

    @Override
    public Class<? extends Screen> getSelectedPanel() {
        return WardrobeScreen.class;
    }

    @Override
    protected void init() {
        super.init();

        resolveConfigPath();
        metadata = readMetadata();
        selectedAutoScripts.clear();
        selectedAutoScripts.addAll(readStringArray(metadata.get("autoScripts")));
        availableScripts = scanScripts();
        for (String script : selectedAutoScripts) {
            if (!availableScripts.contains(script))
                availableScripts.add(script);
        }
        scriptButtons.clear();

        int panelX = panelX();
        int panelY = panelY();
        int panelWidth = panelWidth();
        int buttonY = panelY + panelHeight() - 26;
        int formX = panelX + 10;
        int formY = panelY + 10;
        int formWidth = panelWidth - 20;
        int formHeight = Math.max(48, buttonY - formY - 22);
        int x = 0;
        int y = 0;
        int labelWidth = Math.min(82, Math.max(58, formWidth / 4));
        int fieldWidth = Math.max(96, formWidth - labelWidth - 18);

        form = addRenderableWidget(new AvatarConfigForm(formX, formY, formWidth, formHeight));
        form.addContent(new Label(FiguraText.of("gui.avatar_config.file", avatarName), x, y, formWidth - 16, false, TextUtils.Alignment.LEFT));
        y += 18;

        nameField = addField(FiguraText.of("gui.avatar_config.name"), x, y, labelWidth, fieldWidth, TextField.HintType.NAME, getString(metadata, "name"));
        y += 24;
        descriptionField = addField(FiguraText.of("gui.avatar_config.description"), x, y, labelWidth, fieldWidth, TextField.HintType.ANY, getString(metadata, "description"));
        y += 24;
        authorsField = addField(FiguraText.of("gui.avatar_config.authors"), x, y, labelWidth, fieldWidth, TextField.HintType.ANY, readAuthors());
        y += 24;
        versionField = addField(FiguraText.of("gui.avatar_config.version"), x, y, labelWidth, fieldWidth, TextField.HintType.ANY, getString(metadata, "version"));
        y += 24;
        colorField = addField(FiguraText.of("gui.avatar_config.color"), x, y, labelWidth, fieldWidth, TextField.HintType.HEX_COLOR, getString(metadata, "color"));
        y += 28;

        form.addContent(new Label(FiguraText.of("gui.avatar_config.auto_scripts"), x, y + 5, 118, false, TextUtils.Alignment.LEFT));
        form.addContent(previousScripts = new Button(formWidth - 84, y, 20, 20, Component.literal("<"), null, button -> changeScriptPage(-1)));
        form.addContent(scriptPageText = new Label(Component.empty(), formWidth - 48, y + 6, TextUtils.Alignment.CENTER));
        form.addContent(nextScripts = new Button(formWidth - 34, y, 20, 20, Component.literal(">"), null, button -> changeScriptPage(1)));
        y += 24;

        scriptRows = Math.max(1, Math.min(5, availableScripts.isEmpty() ? 1 : availableScripts.size()));
        for (int i = 0; i < scriptRows; i++) {
            int slot = i;
            SwitchButton button = new SwitchButton(x, y + i * SCRIPT_ROW_HEIGHT, formWidth - 16, 20, Component.empty(), false) {
                @Override
                public void onPress(InputWithModifiers inputWithModifiers) {
                    super.onPress(inputWithModifiers);
                    toggleScript(slot);
                }
            };
            button.setUnderline(false);
            scriptButtons.add(button);
            form.addContent(button);
        }
        form.setContentHeight(y + scriptRows * SCRIPT_ROW_HEIGHT);
        y += scriptRows * SCRIPT_ROW_HEIGHT + 12;

        popupControlSlots.clear();
        form.addContent(new Label(FiguraText.of("gui.avatar_config.popup_controls"), x, y + 5, 118, false, TextUtils.Alignment.LEFT));
        y += 24;

        List<PopupControlConfig> popupControls = readPopupControlConfigs();
        for (int i = 0; i < PopupAPI.MAX_CONTROLS; i++) {
            PopupControlConfig config = i < popupControls.size() ? popupControls.get(i) : new PopupControlConfig();
            PopupControlSlot slot = new PopupControlSlot(i + 1, x, y + i * POPUP_ROW_HEIGHT, formWidth - 16, config);
            popupControlSlots.add(slot);
        }
        form.setContentHeight(y + PopupAPI.MAX_CONTROLS * POPUP_ROW_HEIGHT + 4);

        addRenderableWidget(status = new Label(Component.empty(), formX, buttonY - 16, panelWidth - 20, false, TextUtils.Alignment.LEFT));
        addRenderableWidget(new Button(this.width / 2 - 124, buttonY, 120, 20, FiguraText.of("gui.avatar_config.save"), null, button -> saveConfig()));
        addRenderableWidget(new Button(this.width / 2 + 4, buttonY, 120, 20, FiguraText.of("gui.done"), null, button -> onClose()));

        setStatus(FiguraText.of(configPath == null ? "gui.avatar_config.invalid_path" : "gui.avatar_config.loaded", avatarName)
                .withStyle(configPath == null ? ChatFormatting.RED : ChatFormatting.GRAY));
        dirty = false;
        autoScriptsTouched = false;
        popupControlsTouched = false;
        refreshScriptButtons();
    }

    @Override
    public void renderBackground(GuiGraphics gui, float delta) {
        super.renderBackground(gui, delta);
        UIHelper.blitSliced(gui, panelX(), panelY(), panelWidth(), panelHeight(), UIHelper.OUTLINE_FILL);
    }

    private TextField addField(Component label, int x, int y, int labelWidth, int fieldWidth, TextField.HintType hint, String value) {
        form.addContent(new Label(label, x, y + 6, labelWidth, false, TextUtils.Alignment.LEFT));
        TextField field = new TextField(x + labelWidth, y, fieldWidth, FIELD_HEIGHT, hint, ignored -> markDirty());
        field.getField().setValue(value == null ? "" : value);
        form.addContent(field);
        return field;
    }

    private void resolveConfigPath() {
        avatarFolder = null;
        configPath = null;

        try {
            Path path = LocalAvatarLoader.getLastLoadedPath();
            if (path == null)
                return;

            avatarFolder = path.toAbsolutePath().normalize();
            avatarName = IOUtils.getFileNameOrEmpty(avatarFolder);
            Path resolvedConfig = avatarFolder.resolve("avatar.json").toAbsolutePath().normalize();
            if (!resolvedConfig.startsWith(avatarFolder))
                return;
            configPath = resolvedConfig;
        } catch (Exception e) {
            FiguraMod.LOGGER.error("Failed to resolve avatar metadata path", e);
            avatarFolder = null;
            configPath = null;
        }
    }

    private JsonObject readMetadata() {
        if (configPath == null || !Files.exists(configPath))
            return new JsonObject();

        try {
            JsonElement element = JsonParser.parseString(Files.readString(configPath, StandardCharsets.UTF_8));
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            FiguraMod.LOGGER.error("Failed to read avatar metadata", e);
            setStatus(FiguraText.of("gui.avatar_config.invalid_json").withStyle(ChatFormatting.RED));
            return new JsonObject();
        }
    }

    private List<String> scanScripts() {
        List<String> scripts = new ArrayList<>();
        if (avatarFolder == null || !Files.isDirectory(avatarFolder))
            return scripts;

        for (Path script : IOUtils.getFilesByExtension(avatarFolder, ".lua")) {
            Path relative = avatarFolder.relativize(script.toAbsolutePath().normalize());
            String name = relative.toString().replace('\\', '.').replace('/', '.');
            name = name.substring(0, name.length() - ".lua".length());
            if (!name.isBlank())
                scripts.add(name);
        }
        scripts.sort(String.CASE_INSENSITIVE_ORDER);
        return scripts;
    }

    private void saveConfig() {
        if (configPath == null) {
            setStatus(FiguraText.of("gui.avatar_config.invalid_path").withStyle(ChatFormatting.RED));
            return;
        }

        try {
            JsonObject object = metadata.deepCopy();
            putString(object, "name", nameField.getField().getValue());
            putString(object, "description", descriptionField.getField().getValue());
            putString(object, "version", versionField.getField().getValue());
            putString(object, "color", colorField.getField().getValue());
            putArray(object, "authors", splitList(authorsField.getField().getValue()));
            object.remove("author");

            if (autoScriptsTouched || metadata.has("autoScripts") || !selectedAutoScripts.isEmpty())
                putArray(object, "autoScripts", orderedSelectedScripts(), true);

            JsonArray popupControls = collectPopupControls();
            if (popupControlsTouched || metadata.has("popupControls") || metadata.has("popup_controls") || !popupControls.isEmpty()) {
                object.remove("popup_controls");
                if (popupControls.isEmpty())
                    object.remove("popupControls");
                else
                    object.add("popupControls", popupControls);
            }

            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(object) + "\n", StandardCharsets.UTF_8);

            metadata = object;
            dirty = false;
            autoScriptsTouched = false;
            popupControlsTouched = false;
            setStatus(FiguraText.of("gui.avatar_config.saved").withStyle(ChatFormatting.GREEN));
            FiguraToast.sendToast(FiguraText.of("toast.avatar_config.saved"));
            if (avatarFolder != null)
                AvatarManager.loadLocalAvatar(avatarFolder);
        } catch (Exception e) {
            FiguraMod.LOGGER.error("Failed to save avatar metadata", e);
            setStatus(FiguraText.of("gui.avatar_config.invalid_json").withStyle(ChatFormatting.RED));
        }
    }

    private void toggleScript(int slot) {
        int index = scriptPage * scriptRows + slot;
        if (index < 0 || index >= availableScripts.size())
            return;

        String script = availableScripts.get(index);
        if (!selectedAutoScripts.remove(script))
            selectedAutoScripts.add(script);
        autoScriptsTouched = true;
        markDirty();
        refreshScriptButtons();
    }

    private void changeScriptPage(int direction) {
        int maxPage = maxScriptPage();
        scriptPage = Math.max(0, Math.min(maxPage, scriptPage + direction));
        refreshScriptButtons();
    }

    private void refreshScriptButtons() {
        int maxPage = maxScriptPage();
        scriptPage = Math.max(0, Math.min(maxPage, scriptPage));

        for (int i = 0; i < scriptButtons.size(); i++) {
            int index = scriptPage * scriptRows + i;
            SwitchButton button = scriptButtons.get(i);
            if (index >= availableScripts.size()) {
                button.setMessage(FiguraText.of("gui.avatar_config.no_script"));
                button.setToggled(false);
                button.setActive(false);
                button.setVisible(availableScripts.isEmpty() && i == 0);
                continue;
            }

            String script = availableScripts.get(index);
            button.setMessage(Component.literal(script));
            button.setToggled(selectedAutoScripts.contains(script));
            button.setActive(true);
            button.setVisible(true);
        }

        if (scriptPageText != null)
            scriptPageText.setText(Component.literal((scriptPage + 1) + "/" + (maxPage + 1)));
        if (previousScripts != null)
            previousScripts.setActive(scriptPage > 0);
        if (nextScripts != null)
            nextScripts.setActive(scriptPage < maxPage);
    }

    private int maxScriptPage() {
        if (scriptRows <= 0 || availableScripts.isEmpty())
            return 0;
        return Math.max(0, (availableScripts.size() - 1) / scriptRows);
    }

    private String readAuthors() {
        List<String> authors = readStringArray(metadata.get("authors"));
        if (!authors.isEmpty())
            return String.join(", ", authors);
        return getString(metadata, "author");
    }

    private List<String> orderedSelectedScripts() {
        List<String> ordered = new ArrayList<>();
        for (String script : availableScripts) {
            if (selectedAutoScripts.contains(script))
                ordered.add(script);
        }
        for (String script : selectedAutoScripts) {
            if (!ordered.contains(script))
                ordered.add(script);
        }
        return ordered;
    }

    private List<PopupControlConfig> readPopupControlConfigs() {
        List<PopupControlConfig> controls = new ArrayList<>();
        JsonElement element = metadata.has("popupControls") ? metadata.get("popupControls") : metadata.get("popup_controls");
        if (element == null || !element.isJsonArray())
            return controls;

        for (JsonElement child : element.getAsJsonArray()) {
            if (child == null || !child.isJsonObject())
                continue;

            JsonObject object = child.getAsJsonObject();
            String id = getString(object, "id").trim();
            if (id.isEmpty())
                continue;

            PopupControlConfig config = new PopupControlConfig();
            config.enabled = true;
            config.type = switch (getString(object, "type").trim().toLowerCase(Locale.US)) {
                case "slider" -> PopupInput.Type.SLIDER;
                case "button" -> PopupInput.Type.BUTTON;
                default -> PopupInput.Type.TOGGLE;
            };
            config.id = id;
            config.title = getString(object, "title");
            config.target = PopupInput.Target.byName(getString(object, "target"));
            config.defaultValue = getPrimitiveString(object, "default");
            config.min = getPrimitiveString(object, "min");
            config.max = getPrimitiveString(object, "max");
            config.step = getPrimitiveString(object, "step");
            config.headName = getPrimitiveString(object, "headName");
            if (config.headName.isBlank())
                config.headName = getPrimitiveString(object, "head_name");
            config.synced = parseBoolean(getPrimitiveString(object, "synced"));
            controls.add(config);
            if (controls.size() >= PopupAPI.MAX_CONTROLS)
                break;
        }
        return controls;
    }

    private JsonArray collectPopupControls() {
        JsonArray array = new JsonArray();
        for (PopupControlSlot slot : popupControlSlots) {
            JsonObject object = slot.toJson();
            if (object != null)
                array.add(object);
        }
        return array;
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static String getPrimitiveString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static List<String> readStringArray(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element == null || element.isJsonNull())
            return values;

        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (child != null && child.isJsonPrimitive())
                    addClean(values, child.getAsString());
            }
        } else if (element.isJsonPrimitive()) {
            addClean(values, element.getAsString());
        }
        return values;
    }

    private static List<String> splitList(String value) {
        List<String> values = new ArrayList<>();
        if (value == null)
            return values;

        for (String entry : value.split(",")) {
            addClean(values, entry);
        }
        return values;
    }

    private static void putString(JsonObject object, String key, String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty())
            object.remove(key);
        else
            object.addProperty(key, clean);
    }

    private static void putArray(JsonObject object, String key, List<String> values) {
        putArray(object, key, values, false);
    }

    private static void putArray(JsonObject object, String key, List<String> values, boolean keepEmpty) {
        if (values.isEmpty() && !keepEmpty) {
            object.remove(key);
            return;
        }

        JsonArray array = new JsonArray();
        for (String value : values)
            array.add(value);
        object.add(key, array);
    }

    private static void addClean(List<String> values, String value) {
        String clean = value == null ? "" : value.trim();
        if (!clean.isEmpty() && !values.contains(clean))
            values.add(clean);
    }

    private static void putOptionalString(JsonObject object, String key, String value) {
        String clean = value == null ? "" : value.trim();
        if (!clean.isEmpty())
            object.addProperty(key, clean);
    }

    private static void putOptionalNumber(JsonObject object, String key, String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty())
            return;

        try {
            double parsed = Double.parseDouble(clean);
            if (Double.isFinite(parsed))
                object.addProperty(key, parsed);
        } catch (NumberFormatException ignored) {
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

    private void markDirty() {
        dirty = true;
        setStatus(FiguraText.of("gui.avatar_config.unsaved").withStyle(ChatFormatting.YELLOW));
    }

    private void setStatus(Component message) {
        if (status != null)
            status.setText(message);
    }

    private int panelWidth() {
        return Math.max(260, Math.min(this.width - 24, 500));
    }

    private int panelHeight() {
        return Math.max(150, Math.min(this.height - panelY() - 8, 334));
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelY() {
        return 38;
    }

    @Override
    public void onClose() {
        if (dirty)
            setStatus(FiguraText.of("gui.avatar_config.unsaved").withStyle(ChatFormatting.YELLOW));
        super.onClose();
    }

    private static class AvatarConfigForm extends AbstractContainerElement {

        private static final int SCROLLBAR_GAP = 14;

        private final List<FormEntry> content = new ArrayList<>();
        private final ScrollBarWidget scrollBar;
        private int contentHeight;

        public AvatarConfigForm(int x, int y, int width, int height) {
            super(x, y, width, height);
            children.add(scrollBar = new ScrollBarWidget(x + width - SCROLLBAR_GAP, y + 4, 10, height - 8, 0d));
            scrollBar.setVisible(false);
        }

        public <T extends GuiEventListener & FiguraWidget> T addContent(T widget) {
            content.add(new FormEntry(widget, widget.getX(), widget.getY()));
            children.add(widget);
            return widget;
        }

        public void setContentHeight(int contentHeight) {
            this.contentHeight = contentHeight;
        }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
            boolean needsScroll = contentHeight > getHeight();
            int heightDiff = Math.max(0, contentHeight - getHeight());
            int scroll = needsScroll ? (int) Math.round(Mth.lerp(scrollBar.getScrollProgress(), 0, heightDiff)) : 0;

            scrollBar.setX(getX() + getWidth() - SCROLLBAR_GAP);
            scrollBar.setY(getY() + 4);
            scrollBar.setHeight(getHeight() - 8);
            scrollBar.setVisible(needsScroll);
            if (needsScroll)
                scrollBar.setScrollRatio(20, heightDiff);
            else
                scrollBar.setScrollProgressNoAnim(0d);

            int clipX = getX();
            int clipY = getY();
            int clipRight = getX() + getWidth() - (needsScroll ? SCROLLBAR_GAP : 0);
            int clipBottom = getY() + getHeight();

            gui.enableScissor(clipX, clipY, clipRight, clipBottom);
            for (FormEntry entry : content) {
                FiguraWidget widget = entry.widget;
                int y = getY() + entry.y - scroll;
                widget.setX(getX() + entry.x);
                widget.setY(y);
                widget.setVisible(y + widget.getHeight() > clipY && y < clipBottom);
                widget.render(gui, mouseX, mouseY, delta);
            }
            gui.disableScissor();

            if (scrollBar.isVisible())
                scrollBar.render(gui, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, boolean bl) {
            if (scrollBar.isVisible() && scrollBar.mouseClicked(mouseButtonEvent, bl)) {
                setFocused(scrollBar);
                if (mouseButtonEvent.button() == 0)
                    setDragging(true);
                return true;
            }

            return isMouseOver(mouseButtonEvent.x(), mouseButtonEvent.y()) && super.mouseClicked(mouseButtonEvent, bl);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount, double d) {
            if (scrollBar.isVisible() && isMouseOver(mouseX, mouseY))
                return scrollBar.mouseScrolled(mouseX, mouseY, amount, d);
            return super.mouseScrolled(mouseX, mouseY, amount, d);
        }

        private static class FormEntry {
            private final FiguraWidget widget;
            private final int x;
            private final int y;

            private FormEntry(FiguraWidget widget, int x, int y) {
                this.widget = widget;
                this.x = x;
                this.y = y;
            }
        }
    }

    private static class PopupControlConfig {
        private boolean enabled;
        private PopupInput.Type type = PopupInput.Type.TOGGLE;
        private PopupInput.Target target = PopupInput.Target.PLAYER;
        private String id = "";
        private String title = "";
        private String defaultValue = "";
        private String min = "";
        private String max = "";
        private String step = "";
        private String headName = "";
        private boolean synced;
    }

    private class PopupControlSlot {
        private final SwitchButton enabledButton;
        private final Button typeButton;
        private final Button targetButton;
        private final SwitchButton syncedButton;
        private final TextField idField;
        private final TextField titleField;
        private final TextField defaultField;
        private final TextField minField;
        private final TextField maxField;
        private final TextField stepField;
        private final TextField headNameField;
        private PopupInput.Type type;
        private PopupInput.Target target;

        private PopupControlSlot(int slot, int x, int y, int width, PopupControlConfig config) {
            int clampedWidth = Math.max(220, width);
            this.type = config.type == null ? PopupInput.Type.TOGGLE : config.type;
            this.target = config.target == null ? PopupInput.Target.PLAYER : config.target;

            enabledButton = new SwitchButton(x, y, Math.min(96, clampedWidth / 4), 20, FiguraText.of("gui.avatar_config.popup_control", slot), config.enabled) {
                @Override
                public void onPress(InputWithModifiers inputWithModifiers) {
                    super.onPress(inputWithModifiers);
                    refreshEnabled();
                    touch();
                }
            };
            enabledButton.setUnderline(false);
            form.addContent(enabledButton);

            int typeX = x + enabledButton.getWidth() + 4;
            typeButton = form.addContent(new Button(typeX, y, 72, 20, Component.empty(), null, button -> {
                cycleType();
                refreshType();
                touch();
            }));

            int targetX = typeX + typeButton.getWidth() + 4;
            int syncWidth = 56;
            int targetWidth = Math.min(86, Math.max(54, clampedWidth - targetX + x - syncWidth - 4));
            targetButton = form.addContent(new Button(targetX, y, targetWidth, 20, Component.empty(), null, button -> {
                cycleTarget();
                refreshTarget();
                touch();
            }));

            int syncX = targetX + targetButton.getWidth() + 4;
            syncedButton = new SwitchButton(syncX, y, Math.max(44, clampedWidth - syncX + x), 20, FiguraText.of("gui.avatar_config.popup_synced"), config.synced) {
                @Override
                public void onPress(InputWithModifiers inputWithModifiers) {
                    super.onPress(inputWithModifiers);
                    touch();
                }
            };
            syncedButton.setUnderline(false);
            form.addContent(syncedButton);

            form.addContent(new Label(FiguraText.of("gui.avatar_config.popup_id"), x, y + 24, 36, false, TextUtils.Alignment.LEFT));
            idField = form.addContent(new TextField(x + 38, y + 20, Math.max(48, clampedWidth - 38), FIELD_HEIGHT, TextField.HintType.NAME, ignored -> touch()));
            idField.getField().setMaxLength(PopupAPI.MAX_ID_LENGTH);
            idField.getField().setValue(config.id);

            form.addContent(new Label(FiguraText.of("gui.avatar_config.popup_title"), x, y + 48, 40, false, TextUtils.Alignment.LEFT));
            titleField = form.addContent(new TextField(x + 42, y + 44, Math.max(64, clampedWidth - 42), FIELD_HEIGHT, TextField.HintType.ANY, ignored -> touch()));
            titleField.getField().setMaxLength(PopupAPI.MAX_TITLE_LENGTH);
            titleField.getField().setValue(config.title);

            form.addContent(new Label(FiguraText.of("gui.avatar_config.popup_head_name"), x, y + 72, 40, false, TextUtils.Alignment.LEFT));
            headNameField = form.addContent(new TextField(x + 42, y + 68, Math.max(64, clampedWidth - 42), FIELD_HEIGHT, TextField.HintType.ANY, ignored -> touch()));
            headNameField.getField().setMaxLength(PopupAPI.MAX_TITLE_LENGTH);
            headNameField.getField().setValue(config.headName);

            int numericY = y + 92;
            int columnWidth = Math.max(50, (clampedWidth - 12) / 4);
            defaultField = addPopupValueField(x, numericY, columnWidth, FiguraText.of("gui.avatar_config.popup_default"), config.defaultValue);
            minField = addPopupValueField(x + columnWidth + 4, numericY, columnWidth, FiguraText.of("gui.avatar_config.popup_min"), config.min);
            maxField = addPopupValueField(x + (columnWidth + 4) * 2, numericY, columnWidth, FiguraText.of("gui.avatar_config.popup_max"), config.max);
            stepField = addPopupValueField(x + (columnWidth + 4) * 3, numericY, Math.max(50, clampedWidth - (columnWidth + 4) * 3), FiguraText.of("gui.avatar_config.popup_step"), config.step);

            refreshType();
            refreshTarget();
            refreshEnabled();
        }

        private void cycleType() {
            type = switch (type) {
                case TOGGLE -> PopupInput.Type.SLIDER;
                case SLIDER -> PopupInput.Type.BUTTON;
                case BUTTON -> PopupInput.Type.TOGGLE;
            };
        }

        private void cycleTarget() {
            PopupInput.Target[] targets = PopupInput.Target.values();
            target = targets[(target.ordinal() + 1) % targets.length];
        }

        private TextField addPopupValueField(int x, int y, int width, Component label, String value) {
            int labelWidth = Math.min(42, Math.max(24, width / 2));
            form.addContent(new Label(label, x, y + 4, labelWidth, false, TextUtils.Alignment.LEFT));
            TextField field = form.addContent(new TextField(x + labelWidth, y, Math.max(24, width - labelWidth), FIELD_HEIGHT, TextField.HintType.ANY, ignored -> touch()));
            field.getField().setValue(value);
            return field;
        }

        private void refreshType() {
            typeButton.setMessage(FiguraText.of("gui.avatar_config.popup_type_" + type.name().toLowerCase(Locale.US)));
            boolean slider = type == PopupInput.Type.SLIDER;
            boolean button = type == PopupInput.Type.BUTTON;
            defaultField.setEnabled(!button);
            minField.setEnabled(slider);
            maxField.setEnabled(slider);
            stepField.setEnabled(slider);
        }

        private void refreshTarget() {
            targetButton.setMessage(FiguraText.of("gui.avatar_config.popup_target_" + target.name().toLowerCase(Locale.US)));
            headNameField.setEnabled(enabledButton.isToggled() && target == PopupInput.Target.HEAD);
        }

        private void refreshEnabled() {
            boolean enabled = enabledButton.isToggled();
            boolean slider = type == PopupInput.Type.SLIDER;
            boolean button = type == PopupInput.Type.BUTTON;
            typeButton.setActive(enabled);
            targetButton.setActive(enabled);
            syncedButton.setActive(enabled);
            idField.setEnabled(enabled);
            titleField.setEnabled(enabled);
            headNameField.setEnabled(enabled && target == PopupInput.Target.HEAD);
            defaultField.setEnabled(enabled && !button);
            minField.setEnabled(enabled && slider);
            maxField.setEnabled(enabled && slider);
            stepField.setEnabled(enabled && slider);
        }

        private JsonObject toJson() {
            if (!enabledButton.isToggled())
                return null;

            String id = idField.getField().getValue().trim();
            if (id.isEmpty())
                return null;

            JsonObject object = new JsonObject();
            object.addProperty("id", id);
            object.addProperty("type", type.name().toLowerCase(Locale.US));
            object.addProperty("target", target.name().toLowerCase(Locale.US));
            if (syncedButton.isToggled())
                object.addProperty("synced", true);
            putOptionalString(object, "title", titleField.getField().getValue());
            putOptionalString(object, "headName", headNameField.getField().getValue());

            String defaultValue = defaultField.getField().getValue();
            if (type == PopupInput.Type.SLIDER) {
                putOptionalNumber(object, "default", defaultValue);
                putOptionalNumber(object, "min", minField.getField().getValue());
                putOptionalNumber(object, "max", maxField.getField().getValue());
                putOptionalNumber(object, "step", stepField.getField().getValue());
            } else if (type == PopupInput.Type.TOGGLE && defaultValue != null && !defaultValue.isBlank()) {
                object.addProperty("default", parseBoolean(defaultValue));
            }

            return object;
        }

        private void touch() {
            popupControlsTouched = true;
            markDirty();
        }
    }
}
