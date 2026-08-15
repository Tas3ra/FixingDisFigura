package org.figuramc.figura.mixin.render.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.ducks.CameraRenderStateExtension;
import org.figuramc.figura.ducks.NameTagFeatureRenderer$StorageExtension;
import org.figuramc.figura.lua.api.nameplate.EntityNameplateCustomization;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.TextUtils;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(NameTagFeatureRenderer.Storage.class)
public class NameTagFeatureRenderer$StorageMixin implements NameTagFeatureRenderer$StorageExtension {

    // i literally have to inject a new submission list for outlined text, *screams into void*
    @Unique
    final List<SubmitNodeStorage.NameTagSubmit> figura$outlineSubmits = new ArrayList<>();

    @Unique
    Avatar figura$avatar;
    @Unique
    EntityNameplateCustomization figura$custom;
    @Unique
    List<Component> figura$textList;

    @Unique
    boolean figura$hasCustomNameplate;
    @Unique
    boolean figura$enabled;

    @Unique
    boolean figura$isRenderingName;

    @Inject(at = @At(value = "HEAD"), method = "add")
    private void setupAvatar(PoseStack poseStack, Vec3 vec3, int i, Component component, boolean bl, int j, double d, CameraRenderState cameraRenderState, CallbackInfo ci) {

        figura$avatar = ((CameraRenderStateExtension)cameraRenderState).figura$getAvatar();
        figura$isRenderingName = ((CameraRenderStateExtension)cameraRenderState).figura$isRenderingNameTag();
        ((CameraRenderStateExtension)cameraRenderState).figura$setAvatar(null);
        ((CameraRenderStateExtension)cameraRenderState).figura$setRenderingNameTag(false);

        if (figura$avatar == null)
            return;

        figura$custom = figura$avatar == null || figura$avatar.luaRuntime == null ? null : figura$avatar.luaRuntime.nameplate.ENTITY;
        figura$hasCustomNameplate = figura$custom != null && figura$avatar.permissions.get(Permissions.NAMEPLATE_EDIT) == 1;
        figura$enabled =  Configs.ENTITY_NAMEPLATE.value > 0 && !AvatarManager.panic && figura$hasCustomNameplate;

        figura$textList = TextUtils.splitLines(component);
    }

    @Inject(at = @At(value = "TAIL"), method = "add")
    private void clearAvatar(PoseStack poseStack, Vec3 vec3, int i, Component component, boolean bl, int j, double d, CameraRenderState cameraRenderState, CallbackInfo ci) {
        figura$avatar = null;
        figura$isRenderingName = false;
        figura$custom = null;
        figura$hasCustomNameplate = false;
        figura$enabled =  false;
        figura$textList = null;
    }

    // Push pivot transformations when the nametag is being pivoted (set to entity height in vanilla)
    @WrapOperation(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"), method = "add")
    private void modifyPivot(PoseStack instance, double x, double y, double z, Operation<Void> original) {
        FiguraVec3 pivot = FiguraVec3.of(x, y, z);
        if (figura$enabled && figura$avatar != null) {
            // pivot
            FiguraMod.pushProfiler("pivot");
            if (figura$hasCustomNameplate && figura$custom.getPivot() != null)
                pivot = figura$custom.getPivot();
        }
        original.call(instance, pivot.x, pivot.y, pivot.z);
    }

    // Push position transformations after the nametag has been rotated to face the camera
    @Inject(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V", shift = At.Shift.AFTER), method = "add")
    private void modifyPos(PoseStack matrices, Vec3 vec3, int i, Component component, boolean bl, int j, double d, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (figura$enabled && figura$avatar != null) {
            // pos
            FiguraMod.popPushProfiler("position");
            if (figura$hasCustomNameplate && figura$custom.getPos() != null) {
                FiguraVec3 pos = figura$custom.getPos();
                matrices.translate(pos.x, pos.y, pos.z);
            }
        }
    }

    // push the scale when vanilla does so
    @WrapOperation(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"), method = "add")
    private void modifyScale(PoseStack instance, float x, float y, float z, Operation<Void> original) {
        FiguraVec3 scaleVec = FiguraVec3.of(x, y, z);
        if (figura$enabled && figura$avatar != null) {
            // scale
            FiguraMod.popPushProfiler("scale");
            if (figura$hasCustomNameplate && figura$custom.getScale() != null)
                scaleVec.multiply(figura$custom.getScale());
        }
        original.call(instance, (float) scaleVec.x, (float) scaleVec.y, (float) scaleVec.z);
    }



    @Inject(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;pose()Lorg/joml/Matrix4f;"), method = "add")
    private void setShadowMatrix(PoseStack matrices, Vec3 vec3, int i, Component component, boolean bl, int j, double d, CameraRenderState cameraRenderState, CallbackInfo ci, @Share("textMatrix") LocalRef<Matrix4f> textMatrix) {
        if (!figura$enabled || figura$avatar == null || !figura$hasCustomNameplate || !figura$custom.shadow)
            return;

        textMatrix.set(matrices.last().pose());
        if (figura$enabled && figura$avatar != null && figura$hasCustomNameplate && figura$custom.shadow) {
            matrices.pushPose();
            textMatrix.set(matrices.last().pose());
            matrices.popPose();
        }
    }


    @WrapOperation(method = "add",
            at = @At(value = "INVOKE", target =
                    "Ljava/util/List;add(Ljava/lang/Object;)Z"
            , ordinal = 1))
    private <E> boolean drawWithColor(List<E> instance, E e, Operation<Boolean> original) {
        SubmitNodeStorage.NameTagSubmit submit = (SubmitNodeStorage.NameTagSubmit) e;

        if (figura$shouldCustomizeNameplate()) {
            int light = figura$custom.light != null ? figura$custom.light : submit.lightCoords();
            int backgroundColor = figura$custom.background != null ? figura$custom.background : submit.backgroundColor();
            int color = figura$custom.outline ? figura$transparentColor(submit.color()) : submit.color();

            return figura$addNameplateSubmits(instance, original, submit, submit.pose(), color, backgroundColor, light);
        }

        if (figura$shouldSplitSubmittedNameplate())
            return figura$addNameplateSubmits(instance, original, submit, submit.pose(), submit.color(), submit.backgroundColor(), submit.lightCoords());

        return original.call(instance, e);
    }


    @WrapOperation(method = "add",
            at = @At(value = "INVOKE", target =
                    "Ljava/util/List;add(Ljava/lang/Object;)Z"
                    , ordinal = 0))
    private <E> boolean drawWithOutline(List<E> instance, E e, Operation<Boolean> original, @Share("textMatrix") LocalRef<Matrix4f> textMatrix) {
        SubmitNodeStorage.NameTagSubmit submit = (SubmitNodeStorage.NameTagSubmit) e;

        Matrix4f pose = submit.pose();
        int color = submit.color();
        int light = figura$custom != null && figura$custom.light != null ? figura$custom.light : submit.lightCoords();
        Matrix4f shadowMatrix = textMatrix.get() != null ? textMatrix.get() : pose;

        if (figura$shouldCustomizeNameplate()) {
            if (figura$custom.outline) {
                figura$addOutlineSubmits(submit, pose, color, light);
                return figura$addNameplateSubmits(instance, original, submit, shadowMatrix, figura$transparentColor(color), submit.backgroundColor(), light);
            }

            return figura$addNameplateSubmits(instance, original, submit, shadowMatrix, color, submit.backgroundColor(), light);
        }

        if (figura$shouldSplitSubmittedNameplate())
            return figura$addNameplateSubmits(instance, original, submit, shadowMatrix, color, submit.backgroundColor(), submit.lightCoords());

        return original.call(instance, new SubmitNodeStorage.NameTagSubmit(shadowMatrix, submit.x(), submit.y(), submit.text(),  submit.lightCoords(), color, submit.backgroundColor(), submit.distanceToCameraSq()));
    }

    @WrapOperation(method = "add",
            at = @At(value = "INVOKE", target =
                    "Ljava/util/List;add(Ljava/lang/Object;)Z"
                    , ordinal = 2))
    private <E> boolean drawDiscreteWithOutline(List<E> instance, E e, Operation<Boolean> original, @Share("textMatrix") LocalRef<Matrix4f> textMatrix) {
        SubmitNodeStorage.NameTagSubmit submit = (SubmitNodeStorage.NameTagSubmit) e;

        if (!figura$shouldCustomizeNameplate()) {
            if (figura$shouldSplitSubmittedNameplate())
                return figura$addNameplateSubmits(instance, original, submit, submit.pose(), submit.color(), submit.backgroundColor(), submit.lightCoords());

            return original.call(instance, e);
        }

        Matrix4f pose = submit.pose();
        Matrix4f shadowMatrix = textMatrix.get() != null ? textMatrix.get() : pose;
        int color = submit.color();
        int light = figura$custom.light != null ? figura$custom.light : submit.lightCoords();
        int backgroundColor = figura$custom.background != null ? figura$custom.background : submit.backgroundColor();

        if (figura$custom.outline) {
            figura$addOutlineSubmits(submit, pose, color, light);
            return figura$addNameplateSubmits(instance, original, submit, shadowMatrix, figura$transparentColor(color), backgroundColor, light);
        }

        return figura$addNameplateSubmits(instance, original, submit, shadowMatrix, color, backgroundColor, light);
    }

    @Unique
    private boolean figura$shouldCustomizeNameplate() {
        return figura$enabled && figura$avatar != null && figura$hasCustomNameplate;
    }

    @Unique
    private boolean figura$shouldSplitSubmittedNameplate() {
        return figura$isRenderingName && figura$textList != null && figura$textList.size() > 1;
    }

    @Unique
    private int figura$transparentColor(int color) {
        return color & 0x00FFFFFF;
    }

    @Unique
    private Component figura$cleanSubmittedLine(Component text) {
        return TextUtils.collapseLineSeparators(text);
    }

    @Unique
    @SuppressWarnings("unchecked")
    private <E> boolean figura$addNameplateSubmits(List<E> instance, Operation<Boolean> original, SubmitNodeStorage.NameTagSubmit submit,
                                                   Matrix4f pose, int color, int backgroundColor, int light) {
        if (figura$isRenderingName && figura$textList != null) {
            Font font = Minecraft.getInstance().font;
            for (int i = 0; i < figura$textList.size(); i++) {
                Component text = figura$cleanSubmittedLine(figura$textList.get(i));

                if (text.getString().isEmpty())
                    continue;

                float x = -font.width(text) / 2f;
                float y = submit.y() + (font.lineHeight + 1) * i;

                original.call(instance, (E) new SubmitNodeStorage.NameTagSubmit(pose, x, y, text, light, color, backgroundColor, submit.distanceToCameraSq()));
            }
            return true;
        }

        return original.call(instance, (E) new SubmitNodeStorage.NameTagSubmit(pose, submit.x(), submit.y(), submit.text(), light, color, backgroundColor, submit.distanceToCameraSq()));
    }

    @Unique
    private void figura$addOutlineSubmits(SubmitNodeStorage.NameTagSubmit submit, Matrix4f pose, int color, int light) {
        int outlineColor = figura$custom.outlineColor != null ? figura$custom.outlineColor : 0x202020;

        if (figura$isRenderingName && figura$textList != null) {
            Font font = Minecraft.getInstance().font;
            for (int i = 0; i < figura$textList.size(); i++) {
                Component text = figura$cleanSubmittedLine(figura$textList.get(i));

                if (text.getString().isEmpty())
                    continue;

                float x = -font.width(text) / 2f;
                float y = submit.y() + (font.lineHeight + 1) * i;

                figura$outlineSubmits.add(new SubmitNodeStorage.NameTagSubmit(pose, x, y, text, light, color, outlineColor, submit.distanceToCameraSq()));
            }
        } else {
            figura$outlineSubmits.add(new SubmitNodeStorage.NameTagSubmit(pose, submit.x(), submit.y(), submit.text(), light, color, outlineColor, submit.distanceToCameraSq()));
        }
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void clearOutlineSubmits(CallbackInfo ci) {
        figura$outlineSubmits.clear();
    }

    @Override
    public List<SubmitNodeStorage.NameTagSubmit> getOutlineSubmits() {
        return figura$outlineSubmits;
    }
}
