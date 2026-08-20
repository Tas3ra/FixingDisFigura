package org.figuramc.figura.model.rendering.nodeRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.figuramc.figura.ducks.NodeCollectorExtension;

import java.util.ArrayList;
import java.util.List;

public class FiguraFeatureRenderer {
    public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource, OutlineBufferSource outlineBufferSource) {
        List<FiguraSubmission<?>> figuraSubmissions = new ArrayList<>(((NodeCollectorExtension) submitNodeCollection).getFiguraSubmissions());

        for (FiguraSubmission<?> figuraSubmission : figuraSubmissions) {
            renderSubmission(figuraSubmission, bufferSource);

            EntityRenderState renderState = figuraSubmission.renderState();
            if (renderState != null && renderState.outlineColor != EntityRenderState.NO_OUTLINE) {
                outlineBufferSource.setColor(renderState.outlineColor);
                renderSubmission(figuraSubmission, outlineBufferSource);
            }
        }
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
