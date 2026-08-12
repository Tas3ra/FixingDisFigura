package org.figuramc.figura.ducks;

import java.io.IOException;

public interface NativeImageExtension {
    byte[] figura$asByteArray() throws IOException;
    int figura$getPixelABGR(int i, int j);
    void figura$setPixelABGR(int i, int j, int color);
}
