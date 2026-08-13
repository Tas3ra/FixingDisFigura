package org.figuramc.figura.mixin.font;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import org.figuramc.figura.ducks.BitmapProviderGlyphAccessor;
import org.figuramc.figura.font.EmojiContainer;
import org.figuramc.figura.font.EmojiMetadata;
import org.figuramc.figura.font.Emojis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Mixin(FontSet.class)
public abstract class FontSetMixin {

    @Shadow
    @Final
    private GlyphStitcher stitcher;

    //method_57035 for fabric intermediary, lambda$selectProviders$5 for everything else
    @Inject(method = {"method_57035"}, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/font/GlyphInfo;getAdvance(Z)F", shift = At.Shift.BEFORE, remap = true), locals = LocalCapture.CAPTURE_FAILEXCEPTION, remap = false)
    public void thing(List<?> list, Set<?> set, int i, CallbackInfo ci, Iterator var4, GlyphProvider glyphProvider, UnbakedGlyph unbakedGlyph) {
        figura$setupEmojiGlyph(i, unbakedGlyph);
    }

    @Inject(method = "computeGlyphInfo", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/font/GlyphProvider;getGlyph(I)Lcom/mojang/blaze3d/font/UnbakedGlyph;"))
    public void computeBakedGlyphMixin(int codePoint, CallbackInfoReturnable cir, @Local GlyphProvider provider) {
        UnbakedGlyph unbakedGlyph = provider.getGlyph(codePoint);

        if (unbakedGlyph != null) {
            figura$setupEmojiGlyph(codePoint, unbakedGlyph);
        }
    }

    @Unique
    private void figura$setupEmojiGlyph(int codePoint, UnbakedGlyph unbakedGlyph) {
        if (!figura$isEmojiFont() || !(unbakedGlyph instanceof BitmapProvider.Glyph))
            return;

        EmojiContainer container = Emojis.getCategoryByFont(((GlyphStitcherAccessor) stitcher).getName());
        if (container == null)
            return;

        EmojiMetadata metadata = container.getLookup().getMetadata(codePoint);
        BitmapProviderGlyphAccessor accessor = (BitmapProviderGlyphAccessor) unbakedGlyph;
        accessor.figura$setAdvance(metadata != null ? metadata.width : 8);
        accessor.figura$setupEmoji(container, codePoint);
    }

    @Unique
    private boolean figura$isEmojiFont() {
        return ((GlyphStitcherAccessor)stitcher).getName().getNamespace().equals("figura");
    }
}
