package org.figuramc.figura.avatar;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.local.LocalAvatarLoader;
import org.figuramc.figura.backend2.NetworkStuff;
import org.figuramc.figura.ducks.FiguraEntityRenderStateExtension;
import org.figuramc.figura.gui.FiguraToast;
import org.figuramc.figura.gui.widgets.lists.AvatarList;
import org.figuramc.figura.lua.api.particle.ParticleAPI;
import org.figuramc.figura.lua.api.sound.SoundAPI;
import org.figuramc.figura.utils.EntityUtils;
import org.figuramc.figura.utils.FiguraClientCommandSource;
import org.figuramc.figura.utils.FiguraResourceListener;
import org.figuramc.figura.utils.FiguraText;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Manages all the avatars that are currently loaded in memory, and also
 * handles getting the avatars of entities. If an entity does not have a loaded avatar,
 * the AvatarManager will fetch the avatar and cache it.
 */
public class AvatarManager {

    private static final Map<UUID, UserData> LOADED_USERS = new ConcurrentHashMap<>();
    private static final Set<UUID> FETCHED_USERS = new HashSet<>();

    private static final Int2ObjectMap<Avatar> LOADED_CEM = new Int2ObjectOpenHashMap<>();
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final long PROFILE_ID_RETRY_DELAY_MS = 30_000L;
    private static final Map<String, UUID> PROFILE_ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED_PROFILE_ID_LOOKUPS = new ConcurrentHashMap<>();
    private static final Set<String> PENDING_PROFILE_ID_LOOKUPS = ConcurrentHashMap.newKeySet();

    public static final FiguraResourceListener RESOURCE_RELOAD_EVENT = FiguraResourceListener.createResourceListener("resource_reload_event", manager -> executeAll("resourceReloadEvent", Avatar::resourceReloadEvent));

    public static boolean localUploaded = true; // init as true :3
    public static boolean panic = false;

    // Added to reduce look up for entities + fixes trident and arrow that no longer exist in world not being rendered while picked up
    public static Int2ObjectMap<Entity> ENTITY_CACHE = new Int2ObjectOpenHashMap<>();

    // -- panic mode -- // 

    public static void togglePanic() {
        AvatarManager.panic = !AvatarManager.panic;
        FiguraToast.sendToast(FiguraText.of(AvatarManager.panic ? "toast.panic_enabled" : "toast.panic_disabled"), FiguraToast.ToastType.WARNING);
        SoundAPI.getSoundEngine().figura$stopAllSounds();
        ParticleAPI.getParticleEngine().figura$clearParticles(null);
    }

    // -- avatar events -- // 

    public static void tickLoadedAvatars() {
        if (panic)
            return;

        Minecraft client = Minecraft.getInstance();

        // tick the avatars
        for (UserData user : LOADED_USERS.values()) {
            Avatar avatar = user.getMainAvatar();
            if (avatar != null) {
                FiguraMod.pushProfiler(avatar);
                avatar.tick();
                FiguraMod.popProfiler();
            }
        }

        // CEM
        if (LOADED_CEM.isEmpty())
            return;

        if (client.level == null) {
            clearCEMAvatars();
            ENTITY_CACHE.clear();
            return;
        }

        // unload entities
        IntSet toBeRemoved = new IntOpenHashSet();

        for (int entityId : LOADED_CEM.keySet()) {
            Entity entity = client.level.getEntity(entityId);
            if (entity == null || entity.isRemoved()) {
                toBeRemoved.add(entityId);
                ENTITY_CACHE.remove(entityId);
            }
        }

        for (int entity : toBeRemoved) {
            Avatar removed = LOADED_CEM.remove(entity);
            if (removed != null)
                removed.clean();
        }

        // tick entities
        for (Avatar avatar : LOADED_CEM.values()) {
            if (avatar != null) {
                FiguraMod.pushProfiler(avatar);
                avatar.tick();
                FiguraMod.popProfiler();
            }
        }
    }

    public static void executeAll(String src, Consumer<Avatar> consumer) {
        if (panic) return;

        FiguraMod.pushProfiler(FiguraMod.MOD_ID);
        FiguraMod.pushProfiler(src);

        for (UserData user : LOADED_USERS.values()) {
            Avatar avatar = user.getMainAvatar();
            if (avatar != null) {
                FiguraMod.pushProfiler(avatar);
                consumer.accept(avatar);
                FiguraMod.popProfiler();
            }
        }

        for (Avatar avatar : LOADED_CEM.values()) {
            if (avatar != null) {
                FiguraMod.pushProfiler(avatar);
                consumer.accept(avatar);
                FiguraMod.popProfiler();
            }
        }

        FiguraMod.popProfiler(2);
    }

    // -- avatar getters -- // 

    // player will also attempt to load from network, if possible
    public static Avatar getAvatarForPlayer(UUID player) {
        if (player == null || panic || Minecraft.getInstance().level == null)
            return null;

        fetchBackend(player);

        UserData user = LOADED_USERS.get(player);
        return user == null ? null : user.getMainAvatar();
    }

    public static Avatar getAvatarForProfile(ResolvableProfile profile) {
        UUID id = getIdForProfile(profile);
        if (id == null)
            return null;

        return getAvatarForPlayer(id);
    }

    public static Avatar getAvatarForItem(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return null;

        Avatar avatar = getAvatarForProfile(stack.get(DataComponents.PROFILE));
        if (avatar != null)
            return avatar;

        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        return customName == null ? null : getAvatarForName(customName.getString());
    }

    public static UUID getIdForProfile(ResolvableProfile profile) {
        if (profile == null || profile.partialProfile() == null)
            return null;

        UUID id = profile.partialProfile().id();
        if (id != null && id.version() == 4)
            return id;

        String name = profile.partialProfile().name();
        if ((name == null || name.isBlank()) && profile.name().isPresent())
            name = profile.name().get();

        return getIdForName(name);
    }

    public static UUID getIdForName(String name) {
        if (name == null || name.isBlank())
            return null;

        if (Minecraft.getInstance().player != null && name.equalsIgnoreCase(Minecraft.getInstance().player.getName().getString()))
            return Minecraft.getInstance().player.getUUID();

        for (Map.Entry<String, UUID> entry : EntityUtils.getPlayerList().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                cacheProfileId(name, entry.getValue());
                return entry.getValue();
            }
        }

        String cacheKey = profileCacheKey(name);
        if (cacheKey == null)
            return null;

        UUID cachedId = PROFILE_ID_CACHE.get(cacheKey);
        if (cachedId != null)
            return cachedId;

        requestProfileIdLookup(name, cacheKey);
        return null;
    }

    public static Avatar getAvatarForName(String name) {
        UUID id = getIdForName(name);
        return id == null ? null : getAvatarForPlayer(id);
    }

    public static Entity getCachedEntity(int entityId) {
        if (Minecraft.getInstance().level == null) {
            ENTITY_CACHE.remove(entityId);
            return null;
        }

        Entity cached = ENTITY_CACHE.get(entityId);
        if (cached != null && !cached.isRemoved())
            return cached;

        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
        if (entity == null || entity.isRemoved()) {
            ENTITY_CACHE.remove(entityId);
            return null;
        }

        ENTITY_CACHE.put(entityId, entity);
        return entity;
    }

    private static Avatar getAvatarForEntity(Entity entity) {
        // get loaded
        Avatar loaded = LOADED_CEM.get(entity.getId());
        if (loaded != null)
            return loaded;

        // new avatar
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        CompoundTag nbt = LocalAvatarLoader.CEM_AVATARS.get(type);
        return nbt == null ? null : loadEntityAvatar(entity, nbt);
    }

    public static Avatar getAvatar(EntityRenderState state) {
        if (panic || Minecraft.getInstance().level == null || state == null) return null;

        if (state instanceof AvatarRenderState playerRenderState) {
            return getAvatar(Minecraft.getInstance().level.getEntity(playerRenderState.id));
        }
        Integer id = ((FiguraEntityRenderStateExtension)state).figura$getEntityId();
        Entity entity = id != null ? Minecraft.getInstance().level.getEntity(id) : null;
        return getAvatar(entity);
    }

    public static Entity getEntity(EntityRenderState state) {
        if (Minecraft.getInstance().level == null || state == null) return null;

        if (state instanceof AvatarRenderState playerRenderState) {
            return Minecraft.getInstance().level.getEntity(playerRenderState.id);
        }
        Integer id = ((FiguraEntityRenderStateExtension)state).figura$getEntityId();
        return id != null ? Minecraft.getInstance().level.getEntity(id) : null;
    }

    // tries to get data from an entity
    public static Avatar getAvatar(Entity entity) {
        if (panic || Minecraft.getInstance().level == null || entity == null)
            return null;

        UUID uuid = entity.getUUID();

        // load from player (fetch backend) if is a player
        if (entity instanceof Player){
            Avatar avatar = getAvatarForPlayer(uuid);
            if (avatar != null)
                return avatar;
        }

        // otherwise check for CEM
        return getAvatarForEntity(entity);
    }

    // get a loaded avatar without fetching backend or creating a new one
    public static Avatar getLoadedAvatar(UUID owner) {
        if (panic || Minecraft.getInstance().level == null)
            return null;

        UserData user = LOADED_USERS.get(owner);
        return user == null ? null : user.getMainAvatar();
    }

    // get all main loaded avatars
    public static List<Avatar> getLoadedAvatars() {
        List<Avatar> list = new ArrayList<>();
        for (UserData user : LOADED_USERS.values()) {
            Avatar avatar = user.getMainAvatar();
            if (avatar != null && avatar.nbt != null)
                list.add(avatar);
        }
        return list;
    }

    // -- avatar management -- // 

    // removes an loaded avatar
    public static void clearAvatars(UUID id) {
        FETCHED_USERS.remove(id);

        UserData user = LOADED_USERS.remove(id);
        if (user != null) user.clear();

        NetworkStuff.clear(id);
        FiguraMod.debug("Cleared avatars of " + id);
    }

    public static void clearCEMAvatars() {
        for (Avatar avatar : LOADED_CEM.values())
            avatar.clean();
        LOADED_CEM.clear();
        ENTITY_CACHE.clear();
    }

    // clears ALL loaded avatars, including local
    public static void clearAllAvatars() {
        for (UUID id : LOADED_USERS.keySet())
            clearAvatars(id);

        LOADED_USERS.clear();
        FETCHED_USERS.clear();
        ENTITY_CACHE.clear();
        clearCEMAvatars();

        localUploaded = true;
        AvatarList.selectedEntry = null;
        LocalAvatarLoader.loadAvatar(null, null);
        FiguraMod.LOGGER.info("Cleared all avatars");
    }

    // reloads an avatar
    public static void reloadAvatar(UUID id) {
        if (!localUploaded && FiguraMod.isLocal(id))
            loadLocalAvatar(LocalAvatarLoader.getLastLoadedPath());
        else
            clearAvatars(id);
    }

    // load the local player avatar
    public static void loadLocalAvatar(Path path) {
        UUID id = FiguraMod.getLocalPlayerUUID();

        // clear
        clearAvatars(id);

        // load
        UserData user = LOADED_USERS.computeIfAbsent(id, UserData::new);
        LocalAvatarLoader.loadAvatar(path, user);

        // mark as not uploaded
        localUploaded = false;
    }

    // load CEM avatar
    public static Avatar loadEntityAvatar(Entity entity, CompoundTag nbt) {
        Avatar targetAvatar = new Avatar(entity);
        targetAvatar.load(nbt);
        LOADED_CEM.put(entity.getId(), targetAvatar);
        AvatarManager.ENTITY_CACHE.putIfAbsent(entity.getId(), entity);
        return targetAvatar;
    }

    // load CEM avatar
    public static Avatar loadEntityAvatar(EntityRenderState entity, CompoundTag nbt) {
        Integer id = entity instanceof AvatarRenderState playerRenderState ? playerRenderState.id : ((FiguraEntityRenderStateExtension)entity).figura$getEntityId();
        if (id != null) {
            Entity cachedEntity = getCachedEntity(id.intValue());
            if (cachedEntity == null)
                return null;

            Avatar targetAvatar = new Avatar(cachedEntity);
            targetAvatar.load(nbt);
            LOADED_CEM.put(id.intValue(), targetAvatar);
            AvatarManager.ENTITY_CACHE.putIfAbsent(id.intValue(), cachedEntity);
            return targetAvatar;
        }

        return null;
    }

    // set an user's avatar
    public static void setAvatar(UUID id, CompoundTag nbt) {
        try {
            clearAvatars(id);
            UserData user = LOADED_USERS.computeIfAbsent(id, UserData::new);
            user.loadAvatar(nbt);
        } catch (Exception e) {
            FiguraMod.LOGGER.error("Failed to set avatar for " + id, e);
        }
    }

    // get avatar from the backend
    private static void fetchBackend(UUID id) {
        if (FETCHED_USERS.contains(id))
            return;

        FETCHED_USERS.add(id);

        if (EntityUtils.checkInvalidPlayer(id)) {
            FiguraMod.debug("Voiding userdata for " + id);
            return;
        }

        UserData user = LOADED_USERS.computeIfAbsent(id, UserData::new);

        FiguraMod.debug("Getting userdata for " + id);
        NetworkStuff.getUser(user);
    }

    private static String profileCacheKey(String name) {
        if (name == null || name.isBlank())
            return null;

        String trimmed = name.trim();
        return PLAYER_NAME_PATTERN.matcher(trimmed).matches() ? trimmed.toLowerCase(Locale.ROOT) : null;
    }

    private static void cacheProfileId(String name, UUID id) {
        String cacheKey = profileCacheKey(name);
        if (cacheKey != null && id != null && id.version() == 4) {
            PROFILE_ID_CACHE.put(cacheKey, id);
            FAILED_PROFILE_ID_LOOKUPS.remove(cacheKey);
        }
    }

    private static void requestProfileIdLookup(String name, String cacheKey) {
        long now = System.currentTimeMillis();
        Long failedAt = FAILED_PROFILE_ID_LOOKUPS.get(cacheKey);
        if (failedAt != null && now - failedAt < PROFILE_ID_RETRY_DELAY_MS)
            return;

        if (!PENDING_PROFILE_ID_LOOKUPS.add(cacheKey))
            return;

        String trimmedName = name.trim();
        Minecraft client = Minecraft.getInstance();
        ResolvableProfile.createUnresolved(trimmedName)
                .resolveProfile(client.services().profileResolver())
                .whenComplete((profile, throwable) -> {
                    PENDING_PROFILE_ID_LOOKUPS.remove(cacheKey);

                    UUID id = profile == null ? null : profile.id();
                    if (throwable != null || id == null || id.version() != 4) {
                        FAILED_PROFILE_ID_LOOKUPS.put(cacheKey, System.currentTimeMillis());
                        if (throwable != null)
                            FiguraMod.debug("Failed to resolve profile id for {}", trimmedName, throwable);
                        else
                            FiguraMod.debug("Failed to resolve profile id for {}", trimmedName);
                        return;
                    }

                    PROFILE_ID_CACHE.put(cacheKey, id);
                    FAILED_PROFILE_ID_LOOKUPS.remove(cacheKey);
                    client.execute(() -> getAvatarForPlayer(id));
                    FiguraMod.debug("Resolved profile id for {} as {}", trimmedName, id);
                });
    }

    // -- badges -- // 

    public static Pair<BitSet, BitSet> getBadges(UUID id) {
        UserData user = LOADED_USERS.get(id);
        if (user == null)
            return null;

        Pair<BitSet, BitSet> badges = user.getBadges();
        if (badges != null)
            return badges;

        badges = Badges.emptyBadges();
        user.loadBadges(badges);
        return badges;
    }

    // -- command -- // 

    public static LiteralArgumentBuilder<FiguraClientCommandSource> getCommand() {
        // root
        LiteralArgumentBuilder<FiguraClientCommandSource> root = LiteralArgumentBuilder.literal("set_avatar");

        // source
        RequiredArgumentBuilder<FiguraClientCommandSource, String> target = RequiredArgumentBuilder.argument("target", StringArgumentType.word());

        // target
        RequiredArgumentBuilder<FiguraClientCommandSource, String> source = RequiredArgumentBuilder.argument("source", StringArgumentType.word());
        source.executes(context -> {
            String s = StringArgumentType.getString(context, "source");
            String t = StringArgumentType.getString(context, "target");

            UUID sourceUUID, targetUUID;
            try {
                sourceUUID = UUID.fromString(s);
                targetUUID = UUID.fromString(t);
            } catch (Exception e) {
                context.getSource().figura$sendError(Component.literal("Failed to parse uuids"));
                return 0;
            }

            UserData user = LOADED_USERS.get(sourceUUID);
            Avatar avatar = user == null ? null : user.getMainAvatar();
            if (avatar == null || avatar.nbt == null) {
                context.getSource().figura$sendError(Component.literal("No source Avatar found"));
                return 0;
            }

            if (LOADED_USERS.get(targetUUID) != null) {
                setAvatar(targetUUID, avatar.nbt);
                if (FiguraMod.isLocal(targetUUID))
                    localUploaded = true;
                context.getSource().figura$sendFeedback(Component.literal("Set avatar for " + t));
                return 1;
            }

            Entity targetEntity = EntityUtils.getEntityByUUID(targetUUID);
            if (targetEntity == null) {
                context.getSource().figura$sendError(Component.literal("Target entity not found"));
                return 0;
            }

            loadEntityAvatar(targetEntity, avatar.nbt);
            return 1;
        });
        target.then(source);

        // build root
        root.then(target);
        return root;
    }
}
