package org.figuramc.figura.mixin.render.layers.items;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractSkullBlock;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.ducks.FiguraItemStackRenderStateExtension;
import org.figuramc.figura.ducks.FiguraSubmitCallBackExtension;
import org.figuramc.figura.ducks.SkullBlockRendererAccessor;
import org.figuramc.figura.lua.api.world.ItemStackAPI;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.model.ParentType;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.utils.RenderUtils;
import org.figuramc.figura.utils.ui.UIHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin<S extends ArmedEntityRenderState, M extends EntityModel<S> & ArmedModel> extends RenderLayer<S, M> {

    @Unique
    private Avatar av;

    public ItemInHandLayerMixin(RenderLayerParent<S, M> renderLayerParent) {
        super(renderLayerParent);
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    protected void renderArmWithItemInject(S state, ItemStackRenderState itemStackRenderState, ItemStack itemStack, HumanoidArm humanoidArm, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, CallbackInfo ci) {
        av = AvatarManager.getAvatar(state);

        if (itemStackRenderState.isEmpty()) {
            av = null;
            return;
        }

        boolean left = humanoidArm == HumanoidArm.LEFT;

        if (!RenderUtils.renderArmItem(av, left, ci)) {
            av = null;
            return;
        }

        // pivot part
        if (av.pivotPartRender(left ? ParentType.LeftItemPivot : ParentType.RightItemPivot, stack -> {
            try {
                final float s = 16f;
                stack.scale(s, s, s);
                stack.mulPose(Axis.XP.rotationDegrees(-90f));
                // Must do this bs manually
                ItemStack figuraStack = ((FiguraItemStackRenderStateExtension)itemStackRenderState).figura$getItemStack();
                if (figuraStack == null)
                    figuraStack = itemStack;
                if (figuraStack != null && figuraStack.getItem() instanceof BlockItem bl && bl.getBlock() instanceof AbstractSkullBlock) {
                    Entity entity = AvatarManager.getEntity(state);
                    SkullBlockRendererAccessor.clear();
                    SkullBlockRendererAccessor.setEntity(entity);
                    figura$setSkullEntityRenderMode(av);
                    SkullBlockRendererAccessor.setRenderMode(switch (((FiguraItemStackRenderStateExtension) itemStackRenderState).figura$getDisplayContext()) {
                        case FIRST_PERSON_LEFT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.FIRST_PERSON_LEFT_HAND;
                        case FIRST_PERSON_RIGHT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.FIRST_PERSON_RIGHT_HAND;
                        case THIRD_PERSON_LEFT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_LEFT_HAND;
                        case THIRD_PERSON_RIGHT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_RIGHT_HAND;
                        default -> left ? SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_LEFT_HAND // should never happen
                                : SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_RIGHT_HAND;
                    });
                }

                // sorta have to do this manually otherwise itemRenderEvent isn't called
                ItemTransform transform = ((FiguraItemStackRenderStateExtension)itemStackRenderState).figura$getItemTransform();

                if (av != null && figuraStack != null)
                    av.itemRenderEvent(ItemStackAPI.verify(figuraStack),
                            ((FiguraItemStackRenderStateExtension)itemStackRenderState).figura$getDisplayContext().name(), FiguraVec3.fromVec3f(transform.translation()),
                            FiguraVec3.of(transform.rotation().z(), transform.rotation().y(), transform.rotation().x()), FiguraVec3.fromVec3f(transform.scale()),
                            ((FiguraItemStackRenderStateExtension)itemStackRenderState).figura$isLeftHanded(), stack, submitNodeCollector, light, OverlayTexture.NO_OVERLAY, (FiguraSubmitCallBackExtension)(Object)itemStackRenderState);
                itemStackRenderState.submit(stack, submitNodeCollector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            } finally {
                SkullBlockRendererAccessor.clear();
            }
        })) {
            ci.cancel();
            av = null;
        }
    }

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void figuraItemEvent(ItemStackRenderState instance, PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light, int overlay, int outlineColor, Operation<Void> original, @Local(argsOnly = true) S armedState) {
        try {
            ItemStack stack = ((FiguraItemStackRenderStateExtension)instance).figura$getItemStack();
            Entity entity = AvatarManager.getEntity(armedState);
            if (av != null && stack != null && entity != null && stack.getItem() instanceof BlockItem bl && bl.getBlock() instanceof AbstractSkullBlock sk) {
                SkullBlockRendererAccessor.clear();
                SkullBlockRendererAccessor.setEntity(entity);
                figura$setSkullEntityRenderMode(av);
                SkullBlockRendererAccessor.setRenderMode(switch (((FiguraItemStackRenderStateExtension) instance).figura$getDisplayContext()) {
                    case FIRST_PERSON_LEFT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.FIRST_PERSON_LEFT_HAND;
                    case FIRST_PERSON_RIGHT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.FIRST_PERSON_RIGHT_HAND;
                    case THIRD_PERSON_LEFT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_LEFT_HAND;
                    case THIRD_PERSON_RIGHT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_RIGHT_HAND;
                    default -> ((FiguraItemStackRenderStateExtension) instance).figura$isLeftHanded() ? SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_LEFT_HAND // should never happen
                            : SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_RIGHT_HAND;
                });
            }
            ItemTransform transform = ((FiguraItemStackRenderStateExtension)instance).figura$getItemTransform();

            if (av != null && stack != null)
                av.itemRenderEvent(ItemStackAPI.verify(stack), ((FiguraItemStackRenderStateExtension) instance).figura$getDisplayContext().name(),
                        FiguraVec3.fromVec3f(transform.translation()), FiguraVec3.of(transform.rotation().z(), transform.rotation().y(), transform.rotation().x()),
                        FiguraVec3.fromVec3f(transform.scale()), ((FiguraItemStackRenderStateExtension) instance).figura$isLeftHanded(),
                        matrices, submitNodeCollector, light, overlay, (FiguraSubmitCallBackExtension)(Object)instance);
            original.call(instance, matrices, submitNodeCollector, light, overlay, outlineColor);
        } finally {
            SkullBlockRendererAccessor.clear();
        }
    }

    @Unique
    private static void figura$setSkullEntityRenderMode(Avatar wearerAvatar) {
        if (wearerAvatar != null && wearerAvatar.renderMode != EntityRenderMode.OTHER) {
            SkullBlockRendererAccessor.setEntityRenderMode(wearerAvatar.renderMode);
        } else if (UIHelper.paperdoll) {
            SkullBlockRendererAccessor.setEntityRenderMode(EntityRenderMode.MINECRAFT_GUI);
        }
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void clearAvatar(S state, ItemStackRenderState itemStackRenderState, ItemStack itemStack, HumanoidArm humanoidArm, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, CallbackInfo ci) {
        av = null;
        SkullBlockRendererAccessor.clear();
    }
}
