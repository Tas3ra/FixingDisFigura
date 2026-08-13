package org.figuramc.figura.ducks;

import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.avatar.Avatar;

public interface PlayerHeadRenderInfoExtension {
    Avatar figura$getAvatar();
    void figura$setAvatar(Avatar avatar);
    ItemStack figura$getItemStack();
    void figura$setItemStack(ItemStack itemStack);

    default void figura$clearFiguraContext() {
        figura$setAvatar(null);
        figura$setItemStack(null);
    }
}
