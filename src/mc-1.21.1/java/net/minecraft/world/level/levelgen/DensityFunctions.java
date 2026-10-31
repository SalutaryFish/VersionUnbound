package net.minecraft.world.level.levelgen;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.ToFloatFunction;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.slf4j.Logger;

public final class DensityFunctions {
    private static final Codec<DensityFunction> CODEC = BuiltInRegistries.DENSITY_FUNCTION_TYPE
        .byNameCodec()
        .dispatch(densityFunction -> densityFunction.codec().codec(), Function.identity());
    protected static final double MAX_REASONABLE_NOISE_VALUE = 1000000.0;
    static final Codec<Double> NOISE_VALUE_CODEC = Codec.doubleRange(-1000000.0, 1000000.0);
    public static final Codec<DensityFunction> DIRECT_CODEC = Codec.either(NOISE_VALUE_CODEC, CODEC)
        .xmap(
            either -> either.map(DensityFunctions::constant, Function.identity()),
            densityFunction -> densityFunction instanceof DensityFunctions.Constant constant ? Either.left(constant.value()) : Either.right(densityFunction)
        );

    public static MapCodec<? extends DensityFunction> bootstrap(Registry<MapCodec<? extends DensityFunction>> registry) {
        register(registry, "blend_alpha", DensityFunctions.BlendAlpha.CODEC);
        register(registry, "blend_offset", DensityFunctions.BlendOffset.CODEC);
        register(registry, "beardifier", DensityFunctions.BeardifierMarker.CODEC);
        register(registry, "old_blended_noise", BlendedNoise.CODEC);

        for (DensityFunctions.Marker.Type type : DensityFunctions.Marker.Type.values()) {
            register(registry, type.getSerializedName(), type.codec);
        }

        register(registry, "noise", DensityFunctions.Noise.CODEC);
        register(registry, "end_islands", DensityFunctions.EndIslandDensityFunction.CODEC);
        register(registry, "weird_scaled_sampler", DensityFunctions.WeirdScaledSampler.CODEC);
        register(registry, "shifted_noise", DensityFunctions.ShiftedNoise.CODEC);
        register(registry, "range_choice", DensityFunctions.RangeChoice.CODEC);
        register(registry, "shift_a", DensityFunctions.ShiftA.CODEC);
        register(registry, "shift_b", DensityFunctions.ShiftB.CODEC);
        register(registry, "shift", DensityFunctions.Shift.CODEC);
        register(registry, "blend_density", DensityFunctions.BlendDensity.CODEC);
        register(registry, "clamp", DensityFunctions.Clamp.CODEC);

        for (DensityFunctions.Mapped.Type type2 : DensityFunctions.Mapped.Type.values()) {
            register(registry, type2.getSerializedName(), type2.codec);
        }

        for (DensityFunctions.TwoArgumentSimpleFunction.Type type3 : DensityFunctions.TwoArgumentSimpleFunction.Type.values()) {
            register(registry, type3.getSerializedName(), type3.codec);
        }

        register(registry, "spline", DensityFunctions.Spline.CODEC);
        register(registry, "constant", DensityFunctions.Constant.CODEC);
        return register(registry, "y_clamped_gradient", DensityFunctions.YClampedGradient.CODEC);
    }

    private static MapCodec<? extends DensityFunction> register(
        Registry<MapCodec<? extends DensityFunction>> registry, String name, KeyDispatchDataCodec<? extends DensityFunction> codec
    ) {
        return Registry.register(registry, name, codec.codec());
    }

    static <A, O> KeyDispatchDataCodec<O> singleArgumentCodec(Codec<A> codec, Function<A, O> fromFunction, Function<O, A> toFunction) {
        return KeyDispatchDataCodec.of(codec.fieldOf("argument").xmap(fromFunction, toFunction));
    }

    static <O> KeyDispatchDataCodec<O> singleFunctionArgumentCodec(Function<DensityFunction, O> fromFunction, Function<O, DensityFunction> toFunction) {
        return singleArgumentCodec(DensityFunction.HOLDER_HELPER_CODEC, fromFunction, toFunction);
    }

    static <O> KeyDispatchDataCodec<O> doubleFunctionArgumentCodec(
        BiFunction<DensityFunction, DensityFunction, O> fromFunction, Function<O, DensityFunction> primary, Function<O, DensityFunction> secondary
    ) {
        return KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument1").forGetter(primary),
                        DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument2").forGetter(secondary)
                    )
                    .apply(instance, fromFunction)
            )
        );
    }

    static <O> KeyDispatchDataCodec<O> makeCodec(MapCodec<O> mapCodec) {
        return KeyDispatchDataCodec.of(mapCodec);
    }

    private DensityFunctions() {
    }

    public static DensityFunction interpolated(DensityFunction wrapped) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.Interpolated, wrapped);
    }

    public static DensityFunction flatCache(DensityFunction wrapped) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.FlatCache, wrapped);
    }

    public static DensityFunction cache2d(DensityFunction wrapped) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.Cache2D, wrapped);
    }

    public static DensityFunction cacheOnce(DensityFunction wrapped) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.CacheOnce, wrapped);
    }

    public static DensityFunction cacheAllInCell(DensityFunction wrapped) {
        return new DensityFunctions.Marker(DensityFunctions.Marker.Type.CacheAllInCell, wrapped);
    }

    public static DensityFunction mappedNoise(
        Holder<NormalNoise.NoiseParameters> noiseData, @Deprecated double xzScale, double yScale, double fromY, double toY
    ) {
        return mapFromUnitTo(new DensityFunctions.Noise(new DensityFunction.NoiseHolder(noiseData), xzScale, yScale), fromY, toY);
    }

    public static DensityFunction mappedNoise(Holder<NormalNoise.NoiseParameters> noiseData, double yScale, double fromY, double toY) {
        return mappedNoise(noiseData, 1.0, yScale, fromY, toY);
    }

    public static DensityFunction mappedNoise(Holder<NormalNoise.NoiseParameters> noiseData, double fromY, double toY) {
        return mappedNoise(noiseData, 1.0, 1.0, fromY, toY);
    }

    public static DensityFunction shiftedNoise2d(DensityFunction shiftX, DensityFunction shiftZ, double xzScale, Holder<NormalNoise.NoiseParameters> noiseData) {
        return new DensityFunctions.ShiftedNoise(shiftX, zero(), shiftZ, xzScale, 0.0, new DensityFunction.NoiseHolder(noiseData));
    }

    public static DensityFunction noise(Holder<NormalNoise.NoiseParameters> noiseData) {
        return noise(noiseData, 1.0, 1.0);
    }

    public static DensityFunction noise(Holder<NormalNoise.NoiseParameters> noiseData, double xzScale, double yScale) {
        return new DensityFunctions.Noise(new DensityFunction.NoiseHolder(noiseData), xzScale, yScale);
    }

    public static DensityFunction noise(Holder<NormalNoise.NoiseParameters> noiseData, double yScale) {
        return noise(noiseData, 1.0, yScale);
    }

    public static DensityFunction rangeChoice(
        DensityFunction input, double minInclusive, double maxExclusive, DensityFunction whenInRange, DensityFunction whenOutOfRange
    ) {
        return new DensityFunctions.RangeChoice(input, minInclusive, maxExclusive, whenInRange, whenOutOfRange);
    }

    public static DensityFunction shiftA(Holder<NormalNoise.NoiseParameters> noiseData) {
        return new DensityFunctions.ShiftA(new DensityFunction.NoiseHolder(noiseData));
    }

    public static DensityFunction shiftB(Holder<NormalNoise.NoiseParameters> noiseData) {
        return new DensityFunctions.ShiftB(new DensityFunction.NoiseHolder(noiseData));
    }

    public static DensityFunction shift(Holder<NormalNoise.NoiseParameters> noiseData) {
        return new DensityFunctions.Shift(new DensityFunction.NoiseHolder(noiseData));
    }

    public static DensityFunction blendDensity(DensityFunction input) {
        return new DensityFunctions.BlendDensity(input);
    }

    public static DensityFunction endIslands(long seed) {
        return new DensityFunctions.EndIslandDensityFunction(seed);
    }

    public static DensityFunction weirdScaledSampler(
        DensityFunction input, Holder<NormalNoise.NoiseParameters> noiseData, DensityFunctions.WeirdScaledSampler.RarityValueMapper rarityValueMapper
    ) {
        return new DensityFunctions.WeirdScaledSampler(input, new DensityFunction.NoiseHolder(noiseData), rarityValueMapper);
    }

    public static DensityFunction add(DensityFunction argument1, DensityFunction argument2) {
        return DensityFunctions.TwoArgumentSimpleFunction.create(DensityFunctions.TwoArgumentSimpleFunction.Type.ADD, argument1, argument2);
    }

    public static DensityFunction mul(DensityFunction argument1, DensityFunction argument2) {
        return DensityFunctions.TwoArgumentSimpleFunction.create(DensityFunctions.TwoArgumentSimpleFunction.Type.MUL, argument1, argument2);
    }

    public static DensityFunction min(DensityFunction argument1, DensityFunction argument2) {
        return DensityFunctions.TwoArgumentSimpleFunction.create(DensityFunctions.TwoArgumentSimpleFunction.Type.MIN, argument1, argument2);
    }

    public static DensityFunction max(DensityFunction argument1, DensityFunction argument2) {
        return DensityFunctions.TwoArgumentSimpleFunction.create(DensityFunctions.TwoArgumentSimpleFunction.Type.MAX, argument1, argument2);
    }

    public static DensityFunction spline(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline) {
        return new DensityFunctions.Spline(spline);
    }

    public static DensityFunction zero() {
        return DensityFunctions.Constant.ZERO;
    }

    public static DensityFunction constant(double value) {
        return new DensityFunctions.Constant(value);
    }

    public static DensityFunction yClampedGradient(int fromY, int toY, double fromValue, double toValue) {
        return new DensityFunctions.YClampedGradient(fromY, toY, fromValue, toValue);
    }

    public static DensityFunction map(DensityFunction input, DensityFunctions.Mapped.Type type) {
        return DensityFunctions.Mapped.create(type, input);
    }

    private static DensityFunction mapFromUnitTo(DensityFunction densityFunction, double fromY, double toY) {
        double d = (fromY + toY) * 0.5;
        double e = (toY - fromY) * 0.5;
        return add(constant(d), mul(constant(e), densityFunction));
    }

    public static DensityFunction blendAlpha() {
        return DensityFunctions.BlendAlpha.INSTANCE;
    }

    public static DensityFunction blendOffset() {
        return DensityFunctions.BlendOffset.INSTANCE;
    }

    public static DensityFunction lerp(DensityFunction deltaFunction, DensityFunction minFunction, DensityFunction maxFunction) {
        if (minFunction instanceof DensityFunctions.Constant constant) {
            return lerp(deltaFunction, constant.value, maxFunction);
        } else {
            DensityFunction densityFunction = cacheOnce(deltaFunction);
            DensityFunction densityFunction2 = add(mul(densityFunction, constant(-1.0)), constant(1.0));
            return add(mul(minFunction, densityFunction2), mul(maxFunction, densityFunction));
        }
    }

    public static DensityFunction lerp(DensityFunction deltaFunction, double min, DensityFunction maxFunction) {
        return add(mul(deltaFunction, add(maxFunction, constant(-min))), constant(min));
    }

    record Ap2(DensityFunctions.TwoArgumentSimpleFunction.Type type, DensityFunction argument1, DensityFunction argument2, double minValue, double maxValue)
        implements DensityFunctions.TwoArgumentSimpleFunction {
        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            double d = this.argument1.compute(functionContext);

            return switch (this.type) {
                case ADD -> d + this.argument2.compute(functionContext);
                case MUL -> d == 0.0 ? 0.0 : d * this.argument2.compute(functionContext);
                case MIN -> d < this.argument2.minValue() ? d : Math.min(d, this.argument2.compute(functionContext));
                case MAX -> d > this.argument2.maxValue() ? d : Math.max(d, this.argument2.compute(functionContext));
            };
        }

        @Override
        public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
            this.argument1.fillArray(ds, contextProvider);
            switch (this.type) {
                case ADD:
                    double[] es = new double[ds.length];
                    this.argument2.fillArray(es, contextProvider);

                    for (int i = 0; i < ds.length; i++) {
                        ds[i] += es[i];
                    }
                    break;
                case MUL:
                    for (int j = 0; j < ds.length; j++) {
                        double d = ds[j];
                        ds[j] = d == 0.0 ? 0.0 : d * this.argument2.compute(contextProvider.forIndex(j));
                    }
                    break;
                case MIN:
                    double e = this.argument2.minValue();

                    for (int k = 0; k < ds.length; k++) {
                        double f = ds[k];
                        ds[k] = f < e ? f : Math.min(f, this.argument2.compute(contextProvider.forIndex(k)));
                    }
                    break;
                case MAX:
                    double e = this.argument2.maxValue();

                    for (int k = 0; k < ds.length; k++) {
                        double f = ds[k];
                        ds[k] = f > e ? f : Math.max(f, this.argument2.compute(contextProvider.forIndex(k)));
                    }
            }
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(DensityFunctions.TwoArgumentSimpleFunction.create(this.type, this.argument1.mapAll(visitor), this.argument2.mapAll(visitor)));
        }
    }

    protected enum BeardifierMarker implements DensityFunctions.BeardifierOrMarker {
        INSTANCE;

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            return 0.0;
        }

        @Override
        public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
            Arrays.fill(ds, 0.0);
        }

        @Override
        public double minValue() {
            return 0.0;
        }

        @Override
        public double maxValue() {
            return 0.0;
        }
    }

    public interface BeardifierOrMarker extends DensityFunction.SimpleFunction {
        KeyDispatchDataCodec<DensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(DensityFunctions.BeardifierMarker.INSTANCE));

        @Override
        default KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected enum BlendAlpha implements DensityFunction.SimpleFunction {
        INSTANCE;

        public static final KeyDispatchDataCodec<DensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            return 1.0;
        }

        @Override
        public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
            Arrays.fill(ds, 1.0);
        }

        @Override
        public double minValue() {
            return 1.0;
        }

        @Override
        public double maxValue() {
            return 1.0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    record BlendDensity(DensityFunction input) implements DensityFunctions.TransformerWithContext {
        static final KeyDispatchDataCodec<DensityFunctions.BlendDensity> CODEC = DensityFunctions.singleFunctionArgumentCodec(
            DensityFunctions.BlendDensity::new, DensityFunctions.BlendDensity::input
        );

        @Override
        public double transform(DensityFunction.FunctionContext functionContext, double d) {
            return functionContext.getBlender().blendDensity(functionContext, d);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.BlendDensity(this.input.mapAll(visitor)));
        }

        @Override
        public double minValue() {
            return Double.NEGATIVE_INFINITY;
        }

        @Override
        public double maxValue() {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected enum BlendOffset implements DensityFunction.SimpleFunction {
        INSTANCE;

        public static final KeyDispatchDataCodec<DensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            return 0.0;
        }

        @Override
        public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
            Arrays.fill(ds, 0.0);
        }

        @Override
        public double minValue() {
            return 0.0;
        }

        @Override
        public double maxValue() {
            return 0.0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected record Clamp(DensityFunction input, double minValue, double maxValue) implements DensityFunctions.PureTransformer {
        private static final MapCodec<DensityFunctions.Clamp> DATA_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    DensityFunction.DIRECT_CODEC.fieldOf("input").forGetter(DensityFunctions.Clamp::input),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("min").forGetter(DensityFunctions.Clamp::minValue),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("max").forGetter(DensityFunctions.Clamp::maxValue)
                )
                .apply(instance, DensityFunctions.Clamp::new)
        );
        public static final KeyDispatchDataCodec<DensityFunctions.Clamp> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double transform(double value) {
            return Mth.clamp(value, this.minValue, this.maxValue);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return new DensityFunctions.Clamp(this.input.mapAll(visitor), this.minValue, this.maxValue);
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    record Constant(double value) implements DensityFunction.SimpleFunction {
        static final KeyDispatchDataCodec<DensityFunctions.Constant> CODEC = DensityFunctions.singleArgumentCodec(
            DensityFunctions.NOISE_VALUE_CODEC, DensityFunctions.Constant::new, DensityFunctions.Constant::value
        );
        static final DensityFunctions.Constant ZERO = new DensityFunctions.Constant(0.0);

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            return this.value;
        }

        @Override
        public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
            Arrays.fill(ds, this.value);
        }

        @Override
        public double minValue() {
            return this.value;
        }

        @Override
        public double maxValue() {
            return this.value;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static final class EndIslandDensityFunction implements DensityFunction.SimpleFunction {
        public static final KeyDispatchDataCodec<DensityFunctions.EndIslandDensityFunction> CODEC = KeyDispatchDataCodec.of(
            MapCodec.unit(new DensityFunctions.EndIslandDensityFunction(0L))
        );
        private static final float ISLAND_THRESHOLD = -0.9F;
        private final SimplexNoise islandNoise;

        public EndIslandDensityFunction(long seed) {
            RandomSource randomSource = new LegacyRandomSource(seed);
            randomSource.consumeCount(17292);
            this.islandNoise = new SimplexNoise(randomSource);
        }

        private static float getHeightValue(SimplexNoise noise, int x, int z) {
            int i = x / 2;
            int j = z / 2;
            int k = x % 2;
            int l = z % 2;
            float f = 100.0F - Mth.sqrt(x * x + z * z) * 8.0F;
            f = Mth.clamp(f, -100.0F, 80.0F);

            for (int m = -12; m <= 12; m++) {
                for (int n = -12; n <= 12; n++) {
                    long o = i + m;
                    long p = j + n;
                    if (o * o + p * p > 4096L && noise.getValue(o, p) < -0.9F) {
                        float g = (Mth.abs((float)o) * 3439.0F + Mth.abs((float)p) * 147.0F) % 13.0F + 9.0F;
                        float h = k - m * 2;
                        float q = l - n * 2;
                        float r = 100.0F - Mth.sqrt(h * h + q * q) * g;
                        r = Mth.clamp(r, -100.0F, 80.0F);
                        f = Math.max(f, r);
                    }
                }
            }

            return f;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return (getHeightValue(this.islandNoise, context.blockX() / 8, context.blockZ() / 8) - 8.0) / 128.0;
        }

        @Override
        public double minValue() {
            return -0.84375;
        }

        @Override
        public double maxValue() {
            return 0.5625;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    @VisibleForDebug
    public record HolderHolder(Holder<DensityFunction> function) implements DensityFunction {
        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            return this.function.value().compute(functionContext);
        }

        @Override
        public void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
            this.function.value().fillArray(ds, contextProvider);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.HolderHolder(new Holder.Direct<>(this.function.value().mapAll(visitor))));
        }

        @Override
        public double minValue() {
            return this.function.isBound() ? this.function.value().minValue() : Double.NEGATIVE_INFINITY;
        }

        @Override
        public double maxValue() {
            return this.function.isBound() ? this.function.value().maxValue() : Double.POSITIVE_INFINITY;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("Calling .codec() on HolderHolder");
        }
    }

    protected record Mapped(DensityFunctions.Mapped.Type type, DensityFunction input, double minValue, double maxValue)
        implements DensityFunctions.PureTransformer {
        public static DensityFunctions.Mapped create(DensityFunctions.Mapped.Type type, DensityFunction input) {
            double d = input.minValue();
            double e = transform(type, d);
            double f = transform(type, input.maxValue());
            return type != DensityFunctions.Mapped.Type.ABS && type != DensityFunctions.Mapped.Type.SQUARE
                ? new DensityFunctions.Mapped(type, input, e, f)
                : new DensityFunctions.Mapped(type, input, Math.max(0.0, d), Math.max(e, f));
        }

        private static double transform(DensityFunctions.Mapped.Type type, double value) {
            return switch (type) {
                case ABS -> Math.abs(value);
                case SQUARE -> value * value;
                case CUBE -> value * value * value;
                case HALF_NEGATIVE -> value > 0.0 ? value : value * 0.5;
                case QUARTER_NEGATIVE -> value > 0.0 ? value : value * 0.25;
                case SQUEEZE -> {
                    double d = Mth.clamp(value, -1.0, 1.0);
                    yield d / 2.0 - d * d * d / 24.0;
                }
            };
        }

        @Override
        public double transform(double value) {
            return transform(this.type, value);
        }

        public DensityFunctions.Mapped mapAll(DensityFunction.Visitor visitor) {
            return create(this.type, this.input.mapAll(visitor));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.type.codec;
        }

        enum Type implements StringRepresentable {
            ABS("abs"),
            SQUARE("square"),
            CUBE("cube"),
            HALF_NEGATIVE("half_negative"),
            QUARTER_NEGATIVE("quarter_negative"),
            SQUEEZE("squeeze");

            private final String name;
            final KeyDispatchDataCodec<DensityFunctions.Mapped> codec = DensityFunctions.singleFunctionArgumentCodec(
                input -> DensityFunctions.Mapped.create(this, input), DensityFunctions.Mapped::input
            );

            Type(final String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }
    }

    protected record Marker(DensityFunctions.Marker.Type type, DensityFunction wrapped) implements DensityFunctions.MarkerOrMarked {
        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return this.wrapped.compute(context);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.wrapped.fillArray(array, contextProvider);
        }

        @Override
        public double minValue() {
            return this.wrapped.minValue();
        }

        @Override
        public double maxValue() {
            return this.wrapped.maxValue();
        }

        enum Type implements StringRepresentable {
            Interpolated("interpolated"),
            FlatCache("flat_cache"),
            Cache2D("cache_2d"),
            CacheOnce("cache_once"),
            CacheAllInCell("cache_all_in_cell");

            private final String name;
            final KeyDispatchDataCodec<DensityFunctions.MarkerOrMarked> codec = DensityFunctions.singleFunctionArgumentCodec(
                function -> new DensityFunctions.Marker(this, function), DensityFunctions.MarkerOrMarked::wrapped
            );

            Type(final String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }
    }

    public interface MarkerOrMarked extends DensityFunction {
        DensityFunctions.Marker.Type type();

        DensityFunction wrapped();

        @Override
        default KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.type().codec;
        }

        @Override
        default DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.Marker(this.type(), this.wrapped().mapAll(visitor)));
        }
    }

    record MulOrAdd(DensityFunctions.MulOrAdd.Type specificType, DensityFunction input, double minValue, double maxValue, double argument)
        implements DensityFunctions.PureTransformer,
        DensityFunctions.TwoArgumentSimpleFunction {
        @Override
        public DensityFunctions.TwoArgumentSimpleFunction.Type type() {
            return this.specificType == DensityFunctions.MulOrAdd.Type.MUL
                ? DensityFunctions.TwoArgumentSimpleFunction.Type.MUL
                : DensityFunctions.TwoArgumentSimpleFunction.Type.ADD;
        }

        @Override
        public DensityFunction argument1() {
            return DensityFunctions.constant(this.argument);
        }

        @Override
        public DensityFunction argument2() {
            return this.input;
        }

        @Override
        public double transform(double d) {
            return switch (this.specificType) {
                case MUL -> d * this.argument;
                case ADD -> d + this.argument;
            };
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            DensityFunction densityFunction = this.input.mapAll(visitor);
            double d = densityFunction.minValue();
            double e = densityFunction.maxValue();
            double f;
            double g;
            if (this.specificType == DensityFunctions.MulOrAdd.Type.ADD) {
                f = d + this.argument;
                g = e + this.argument;
            } else if (this.argument >= 0.0) {
                f = d * this.argument;
                g = e * this.argument;
            } else {
                f = e * this.argument;
                g = d * this.argument;
            }

            return new DensityFunctions.MulOrAdd(this.specificType, densityFunction, f, g, this.argument);
        }

        enum Type {
            MUL,
            ADD;
        }
    }

    protected record Noise(DensityFunction.NoiseHolder noise, @Deprecated double xzScale, double yScale) implements DensityFunction {
        public static final MapCodec<DensityFunctions.Noise> DATA_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(DensityFunctions.Noise::noise),
                    Codec.DOUBLE.fieldOf("xz_scale").forGetter(DensityFunctions.Noise::xzScale),
                    Codec.DOUBLE.fieldOf("y_scale").forGetter(DensityFunctions.Noise::yScale)
                )
                .apply(instance, DensityFunctions.Noise::new)
        );
        public static final KeyDispatchDataCodec<DensityFunctions.Noise> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return this.noise.getValue(context.blockX() * this.xzScale, context.blockY() * this.yScale, context.blockZ() * this.xzScale);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.Noise(visitor.visitNoise(this.noise), this.xzScale, this.yScale));
        }

        @Override
        public double minValue() {
            return -this.maxValue();
        }

        @Override
        public double maxValue() {
            return this.noise.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    interface PureTransformer extends DensityFunction {
        DensityFunction input();

        @Override
        default double compute(DensityFunction.FunctionContext context) {
            return this.transform(this.input().compute(context));
        }

        @Override
        default void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.input().fillArray(array, contextProvider);

            for (int i = 0; i < array.length; i++) {
                array[i] = this.transform(array[i]);
            }
        }

        double transform(double var1);
    }

    record RangeChoice(DensityFunction input, double minInclusive, double maxExclusive, DensityFunction whenInRange, DensityFunction whenOutOfRange)
        implements DensityFunction {
        public static final MapCodec<DensityFunctions.RangeChoice> DATA_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(DensityFunctions.RangeChoice::input),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("min_inclusive").forGetter(DensityFunctions.RangeChoice::minInclusive),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("max_exclusive").forGetter(DensityFunctions.RangeChoice::maxExclusive),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("when_in_range").forGetter(DensityFunctions.RangeChoice::whenInRange),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("when_out_of_range").forGetter(DensityFunctions.RangeChoice::whenOutOfRange)
                )
                .apply(instance, DensityFunctions.RangeChoice::new)
        );
        public static final KeyDispatchDataCodec<DensityFunctions.RangeChoice> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            double d = this.input.compute(context);
            return d >= this.minInclusive && d < this.maxExclusive ? this.whenInRange.compute(context) : this.whenOutOfRange.compute(context);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.input.fillArray(array, contextProvider);

            for (int i = 0; i < array.length; i++) {
                double d = array[i];
                if (d >= this.minInclusive && d < this.maxExclusive) {
                    array[i] = this.whenInRange.compute(contextProvider.forIndex(i));
                } else {
                    array[i] = this.whenOutOfRange.compute(contextProvider.forIndex(i));
                }
            }
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(
                new DensityFunctions.RangeChoice(
                    this.input.mapAll(visitor), this.minInclusive, this.maxExclusive, this.whenInRange.mapAll(visitor), this.whenOutOfRange.mapAll(visitor)
                )
            );
        }

        @Override
        public double minValue() {
            return Math.min(this.whenInRange.minValue(), this.whenOutOfRange.minValue());
        }

        @Override
        public double maxValue() {
            return Math.max(this.whenInRange.maxValue(), this.whenOutOfRange.maxValue());
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected record Shift(DensityFunction.NoiseHolder offsetNoise) implements DensityFunctions.ShiftNoise {
        static final KeyDispatchDataCodec<DensityFunctions.Shift> CODEC = DensityFunctions.singleArgumentCodec(
            DensityFunction.NoiseHolder.CODEC, DensityFunctions.Shift::new, DensityFunctions.Shift::offsetNoise
        );

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            return this.compute(functionContext.blockX(), functionContext.blockY(), functionContext.blockZ());
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.Shift(visitor.visitNoise(this.offsetNoise)));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected record ShiftA(DensityFunction.NoiseHolder offsetNoise) implements DensityFunctions.ShiftNoise {
        static final KeyDispatchDataCodec<DensityFunctions.ShiftA> CODEC = DensityFunctions.singleArgumentCodec(
            DensityFunction.NoiseHolder.CODEC, DensityFunctions.ShiftA::new, DensityFunctions.ShiftA::offsetNoise
        );

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            return this.compute(functionContext.blockX(), 0.0, functionContext.blockZ());
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.ShiftA(visitor.visitNoise(this.offsetNoise)));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected record ShiftB(DensityFunction.NoiseHolder offsetNoise) implements DensityFunctions.ShiftNoise {
        static final KeyDispatchDataCodec<DensityFunctions.ShiftB> CODEC = DensityFunctions.singleArgumentCodec(
            DensityFunction.NoiseHolder.CODEC, DensityFunctions.ShiftB::new, DensityFunctions.ShiftB::offsetNoise
        );

        @Override
        public double compute(DensityFunction.FunctionContext functionContext) {
            return this.compute(functionContext.blockZ(), functionContext.blockX(), 0.0);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.ShiftB(visitor.visitNoise(this.offsetNoise)));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    interface ShiftNoise extends DensityFunction {
        DensityFunction.NoiseHolder offsetNoise();

        @Override
        default double minValue() {
            return -this.maxValue();
        }

        @Override
        default double maxValue() {
            return this.offsetNoise().maxValue() * 4.0;
        }

        default double compute(double x, double y, double z) {
            return this.offsetNoise().getValue(x * 0.25, y * 0.25, z * 0.25) * 4.0;
        }

        @Override
        default void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }
    }

    protected record ShiftedNoise(
        DensityFunction shiftX, DensityFunction shiftY, DensityFunction shiftZ, double xzScale, double yScale, DensityFunction.NoiseHolder noise
    ) implements DensityFunction {
        private static final MapCodec<DensityFunctions.ShiftedNoise> DATA_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("shift_x").forGetter(DensityFunctions.ShiftedNoise::shiftX),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("shift_y").forGetter(DensityFunctions.ShiftedNoise::shiftY),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("shift_z").forGetter(DensityFunctions.ShiftedNoise::shiftZ),
                    Codec.DOUBLE.fieldOf("xz_scale").forGetter(DensityFunctions.ShiftedNoise::xzScale),
                    Codec.DOUBLE.fieldOf("y_scale").forGetter(DensityFunctions.ShiftedNoise::yScale),
                    DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(DensityFunctions.ShiftedNoise::noise)
                )
                .apply(instance, DensityFunctions.ShiftedNoise::new)
        );
        public static final KeyDispatchDataCodec<DensityFunctions.ShiftedNoise> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            double d = context.blockX() * this.xzScale + this.shiftX.compute(context);
            double e = context.blockY() * this.yScale + this.shiftY.compute(context);
            double f = context.blockZ() * this.xzScale + this.shiftZ.compute(context);
            return this.noise.getValue(d, e, f);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(
                new DensityFunctions.ShiftedNoise(
                    this.shiftX.mapAll(visitor),
                    this.shiftY.mapAll(visitor),
                    this.shiftZ.mapAll(visitor),
                    this.xzScale,
                    this.yScale,
                    visitor.visitNoise(this.noise)
                )
            );
        }

        @Override
        public double minValue() {
            return -this.maxValue();
        }

        @Override
        public double maxValue() {
            return this.noise.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    public record Spline(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline) implements DensityFunction {
        private static final Codec<CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate>> SPLINE_CODEC = CubicSpline.codec(
            DensityFunctions.Spline.Coordinate.CODEC
        );
        private static final MapCodec<DensityFunctions.Spline> DATA_CODEC = SPLINE_CODEC.fieldOf("spline")
            .xmap(DensityFunctions.Spline::new, DensityFunctions.Spline::spline);
        public static final KeyDispatchDataCodec<DensityFunctions.Spline> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return this.spline.apply(new DensityFunctions.Spline.Point(context));
        }

        @Override
        public double minValue() {
            return this.spline.minValue();
        }

        @Override
        public double maxValue() {
            return this.spline.maxValue();
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.Spline(this.spline.mapAll(coordinate -> coordinate.mapAll(visitor))));
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        public record Coordinate(Holder<DensityFunction> function) implements ToFloatFunction<DensityFunctions.Spline.Point> {
            public static final Codec<DensityFunctions.Spline.Coordinate> CODEC = DensityFunction.CODEC
                .xmap(DensityFunctions.Spline.Coordinate::new, DensityFunctions.Spline.Coordinate::function);

            @Override
            public String toString() {
                Optional<ResourceKey<DensityFunction>> optional = this.function.unwrapKey();
                if (optional.isPresent()) {
                    ResourceKey<DensityFunction> resourceKey = optional.get();
                    if (resourceKey == NoiseRouterData.CONTINENTS) {
                        return "continents";
                    }

                    if (resourceKey == NoiseRouterData.EROSION) {
                        return "erosion";
                    }

                    if (resourceKey == NoiseRouterData.RIDGES) {
                        return "weirdness";
                    }

                    if (resourceKey == NoiseRouterData.RIDGES_FOLDED) {
                        return "ridges";
                    }
                }

                return "Coordinate[" + this.function + "]";
            }

            public float apply(DensityFunctions.Spline.Point object) {
                return (float)this.function.value().compute(object.context());
            }

            @Override
            public float minValue() {
                return this.function.isBound() ? (float)this.function.value().minValue() : Float.NEGATIVE_INFINITY;
            }

            @Override
            public float maxValue() {
                return this.function.isBound() ? (float)this.function.value().maxValue() : Float.POSITIVE_INFINITY;
            }

            public DensityFunctions.Spline.Coordinate mapAll(DensityFunction.Visitor visitor) {
                return new DensityFunctions.Spline.Coordinate(new Holder.Direct<>(this.function.value().mapAll(visitor)));
            }
        }

        public record Point(DensityFunction.FunctionContext context) {
        }
    }

    interface TransformerWithContext extends DensityFunction {
        DensityFunction input();

        @Override
        default double compute(DensityFunction.FunctionContext context) {
            return this.transform(context, this.input().compute(context));
        }

        @Override
        default void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.input().fillArray(array, contextProvider);

            for (int i = 0; i < array.length; i++) {
                array[i] = this.transform(contextProvider.forIndex(i), array[i]);
            }
        }

        double transform(DensityFunction.FunctionContext var1, double var2);
    }

    interface TwoArgumentSimpleFunction extends DensityFunction {
        Logger LOGGER = LogUtils.getLogger();

        static DensityFunctions.TwoArgumentSimpleFunction create(
            DensityFunctions.TwoArgumentSimpleFunction.Type type, DensityFunction argument1, DensityFunction argument2
        ) {
            double d = argument1.minValue();
            double e = argument2.minValue();
            double f = argument1.maxValue();
            double g = argument2.maxValue();
            if (type == DensityFunctions.TwoArgumentSimpleFunction.Type.MIN || type == DensityFunctions.TwoArgumentSimpleFunction.Type.MAX) {
                boolean bl = d >= g;
                boolean bl2 = e >= f;
                if (bl || bl2) {
                    LOGGER.warn("Creating a " + type + " function between two non-overlapping inputs: " + argument1 + " and " + argument2);
                }
            }
            double h = switch (type) {
                case ADD -> d + e;
                case MUL -> d > 0.0 && e > 0.0 ? d * e : (f < 0.0 && g < 0.0 ? f * g : Math.min(d * g, f * e));
                case MIN -> Math.min(d, e);
                case MAX -> Math.max(d, e);
            };

            double i = switch (type) {
                case ADD -> f + g;
                case MUL -> d > 0.0 && e > 0.0 ? f * g : (f < 0.0 && g < 0.0 ? d * e : Math.max(d * e, f * g));
                case MIN -> Math.min(f, g);
                case MAX -> Math.max(f, g);
            };
            if (type == DensityFunctions.TwoArgumentSimpleFunction.Type.MUL || type == DensityFunctions.TwoArgumentSimpleFunction.Type.ADD) {
                if (argument1 instanceof DensityFunctions.Constant constant) {
                    return new DensityFunctions.MulOrAdd(
                        type == DensityFunctions.TwoArgumentSimpleFunction.Type.ADD ? DensityFunctions.MulOrAdd.Type.ADD : DensityFunctions.MulOrAdd.Type.MUL,
                        argument2,
                        h,
                        i,
                        constant.value
                    );
                }

                if (argument2 instanceof DensityFunctions.Constant constant) {
                    return new DensityFunctions.MulOrAdd(
                        type == DensityFunctions.TwoArgumentSimpleFunction.Type.ADD ? DensityFunctions.MulOrAdd.Type.ADD : DensityFunctions.MulOrAdd.Type.MUL,
                        argument1,
                        h,
                        i,
                        constant.value
                    );
                }
            }

            return new DensityFunctions.Ap2(type, argument1, argument2, h, i);
        }

        DensityFunctions.TwoArgumentSimpleFunction.Type type();

        DensityFunction argument1();

        DensityFunction argument2();

        @Override
        default KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.type().codec;
        }

        enum Type implements StringRepresentable {
            ADD("add"),
            MUL("mul"),
            MIN("min"),
            MAX("max");

            final KeyDispatchDataCodec<DensityFunctions.TwoArgumentSimpleFunction> codec = DensityFunctions.doubleFunctionArgumentCodec(
                (from, to) -> DensityFunctions.TwoArgumentSimpleFunction.create(this, from, to),
                DensityFunctions.TwoArgumentSimpleFunction::argument1,
                DensityFunctions.TwoArgumentSimpleFunction::argument2
            );
            private final String name;

            Type(final String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }
    }

    protected record WeirdScaledSampler(
        DensityFunction input, DensityFunction.NoiseHolder noise, DensityFunctions.WeirdScaledSampler.RarityValueMapper rarityValueMapper
    ) implements DensityFunctions.TransformerWithContext {
        private static final MapCodec<DensityFunctions.WeirdScaledSampler> DATA_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(DensityFunctions.WeirdScaledSampler::input),
                    DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(DensityFunctions.WeirdScaledSampler::noise),
                    DensityFunctions.WeirdScaledSampler.RarityValueMapper.CODEC
                        .fieldOf("rarity_value_mapper")
                        .forGetter(DensityFunctions.WeirdScaledSampler::rarityValueMapper)
                )
                .apply(instance, DensityFunctions.WeirdScaledSampler::new)
        );
        public static final KeyDispatchDataCodec<DensityFunctions.WeirdScaledSampler> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double transform(DensityFunction.FunctionContext context, double value) {
            double d = this.rarityValueMapper.mapper.get(value);
            return d * Math.abs(this.noise.getValue(context.blockX() / d, context.blockY() / d, context.blockZ() / d));
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new DensityFunctions.WeirdScaledSampler(this.input.mapAll(visitor), visitor.visitNoise(this.noise), this.rarityValueMapper));
        }

        @Override
        public double minValue() {
            return 0.0;
        }

        @Override
        public double maxValue() {
            return this.rarityValueMapper.maxRarity * this.noise.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        public enum RarityValueMapper implements StringRepresentable {
            TYPE1("type_1", NoiseRouterData.QuantizedSpaghettiRarity::getSpaghettiRarity3D, 2.0),
            TYPE2("type_2", NoiseRouterData.QuantizedSpaghettiRarity::getSphaghettiRarity2D, 3.0);

            public static final Codec<DensityFunctions.WeirdScaledSampler.RarityValueMapper> CODEC = StringRepresentable.fromEnum(
                DensityFunctions.WeirdScaledSampler.RarityValueMapper::values
            );
            private final String name;
            final Double2DoubleFunction mapper;
            final double maxRarity;

            RarityValueMapper(final String name, final Double2DoubleFunction mapper, final double maxRarity) {
                this.name = name;
                this.mapper = mapper;
                this.maxRarity = maxRarity;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }
    }

    record YClampedGradient(int fromY, int toY, double fromValue, double toValue) implements DensityFunction.SimpleFunction {
        private static final MapCodec<DensityFunctions.YClampedGradient> DATA_CODEC = RecordCodecBuilder.mapCodec(
            codec -> codec.group(
                    Codec.intRange(DimensionType.MIN_Y * 2, DimensionType.MAX_Y * 2).fieldOf("from_y").forGetter(DensityFunctions.YClampedGradient::fromY),
                    Codec.intRange(DimensionType.MIN_Y * 2, DimensionType.MAX_Y * 2).fieldOf("to_y").forGetter(DensityFunctions.YClampedGradient::toY),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("from_value").forGetter(DensityFunctions.YClampedGradient::fromValue),
                    DensityFunctions.NOISE_VALUE_CODEC.fieldOf("to_value").forGetter(DensityFunctions.YClampedGradient::toValue)
                )
                .apply(codec, DensityFunctions.YClampedGradient::new)
        );
        public static final KeyDispatchDataCodec<DensityFunctions.YClampedGradient> CODEC = DensityFunctions.makeCodec(DATA_CODEC);

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return Mth.clampedMap(context.blockY(), this.fromY, this.toY, this.fromValue, this.toValue);
        }

        @Override
        public double minValue() {
            return Math.min(this.fromValue, this.toValue);
        }

        @Override
        public double maxValue() {
            return Math.max(this.fromValue, this.toValue);
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }
}
