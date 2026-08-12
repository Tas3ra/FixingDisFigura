package org.figuramc.figura.mixin.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.ducks.GeckolibGeoArmorAccessor;
import org.figuramc.figura.lua.api.vanilla_model.VanillaPart;
import org.figuramc.figura.model.ParentType;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.RenderUtils;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

import java.util.List;
import java.util.Objects;

@Pseudo
@Mixin(value = GeoArmorRenderer.class, remap = false)
public abstract class GeckolibGeoArmorRendererMixin<T extends Item & GeoItem, R extends HumanoidRenderState & GeoRenderState> implements GeckolibGeoArmorAccessor {
    @Unique
    private Avatar figura$avatar;

    @Shadow
    protected float scaleWidth;

    @Shadow
    protected float scaleHeight;

    @Shadow
    public abstract List<GeoArmorRenderer.ArmorSegment> getSegmentsForSlot(R renderState, EquipmentSlot slot);

    @Shadow
    public abstract String getBoneNameForSegment(R renderState, GeoArmorRenderer.ArmorSegment segment);

    @Inject(method = "captureDefaultRenderState(Lnet/minecraft/world/item/Item;Lsoftware/bernie/geckolib/renderer/GeoArmorRenderer$RenderData;Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;F)V", at = @At("HEAD"))
    private void figura$prepAvatar(T animatable, GeoArmorRenderer.RenderData renderData, R renderState, float partialTick, CallbackInfo ci){
        figura$avatar = renderData.entity() == null ? null : AvatarManager.getAvatar(renderData.entity());
    }

    @Inject(method = "submitRenderTasks", at = @At("HEAD"), cancellable = true)
    private void figura$submitRenderTasks(RenderPassInfo<R> renderPassInfo, OrderedSubmitNodeCollector renderTasks, RenderType renderType, CallbackInfo ci) {
        if (renderType != null) {
            int packedLight = renderPassInfo.packedLight();
            int packedOverlay = renderPassInfo.packedOverlay();
            int renderColor = renderPassInfo.renderColor();
            R renderState = renderPassInfo.renderState();
            EquipmentSlot slot = Objects.requireNonNull(renderState.getGeckolibData(DataTickets.EQUIPMENT_SLOT));
            Avatar avatar = figura$avatar;

            renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), renderType, (pose, vertexConsumer) -> {
                PoseStack poseStack = renderPassInfo.poseStack();

                poseStack.pushPose();
                poseStack.last().set(pose);
                renderPassInfo.renderPosed(() -> {
                    for (GeoArmorRenderer.ArmorSegment segment : getSegmentsForSlot(renderState, slot)) {
                        renderPassInfo.model().getBone(getBoneNameForSegment(renderState, segment))
                                .ifPresent(bone -> figura$renderSegment(avatar, segment, bone, renderPassInfo, vertexConsumer, packedLight, packedOverlay, renderColor));
                    }
                });
                poseStack.popPose();
            });
        }

        ci.cancel();
    }

    @Override
    @Unique
    public Avatar figura$getAvatar() {
        return figura$avatar;
    }

    @Override
    public float figura$getScaleWidth() {
        return scaleWidth;
    }

    @Override
    public float figura$getScaleHeight() {
        return scaleHeight;
    }

    @Unique
    private void figura$renderSegment(Avatar avatar, GeoArmorRenderer.ArmorSegment segment, GeoBone bone, RenderPassInfo<R> renderPassInfo, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int renderColor) {
        ParentType parentType = figura$parentTypeFor(segment);
        if (avatar == null || parentType == ParentType.None || avatar.permissions.get(Permissions.VANILLA_MODEL_EDIT) != 1) {
            bone.positionAndRender(renderPassInfo, vertexConsumer, packedLight, packedOverlay, renderColor);
            return;
        }

        VanillaPart vanillaPart = RenderUtils.pivotToPart(avatar, parentType);
        if (vanillaPart != null && !vanillaPart.checkVisible())
            return;

        boolean renderedPivot = avatar.pivotPartRender(parentType, pivotStack -> {
            PoseStack poseStack = renderPassInfo.poseStack();

            poseStack.pushPose();
            poseStack.last().set(pivotStack.last());
            figura$prepareArmorRender(poseStack);
            figura$transformBasedOnType(poseStack, parentType);
            bone.positionAndRender(renderPassInfo, vertexConsumer, packedLight, packedOverlay, renderColor);
            poseStack.popPose();
        });

        if (!renderedPivot)
            bone.positionAndRender(renderPassInfo, vertexConsumer, packedLight, packedOverlay, renderColor);
    }

    @Unique
    private static ParentType figura$parentTypeFor(GeoArmorRenderer.ArmorSegment segment) {
        return switch (segment) {
            case HEAD -> ParentType.HelmetPivot;
            case CHEST -> ParentType.ChestplatePivot;
            case LEFT_ARM -> ParentType.LeftShoulderPivot;
            case RIGHT_ARM -> ParentType.RightShoulderPivot;
            case LEFT_LEG -> ParentType.LeftLeggingPivot;
            case RIGHT_LEG -> ParentType.RightLeggingPivot;
            case LEFT_FOOT -> ParentType.LeftBootPivot;
            case RIGHT_FOOT -> ParentType.RightBootPivot;
        };
    }

    @Unique
    private static void figura$transformBasedOnType(PoseStack poseStack, ParentType parentType) {
        if (parentType == ParentType.LeftShoulderPivot) {
            poseStack.translate(-6 / 16f, 0f, 0f);
        } else if (parentType == ParentType.RightShoulderPivot) {
            poseStack.translate(6 / 16f, 0f, 0f);
        } else if (parentType == ParentType.LeggingsPivot) {
            poseStack.translate(0, -12 / 16f, 0);
        } else if (parentType == ParentType.LeftLeggingPivot) {
            poseStack.translate(-2 / 16f, -12 / 16f, 0);
        } else if (parentType == ParentType.RightLeggingPivot) {
            poseStack.translate(2 / 16f, -12 / 16f, 0);
        } else if (parentType == ParentType.LeftBootPivot) {
            poseStack.translate(-2 / 16f, -24 / 16f, 0);
        } else if (parentType == ParentType.RightBootPivot) {
            poseStack.translate(2 / 16f, -24 / 16f, 0);
        }
    }

    @Unique
    private static void figura$prepareArmorRender(PoseStack stack) {
        stack.scale(16, 16, 16);
        stack.mulPose(Axis.XP.rotationDegrees(180f));
        stack.mulPose(Axis.YP.rotationDegrees(180f));
    }
}
