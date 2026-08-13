package org.figuramc.figura.mixin.gui;

import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.ducks.PlayerHeadRenderInfoExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerSkinRenderCache.RenderInfo.class)
public class PlayerHeadRenderInfoMixin implements PlayerHeadRenderInfoExtension {
    @Unique
    private Avatar figura$avatar = null;
    @Unique
    private ItemStack figura$itemStack = null;

    @Override
    public Avatar figura$getAvatar() {
        return figura$avatar;
    }

    @Override
    public void figura$setAvatar(Avatar avatar) {
        this.figura$avatar = avatar;
    }

    @Override
    public ItemStack figura$getItemStack() {
        return figura$itemStack;
    }

    @Override
    public void figura$setItemStack(ItemStack itemStack) {
        this.figura$itemStack = itemStack;
    }
}
