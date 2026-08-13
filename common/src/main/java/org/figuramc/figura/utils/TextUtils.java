package org.figuramc.figura.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.core.RegistryAccess;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.figuramc.figura.gui.FiguraFunctionClickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public class TextUtils {

    public static final Component TAB = FiguraText.of("tab");
    public static final Component ELLIPSIS = FiguraText.of("ellipsis");
    public static final Component UNKNOWN = Component.literal("�").withStyle(Style.EMPTY);
    private static final String LINE_SEPARATOR_REGEX = "\\r\\n|\\n|\\r|\\\\r\\\\n|\\\\n|\\\\r";

    public static boolean allowScriptEvents;

    public static List<Component> splitLines(FormattedText text) {
        return splitText(text, LINE_SEPARATOR_REGEX);
    }

    public static Component collapseLineSeparators(FormattedText text) {
        MutableComponent ret = Component.empty();
        text.visit((style, string) -> {
            ret.append(Component.literal(string.replaceAll(LINE_SEPARATOR_REGEX, " ")).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return ret;
    }

    public static List<Component> splitText(FormattedText text, String regex) {
        // list to return
        ArrayList<Component> textList = new ArrayList<>();

        // current line variable
        MutableComponent[] currentText = {Component.empty()};

        // iterate over the text
        text.visit((style, string) -> {
            // split text based on regex
            String[] lines = string.split(regex, -1);

            // iterate over the split text
            for (int i = 0; i < lines.length; i++) {
                // if it is not the first iteration, add to return list and reset the line variable
                if (i != 0) {
                    textList.add(currentText[0].copy());
                    currentText[0] = Component.empty();
                }

                // append text with the line text
                currentText[0].append(Component.literal(lines[i]).withStyle(style));
            }

            return Optional.empty();
        }, Style.EMPTY);

        // add the last text iteration then return
        textList.add(currentText[0]);
        return textList;
    }

    public static Component removeClickableObjects(FormattedText text) {
        return removeClickableObjects(text, p -> true);
    }

    public static Component removeClickableObjects(FormattedText text, Predicate<ClickEvent> pred) {
        MutableComponent ret = Component.empty();
        text.visit((style, string) -> {
            ret.append(Component.literal(string).withStyle(style.getClickEvent() != null && pred.test(style.getClickEvent()) ? style.withClickEvent(null) : style));
            return Optional.empty();
        }, Style.EMPTY);
        return ret;
    }

    public static final RegistryOps<JsonElement> OPS = RegistryAccess.EMPTY.createSerializationContext(JsonOps.INSTANCE);
    public static Component tryParseJson(String text) {
        if (text == null)
            return Component.empty();

        // text to return
        Component finalText;

        try {
            // check if its valid json text
            JsonElement object = JsonParser.parseString(text);

            // this is to account for click and hover events being reworked in 1.21.5, they say every mod devolves into
            // some form of via version eventually, the rumors were true...
            convertLegacyTextEvents(object);

            // attempt to parse json
            finalText = ComponentSerialization.CODEC.decode(OPS, object).getOrThrow().getFirst();

            // if failed, throw a dummy exception
            if (finalText == null)
                throw new Exception("Error parsing JSON string");
        } catch (Exception ignored) {
            // on any exception, make the text as-is
            finalText = Component.literal(text);
        }

        // return text
        return finalText;
    }

    private static void convertLegacyTextEvents(JsonElement element) {
        if (element == null || element.isJsonNull())
            return;

        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray())
                convertLegacyTextEvents(child);
            return;
        }

        if (!element.isJsonObject())
            return;

        JsonObject obj = element.getAsJsonObject();
        if (obj.has("clickEvent")) {
            JsonObject replacement = convertClickEvent(obj.get("clickEvent"));
            obj.remove("clickEvent");
            if (!obj.has("click_event") && replacement.size() > 0)
                obj.add("click_event", replacement);
        }

        if (obj.has("hoverEvent")) {
            JsonObject replacement = convertHoverEvent(obj.get("hoverEvent"));
            obj.remove("hoverEvent");
            if (!obj.has("hover_event") && replacement.size() > 0)
                obj.add("hover_event", replacement);
        }

        for (Map.Entry<String, JsonElement> entry : new ArrayList<>(obj.entrySet()))
            convertLegacyTextEvents(entry.getValue());
    }

    private static @NotNull JsonObject convertHoverEvent(JsonElement hoverEvent) {
        JsonObject replacement = new JsonObject();
        if (!hoverEvent.isJsonObject())
            return replacement;

        JsonObject event = hoverEvent.getAsJsonObject();
        JsonElement actionElement = event.get("action");
        if (actionElement == null || !actionElement.isJsonPrimitive())
            return replacement;

        String action = actionElement.getAsString();
        replacement.addProperty("action", action);
        switch (action) {
            case "show_text": {
                JsonElement value = getFirst(event, "value", "contents");
                if (value == null)
                    value = new JsonPrimitive("");
                replacement.add("value", normalizeTextComponentValue(value));
                break;
            }
            case "show_item": {
                JsonElement item = getFirst(event, "contents", "value");
                copyInlineObjectOrString(item, replacement, "id");
                break;
            }
            case "show_entity": {
                JsonElement entity = getFirst(event, "contents", "value");
                JsonObject source = entity != null && entity.isJsonObject() ? entity.getAsJsonObject() : event;
                for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
                    String key = switch (entry.getKey()) {
                        case "type" -> "id";
                        case "id" -> "uuid";
                        default -> entry.getKey();
                    };

                    if (key.equals("action") || key.equals("contents") || key.equals("value"))
                        continue;

                    JsonElement value = key.equals("name") ? normalizeTextComponentValue(entry.getValue()) : entry.getValue().deepCopy();
                    replacement.add(key, value);
                }
                break;
            }
        }
        return replacement;
    }

    private static @NotNull JsonObject convertClickEvent(JsonElement clickEvent) {
        JsonObject replacement = new JsonObject();
        if (!clickEvent.isJsonObject())
            return replacement;

        JsonObject event = clickEvent.getAsJsonObject();
        JsonElement actionElement = event.get("action");
        JsonElement valueElement = event.get("value");
        if (actionElement == null || valueElement == null || !actionElement.isJsonPrimitive())
            return replacement;

        String action = actionElement.getAsString();
        replacement.addProperty("action", action);
        switch (action) {
            case "open_url": {
                String url = valueElement.getAsString();
                if (!url.startsWith("http"))
                    url = "http://" + url;
                replacement.addProperty("url", url);
                break;
            }
            case "open_file": {
                replacement.add("path", valueElement.deepCopy());
                break;
            }
            case "run_command", "suggest_command": {
                replacement.add("command", valueElement.deepCopy());
                break;
            }
            case "change_page": {
                replacement.addProperty("page", valueElement.getAsInt());
                break;
            }
            case "copy_to_clipboard": {
                replacement.add("value", valueElement.deepCopy());
                break;
            }
        }
        return replacement;
    }

    private static JsonElement normalizeTextComponentValue(JsonElement value) {
        JsonElement copy = value.deepCopy();
        if (copy.isJsonPrimitive() && copy.getAsJsonPrimitive().isString()) {
            String string = copy.getAsString();
            try {
                JsonElement parsed = JsonParser.parseString(string);
                if (parsed.isJsonObject() || parsed.isJsonArray() || (parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isString()))
                    copy = parsed;
            } catch (Exception ignored) {
            }
        }

        convertLegacyTextEvents(copy);
        return copy;
    }

    private static JsonElement getFirst(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key))
                return object.get(key);
        }
        return null;
    }

    private static void copyInlineObjectOrString(JsonElement source, JsonObject target, String stringKey) {
        if (source == null || source.isJsonNull())
            return;

        if (source.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : source.getAsJsonObject().entrySet())
                target.add(entry.getKey(), entry.getValue().deepCopy());
            return;
        }

        target.add(stringKey, source.deepCopy());
    }

    public static Component replaceInText(FormattedText text, String regex, Object replacement) {
        return replaceInText(text, regex, replacement, (s, style) -> true, Integer.MAX_VALUE);
    }

    public static Component replaceInText(FormattedText text, String regex, Object replacement, BiPredicate<String, Style> predicate, int times) {
        return replaceInText(text, regex, replacement, predicate, 0, times);
    }

    public static Component replaceInText(FormattedText text, String regex, Object replacement, BiPredicate<String, Style> predicate, int beginIndex, int times) {
        // fix replacement object
        Component replace = replacement instanceof Component c ? c : Component.literal(replacement.toString());
        MutableComponent ret = Component.empty();

        int[] ints = {beginIndex, times};
        text.visit((style, string) -> {
            // test predicate
            if (!predicate.test(string, style)) {
                ret.append(Component.literal(string).withStyle(style));
                return Optional.empty();
            }

            // split
            String[] split = string.split("((?<=" + regex + ")|(?=" + regex + "))");
            for (String s : split) {
                if (!s.matches(regex)) {
                    ret.append(Component.literal(s).withStyle(style));
                    continue;
                }

                if (ints[0] > 0 || ints[1] <= 0) {
                    ret.append(Component.literal(s).withStyle(style));
                } else {
                    ret.append(Component.empty().withStyle(style).append(replace));
                }

                ints[0]--;
                ints[1]--;
            }

            return Optional.empty();
        }, Style.EMPTY);

        return ret;
    }

    public static Component trimToWidthEllipsis(Font font, Component text, int width, Component ellipsis) {
        // return text without changes if it is not larger than width
        if (font.width(text.getVisualOrderText()) <= width)
            return text;

        // add ellipsis
        return addEllipsis(font, text, width, ellipsis);
    }

    public static Component addEllipsis(Font font, FormattedText text, int width, Component ellipsis) {
        // trim with the ellipsis size and return the modified text
        FormattedText trimmed = font.substrByWidth(text, width - font.width(ellipsis));
        return formattedTextToText(trimmed).copy().append(ellipsis);
    }

    public static Component replaceTabs(FormattedText text) {
        return TextUtils.replaceInText(text, "\\t", TAB);
    }

    public static List<FormattedCharSequence> wrapTooltip(FormattedText text, Font font, int mousePos, int screenWidth, int offset) {
        // first split the new line text
        List<Component> splitText = TextUtils.splitLines(text);

        // get the possible tooltip width
        int left = mousePos - offset;
        int right = screenWidth - mousePos - offset;

        // get largest text size
        int largest = getWidth(splitText, font);

        // get the optimal side for warping
        int side = largest <= right ? right : largest <= left ? left : Math.max(left, right);

        // wrap each line separately so line-feed control glyphs do not reach the font renderer
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : splitText) {
            List<FormattedCharSequence> wrappedLine = wrapText(line, side, font);
            if (wrappedLine.isEmpty())
                wrapped.add(Language.getInstance().getVisualOrder(line));
            else
                wrapped.addAll(wrappedLine);
        }
        return wrapped;
    }

    // get the largest text width from a list
    public static int getWidth(List<?> text, Font font) {
        int width = 0;

        for (Object object : text) {
            int w;
            if (object instanceof Component component) // instanceof switch case only for java 17 experimental ;-;
                w = font.width(component);
            else if (object instanceof FormattedCharSequence charSequence)
                w = font.width(charSequence);
            else if (object instanceof String s)
                w = font.width(s);
            else
                w = 0;

            width = Math.max(width, w);
        }
        return width;
    }

    // correctly calculates the height of a list of text componennts
    public static int getHeight(List<?> text, Font font, int lineSpaceing) {
        int lines = text.size();
        return (lines * font.lineHeight) + Math.max((lines-1)*lineSpaceing, 0);
    }

    public static int getHeight(List<?> text, Font font) {
        return getHeight(text, font, 1);
    }

    public static Component replaceStyle(FormattedText text, Style newStyle, Predicate<Style> predicate) {
        MutableComponent ret = Component.empty();
        text.visit((style, string) -> {
            ret.append(Component.literal(string).withStyle(predicate.test(style) ? newStyle.applyTo(style) : style));
            return Optional.empty();
        }, Style.EMPTY);
        return ret;
    }

    public static Component setStyleAtWidth(FormattedText text, int width, Font font, Style newStyle) {
        MutableComponent ret = Component.empty();
        text.visit((style, string) -> {
            MutableComponent current = Component.literal(string).withStyle(style);

            int prevWidth = font.width(ret);
            int currentWidth = font.width(current);
            if (prevWidth <= width && prevWidth + currentWidth > width)
                current.withStyle(newStyle);

            ret.append(current);
            return Optional.empty();
        }, Style.EMPTY);
        return ret;
    }

    public static List<FormattedCharSequence> wrapText(FormattedText text, int width, Font font) {
        List<FormattedCharSequence> warp = new ArrayList<>();
        font.getSplitter().splitLines(text, width, Style.EMPTY, (formattedText, aBoolean) -> warp.add(Language.getInstance().getVisualOrder(formattedText)));
        return warp;
    }

    public static Component charSequenceToText(FormattedCharSequence charSequence) {
        MutableComponent builder = Component.empty();
        StringBuilder buffer = new StringBuilder();
        Style[] lastStyle = new Style[1];

        charSequence.accept((index, style, codePoint) -> {
            if (!style.equals(lastStyle[0])) {
                if (buffer.length() > 0) {
                    builder.append(Component.literal(buffer.toString()).withStyle(lastStyle[0]));
                    buffer.setLength(0);
                }
                lastStyle[0] = style;
            }

            buffer.append(Character.toChars(codePoint));
            return true;
        });

        if (buffer.length() > 0)
            builder.append(Component.literal(buffer.toString()).withStyle(lastStyle[0]));

        return builder;
    }

    public static Component formattedTextToText(FormattedText formattedText) {
        if (formattedText instanceof Component c)
            return c;

        MutableComponent builder = Component.empty();
        formattedText.visit((style, string) -> {
            builder.append(Component.literal(string).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return builder;
    }

    public static Component substring(FormattedText text, int beginIndex, int endIndex) {
        StringBuilder counter = new StringBuilder();
        MutableComponent builder = Component.empty();
        text.visit((style, string) -> {
            int index = counter.length();
            int len = string.length();

            if (index <= endIndex && index + len >= beginIndex) {
                int sub = Math.max(beginIndex - index, 0);
                int top = Math.min(endIndex - index, len);
                builder.append(Component.literal(string.substring(sub, top)).withStyle(style));
            }

            counter.append(string);
            return counter.length() > endIndex ? FormattedText.STOP_ITERATION : Optional.empty();
        }, Style.EMPTY);
        return builder;
    }

    public static Component parseLegacyFormatting(FormattedText text) {
        MutableComponent builder = Component.empty();
        text.visit((style, string) -> {
            formatting: {
                // check for the string have the formatting char
                if (!string.contains("§"))
                    break formatting;

                // split the string at the special char
                String[] split = string.split("§");
                if (split.length < 2)
                    break formatting;

                // creates a new text with the left part of the string
                MutableComponent newText = Component.literal(split[0]).withStyle(style);

                // if right part has text
                for (int i = 1; i < split.length; i++) {
                    String s = split[i];

                    if (s.length() == 0)
                        continue;

                    // get the formatting code and apply to the style
                    ChatFormatting formatting = ChatFormatting.getByCode(s.charAt(0));
                    if (formatting != null)
                        style = style.applyLegacyFormat(formatting);

                    // create right text, and yeet the formatting code
                    newText.append(Component.literal(s.substring(1)).withStyle(style));
                }

                builder.append(newText);
                return Optional.empty();
            }

            builder.append(Component.literal(string).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return builder;
    }

    public static Component reverse(FormattedText text) {
        MutableComponent[] builder = {Component.empty()};
        text.visit((style, string) -> {
            StringBuilder str = new StringBuilder(string).reverse();
            builder[0] = Component.literal(str.toString()).withStyle(style).append(builder[0]);
            return Optional.empty();
        }, Style.EMPTY);
        return builder[0];
    }

    public static Component trim(FormattedText text) {
        String string = text.getString();
        int start = 0;
        int end = string.length();

        // trim
        while (start < end && string.charAt(start) <= ' ')
            start++;
        while (start < end && string.charAt(end - 1) <= ' ')
            end--;

        // apply trim
        return substring(text, start, end);
    }

    public static List<Component> formatInBounds(FormattedText text, Font font, int maxWidth, boolean wrap) {
        if (maxWidth > 0) {
            List<Component> lines = splitLines(text);
            if (wrap) {
                List<Component> newList = new ArrayList<>();
                for (Component line : lines) {
                    List<FormattedCharSequence> warped = wrapText(line, maxWidth, font);
                    if (warped.isEmpty()) {
                        newList.add(line);
                    } else {
                        for (FormattedCharSequence charSequence : warped)
                            newList.add(charSequenceToText(charSequence));
                    }
                }
                return newList;
            } else {
                List<Component> newList = new ArrayList<>();
                for (Component component : lines)
                    newList.add(formattedTextToText(font.substrByWidth(component, maxWidth)));
                return newList;
            }
        } else {
            return splitLines(text);
        }
    }

    public static Style componentStyleAtWidth(Font font, Component text, int pos) {
        StringSplitter.WidthLimitedCharSink widthLimitedCharSink = font.getSplitter().new WidthLimitedCharSink((float) pos);
        return text.visit((style, string) -> StringDecomposer.iterateFormatted(string, style, widthLimitedCharSink) ? Optional.empty() : Optional.of(style), Style.EMPTY).orElse(null);
    }

    public enum Alignment {
        LEFT((font, component) -> 0, i -> 0),
        RIGHT((font, component) -> font.width(component), i -> i),
        CENTER((font, component) -> font.width(component) / 2, i -> i / 2);

        private final BiFunction<Font, FormattedText, Integer> textFunction;
        private final Function<Integer, Integer> integerFunction;

        Alignment(BiFunction<Font, FormattedText, Integer> textFunction, Function<Integer, Integer> integerFunction) {
            this.textFunction = textFunction;
            this.integerFunction = integerFunction;
        }

        public int apply(Font font, FormattedText component) {
            return textFunction.apply(font, component);
        }

        public int apply(int width) {
            return integerFunction.apply(width);
        }

        public TextAlignment toVanilla() {
            return switch (this) {
                case LEFT -> TextAlignment.LEFT;
                case RIGHT -> TextAlignment.RIGHT;
                case CENTER -> TextAlignment.CENTER;
            };
        }
    }

    public record FiguraClickEvent(Runnable onClick) implements ClickEvent {
        @Override
        public @NotNull Action action() {
            return Action.SUGGEST_COMMAND;
        }
    }
}
