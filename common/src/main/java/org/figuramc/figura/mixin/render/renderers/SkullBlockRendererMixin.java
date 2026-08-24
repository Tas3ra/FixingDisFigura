package org.figuramc.figura.mixin.render.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.ducks.FiguraSubmitCallBackExtension;
import org.figuramc.figura.ducks.NodeCollectorExtension;
import org.figuramc.figura.ducks.SkullBlockRenderStateExtension;
import org.figuramc.figura.ducks.SkullBlockRendererAccessor;
import org.figuramc.figura.ducks.SkullBlockRendererHelper;
import org.figuramc.figura.gui.PopupMenu;
import org.figuramc.figura.gui.ViewerVisibilityManager;
import org.figuramc.figura.lua.api.entity.EntityAPI;
import org.figuramc.figura.lua.api.popup.PopupAPI;
import org.figuramc.figura.lua.api.world.BlockStateAPI;
import org.figuramc.figura.lua.api.world.ItemStackAPI;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.permissions.Permissions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkullBlockRenderer.class)
public abstract class SkullBlockRendererMixin implements BlockEntityRenderer<SkullBlockEntity, SkullBlockRenderState> {

    @Unique
    private static Avatar avatar;
    @Unique
    private static SkullBlockRenderState block;

    @Inject(at = @At("HEAD"), method = "submitSkull", cancellable = true)
    private static void renderSkull(Direction direction, float yaw, float animationProgress, PoseStack stack, SubmitNodeCollector submitNodeCollector, int light, SkullModelBase model, RenderType renderLayer, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, CallbackInfo ci) {
        // parse block and items first, so we can yeet them in case of a missed event
        if (avatar == null) {
            avatar = SkullBlockRendererHelper.getAvatar();
        }
        SkullBlockRendererHelper.clear();

        SkullBlockRenderState localBlock = block;
        block = null;

        ItemStack localItem = SkullBlockRendererAccessor.getItem();

        Entity localEntity = SkullBlockRendererAccessor.getEntity();

        SkullBlockRendererAccessor.SkullRenderMode localMode = SkullBlockRendererAccessor.getRenderMode();
        EntityRenderMode localEntityRenderMode = SkullBlockRendererAccessor.getEntityRenderMode();
        SkullBlockRendererAccessor.clear();

        // avatar pointer incase avatar variable is set during render. (unlikely)
        Avatar localAvatar = avatar;
        avatar = null;

        if (AvatarManager.panic || localAvatar == null || localAvatar.permissions.get(Permissions.CUSTOM_SKULL) == 0)
            return;

        if (!ViewerVisibilityManager.areCustomSkullsVisible(localAvatar.owner))
            return;

        if (figura$isUnownedItemRender(localBlock, localItem, localEntity, localMode))
            return;

        boolean guiItemSkull = localMode == SkullBlockRendererAccessor.SkullRenderMode.GUI;
        boolean guiEntitySkull = figura$isGuiEntityRender(localEntityRenderMode);
        if (guiItemSkull || guiEntitySkull) {
            String eventMode = guiItemSkull ? SkullBlockRendererAccessor.SkullRenderMode.OTHER.name() : localMode.name();
            figura$submitGuiSkull(localAvatar, localBlock, localItem, localEntity, stack, submitNodeCollector, light, direction, yaw, model, eventMode);
            return;
        }

        float tickDelta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);

        FiguraSubmitCallBackExtension modelExtension = (FiguraSubmitCallBackExtension) model;
        modelExtension.figura$addPreRenderingCallback((bufferSource, poseStack) -> {

            FiguraMod.pushProfiler(FiguraMod.MOD_ID);
            FiguraMod.pushProfiler(localAvatar);
            FiguraMod.pushProfiler("skullRender");

            // event
            BlockStateAPI b = localBlock == null ? null : new BlockStateAPI(localBlock.blockState, localBlock.blockPos);
            ItemStackAPI i = localItem != null ? ItemStackAPI.verify(localItem) : null;
            EntityAPI<?> e = localEntity != null ? EntityAPI.wrap(localEntity) : null;
            String m = localMode == SkullBlockRendererAccessor.SkullRenderMode.GUI ? SkullBlockRendererAccessor.SkullRenderMode.OTHER.name() : localMode.name();

            FiguraMod.pushProfiler(localBlock != null ? localBlock.blockPos.toString() : String.valueOf(i));

            FiguraMod.pushProfiler("event");
            PopupAPI.pushContext(PopupMenu.contextKeyForHead(localBlock == null ? null : localBlock.blockPos, localItem, localEntity));
            boolean bool;
            try {
                bool = localAvatar.skullRenderEvent(tickDelta, b, i, e, m);

                // render skull :3
                FiguraMod.popPushProfiler("render");
                boolean guiItem = localMode == SkullBlockRendererAccessor.SkullRenderMode.GUI;
                boolean renderedSkull = localAvatar.skullRender(poseStack, bufferSource, light, direction, yaw, false, guiItem);
                if (bool || renderedSkull)
                    return false;
            } finally {
                PopupAPI.popContext();
            }

            FiguraMod.popProfiler(5);
            return true;
        });
    }

    @Unique
    private static boolean figura$isUnownedItemRender(SkullBlockRenderState block, ItemStack item, Entity entity, SkullBlockRendererAccessor.SkullRenderMode mode) {
        return mode == SkullBlockRendererAccessor.SkullRenderMode.OTHER && block == null && entity == null && item != null;
    }

    @Unique
    private static boolean figura$isGuiEntityRender(EntityRenderMode renderMode) {
        return renderMode == EntityRenderMode.MINECRAFT_GUI || renderMode == EntityRenderMode.FIGURA_GUI || renderMode == EntityRenderMode.PAPERDOLL;
    }

    @Unique
    private static void figura$submitGuiSkull(Avatar localAvatar, SkullBlockRenderState localBlock, ItemStack localItem, Entity localEntity, PoseStack stack, SubmitNodeCollector submitNodeCollector, int light, Direction direction, float yaw, SkullModelBase model, String eventMode) {
        PoseStack guiStack = new PoseStack();
        guiStack.last().set(stack.last());
        boolean[] rendered = {false};

        ((NodeCollectorExtension) submitNodeCollector).submitFiguraModel(localAvatar, null, (avatar, entity, bufferSource) -> {
            FiguraMod.pushProfiler(FiguraMod.MOD_ID);
            FiguraMod.pushProfiler(localAvatar);
            FiguraMod.pushProfiler("skullGuiRender");

            BlockStateAPI b = localBlock == null ? null : new BlockStateAPI(localBlock.blockState, localBlock.blockPos);
            ItemStackAPI i = localItem != null ? ItemStackAPI.verify(localItem) : null;
            EntityAPI<?> e = localEntity != null ? EntityAPI.wrap(localEntity) : null;

            FiguraMod.pushProfiler(localBlock != null ? localBlock.blockPos.toString() : String.valueOf(i));
            FiguraMod.pushProfiler("event");
            PopupAPI.pushContext(PopupMenu.contextKeyForHead(localBlock == null ? null : localBlock.blockPos, localItem, localEntity));
            try {
                boolean bool = localAvatar.skullRenderEvent(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true), b, i, e, eventMode);

                FiguraMod.popPushProfiler("render");
                boolean renderedSkull = localAvatar.skullRender(guiStack, bufferSource, light, direction, yaw, true, true);
                rendered[0] = bool || renderedSkull;
            } finally {
                PopupAPI.popContext();
            }

            FiguraMod.popProfiler(5);
            return null;
        });

        FiguraSubmitCallBackExtension modelExtension = (FiguraSubmitCallBackExtension) model;
        modelExtension.figura$addPreRenderingCallback((bufferSource, poseStack) -> !rendered[0]);
    }

    @Inject(at = @At("RETURN"), method = "submitSkull")
    private static void figura$clearUnclaimedSkullCallbacks(Direction direction, float yaw, float animationProgress, PoseStack stack, SubmitNodeCollector submitNodeCollector, int light, SkullModelBase model, RenderType renderLayer, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, CallbackInfo ci) {
        FiguraSubmitCallBackExtension modelExtension = (FiguraSubmitCallBackExtension) model;
        modelExtension.figura$getPreRenderingCallbacks().clear();
        modelExtension.figura$getPostRenderingCallbacks().clear();
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/SkullBlockRenderer;submitSkull(Lnet/minecraft/core/Direction;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/model/object/skull/SkullModelBase;Lnet/minecraft/client/renderer/rendertype/RenderType;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"), method = "submit(Lnet/minecraft/client/renderer/blockentity/state/SkullBlockRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V")
    public void render(SkullBlockRenderState skullBlockRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        block = skullBlockRenderState;
        avatar = ((SkullBlockRenderStateExtension)skullBlockRenderState).figura$getAvatar();
        SkullBlockRendererAccessor.setRenderMode(SkullBlockRendererAccessor.SkullRenderMode.BLOCK);
    }

    @Inject(at = @At("RETURN"), method = "submit(Lnet/minecraft/client/renderer/blockentity/state/SkullBlockRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V")
    public void clearRenderContext(SkullBlockRenderState skullBlockRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        block = null;
        avatar = null;
        SkullBlockRendererHelper.clear();
        SkullBlockRendererAccessor.clear();
    }

    @Inject(at = @At("TAIL"), method = "extractRenderState(Lnet/minecraft/world/level/block/entity/SkullBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/SkullBlockRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V")
    public void captureAvatar(SkullBlockEntity skullBlockEntity, SkullBlockRenderState skullBlockRenderState, float tickDelta, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, CallbackInfo ci) {
        Avatar skullAvatar = null;
        if (!AvatarManager.panic && skullBlockRenderState.skullType == SkullBlock.Types.PLAYER) {
            ResolvableProfile profile = skullBlockEntity.getOwnerProfile();
            skullAvatar = AvatarManager.getAvatarForProfile(profile);
            if (skullAvatar != null && !ViewerVisibilityManager.areCustomSkullsVisible(skullAvatar.owner))
                skullAvatar = null;
        }

        ((SkullBlockRenderStateExtension)skullBlockRenderState).figura$setAvatar(skullAvatar);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        Avatar localAvatar = avatar; // avatar pointer incase avatar variable is set during render.
        return localAvatar == null || localAvatar.permissions == null ? BlockEntityRenderer.super.shouldRenderOffScreen() : localAvatar.permissions.get(Permissions.OFFSCREEN_RENDERING) == 1;
    }
}
