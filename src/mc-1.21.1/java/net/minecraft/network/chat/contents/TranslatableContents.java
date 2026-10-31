package net.minecraft.network.chat.contents;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

public class TranslatableContents implements ComponentContents {
    public static final Object[] NO_ARGS = new Object[0];
    private static final Codec<Object> PRIMITIVE_ARG_CODEC = ExtraCodecs.JAVA.validate(TranslatableContents::filterAllowedArguments);
    private static final Codec<Object> ARG_CODEC = Codec.either(PRIMITIVE_ARG_CODEC, ComponentSerialization.CODEC)
        .xmap(
            arg -> arg.map(argx -> argx, text -> Objects.requireNonNullElse(text.tryCollapseToString(), text)),
            arg -> arg instanceof Component component ? Either.right(component) : Either.left(arg)
        );
    public static final MapCodec<TranslatableContents> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.STRING.fieldOf("translate").forGetter(contents -> contents.key),
                Codec.STRING.lenientOptionalFieldOf("fallback").forGetter(contents -> Optional.ofNullable(contents.fallback)),
                ARG_CODEC.listOf().optionalFieldOf("with").forGetter(contents -> adjustArgs(contents.args))
            )
            .apply(instance, TranslatableContents::create)
    );
    public static final ComponentContents.Type<TranslatableContents> TYPE = new ComponentContents.Type<>(CODEC, "translatable");
    private static final FormattedText TEXT_PERCENT = FormattedText.of("%");
    private static final FormattedText TEXT_NULL = FormattedText.of("null");
    private final String key;
    @Nullable
    private final String fallback;
    private final Object[] args;
    @Nullable
    private Language decomposedWith;
    /**
     * The discrete elements that make up this component. For example, this would be ["Prefix, ", "FirstArg", "SecondArg", " again ", "SecondArg", " and ", "FirstArg", " lastly ", "ThirdArg", " and also ", "FirstArg", " again!"] for "translation.test.complex" (see en_us.json)
     */
    private List<FormattedText> decomposedParts = ImmutableList.of();
    private static final Pattern FORMAT_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private static DataResult<Object> filterAllowedArguments(@Nullable Object input) {
        return !isAllowedPrimitiveArgument(input) ? DataResult.error(() -> "This value needs to be parsed as component") : DataResult.success(input);
    }

    public static boolean isAllowedPrimitiveArgument(@Nullable Object input) {
        return input instanceof Number || input instanceof Boolean || input instanceof String;
    }

    private static Optional<List<Object>> adjustArgs(Object[] args) {
        return args.length == 0 ? Optional.empty() : Optional.of(Arrays.asList(args));
    }

    private static Object[] adjustArgs(Optional<List<Object>> args) {
        return args.<Object[]>map(arg -> arg.isEmpty() ? NO_ARGS : arg.toArray()).orElse(NO_ARGS);
    }

    private static TranslatableContents create(String key, Optional<String> fallback, Optional<List<Object>> args) {
        return new TranslatableContents(key, fallback.orElse(null), adjustArgs(args));
    }

    public TranslatableContents(String key, @Nullable String fallback, Object[] args) {
        this.key = key;
        this.fallback = fallback;
        this.args = args;
    }

    @Override
    public ComponentContents.Type<?> type() {
        return TYPE;
    }

    private void decompose() {
        Language language = Language.getInstance();
        if (language != this.decomposedWith) {
            this.decomposedWith = language;
            String string = this.fallback != null ? language.getOrDefault(this.key, this.fallback) : language.getOrDefault(this.key);

            try {
                Builder<FormattedText> builder = ImmutableList.builder();
                this.decomposeTemplate(string, builder::add);
                this.decomposedParts = builder.build();
            } catch (TranslatableFormatException translatableFormatException) {
                this.decomposedParts = ImmutableList.of(FormattedText.of(string));
            }
        }
    }

    private void decomposeTemplate(String formatTemplate, Consumer<FormattedText> consumer) {
        Matcher matcher = FORMAT_PATTERN.matcher(formatTemplate);

        try {
            int i = 0;
            int j = 0;

            while (matcher.find(j)) {
                int k = matcher.start();
                int l = matcher.end();
                if (k > j) {
                    String string = formatTemplate.substring(j, k);
                    if (string.indexOf(37) != -1) {
                        throw new IllegalArgumentException();
                    }

                    consumer.accept(FormattedText.of(string));
                }

                String string = matcher.group(2);
                String string2 = formatTemplate.substring(k, l);
                if ("%".equals(string) && "%%".equals(string2)) {
                    consumer.accept(TEXT_PERCENT);
                } else {
                    if (!"s".equals(string)) {
                        throw new TranslatableFormatException(this, "Unsupported format: '" + string2 + "'");
                    }

                    String string3 = matcher.group(1);
                    int m = string3 != null ? Integer.parseInt(string3) - 1 : i++;
                    consumer.accept(this.getArgument(m));
                }

                j = l;
            }

            if (j < formatTemplate.length()) {
                String string4 = formatTemplate.substring(j);
                if (string4.indexOf(37) != -1) {
                    throw new IllegalArgumentException();
                }

                consumer.accept(FormattedText.of(string4));
            }
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new TranslatableFormatException(this, illegalArgumentException);
        }
    }

    private FormattedText getArgument(int index) {
        if (index >= 0 && index < this.args.length) {
            Object object = this.args[index];
            if (object instanceof Component component) {
                return component;
            } else {
                return object == null ? TEXT_NULL : FormattedText.of(object.toString());
            }
        } else {
            throw new TranslatableFormatException(this, index);
        }
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
        this.decompose();

        for (FormattedText formattedText : this.decomposedParts) {
            Optional<T> optional = formattedText.visit(styledContentConsumer, style);
            if (optional.isPresent()) {
                return optional;
            }
        }

        return Optional.empty();
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
        this.decompose();

        for (FormattedText formattedText : this.decomposedParts) {
            Optional<T> optional = formattedText.visit(contentConsumer);
            if (optional.isPresent()) {
                return optional;
            }
        }

        return Optional.empty();
    }

    @Override
    public MutableComponent resolve(@Nullable CommandSourceStack nbtPathPattern, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        Object[] objects = new Object[this.args.length];

        for (int i = 0; i < objects.length; i++) {
            Object object = this.args[i];
            if (object instanceof Component component) {
                objects[i] = ComponentUtils.updateForEntity(nbtPathPattern, component, entity, recursionDepth);
            } else {
                objects[i] = object;
            }
        }

        return MutableComponent.create(new TranslatableContents(this.key, this.fallback, objects));
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            ? true
            : other instanceof TranslatableContents translatableContents
                && Objects.equals(this.key, translatableContents.key)
                && Objects.equals(this.fallback, translatableContents.fallback)
                && Arrays.equals(this.args, translatableContents.args);
    }

    @Override
    public int hashCode() {
        int i = Objects.hashCode(this.key);
        i = 31 * i + Objects.hashCode(this.fallback);
        return 31 * i + Arrays.hashCode(this.args);
    }

    @Override
    public String toString() {
        return "translation{key='"
            + this.key
            + "'"
            + (this.fallback != null ? ", fallback='" + this.fallback + "'" : "")
            + ", args="
            + Arrays.toString(this.args)
            + "}";
    }

    public String getKey() {
        return this.key;
    }

    @Nullable
    public String getFallback() {
        return this.fallback;
    }

    public Object[] getArgs() {
        return this.args;
    }
}
