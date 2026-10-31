package net.minecraft.network.chat;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.Unit;

public interface FormattedText {
    Optional<Unit> STOP_ITERATION = Optional.of(Unit.INSTANCE);
    FormattedText EMPTY = new FormattedText() {
        @Override
        public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
            return Optional.empty();
        }
    };

    <T> Optional<T> visit(FormattedText.ContentConsumer<T> var1);

    <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> var1, Style var2);

    static FormattedText of(String text) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
                return contentConsumer.accept(text);
            }

            @Override
            public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
                return styledContentConsumer.accept(style, text);
            }
        };
    }

    static FormattedText of(String text, Style style) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
                return contentConsumer.accept(text);
            }

            @Override
            public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
                return styledContentConsumer.accept(style.applyTo(style), text);
            }
        };
    }

    static FormattedText composite(FormattedText... elements) {
        return composite(ImmutableList.copyOf(elements));
    }

    static FormattedText composite(List<? extends FormattedText> elements) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
                for (FormattedText formattedText : elements) {
                    Optional<T> optional = formattedText.visit(contentConsumer);
                    if (optional.isPresent()) {
                        return optional;
                    }
                }

                return Optional.empty();
            }

            @Override
            public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
                for (FormattedText formattedText : elements) {
                    Optional<T> optional = formattedText.visit(styledContentConsumer, style);
                    if (optional.isPresent()) {
                        return optional;
                    }
                }

                return Optional.empty();
            }
        };
    }

    default String getString() {
        StringBuilder stringBuilder = new StringBuilder();
        this.visit(content -> {
            stringBuilder.append(content);
            return Optional.empty();
        });
        return stringBuilder.toString();
    }

    interface ContentConsumer<T> {
        Optional<T> accept(String var1);
    }

    interface StyledContentConsumer<T> {
        Optional<T> accept(Style var1, String var2);
    }
}
