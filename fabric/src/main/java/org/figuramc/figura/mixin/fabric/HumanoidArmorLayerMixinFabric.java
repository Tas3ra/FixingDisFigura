package org.figuramc.figura.mixin.fabric;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.compat.GeckoLibCompat;
import org.figuramc.figura.ducks.FiguraEntityRenderStateExtension;
import org.figuramc.figura.ducks.FiguraSubmitCallBackExtension;
import org.figuramc.figura.lua.api.vanilla_model.VanillaPart;
import org.figuramc.figura.mixin.render.layers.EquipmentLayerRendererAccessor;
import org.figuramc.figura.mixin.render.layers.HumanoidArmorLayerAccessor;
import org.figuramc.figura.model.ParentType;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.FiguraArmorPartRenderer;
import org.figuramc.figura.utils.RenderUtils;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(value = HumanoidArmorLayer.class, priority = 900)
public abstract class HumanoidArmorLayerMixinFabric<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> extends RenderLayer<S, M> implements HumanoidArmorLayerAccessor<S, M, A> {
    @Shadow @Final private EquipmentLayerRenderer equipmentRenderer;

    @Shadow protected abstract A getArmorModel(S humanoidRenderState, EquipmentSlot equipmentSlot);

    @Shadow
    protected abstract void renderArmorPiece(PoseStack matrices, SubmitNodeCollector submitNodeCollector, ItemStack stack, EquipmentSlot armorSlot, int light, S state);

    @Unique
    private Avatar figura$avatar;

    public HumanoidArmorLayerMixinFabric(RenderLayerParent<S, M> context) {
        super(context);
    }

    @Inject(at = @At(value = "HEAD"), method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V")
    public void setAvatar(PoseStack matrices, SubmitNodeCollector submitNodeCollector, int i, S humanoidRenderState, float f, float g, CallbackInfo ci) {
        figura$avatar = AvatarManager.getAvatar(humanoidRenderState);
    }

    @Inject(at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 3, target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V"), method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V")
    public void onRenderEnd(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S humanoidRenderState, float f, float g, CallbackInfo ci) {
        if (figura$avatar == null) return;

        figura$tryRenderArmorPart(EquipmentSlot.HEAD,  this::figura$helmetRenderer, poseStack, humanoidRenderState, submitNodeCollector, i, ParentType.HelmetPivot);
        figura$tryRenderArmorPart(EquipmentSlot.CHEST, this::figura$chestplateRenderer, poseStack, humanoidRenderState, submitNodeCollector, i, ParentType.LeftShoulderPivot, ParentType.ChestplatePivot, ParentType.RightShoulderPivot);
        figura$tryRenderArmorPart(EquipmentSlot.LEGS,  this::figura$leggingsRenderer, poseStack, humanoidRenderState, submitNodeCollector, i, ParentType.LeftLeggingPivot, ParentType.RightLeggingPivot, ParentType.LeggingsPivot);
        figura$tryRenderArmorPart(EquipmentSlot.FEET,  this::figura$bootsRenderer, poseStack, humanoidRenderState, submitNodeCollector, i, ParentType.LeftBootPivot, ParentType.RightBootPivot);
    }

    @Inject(at = @At("HEAD"), method = "renderArmorPiece", cancellable = true)
    public void cancelHiddenArmorPiece(PoseStack matrices, SubmitNodeCollector submitNodeCollector, ItemStack stack, EquipmentSlot equipmentSlot, int light, S state, CallbackInfo ci) {
        if (figura$avatar != null && !figura$slotHasVisibleParts(figura$avatar, equipmentSlot))
            ci.cancel();
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;usesInnerModel(Lnet/minecraft/world/entity/EquipmentSlot;)Z"), method = "renderArmorPiece")
    public void addFiguraCallbacks(PoseStack matrices, SubmitNodeCollector submitNodeCollector, ItemStack stack, EquipmentSlot equipmentSlot, int light, S state, CallbackInfo ci) {
        if (figura$avatar == null) return;
        Avatar localAvatar = figura$avatar;
        A humanoidModel = this.getArmorModel(state, equipmentSlot);

        var extension = (FiguraSubmitCallBackExtension)humanoidModel;
        extension.figura$addPreRenderingCallback((poseStack, nodeCollector) -> {
            VanillaPart part = RenderUtils.partFromSlot(localAvatar, equipmentSlot);
            if (part != null) {
                part.save(humanoidModel);
                part.preTransform(humanoidModel);
                part.posTransform(humanoidModel);
            }
            return true;
        });

        extension.figura$addPostRenderingCallback(() -> {
            VanillaPart part = RenderUtils.partFromSlot(localAvatar, equipmentSlot);
            if (part != null)
                part.restore(humanoidModel);
        });
    }


    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;usesInnerModel(Lnet/minecraft/world/entity/EquipmentSlot;)Z", shift = At.Shift.AFTER), method = "renderArmorPiece")
    public void renderArmorPieceHijack(PoseStack matrices, SubmitNodeCollector submitNodeCollector, ItemStack stack, EquipmentSlot armorSlot, int light, S state, CallbackInfo ci, @Local A humanoidModel) {
        figura$setPartVisibility(humanoidModel, armorSlot, figura$avatar, stack);
    }

    @Unique
    private void figura$tryRenderArmorPart(EquipmentSlot slot, FiguraArmorPartRenderer<S, A> renderer, PoseStack vanillaPoseStack, S state, SubmitNodeCollector submitNodeCollector, int light, ParentType... parentTypes) {
        if (slot == null) return; // ?
        Integer id = state instanceof AvatarRenderState playerRenderState ? playerRenderState.id : ((FiguraEntityRenderStateExtension)state).figura$getEntityId();
        if (id == null) return;
        if (Minecraft.getInstance().level == null) return;

        Entity entity = Minecraft.getInstance().level.getEntity(id);
        if (!(entity instanceof LivingEntity livingEntity)) return;
        ItemStack itemStack = livingEntity.getItemBySlot(slot);

        // Make sure the item in the equipment slot is actually a piece of armor
        if (figura$isArmorForSlot(itemStack, slot)) {
            if (!figura$armorCanUsePivots(itemStack, slot))
                return;

            A armorModel = getArmorModel(state, slot);
            var armorPose = RenderUtils.captureModelState(armorModel);

            try {
                // Bones have to be their defaults to prevent issues with clipping
                armorModel.body.xRot = 0.0f;
                armorModel.rightLeg.z = 0.0f;
                armorModel.leftLeg.z = 0.0f;
                armorModel.rightLeg.y = 12.0f;
                armorModel.leftLeg.y = 12.0f;
                armorModel.head.y = 0.0f;
                armorModel.body.y = 0.0f;
                armorModel.leftArm.y = 2.0f;
                armorModel.rightArm.y = 2.0f;
                armorModel.leftArm.x = 5.0f;
                armorModel.rightArm.x = -5.0f;
                armorModel.leftArm.z = 0.0f;
                armorModel.rightArm.z = 0.0f;

                VanillaPart mainPart = RenderUtils.partFromSlot(figura$avatar, slot);
                int armorEditPermission = figura$avatar.permissions.get(Permissions.VANILLA_MODEL_EDIT);
                if (armorEditPermission == 1 && mainPart != null && !mainPart.checkVisible()) return;

                // Go through each parent type needed to render the current piece of armor
                for (ParentType parentType : parentTypes) {
                    // Skip the part if it's hidden
                    VanillaPart part = figura$armorPartFromPivot(figura$avatar, parentType);
                    if (armorEditPermission == 1 && part != null && !part.checkVisible()) continue;
                    if (armorEditPermission == 1 && figura$willRenderArmorPivot(figura$avatar, itemStack, slot, parentType)) {
                        figura$avatar.pivotPartRender(parentType, stack -> {
                            stack.pushPose();
                            figura$prepareArmorRender(stack);
                            renderer.renderArmorPart(stack, submitNodeCollector, light, armorModel, itemStack, slot, parentType);
                            stack.popPose();
                        });
                    }
                }
            } finally {
                RenderUtils.restoreModelPoseState(armorModel, armorPose);
            }
        }

    }

    // Prepare the transformations for rendering armor on the avatar
    @Unique
    private void figura$prepareArmorRender(PoseStack stack) {
        stack.scale(16, 16, 16);
        stack.mulPose(Axis.XP.rotationDegrees(180f));
        stack.mulPose(Axis.YP.rotationDegrees(180f));
    }

    @Unique
    private void figura$helmetRenderer(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, A model, ItemStack itemStack, EquipmentSlot armorSlot, ParentType parentType) {
        if (parentType == ParentType.HelmetPivot) {
            if (figura$armorPartVisible(figura$avatar, figura$avatar != null && figura$avatar.luaRuntime != null ? figura$avatar.luaRuntime.vanilla_model.HELMET_HEAD : null))
                figura$renderArmorPart(model.head, poseStack, submitNodeCollector, light, itemStack, armorSlot);
            if (figura$armorPartVisible(figura$avatar, figura$avatar != null && figura$avatar.luaRuntime != null ? figura$avatar.luaRuntime.vanilla_model.HELMET_HAT : null))
                figura$renderArmorPart(model.hat, poseStack, submitNodeCollector, light, itemStack, armorSlot);
        }
    }

    @Unique
    private void figura$chestplateRenderer(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, A model, ItemStack itemStack, EquipmentSlot armorSlot, ParentType parentType) {
        if (parentType == ParentType.ChestplatePivot) {
            if (!figura$armorPartVisible(figura$avatar, figura$avatar != null && figura$avatar.luaRuntime != null ? figura$avatar.luaRuntime.vanilla_model.CHESTPLATE_BODY : null))
                return;
            figura$renderArmorPart(model.body, poseStack, submitNodeCollector, light, itemStack, armorSlot);
        }

        if (parentType == ParentType.LeftShoulderPivot) {
            if (!figura$armorPartVisible(figura$avatar, figura$avatar != null && figura$avatar.luaRuntime != null ? figura$avatar.luaRuntime.vanilla_model.CHESTPLATE_LEFT_ARM : null))
                return;
            poseStack.pushPose();
            poseStack.translate(-6 / 16f, 0f, 0f);
            figura$renderArmorPart(model.leftArm, poseStack, submitNodeCollector, light, itemStack, armorSlot);
            poseStack.popPose();
        }

        if (parentType == ParentType.RightShoulderPivot) {
            if (!figura$armorPartVisible(figura$avatar, figura$avatar != null && figura$avatar.luaRuntime != null ? figura$avatar.luaRuntime.vanilla_model.CHESTPLATE_RIGHT_ARM : null))
                return;
            poseStack.pushPose();
            poseStack.translate(6 / 16f, 0f, 0f);
            figura$renderArmorPart(model.rightArm, poseStack, submitNodeCollector, light, itemStack, armorSlot);
            poseStack.popPose();
        }
    }

    @Unique
    private void figura$leggingsRenderer(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, A model, ItemStack itemStack, EquipmentSlot armorSlot, ParentType parentType) {
        if (parentType == ParentType.LeggingsPivot) {
            if (!figura$armorPartVisible(figura$avatar, figura$avatar != null && figura$avatar.luaRuntime != null ? figura$avatar.luaRuntime.vanilla_model.LEGGINGS_BODY : null))
                return;
            poseStack.pushPose();
            poseStack.translate(0, -12 / 16f, 0);
            figura$renderArmorPart(model.body, poseStack, submitNodeCollector, light, itemStack, armorSlot);
            poseStack.popPose();
        }

        if (parentType == ParentType.LeftLeggingPivot) {
            if (!figura$armorPartVisible(figura$avatar, figura$avatar != null && figura$avatar.luaRuntime != null ? figura$avatar.luaRuntime.vanilla_model.LEGGINGS_LEFT_LEG : null))
                return;
            poseStack.pushPose();
            poseStack.translate(-2 / 16f, -12 / 16f, 0);
            figura$renderArmorPart(model.leftLeg, poseStack, submitNodeCollector, light, itemStack, armorSlot);
            poseStack.popPose();
        }

        if (parentType == ParentType.RightLeggingPivot) {
            if (!figura$armorPartVisible(figura$avatar, figura$avatar != null && figura$avatar.luaRuntime != null ? figura$avatar.luaRuntime.vanilla_model.LEGGINGS_RIGHT_LEG : null))
                return;
            poseStack.pushPose();
            poseStack.translate(2 / 16f, -12 / 16f, 0);
            figura$renderArmorPart(model.rightLeg, poseStack, submitNodeCollector, light, itemStack, armorSlot);
            poseStack.popPose();
        }
    }

    @Unique
    private void figura$bootsRenderer(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, A model, ItemStack itemStack, EquipmentSlot armorSlot, ParentType parentType) {
        if (parentType == ParentType.LeftBootPivot) {
            if (!figura$armorPartVisible(figura$avatar, figura$avatar != null && figura$avatar.luaRuntime != null ? figura$avatar.luaRuntime.vanilla_model.BOOTS_LEFT_LEG : null))
                return;
            poseStack.pushPose();
            poseStack.translate(-2 / 16f, -24 / 16f, 0);
            figura$renderArmorPart(model.leftLeg, poseStack, submitNodeCollector, light, itemStack, armorSlot);
            poseStack.popPose();
        }

        if (parentType == ParentType.RightBootPivot) {
            if (!figura$armorPartVisible(figura$avatar, figura$avatar != null && figura$avatar.luaRuntime != null ? figura$avatar.luaRuntime.vanilla_model.BOOTS_RIGHT_LEG : null))
                return;
            poseStack.pushPose();
            poseStack.translate(2 / 16f, -24 / 16f, 0);
            figura$renderArmorPart(model.rightLeg, poseStack, submitNodeCollector, light, itemStack, armorSlot);
            poseStack.popPose();
        }
    }


    // Similar to vanilla's renderArmorModel, but it renders each part individually, instead of the whole model at once.
    // Could be optimized by calculating the tint, overlays, and trims beforehand instead of re-calculating for each ModelPart, but it's not super important.
    @Unique
    private void figura$renderArmorPart(ModelPart modelPart, PoseStack poseStack, SubmitNodeCollector nodeCollector, int light, ItemStack itemStack, EquipmentSlot armorSlot) {
        boolean hasGlint = itemStack.hasFoil();

        PartPose originalPose = modelPart.storePose();
        modelPart.visible = true;
        modelPart.xRot = 0;
        modelPart.yRot = 0;
        modelPart.zRot = 0;
        PartPose armorPartPose = modelPart.storePose();
        modelPart.loadPose(originalPose);

        EquipmentClientInfo.LayerType layerType = this.usesInnerModel(armorSlot) ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;
        Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);

        if (equippable == null)
            return;

        Optional<ResourceKey<EquipmentAsset>> location = equippable.assetId();
        if (location.isEmpty())
            return;

        List<EquipmentClientInfo.Layer> list = ((EquipmentLayerRendererAccessor)this.equipmentRenderer).figura$getAssetsManager().get(location.get()).getLayers(layerType);

        int i = itemStack.is(ItemTags.DYEABLE) ? DyedItemColor.getOrDefault(itemStack, -6265536) : -1;
        int order = 0;

        for(EquipmentClientInfo.Layer layer : list) {
            int k = EquipmentLayerRendererAccessor.getColorForLayer(layer, i);

            if (k != 0) {
                Identifier normalArmorResource = ((EquipmentLayerRendererAccessor)this.equipmentRenderer).layerTextureLookup().apply(new EquipmentLayerRenderer.LayerTextureKey(layerType, layer));
                figura$submitArmorModelPart(nodeCollector, order++, modelPart, armorPartPose, poseStack, RenderTypes.armorCutoutNoCull(normalArmorResource), light, null, k);
                if (hasGlint)
                    figura$submitArmorModelPart(nodeCollector, order++, modelPart, armorPartPose, poseStack, RenderTypes.armorEntityGlint(), light, null, -1);
                hasGlint = false;
            }
        }

        ArmorTrim trim = itemStack.get(DataComponents.TRIM);
        if (trim != null) {
            TextureAtlasSprite textureAtlasSprite = ((EquipmentLayerRendererAccessor)equipmentRenderer).trimSpriteLookup()
                    .apply(new EquipmentLayerRenderer.TrimSpriteKey(trim, layerType, location.get()));

            RenderType renderType = Sheets.armorTrimsSheet(trim.pattern().value().decal());
            figura$submitArmorModelPart(nodeCollector, order++, modelPart, armorPartPose, poseStack, renderType, light, textureAtlasSprite, -1);
        }

        if (hasGlint) {
            figura$submitArmorModelPart(nodeCollector, order, modelPart, armorPartPose, poseStack, RenderTypes.armorEntityGlint(), light, null, -1);
        }
    }

    @Unique
    private void figura$submitArmorModelPart(SubmitNodeCollector nodeCollector, int order, ModelPart modelPart, PartPose renderPose, PoseStack poseStack, RenderType renderType, int light, TextureAtlasSprite sprite, int color) {
        FiguraSubmitCallBackExtension callbacks = (FiguraSubmitCallBackExtension)(Object)modelPart;
        PartPose[] previousPose = new PartPose[1];

        callbacks.figura$addPreRenderingCallback((bufferSource, stack) -> {
            previousPose[0] = modelPart.storePose();
            modelPart.loadPose(renderPose);
            return true;
        });
        callbacks.figura$addPostRenderingCallback(() -> {
            if (previousPose[0] != null)
                modelPart.loadPose(previousPose[0]);
        });

        nodeCollector.order(order).submitModelPart(modelPart, poseStack, renderType, light, OverlayTexture.NO_OVERLAY, sprite, color, null);
    }

    @Unique
    protected void figura$setPartVisibility(A bipedModel, EquipmentSlot slot, Avatar avatar, ItemStack itemStack) {
        bipedModel.setAllVisible(false);
        switch (slot) {
            case HEAD:
                bipedModel.head.visible = figura$vanillaArmorPartVisible(avatar, itemStack, slot, ParentType.HelmetPivot, avatar != null && avatar.luaRuntime != null ? avatar.luaRuntime.vanilla_model.HELMET_HEAD : null);
                bipedModel.hat.visible = figura$vanillaArmorPartVisible(avatar, itemStack, slot, ParentType.HelmetPivot, avatar != null && avatar.luaRuntime != null ? avatar.luaRuntime.vanilla_model.HELMET_HAT : null);
                break;
            case CHEST:
                bipedModel.body.visible = figura$vanillaArmorPartVisible(avatar, itemStack, slot, ParentType.ChestplatePivot, avatar != null && avatar.luaRuntime != null ? avatar.luaRuntime.vanilla_model.CHESTPLATE_BODY : null);
                bipedModel.rightArm.visible = figura$vanillaArmorPartVisible(avatar, itemStack, slot, ParentType.RightShoulderPivot, avatar != null && avatar.luaRuntime != null ? avatar.luaRuntime.vanilla_model.CHESTPLATE_RIGHT_ARM : null);
                bipedModel.leftArm.visible = figura$vanillaArmorPartVisible(avatar, itemStack, slot, ParentType.LeftShoulderPivot, avatar != null && avatar.luaRuntime != null ? avatar.luaRuntime.vanilla_model.CHESTPLATE_LEFT_ARM : null);
                break;
            case LEGS:
                bipedModel.body.visible = figura$vanillaArmorPartVisible(avatar, itemStack, slot, ParentType.LeggingsPivot, avatar != null && avatar.luaRuntime != null ? avatar.luaRuntime.vanilla_model.LEGGINGS_BODY : null);
                bipedModel.rightLeg.visible = figura$vanillaArmorPartVisible(avatar, itemStack, slot, ParentType.RightLeggingPivot, avatar != null && avatar.luaRuntime != null ? avatar.luaRuntime.vanilla_model.LEGGINGS_RIGHT_LEG : null);
                bipedModel.leftLeg.visible = figura$vanillaArmorPartVisible(avatar, itemStack, slot, ParentType.LeftLeggingPivot, avatar != null && avatar.luaRuntime != null ? avatar.luaRuntime.vanilla_model.LEGGINGS_LEFT_LEG : null);
                break;
            case FEET:
                bipedModel.rightLeg.visible = figura$vanillaArmorPartVisible(avatar, itemStack, slot, ParentType.RightBootPivot, avatar != null && avatar.luaRuntime != null ? avatar.luaRuntime.vanilla_model.BOOTS_RIGHT_LEG : null);
                bipedModel.leftLeg.visible = figura$vanillaArmorPartVisible(avatar, itemStack, slot, ParentType.LeftBootPivot, avatar != null && avatar.luaRuntime != null ? avatar.luaRuntime.vanilla_model.BOOTS_LEFT_LEG : null);
        }
    }

    @Unique
    private boolean figura$vanillaArmorPartVisible(Avatar avatar, ItemStack itemStack, EquipmentSlot slot, ParentType pivot, VanillaPart part) {
        return figura$armorPartVisible(avatar, part) && !figura$willRenderArmorPivot(avatar, itemStack, slot, pivot);
    }

    @Unique
    private boolean figura$willRenderArmorPivot(Avatar avatar, ItemStack itemStack, EquipmentSlot slot, ParentType pivot) {
        return avatar != null
                && avatar.permissions.get(Permissions.VANILLA_MODEL_EDIT) == 1
                && figura$armorCanUsePivots(itemStack, slot)
                && avatar.hasPivotPart(pivot);
    }

    @Unique
    private boolean figura$armorCanUsePivots(ItemStack itemStack, EquipmentSlot slot) {
        if (!figura$isArmorForSlot(itemStack, slot))
            return false;

        EquipmentClientInfo.LayerType layerType = slot == EquipmentSlot.LEGS ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;
        return !GeckoLibCompat.armorHasCustomModel(itemStack, slot, layerType);
    }

    @Unique
    private boolean figura$isArmorForSlot(ItemStack itemStack, EquipmentSlot slot) {
        return itemStack != null && !itemStack.isEmpty()
                && itemStack.getItem() instanceof Item armorItem
                && armorItem.components().has(DataComponents.EQUIPPABLE)
                && armorItem.components().get(DataComponents.EQUIPPABLE).slot() == slot
                && armorItem.components().has(DataComponents.ATTRIBUTE_MODIFIERS)
                && armorItem.components().get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream().anyMatch(attribute -> attribute.attribute() == Attributes.ARMOR);
    }

    @Unique
    private boolean figura$armorPartVisible(Avatar avatar, VanillaPart part) {
        return avatar == null || avatar.permissions.get(Permissions.VANILLA_MODEL_EDIT) != 1 || part == null || part.checkVisible();
    }

    @Unique
    private boolean figura$slotHasVisibleParts(Avatar avatar, EquipmentSlot slot) {
        if (avatar.permissions.get(Permissions.VANILLA_MODEL_EDIT) != 1 || avatar.luaRuntime == null)
            return true;

        VanillaPart slotPart = RenderUtils.partFromSlot(avatar, slot);
        if (slotPart != null && !slotPart.checkVisible())
            return false;

        return switch (slot) {
            case HEAD -> avatar.luaRuntime.vanilla_model.HELMET_HEAD.checkVisible() || avatar.luaRuntime.vanilla_model.HELMET_HAT.checkVisible();
            case CHEST -> avatar.luaRuntime.vanilla_model.CHESTPLATE_BODY.checkVisible()
                    || avatar.luaRuntime.vanilla_model.CHESTPLATE_LEFT_ARM.checkVisible()
                    || avatar.luaRuntime.vanilla_model.CHESTPLATE_RIGHT_ARM.checkVisible();
            case LEGS -> avatar.luaRuntime.vanilla_model.LEGGINGS_BODY.checkVisible()
                    || avatar.luaRuntime.vanilla_model.LEGGINGS_LEFT_LEG.checkVisible()
                    || avatar.luaRuntime.vanilla_model.LEGGINGS_RIGHT_LEG.checkVisible();
            case FEET -> avatar.luaRuntime.vanilla_model.BOOTS_LEFT_LEG.checkVisible()
                    || avatar.luaRuntime.vanilla_model.BOOTS_RIGHT_LEG.checkVisible();
            default -> true;
        };
    }

    @Unique
    private VanillaPart figura$armorPartFromPivot(Avatar avatar, ParentType parentType) {
        if (avatar.luaRuntime == null)
            return null;

        return switch (parentType) {
            case HelmetPivot -> avatar.luaRuntime.vanilla_model.HELMET;
            case ChestplatePivot -> avatar.luaRuntime.vanilla_model.CHESTPLATE_BODY;
            case LeftShoulderPivot -> avatar.luaRuntime.vanilla_model.CHESTPLATE_LEFT_ARM;
            case RightShoulderPivot -> avatar.luaRuntime.vanilla_model.CHESTPLATE_RIGHT_ARM;
            case LeggingsPivot -> avatar.luaRuntime.vanilla_model.LEGGINGS_BODY;
            case LeftLeggingPivot -> avatar.luaRuntime.vanilla_model.LEGGINGS_LEFT_LEG;
            case RightLeggingPivot -> avatar.luaRuntime.vanilla_model.LEGGINGS_RIGHT_LEG;
            case LeftBootPivot -> avatar.luaRuntime.vanilla_model.BOOTS_LEFT_LEG;
            case RightBootPivot -> avatar.luaRuntime.vanilla_model.BOOTS_RIGHT_LEG;
            default -> RenderUtils.pivotToPart(avatar, parentType);
        };
    }
}
