package org.figuramc.figura.utils;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.figuramc.figura.mixin.font.FontAccessor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class TextRenderUtils {
    private static final List<DeferredTextTask> WORLD_DEFERRED_TEXT_TASKS = new ArrayList<>();
    private static final List<DeferredTextTask> IMMEDIATE_TEXT_TASKS = new ArrayList<>();
    private static boolean deferringTextTasks;
    private static boolean immediateTextTaskLayer;

    private TextRenderUtils() {
    }

    public static void beginDeferredTextTasks() {
        deferringTextTasks = true;
        immediateTextTaskLayer = false;
    }

    public static void endDeferredTextTasks() {
        deferringTextTasks = false;
        immediateTextTaskLayer = false;
    }

    public static void clearDeferredTextTasks() {
        WORLD_DEFERRED_TEXT_TASKS.clear();
        IMMEDIATE_TEXT_TASKS.clear();
        deferringTextTasks = false;
        immediateTextTaskLayer = false;
    }

    public static boolean isDeferringTextTasks() {
        return deferringTextTasks;
    }

    public static boolean hasDeferredTextTasks() {
        return !WORLD_DEFERRED_TEXT_TASKS.isEmpty();
    }

    public static boolean beginImmediateTextTaskLayer() {
        if (deferringTextTasks)
            return false;

        IMMEDIATE_TEXT_TASKS.clear();
        deferringTextTasks = true;
        immediateTextTaskLayer = true;
        return true;
    }

    public static void renderImmediateTextTaskLayer(MultiBufferSource.BufferSource buffer) {
        if (!immediateTextTaskLayer)
            return;

        try {
            renderTextTasks(buffer, IMMEDIATE_TEXT_TASKS);
        } finally {
            IMMEDIATE_TEXT_TASKS.clear();
            deferringTextTasks = false;
            immediateTextTaskLayer = false;
        }
    }

    public static void clearImmediateTextTaskLayer() {
        if (!immediateTextTaskLayer)
            return;

        IMMEDIATE_TEXT_TASKS.clear();
        deferringTextTasks = false;
        immediateTextTaskLayer = false;
    }

    public static void queueTextTask(Matrix4f matrix, List<Component> text, TextUtils.Alignment alignment,
                                     boolean shadow, boolean outline, int backgroundColor, int outlineColor,
                                     int opacityColor, int light, int width, int height, float vertexOffset) {
        List<DeferredTextTask> queue = immediateTextTaskLayer ? IMMEDIATE_TEXT_TASKS : WORLD_DEFERRED_TEXT_TASKS;
        queue.add(new DeferredTextTask(new Matrix4f(matrix), List.copyOf(text), alignment, shadow, outline,
                backgroundColor, outlineColor, opacityColor, light, width, height, vertexOffset));
    }

    public static void renderDeferredTextTasks(MultiBufferSource.BufferSource buffer) {
        try {
            renderTextTasks(buffer, WORLD_DEFERRED_TEXT_TASKS);
        } finally {
            WORLD_DEFERRED_TEXT_TASKS.clear();
        }
    }

    private static void renderTextTasks(MultiBufferSource.BufferSource buffer, List<DeferredTextTask> tasks) {
        Font font = Minecraft.getInstance().font;

        for (DeferredTextTask task : tasks)
            renderDeferredTextTask(font, buffer, task);
    }

    public static void drawText(Font font, Component text, float x, float y, int color, boolean shadow,
                                Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode displayMode, int light) {
        if (displayMode == Font.DisplayMode.SEE_THROUGH) {
            FormattedCharSequence visualText = TextUtils.stripLineSeparatorGlyphs(text.getVisualOrderText());
            if (shadow)
                font.drawInBatch(withShadowColors(visualText, color), x + 1f, y + 1f, shadowColor(color), false, matrix, buffer, displayMode, 0, light);

            font.drawInBatch(visualText, x, y, color, false, matrix, buffer, displayMode, 0, light);
            return;
        }

        font.drawInBatch(text, x, y, color, shadow, matrix, buffer, displayMode, 0, light);
    }

    public static void drawOutlinedText(Font font, FormattedCharSequence text, float x, float y, int color,
                                        int outlineColor, Matrix4f matrix, MultiBufferSource buffer,
                                        Font.DisplayMode displayMode, int light) {
        text = TextUtils.stripLineSeparatorGlyphs(text);
        Font.PreparedTextBuilder outlineBuilder = font.new PreparedTextBuilder(0, 0, outlineColor, false, false);

        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                if (xOffset == 0 && yOffset == 0)
                    continue;

                float[] cursor = new float[] {x};
                int outlineX = xOffset;
                int outlineY = yOffset;
                int rgbOutline = outlineColor & 0x00FFFFFF;
                text.accept((index, style, codePoint) -> {
                    boolean bold = style.isBold();
                    GlyphSource glyphSource = ((FontAccessor) font).figura$getFontSet(style.getFont());
                    var glyphInfo = glyphSource.getGlyph(codePoint).info();
                    outlineBuilder.x = cursor[0] + outlineX * glyphInfo.getShadowOffset();
                    outlineBuilder.y = y + outlineY * glyphInfo.getShadowOffset();
                    cursor[0] += glyphInfo.getAdvance(bold);
                    return outlineBuilder.accept(index, style.withColor(rgbOutline), codePoint);
                });
            }
        }

        outlineBuilder.visit(Font.GlyphVisitor.forMultiBufferSource(buffer, matrix, displayMode, light));
        font.drawInBatch(text, x, y, color, false, matrix, buffer, displayMode, 0, light);
    }

    private static void renderDeferredTextTask(Font font, MultiBufferSource buffer, DeferredTextTask task) {
        if (task.backgroundColor != 0) {
            int offset = task.alignment.apply(task.width);
            float x1 = -1 - offset;
            float x2 = task.width - offset;
            VertexConsumer vertexConsumer = buffer.getBuffer(RenderTypes.textBackgroundSeeThrough());
            vertexConsumer.addVertex(task.matrix, x1, -1f, task.vertexOffset).setColor(task.backgroundColor).setLight(task.light);
            vertexConsumer.addVertex(task.matrix, x1, task.height, task.vertexOffset).setColor(task.backgroundColor).setLight(task.light);
            vertexConsumer.addVertex(task.matrix, x2, task.height, task.vertexOffset).setColor(task.backgroundColor).setLight(task.light);
            vertexConsumer.addVertex(task.matrix, x2, -1f, task.vertexOffset).setColor(task.backgroundColor).setLight(task.light);
        }

        for (int i = 0, y = 0; i < task.text.size(); i++, y += (font.lineHeight + 1)) {
            Component text = task.text.get(i);
            int x = -task.alignment.apply(font, text);

            if (task.outline)
                drawOutlinedText(font, text.getVisualOrderText(), x, y, task.opacityColor, task.outlineColor, task.matrix, buffer, Font.DisplayMode.SEE_THROUGH, task.light);
            else
                drawText(font, text, x, y, task.opacityColor, task.shadow, task.matrix, buffer, Font.DisplayMode.SEE_THROUGH, task.light);
        }
    }

    private static FormattedCharSequence withShadowColors(FormattedCharSequence text, int fallbackColor) {
        int fallbackRgb = fallbackColor & 0x00FFFFFF;
        return sink -> text.accept((index, style, codePoint) -> {
            if (TextUtils.isLineSeparatorCodePoint(codePoint))
                return true;

            int rgb = style.getColor() != null ? style.getColor().getValue() : fallbackRgb;
            return sink.accept(index, style.withColor(shadowColor(rgb) & 0x00FFFFFF), codePoint);
        });
    }

    private static int shadowColor(int color) {
        int alpha = color & 0xFF000000;
        if (alpha == 0)
            alpha = 0xFF000000;

        return alpha | ((color & 0xFCFCFC) >> 2);
    }

    private record DeferredTextTask(Matrix4f matrix, List<Component> text, TextUtils.Alignment alignment,
                                    boolean shadow, boolean outline, int backgroundColor, int outlineColor,
                                    int opacityColor, int light, int width, int height, float vertexOffset) {
    }
}
