package org.figuramc.figura.gui.screens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.gui.FiguraToast;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.Label;
import org.figuramc.figura.gui.widgets.SliderWidget;
import org.figuramc.figura.gui.widgets.SwitchButton;
import org.figuramc.figura.gui.widgets.TextField;
import org.figuramc.figura.lua.api.popup.PopupAPI;
import org.figuramc.figura.lua.api.popup.PopupInput;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.IOUtils;
import org.figuramc.figura.utils.TextUtils;
import org.figuramc.figura.utils.ui.UIHelper;
import org.luaj.vm2.LuaError;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class LuaTypesScreen extends AbstractPanelScreen {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final FiguraIdentifier BUTTON_TEXTURE = new FiguraIdentifier("textures/gui/button.png");

    private TextField idField;
    private TextField titleField;
    private TextField defaultField;
    private TextField minField;
    private TextField maxField;
    private TextField stepField;
    private Button kindButton;
    private Button targetButton;
    private Label status;
    private ControlKind kind = ControlKind.TOGGLE;
    private TargetKind target = TargetKind.PLAYER;

    public LuaTypesScreen(Screen parentScreen) {
        super(parentScreen, FiguraText.of("gui.panels.title.lua_types"));
    }

    @Override
    protected void init() {
        super.init();
        loadPreset();

        int panelX = panelX();
        int panelY = panelY();
        int panelWidth = panelWidth();
        int controlsWidth = Math.min(320, Math.max(236, panelWidth / 2));
        int x = panelX + 12;
        int y = panelY + 14;
        int labelWidth = 72;
        int fieldWidth = controlsWidth - labelWidth;

        addRenderableWidget(new Label(FiguraText.of("gui.ui_builder.id"), x, y + 6, labelWidth, false, TextUtils.Alignment.LEFT));
        idField = addRenderableWidget(new TextField(x + labelWidth, y, fieldWidth, 20, TextField.HintType.NAME, value -> updateLabels()));
        idField.getField().setMaxLength(PopupAPI.MAX_ID_LENGTH);
        idField.getField().setValue(readPresetString("id", "visibility_toggle"));
        y += 26;

        addRenderableWidget(new Label(FiguraText.of("gui.ui_builder.title"), x, y + 6, labelWidth, false, TextUtils.Alignment.LEFT));
        titleField = addRenderableWidget(new TextField(x + labelWidth, y, fieldWidth, 20, TextField.HintType.ANY, value -> updateLabels()));
        titleField.getField().setMaxLength(PopupAPI.MAX_TITLE_LENGTH);
        titleField.getField().setValue(readPresetString("title", "Visible"));
        y += 28;

        addRenderableWidget(new Label(FiguraText.of("gui.ui_builder.type"), x, y + 6, labelWidth, false, TextUtils.Alignment.LEFT));
        kindButton = addRenderableWidget(new Button(x + labelWidth, y, fieldWidth, 20, Component.empty(), null, button -> {
            kind = kind.next();
            updateLabels();
        }));
        y += 26;

        addRenderableWidget(new Label(FiguraText.of("gui.ui_builder.target"), x, y + 6, labelWidth, false, TextUtils.Alignment.LEFT));
        targetButton = addRenderableWidget(new Button(x + labelWidth, y, fieldWidth, 20, Component.empty(), null, button -> {
            target = target.next();
            updateLabels();
        }));
        y += 30;

        defaultField = addValueField(x, y, labelWidth, fieldWidth, FiguraText.of("gui.ui_builder.default"), "default", "false");
        y += 24;
        minField = addValueField(x, y, labelWidth, fieldWidth, FiguraText.of("gui.ui_builder.min"), "min", "0");
        y += 24;
        maxField = addValueField(x, y, labelWidth, fieldWidth, FiguraText.of("gui.ui_builder.max"), "max", "1");
        y += 24;
        stepField = addValueField(x, y, labelWidth, fieldWidth, FiguraText.of("gui.ui_builder.step"), "step", "0.05");
        y += 32;

        int buttonWidth = Math.max(74, (controlsWidth - 8) / 3);
        addRenderableWidget(new Button(x, y, buttonWidth, 20, FiguraText.of("gui.ui_builder.copy_lua"), null, button -> copyLua()));
        addRenderableWidget(new Button(x + buttonWidth + 4, y, buttonWidth, 20, FiguraText.of("gui.ui_builder.copy_json"), null, button -> copyJson()));
        addRenderableWidget(new Button(x + (buttonWidth + 4) * 2, y, buttonWidth, 20, FiguraText.of("gui.ui_builder.save_preset"), null, button -> savePreset()));
        y += 26;

        addRenderableWidget(new Button(x, y, buttonWidth, 20, FiguraText.of("gui.ui_builder.preview_apply"), null, button -> applyPreview()));
        addRenderableWidget(new Button(x + buttonWidth + 4, y, buttonWidth, 20, FiguraText.of("gui.done"), null, button -> onClose()));
        y += 28;

        status = addRenderableWidget(new Label(FiguraText.of("gui.ui_builder.ready").withStyle(ChatFormatting.GRAY), x, y, controlsWidth, true, TextUtils.Alignment.LEFT));
        updateLabels();
    }

    private TextField addValueField(int x, int y, int labelWidth, int fieldWidth, Component label, String presetKey, String fallback) {
        addRenderableWidget(new Label(label, x, y + 6, labelWidth, false, TextUtils.Alignment.LEFT));
        TextField field = addRenderableWidget(new TextField(x + labelWidth, y, fieldWidth, 20, TextField.HintType.ANY, value -> updateLabels()));
        field.getField().setValue(readPresetString(presetKey, fallback));
        return field;
    }

    @Override
    public void renderBackground(GuiGraphics gui, float delta) {
        super.renderBackground(gui, delta);
        UIHelper.blitSliced(gui, panelX(), panelY(), panelWidth(), panelHeight(), UIHelper.OUTLINE_FILL);
        renderPreview(gui);
    }

    @Override
    public void renderOverlays(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        renderSnippet(gui);
        super.renderOverlays(gui, mouseX, mouseY, delta);
    }

    private void updateLabels() {
        if (kindButton != null)
            kindButton.setMessage(kind.title());
        if (targetButton != null)
            targetButton.setMessage(target.title());

        boolean slider = kind == ControlKind.SLIDER;
        boolean button = kind == ControlKind.BUTTON;
        if (defaultField != null)
            defaultField.setEnabled(!button);
        if (minField != null)
            minField.setEnabled(slider);
        if (maxField != null)
            maxField.setEnabled(slider);
        if (stepField != null)
            stepField.setEnabled(slider);
    }

    private void copyLua() {
        Minecraft.getInstance().keyboardHandler.setClipboard(luaSnippet());
        FiguraToast.sendToast(FiguraText.of("toast.clipboard"));
        setStatus(FiguraText.of("gui.ui_builder.copied_lua").withStyle(ChatFormatting.GREEN));
    }

    private void copyJson() {
        Minecraft.getInstance().keyboardHandler.setClipboard(jsonSnippet());
        FiguraToast.sendToast(FiguraText.of("toast.clipboard"));
        setStatus(FiguraText.of("gui.ui_builder.copied_json").withStyle(ChatFormatting.GREEN));
    }

    private void applyPreview() {
        Avatar avatar = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
        if (avatar == null || avatar.luaRuntime == null || avatar.luaRuntime.popup == null) {
            setStatus(FiguraText.of("gui.ui_builder.no_avatar").withStyle(ChatFormatting.RED));
            return;
        }

        try {
            PopupInput input = switch (kind) {
                case TOGGLE -> avatar.luaRuntime.popup.addToggle(id(), title(), defaultBoolean(), null);
                case SLIDER -> avatar.luaRuntime.popup.addSlider(id(), title(), defaultNumber(0d), minNumber(), maxNumber(), stepNumber(), null);
                case BUTTON -> avatar.luaRuntime.popup.addButton(id(), title(), null);
            };
            input.setTarget(targetName());
            setStatus(FiguraText.of("gui.ui_builder.preview_applied", id()).withStyle(ChatFormatting.GREEN));
        } catch (LuaError error) {
            setStatus(Component.literal(error.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    private void savePreset() {
        JsonObject object = new JsonObject();
        object.addProperty("id", id());
        object.addProperty("title", title());
        object.addProperty("type", kind.name().toLowerCase(Locale.US));
        object.addProperty("target", target.name().toLowerCase(Locale.US));
        object.addProperty("default", value(defaultField));
        object.addProperty("min", value(minField));
        object.addProperty("max", value(maxField));
        object.addProperty("step", value(stepField));

        try {
            Files.writeString(presetPath(), GSON.toJson(object), StandardCharsets.UTF_8);
            setStatus(FiguraText.of("gui.ui_builder.preset_saved").withStyle(ChatFormatting.GREEN));
        } catch (Exception e) {
            FiguraMod.LOGGER.warn("Failed to save popup UI builder preset", e);
            setStatus(FiguraText.of("gui.ui_builder.preset_error").withStyle(ChatFormatting.RED));
        }
    }

    private JsonObject preset;

    private void loadPreset() {
        preset = new JsonObject();
        Path path = presetPath();
        if (!Files.exists(path))
            return;

        try {
            JsonObject object = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            preset = object;
            kind = ControlKind.byName(readPresetString("type", kind.name()));
            target = TargetKind.byName(readPresetString("target", target.name()));
        } catch (Exception e) {
            FiguraMod.LOGGER.warn("Failed to load popup UI builder preset", e);
            preset = new JsonObject();
        }
    }

    private Path presetPath() {
        return IOUtils.getOrCreateDir(FiguraMod.getFiguraDirectory(), "config").resolve("ui_builder_preset.json");
    }

    private String readPresetString(String key, String fallback) {
        if (preset == null)
            return fallback;
        return preset.has(key) && preset.get(key).isJsonPrimitive() ? preset.get(key).getAsString() : fallback;
    }

    private void setStatus(Component component) {
        if (status != null)
            status.setText(component);
    }

    private void renderPreview(GuiGraphics gui) {
        int previewWidth = Math.min(280, Math.max(170, panelWidth() / 2 - 28));
        int previewHeight = panelHeight() - 52;
        int x = panelX() + panelWidth() - previewWidth - 12;
        int y = panelY() + 14;
        Font font = Minecraft.getInstance().font;

        UIHelper.fillOutline(gui, x, y, previewWidth, previewHeight, UIHelper.adjustColor(0xFFFFFFFF));
        gui.fill(x + 1, y + 1, x + previewWidth - 1, y + previewHeight - 1, UIHelper.adjustColor(0xA0000000));
        gui.drawString(font, FiguraText.of("gui.ui_builder.preview"), x + 8, y + 8, UIHelper.adjustColor(0xFFFFFF));
        gui.drawString(font, target.title(), x + 8, y + 20, UIHelper.adjustColor(0xAAAAAA));

        int controlX = x + 12;
        int controlY = y + 46;
        int controlWidth = previewWidth - 24;
        UIHelper.blitSliced(gui, controlX, controlY, controlWidth, 28, UIHelper.OUTLINE_FILL);
        UIHelper.renderScrollingText(gui, Component.literal(title()), controlX + 8, controlY + 10, controlWidth - 82, 0xFFFFFF);

        if (kind == ControlKind.SLIDER) {
            int sliderX = controlX + controlWidth - 72;
            UIHelper.enableBlend();
            gui.blit(RenderPipelines.GUI_TEXTURED, SliderWidget.SLIDER_TEXTURE, sliderX, controlY + 12, 0f, 0f, 60, 5, 5, 5, 33, 16);
            gui.blit(RenderPipelines.GUI_TEXTURED, SliderWidget.SLIDER_TEXTURE, sliderX + 24, controlY + 9, 11f, 5f, 11, 11, 33, 16);
        } else if (kind == ControlKind.BUTTON) {
            UIHelper.blitSliced(gui, controlX + controlWidth - 48, controlY + 7, 38, 14, 16f, 0f, 16, 16, 48, 32, BUTTON_TEXTURE);
            UIHelper.renderCenteredScrollingText(gui, FiguraText.of("popup_menu.run"), controlX + controlWidth - 47, controlY + 7, 36, 14, 0xFFFFFF);
        } else {
            UIHelper.blitSliced(gui, controlX + controlWidth - 44, controlY + 7, 34, 14, 16f, 0f, 16, 16, 48, 32, BUTTON_TEXTURE);
            UIHelper.renderCenteredScrollingText(gui, defaultBoolean() ? SwitchButton.ON : SwitchButton.OFF, controlX + controlWidth - 43, controlY + 7, 32, 14, defaultBoolean() ? 0x55FF55 : 0xFF7777);
        }

        Component note = FiguraText.of("gui.ui_builder.preview_note");
        UIHelper.renderCenteredScrollingText(gui, note, x + 8, y + previewHeight - 18, previewWidth - 16, 12, 0xA0A0A0);
    }

    private void renderSnippet(GuiGraphics gui) {
        int x = panelX() + 12;
        int y = panelY() + panelHeight() - 20;
        int width = panelWidth() - 24;
        UIHelper.renderScrollingText(gui, Component.literal(luaSnippet()).withStyle(ChatFormatting.GRAY), x, y, width, 0xFFFFFF);
    }

    private String luaSnippet() {
        String id = luaString(id());
        String title = luaString(title());
        String targetSetter = ":setTarget(" + luaString(targetName()) + ")";
        return switch (kind) {
            case TOGGLE -> "popup:add_toggle(" + id + ", " + title + ", " + defaultBoolean() + ", function(value, input)\n    -- viewer changed this control\nend)" + targetSetter;
            case SLIDER -> "popup:add_slider(" + id + ", " + title + ", " + defaultNumber(0d) + ", " + minNumber() + ", " + maxNumber() + ", " + stepNumber() + ", function(value, input)\n    -- viewer changed this control\nend)" + targetSetter;
            case BUTTON -> "popup:add_button(" + id + ", " + title + ", function(value, input)\n    -- viewer pressed this button\nend)" + targetSetter;
        };
    }

    private String jsonSnippet() {
        JsonObject wrapper = new JsonObject();
        JsonArray controls = new JsonArray();
        controls.add(jsonControl());
        wrapper.add("popupControls", controls);
        return GSON.toJson(wrapper);
    }

    private JsonObject jsonControl() {
        JsonObject object = new JsonObject();
        object.addProperty("id", id());
        object.addProperty("title", title());
        object.addProperty("type", kind.name().toLowerCase(Locale.US));
        object.addProperty("target", targetName());
        if (kind == ControlKind.TOGGLE) {
            object.addProperty("default", defaultBoolean());
        } else if (kind == ControlKind.SLIDER) {
            object.addProperty("default", defaultNumber(0d));
            object.addProperty("min", minNumber());
            object.addProperty("max", maxNumber());
            object.addProperty("step", stepNumber());
        }
        return object;
    }

    private String id() {
        String id = value(idField).trim();
        return id.isBlank() ? "popup_control" : id;
    }

    private String title() {
        String title = value(titleField).trim();
        return title.isBlank() ? id() : title;
    }

    private String targetName() {
        return target.name().toLowerCase(Locale.US);
    }

    private boolean defaultBoolean() {
        return switch (value(defaultField).trim().toLowerCase(Locale.US)) {
            case "true", "1", "yes", "on" -> true;
            default -> false;
        };
    }

    private double defaultNumber(double fallback) {
        return parseDouble(value(defaultField), fallback);
    }

    private double minNumber() {
        return parseDouble(value(minField), 0d);
    }

    private double maxNumber() {
        return parseDouble(value(maxField), 1d);
    }

    private double stepNumber() {
        return parseDouble(value(stepField), 0.05d);
    }

    private static double parseDouble(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String value(TextField field) {
        return field == null ? "" : field.getField().getValue();
    }

    private static String luaString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private int panelX() {
        return 8;
    }

    private int panelY() {
        return 36;
    }

    private int panelWidth() {
        return width - 16;
    }

    private int panelHeight() {
        return height - 44;
    }

    private enum ControlKind {
        TOGGLE,
        SLIDER,
        BUTTON;

        private ControlKind next() {
            ControlKind[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private Component title() {
            return FiguraText.of("gui.ui_builder.type_" + name().toLowerCase(Locale.US));
        }

        private static ControlKind byName(String name) {
            try {
                return valueOf(name.trim().toUpperCase(Locale.US));
            } catch (Exception ignored) {
                return TOGGLE;
            }
        }
    }

    private enum TargetKind {
        PLAYER,
        HEAD,
        ENTITY,
        ANY,
        HUD,
        WORLD;

        private TargetKind next() {
            TargetKind[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private Component title() {
            return FiguraText.of("gui.ui_builder.target_" + name().toLowerCase(Locale.US));
        }

        private static TargetKind byName(String name) {
            try {
                return valueOf(name.trim().toUpperCase(Locale.US));
            } catch (Exception ignored) {
                return PLAYER;
            }
        }
    }
}
