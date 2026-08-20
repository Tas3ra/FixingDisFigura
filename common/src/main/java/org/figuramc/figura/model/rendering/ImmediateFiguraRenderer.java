package org.figuramc.figura.model.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.lua.api.ClientAPI;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.math.vector.FiguraVec4;
import org.figuramc.figura.model.*;
import org.figuramc.figura.model.rendering.texture.FiguraRenderTypes;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.model.rendering.texture.FiguraTextureSet;
import org.figuramc.figura.model.rendertasks.RenderTask;
import org.figuramc.figura.utils.ColorUtils;
import org.figuramc.figura.utils.TextRenderUtils;
import org.figuramc.figura.utils.ui.UIHelper;

import java.util.*;
import java.util.function.Consumer;

public class ImmediateFiguraRenderer extends FiguraRenderer {

    protected final PartCustomization.PartCustomizationStack customizationStack = new PartCustomization.PartCustomizationStack();

    public static final FiguraMat4 CAMERA_POS_TO_WORLD_MATRIX = FiguraMat4.of();
    public static final FiguraMat4 CAMERA_VIEW_TO_WORLD_MATRIX = FiguraMat4.of();

    protected static final VertexBuffer VERTEX_BUFFER = new VertexBuffer();

    public ImmediateFiguraRenderer(Avatar avatar) {
        super(avatar);

        // Vertex data, read model parts
        root = FiguraModelPartReader.read(avatar, avatar.nbt.getCompoundOrEmpty("models"), textureSets, false);

        sortParts();
    }

    public void checkEmpty() {
        if (!customizationStack.isEmpty())
            throw new IllegalStateException("Customization stack not empty!");
    }

    @Override
    public int render() {
        return commonRender(1.5d);
    }

    @Override
    public int renderSpecialParts() {
        return commonRender(0);
    }

    @Override
    public void updateMatrices() {
        // flag rendering state
        this.isRendering = true;

        try {
            if (root == null)
                return;

            clearPivotCustomizations();

            // setup root customizations
            PartCustomization customization = setupRootCustomization(1.5d);

            // Push transform
            customizationStack.push(customization);

            // world matrices
            CAMERA_POS_TO_WORLD_MATRIX.set(FiguraRenderer.worldToCameraPosMatrix().invert());
            CAMERA_VIEW_TO_WORLD_MATRIX.set(FiguraRenderer.viewToWorldMatrix());

            // calculate each part matrices
            calculatePartMatrices(root, currentFilterScheme.initialValue);

            // Pivot matrices are consumed by vanilla render layers after this pass. They should keep
            // following Figura's transform tree even when the matching vanilla model part is hidden.
            clearPivotCustomizations();
            collectPivotMatrices(root, PartFilterScheme.PIVOTS.initialValue);

            // finish rendering
            customizationStack.pop();
            checkEmpty();
        } finally {
            customizationStack.clear();
            this.isRendering = false;
            if (this.dirty)
                clean();
        }
    }

    public int getComplexity() {
        // complexity
        int prev = avatar.complexity.remaining;
        int[] remainingComplexity = new int[] {prev};

        // explore all model parts
        if (root != null && root.customization.visible) {
            if (currentFilterScheme.parentType.isSeparate) {
                List<FiguraModelPart> parts = separatedParts.get(currentFilterScheme.parentType);
                if (parts != null) {
                    for (FiguraModelPart part : parts) {
                        if (currentFilterScheme.parentType == ParentType.Item && part != itemToRender)
                            continue;

                        getPartComplexity(part, remainingComplexity, currentFilterScheme.initialValue);
                    }
                }
            } else {
                getPartComplexity(root, remainingComplexity, currentFilterScheme.initialValue);
            }
        }
        return prev - Math.max(remainingComplexity[0], 0);
    }

    protected int commonRender(double vertOffset) {
        // flag rendering state
        this.isRendering = true;
        boolean immediateTextTaskLayer = false;
        try {
            if (root == null)
                return 0;

            immediateTextTaskLayer = bufferSource instanceof MultiBufferSource.BufferSource && TextRenderUtils.beginImmediateTextTaskLayer();

            // iris fix
            int irisConfig = UIHelper.paperdoll || !ClientAPI.hasShaderPackMod() ? 0 : Configs.IRIS_COMPATIBILITY_FIX.value;
            boolean worldLikeRender = avatar.renderMode == EntityRenderMode.WORLD || avatar.renderMode == EntityRenderMode.FIRST_PERSON_WORLD;
            doIrisEmissiveFix = (irisConfig >= 2 && ClientAPI.hasShaderPack()) || (avatar.renderMode != EntityRenderMode.RENDER && !worldLikeRender);
            offsetRenderLayers = irisConfig >= 1;

            // custom textures
            for (FiguraTextureSet set : textureSets)
                set.uploadIfNeeded();
            for (FiguraTexture texture : customTextures.values())
                texture.uploadIfDirty(false, false);

            // Set shouldRenderPivots
            int config = Configs.RENDER_DEBUG_PARTS_PIVOT.value;
            if (config <= 1 || !Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES) || (!avatar.isHost && config < 3))
                shouldRenderPivots = 0;
            else
                shouldRenderPivots = config;

            // world matrices
            if (allowMatrixUpdate) {
                CAMERA_POS_TO_WORLD_MATRIX.set(FiguraRenderer.worldToCameraPosMatrix().invert());
                CAMERA_VIEW_TO_WORLD_MATRIX.set(FiguraRenderer.viewToWorldMatrix());
            }

            // complexity
            int prev = avatar.complexity.remaining;
            int[] remainingComplexity = new int[] {prev};

            // render all model parts
            if (root.customization.visible) {
                if (currentFilterScheme.parentType.isSeparate) {
                    List<FiguraModelPart> parts = separatedParts.get(currentFilterScheme.parentType);
                    if (parts != null) {
                        boolean renderLayer = !currentFilterScheme.parentType.isRenderLayer;
                        if (renderLayer) {
                            PartCustomization customization = setupRootCustomization(vertOffset);
                            customizationStack.push(customization); // push root
                            customizationStack.push(root.customization); // push "models"
                        }

                        for (FiguraModelPart part : parts) {
                            if (currentFilterScheme.parentType == ParentType.Item && part != itemToRender)
                                continue;

                            boolean saved = part.savedCustomization != null;
                            if (saved) {
                                customizationStack.push(part.savedCustomization);
                                part.savedCustomization = null;
                            }

                            renderPart(part, remainingComplexity, currentFilterScheme.initialValue);

                            if (saved) customizationStack.pop();
                        }

                        if (renderLayer) {
                            customizationStack.pop(); // pop "models"
                            customizationStack.pop(); // pop root
                        }
                    }
                } else {
                    PartCustomization customization = setupRootCustomization(vertOffset);
                    customizationStack.push(customization);
                    renderPart(root, remainingComplexity, currentFilterScheme.initialValue);
                    customizationStack.pop();
                }

                // push vertices to vertex consumer
                FiguraMod.pushProfiler("draw");
                FiguraMod.pushProfiler("primary");
                VERTEX_BUFFER.consume(true, bufferSource);
                FiguraMod.popPushProfiler("secondary");
                VERTEX_BUFFER.consume(false, bufferSource);
                FiguraMod.popProfiler(2);
                if (immediateTextTaskLayer) {
                    TextRenderUtils.renderImmediateTextTaskLayer((MultiBufferSource.BufferSource) bufferSource);
                    immediateTextTaskLayer = false;
                }

                // finish rendering
                checkEmpty();
            } else if (immediateTextTaskLayer) {
                TextRenderUtils.renderImmediateTextTaskLayer((MultiBufferSource.BufferSource) bufferSource);
                immediateTextTaskLayer = false;
            }

            return prev - Math.max(remainingComplexity[0], 0);
        } finally {
            if (immediateTextTaskLayer)
                TextRenderUtils.clearImmediateTextTaskLayer();
            VERTEX_BUFFER.clear();
            customizationStack.clear();
            this.isRendering = false;
            if (this.dirty)
                clean();
        }
    }

    protected PartCustomization setupRootCustomization(double vertOffset) {
        PartCustomization customization = new PartCustomization();

        customization.setPrimaryRenderType(FiguraRenderTypes.TRANSLUCENT);
        customization.setSecondaryRenderType(FiguraRenderTypes.EMISSIVE);

        double s = 1.0 / 16;
        customization.positionMatrix.scale(s, s, s);
        customization.positionMatrix.rotateZ(180);
        customization.positionMatrix.translate(0, vertOffset, 0);
        customization.normalMatrix.rotateZ(180);

        customization.positionMatrix.multiply(posMat);
        customization.normalMatrix.multiply(normalMat);

        customization.light = light;
        customization.alpha = alpha;
        customization.overlay = overlay;

        customization.primaryTexture = new TextureCustomization(FiguraTextureSet.OverrideType.PRIMARY, null);
        customization.secondaryTexture = new TextureCustomization(FiguraTextureSet.OverrideType.SECONDARY, null);

        return customization;
    }

    protected boolean renderPart(FiguraModelPart part, int[] remainingComplexity, boolean prevPredicate) {
        return renderPart(part, remainingComplexity, prevPredicate, true);
    }

    protected boolean renderPart(FiguraModelPart part, int[] remainingComplexity, boolean prevPredicate, boolean ancestorsVisible) {
        FiguraMod.pushProfiler(part.name);

        PartCustomization custom = part.customization;

        // test the current filter scheme
        FiguraMod.pushProfiler("predicate");
        Boolean thisPassedPredicate = currentFilterScheme.test(part.parentType, prevPredicate);
        boolean hidden = !custom.visible;
        if (thisPassedPredicate == null || (hidden && !allowHiddenTransforms)) {
            if (part.parentType.isRenderLayer)
                part.savedCustomization = customizationStack.peek();
            FiguraMod.popProfiler(2);
            return true;
        }

        // calculate part transforms

        // calculate vanilla parent
        FiguraMod.popPushProfiler("copyVanillaPart");
        part.applyVanillaTransforms(vanillaModelData);
        part.applyExtraTransforms(customizationStack.peek());

        // visibility
        FiguraMod.popPushProfiler("checkVanillaVisible");
        boolean vanillaHidden = !ignoreVanillaVisibility && custom.vanillaVisible != null && !custom.vanillaVisible;
        if (vanillaHidden && !allowHiddenTransforms) {
            FiguraMod.popPushProfiler("removeVanillaTransforms");
            part.resetVanillaTransforms();
            FiguraMod.popProfiler(2);
            return true;
        }

        boolean renderThisPart = ancestorsVisible && !hidden && !vanillaHidden && thisPassedPredicate;
        boolean renderChildren = ancestorsVisible && ((!hidden && !vanillaHidden) || allowHiddenDescendantRendering);

        // pre render function
        if (renderThisPart && part.preRender != null) {
            FiguraMod.popPushProfiler("preRenderFunction");
            avatar.run(part.preRender, avatar.render, tickDelta, avatar.renderMode.luaName(), part);
        }

        // recalculate stuff
        FiguraMod.popPushProfiler("calculatePartMatrices");
        custom.recalculate();

        // void blocked matrices
        // that's right, check only for previous predicate
        FiguraMat4 positionCopy = null;
        FiguraMat3 normalCopy = null;
        boolean voidMatrices = !allowHiddenTransforms && !prevPredicate;
        if (voidMatrices) {
            FiguraMod.popPushProfiler("clearMatrices");
            positionCopy = custom.positionMatrix.copy();
            normalCopy = custom.normalMatrix.copy();
            custom.positionMatrix.reset();
            custom.normalMatrix.reset();
        }

        // push stack
        FiguraMod.popPushProfiler("pushCustomizationStack");
        customizationStack.push(custom);

        // restore variables
        if (voidMatrices) {
            FiguraMod.popPushProfiler("restoreMatrices");
            custom.positionMatrix.set(positionCopy);
            custom.normalMatrix.set(normalCopy);
        }

        boolean previousUpdateLight = updateLight;
        if (thisPassedPredicate) {
            // recalculate world matrices
            FiguraMod.popPushProfiler("worldMatrices");
            if (allowMatrixUpdate) {
                FiguraMat4 mat = partToWorldMatrices(custom);
                part.savedPartToWorldMat.set(mat);
            }

            // recalculate light
            FiguraMod.popPushProfiler("calculateLight");
            Level l;
            if (!renderThisPart) {
                updateLight = false;
            }
            else if (custom.light != null) {
                updateLight = false;
            }
            else if (updateLight && (l = Minecraft.getInstance().level) != null) {
                FiguraVec3 pos = part.savedPartToWorldMat.apply(0d, 0d, 0d);
                int block = l.getBrightness(LightLayer.BLOCK, pos.asBlockPos());
                int sky = l.getBrightness(LightLayer.SKY, pos.asBlockPos());
                customizationStack.peek().light = LightTexture.pack(block, sky);
            }
        }

        // mid render function
        if (renderThisPart && part.midRender != null) {
            FiguraMod.popPushProfiler("midRenderFunction");
            avatar.run(part.midRender, avatar.render, tickDelta, avatar.renderMode.luaName(), part);
        }

        // render this
        FiguraMod.popPushProfiler("pushVertices");
        boolean breakRender = renderThisPart && !part.pushVerticesImmediate(this, remainingComplexity);

        // render extras
        FiguraMod.popPushProfiler("extras");
        if (!breakRender && renderThisPart) {
            boolean renderPivot = shouldRenderPivots > 0;
            boolean renderTasks = !part.renderTasks.isEmpty();
            boolean renderPivotParts = part.parentType.isPivot && allowPivotParts;

            if (renderPivot || renderTasks || renderPivotParts) {
                // fix pivots
                FiguraMod.pushProfiler("fixMatricesPivot");
                PartCustomization prePivot = customizationStack.peek();
                int taskLight = prePivot.light != null ? prePivot.light : LightTexture.FULL_BRIGHT;
                int taskOverlay = prePivot.overlay != null ? prePivot.overlay : OverlayTexture.NO_OVERLAY;

                FiguraVec3 pivot = custom.getPivot().copy().add(custom.getOffsetPivot());
                PartCustomization pivotOffsetter = new PartCustomization();
                pivotOffsetter.setPos(pivot);
                pivotOffsetter.recalculate();
                customizationStack.push(pivotOffsetter);
                try {

                    PartCustomization peek = customizationStack.peek();

                    // render pivot indicators
                    if (renderPivot) {
                        FiguraMod.popPushProfiler("renderPivotCube");
                        renderPivot(part, peek);
                    }

                    // render tasks
                    if (renderTasks) {
                        FiguraMod.popPushProfiler("renderTasks");
                        boolean previousIntercept = interceptRendersIntoFigura;
                        interceptRendersIntoFigura = false;
                        try {
                            for (RenderTask task : part.renderTasks.values()) {
                                if (!task.shouldRender())
                                    continue;
                                int neededComplexity = task.getComplexity();
                                if (neededComplexity > remainingComplexity[0])
                                    continue;
                                FiguraMod.pushProfiler(task.getName());
                                try {
                                    task.render(customizationStack, bufferSource, taskLight, taskOverlay);
                                    remainingComplexity[0] -= neededComplexity;
                                } finally {
                                    FiguraMod.popProfiler();
                                }
                            }
                        } finally {
                            interceptRendersIntoFigura = previousIntercept;
                        }
                    }

                    // render pivot parts
                    if (renderPivotParts && part.parentType.isPivot) {
                        FiguraMod.popPushProfiler("savePivotParts");
                        savePivotTransform(part, peek);
                    }
                } finally {
                    customizationStack.pop();
                    FiguraMod.popProfiler();
                }
            }
        }

        // render children
        FiguraMod.popPushProfiler("children");
        for (FiguraModelPart child : List.copyOf(part.children)) {
            if (!renderPart(child, remainingComplexity, thisPassedPredicate, renderChildren)) {
                breakRender = true;
                break;
            }
        }

        // reset the parent
        FiguraMod.popPushProfiler("removeVanillaTransforms");
        part.resetVanillaTransforms();

        // post render function
        if (renderThisPart && part.postRender != null) {
            FiguraMod.popPushProfiler("postRenderFunction");
            avatar.run(part.postRender, avatar.render, tickDelta, avatar.renderMode.luaName(), part);
        }

        // pop
        updateLight = previousUpdateLight;
        customizationStack.pop();
        FiguraMod.popProfiler(2);

        return !breakRender;
    }


    protected boolean getPartComplexity(FiguraModelPart part, int[] remainingComplexity, boolean prevPredicate) {
        PartCustomization custom = part.customization;

        // test the current filter scheme
        Boolean thisPassedPredicate = currentFilterScheme.test(part.parentType, prevPredicate);
        if (thisPassedPredicate == null || (!custom.visible)) {
            return true;
        }

        // visibility
        if (!ignoreVanillaVisibility && custom.vanillaVisible != null && !custom.vanillaVisible) {
            return true;
        }

        // calculate this part's complexity
        FiguraMod.popPushProfiler("pushVertices");
        boolean breakRender = thisPassedPredicate && !part.calculateComplexity(remainingComplexity);

        // calculate extras
        if (!breakRender && thisPassedPredicate) {
            boolean renderTasks = !part.renderTasks.isEmpty();
            // add tasks
            if (renderTasks) {

                for (RenderTask task : part.renderTasks.values()) {
                    if (!task.shouldRender())
                        continue;
                    int neededComplexity = task.getComplexity();
                    if (neededComplexity > remainingComplexity[0])
                        continue;
                    FiguraMod.pushProfiler(task.getName());
                    remainingComplexity[0] -= neededComplexity;
                    FiguraMod.popProfiler();
                }
            }
        }

        // calculate children
        for (FiguraModelPart child : List.copyOf(part.children)) {
            if (!getPartComplexity(child, remainingComplexity, thisPassedPredicate)) {
                breakRender = true;
                break;
            }
        }
        return !breakRender;
    }

    protected void renderPivot(FiguraModelPart part, PartCustomization customization) {
        boolean group = part.customization.partType == PartCustomization.PartType.GROUP;
        FiguraVec3 color = group ? ColorUtils.Colors.FIGURA_BLUE.vec : ColorUtils.Colors.AWESOME_BLUE.vec;
        double determinant = Math.abs(part.savedPartToWorldMat.det());
        if (!Double.isFinite(determinant) || !figura$isFinite(customization.positionMatrix) || !figura$isFinite(customization.normalMatrix))
            return;

        double worldScale = Math.cbrt(determinant);
        if (!Double.isFinite(worldScale))
            return;

        double boxSize = group ? 1 / 16d : 1 / 32d;
        boxSize /= Math.max(worldScale, 0.02);
        if (!Double.isFinite(boxSize))
            return;

        PoseStack stack = customization.copyIntoGlobalPoseStack();

        renderLineBox(stack.last(), bufferSource.getBuffer(RenderTypes.LINES),
                -boxSize, -boxSize, -boxSize,
                boxSize, boxSize, boxSize,
                (float) color.x, (float) color.y, (float) color.z, 1f);
    }

    private static boolean figura$isFinite(FiguraMat4 matrix) {
        return Double.isFinite(matrix.v11) && Double.isFinite(matrix.v12) && Double.isFinite(matrix.v13) && Double.isFinite(matrix.v14) &&
                Double.isFinite(matrix.v21) && Double.isFinite(matrix.v22) && Double.isFinite(matrix.v23) && Double.isFinite(matrix.v24) &&
                Double.isFinite(matrix.v31) && Double.isFinite(matrix.v32) && Double.isFinite(matrix.v33) && Double.isFinite(matrix.v34) &&
                Double.isFinite(matrix.v41) && Double.isFinite(matrix.v42) && Double.isFinite(matrix.v43) && Double.isFinite(matrix.v44);
    }

    private static boolean figura$isFinite(FiguraMat3 matrix) {
        return Double.isFinite(matrix.v11) && Double.isFinite(matrix.v12) && Double.isFinite(matrix.v13) &&
                Double.isFinite(matrix.v21) && Double.isFinite(matrix.v22) && Double.isFinite(matrix.v23) &&
                Double.isFinite(matrix.v31) && Double.isFinite(matrix.v32) && Double.isFinite(matrix.v33);
    }

    public static void renderLineBox(PoseStack.Pose pose, VertexConsumer vertices, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        float f = (float)x1;
        float h = (float)y1;
        float i = (float)z1;
        float j = (float)x2;
        float k = (float)y2;
        float l = (float)z2;
        float lineWidth = figura$getLineWidth();
        addLineVertex(pose, vertices, f, h, i, r, g, b, a, 1.0F, 0.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, j, h, i, r, g, b, a, 1.0F, 0.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, f, h, i, r, g, b, a, 0.0F, 1.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, f, k, i, r, g, b, a, 0.0F, 1.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, f, h, i, r, g, b, a, 0.0F, 0.0F, 1.0F, lineWidth);
        addLineVertex(pose, vertices, f, h, l, r, g, b, a, 0.0F, 0.0F, 1.0F, lineWidth);
        addLineVertex(pose, vertices, j, h, i, r, g, b, a, 0.0F, 1.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, j, k, i, r, g, b, a, 0.0F, 1.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, j, k, i, r, g, b, a, -1.0F, 0.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, f, k, i, r, g, b, a, -1.0F, 0.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, f, k, i, r, g, b, a, 0.0F, 0.0F, 1.0F, lineWidth);
        addLineVertex(pose, vertices, f, k, l, r, g, b, a, 0.0F, 0.0F, 1.0F, lineWidth);
        addLineVertex(pose, vertices, f, k, l, r, g, b, a, 0.0F, -1.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, f, h, l, r, g, b, a, 0.0F, -1.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, f, h, l, r, g, b, a, 1.0F, 0.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, j, h, l, r, g, b, a, 1.0F, 0.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, j, h, l, r, g, b, a, 0.0F, 0.0F, -1.0F, lineWidth);
        addLineVertex(pose, vertices, j, h, i, r, g, b, a, 0.0F, 0.0F, -1.0F, lineWidth);
        addLineVertex(pose, vertices, f, k, l, r, g, b, a, 1.0F, 0.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, j, k, l, r, g, b, a, 1.0F, 0.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, j, h, l, r, g, b, a, 0.0F, 1.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, j, k, l, r, g, b, a, 0.0F, 1.0F, 0.0F, lineWidth);
        addLineVertex(pose, vertices, j, k, i, r, g, b, a, 0.0F, 0.0F, 1.0F, lineWidth);
        addLineVertex(pose, vertices, j, k, l, r, g, b, a, 0.0F, 0.0F, 1.0F, lineWidth);
    }

    private static void addLineVertex(PoseStack.Pose pose, VertexConsumer vertices, float x, float y, float z, float r, float g, float b, float a, float nx, float ny, float nz, float lineWidth) {
        vertices.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(lineWidth);
    }

    private static float figura$getLineWidth() {
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        return Float.isFinite(lineWidth) && lineWidth > 0f ? lineWidth : 1f;
    }

    protected void savePivotTransform(FiguraModelPart part, PartCustomization customization) {
        ParentType parentType = part.parentType;
        FiguraMat4 currentPosMat = customization.getPositionMatrix();
        FiguraMat3 currentNormalMat = customization.getNormalMatrix();
        Queue<PivotCustomization> queue = getPivotCustomizationQueue(parentType);
        if (queue != null)
            queue.add(new PivotCustomization(currentPosMat, currentNormalMat, getModelDepth(part))); // These are COPIES, so ok to add
    }

    private static int getModelDepth(FiguraModelPart part) {
        int depth = 0;
        for (FiguraModelPart current = part; current != null; current = current.parent)
            depth++;
        return depth;
    }

    protected FiguraMat4 partToWorldMatrices(PartCustomization cust) {
        FiguraMat4 customizePeek = customizationStack.peek().positionMatrix.copy();
        // Entity/world poses are camera-position-relative in 1.21.11, while first-person
        // hand poses are camera/view-space and need the full camera orientation restored.
        customizePeek.multiply(avatar.renderMode == EntityRenderMode.FIRST_PERSON ? CAMERA_VIEW_TO_WORLD_MATRIX : CAMERA_POS_TO_WORLD_MATRIX);
        FiguraVec3 piv = cust.getPivot().add(cust.getOffsetPivot());

        FiguraMat4 translation = FiguraMat4.of();
        translation.translate(piv);
        customizePeek.rightMultiply(translation);

        return customizePeek;
    }

    protected void calculatePartMatrices(FiguraModelPart part, boolean prevPredicate) {
        FiguraMod.pushProfiler(part.name);

        PartCustomization custom = part.customization;

        // Store old visibility, but overwrite it in case we only want to render certain parts
        FiguraMod.pushProfiler("predicate");
        Boolean thisPassedPredicate = currentFilterScheme.test(part.parentType, prevPredicate);
        if (thisPassedPredicate == null || (!custom.visible && !allowHiddenTransforms)) {
            FiguraMod.popProfiler(2);
            return;
        }

        // calculate part transforms

        // calculate vanilla parent
        FiguraMod.popPushProfiler("copyVanillaPart");
        part.applyVanillaTransforms(vanillaModelData);
        part.applyExtraTransforms(customizationStack.peek());

        FiguraMod.popPushProfiler("checkVanillaVisible");
        if (!ignoreVanillaVisibility && custom.vanillaVisible != null && !custom.vanillaVisible && !allowHiddenTransforms) {
            FiguraMod.popPushProfiler("removeVanillaTransforms");
            part.resetVanillaTransforms();
            FiguraMod.popProfiler(2);
            return;
        }

        // push customization stack
        FiguraMod.popPushProfiler("calculatePartMatrices");
        custom.recalculate();
        FiguraMod.popPushProfiler("applyOnStack");
        customizationStack.push(custom);

        // render extras
        if (thisPassedPredicate) {
            // part to world matrices
            FiguraMod.popPushProfiler("worldMatrices");
            FiguraMat4 mat = partToWorldMatrices(custom);
            part.savedPartToWorldMat.set(mat);

            if (part.parentType.isPivot && allowPivotParts) {
                FiguraVec3 pivot = custom.getPivot().copy().add(custom.getOffsetPivot());
                PartCustomization pivotOffsetter = new PartCustomization();
                pivotOffsetter.setPos(pivot);
                pivotOffsetter.recalculate();
                customizationStack.push(pivotOffsetter);
                try {
                    savePivotTransform(part, customizationStack.peek());
                } finally {
                    customizationStack.pop();
                }
            }
        }

        // render children
        FiguraMod.popPushProfiler("children");
        for (FiguraModelPart child : part.children)
            calculatePartMatrices(child, thisPassedPredicate);

        // reset the parent
        part.resetVanillaTransforms();

        // pop
        customizationStack.pop();
        FiguraMod.popProfiler(2);
    }

    protected void collectPivotMatrices(FiguraModelPart part, boolean prevPredicate) {
        FiguraMod.pushProfiler(part.name);

        PartCustomization custom = part.customization;

        FiguraMod.pushProfiler("predicate");
        Boolean thisPassedPredicate = PartFilterScheme.PIVOTS.test(part.parentType, prevPredicate);
        if (thisPassedPredicate == null || (!custom.visible && !allowHiddenTransforms)) {
            FiguraMod.popProfiler(2);
            return;
        }

        FiguraMod.popPushProfiler("copyVanillaPart");
        part.applyVanillaTransforms(vanillaModelData);
        part.applyExtraTransforms(customizationStack.peek());

        FiguraMod.popPushProfiler("checkVanillaVisible");
        if (!ignoreVanillaVisibility && custom.vanillaVisible != null && !custom.vanillaVisible && !allowHiddenTransforms) {
            FiguraMod.popPushProfiler("removeVanillaTransforms");
            part.resetVanillaTransforms();
            FiguraMod.popProfiler(2);
            return;
        }

        FiguraMod.popPushProfiler("calculatePartMatrices");
        custom.recalculate();
        FiguraMod.popPushProfiler("applyOnStack");
        customizationStack.push(custom);

        if (thisPassedPredicate && part.parentType.isPivot && allowPivotParts) {
            FiguraMod.popPushProfiler("savePivotParts");
            FiguraVec3 pivot = custom.getPivot().copy().add(custom.getOffsetPivot());
            PartCustomization pivotOffsetter = new PartCustomization();
            pivotOffsetter.setPos(pivot);
            pivotOffsetter.recalculate();
            customizationStack.push(pivotOffsetter);
            try {
                savePivotTransform(part, customizationStack.peek());
            } finally {
                customizationStack.pop();
            }
        }

        FiguraMod.popPushProfiler("children");
        for (FiguraModelPart child : part.children)
            collectPivotMatrices(child, thisPassedPredicate);

        part.resetVanillaTransforms();

        customizationStack.pop();
        FiguraMod.popProfiler(2);
    }

    public void pushFaces(int faceCount, int[] remainingComplexity, FiguraTextureSet textureSet, List<Vertex> vertices) {
        // Handle cases that we can quickly
        if (faceCount == 0 || vertices.isEmpty())
            return;

        PartCustomization customization = customizationStack.peek();

        VertexData primary = getTexture(customization, textureSet, true);
        VertexData secondary = getTexture(customization, textureSet, false);

        if (primary.renderType == null && secondary.renderType == null) {
            remainingComplexity[0] += faceCount;
            return;
        }

        if (primary.renderType != null)
            pushToBuffer(faceCount, primary, customization, textureSet, vertices);
        if (secondary.renderType != null)
            pushToBuffer(faceCount, secondary, customization, textureSet, vertices);
    }

    private VertexData getTexture(PartCustomization customization, FiguraTextureSet textureSet, boolean primary) {
        FiguraRenderTypes types = primary ? customization.getPrimaryRenderType() : customization.getSecondaryRenderType();
        TextureCustomization texture = primary ? customization.primaryTexture : customization.secondaryTexture;
        VertexData ret = new VertexData();

        if (types == FiguraRenderTypes.NONE)
            return ret;

        // get texture
        Identifier id = textureSet.getOverrideTexture(avatar.owner, texture);

        // color
        ret.color = primary ? customization.color : customization.color2;

        // primary
        ret.primary = primary;

        // get render type
        if (glowing) {
            if (id != null)
                ret.renderType = RenderTypes.outline(id);
            return ret;
        }

        if (id != null) {
            if (translucent) {
                ret.renderType = RenderTypes.itemEntityTranslucentCull(id);
                return ret;
            }
        }

        if (types == null)
            return ret;

        ret.fullBright = types.isFullBright();
        if (types.needsLineWidth())
            ret.lineWidth = figura$getLineWidth();

        if (offsetRenderLayers && !primary && types.isOffset())
            ret.vertexOffset = FiguraMod.VERTEX_OFFSET;

        // Switch to cutout with fullbright if the iris emissive fix is enabled
        if (doIrisEmissiveFix && types == FiguraRenderTypes.EMISSIVE) {
            ret.fullBright = true;
            ret.renderType = FiguraRenderTypes.TRANSLUCENT_CULL.get(id);
        } else {
            ret.renderType = types.get(id);
        }

        return ret;
    }

    private static final FiguraVec4 pos = FiguraVec4.of();
    private static final FiguraVec3 normal = FiguraVec3.of();
    private static final FiguraVec3 uv = FiguraVec3.of(0, 0, 1);
    private void pushToBuffer(int faceCount, VertexData vertexData, PartCustomization customization, FiguraTextureSet textureSet, List<Vertex> vertices) {
        int vertCount = faceCount * 4;

        FiguraVec3 uvFixer = FiguraVec3.of();
        uvFixer.set(textureSet.getWidth(), textureSet.getHeight(), 1); // Dividing by this makes uv 0 to 1

        int overlay = customization.overlay;
        int light = vertexData.fullBright ? LightTexture.FULL_BRIGHT : customization.light;

        VERTEX_BUFFER.getBufferFor(vertexData.renderType, vertexData.primary, vertexConsumer -> {
            for (int i = 0; i < vertCount; i++) {
                Vertex vertex = vertices.get(i);

                pos.set(vertex.x, vertex.y, vertex.z, 1);
                pos.transform(customization.positionMatrix);
                pos.add(pos.normalized().scale(vertexData.vertexOffset));
                normal.set(vertex.nx, vertex.ny, vertex.nz);
                normal.transform(customization.normalMatrix);
                uv.set(vertex.u, vertex.v, 1);
                uv.divide(uvFixer);
                uv.transform(customization.uvMatrix);

                VertexConsumer consumer = vertexConsumer
                        .addVertex((float) pos.x, (float) pos.y, (float) pos.z)
                        .setColor((float) vertexData.color.x, (float) vertexData.color.y, (float) vertexData.color.z, customization.alpha)
                        .setUv((float) uv.x, (float) uv.y)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
                if (vertexData.lineWidth > 0f)
                    consumer.setLineWidth(vertexData.lineWidth);
            }
        });
    }

    private static class VertexData {
        public RenderType renderType;
        public boolean fullBright;
        public float vertexOffset;
        public float lineWidth;
        public FiguraVec3 color;
        public boolean primary;
    }

    private static class VertexBuffer {
        private final HashMap<RenderType, List<Consumer<VertexConsumer>>> primaryBuffers = new LinkedHashMap<>();
        private final HashMap<RenderType, List<Consumer<VertexConsumer>>> secondaryBuffers = new LinkedHashMap<>();

        public void getBufferFor(RenderType renderType, boolean primary, Consumer<VertexConsumer> consumer) {
            HashMap<RenderType, List<Consumer<VertexConsumer>>> buffer = primary ? primaryBuffers : secondaryBuffers;
            List<Consumer<VertexConsumer>> list = buffer.computeIfAbsent(renderType, renderType1 -> new ArrayList<>());
            list.add(consumer);
        }

        public void consume(boolean primary, MultiBufferSource bufferSource) {
            HashMap<RenderType, List<Consumer<VertexConsumer>>> map = primary ? primaryBuffers : secondaryBuffers;
            for (Map.Entry<RenderType, List<Consumer<VertexConsumer>>> entry : map.entrySet()) {
                VertexConsumer vertexConsumer = bufferSource.getBuffer(entry.getKey());
                List<Consumer<VertexConsumer>> consumers = entry.getValue();
                for (Consumer<VertexConsumer> consumer : consumers)
                    consumer.accept(vertexConsumer);
            }
            map.clear();
        }

        public void clear() {
            primaryBuffers.clear();
            secondaryBuffers.clear();
        }
    }
}
