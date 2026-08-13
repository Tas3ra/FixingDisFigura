package org.figuramc.figura.mixin.font;

import com.mojang.blaze3d.font.UnbakedGlyph;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import org.figuramc.figura.ducks.BakedGlyphAccessor;
import org.figuramc.figura.ducks.BitmapProviderGlyphAccessor;
import org.figuramc.figura.font.EmojiContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BitmapProvider.Glyph.class)
public class BitmapGlyphMixin implements BitmapProviderGlyphAccessor {
    @Final
    @Mutable
    @Shadow
    private int advance;
    @Unique
    private EmojiContainer figura$emojiContainer;
    @Unique
    private int figura$emojiCodePoint = -1;

    @Override
    public void figura$setAdvance(int advance) {
        this.advance = advance;
    }

    @Override
    public void figura$setupEmoji(EmojiContainer container, int codePoint) {
        this.figura$emojiContainer = container;
        this.figura$emojiCodePoint = codePoint;
    }

    @Inject(method = "bake", at = @At("RETURN"))
    private void figura$setupBakedEmoji(UnbakedGlyph.Stitcher stitcher, CallbackInfoReturnable<BakedGlyph> cir) {
        BakedGlyph glyph = cir.getReturnValue();
        if (glyph != null && figura$emojiContainer != null && figura$emojiCodePoint >= 0 && glyph instanceof BakedGlyphAccessor accessor) {
            accessor.figura$setupEmoji(figura$emojiContainer, figura$emojiCodePoint);
        }
    }
}
