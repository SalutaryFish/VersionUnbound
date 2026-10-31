package net.minecraft.client;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractOptionSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.OptionEnum;
import org.slf4j.Logger;

public final class OptionInstance<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final OptionInstance.Enum<Boolean> BOOLEAN_VALUES = new OptionInstance.Enum<>(ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL);
    public static final OptionInstance.CaptionBasedToString<Boolean> BOOLEAN_TO_STRING = (caption, value) -> value
        ? CommonComponents.OPTION_ON
        : CommonComponents.OPTION_OFF;
    private final OptionInstance.TooltipSupplier<T> tooltip;
    final Function<T, Component> toString;
    private final OptionInstance.ValueSet<T> values;
    private final Codec<T> codec;
    private final T initialValue;
    private final Consumer<T> onValueUpdate;
    final Component caption;
    T value;

    public static OptionInstance<Boolean> createBoolean(String key, boolean initialValue, Consumer<Boolean> onValueUpdate) {
        return createBoolean(key, noTooltip(), initialValue, onValueUpdate);
    }

    public static OptionInstance<Boolean> createBoolean(String key, boolean initialValue) {
        return createBoolean(key, noTooltip(), initialValue, value -> {});
    }

    public static OptionInstance<Boolean> createBoolean(String caption, OptionInstance.TooltipSupplier<Boolean> tooltip, boolean initialValue) {
        return createBoolean(caption, tooltip, initialValue, value -> {});
    }

    public static OptionInstance<Boolean> createBoolean(
        String caption, OptionInstance.TooltipSupplier<Boolean> tooltip, boolean initialValue, Consumer<Boolean> onValueUpdate
    ) {
        return createBoolean(caption, tooltip, BOOLEAN_TO_STRING, initialValue, onValueUpdate);
    }

    public static OptionInstance<Boolean> createBoolean(
        String caption,
        OptionInstance.TooltipSupplier<Boolean> tooltip,
        OptionInstance.CaptionBasedToString<Boolean> valueStringifier,
        boolean initialValue,
        Consumer<Boolean> onValueUpdate
    ) {
        return new OptionInstance<>(caption, tooltip, valueStringifier, BOOLEAN_VALUES, initialValue, onValueUpdate);
    }

    public OptionInstance(
        String caption,
        OptionInstance.TooltipSupplier<T> tooltip,
        OptionInstance.CaptionBasedToString<T> valueStringifier,
        OptionInstance.ValueSet<T> values,
        T initialValue,
        Consumer<T> onValueUpdate
    ) {
        this(caption, tooltip, valueStringifier, values, values.codec(), initialValue, onValueUpdate);
    }

    public OptionInstance(
        String caption,
        OptionInstance.TooltipSupplier<T> tooltip,
        OptionInstance.CaptionBasedToString<T> valueStringifier,
        OptionInstance.ValueSet<T> values,
        Codec<T> codec,
        T initialValue,
        Consumer<T> onValueUpdate
    ) {
        this.caption = Component.translatable(caption);
        this.tooltip = tooltip;
        this.toString = value -> valueStringifier.toString(this.caption, value);
        this.values = values;
        this.codec = codec;
        this.initialValue = initialValue;
        this.onValueUpdate = onValueUpdate;
        this.value = this.initialValue;
    }

    public static <T> OptionInstance.TooltipSupplier<T> noTooltip() {
        return value -> null;
    }

    public static <T> OptionInstance.TooltipSupplier<T> cachedConstantTooltip(Component message) {
        return value -> Tooltip.create(message);
    }

    public static <T extends OptionEnum> OptionInstance.CaptionBasedToString<T> forOptionEnum() {
        return (caption, optionEnum) -> optionEnum.getCaption();
    }

    public AbstractWidget createButton(Options options) {
        return this.createButton(options, 0, 0, 150);
    }

    public AbstractWidget createButton(Options options, int x, int y, int width) {
        return this.createButton(options, x, y, width, value -> {});
    }

    public AbstractWidget createButton(Options options, int x, int y, int width, Consumer<T> onValueChanged) {
        return this.values.createButton(this.tooltip, options, x, y, width, onValueChanged).apply(this);
    }

    public T get() {
        return this.value;
    }

    public Codec<T> codec() {
        return this.codec;
    }

    @Override
    public String toString() {
        return this.caption.getString();
    }

    public void set(T value) {
        T object = this.values.validateValue(value).orElseGet(() -> {
            LOGGER.error("Illegal option value " + value + " for " + this.caption);
            return this.initialValue;
        });
        if (!Minecraft.getInstance().isRunning()) {
            this.value = object;
        } else {
            if (!Objects.equals(this.value, object)) {
                this.value = object;
                this.onValueUpdate.accept(this.value);
            }
        }
    }

    public OptionInstance.ValueSet<T> values() {
        return this.values;
    }

    public record AltEnum<T>(
        List<T> values, List<T> altValues, BooleanSupplier altCondition, OptionInstance.CycleableValueSet.ValueSetter<T> valueSetter, Codec<T> codec
    ) implements OptionInstance.CycleableValueSet<T> {
        @Override
        public CycleButton.ValueListSupplier<T> valueListSupplier() {
            return CycleButton.ValueListSupplier.create(this.altCondition, this.values, this.altValues);
        }

        @Override
        public Optional<T> validateValue(T object) {
            return (this.altCondition.getAsBoolean() ? this.altValues : this.values).contains(object) ? Optional.of(object) : Optional.empty();
        }
    }

    public interface CaptionBasedToString<T> {
        Component toString(Component var1, T var2);
    }

    public record ClampingLazyMaxIntRange(int minInclusive, IntSupplier maxSupplier, int encodableMaxInclusive)
        implements OptionInstance.IntRangeBase,
        OptionInstance.SliderableOrCyclableValueSet<Integer> {
        public Optional<Integer> validateValue(Integer value) {
            return Optional.of(Mth.clamp(value, this.minInclusive(), this.maxInclusive()));
        }

        @Override
        public int maxInclusive() {
            return this.maxSupplier.getAsInt();
        }

        @Override
        public Codec<Integer> codec() {
            return Codec.INT
                .validate(
                    integer -> {
                        int i = this.encodableMaxInclusive + 1;
                        return integer.compareTo(this.minInclusive) >= 0 && integer.compareTo(i) <= 0
                            ? DataResult.success(integer)
                            : DataResult.error(() -> "Value " + integer + " outside of range [" + this.minInclusive + ":" + i + "]", integer);
                    }
                );
        }

        @Override
        public boolean createCycleButton() {
            return true;
        }

        @Override
        public CycleButton.ValueListSupplier<Integer> valueListSupplier() {
            return CycleButton.ValueListSupplier.create(IntStream.range(this.minInclusive, this.maxInclusive() + 1).boxed().toList());
        }
    }

    interface CycleableValueSet<T> extends OptionInstance.ValueSet<T> {
        CycleButton.ValueListSupplier<T> valueListSupplier();

        default OptionInstance.CycleableValueSet.ValueSetter<T> valueSetter() {
            return OptionInstance::set;
        }

        @Override
        default Function<OptionInstance<T>, AbstractWidget> createButton(
            OptionInstance.TooltipSupplier<T> tooltipSupplier, Options options, int x, int y, int width, Consumer<T> onValueChanged
        ) {
            return optionInstance -> CycleButton.builder(optionInstance.toString)
                .withValues(this.valueListSupplier())
                .withTooltip(tooltipSupplier)
                .withInitialValue(optionInstance.value)
                .create(x, y, width, 20, optionInstance.caption, (cycleButton, object) -> {
                    this.valueSetter().set(optionInstance, object);
                    options.save();
                    onValueChanged.accept(object);
                });
        }

        interface ValueSetter<T> {
            void set(OptionInstance<T> var1, T var2);
        }
    }

    public record Enum<T>(List<T> values, Codec<T> codec) implements OptionInstance.CycleableValueSet<T> {
        @Override
        public Optional<T> validateValue(T object) {
            return this.values.contains(object) ? Optional.of(object) : Optional.empty();
        }

        @Override
        public CycleButton.ValueListSupplier<T> valueListSupplier() {
            return CycleButton.ValueListSupplier.create(this.values);
        }
    }

    public record IntRange(int minInclusive, int maxInclusive, boolean applyValueImmediately) implements OptionInstance.IntRangeBase {
        public IntRange(int minInclusive, int maxInclusive) {
            this(minInclusive, maxInclusive, true);
        }

        public Optional<Integer> validateValue(Integer value) {
            return value.compareTo(this.minInclusive()) >= 0 && value.compareTo(this.maxInclusive()) <= 0 ? Optional.of(value) : Optional.empty();
        }

        @Override
        public Codec<Integer> codec() {
            return Codec.intRange(this.minInclusive, this.maxInclusive + 1);
        }
    }

    interface IntRangeBase extends OptionInstance.SliderableValueSet<Integer> {
        int minInclusive();

        int maxInclusive();

        default double toSliderValue(Integer value) {
            if (value == this.minInclusive()) {
                return 0.0;
            } else {
                return value == this.maxInclusive() ? 1.0 : Mth.map(value.intValue() + 0.5, this.minInclusive(), this.maxInclusive() + 1.0, 0.0, 1.0);
            }
        }

        default Integer fromSliderValue(double value) {
            if (value >= 1.0) {
                value = 0.99999F;
            }

            return Mth.floor(Mth.map(value, 0.0, 1.0, this.minInclusive(), this.maxInclusive() + 1.0));
        }

        default <R> OptionInstance.SliderableValueSet<R> xmap(IntFunction<? extends R> to, ToIntFunction<? super R> from) {
            return new OptionInstance.SliderableValueSet<R>() {
                @Override
                public Optional<R> validateValue(R object) {
                    return IntRangeBase.this.validateValue(from.applyAsInt(object)).map(to::apply);
                }

                @Override
                public double toSliderValue(R object) {
                    return IntRangeBase.this.toSliderValue(from.applyAsInt(object));
                }

                @Override
                public R fromSliderValue(double d) {
                    return (R)to.apply(IntRangeBase.this.fromSliderValue(d));
                }

                @Override
                public Codec<R> codec() {
                    return IntRangeBase.this.codec().xmap(to::apply, from::applyAsInt);
                }
            };
        }
    }

    public record LazyEnum<T>(Supplier<List<T>> values, Function<T, Optional<T>> validateValue, Codec<T> codec) implements OptionInstance.CycleableValueSet<T> {
        @Override
        public Optional<T> validateValue(T object) {
            return this.validateValue.apply(object);
        }

        @Override
        public CycleButton.ValueListSupplier<T> valueListSupplier() {
            return CycleButton.ValueListSupplier.create(this.values.get());
        }
    }

    public static final class OptionInstanceSliderButton<N> extends AbstractOptionSliderButton {
        private final OptionInstance<N> instance;
        private final OptionInstance.SliderableValueSet<N> values;
        private final OptionInstance.TooltipSupplier<N> tooltipSupplier;
        private final Consumer<N> onValueChanged;
        @Nullable
        private Long delayedApplyAt;
        private final boolean applyValueImmediately;

        OptionInstanceSliderButton(
            Options options,
            int x,
            int y,
            int width,
            int height,
            OptionInstance<N> instance,
            OptionInstance.SliderableValueSet<N> values,
            OptionInstance.TooltipSupplier<N> tooltipSupplier,
            Consumer<N> onValueChanged,
            boolean applyValueImmediately
        ) {
            super(options, x, y, width, height, values.toSliderValue(instance.get()));
            this.instance = instance;
            this.values = values;
            this.tooltipSupplier = tooltipSupplier;
            this.onValueChanged = onValueChanged;
            this.applyValueImmediately = applyValueImmediately;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(this.instance.toString.apply(this.values.fromSliderValue(this.value)));
            this.setTooltip(this.tooltipSupplier.apply(this.values.fromSliderValue(this.value)));
        }

        @Override
        protected void applyValue() {
            if (this.applyValueImmediately) {
                this.applyUnsavedValue();
            } else {
                this.delayedApplyAt = Util.getMillis() + 600L;
            }
        }

        public void applyUnsavedValue() {
            N object = this.values.fromSliderValue(this.value);
            if (!Objects.equals(object, this.instance.get())) {
                this.instance.set(object);
                this.options.save();
                this.onValueChanged.accept(this.instance.get());
            }
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            if (this.delayedApplyAt != null && Util.getMillis() >= this.delayedApplyAt) {
                this.delayedApplyAt = null;
                this.applyUnsavedValue();
            }
        }
    }

    interface SliderableOrCyclableValueSet<T> extends OptionInstance.CycleableValueSet<T>, OptionInstance.SliderableValueSet<T> {
        boolean createCycleButton();

        @Override
        default Function<OptionInstance<T>, AbstractWidget> createButton(
            OptionInstance.TooltipSupplier<T> tooltipSupplier, Options options, int i, int j, int k, Consumer<T> consumer
        ) {
            return this.createCycleButton()
                ? OptionInstance.CycleableValueSet.super.createButton(tooltipSupplier, options, i, j, k, consumer)
                : OptionInstance.SliderableValueSet.super.createButton(tooltipSupplier, options, i, j, k, consumer);
        }
    }

    interface SliderableValueSet<T> extends OptionInstance.ValueSet<T> {
        double toSliderValue(T var1);

        T fromSliderValue(double var1);

        default boolean applyValueImmediately() {
            return true;
        }

        @Override
        default Function<OptionInstance<T>, AbstractWidget> createButton(
            OptionInstance.TooltipSupplier<T> tooltipSupplier, Options options, int x, int y, int width, Consumer<T> onValueChanged
        ) {
            return instance -> new OptionInstance.OptionInstanceSliderButton<>(
                options, x, y, width, 20, instance, this, tooltipSupplier, onValueChanged, this.applyValueImmediately()
            );
        }
    }

    @FunctionalInterface
    public interface TooltipSupplier<T> {
        @Nullable
        Tooltip apply(T var1);
    }

    public enum UnitDouble implements OptionInstance.SliderableValueSet<Double> {
        INSTANCE;

        public Optional<Double> validateValue(Double value) {
            return value >= 0.0 && value <= 1.0 ? Optional.of(value) : Optional.empty();
        }

        public double toSliderValue(Double value) {
            return value;
        }

        public Double fromSliderValue(double value) {
            return value;
        }

        public <R> OptionInstance.SliderableValueSet<R> xmap(DoubleFunction<? extends R> encoder, ToDoubleFunction<? super R> decoder) {
            return new OptionInstance.SliderableValueSet<R>() {
                @Override
                public Optional<R> validateValue(R object) {
                    return UnitDouble.this.validateValue(decoder.applyAsDouble(object)).map(encoder::apply);
                }

                @Override
                public double toSliderValue(R object) {
                    return UnitDouble.this.toSliderValue(decoder.applyAsDouble(object));
                }

                @Override
                public R fromSliderValue(double d) {
                    return (R)encoder.apply(UnitDouble.this.fromSliderValue(d));
                }

                @Override
                public Codec<R> codec() {
                    return UnitDouble.this.codec().xmap(encoder::apply, decoder::applyAsDouble);
                }
            };
        }

        @Override
        public Codec<Double> codec() {
            return Codec.withAlternative(Codec.doubleRange(0.0, 1.0), Codec.BOOL, boolean_ -> boolean_ ? 1.0 : 0.0);
        }
    }

    interface ValueSet<T> {
        Function<OptionInstance<T>, AbstractWidget> createButton(
            OptionInstance.TooltipSupplier<T> var1, Options var2, int var3, int var4, int var5, Consumer<T> var6
        );

        Optional<T> validateValue(T var1);

        Codec<T> codec();
    }
}
