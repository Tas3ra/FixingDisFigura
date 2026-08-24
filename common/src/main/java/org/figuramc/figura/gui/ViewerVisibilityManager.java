package org.figuramc.figura.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.IOUtils;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ViewerVisibilityManager {

    private static final String CACHE_NAME = "viewer_visibility";
    private static final String PLAYERS_KEY = "players";
    private static final Map<UUID, EnumMap<Setting, Boolean>> OVERRIDES = new HashMap<>();
    private static boolean loaded;

    private ViewerVisibilityManager() {
    }

    public enum Setting {
        AVATAR("avatar", "popup_menu.visibility_avatar"),
        NAMEPLATE("nameplate", "popup_menu.visibility_nameplate"),
        CUSTOM_SKULLS("custom_skulls", "popup_menu.visibility_custom_skulls");

        public final String key;
        private final String translationKey;

        Setting(String key, String translationKey) {
            this.key = key;
            this.translationKey = translationKey;
        }

        public Component title() {
            return FiguraText.of(translationKey);
        }
    }

    public static boolean isAvatarVisible(UUID id) {
        return isVisible(id, Setting.AVATAR);
    }

    public static boolean isNameplateVisible(UUID id) {
        return isVisible(id, Setting.NAMEPLATE);
    }

    public static boolean areCustomSkullsVisible(UUID id) {
        return isVisible(id, Setting.CUSTOM_SKULLS);
    }

    public static boolean isVisible(UUID id, Setting setting) {
        ensureLoaded();
        if (id == null || setting == null)
            return true;

        EnumMap<Setting, Boolean> map = OVERRIDES.get(id);
        if (map == null)
            return true;

        return map.getOrDefault(setting, true);
    }

    public static boolean toggle(UUID id, Setting setting) {
        boolean next = !isVisible(id, setting);
        setVisible(id, setting, next);
        return next;
    }

    public static void setVisible(UUID id, Setting setting, boolean visible) {
        ensureLoaded();
        if (id == null || setting == null)
            return;

        EnumMap<Setting, Boolean> map = OVERRIDES.computeIfAbsent(id, ignored -> new EnumMap<>(Setting.class));
        if (visible)
            map.remove(setting);
        else
            map.put(setting, false);

        if (map.isEmpty())
            OVERRIDES.remove(id);

        save();
    }

    private static void ensureLoaded() {
        if (loaded)
            return;

        IOUtils.readCacheFile(CACHE_NAME, nbt -> {
            ListTag players = nbt.getListOrEmpty(PLAYERS_KEY);
            for (Tag tag : players) {
                if (!(tag instanceof CompoundTag compound))
                    continue;

                String uuidString = compound.getStringOr("id", "");
                if (uuidString.isBlank())
                    continue;

                try {
                    UUID id = UUID.fromString(uuidString);
                    EnumMap<Setting, Boolean> map = new EnumMap<>(Setting.class);
                    for (Setting setting : Setting.values()) {
                        if (compound.contains(setting.key) && !compound.getBooleanOr(setting.key, true))
                            map.put(setting, false);
                    }
                    if (!map.isEmpty())
                        OVERRIDES.put(id, map);
                } catch (IllegalArgumentException ignored) {
                }
            }
        });

        loaded = true;
    }

    private static void save() {
        IOUtils.saveCacheFile(CACHE_NAME, nbt -> {
            ListTag players = new ListTag();
            for (Map.Entry<UUID, EnumMap<Setting, Boolean>> entry : OVERRIDES.entrySet()) {
                CompoundTag player = new CompoundTag();
                player.putString("id", entry.getKey().toString());
                for (Map.Entry<Setting, Boolean> override : entry.getValue().entrySet())
                    player.putBoolean(override.getKey().key, override.getValue());
                players.add(player);
            }
            nbt.put(PLAYERS_KEY, players);
        });
    }
}
