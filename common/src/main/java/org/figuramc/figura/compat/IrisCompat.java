package org.figuramc.figura.compat;

import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import org.figuramc.figura.model.rendering.texture.FiguraRenderTypes;

public class IrisCompat {
    public static void assignPipelinesToIrisPrograms() {
        IrisApi.getInstance().assignPipeline(FiguraRenderTypes.FiguraRenderPipelines.FIGURA_SOLID, IrisProgram.ENTITIES);
    }
}
