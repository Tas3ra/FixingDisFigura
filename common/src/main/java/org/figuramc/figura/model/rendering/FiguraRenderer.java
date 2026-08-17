package org.figuramc.figura.model.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.model.FiguraModelPart;
import org.figuramc.figura.model.ParentType;
import org.figuramc.figura.model.VanillaModelData;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.model.rendering.texture.FiguraTextureSet;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Mainly exists as an abstract superclass for VAO-based and
 * immediate mode avatar renderers. (VAO-based don't exist yet)
 */
public abstract class FiguraRenderer {

    protected final Avatar avatar;
    public FiguraModelPart root;

    protected final Map<ParentType, List<FiguraModelPart>> separatedParts = new ConcurrentHashMap<>();

    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    protected volatile boolean isRendering, dirty;

    // -- rendering data -- // 

    // entity
    public Entity entity;
    public float yaw, tickDelta;
    public int light;
    public int overlay;
    public float alpha;
    public boolean translucent, glowing;
    public FiguraMat4 posMat = FiguraMat4.of();
    public FiguraMat3 normalMat = FiguraMat3.of();

    // matrices
    public MultiBufferSource bufferSource;
    public VanillaModelData vanillaModelData = new VanillaModelData();

    public PartFilterScheme currentFilterScheme;
    public final Map<ParentType, ConcurrentLinkedQueue<PivotCustomization>> pivotCustomizations = new EnumMap<>(ParentType.class);
    protected final List<FiguraTextureSet> textureSets = new ArrayList<>();
    public final ConcurrentMap<String, FiguraTexture> textures = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, FiguraTexture> customTextures = new ConcurrentHashMap<>();
    protected static int shouldRenderPivots;
    public boolean allowMatrixUpdate = false;
    public boolean allowHiddenTransforms = true;
    public boolean interceptRendersIntoFigura = true;
    public boolean allowPivotParts = true;
    public boolean updateLight = false;
    public boolean doIrisEmissiveFix = false;
    public boolean offsetRenderLayers = false;
    public boolean ignoreVanillaVisibility = false;
    public FiguraModelPart itemToRender;

    public FiguraRenderer(Avatar avatar) {
        this.avatar = avatar;

        for (ParentType parentType : ParentType.values()) {
            if (parentType.isPivot)
                pivotCustomizations.put(parentType, new ConcurrentLinkedQueue<>());
        }

        // textures

        CompoundTag nbt = avatar.nbt.getCompoundOrEmpty("textures");
        CompoundTag src = nbt.getCompoundOrEmpty("src");

        // src files
        for (String key : src.keySet()) {
            byte[] bytes = src.getByteArray(key).get();
            if (bytes.length > 0) {
                textures.put(key, new FiguraTexture(avatar, key, bytes));
            } else {
                ListTag size = src.getListOrEmpty(key);
                textures.put(key, new FiguraTexture(avatar, key, size.getIntOr(0, 0), size.getIntOr(1, 0)));
            }
        }

        // data files
        ListTag texturesList = nbt.getListOrEmpty("data");
        for (Tag t : texturesList) {
            CompoundTag tag = (CompoundTag) t;
            textureSets.add(new FiguraTextureSet(
                    getTextureName(tag),
                    textures.get(tag.getStringOr("d", "")),
                    textures.get(tag.getStringOr("e", "")),
                    textures.get(tag.getStringOr("s", "")),
                    textures.get(tag.getStringOr("n", ""))
            ));
        }

        avatar.hasTexture = !texturesList.isEmpty();
    }

    private String getTextureName(CompoundTag tag) {
        String s = tag.getStringOr("d", "");
        if (!s.isEmpty()) return s;
        s = tag.getStringOr("e", "");
        if (!s.isEmpty()) return s.substring(0, s.length() - 2);
        s = tag.getStringOr("s", "");
        if (!s.isEmpty()) return s.substring(0, s.length() - 2);
        s = tag.getStringOr("n", "");
        if (!s.isEmpty()) return s.substring(0, s.length() - 2);
        return "";
    }

    public FiguraTexture getTexture(String name) {
        FiguraTexture texture = customTextures.get(name);
        if (texture != null)
            return texture;

        for (Map.Entry<String, FiguraTexture> entry : textures.entrySet()) {
            if (entry.getKey().equals(name))
                return entry.getValue();
        }

        return null;
    }

    public abstract int render();
    public abstract int getComplexity();
    public abstract int renderSpecialParts();
    public abstract void updateMatrices();

    public ConcurrentLinkedQueue<PivotCustomization> getPivotCustomizationQueue(ParentType parentType) {
        return parentType == null ? null : pivotCustomizations.get(parentType);
    }

    public void clearPivotCustomizations() {
        pivotCustomizations.values().forEach(Queue::clear);
    }

    public boolean hasRoot() {
        lifecycleLock.readLock().lock();
        try {
            return root != null;
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    public double getVisibleModelTopY(Entity entity, float tickDelta) {
        lifecycleLock.readLock().lock();
        try {
            if (root == null || entity == null)
                return Double.NaN;

            double[] top = {Double.NEGATIVE_INFINITY};
            collectVisibleModelTopY(root, true, top);

            if (!Double.isFinite(top[0]))
                return Double.NaN;

            return top[0] - entity.getPosition(tickDelta).y;
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    private void collectVisibleModelTopY(FiguraModelPart part, boolean previousSucceeded, double[] top) {
        Boolean thisPassedPredicate = PartFilterScheme.MODEL.test(part.parentType, previousSucceeded);
        if (thisPassedPredicate == null || !part.customization.visible)
            return;

        if (thisPassedPredicate) {
            for (List<Vertex> vertices : part.vertices.values()) {
                for (Vertex vertex : vertices) {
                    FiguraVec3 pos = part.savedPartToWorldMat.apply((double) vertex.x, (double) vertex.y, (double) vertex.z);
                    if (Double.isFinite(pos.y))
                        top[0] = Math.max(top[0], pos.y);
                }
            }
        }

        for (FiguraModelPart child : part.children)
            collectVisibleModelTopY(child, thisPassedPredicate, top);
    }

    protected void clean() {
        lifecycleLock.writeLock().lock();
        try {
            cleanUnlocked();
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private void cleanUnlocked() {
        Set<FiguraTexture> texturesToCloseFromRenderThread = new LinkedHashSet<>(textures.values());
        for (FiguraTextureSet set : textureSets) {
            for (FiguraTexture texture : set.textures) {
                if (texture != null)
                    texturesToCloseFromRenderThread.add(texture);
            }
        }

        List<FiguraTexture> customTexturesToClose = new ArrayList<>(customTextures.values());

        textureSets.clear();
        textures.clear();
        customTextures.clear();
        separatedParts.clear();
        clearPivotCustomizations();
        root = null;
        entity = null;
        bufferSource = null;
        itemToRender = null;
        dirty = false;

        for (FiguraTexture texture : texturesToCloseFromRenderThread)
            texture.closeFromRenderThread();
        for (FiguraTexture texture : customTexturesToClose)
            texture.closeFromRenderThread();
    }

    public void invalidate() {
        lifecycleLock.writeLock().lock();
        try {
            this.dirty = true;
            if (!this.isRendering)
                cleanUnlocked();
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    public void sortParts() {
        separatedParts.clear();
        if (root == null)
            return;
        _sortParts(root, ParentType.None);
    }

    private void _sortParts(FiguraModelPart part, ParentType activeSeparateParent) {
        if (part.parentType.isSeparate) {
            if (part.parentType != activeSeparateParent) {
                List<FiguraModelPart> list = separatedParts.computeIfAbsent(part.parentType, parentType -> new ArrayList<>());
                list.add(part);
            }
            activeSeparateParent = part.parentType;
        }

        for (FiguraModelPart child : part.children)
            _sortParts(child, activeSeparateParent);
    }

    public static class PivotCustomization {
        private final FiguraMat4 positionMatrix;
        private final FiguraMat3 normalMatrix;
        private final int modelDepth;

        public PivotCustomization(FiguraMat4 positionMatrix, FiguraMat3 normalMatrix, int modelDepth) {
            this.positionMatrix = positionMatrix;
            this.normalMatrix = normalMatrix;
            this.modelDepth = modelDepth;
        }

        public FiguraMat4 positionMatrix() {
            return positionMatrix;
        }

        public FiguraMat3 normalMatrix() {
            return normalMatrix;
        }

        public int modelDepth() {
            return modelDepth;
        }
    }

    /**
     * Returns the matrix for an entity, used to transform from entity space to world space.
     * @param e The entity to get the matrix for.
     * @return A matrix which represents the transformation from entity space to part space.
     */
    public static FiguraMat4 entityToWorldMatrix(Entity e, float delta) {
        double yaw = e instanceof LivingEntity le ? Mth.lerp(delta, le.yBodyRotO, le.yBodyRot) : e.getViewYRot(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        FiguraMat4 result = FiguraMat4.of();
        result.rotateX(180 - yaw);
        result.translate(e.getPosition(delta));
        return result;
    }

    public static double getYawOffsetRot(Entity e, float delta) {
        double yaw = e instanceof LivingEntity le ? Mth.lerp(delta, le.yBodyRotO, le.yBodyRot) : e.getViewYRot(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        return 180 - yaw;
    }

    /**
     * Gets a matrix to transform from world space to view space, based on the
     * player's camera position and orientation.
     * This is legacy and should not be used to go from world to view as of 1.20.5 it does not depend on the rotation anymore
     * @return That matrix.
     */
    public static FiguraMat4 worldToViewMatrix() {
        Minecraft client = Minecraft.getInstance();
        Camera camera = client.gameRenderer.getMainCamera();
        Quaternionf rot = new Quaternionf(camera.rotation());
        rot.x *= -1;
        rot.z *= -1;
        Matrix3f cameraMat3f = new Matrix3f().rotate(rot);
        FiguraMat4 result = FiguraMat4.of();
        FiguraMat3 cameraMat = FiguraMat3.of().set(cameraMat3f);
        result.multiply(cameraMat.augmented());
        result.scale(-1, 1, -1);
        return result;
    }

    /**
     * Gets a matrix to transform from world space to camera space, based on the
     * player's camera position
     * @return That matrix.
     */
    public static FiguraMat4 worldToCameraPosMatrix() {
        Minecraft client = Minecraft.getInstance();
        Camera camera = client.gameRenderer.getMainCamera();
        FiguraMat4 result = FiguraMat4.of();
        Vec3 cameraPos = camera.position().scale(-1);
        result.translate(cameraPos.x, cameraPos.y, cameraPos.z);
        return result;
    }

    public void setupRenderer(PartFilterScheme currentFilterScheme, MultiBufferSource bufferSource, PoseStack matrices, float tickDelta, int light, float alpha, int overlay, boolean translucent, boolean glowing) {
        this.setupRenderer(currentFilterScheme, bufferSource, tickDelta, light, alpha, overlay, translucent, glowing);
        this.setMatrices(matrices);
    }

    public void setupRenderer(PartFilterScheme currentFilterScheme, MultiBufferSource bufferSource, PoseStack matrices, float tickDelta, int light, float alpha, int overlay, boolean translucent, boolean glowing, double camX, double camY, double camZ) {
        this.setupRenderer(currentFilterScheme, bufferSource, tickDelta, light, alpha, overlay, translucent, glowing);
        this.setMatrices(camX, camY, camZ, matrices);
    }

    private void setupRenderer(PartFilterScheme currentFilterScheme, MultiBufferSource bufferSource, float tickDelta, int light, float alpha, int overlay, boolean translucent, boolean glowing) {
        this.currentFilterScheme = currentFilterScheme;
        this.bufferSource = bufferSource;
        this.tickDelta = tickDelta;
        this.light = light;
        this.alpha = alpha;
        this.overlay = overlay;
        this.translucent = translucent;
        this.glowing = glowing;
    }

    public void setMatrices(PoseStack matrices) {
        PoseStack.Pose pose = matrices.last();
        this.posMat.set(pose.pose());
        this.normalMat.set(pose.normal());
    }

    public void setMatrices(double camX, double camY, double camZ, PoseStack matrices) {
        PoseStack.Pose pose = matrices.last();

        // pos
        Matrix4d posMat = new Matrix4d(pose.pose());
        posMat.translate(-camX, -camY, -camZ);
        posMat.scale(-1, -1, 1);
        this.posMat.set(posMat);

        // normal
        Matrix3f normalMat = new Matrix3f(pose.normal());
        normalMat.scale(-1, -1, 1);
        this.normalMat.set(normalMat);
    }
}
