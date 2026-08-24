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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.Badges;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.math.vector.FiguraVec4;
import org.figuramc.figura.lua.api.popup.PopupAPI;
import org.figuramc.figura.lua.api.popup.PopupInput;
import org.figuramc.figura.permissions.PermissionManager;
import org.figuramc.figura.permissions.PermissionPack;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.MathUtils;
import org.figuramc.figura.utils.TextUtils;
import org.figuramc.figura.utils.ui.UIHelper;
import org.figuramc.figura.gui.widgets.SliderWidget;
import org.figuramc.figura.gui.widgets.SwitchButton;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class PopupMenu {

    private static final FiguraIdentifier BACKGROUND = new FiguraIdentifier("textures/gui/popup.png");
    private static final FiguraIdentifier ICONS = new FiguraIdentifier("textures/gui/popup_icons.png");
    private static final FiguraIdentifier BUTTON = new FiguraIdentifier("textures/gui/button.png");

    private static final int ICON_SIZE = 18;
    private static final int VOLUME_PANEL_WIDTH = 132;
    private static final int VOLUME_PANEL_HEIGHT = 24;
    private static final int VOLUME_TOGGLE_WIDTH = 38;
    private static final int VOLUME_TOGGLE_HEIGHT = 14;
    private static final int VOLUME_SLIDER_WIDTH = 58;
    private static final int VOLUME_SLIDER_HEIGHT = 11;
    private static final int PERMISSION_INDEX = 2;
    private static final int CONTROLS_INDEX = 3;
    private static final int VOLUME_INDEX = 4;
    private static final int CONTROL_PANEL_WIDTH = 168;
    private static final int CUSTOM_ROW_HEIGHT = 18;
    private static final int CUSTOM_VISIBLE_ROWS = 4;
    private static final int CUSTOM_SLIDER_WIDTH = 60;
    private static final int CUSTOM_TOGGLE_WIDTH = 34;
    private static final int CUSTOM_BUTTON_WIDTH = 38;
    private static final List<ViewerVisibilityManager.Setting> VISIBILITY_CONTROLS = List.of(
            ViewerVisibilityManager.Setting.AVATAR,
            ViewerVisibilityManager.Setting.NAMEPLATE,
            ViewerVisibilityManager.Setting.CUSTOM_SKULLS
    );

    private enum PanelKind {
        NONE,
        PERMISSIONS,
        CONTROLS,
        VOLUME
    }

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
            Pair.of(FiguraText.of("popup_menu.permissions"), PopupMenu::openPermissionPanel),
            Pair.of(FiguraText.of("popup_menu.avatar_controls"), PopupMenu::openControlsPanel),
            Pair.of(FiguraText.of("popup_menu.change_volume"), PopupMenu::openVolumePanel)
    );
    private static final int LENGTH = BUTTONS.size();

    // runtime data
    private static int index = 0;
    private static boolean enabled = false;
    private static PanelKind panel = PanelKind.NONE;
    private static boolean volumeDragging = false;
    private static PopupInput customDraggingInput;
    private static boolean editCategoryVolume = false;
    private static boolean directControlsOnly = false;
    private static PopupInput.Target forcedControlTarget = null;
    private static int customScrollIndex = 0;
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
    private static String targetContextKey;
    private static String targetHeadName;

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

        if (directControlsOnly && renderPos != null)
            renderPos = renderPos.add(0d, Configs.HEAD_POPUP_Y_OFFSET.value, 0d);

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
        if (isHeadCursorMode()) {
            lastMouseX = w / 2d;
            lastMouseY = h / 2d;
        }

        pose.translate((float) popupX, (float) popupY);
        pose.scale((float) (s * 0.5), (float) (s * 0.5));

        Font font = minecraft.font;
        if (directControlsOnly) {
            pose.scale(0.5f, 0.5f);
            pushTargetContext();
            try {
                renderPanel(gui, font);
            } finally {
                popTargetContext();
            }
            pose.popMatrix();
            return;
        }

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

        if (panel != PanelKind.NONE) {
            pushTargetContext();
            try {
                renderPanel(gui, font);
            } finally {
                popTargetContext();
            }
        }

        // finish rendering
        pose.popMatrix();
    }

    public static void scroll(double d) {
        if (panel == PanelKind.VOLUME) {
            setVolume(getVolume() + (d > 0 ? 5 : -5), true);
            return;
        }

        if (panel == PanelKind.PERMISSIONS) {
            cyclePermissionCategory(d);
            return;
        }

        if (panel == PanelKind.CONTROLS) {
            pushTargetContext();
            try {
                scrollControlsPanel(d);
            } finally {
                popTargetContext();
            }
            return;
        }

        index = (int) (index - d + LENGTH) % LENGTH;
    }

    public static void hotbarKeyPressed(int i) {
        if (isPanelOpen())
            return;

        if (i < LENGTH && i >= 0)
            index = i;
    }

    public static void run() {
        if (id != null)
            BUTTONS.get(index).getSecond().accept(id);

        if (!isPanelOpen())
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
        directControlsOnly = false;
        forcedControlTarget = null;
    }

    public static boolean isVolumePanelOpen() {
        return isPanelOpen();
    }

    public static boolean isPanelOpen() {
        return panel != PanelKind.NONE;
    }

    public static boolean mouseButton(double mouseX, double mouseY, int button, int action) {
        mouseX = getInteractionMouseX(mouseX);
        mouseY = getInteractionMouseY(mouseY);
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (directControlsOnly && button == 1 && action != 0) {
            close();
            return true;
        }

        if (!isEnabled() || id == null || button != 0)
            return false;

        if (action == 0) {
            if (volumeDragging) {
                volumeDragging = false;
                saveVolume();
                return true;
            }

            if (customDraggingInput != null) {
                customDraggingInput.persist();
                customDraggingInput = null;
                return true;
            }

            return false;
        }

        if (!isPanelOpen()) {
            if ((index == PERMISSION_INDEX || index == CONTROLS_INDEX || index == VOLUME_INDEX) && isMouseOverIcon(mouseX, mouseY, index)) {
                BUTTONS.get(index).getSecond().accept(id);
                return true;
            }

            return false;
        }

        double localX = toPanelLocalX(mouseX);
        double localY = toPanelLocalY(mouseY);

        if (panel == PanelKind.PERMISSIONS)
            return handlePermissionPanelClick(localX, localY);
        if (panel == PanelKind.CONTROLS) {
            pushTargetContext();
            try {
                return handleControlsPanelClick(localX, localY);
            } finally {
                popTargetContext();
            }
        }

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

        if (!isMouseOverPanel(localX, localY)) {
            if (directControlsOnly)
                return true;

            saveVolume();
            close();
            return true;
        }

        return true;
    }

    public static boolean mouseMoved(double mouseX, double mouseY) {
        mouseX = getInteractionMouseX(mouseX);
        mouseY = getInteractionMouseY(mouseY);
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (customDraggingInput != null) {
            pushTargetContext();
            try {
                setCustomSliderFromLocalX(customDraggingInput, toPanelLocalX(mouseX), false);
            } finally {
                popTargetContext();
            }
            return true;
        }

        if (!volumeDragging)
            return false;

        setVolumeFromLocalX(toPanelLocalX(mouseX), false);
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
        PopupMenu.targetContextKey = null;
        PopupMenu.targetHeadName = null;
    }

    public static void setProfileTarget(UUID id, Component name, Vec3 pos) {
        PopupMenu.entity = null;
        PopupMenu.id = id;
        PopupMenu.targetName = name;
        PopupMenu.targetPos = pos;
        PopupMenu.skullTarget = null;
        PopupMenu.profileTarget = true;
        PopupMenu.targetContextKey = contextKeyForProfile(id);
        PopupMenu.targetHeadName = null;
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
        PopupMenu.targetContextKey = contextKeyForBlock(skullTarget.getBlockPos());
        PopupMenu.targetHeadName = getHeadName(skullTarget);
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
        targetContextKey = null;
        targetHeadName = null;
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
        if (customDraggingInput != null)
            customDraggingInput.persist();
        enabled = false;
        panel = PanelKind.NONE;
        volumeDragging = false;
        customDraggingInput = null;
        editCategoryVolume = false;
        directControlsOnly = false;
        forcedControlTarget = null;
        customScrollIndex = 0;
        clearTarget();
        index = 0;
        if (grabMouse)
            grabMouseIfNeeded();
    }

    private static void openPanel(UUID targetId, PanelKind panel, int index) {
        if (targetId == null)
            return;

        id = targetId;
        PopupMenu.index = index;
        enabled = true;
        PopupMenu.panel = panel;
        volumeDragging = false;
        customDraggingInput = null;
        directControlsOnly = false;
        forcedControlTarget = null;
        customScrollIndex = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null)
            minecraft.mouseHandler.releaseMouse();
    }

    private static void openDirectControlsPanel(UUID targetId, PopupInput.Target target) {
        if (targetId == null)
            return;

        id = targetId;
        index = CONTROLS_INDEX;
        enabled = true;
        panel = PanelKind.CONTROLS;
        volumeDragging = false;
        customDraggingInput = null;
        directControlsOnly = true;
        forcedControlTarget = target;
        customScrollIndex = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && !isHeadCursorMode())
            minecraft.mouseHandler.releaseMouse();
    }

    public static boolean openHeadControlsFromLook() {
        if (AvatarManager.panic)
            return false;

        if (directControlsOnly) {
            close();
            return true;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null)
            return false;

        if (!findHeadTarget(minecraft))
            return false;

        openDirectControlsPanel(id, PopupInput.Target.HEAD);
        return true;
    }

    public static boolean shouldOpenHeadControlsForMouse(int button) {
        return switch (Configs.HEAD_POPUP_TRIGGER.value) {
            case 1 -> button == 1;
            case 2 -> button == 0;
            case 3 -> button == 0 || button == 1;
            default -> false;
        };
    }

    private static boolean findHeadTarget(Minecraft minecraft) {
        float tickDelta = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Entity target = FiguraMod.extendedPickEntity;

        if (target != null && !target.isInvisibleTo(minecraft.player)) {
            ItemStack headItem = getProfileItem(target);
            Vec3 pos = target.getPosition(tickDelta).add(0d, target.getBbHeight() + 0.1d, 0d);
            if (setProfileTarget(headItem.isEmpty() ? null : headItem.get(DataComponents.PROFILE), pos)) {
                targetContextKey = contextKeyForHead(null, null, target);
                targetHeadName = getHeadName(headItem);
                return true;
            }
        }

        HitResult blockTarget = minecraft.hitResult;
        Entity cameraEntity = minecraft.getCameraEntity();
        if (!(blockTarget instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) && cameraEntity != null)
            blockTarget = cameraEntity.pick(20d, tickDelta, false);

        if (blockTarget instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK && minecraft.level != null) {
            if (minecraft.level.getBlockEntity(blockHit.getBlockPos()) instanceof SkullBlockEntity skullBlockEntity)
                return setSkullTarget(skullBlockEntity);
        }

        return false;
    }

    private static ItemStack getProfileItem(Entity entity) {
        ItemStack stack = ItemStack.EMPTY;
        if (entity instanceof ItemFrame itemFrame)
            stack = itemFrame.getItem();
        else if (entity instanceof ItemEntity itemEntity)
            stack = itemEntity.getItem();
        else if (entity instanceof LivingEntity livingEntity)
            stack = livingEntity.getItemBySlot(EquipmentSlot.HEAD);

        return stack;
    }

    private static String getHeadName(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : cleanHeadName(stack.getHoverName().getString());
    }

    private static String getHeadName(SkullBlockEntity skull) {
        Minecraft minecraft = Minecraft.getInstance();
        if (skull == null || minecraft.level == null)
            return null;

        try {
            CompoundTag tag = skull.saveWithoutMetadata(minecraft.level.registryAccess());
            String name = getHeadNameFromTag(tag, "custom_name");
            if (name == null)
                name = getHeadNameFromTag(tag, "CustomName");
            return name;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getHeadNameFromTag(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key))
            return null;

        String raw = tag.getStringOr(key, "");
        if (raw == null || raw.isBlank())
            return null;

        return cleanHeadName(TextUtils.tryParseJson(raw).getString());
    }

    private static String cleanHeadName(String name) {
        if (name == null)
            return null;

        String clean = name.trim();
        while (clean.length() >= 2 && clean.startsWith("\"") && clean.endsWith("\""))
            clean = clean.substring(1, clean.length() - 1).trim();

        return clean.isEmpty() ? null : clean;
    }

    private static boolean setProfileTarget(ResolvableProfile profile, Vec3 pos) {
        UUID id = AvatarManager.getIdForProfile(profile);
        if (id == null)
            return false;

        String name = profile == null || profile.partialProfile() == null ? null : profile.partialProfile().name();
        if ((name == null || name.isBlank()) && profile != null && profile.name().isPresent())
            name = profile.name().get();
        setProfileTarget(id, name == null || name.isBlank() ? Component.literal(id.toString()) : Component.literal(name), pos);
        return true;
    }

    public static String contextKeyForHead(BlockPos blockPos, ItemStack item, Entity entity) {
        if (blockPos != null)
            return contextKeyForBlock(blockPos);
        if (entity != null)
            return "entity:" + entity.getUUID() + ":head";
        if (item != null && !item.isEmpty()) {
            ResolvableProfile profile = item.get(DataComponents.PROFILE);
            UUID id = AvatarManager.getIdForProfile(profile);
            String name = item.getHoverName().getString();
            return "item:" + (id == null ? "unknown" : id) + ":" + name;
        }
        return null;
    }

    private static String contextKeyForBlock(BlockPos blockPos) {
        Minecraft minecraft = Minecraft.getInstance();
        String level = minecraft.level == null ? "unknown" : minecraft.level.dimension().toString();
        return "block:" + level + ":" + blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ();
    }

    private static String contextKeyForProfile(UUID id) {
        return id == null ? null : "profile:" + id;
    }

    private static void pushTargetContext() {
        PopupAPI.pushContext(targetContextKey);
    }

    private static void popTargetContext() {
        PopupAPI.popContext();
    }

    private static void openPermissionPanel(UUID targetId) {
        openPanel(targetId, PanelKind.PERMISSIONS, PERMISSION_INDEX);
    }

    private static void openControlsPanel(UUID targetId) {
        openPanel(targetId, PanelKind.CONTROLS, CONTROLS_INDEX);
    }

    private static void openVolumePanel(UUID targetId) {
        openPanel(targetId, PanelKind.VOLUME, VOLUME_INDEX);
    }

    private static void renderPanel(GuiGraphics gui, Font font) {
        switch (panel) {
            case PERMISSIONS -> renderPermissionPanel(gui, font);
            case CONTROLS -> renderControlsPanel(gui, font);
            case VOLUME -> renderVolumePanel(gui, font);
            default -> {}
        }
    }

    private static void drawPanel(GuiGraphics gui, int width, int height) {
        int panelX = -width / 2;
        int panelY = 6;

        gui.fill(panelX + 2, panelY + 2, panelX + width + 2, panelY + height + 2, 0x80000000);
        gui.fill(panelX + 1, panelY + 1, panelX + width - 1, panelY + height - 1, UIHelper.adjustColor(0xE0101010));
        UIHelper.fillOutline(gui, panelX, panelY, width, height, UIHelper.adjustColor(0xFFFFFFFF));
    }

    private static void renderPermissionPanel(GuiGraphics gui, Font font) {
        int panelWidth = CONTROL_PANEL_WIDTH;
        int panelHeight = 20 + Permissions.Category.values().length * CUSTOM_ROW_HEIGHT + 4;
        int panelX = -panelWidth / 2;
        int panelY = 6;
        drawPanel(gui, panelWidth, panelHeight);

        gui.drawString(font, FiguraText.of("popup_menu.permissions"), panelX + 6, panelY + 6, UIHelper.adjustColor(0xFFFFFF));

        PermissionPack pack = PermissionManager.get(id);
        int rowY = panelY + 20;
        double localX = toPanelLocalX(lastMouseX);
        double localY = toPanelLocalY(lastMouseY);
        for (Permissions.Category category : Permissions.Category.values()) {
            int y = rowY + category.index * CUSTOM_ROW_HEIGHT;
            boolean selected = pack.getCategory() == category;
            boolean hovered = isMouseOverPermissionRow(localX, localY, category);
            int bg = selected ? 0xA0303030 : hovered ? 0x60202020 : 0x00000000;
            if (bg != 0)
                gui.fill(panelX + 4, y, panelX + panelWidth - 4, y + CUSTOM_ROW_HEIGHT - 1, UIHelper.adjustColor(bg));

            Component name = category.text.copy();
            int color = selected ? category.color : 0xFFFFFF;
            UIHelper.renderCenteredScrollingText(gui, name, panelX + 16, y, panelWidth - 28, CUSTOM_ROW_HEIGHT, color);
            if (selected)
                gui.drawString(font, ">", panelX + 7, y + 5, UIHelper.adjustColor(0xFFFFFF));
        }
    }

    private static void renderVolumePanel(GuiGraphics gui, Font font) {
        int panelX = -VOLUME_PANEL_WIDTH / 2;
        int panelY = 6;

        drawPanel(gui, VOLUME_PANEL_WIDTH, VOLUME_PANEL_HEIGHT);

        int toggleX = volumeToggleX();
        int toggleY = volumeToggleY();
        boolean toggleHovered = isMouseOverVolumeToggle(toPanelLocalX(lastMouseX), toPanelLocalY(lastMouseY));
        UIHelper.blitSliced(gui, toggleX, toggleY, VOLUME_TOGGLE_WIDTH, VOLUME_TOGGLE_HEIGHT, toggleHovered ? 32f : 16f, 0f, 16, 16, 48, 32, BUTTON);

        Component scope = FiguraText.of(editCategoryVolume ? "popup_menu.volume_scope_all" : "popup_menu.volume_scope_target");
        UIHelper.renderCenteredScrollingText(gui, scope, toggleX + 1, toggleY, VOLUME_TOGGLE_WIDTH - 2, VOLUME_TOGGLE_HEIGHT, 0xFFFFFF);

        int sliderX = volumeSliderX();
        int sliderY = volumeSliderY();
        int volume = getVolume();
        float progress = volume / 100f;
        boolean sliderActive = volumeDragging || isMouseOverVolumeSlider(toPanelLocalX(lastMouseX), toPanelLocalY(lastMouseY));

        UIHelper.enableBlend();
        gui.blit(RenderPipelines.GUI_TEXTURED, SliderWidget.SLIDER_TEXTURE, sliderX, sliderY + 3, sliderActive ? 10f : 0f, 0f, VOLUME_SLIDER_WIDTH, 5, 5, 5, 33, 16);
        for (int i = 0; i < 3; i++)
            gui.blit(RenderPipelines.GUI_TEXTURED, SliderWidget.SLIDER_TEXTURE, sliderX + 3 + ((VOLUME_SLIDER_WIDTH - 11) * i / 2), sliderY + 3, sliderActive ? 15f : 5f, 0f, 5, 5, 5, 5, 33, 16);
        gui.blit(RenderPipelines.GUI_TEXTURED, SliderWidget.SLIDER_TEXTURE, sliderX + Math.round(progress * (VOLUME_SLIDER_WIDTH - 11)), sliderY, sliderActive ? 22f : 11f, 5f, 11, 11, 33, 16);

        Component value = Component.literal(volume + "%");
        gui.drawString(font, value, panelX + VOLUME_PANEL_WIDTH - font.width(value) - 6, panelY + 8, UIHelper.adjustColor(0xFFFFFF));
    }

    private static void renderControlsPanel(GuiGraphics gui, Font font) {
        List<ViewerVisibilityManager.Setting> visibilityControls = getVisibilityControls();
        List<PopupInput> controls = getPopupControls();
        clampControlScroll(getControlCount(visibilityControls, controls));

        int panelWidth = CONTROL_PANEL_WIDTH;
        int controlCount = getControlCount(visibilityControls, controls);
        int visibleRows = Math.min(controlCount, CUSTOM_VISIBLE_ROWS);
        int panelHeight = controlCount == 0 ? 42 : 22 + visibleRows * CUSTOM_ROW_HEIGHT + (controlCount > CUSTOM_VISIBLE_ROWS ? 10 : 4);
        int panelX = -panelWidth / 2;
        int panelY = 6;
        drawPanel(gui, panelWidth, panelHeight);

        gui.drawString(font, FiguraText.of(directControlsOnly ? "popup_menu.head_controls" : "popup_menu.avatar_controls"), panelX + 6, panelY + 6, UIHelper.adjustColor(0xFFFFFF));

        if (controlCount == 0) {
            Component empty = FiguraText.of(directControlsOnly ? "popup_menu.no_head_controls" : "popup_menu.no_avatar_controls");
            UIHelper.renderCenteredScrollingText(gui, empty, panelX + 6, panelY + 22, panelWidth - 12, 14, 0xAAAAAA);
            return;
        }

        double localX = toPanelLocalX(lastMouseX);
        double localY = toPanelLocalY(lastMouseY);
        for (int i = 0; i < visibleRows; i++) {
            int controlIndex = customScrollIndex + i;
            int y = customRowY(i);
            boolean hovered = isMouseOverCustomRow(localX, localY, i);
            if (hovered)
                gui.fill(panelX + 4, y, panelX + panelWidth - 4, y + CUSTOM_ROW_HEIGHT - 1, UIHelper.adjustColor(0x60202020));

            if (controlIndex < visibilityControls.size()) {
                ViewerVisibilityManager.Setting setting = visibilityControls.get(controlIndex);
                UIHelper.renderCenteredScrollingText(gui, setting.title(), panelX + 6, y, panelWidth - CUSTOM_TOGGLE_WIDTH - 18, CUSTOM_ROW_HEIGHT, 0xFFFFFF);
                renderVisibilityToggle(gui, setting, y);
                continue;
            }

            PopupInput input = controls.get(controlIndex - visibilityControls.size());
            int rightWidth = switch (input.getInputType()) {
                case TOGGLE -> CUSTOM_TOGGLE_WIDTH;
                case SLIDER -> CUSTOM_SLIDER_WIDTH;
                case BUTTON -> CUSTOM_BUTTON_WIDTH;
            };
            UIHelper.renderCenteredScrollingText(gui, Component.literal(input.getTitle()), panelX + 6, y, panelWidth - rightWidth - 18, CUSTOM_ROW_HEIGHT, 0xFFFFFF);

            if (input.getInputType() == PopupInput.Type.TOGGLE)
                renderCustomToggle(gui, font, input, y);
            else if (input.getInputType() == PopupInput.Type.SLIDER)
                renderCustomSlider(gui, font, input, y);
            else
                renderCustomButton(gui, input, y);
        }

        if (controlCount > CUSTOM_VISIBLE_ROWS) {
            Component position = Component.literal((customScrollIndex + 1) + "-" + (customScrollIndex + visibleRows) + "/" + controlCount);
            UIHelper.renderCenteredScrollingText(gui, position, panelX + panelWidth - 48, panelY + panelHeight - 10, 42, 8, 0xA0A0A0);
        }
    }

    private static void renderVisibilityToggle(GuiGraphics gui, ViewerVisibilityManager.Setting setting, int rowY) {
        int toggleX = customToggleX();
        int toggleY = rowY + 2;
        boolean hovered = isMouseOverCustomToggle(toPanelLocalX(lastMouseX), toPanelLocalY(lastMouseY), rowY);
        UIHelper.blitSliced(gui, toggleX, toggleY, CUSTOM_TOGGLE_WIDTH, VOLUME_TOGGLE_HEIGHT, hovered ? 32f : 16f, 0f, 16, 16, 48, 32, BUTTON);

        boolean visible = ViewerVisibilityManager.isVisible(id, setting);
        Component value = visible ? SwitchButton.ON : SwitchButton.OFF;
        UIHelper.renderCenteredScrollingText(gui, value, toggleX + 1, toggleY, CUSTOM_TOGGLE_WIDTH - 2, VOLUME_TOGGLE_HEIGHT, visible ? 0x55FF55 : 0xFF7777);
    }

    private static void renderCustomToggle(GuiGraphics gui, Font font, PopupInput input, int rowY) {
        int toggleX = customToggleX();
        int toggleY = rowY + 2;
        boolean hovered = isMouseOverCustomToggle(toPanelLocalX(lastMouseX), toPanelLocalY(lastMouseY), rowY);
        UIHelper.blitSliced(gui, toggleX, toggleY, CUSTOM_TOGGLE_WIDTH, VOLUME_TOGGLE_HEIGHT, hovered ? 32f : 16f, 0f, 16, 16, 48, 32, BUTTON);

        boolean toggled = Boolean.TRUE.equals(input.getValue());
        Component value = toggled ? SwitchButton.ON : SwitchButton.OFF;
        UIHelper.renderCenteredScrollingText(gui, value, toggleX + 1, toggleY, CUSTOM_TOGGLE_WIDTH - 2, VOLUME_TOGGLE_HEIGHT, toggled ? 0x55FF55 : 0xFF7777);
    }

    private static void renderCustomSlider(GuiGraphics gui, Font font, PopupInput input, int rowY) {
        int sliderX = customSliderX();
        int sliderY = rowY + 4;
        boolean sliderActive = customDraggingInput == input || isMouseOverCustomSlider(toPanelLocalX(lastMouseX), toPanelLocalY(lastMouseY), rowY);
        float progress = (float) input.getProgress();

        UIHelper.enableBlend();
        gui.blit(RenderPipelines.GUI_TEXTURED, SliderWidget.SLIDER_TEXTURE, sliderX, sliderY + 3, sliderActive ? 10f : 0f, 0f, CUSTOM_SLIDER_WIDTH, 5, 5, 5, 33, 16);
        gui.blit(RenderPipelines.GUI_TEXTURED, SliderWidget.SLIDER_TEXTURE, sliderX + Math.round(progress * (CUSTOM_SLIDER_WIDTH - 11)), sliderY, sliderActive ? 22f : 11f, 5f, 11, 11, 33, 16);

        Component value = Component.literal(input.getDisplayValue());
        int valueX = sliderX + CUSTOM_SLIDER_WIDTH / 2 - font.width(value) / 2;
        gui.drawString(font, value, valueX, rowY + 5, UIHelper.adjustColor(0xFFFFFF));
    }

    private static void renderCustomButton(GuiGraphics gui, PopupInput input, int rowY) {
        int buttonX = customButtonX();
        int buttonY = rowY + 2;
        boolean hovered = isMouseOverCustomButton(toPanelLocalX(lastMouseX), toPanelLocalY(lastMouseY), rowY);
        UIHelper.blitSliced(gui, buttonX, buttonY, CUSTOM_BUTTON_WIDTH, VOLUME_TOGGLE_HEIGHT, hovered ? 32f : 16f, 0f, 16, 16, 48, 32, BUTTON);
        UIHelper.renderCenteredScrollingText(gui, FiguraText.of("popup_menu.run"), buttonX + 1, buttonY, CUSTOM_BUTTON_WIDTH - 2, VOLUME_TOGGLE_HEIGHT, 0xFFFFFF);
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
        if (panel == PanelKind.VOLUME && id != null)
            PermissionManager.saveToDisk();
    }

    private static boolean isMouseOverIcon(double mouseX, double mouseY, int icon) {
        if (!popupProjected)
            return false;

        return UIHelper.isMouseOver((LENGTH * ICON_SIZE) / -2 + (ICON_SIZE * icon), -24, ICON_SIZE, ICON_SIZE, toLocalX(mouseX), toLocalY(mouseY));
    }

    private static boolean isMouseOverPanel(double localX, double localY) {
        return switch (panel) {
            case PERMISSIONS -> UIHelper.isMouseOver(-CONTROL_PANEL_WIDTH / 2, 6, CONTROL_PANEL_WIDTH, 20 + Permissions.Category.values().length * CUSTOM_ROW_HEIGHT + 4, localX, localY);
            case CONTROLS -> UIHelper.isMouseOver(-CONTROL_PANEL_WIDTH / 2, 6, CONTROL_PANEL_WIDTH, getControlsPanelHeight(), localX, localY);
            case VOLUME -> UIHelper.isMouseOver(-VOLUME_PANEL_WIDTH / 2, 6, VOLUME_PANEL_WIDTH, VOLUME_PANEL_HEIGHT, localX, localY);
            default -> false;
        };
    }

    private static boolean handlePermissionPanelClick(double localX, double localY) {
        for (Permissions.Category category : Permissions.Category.values()) {
            if (isMouseOverPermissionRow(localX, localY, category)) {
                setPermissionCategory(category);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }
        }

        if (!isMouseOverPanel(localX, localY)) {
            close();
            return true;
        }

        return true;
    }

    private static void cyclePermissionCategory(double d) {
        PermissionPack pack = PermissionManager.get(id);
        int direction = d > 0 ? 1 : -1;
        Permissions.Category category = Permissions.Category.indexOf(pack.getCategory().index + direction);
        if (category != null)
            setPermissionCategory(category);
    }

    private static void setPermissionCategory(Permissions.Category category) {
        PermissionPack pack = PermissionManager.get(id);
        if (pack.getCategory() == category)
            return;

        PermissionPack.CategoryPermissionPack categoryPack = PermissionManager.CATEGORIES.get(category);
        if (categoryPack == null)
            return;

        pack.setCategory(categoryPack);
        PermissionManager.saveToDisk();
        FiguraToast.sendToast(FiguraText.of("toast.permission_change"), pack.getCategoryName());
    }

    private static boolean isMouseOverPermissionRow(double localX, double localY, Permissions.Category category) {
        return UIHelper.isMouseOver(-CONTROL_PANEL_WIDTH / 2 + 4, 26 + category.index * CUSTOM_ROW_HEIGHT, CONTROL_PANEL_WIDTH - 8, CUSTOM_ROW_HEIGHT - 1, localX, localY);
    }

    private static boolean handleControlsPanelClick(double localX, double localY) {
        int visibleIndex = getCustomVisibleIndex(localY);
        List<ViewerVisibilityManager.Setting> visibilityControls = getVisibilityControls();
        int controlIndex = customScrollIndex + visibleIndex;
        if (visibleIndex >= 0 && visibleIndex < CUSTOM_VISIBLE_ROWS && controlIndex >= 0 && controlIndex < visibilityControls.size()) {
            ViewerVisibilityManager.toggle(id, visibilityControls.get(controlIndex));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
            return true;
        }

        PopupInput input = getCustomInputAt(localY);
        if (input != null) {
            int rowY = customRowY(getCustomVisibleIndex(localY));
            if (input.getInputType() == PopupInput.Type.TOGGLE) {
                input.setUserBoolean(!Boolean.TRUE.equals(input.getValue()));
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }

            if (input.getInputType() == PopupInput.Type.BUTTON && isMouseOverCustomButton(localX, localY, rowY)) {
                input.press();
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }

            if (isMouseOverCustomSlider(localX, localY, rowY)) {
                customDraggingInput = input;
                setCustomSliderFromLocalX(input, localX, false);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }
        }

        if (!isMouseOverPanel(localX, localY)) {
            close();
            return true;
        }

        return true;
    }

    private static void scrollControlsPanel(double d) {
        PopupInput input = getCustomInputAt(toPanelLocalY(lastMouseY));
        if (input != null && input.getInputType() == PopupInput.Type.SLIDER) {
            input.nudge(d);
            return;
        }

        List<ViewerVisibilityManager.Setting> visibilityControls = getVisibilityControls();
        List<PopupInput> controls = getPopupControls();
        int maxScroll = Math.max(getControlCount(visibilityControls, controls) - CUSTOM_VISIBLE_ROWS, 0);
        customScrollIndex = Mth.clamp(customScrollIndex + (d > 0 ? -1 : 1), 0, maxScroll);
    }

    private static List<ViewerVisibilityManager.Setting> getVisibilityControls() {
        if (directControlsOnly)
            return List.of();
        return id == null ? List.of() : VISIBILITY_CONTROLS;
    }

    private static List<PopupInput> getPopupControls() {
        Avatar avatar = AvatarManager.getAvatarForPlayer(id);
        PopupInput.Target target = getPopupControlTarget();
        return PopupAPI.getInputs(avatar, target, target == PopupInput.Target.HEAD ? targetHeadName : null);
    }

    private static PopupInput.Target getPopupControlTarget() {
        if (forcedControlTarget != null)
            return forcedControlTarget;
        if (skullTarget != null || profileTarget)
            return PopupInput.Target.PLAYER;
        if (entity instanceof Player)
            return PopupInput.Target.PLAYER;
        if (entity != null)
            return PopupInput.Target.ENTITY;
        return PopupInput.Target.PLAYER;
    }

    private static int getControlsPanelHeight() {
        int controls = getControlCount(getVisibilityControls(), getPopupControls());
        int visibleRows = Math.min(controls, CUSTOM_VISIBLE_ROWS);
        return controls == 0 ? 42 : 22 + visibleRows * CUSTOM_ROW_HEIGHT + (controls > CUSTOM_VISIBLE_ROWS ? 10 : 4);
    }

    private static int getControlCount(List<ViewerVisibilityManager.Setting> visibilityControls, List<PopupInput> inputs) {
        return visibilityControls.size() + inputs.size();
    }

    private static void clampControlScroll(int controls) {
        int maxScroll = Math.max(controls - CUSTOM_VISIBLE_ROWS, 0);
        customScrollIndex = Mth.clamp(customScrollIndex, 0, maxScroll);
    }

    private static PopupInput getCustomInputAt(double localY) {
        int visibleIndex = getCustomVisibleIndex(localY);
        if (visibleIndex < 0 || visibleIndex >= CUSTOM_VISIBLE_ROWS)
            return null;

        int visibilityControls = getVisibilityControls().size();
        List<PopupInput> controls = getPopupControls();
        int index = customScrollIndex + visibleIndex - visibilityControls;
        return index >= 0 && index < controls.size() ? controls.get(index) : null;
    }

    private static int getCustomVisibleIndex(double localY) {
        int y = (int) Math.floor((localY - customRowsY()) / CUSTOM_ROW_HEIGHT);
        return y < 0 ? -1 : y;
    }

    private static boolean isMouseOverCustomRow(double localX, double localY, int visibleIndex) {
        return UIHelper.isMouseOver(-CONTROL_PANEL_WIDTH / 2 + 4, customRowY(visibleIndex), CONTROL_PANEL_WIDTH - 8, CUSTOM_ROW_HEIGHT - 1, localX, localY);
    }

    private static boolean isMouseOverCustomToggle(double localX, double localY, int rowY) {
        return UIHelper.isMouseOver(customToggleX(), rowY + 2, CUSTOM_TOGGLE_WIDTH, VOLUME_TOGGLE_HEIGHT, localX, localY);
    }

    private static boolean isMouseOverCustomSlider(double localX, double localY, int rowY) {
        return UIHelper.isMouseOver(customSliderX(), rowY + 4, CUSTOM_SLIDER_WIDTH, VOLUME_SLIDER_HEIGHT, localX, localY);
    }

    private static boolean isMouseOverCustomButton(double localX, double localY, int rowY) {
        return UIHelper.isMouseOver(customButtonX(), rowY + 2, CUSTOM_BUTTON_WIDTH, VOLUME_TOGGLE_HEIGHT, localX, localY);
    }

    private static void setCustomSliderFromLocalX(PopupInput input, double localX, boolean save) {
        double min = input.getMin() == null ? 0d : input.getMin();
        double max = input.getMax() == null ? 1d : input.getMax();
        double progress = (localX - customSliderX()) / (double) (CUSTOM_SLIDER_WIDTH - 11);
        input.setUserNumber(min + Mth.clamp(progress, 0d, 1d) * (max - min), save);
    }

    private static int customRowsY() {
        return 28;
    }

    private static int customRowY(int visibleIndex) {
        return customRowsY() + visibleIndex * CUSTOM_ROW_HEIGHT;
    }

    private static int customToggleX() {
        return CONTROL_PANEL_WIDTH / 2 - CUSTOM_TOGGLE_WIDTH - 6;
    }

    private static int customSliderX() {
        return CONTROL_PANEL_WIDTH / 2 - CUSTOM_SLIDER_WIDTH - 6;
    }

    private static int customButtonX() {
        return CONTROL_PANEL_WIDTH / 2 - CUSTOM_BUTTON_WIDTH - 6;
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

    private static boolean isHeadCursorMode() {
        return directControlsOnly && Configs.HEAD_POPUP_CURSOR.value == 1;
    }

    private static double getInteractionMouseX(double mouseX) {
        return isHeadCursorMode() ? Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2d : mouseX;
    }

    private static double getInteractionMouseY(double mouseY) {
        return isHeadCursorMode() ? Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2d : mouseY;
    }

    private static double toPanelLocalX(double mouseX) {
        return popupScale == 0d ? 0d : (mouseX - popupX) / (popupScale * 0.5d);
    }

    private static double toPanelLocalY(double mouseY) {
        return popupScale == 0d ? 0d : (mouseY - popupY) / (popupScale * 0.5d);
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
