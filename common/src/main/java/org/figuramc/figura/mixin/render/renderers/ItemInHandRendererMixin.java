package org.figuramc.figura.mixin.render.renderers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractSkullBlock;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.ducks.FiguraItemStackRenderStateExtension;
import org.figuramc.figura.ducks.FiguraSubmitCallBackExtension;
import org.figuramc.figura.ducks.NodeCollectorExtension;
import org.figuramc.figura.ducks.SkullBlockRendererAccessor;
import org.figuramc.figura.lua.api.vanilla_model.VanillaModelPart;
import org.figuramc.figura.lua.api.world.ItemStackAPI;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow private ItemStack mainHandItem;

    @Shadow
    protected abstract void renderPlayerArm(PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light, float equipProgress, float swingProgress, HumanoidArm arm);

    @Unique Avatar avatar;
    @Unique private boolean figura$hideFirstPersonItem;
    @Unique private ItemDisplayContext figura$firstPersonItemDisplayContext;

    // apparently hands are basically still immediate mode, wow thanks game...
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void onRenderHandsWithItems(float tickDelta, PoseStack matrices, SubmitNodeCollector submitNodeCollector, LocalPlayer player, int light, CallbackInfo ci) {
        avatar = AvatarManager.getAvatarForPlayer(player.getUUID());
        if (avatar == null)
            return;

        FiguraMod.pushProfiler(FiguraMod.MOD_ID);
        FiguraMod.pushProfiler(avatar);
        FiguraMod.pushProfiler("renderEvent");
        avatar.renderMode = EntityRenderMode.FIRST_PERSON;
        avatar.renderEvent(tickDelta, new FiguraMat4().set(matrices.last().pose()));
        FiguraMod.popProfiler(3);
    }

    @Inject(method = "renderHandsWithItems", at = @At(value = "RETURN"))
    private void afterRenderHandsWithItems(float tickDelta, PoseStack matrices, SubmitNodeCollector submitNodeCollector, LocalPlayer player, int light, CallbackInfo ci) {
        if (avatar == null)
            return;

        Avatar localAvatar = avatar;
        FiguraMat4 poseMatrix = new FiguraMat4().set(matrices.last().pose());
        ((NodeCollectorExtension) submitNodeCollector).submitFiguraModel(localAvatar, null, (playerAvatar, state, bufferSource) -> {
            FiguraMod.pushProfiler(FiguraMod.MOD_ID);
            FiguraMod.pushProfiler(playerAvatar);
            FiguraMod.pushProfiler("postRenderEvent");
            playerAvatar.postRenderEvent(tickDelta, poseMatrix);
            FiguraMod.popProfiler(3);
            return null;
        });
        avatar = null;

    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void renderArmWithItem(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light, CallbackInfo ci) {
        figura$hideFirstPersonItem = false;
        figura$firstPersonItemDisplayContext = null;

        if (player.isScoping() || avatar == null || avatar.luaRuntime == null)
            return;

        boolean main = hand ==InteractionHand.MAIN_HAND;
        HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        figura$firstPersonItemDisplayContext = arm == HumanoidArm.LEFT ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        Boolean armVisible = arm == HumanoidArm.LEFT ? avatar.luaRuntime.renderer.renderLeftArm : avatar.luaRuntime.renderer.renderRightArm;

        boolean willRenderItem = !item.isEmpty();
        boolean willRenderArm = (!willRenderItem && main) || item.is(Items.FILLED_MAP) || (!willRenderItem && this.mainHandItem.is(Items.FILLED_MAP));

        // hide arm
        if (willRenderArm && !willRenderItem && armVisible != null && !armVisible) {
            ci.cancel();
            return;
        }
        // render arm
        if (!willRenderArm && !player.isInvisible() && armVisible != null && armVisible) {
            matrices.pushPose();
            this.renderPlayerArm(matrices, submitNodeCollector, light, equipProgress, swingProgress, arm);
            matrices.popPose();
        }

        // hide item
        VanillaModelPart part = arm == HumanoidArm.LEFT ? avatar.luaRuntime.vanilla_model.LEFT_ITEM : avatar.luaRuntime.vanilla_model.RIGHT_ITEM;
        if (willRenderItem && !part.checkVisible()) {
            figura$hideFirstPersonItem = true;
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void clearFirstPersonItemState(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light, CallbackInfo ci) {
        figura$hideFirstPersonItem = false;
        figura$firstPersonItemDisplayContext = null;
        SkullBlockRendererAccessor.clear();
    }

    @WrapOperation(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void renderFirstPersonItemReplacement(ItemStackRenderState instance, PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light, int overlay, int outlineColor, Operation<Void> original, @Local(argsOnly = true) LivingEntity entity, @Local(argsOnly = true) ItemStack stack) {
        try {
            FiguraItemStackRenderStateExtension extension = (FiguraItemStackRenderStateExtension) instance;
            ItemStack figuraStack = extension.figura$getItemStack();
            if (figuraStack == null)
                figuraStack = stack;

            ItemTransform transform = extension.figura$getItemTransform();
            Avatar localAvatar = avatar != null ? avatar : AvatarManager.getAvatar(entity);
            boolean skullItem = figura$isSkullItem(figuraStack);
            boolean renderedReplacement = figura$renderFirstPersonItemReplacement(
                    localAvatar,
                    figuraStack,
                    extension.figura$getDisplayContext(),
                    transform,
                    matrices,
                    submitNodeCollector,
                    light,
                    overlay,
                    !skullItem,
                    skullItem ? null : (FiguraSubmitCallBackExtension)(Object)instance
            );
            if (!skullItem && renderedReplacement)
                original.call(instance, matrices, submitNodeCollector, light, overlay, outlineColor);
            else if (!renderedReplacement && !figura$hideFirstPersonItem)
                original.call(instance, matrices, submitNodeCollector, light, overlay, outlineColor);
        } finally {
            SkullBlockRendererAccessor.clear();
        }
    }

    @Inject(method = "renderMap(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void renderFirstPersonMapReplacement(PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light, ItemStack stack, CallbackInfo ci) {
        ItemDisplayContext context = figura$firstPersonItemDisplayContext != null ? figura$firstPersonItemDisplayContext : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean renderedReplacement = figura$renderFirstPersonItemReplacement(avatar, stack, context, ItemTransform.NO_TRANSFORM, matrices, submitNodeCollector, light, 0, false, null);
        if (renderedReplacement || figura$hideFirstPersonItem)
            ci.cancel();
    }

    @Unique
    private boolean figura$renderFirstPersonItemReplacement(Avatar localAvatar, ItemStack itemStack, ItemDisplayContext displayContext, ItemTransform transform, PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light, int overlay, boolean itemSubmitOwned, FiguraSubmitCallBackExtension submitCallbacks) {
        return localAvatar != null && itemStack != null && localAvatar.itemRenderEvent(
                ItemStackAPI.verify(itemStack),
                displayContext.name(),
                FiguraVec3.fromVec3f(transform.translation()),
                FiguraVec3.of(transform.rotation().z(), transform.rotation().y(), transform.rotation().x()),
                FiguraVec3.fromVec3f(transform.scale()),
                displayContext.leftHand(),
                matrices,
                submitNodeCollector,
                light,
                overlay,
                itemSubmitOwned,
                submitCallbacks
        );
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext itemDisplayContext, PoseStack matrices, SubmitNodeCollector submitNodeCollector, int light, CallbackInfo ci) {
        if (figura$isSkullItem(stack)) {
            SkullBlockRendererAccessor.clear();
            SkullBlockRendererAccessor.setEntity(entity);
            SkullBlockRendererAccessor.setRenderMode(switch (itemDisplayContext) {
                case FIRST_PERSON_LEFT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.FIRST_PERSON_LEFT_HAND;
                case FIRST_PERSON_RIGHT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.FIRST_PERSON_RIGHT_HAND;
                case THIRD_PERSON_LEFT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_LEFT_HAND;
                case THIRD_PERSON_RIGHT_HAND -> SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_RIGHT_HAND;
                default -> itemDisplayContext.leftHand() ? SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_LEFT_HAND // should never happen
                        : SkullBlockRendererAccessor.SkullRenderMode.THIRD_PERSON_RIGHT_HAND; 
            });
        }
    }

    @Unique
    private boolean figura$isSkullItem(ItemStack stack) {
        return stack != null && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof AbstractSkullBlock;
    }
}
