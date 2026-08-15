package org.figuramc.figura.gui;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.Badges;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.math.vector.FiguraVec4;
import org.figuramc.figura.permissions.PermissionManager;
import org.figuramc.figura.permissions.PermissionPack;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.MathUtils;
import org.figuramc.figura.utils.ui.UIHelper;
import org.figuramc.figura.gui.widgets.SliderWidget;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class PopupMenu {

    private static final FiguraIdentifier BACKGROUND = new FiguraIdentifier("textures/gui/popup.png");
    private static final FiguraIdentifier ICONS = new FiguraIdentifier("textures/gui/popup_icons.png");
    private static final FiguraIdentifier BUTTON = new FiguraIdentifier("textures/gui/button.png");

    private static final int ICON_SIZE = 18;
    private static final int VOLUME_INDEX = 4;
    private static final int VOLUME_PANEL_WIDTH = 132;
    private static final int VOLUME_PANEL_HEIGHT = 24;
    private static final int VOLUME_TOGGLE_WIDTH = 38;
    private static final int VOLUME_TOGGLE_HEIGHT = 14;
    private static final int VOLUME_SLIDER_WIDTH = 58;
    private static final int VOLUME_SLIDER_HEIGHT = 11;

    private static final MutableComponent VERSION_WARN = Component.empty()
            .append(Badges.System.WARNING.badge.copy().withStyle(Style.EMPTY.withFont(new FontDescription.Resource(Badges.FONT))))
            .append(" ")
            .append(Badges.System.WARNING.desc.copy().withStyle(ChatFormatting.YELLOW));
    private static final MutableComponent ERROR_WARN = Component.empty()
            .append(Badges.System.ERROR.badge.copy().withStyle(Style.EMPTY.withFont(new FontDescription.Resource(Badges.FONT))))
            .append(" ")
            .append(Badges.System.ERROR.desc.copy().withStyle(ChatFormatting.RED));
    private static final MutableComponent PERMISSION_WARN = Component.empty()
            .append(Badges.System.PERMISSIONS.badge.copy().withStyle(Style.EMPTY.withFont(new FontDescription.Resource(Badges.FONT))))
            .append(" ")
            .append(Badges.System.PERMISSIONS.desc.copy().withStyle(ChatFormatting.BLUE));

    private static final List<Pair<Component, Consumer<UUID>>> BUTTONS = List.of(
            Pair.of(FiguraText.of("popup_menu.cancel"), id -> {}),
            Pair.of(FiguraText.of("popup_menu.reload"), id -> {
                AvatarManager.reloadAvatar(id);
                FiguraToast.sendToast(FiguraText.of("toast.reload"));
            }),
            Pair.of(FiguraText.of("popup_menu.increase_permissions"), id -> {
                PermissionPack pack = PermissionManager.get(id);
                if (PermissionManager.increaseCategory(pack))
                    FiguraToast.sendToast(FiguraText.of("toast.permission_change"), pack.getCategoryName());
            }),
            Pair.of(FiguraText.of("popup_menu.decrease_permissions"), id -> {
                PermissionPack pack = PermissionManager.get(id);
                if (PermissionManager.decreaseCategory(pack))
                    FiguraToast.sendToast(FiguraText.of("toast.permission_change"), pack.getCategoryName());
            }),
            Pair.of(FiguraText.of("popup_menu.change_volume"), PopupMenu::openVolumePanel)
    );
    private static final int LENGTH = BUTTONS.size();

    // runtime data
    private static int index = 0;
    private static boolean enabled = false;
    private static boolean volumePanelOpen = false;
    private static boolean volumeDragging = false;
    private static boolean editCategoryVolume = false;
    private static double popupX;
    private static double popupY;
    private static double popupScale = 1d;
    private static double lastMouseX;
    private static double lastMouseY;
    private static boolean popupProjected;
    private static Entity entity;
    private static UUID id;
    private static Component targetName;
    private static Vec3 targetPos;
    private static SkullBlockEntity skullTarget;
    private static boolean profileTarget;

    public static void render(GuiGraphics gui) {
        if (!isEnabled()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            close();
            return;
        }

        UUID renderId = id;
        Component renderName = targetName;
        Vec3 renderPos = targetPos;

        if (entity != null) {
            renderId = entity.getUUID();
            renderName = entity.getName().copy();
            renderPos = entity.getPosition(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false))
                    .add(0f, entity.getBbHeight() + 0.1f, 0f);

            if (entity.isInvisibleTo(minecraft.player) && entity != minecraft.player) {
                close();
                return;
            }
        } else if (skullTarget != null) {
            if (minecraft.level == null || skullTarget.isRemoved() || minecraft.level.getBlockEntity(skullTarget.getBlockPos()) != skullTarget) {
                close();
                return;
            }

            ResolvableProfile profile = skullTarget.getOwnerProfile();
            renderId = AvatarManager.getIdForProfile(profile);
            if (renderId == null || AvatarManager.getAvatarForPlayer(renderId) == null) {
                close();
                return;
            }

            renderName = resolveProfileName(profile, renderId);
            renderPos = Vec3.atCenterOf(skullTarget.getBlockPos()).add(0d, 0.75d, 0d);
        }

        if (renderId == null || renderName == null || renderPos == null) {
            close();
            return;
        }

        id = renderId;

        GlStateManager._disableDepthTest();
        Matrix3x2fStack pose = gui.pose();
        pose.pushMatrix();

        // world to screen space
        FiguraVec4 vec = MathUtils.worldToScreenSpace(FiguraVec3.fromVec3(renderPos));
        if (vec.z < 1) { // too close
            pose.popMatrix();
            return;
        }

        Window window = minecraft.getWindow();
        double w = window.getGuiScaledWidth();
        double h = window.getGuiScaledHeight();
        double s = Configs.POPUP_SCALE.value * Math.max(Math.min(window.getHeight() * 0.035 / vec.w * (1d / window.getGuiScale()), Configs.POPUP_MAX_SIZE.value), Configs.POPUP_MIN_SIZE.value);

        popupX = (vec.x + 1) / 2 * w;
        popupY = (vec.y + 1) / 2 * h;
        popupScale = s * 0.5d;
        popupProjected = true;

        pose.translate((float) popupX, (float) popupY);
        pose.scale((float) (s * 0.5), (float) (s * 0.5));

        // background
        int width = LENGTH * ICON_SIZE;

        UIHelper.enableBlend();
        int frame = Configs.REDUCED_MOTION.value ? 0 : (int) ((FiguraMod.ticks / 5f) % 4);
        gui.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, width / -2, -24, 0, frame * 26, width, 26, width, 26, width, 104);

        // icons
        pose.translate(0f, 0f);
        UIHelper.enableBlend();
        for (int i = 0; i < LENGTH; i++)
            gui.blit(RenderPipelines.GUI_TEXTURED, ICONS, width / -2 + (ICON_SIZE * i), -24, ICON_SIZE * i, i == index ? ICON_SIZE : 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, width, 36);

        // texts
        Font font = minecraft.font;

        Component title = BUTTONS.get(index).getFirst();

        PermissionPack tc = PermissionManager.get(id);
        MutableComponent permissionName = tc.getCategoryName().append(tc.hasChanges() ? "*" : "");

        MutableComponent name = renderName.copy();

        boolean error = false;
        boolean version = false;
        boolean noPermissions = false;

        Component badges = Badges.fetchBadges(id);
        if (!badges.getString().isEmpty())
            name.append(" ").append(badges);

        Avatar avatar = AvatarManager.getAvatarForPlayer(id);
        if (avatar != null) {
            error = avatar.scriptError;
            version = avatar.versionStatus > 0;
            noPermissions = !avatar.noPermissions.isEmpty();
        }

        // render texts
        UIHelper.renderOutlineText(gui, font, name, -font.width(name) / 2, -36, 0xFFFFFF, 0x202020);

        pose.scale(0.5f, 0.5f);
        pose.translate(0f, 0f);

        UIHelper.renderOutlineText(gui, font, permissionName, -font.width(permissionName) / 2, -54, 0xFFFFFF, 0x202020);
        gui.drawString(font, title, -width + 4, -12, UIHelper.adjustColor(0xFFFFFF));

        if (error)
            UIHelper.renderOutlineText(gui, font, ERROR_WARN, -font.width(ERROR_WARN) / 2, 0, 0xFFFFFF, 0x202020);
        if (version)
            UIHelper.renderOutlineText(gui, font, VERSION_WARN, -font.width(VERSION_WARN) / 2, error ? font.lineHeight : 0, 0xFFFFFF, 0x202020);
        if (noPermissions)
            UIHelper.renderOutlineText(gui, font, PERMISSION_WARN, -font.width(PERMISSION_WARN) / 2, (error ? font.lineHeight : 0) + (version ? font.lineHeight : 0), 0xFFFFFF, 0x202020);

        if (volumePanelOpen)
            renderVolumePanel(gui, font);

        // finish rendering
        pose.popMatrix();
    }

    public static void scroll(double d) {
        if (volumePanelOpen) {
            setVolume(getVolume() + (d > 0 ? 5 : -5), true);
            return;
        }

        index = (int) (index - d + LENGTH) % LENGTH;
    }

    public static void hotbarKeyPressed(int i) {
        if (volumePanelOpen)
            return;

        if (i < LENGTH && i >= 0)
            index = i;
    }

    public static void run() {
        if (id != null)
            BUTTONS.get(index).getSecond().accept(id);

        if (!volumePanelOpen)
            close();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        if (!enabled) {
            close();
            return;
        }

        PopupMenu.enabled = enabled;
    }

    public static boolean isVolumePanelOpen() {
        return volumePanelOpen;
    }

    public static boolean mouseButton(double mouseX, double mouseY, int button, int action) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (!isEnabled() || id == null || button != 0)
            return false;

        if (action == 0) {
            if (volumeDragging) {
                volumeDragging = false;
                saveVolume();
                return true;
            }

            return false;
        }

        if (!volumePanelOpen) {
            if (index == VOLUME_INDEX && isMouseOverIcon(mouseX, mouseY, VOLUME_INDEX)) {
                openVolumePanel(id);
                return true;
            }

            return false;
        }

        double localX = toLocalX(mouseX);
        double localY = toLocalY(mouseY);
        if (isMouseOverVolumeToggle(localX, localY)) {
            editCategoryVolume = !editCategoryVolume;
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
            return true;
        }

        if (isMouseOverVolumeSlider(localX, localY)) {
            volumeDragging = true;
            setVolumeFromLocalX(localX, false);
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
            return true;
        }

        if (!isMouseOverVolumePanel(localX, localY)) {
            saveVolume();
            close();
            return true;
        }

        return true;
    }

    public static boolean mouseMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (!volumeDragging)
            return false;

        setVolumeFromLocalX(toLocalX(mouseX), false);
        return true;
    }

    public static boolean hasEntity() {
        return entity != null || skullTarget != null || (id != null && targetName != null && targetPos != null);
    }

    public static void setEntity(Entity entity) {
        PopupMenu.entity = entity;
        PopupMenu.id = entity == null ? null : entity.getUUID();
        PopupMenu.targetName = null;
        PopupMenu.targetPos = null;
        PopupMenu.skullTarget = null;
        PopupMenu.profileTarget = false;
    }

    public static void setProfileTarget(UUID id, Component name, Vec3 pos) {
        PopupMenu.entity = null;
        PopupMenu.id = id;
        PopupMenu.targetName = name;
        PopupMenu.targetPos = pos;
        PopupMenu.skullTarget = null;
        PopupMenu.profileTarget = true;
    }

    public static boolean setSkullTarget(SkullBlockEntity skullTarget) {
        if (skullTarget == null)
            return false;

        ResolvableProfile profile = skullTarget.getOwnerProfile();
        UUID id = AvatarManager.getIdForProfile(profile);
        if (id == null)
            return false;

        PopupMenu.entity = null;
        PopupMenu.id = id;
        PopupMenu.targetName = resolveProfileName(profile, id);
        PopupMenu.targetPos = Vec3.atCenterOf(skullTarget.getBlockPos()).add(0d, 0.75d, 0d);
        PopupMenu.skullTarget = skullTarget;
        PopupMenu.profileTarget = true;
        return true;
    }

    public static UUID getEntityId() {
        return id;
    }

    public static boolean isProfileTarget() {
        return profileTarget;
    }

    private static void clearTarget() {
        entity = null;
        id = null;
        targetName = null;
        targetPos = null;
        skullTarget = null;
        profileTarget = false;
        popupProjected = false;
    }

    public static void close() {
        close(true);
    }

    public static void dismiss() {
        close(false);
    }

    private static void close(boolean grabMouse) {
        saveVolume();
        enabled = false;
        volumePanelOpen = false;
        volumeDragging = false;
        editCategoryVolume = false;
        clearTarget();
        index = 0;
        if (grabMouse)
            grabMouseIfNeeded();
    }

    private static void openVolumePanel(UUID targetId) {
        if (targetId == null)
            return;

        id = targetId;
        index = VOLUME_INDEX;
        enabled = true;
        volumePanelOpen = true;
        volumeDragging = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null)
            minecraft.mouseHandler.releaseMouse();
    }

    private static void renderVolumePanel(GuiGraphics gui, Font font) {
        int panelX = -VOLUME_PANEL_WIDTH / 2;
        int panelY = 6;

        gui.fill(panelX + 2, panelY + 2, panelX + VOLUME_PANEL_WIDTH + 2, panelY + VOLUME_PANEL_HEIGHT + 2, 0x80000000);
        gui.fill(panelX + 1, panelY + 1, panelX + VOLUME_PANEL_WIDTH - 1, panelY + VOLUME_PANEL_HEIGHT - 1, UIHelper.adjustColor(0xE0101010));
        UIHelper.fillOutline(gui, panelX, panelY, VOLUME_PANEL_WIDTH, VOLUME_PANEL_HEIGHT, UIHelper.adjustColor(0xFFFFFFFF));

        int toggleX = volumeToggleX();
        int toggleY = volumeToggleY();
        boolean toggleHovered = isMouseOverVolumeToggle(toLocalX(lastMouseX), toLocalY(lastMouseY));
        UIHelper.blitSliced(gui, toggleX, toggleY, VOLUME_TOGGLE_WIDTH, VOLUME_TOGGLE_HEIGHT, toggleHovered ? 32f : 16f, 0f, 16, 16, 48, 32, BUTTON);

        Component scope = FiguraText.of(editCategoryVolume ? "popup_menu.volume_scope_all" : "popup_menu.volume_scope_target");
        UIHelper.renderCenteredScrollingText(gui, scope, toggleX + 1, toggleY, VOLUME_TOGGLE_WIDTH - 2, VOLUME_TOGGLE_HEIGHT, 0xFFFFFF);

        int sliderX = volumeSliderX();
        int sliderY = volumeSliderY();
        int volume = getVolume();
        float progress = volume / 100f;
        boolean sliderActive = volumeDragging || isMouseOverVolumeSlider(toLocalX(lastMouseX), toLocalY(lastMouseY));

        UIHelper.enableBlend();
        gui.blit(RenderPipelines.GUI_TEXTURED, SliderWidget.SLIDER_TEXTURE, sliderX, sliderY + 3, sliderActive ? 10f : 0f, 0f, VOLUME_SLIDER_WIDTH, 5, 5, 5, 33, 16);
        for (int i = 0; i < 3; i++)
            gui.blit(RenderPipelines.GUI_TEXTURED, SliderWidget.SLIDER_TEXTURE, sliderX + 3 + ((VOLUME_SLIDER_WIDTH - 11) * i / 2), sliderY + 3, sliderActive ? 15f : 5f, 0f, 5, 5, 5, 5, 33, 16);
        gui.blit(RenderPipelines.GUI_TEXTURED, SliderWidget.SLIDER_TEXTURE, sliderX + Math.round(progress * (VOLUME_SLIDER_WIDTH - 11)), sliderY, sliderActive ? 22f : 11f, 5f, 11, 11, 33, 16);

        Component value = Component.literal(volume + "%");
        gui.drawString(font, value, panelX + VOLUME_PANEL_WIDTH - font.width(value) - 6, panelY + 8, UIHelper.adjustColor(0xFFFFFF));
    }

    private static PermissionPack getVolumePack() {
        PermissionPack target = PermissionManager.get(id);
        if (!editCategoryVolume)
            return target;

        PermissionPack category = PermissionManager.CATEGORIES.get(target.getCategory());
        return category == null ? target : category;
    }

    private static int getVolume() {
        return Mth.clamp(getVolumePack().get(Permissions.VOLUME), 0, 100);
    }

    private static void setVolume(int volume, boolean save) {
        getVolumePack().insert(Permissions.VOLUME, Mth.clamp(volume, 0, 100), FiguraMod.MOD_ID);
        if (save)
            saveVolume();
    }

    private static void setVolumeFromLocalX(double localX, boolean save) {
        double progress = (localX - volumeSliderX()) / (double) (VOLUME_SLIDER_WIDTH - 11);
        setVolume((int) Math.round(Mth.clamp(progress, 0d, 1d) * 100d), save);
    }

    private static void saveVolume() {
        if (volumePanelOpen && id != null)
            PermissionManager.saveToDisk();
    }

    private static boolean isMouseOverIcon(double mouseX, double mouseY, int icon) {
        if (!popupProjected)
            return false;

        return UIHelper.isMouseOver((LENGTH * ICON_SIZE) / -2 + (ICON_SIZE * icon), -24, ICON_SIZE, ICON_SIZE, toLocalX(mouseX), toLocalY(mouseY));
    }

    private static boolean isMouseOverVolumePanel(double localX, double localY) {
        return UIHelper.isMouseOver(-VOLUME_PANEL_WIDTH / 2, 6, VOLUME_PANEL_WIDTH, VOLUME_PANEL_HEIGHT, localX, localY);
    }

    private static boolean isMouseOverVolumeToggle(double localX, double localY) {
        return UIHelper.isMouseOver(volumeToggleX(), volumeToggleY(), VOLUME_TOGGLE_WIDTH, VOLUME_TOGGLE_HEIGHT, localX, localY);
    }

    private static boolean isMouseOverVolumeSlider(double localX, double localY) {
        return UIHelper.isMouseOver(volumeSliderX(), volumeSliderY(), VOLUME_SLIDER_WIDTH, VOLUME_SLIDER_HEIGHT, localX, localY);
    }

    private static int volumeToggleX() {
        return -VOLUME_PANEL_WIDTH / 2 + 6;
    }

    private static int volumeToggleY() {
        return 11;
    }

    private static int volumeSliderX() {
        return -VOLUME_PANEL_WIDTH / 2 + 50;
    }

    private static int volumeSliderY() {
        return 12;
    }

    private static double toLocalX(double mouseX) {
        return popupScale == 0d ? 0d : (mouseX - popupX) / popupScale;
    }

    private static double toLocalY(double mouseY) {
        return popupScale == 0d ? 0d : (mouseY - popupY) / popupScale;
    }

    private static void grabMouseIfNeeded() {
        Minecraft minecraft = Minecraft.getInstance();
        Avatar avatar = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
        if (minecraft.screen == null && (avatar == null || avatar.luaRuntime == null || !avatar.luaRuntime.host.unlockCursor))
            minecraft.mouseHandler.grabMouse();
    }

    private static Component resolveProfileName(ResolvableProfile profile, UUID id) {
        String name = null;
        if (profile != null && profile.partialProfile() != null)
            name = profile.partialProfile().name();
        if ((name == null || name.isBlank()) && profile != null && profile.name().isPresent())
            name = profile.name().get();

        return Component.literal(name == null || name.isBlank() ? id.toString() : name);
    }
}
