package net.minecraft.client;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;

public class StringSplitter {
    final StringSplitter.WidthProvider widthProvider;

    public StringSplitter(StringSplitter.WidthProvider widthProvider) {
        this.widthProvider = widthProvider;
    }

    public float stringWidth(@Nullable String content) {
        if (content == null) {
            return 0.0F;
        }

        MutableFloat mutableFloat = new MutableFloat();
        StringDecomposer.iterateFormatted(content, Style.EMPTY, (maxWidth, style, codePoint) -> {
            mutableFloat.add(this.widthProvider.getWidth(codePoint, style));
            return true;
        });
        return mutableFloat.floatValue();
    }

    public float stringWidth(FormattedText content) {
        MutableFloat mutableFloat = new MutableFloat();
        StringDecomposer.iterateFormatted(content, Style.EMPTY, (maxWidth, style, codePoint) -> {
            mutableFloat.add(this.widthProvider.getWidth(codePoint, style));
            return true;
        });
        return mutableFloat.floatValue();
    }

    public float stringWidth(FormattedCharSequence content) {
        MutableFloat mutableFloat = new MutableFloat();
        content.accept((maxWidth, style, codePoint) -> {
            mutableFloat.add(this.widthProvider.getWidth(codePoint, style));
            return true;
        });
        return mutableFloat.floatValue();
    }

    public int plainIndexAtWidth(String content, int maxWidth, Style style) {
        StringSplitter.WidthLimitedCharSink widthLimitedCharSink = new StringSplitter.WidthLimitedCharSink(maxWidth);
        StringDecomposer.iterate(content, style, widthLimitedCharSink);
        return widthLimitedCharSink.getPosition();
    }

    public String plainHeadByWidth(String content, int maxWidth, Style style) {
        return content.substring(0, this.plainIndexAtWidth(content, maxWidth, style));
    }

    public String plainTailByWidth(String content, int maxWidth, Style style) {
        MutableFloat mutableFloat = new MutableFloat();
        MutableInt mutableInt = new MutableInt(content.length());
        StringDecomposer.iterateBackwards(content, style, (maxWidthx, stylex, codePoint) -> {
            float f = mutableFloat.addAndGet(this.widthProvider.getWidth(codePoint, stylex));
            if (f > maxWidth) {
                return false;
            }

            mutableInt.setValue(maxWidthx);
            return true;
        });
        return content.substring(mutableInt.intValue());
    }

    public int formattedIndexByWidth(String content, int maxWidth, Style style) {
        StringSplitter.WidthLimitedCharSink widthLimitedCharSink = new StringSplitter.WidthLimitedCharSink(maxWidth);
        StringDecomposer.iterateFormatted(content, style, widthLimitedCharSink);
        return widthLimitedCharSink.getPosition();
    }

    @Nullable
    public Style componentStyleAtWidth(FormattedText content, int maxWidth) {
        StringSplitter.WidthLimitedCharSink widthLimitedCharSink = new StringSplitter.WidthLimitedCharSink(maxWidth);
        return content.<Style>visit(
                (style, contentx) -> StringDecomposer.iterateFormatted(contentx, style, widthLimitedCharSink) ? Optional.empty() : Optional.of(style),
                Style.EMPTY
            )
            .orElse(null);
    }

    @Nullable
    public Style componentStyleAtWidth(FormattedCharSequence content, int maxWidth) {
        StringSplitter.WidthLimitedCharSink widthLimitedCharSink = new StringSplitter.WidthLimitedCharSink(maxWidth);
        MutableObject<Style> mutableObject = new MutableObject<>();
        content.accept((maxWidthx, style, codePoint) -> {
            if (!widthLimitedCharSink.accept(maxWidthx, style, codePoint)) {
                mutableObject.setValue(style);
                return false;
            } else {
                return true;
            }
        });
        return mutableObject.getValue();
    }

    public String formattedHeadByWidth(String content, int maxWidth, Style style) {
        return content.substring(0, this.formattedIndexByWidth(content, maxWidth, style));
    }

    public FormattedText headByWidth(FormattedText content, int maxWidth, Style style) {
        final StringSplitter.WidthLimitedCharSink widthLimitedCharSink = new StringSplitter.WidthLimitedCharSink(maxWidth);
        return content.visit(new FormattedText.StyledContentConsumer<FormattedText>() {
            private final ComponentCollector collector = new ComponentCollector();

            @Override
            public Optional<FormattedText> accept(Style style, String string) {
                widthLimitedCharSink.resetPosition();
                if (!StringDecomposer.iterateFormatted(string, style, widthLimitedCharSink)) {
                    String string2 = string.substring(0, widthLimitedCharSink.getPosition());
                    if (!string2.isEmpty()) {
                        this.collector.append(FormattedText.of(string2, style));
                    }

                    return Optional.of(this.collector.getResultOrEmpty());
                } else {
                    if (!string.isEmpty()) {
                        this.collector.append(FormattedText.of(string, style));
                    }

                    return Optional.empty();
                }
            }
        }, style).orElse(content);
    }

    public int findLineBreak(String content, int maxWidth, Style style) {
        StringSplitter.LineBreakFinder lineBreakFinder = new StringSplitter.LineBreakFinder(maxWidth);
        StringDecomposer.iterateFormatted(content, style, lineBreakFinder);
        return lineBreakFinder.getSplitPosition();
    }

    public static int getWordPosition(String content, int skipCount, int cursorPoint, boolean includeWhitespace) {
        int i = cursorPoint;
        boolean bl = skipCount < 0;
        int j = Math.abs(skipCount);

        for (int k = 0; k < j; k++) {
            if (bl) {
                while (includeWhitespace && i > 0 && (content.charAt(i - 1) == ' ' || content.charAt(i - 1) == '\n')) {
                    i--;
                }

                while (i > 0 && content.charAt(i - 1) != ' ' && content.charAt(i - 1) != '\n') {
                    i--;
                }
            } else {
                int l = content.length();
                int m = content.indexOf(32, i);
                int n = content.indexOf(10, i);
                if (m == -1 && n == -1) {
                    i = -1;
                } else if (m != -1 && n != -1) {
                    i = Math.min(m, n);
                } else if (m != -1) {
                    i = m;
                } else {
                    i = n;
                }

                if (i == -1) {
                    i = l;
                } else {
                    while (includeWhitespace && i < l && (content.charAt(i) == ' ' || content.charAt(i) == '\n')) {
                        i++;
                    }
                }
            }
        }

        return i;
    }

    public void splitLines(String content, int maxWidth, Style style, boolean withNewLines, StringSplitter.LinePosConsumer linePos) {
        int i = 0;
        int j = content.length();
        Style style2 = style;

        while (i < j) {
            StringSplitter.LineBreakFinder lineBreakFinder = new StringSplitter.LineBreakFinder(maxWidth);
            boolean bl = StringDecomposer.iterateFormatted(content, i, style2, style, lineBreakFinder);
            if (bl) {
                linePos.accept(style2, i, j);
                break;
            }

            int k = lineBreakFinder.getSplitPosition();
            char c = content.charAt(k);
            int l = c != '\n' && c != ' ' ? k : k + 1;
            linePos.accept(style2, i, withNewLines ? l : k);
            i = l;
            style2 = lineBreakFinder.getSplitStyle();
        }
    }

    public List<FormattedText> splitLines(String content, int maxWidth, Style style) {
        List<FormattedText> list = Lists.newArrayList();
        this.splitLines(content, maxWidth, style, false, (stylex, begin, end) -> list.add(FormattedText.of(content.substring(begin, end), stylex)));
        return list;
    }

    public List<FormattedText> splitLines(FormattedText content, int maxWidth, Style style) {
        List<FormattedText> list = Lists.newArrayList();
        this.splitLines(content, maxWidth, style, (text, value) -> list.add(text));
        return list;
    }

    public List<FormattedText> splitLines(FormattedText content, int maxWidth, Style style, FormattedText prefix) {
        List<FormattedText> list = Lists.newArrayList();
        this.splitLines(content, maxWidth, style, (message, prefixx) -> list.add(prefixx ? FormattedText.composite(prefix, message) : message));
        return list;
    }

    public void splitLines(FormattedText content, int maxWidth, Style style, BiConsumer<FormattedText, Boolean> splitifier) {
        List<StringSplitter.LineComponent> list = Lists.newArrayList();
        content.visit((stylex, contentx) -> {
            if (!contentx.isEmpty()) {
                list.add(new StringSplitter.LineComponent(contentx, stylex));
            }

            return Optional.empty();
        }, style);
        StringSplitter.FlatComponents flatComponents = new StringSplitter.FlatComponents(list);
        boolean bl = true;
        boolean bl2 = false;
        boolean bl3 = false;

        while (bl) {
            bl = false;
            StringSplitter.LineBreakFinder lineBreakFinder = new StringSplitter.LineBreakFinder(maxWidth);

            for (StringSplitter.LineComponent lineComponent : flatComponents.parts) {
                boolean bl4 = StringDecomposer.iterateFormatted(lineComponent.contents, 0, lineComponent.style, style, lineBreakFinder);
                if (!bl4) {
                    int i = lineBreakFinder.getSplitPosition();
                    Style style2 = lineBreakFinder.getSplitStyle();
                    char c = flatComponents.charAt(i);
                    boolean bl5 = c == '\n';
                    boolean bl6 = bl5 || c == ' ';
                    bl2 = bl5;
                    FormattedText formattedText = flatComponents.splitAt(i, bl6 ? 1 : 0, style2);
                    splitifier.accept(formattedText, bl3);
                    bl3 = !bl5;
                    bl = true;
                    break;
                }

                lineBreakFinder.addToOffset(lineComponent.contents.length());
            }
        }

        FormattedText formattedText2 = flatComponents.getRemainder();
        if (formattedText2 != null) {
            splitifier.accept(formattedText2, bl3);
        } else if (bl2) {
            splitifier.accept(FormattedText.EMPTY, false);
        }
    }

    static class FlatComponents {
        final List<StringSplitter.LineComponent> parts;
        private String flatParts;

        public FlatComponents(List<StringSplitter.LineComponent> parts) {
            this.parts = parts;
            this.flatParts = parts.stream().map(part -> part.contents).collect(Collectors.joining());
        }

        public char charAt(int codePoint) {
            return this.flatParts.charAt(codePoint);
        }

        public FormattedText splitAt(int begin, int end, Style style) {
            ComponentCollector componentCollector = new ComponentCollector();
            ListIterator<StringSplitter.LineComponent> listIterator = this.parts.listIterator();
            int i = begin;
            boolean bl = false;

            while (listIterator.hasNext()) {
                StringSplitter.LineComponent lineComponent = listIterator.next();
                String string = lineComponent.contents;
                int j = string.length();
                if (!bl) {
                    if (i > j) {
                        componentCollector.append(lineComponent);
                        listIterator.remove();
                        i -= j;
                    } else {
                        String string2 = string.substring(0, i);
                        if (!string2.isEmpty()) {
                            componentCollector.append(FormattedText.of(string2, lineComponent.style));
                        }

                        i += end;
                        bl = true;
                    }
                }

                if (bl) {
                    if (i <= j) {
                        String string2 = string.substring(i);
                        if (string2.isEmpty()) {
                            listIterator.remove();
                        } else {
                            listIterator.set(new StringSplitter.LineComponent(string2, style));
                        }
                        break;
                    }

                    listIterator.remove();
                    i -= j;
                }
            }

            this.flatParts = this.flatParts.substring(begin + end);
            return componentCollector.getResultOrEmpty();
        }

        @Nullable
        public FormattedText getRemainder() {
            ComponentCollector componentCollector = new ComponentCollector();
            this.parts.forEach(componentCollector::append);
            this.parts.clear();
            return componentCollector.getResult();
        }
    }

    class LineBreakFinder implements FormattedCharSink {
        private final float maxWidth;
        private int lineBreak = -1;
        private Style lineBreakStyle = Style.EMPTY;
        private boolean hadNonZeroWidthChar;
        private float width;
        private int lastSpace = -1;
        private Style lastSpaceStyle = Style.EMPTY;
        private int nextChar;
        private int offset;

        public LineBreakFinder(final float maxWidth) {
            this.maxWidth = Math.max(maxWidth, 1.0F);
        }

        /**
         * Accepts a single code point from a {@link net.minecraft.util.FormattedCharSequence}.
         * @return {@code true} to accept more characters, {@code false} to stop traversing the sequence.
         *
         * @param positionInCurrentSequence Contains the relative position of the
         *                                  character in the current sub-sequence. If
         *                                  multiple formatted char sequences have been
         *                                  combined, this value will reset to {@code 0}
         *                                  after each sequence has been fully consumed.
         */
        @Override
        public boolean accept(int positionInCurrentSequence, Style style, int codePoint) {
            int i = positionInCurrentSequence + this.offset;
            switch (codePoint) {
                case 10:
                    return this.finishIteration(i, style);
                case 32:
                    this.lastSpace = i;
                    this.lastSpaceStyle = style;
                default:
                    float f = StringSplitter.this.widthProvider.getWidth(codePoint, style);
                    this.width += f;
                    if (!this.hadNonZeroWidthChar || !(this.width > this.maxWidth)) {
                        this.hadNonZeroWidthChar |= f != 0.0F;
                        this.nextChar = i + Character.charCount(codePoint);
                        return true;
                    } else {
                        return this.lastSpace != -1 ? this.finishIteration(this.lastSpace, this.lastSpaceStyle) : this.finishIteration(i, style);
                    }
            }
        }

        private boolean finishIteration(int lineBreak, Style lineBreakStyle) {
            this.lineBreak = lineBreak;
            this.lineBreakStyle = lineBreakStyle;
            return false;
        }

        private boolean lineBreakFound() {
            return this.lineBreak != -1;
        }

        public int getSplitPosition() {
            return this.lineBreakFound() ? this.lineBreak : this.nextChar;
        }

        public Style getSplitStyle() {
            return this.lineBreakStyle;
        }

        public void addToOffset(int offset) {
            this.offset += offset;
        }
    }

    static class LineComponent implements FormattedText {
        final String contents;
        final Style style;

        public LineComponent(String contents, Style style) {
            this.contents = contents;
            this.style = style;
        }

        @Override
        public <T> Optional<T> visit(FormattedText.ContentConsumer<T> acceptor) {
            return acceptor.accept(this.contents);
        }

        @Override
        public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> acceptor, Style style) {
            return acceptor.accept(this.style.applyTo(style), this.contents);
        }
    }

    @FunctionalInterface
    public interface LinePosConsumer {
        void accept(Style var1, int var2, int var3);
    }

    class WidthLimitedCharSink implements FormattedCharSink {
        private float maxWidth;
        private int position;

        public WidthLimitedCharSink(final float maxWidth) {
            this.maxWidth = maxWidth;
        }

        /**
         * Accepts a single code point from a {@link net.minecraft.util.FormattedCharSequence}.
         * @return {@code true} to accept more characters, {@code false} to stop traversing the sequence.
         *
         * @param positionInCurrentSequence Contains the relative position of the
         *                                  character in the current sub-sequence. If
         *                                  multiple formatted char sequences have been
         *                                  combined, this value will reset to {@code 0}
         *                                  after each sequence has been fully consumed.
         */
        @Override
        public boolean accept(int positionInCurrentSequence, Style style, int codePoint) {
            this.maxWidth = this.maxWidth - StringSplitter.this.widthProvider.getWidth(codePoint, style);
            if (this.maxWidth >= 0.0F) {
                this.position = positionInCurrentSequence + Character.charCount(codePoint);
                return true;
            } else {
                return false;
            }
        }

        public int getPosition() {
            return this.position;
        }

        public void resetPosition() {
            this.position = 0;
        }
    }

    @FunctionalInterface
    public interface WidthProvider {
        float getWidth(int var1, Style var2);
    }
}
