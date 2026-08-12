package org.figuramc.figura.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.figuramc.figura.ducks.NativeImageExtension;
import org.lwjgl.stb.STBImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

@Mixin(NativeImage.class)
public abstract class NativeImageMixin implements NativeImageExtension {
    @Shadow protected abstract boolean writeToChannel(WritableByteChannel writableByteChannel) throws IOException;

    @Shadow protected abstract int getPixelABGR(int i, int j);

    @Shadow protected abstract void setPixelABGR(int i, int j, int k);

    @Unique
    public byte[] figura$asByteArray() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byte[] var3;
        try {
            WritableByteChannel writableByteChannel = Channels.newChannel(byteArrayOutputStream);

            try {
                if (!this.writeToChannel(writableByteChannel)) {
                    throw new IOException("Could not write image to byte array: " + STBImage.stbi_failure_reason());
                }

                var3 = byteArrayOutputStream.toByteArray();
            } catch (Throwable var7) {
                if (writableByteChannel != null) {
                    try {
                        writableByteChannel.close();
                    } catch (Throwable var6) {
                        var7.addSuppressed(var6);
                    }
                }

                throw var7;
            }

            if (writableByteChannel != null) {
                writableByteChannel.close();
            }
        } catch (Throwable var8) {
            try {
                byteArrayOutputStream.close();
            } catch (Throwable var5) {
                var8.addSuppressed(var5);
            }

            throw var8;
        }

        byteArrayOutputStream.close();
        return var3;
    }

    @Override
    public int figura$getPixelABGR(int i, int j) {
        return getPixelABGR(i, j);
    }

    @Override
    public void figura$setPixelABGR(int i, int j, int color) {
        setPixelABGR(i,j,color);
    }
}
