package org.figuramc.figura.mixin.render.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

        figura$textList = TextUtils.splitText(component, "\n");
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

    @WrapOperation(method = "add",
            at = @At(value = "INVOKE", target =
                    "Ljava/util/List;add(Ljava/lang/Object;)Z"
            , ordinal = 1))
    private <E> boolean drawWithColor(List<E> instance, E e, Operation<Boolean> original) {
        SubmitNodeStorage.NameTagSubmit submit = (SubmitNodeStorage.NameTagSubmit) e;

        Font font = Minecraft.getInstance().font;

        if (figura$enabled && figura$avatar != null && figura$hasCustomNameplate) {
            int light = figura$custom.light != null ? figura$custom.light : submit.lightCoords();
            int backgroundColor = figura$custom.background != null ? figura$custom.background : submit.backgroundColor();
            boolean deadmau = submit.text().getString().equals("deadmau5");

            // This renders the translucent part of the nametag you see when shifting, and the background
            if (figura$isRenderingName) {
                // If the player's name is being rendered, render by lines otherwise just render whatever component is being passed. Applies for the rest of the loops below
                for (int i = 0; i < figura$textList.size(); i++) {
                    Component text1 = figura$textList.get(i);

                    if (text1.getString().isEmpty())
                        continue;

                    int line = i - figura$textList.size() + 1;
                    float x = -font.width(text1) / 2f;
                    float y =  (deadmau ? -10f : 0f) + (font.lineHeight + 1) * line;

                    original.call(instance, new SubmitNodeStorage.NameTagSubmit(submit.pose(), x, y, text1, light, submit.color(), backgroundColor, submit.distanceToCameraSq()));
                }
                return true;
            }  else {
                return original.call(instance, new SubmitNodeStorage.NameTagSubmit(submit.pose(), submit.x(), submit.y(), submit.text(), light, submit.color(), backgroundColor, submit.distanceToCameraSq()));
            }
        }
        return original.call(instance, e);
    }


    @WrapOperation(method = "add",
            at = @At(value = "INVOKE", target =
                    "Ljava/util/List;add(Ljava/lang/Object;)Z"
                    , ordinal = 0))
    private <E> boolean drawWithOutline(List<E> instance, E e, Operation<Boolean> original) {
        SubmitNodeStorage.NameTagSubmit submit = (SubmitNodeStorage.NameTagSubmit) e;

        Font font = Minecraft.getInstance().font;

        Matrix4f pose = submit.pose();
        int light = figura$custom != null && figura$custom.light != null ? figura$custom.light : submit.lightCoords();
        int color = submit.color();
        int backgroundColor = submit.backgroundColor() != 0 && figura$custom != null && figura$custom.background != null ? figura$custom.background : submit.backgroundColor();
        boolean deadmau = submit.text().getString().equals("deadmau5");
        if (figura$enabled && figura$avatar != null && figura$hasCustomNameplate && figura$custom.outline) {
            // This renders the opaque text with an outline if the player has that enabled.
            int outlineColor = figura$custom.outlineColor != null ? figura$custom.outlineColor : 0x202020;

            if (figura$isRenderingName) {
                for (int i = 0; i < figura$textList.size(); i++) {
                    Component text1 = figura$textList.get(i);

                    if (text1.getString().isEmpty())
                        continue;

                    int line = i - figura$textList.size() + 1;
                    float x = -font.width(text1) / 2f;
                    float y = (deadmau ? -10f : 0f) + (font.lineHeight + 1) * line;
                    // yes i am using the bg color field to store the outline color, sue me
                    figura$outlineSubmits.add(new SubmitNodeStorage.NameTagSubmit(pose, x, y, text1, light, color, outlineColor, submit.distanceToCameraSq()));
                    figura$addBackgroundOnly(instance, original, submit, x, y, text1, light, backgroundColor);
                }
                return true;
            } else {
                figura$outlineSubmits.add(new SubmitNodeStorage.NameTagSubmit(pose, submit.x(), submit.y(), submit.text(), light, color, outlineColor, submit.distanceToCameraSq()));
                return figura$addBackgroundOnly(instance, original, submit, submit.x(), submit.y(), submit.text(), light, backgroundColor);
            }
        } else {
            if (figura$enabled && figura$avatar != null && figura$hasCustomNameplate && figura$isRenderingName) {
                // This renders the opaque part of the nametag, that is text
                for (int i = 0; i < figura$textList.size(); i++) {
                    Component text1 = figura$textList.get(i);

                    if (text1.getString().isEmpty())
                        continue;

                    int line = i - figura$textList.size() + 1;
                    float x = -font.width(text1) / 2f;
                    float y = (deadmau ? -10f : 0f) + (font.lineHeight + 1) * line;
                    figura$addTextWithShadow(instance, original, submit, x, y, text1, light, color, backgroundColor);
                }
                return true;
            } else {
                return figura$addTextWithShadow(instance, original, submit, submit.x(), submit.y(), submit.text(), light, color, backgroundColor);
            }
        }
    }

    @Unique
    private <E> boolean figura$addTextWithShadow(List<E> instance, Operation<Boolean> original, SubmitNodeStorage.NameTagSubmit submit,
                                                 float x, float y, Component text, int light, int color, int backgroundColor) {
        if (figura$enabled && figura$avatar != null && figura$hasCustomNameplate && figura$custom.shadow) {
            original.call(instance, new SubmitNodeStorage.NameTagSubmit(submit.pose(), x + 1f, y + 1f, text, light, figura$shadowColor(color), 0, submit.distanceToCameraSq()));
        }

        return original.call(instance, new SubmitNodeStorage.NameTagSubmit(submit.pose(), x, y, text, light, color, backgroundColor, submit.distanceToCameraSq()));
    }

    @Unique
    private <E> boolean figura$addBackgroundOnly(List<E> instance, Operation<Boolean> original, SubmitNodeStorage.NameTagSubmit submit,
                                                 float x, float y, Component text, int light, int backgroundColor) {
        if (backgroundColor == 0)
            return true;

        return original.call(instance, new SubmitNodeStorage.NameTagSubmit(submit.pose(), x, y, text, light, 0, backgroundColor, submit.distanceToCameraSq()));
    }

    @Unique
    private static int figura$shadowColor(int color) {
        int alpha = color & 0xFF000000;
        if (alpha == 0)
            alpha = 0xFF000000;

        return alpha | ((color & 0xFCFCFC) >> 2);
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
