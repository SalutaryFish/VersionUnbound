package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;

/**
 * A Component which can have its Style and siblings modified.
 */
public class MutableComponent implements Component {
    private final ComponentContents contents;
    private final List<Component> siblings;
    private Style style;
    private FormattedCharSequence visualOrderText = FormattedCharSequence.EMPTY;
    @Nullable
    private Language decomposedWith;

    MutableComponent(ComponentContents contents, List<Component> siblings, Style style) {
        this.contents = contents;
        this.siblings = siblings;
        this.style = style;
    }

    public static MutableComponent create(ComponentContents contents) {
        return new MutableComponent(contents, Lists.newArrayList(), Style.EMPTY);
    }

    @Override
    public ComponentContents getContents() {
        return this.contents;
    }

    @Override
    public List<Component> getSiblings() {
        return this.siblings;
    }

    /**
     * Sets the style for this component and returns the component itself.
     */
    public MutableComponent setStyle(Style style) {
        this.style = style;
        return this;
    }

    @Override
    public Style getStyle() {
        return this.style;
    }

    /**
     * Add the given text to this component's siblings.
     *
     * Note: If this component turns the text bold, that will apply to all the siblings until a later sibling turns the text something else.
     */
    public MutableComponent append(String string) {
        return string.isEmpty() ? this : this.append(Component.literal(string));
    }

    /**
     * Add the given component to this component's siblings.
     *
     * Note: If this component turns the text bold, that will apply to all the siblings until a later sibling turns the text something else.
     */
    public MutableComponent append(Component sibling) {
        this.siblings.add(sibling);
        return this;
    }

    public MutableComponent withStyle(UnaryOperator<Style> modifyFunc) {
        this.setStyle(modifyFunc.apply(this.getStyle()));
        return this;
    }

    public MutableComponent withStyle(Style style) {
        this.setStyle(style.applyTo(this.getStyle()));
        return this;
    }

    public MutableComponent withStyle(ChatFormatting... formats) {
        this.setStyle(this.getStyle().applyFormats(formats));
        return this;
    }

    public MutableComponent withStyle(ChatFormatting format) {
        this.setStyle(this.getStyle().applyFormat(format));
        return this;
    }

    public MutableComponent withColor(int color) {
        this.setStyle(this.getStyle().withColor(color));
        return this;
    }

    @Override
    public FormattedCharSequence getVisualOrderText() {
        Language language = Language.getInstance();
        if (this.decomposedWith != language) {
            this.visualOrderText = language.getVisualOrder(this);
            this.decomposedWith = language;
        }

        return this.visualOrderText;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return !(other instanceof MutableComponent mutableComponent)
                ? false
                : this.contents.equals(mutableComponent.contents)
                    && this.style.equals(mutableComponent.style)
                    && this.siblings.equals(mutableComponent.siblings);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.contents, this.style, this.siblings);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder(this.contents.toString());
        boolean bl = !this.style.isEmpty();
        boolean bl2 = !this.siblings.isEmpty();
        if (bl || bl2) {
            stringBuilder.append('[');
            if (bl) {
                stringBuilder.append("style=");
                stringBuilder.append(this.style);
            }

            if (bl && bl2) {
                stringBuilder.append(", ");
            }

            if (bl2) {
                stringBuilder.append("siblings=");
                stringBuilder.append(this.siblings);
            }

            stringBuilder.append(']');
        }

        return stringBuilder.toString();
    }
}
