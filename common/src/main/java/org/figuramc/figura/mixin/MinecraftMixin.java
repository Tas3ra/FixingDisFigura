package org.figuramc.figura.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.backend2.NetworkStuff;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.gui.ActionWheel;
import org.figuramc.figura.gui.FiguraToast;
import org.figuramc.figura.gui.PopupMenu;
import org.figuramc.figura.gui.screens.WardrobeScreen;
import org.figuramc.figura.lua.FiguraLuaPrinter;
import org.figuramc.figura.utils.FiguraText;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.UUID;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow @Final public MouseHandler mouseHandler;
    @Shadow @Final public Options options;
    @Shadow public LocalPlayer player;
    @Shadow public Entity cameraEntity;
    @Shadow public HitResult hitResult;

    @Shadow public abstract void setScreen(@Nullable Screen screen);

    @Unique
    private boolean scriptMouseUnlock = false;
    @Unique
    private boolean figura$popupButtonWasDown = false;

    @Inject(at = @At("RETURN"), method = "handleKeybinds")
    private void handleKeybinds(CallbackInfo ci) {
        // don't handle keybinds on panic
        if (AvatarManager.panic)
            return;

        // reload avatar button
        if (Configs.RELOAD_BUTTON.keyBind.consumeClick()) {
            AvatarManager.reloadAvatar(FiguraMod.getLocalPlayerUUID());
            FiguraToast.sendToast(FiguraText.of("toast.reload"));
        }

        // reload avatar button
        if (Configs.WARDROBE_BUTTON.keyBind.consumeClick())
            this.setScreen(new WardrobeScreen(null));

        // direct head popup button
        if (Configs.HEAD_POPUP_TRIGGER.value == 0 && Configs.HEAD_POPUP_BUTTON.keyBind.consumeClick()) {
            if (PopupMenu.isPanelOpen())
                PopupMenu.close();
            else
                PopupMenu.openHeadControlsFromLook();
        }

        // action wheel button
        Boolean wheel = null;
        if (Configs.ACTION_WHEEL_MODE.value % 2 == 1) {
            if (Configs.ACTION_WHEEL_BUTTON.keyBind.consumeClick())
                wheel = !ActionWheel.isEnabled();
        } else if (Configs.ACTION_WHEEL_BUTTON.keyBind.isDown()) {
            wheel = true;
        } else if (ActionWheel.isEnabled()) {
            wheel = false;
        }
        if (wheel != null) {
            if (wheel) {
                ActionWheel.setEnabled(true);
                this.mouseHandler.releaseMouse();
            } else {
                if (Configs.ACTION_WHEEL_MODE.value >= 2)
                    ActionWheel.execute(ActionWheel.getSelected(), true);
                ActionWheel.setEnabled(false);
                this.mouseHandler.grabMouse();
            }
        }

        // popup menu button
        boolean popupButtonDown = Configs.POPUP_BUTTON.keyBind.isDown();
        if (PopupMenu.isPanelOpen()) {
            if (popupButtonDown && !figura$popupButtonWasDown)
                PopupMenu.close();
        } else if (popupButtonDown && (!figura$popupButtonWasDown || PopupMenu.isEnabled())) {
            PopupMenu.setEnabled(true);

            if (!PopupMenu.hasEntity())
                figura$findPopupTarget();
        } else if (PopupMenu.isEnabled() && !PopupMenu.isPanelOpen()) {
            PopupMenu.run();
        }
        figura$popupButtonWasDown = popupButtonDown;

        // unlock cursor :p
        Avatar avatar = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
        if (avatar != null && avatar.luaRuntime != null && avatar.luaRuntime.host.unlockCursor) {
            this.mouseHandler.releaseMouse();
            scriptMouseUnlock = true;
        } else if (scriptMouseUnlock) {
            this.mouseHandler.grabMouse();
            scriptMouseUnlock = false;
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getInventory()Lnet/minecraft/world/entity/player/Inventory;"), method = "handleKeybinds", locals = LocalCapture.CAPTURE_FAILSOFT)
    private void handleHotbarSlots(CallbackInfo ci, int i) {
        if (PopupMenu.isEnabled())
            PopupMenu.hotbarKeyPressed(i);
        if (ActionWheel.isEnabled())
            ActionWheel.hotbarKeyPressed(i);
    }

    @Inject(at = @At("HEAD"), method = "setScreen")
    private void setScreen(Screen screen, CallbackInfo ci) {
        if (ActionWheel.isEnabled())
            ActionWheel.setEnabled(false);

        if (PopupMenu.isEnabled())
            PopupMenu.dismiss();
    }

    @Inject(at = @At("RETURN"), method = "clearClientLevel")
    private void clearLevel(Screen screen, CallbackInfo ci) {
        AvatarManager.clearAllAvatars();
        FiguraLuaPrinter.clearPrintQueue();
        NetworkStuff.unsubscribeAll();
    }

    @Inject(at = @At("RETURN"), method = "setLevel")
    private void setLevel(ClientLevel world, CallbackInfo ci) {
        NetworkStuff.auth();
    }

    @Inject(at = @At("HEAD"), method = "runTick")
    private void preTick(boolean tick, CallbackInfo ci) {
        AvatarManager.executeAll("applyBBAnimations", Avatar::applyAnimations);
    }

    @Inject(at = @At("RETURN"), method = "runTick")
    private void afterTick(boolean tick, CallbackInfo ci) {
        AvatarManager.executeAll("clearBBAnimations", Avatar::clearAnimations);
    }

    @Inject(at = @At("RETURN"), method = "tick")
    private void startTick(CallbackInfo ci) {
        FiguraMod.pushProfiler(FiguraMod.MOD_ID);
        FiguraMod.tick();
        FiguraMod.popProfiler();
    }

    @Unique
    private void figura$findPopupTarget() {
        Entity target = FiguraMod.extendedPickEntity;
        float tickDelta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

        if (this.player != null && target instanceof Player && !target.isInvisibleTo(this.player)) {
            PopupMenu.setEntity(target);
            return;
        }

        if (target != null && this.player != null && !target.isInvisibleTo(this.player)) {
            Vec3 pos = target.getPosition(tickDelta).add(0d, target.getBbHeight() + 0.1d, 0d);
            if (figura$setPopupProfileTarget(figura$getProfile(target), pos))
                return;
        }

        HitResult blockTarget = this.hitResult;
        if (!(blockTarget instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) && this.cameraEntity != null)
            blockTarget = this.cameraEntity.pick(20d, tickDelta, false);

        if (this.player != null && blockTarget instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            if (this.player.level().getBlockEntity(blockHit.getBlockPos()) instanceof SkullBlockEntity skullBlockEntity) {
                if (PopupMenu.setSkullTarget(skullBlockEntity))
                    return;
            }
        }

        if (!this.options.getCameraType().isFirstPerson() && this.cameraEntity != null)
            PopupMenu.setEntity(this.cameraEntity);
    }

    @Unique
    private static ResolvableProfile figura$getProfile(Entity entity) {
        ItemStack stack = ItemStack.EMPTY;
        if (entity instanceof ItemFrame itemFrame)
            stack = itemFrame.getItem();
        else if (entity instanceof ItemEntity itemEntity)
            stack = itemEntity.getItem();
        else if (entity instanceof LivingEntity livingEntity)
            stack = livingEntity.getItemBySlot(EquipmentSlot.HEAD);

        return stack.isEmpty() ? null : stack.get(DataComponents.PROFILE);
    }

    @Unique
    private static boolean figura$setPopupProfileTarget(ResolvableProfile profile, Vec3 pos) {
        UUID id = AvatarManager.getIdForProfile(profile);
        if (id == null)
            return false;

        String name = profile == null || profile.partialProfile() == null ? null : profile.partialProfile().name();
        if ((name == null || name.isBlank()) && profile != null && profile.name().isPresent())
            name = profile.name().get();
        PopupMenu.setProfileTarget(id, name == null || name.isBlank() ? Component.literal(id.toString()) : Component.literal(name), pos);
        return true;
    }
}
