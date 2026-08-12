package org.figuramc.figura.mixin.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.figuramc.figura.config.Configs;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.RenderEvents")
public class SimpleVCMixin {
    // Simple VC will hide your icon, this makes it so it is rendered if your nameplate is rendered
    @Redirect(method = "onRenderName", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;", opcode = Opcodes.GETFIELD, remap = true), remap = false)
    private LocalPlayer renderSelfNameplate(Minecraft minecraft){
        return Configs.SELF_NAMEPLATE.value ? null : minecraft.player;
    }

    @Inject(method = "shouldShowIcons", at = @At(value = "HEAD"), remap = false, cancellable = true)
    private void renderSelfNameplate(CallbackInfoReturnable<Boolean> callbackInfoReturnable){
        callbackInfoReturnable.setReturnValue(true);
    }
}