package org.figuramc.figura.mixin.render.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.ducks.FiguraSubmitCallBackExtension;
import org.figuramc.figura.lua.api.vanilla_model.VanillaPart;
import org.figuramc.figura.model.ParentType;
import org.figuramc.figura.utils.RenderUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> extends RenderLayer<S, M> {
    @Shadow @Final private EquipmentLayerRenderer equipmentRenderer;

    public HumanoidArmorLayerMixin(RenderLayerParent<S, M> renderLayerParent) {
        super(renderLayerParent);
    }

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void figura$renderArmorPiece(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, ItemStack itemStack, EquipmentSlot armorSlot, int light, S renderState, CallbackInfo ci) {
        Avatar avatar = AvatarManager.getAvatar(renderState);
        if (!RenderUtils.vanillaModel(avatar))
            return;

        if (!HumanoidArmorLayer.shouldRender(itemStack, armorSlot))
            return;

        Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty())
            return;
        ResourceKey<EquipmentAsset> assetId = equippable.assetId().get();

        ParentType[] pivotTypes = figura$getPivotTypes(armorSlot);
        if (pivotTypes.length == 0)
            return;

        if (figura$isSlotHidden(avatar, armorSlot)) {
            for (ParentType parentType : pivotTypes)
                avatar.clearPivotPart(parentType);
            ci.cancel();
            return;
        }

        if (!figura$shouldHandleSlot(avatar, pivotTypes))
            return;

        @SuppressWarnings("unchecked")
        HumanoidArmorLayerAccessor<S, M, A> accessor = (HumanoidArmorLayerAccessor<S, M, A>) (Object) this;
        A armorModel = accessor.figura$getArmorModel(renderState, armorSlot);
        EquipmentClientInfo.LayerType layerType = accessor.usesInnerModel(armorSlot)
                ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
                : EquipmentClientInfo.LayerType.HUMANOID;

        int[] order = {0};
        switch (armorSlot) {
            case HEAD -> figura$submitPivotPart(avatar, renderState, armorModel, armorModel.head, ParentType.HelmetPivot, layerType, assetId, itemStack, poseStack, submitNodeCollector, light, renderState.outlineColor, order);
            case CHEST -> {
                figura$submitPivotPart(avatar, renderState, armorModel, armorModel.body, ParentType.ChestplatePivot, layerType, assetId, itemStack, poseStack, submitNodeCollector, light, renderState.outlineColor, order);
                figura$submitPivotPart(avatar, renderState, armorModel, armorModel.leftArm, ParentType.LeftShoulderPivot, layerType, assetId, itemStack, poseStack, submitNodeCollector, light, renderState.outlineColor, order);
                figura$submitPivotPart(avatar, renderState, armorModel, armorModel.rightArm, ParentType.RightShoulderPivot, layerType, assetId, itemStack, poseStack, submitNodeCollector, light, renderState.outlineColor, order);
            }
            case LEGS -> {
                figura$submitPivotPart(avatar, renderState, armorModel, armorModel.body, ParentType.LeggingsPivot, layerType, assetId, itemStack, poseStack, submitNodeCollector, light, renderState.outlineColor, order);
                figura$submitPivotPart(avatar, renderState, armorModel, armorModel.leftLeg, ParentType.LeftLeggingPivot, layerType, assetId, itemStack, poseStack, submitNodeCollector, light, renderState.outlineColor, order);
                figura$submitPivotPart(avatar, renderState, armorModel, armorModel.rightLeg, ParentType.RightLeggingPivot, layerType, assetId, itemStack, poseStack, submitNodeCollector, light, renderState.outlineColor, order);
            }
            case FEET -> {
                figura$submitPivotPart(avatar, renderState, armorModel, armorModel.leftLeg, ParentType.LeftBootPivot, layerType, assetId, itemStack, poseStack, submitNodeCollector, light, renderState.outlineColor, order);
                figura$submitPivotPart(avatar, renderState, armorModel, armorModel.rightLeg, ParentType.RightBootPivot, layerType, assetId, itemStack, poseStack, submitNodeCollector, light, renderState.outlineColor, order);
            }
            default -> {
            }
        }

        ci.cancel();
    }

    @Unique
    private static ParentType[] figura$getPivotTypes(EquipmentSlot armorSlot) {
        return switch (armorSlot) {
            case HEAD -> new ParentType[] {ParentType.HelmetPivot};
            case CHEST -> new ParentType[] {ParentType.ChestplatePivot, ParentType.LeftShoulderPivot, ParentType.RightShoulderPivot};
            case LEGS -> new ParentType[] {ParentType.LeggingsPivot, ParentType.LeftLeggingPivot, ParentType.RightLeggingPivot};
            case FEET -> new ParentType[] {ParentType.LeftBootPivot, ParentType.RightBootPivot};
            default -> new ParentType[0];
        };
    }

    @Unique
    private static boolean figura$isSlotHidden(Avatar avatar, EquipmentSlot armorSlot) {
        VanillaPart slotPart = RenderUtils.partFromSlot(avatar, armorSlot);
        return slotPart != null && !slotPart.checkVisible();
    }

    @Unique
    private static boolean figura$shouldHandleSlot(Avatar avatar, ParentType[] pivotTypes) {
        for (ParentType parentType : pivotTypes) {
            if (avatar.hasPivotPart(parentType))
                return true;

            VanillaPart part = RenderUtils.pivotToPart(avatar, parentType);
            if (part != null && !part.checkVisible())
                return true;
        }

        return false;
    }

    @Unique
    private void figura$submitPivotPart(Avatar avatar, S renderState, A armorModel, ModelPart modelPart, ParentType parentType, EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> assetId, ItemStack itemStack, PoseStack fallbackStack, SubmitNodeCollector nodeCollector, int light, int outlineColor, int[] order) {
        VanillaPart part = RenderUtils.pivotToPart(avatar, parentType);
        if (part != null && !part.checkVisible()) {
            avatar.clearPivotPart(parentType);
            return;
        }

        boolean renderedPivot = avatar.pivotPartRender(parentType, stack -> {
            stack.pushPose();
            figura$prepareArmorRender(stack);
            figura$transformBasedOnType(stack, parentType);
            figura$submitArmorModelPart(renderState, armorModel, modelPart, layerType, assetId, itemStack, stack, nodeCollector, light, outlineColor, order);
            stack.popPose();
        });

        if (!renderedPivot)
            figura$submitArmorModelPart(renderState, armorModel, modelPart, layerType, assetId, itemStack, fallbackStack, nodeCollector, light, outlineColor, order);
    }

    @Unique
    private void figura$submitArmorModelPart(S renderState, A armorModel, ModelPart modelPart, EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> assetId, ItemStack itemStack, PoseStack poseStack, SubmitNodeCollector nodeCollector, int light, int outlineColor, int[] order) {
        List<EquipmentClientInfo.Layer> layers = ((EquipmentLayerRendererAccessor) this.equipmentRenderer).figura$getAssetsManager().get(assetId).getLayers(layerType);
        if (layers.isEmpty())
            return;

        int dyeColor = DyedItemColor.getOrDefault(itemStack, 0);
        boolean hasFoil = itemStack.hasFoil();

        for (EquipmentClientInfo.Layer layer : layers) {
            int color = EquipmentLayerRendererAccessor.getColorForLayer(layer, dyeColor);
            if (color == 0)
                continue;

            Identifier texture = ((EquipmentLayerRendererAccessor) this.equipmentRenderer).layerTextureLookup().apply(new EquipmentLayerRenderer.LayerTextureKey(layerType, layer));
            figura$setupArmorPartAnimation(armorModel, renderState, modelPart);
            nodeCollector.order(order[0]++).submitModelPart(modelPart, poseStack, RenderTypes.armorCutoutNoCull(texture), light, OverlayTexture.NO_OVERLAY, null, color, null);

            if (hasFoil) {
                figura$setupArmorPartAnimation(armorModel, renderState, modelPart);
                nodeCollector.order(order[0]++).submitModelPart(modelPart, poseStack, RenderTypes.armorEntityGlint(), light, OverlayTexture.NO_OVERLAY, null, color, null);
                hasFoil = false;
            }
        }

        ArmorTrim trim = itemStack.get(DataComponents.TRIM);
        if (trim == null)
            return;

        TextureAtlasSprite trimSprite = ((EquipmentLayerRendererAccessor) this.equipmentRenderer).trimSpriteLookup()
                .apply(new EquipmentLayerRenderer.TrimSpriteKey(trim, layerType, assetId));
        RenderType renderType = Sheets.armorTrimsSheet(trim.pattern().value().decal());
        figura$setupArmorPartAnimation(armorModel, renderState, modelPart);
        nodeCollector.order(order[0]++).submitModelPart(modelPart, poseStack, renderType, light, OverlayTexture.NO_OVERLAY, trimSprite, -1, null);
    }

    @Unique
    private void figura$setupArmorPartAnimation(A armorModel, S renderState, ModelPart modelPart) {
        ((FiguraSubmitCallBackExtension) (Object) modelPart).figura$addPreRenderingCallback((multiBufferSource, stack) -> {
            armorModel.setupAnim(renderState);
            return true;
        });
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
