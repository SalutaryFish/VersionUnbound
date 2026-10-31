package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

/**
 * A possibly unbounded range of integers based on {@link LootContext}. Minimum and maximum are given in the form of {@link NumberProvider}s.
 * Minimum and maximum are both optional. If given, they are both inclusive.
 */
public class IntRange {
    private static final Codec<IntRange> RECORD_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                NumberProviders.CODEC.optionalFieldOf("min").forGetter(intRange -> Optional.ofNullable(intRange.min)),
                NumberProviders.CODEC.optionalFieldOf("max").forGetter(intRange -> Optional.ofNullable(intRange.max))
            )
            .apply(instance, IntRange::new)
    );
    public static final Codec<IntRange> CODEC = Codec.either(Codec.INT, RECORD_CODEC)
        .xmap(either -> either.map(IntRange::exact, Function.identity()), intRange -> {
            OptionalInt optionalInt = intRange.unpackExact();
            return optionalInt.isPresent() ? Either.left(optionalInt.getAsInt()) : Either.right(intRange);
        });
    @Nullable
    private final NumberProvider min;
    @Nullable
    private final NumberProvider max;
    private final IntRange.IntLimiter limiter;
    private final IntRange.IntChecker predicate;

    public Set<LootContextParam<?>> getReferencedContextParams() {
        Builder<LootContextParam<?>> builder = ImmutableSet.builder();
        if (this.min != null) {
            builder.addAll(this.min.getReferencedContextParams());
        }

        if (this.max != null) {
            builder.addAll(this.max.getReferencedContextParams());
        }

        return builder.build();
    }

    private IntRange(Optional<NumberProvider> min, Optional<NumberProvider> max) {
        this(min.orElse(null), max.orElse(null));
    }

    private IntRange(@Nullable NumberProvider min, @Nullable NumberProvider max) {
        this.min = min;
        this.max = max;
        if (min == null) {
            if (max == null) {
                this.limiter = (lootContext, i) -> i;
                this.predicate = (lootContext, i) -> true;
            } else {
                this.limiter = (lootContext, i) -> Math.min(max.getInt(lootContext), i);
                this.predicate = (lootContext, i) -> i <= max.getInt(lootContext);
            }
        } else if (max == null) {
            this.limiter = (lootContext, i) -> Math.max(min.getInt(lootContext), i);
            this.predicate = (lootContext, i) -> i >= min.getInt(lootContext);
        } else {
            this.limiter = (lootContext, i) -> Mth.clamp(i, min.getInt(lootContext), max.getInt(lootContext));
            this.predicate = (lootContext, i) -> i >= min.getInt(lootContext) && i <= max.getInt(lootContext);
        }
    }

    /**
     * Create an IntRange that contains only exactly the given value.
     */
    public static IntRange exact(int exactValue) {
        ConstantValue constantValue = ConstantValue.exactly(exactValue);
        return new IntRange(Optional.of(constantValue), Optional.of(constantValue));
    }

    /**
     * Create an IntRange that ranges from {@code min} to {@code max}, both inclusive.
     */
    public static IntRange range(int min, int max) {
        return new IntRange(Optional.of(ConstantValue.exactly(min)), Optional.of(ConstantValue.exactly(max)));
    }

    /**
     * Create an IntRange with the given minimum (inclusive) and no upper bound.
     */
    public static IntRange lowerBound(int min) {
        return new IntRange(Optional.of(ConstantValue.exactly(min)), Optional.empty());
    }

    /**
     * Create an IntRange with the given maximum (inclusive) and no lower bound.
     */
    public static IntRange upperBound(int max) {
        return new IntRange(Optional.empty(), Optional.of(ConstantValue.exactly(max)));
    }

    /**
     * Clamp the given value so that it falls within this IntRange.
     */
    public int clamp(LootContext lootContext, int value) {
        return this.limiter.apply(lootContext, value);
    }

    /**
     * Check whether the given value falls within this IntRange.
     */
    public boolean test(LootContext lootContext, int value) {
        return this.predicate.test(lootContext, value);
    }

    private OptionalInt unpackExact() {
        return Objects.equals(this.min, this.max)
                && this.min instanceof ConstantValue constantValue
                && Math.floor(constantValue.value()) == constantValue.value()
            ? OptionalInt.of((int)constantValue.value())
            : OptionalInt.empty();
    }

    @FunctionalInterface
    interface IntChecker {
        boolean test(LootContext var1, int var2);
    }

    @FunctionalInterface
    interface IntLimiter {
        int apply(LootContext var1, int var2);
    }
}
