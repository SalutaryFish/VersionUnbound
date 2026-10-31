package net.minecraft.util;

import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public class StringDecomposer {
    private static final char REPLACEMENT_CHAR = '�';
    private static final Optional<Object> STOP_ITERATION = Optional.of(Unit.INSTANCE);

    private static boolean feedChar(Style style, FormattedCharSink sink, int position, char character) {
        return Character.isSurrogate(character) ? sink.accept(position, style, 65533) : sink.accept(position, style, character);
    }

    public static boolean iterate(String text, Style style, FormattedCharSink sink) {
        int i = text.length();

        for (int j = 0; j < i; j++) {
            char c = text.charAt(j);
            if (Character.isHighSurrogate(c)) {
                if (j + 1 >= i) {
                    if (!sink.accept(j, style, 65533)) {
                        return false;
                    }
                    break;
                }

                char d = text.charAt(j + 1);
                if (Character.isLowSurrogate(d)) {
                    if (!sink.accept(j, style, Character.toCodePoint(c, d))) {
                        return false;
                    }

                    j++;
                } else if (!sink.accept(j, style, 65533)) {
                    return false;
                }
            } else if (!feedChar(style, sink, j, c)) {
                return false;
            }
        }

        return true;
    }

    public static boolean iterateBackwards(String text, Style style, FormattedCharSink sink) {
        int i = text.length();

        for (int j = i - 1; j >= 0; j--) {
            char c = text.charAt(j);
            if (Character.isLowSurrogate(c)) {
                if (j - 1 < 0) {
                    if (!sink.accept(0, style, 65533)) {
                        return false;
                    }
                    break;
                }

                char d = text.charAt(j - 1);
                if (Character.isHighSurrogate(d)) {
                    if (!sink.accept(--j, style, Character.toCodePoint(d, c))) {
                        return false;
                    }
                } else if (!sink.accept(j, style, 65533)) {
                    return false;
                }
            } else if (!feedChar(style, sink, j, c)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Iterate a String while applying legacy formatting codes starting with a {@code §} sign.
     */
    public static boolean iterateFormatted(String text, Style style, FormattedCharSink sink) {
        return iterateFormatted(text, 0, style, sink);
    }

    /**
     * Iterate a String while applying legacy formatting codes starting with a {@code §} sign.
     *
     * @param skip The amount of characters to skip from the beginning.
     */
    public static boolean iterateFormatted(String text, int skip, Style style, FormattedCharSink sink) {
        return iterateFormatted(text, skip, style, style, sink);
    }

    /**
     * Iterate a String while applying legacy formatting codes starting with a {@code §} sign.
     *
     * @param skip         The amount of character to skip from the beginning.
     * @param currentStyle The current style at the starting position after the skip.
     * @param defaultStyle The default style for the sequence that should be applied
     *                     after a reset format code ({@code §r})
     */
    public static boolean iterateFormatted(String text, int skip, Style currentStyle, Style defaultStyle, FormattedCharSink sink) {
        int i = text.length();
        Style style = currentStyle;

        for (int j = skip; j < i; j++) {
            char c = text.charAt(j);
            if (c == 167) {
                if (j + 1 >= i) {
                    break;
                }

                char d = text.charAt(j + 1);
                ChatFormatting chatFormatting = ChatFormatting.getByCode(d);
                if (chatFormatting != null) {
                    style = chatFormatting == ChatFormatting.RESET ? defaultStyle : style.applyLegacyFormat(chatFormatting);
                }

                j++;
            } else if (Character.isHighSurrogate(c)) {
                if (j + 1 >= i) {
                    if (!sink.accept(j, style, 65533)) {
                        return false;
                    }
                    break;
                }

                char d = text.charAt(j + 1);
                if (Character.isLowSurrogate(d)) {
                    if (!sink.accept(j, style, Character.toCodePoint(c, d))) {
                        return false;
                    }

                    j++;
                } else if (!sink.accept(j, style, 65533)) {
                    return false;
                }
            } else if (!feedChar(style, sink, j, c)) {
                return false;
            }
        }

        return true;
    }

    public static boolean iterateFormatted(FormattedText text, Style style, FormattedCharSink sink) {
        return text.visit((stylex, string) -> iterateFormatted(string, 0, stylex, sink) ? Optional.empty() : STOP_ITERATION, style).isEmpty();
    }

    public static String filterBrokenSurrogates(String text) {
        StringBuilder stringBuilder = new StringBuilder();
        iterate(text, Style.EMPTY, (position, style, codePoint) -> {
            stringBuilder.appendCodePoint(codePoint);
            return true;
        });
        return stringBuilder.toString();
    }

    public static String getPlainText(FormattedText text) {
        StringBuilder stringBuilder = new StringBuilder();
        iterateFormatted(text, Style.EMPTY, (position, style, codePoint) -> {
            stringBuilder.appendCodePoint(codePoint);
            return true;
        });
        return stringBuilder.toString();
    }
}
