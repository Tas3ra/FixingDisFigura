package org.figuramc.figura.ducks;

import org.figuramc.figura.font.EmojiContainer;

public interface BitmapProviderGlyphAccessor {
    void figura$setAdvance(int advance);
    void figura$setupEmoji(EmojiContainer container, int codePoint);
}
