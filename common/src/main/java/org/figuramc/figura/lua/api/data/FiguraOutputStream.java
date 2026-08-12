package org.figuramc.figura.lua.api.data;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;

import java.io.IOException;
import java.io.OutputStream;

@LuaWhitelist
@LuaTypeDoc(name = "OutputStream", value = "output_stream")
public class FiguraOutputStream extends OutputStream {
    private final Avatar parent;
    private final OutputStream destinationStream;

    public FiguraOutputStream(Avatar parent, OutputStream destinationStream) {
        this.destinationStream = destinationStream;
        this.parent = parent;
        parent.openOutputStreams.add(this);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            value = "output_stream.write",
            overloads = @LuaMethodOverload(
                    argumentNames = "b",
                    argumentTypes = Integer.class
            )
    )
    @Override
    public void write(int b) {
        try {
            destinationStream.write(b);
        } catch (IOException e) {
            throw new LuaError(e);
        }
    }

    @Override
    @LuaWhitelist
    @LuaMethodDoc("output_stream.flush")
    public void flush() throws IOException {
        destinationStream.flush();
    }

    @Override
    @LuaWhitelist
    @LuaMethodDoc("output_stream.close")
    public void close() throws IOException {
        destinationStream.close();
        parent.openOutputStreams.remove(this);
    }

    @Override
    public String toString() {
        return "OutputStream";
    }
}
