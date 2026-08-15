package org.figuramc.figura.model.rendering.nodeRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.figuramc.figura.ducks.NodeCollectorExtension;

import java.util.ArrayList;
import java.util.List;

public class FiguraFeatureRenderer {
    public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource) {
        List<FiguraSubmission<?>> figuraSubmissions = new ArrayList<>(((NodeCollectorExtension) submitNodeCollection).getFiguraSubmissions());

        for (FiguraSubmission<?> figuraSubmission : figuraSubmissions)
            renderSubmission(figuraSubmission, bufferSource);
    }

    private static <S extends net.minecraft.client.renderer.entity.state.EntityRenderState> void renderSubmission(FiguraSubmission<S> figuraSubmission, MultiBufferSource bufferSource) {
        if (figuraSubmission.avatar() == null || !figuraSubmission.avatar().canRender())
            return;

        figuraSubmission.renderer().apply(
                figuraSubmission.avatar(),
                figuraSubmission.renderState(),
                bufferSource
        );
    }
}
