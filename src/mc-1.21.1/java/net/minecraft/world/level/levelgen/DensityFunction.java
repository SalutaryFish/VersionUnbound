package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public interface DensityFunction {
    Codec<DensityFunction> DIRECT_CODEC = DensityFunctions.DIRECT_CODEC;
    Codec<Holder<DensityFunction>> CODEC = RegistryFileCodec.create(Registries.DENSITY_FUNCTION, DIRECT_CODEC);
    Codec<DensityFunction> HOLDER_HELPER_CODEC = CODEC.xmap(
        DensityFunctions.HolderHolder::new,
        holder -> holder instanceof DensityFunctions.HolderHolder holderHolder ? holderHolder.function() : new Holder.Direct<>(holder)
    );

    double compute(DensityFunction.FunctionContext var1);

    void fillArray(double[] var1, DensityFunction.ContextProvider var2);

    DensityFunction mapAll(DensityFunction.Visitor var1);

    double minValue();

    double maxValue();

    KeyDispatchDataCodec<? extends DensityFunction> codec();

    default DensityFunction clamp(double minValue, double maxValue) {
        return new DensityFunctions.Clamp(this, minValue, maxValue);
    }

    default DensityFunction abs() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.ABS);
    }

    default DensityFunction square() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUARE);
    }

    default DensityFunction cube() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.CUBE);
    }

    default DensityFunction halfNegative() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.HALF_NEGATIVE);
    }

    default DensityFunction quarterNegative() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.QUARTER_NEGATIVE);
    }

    default DensityFunction squeeze() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUEEZE);
    }

    interface ContextProvider {
        DensityFunction.FunctionContext forIndex(int var1);

        void fillAllDirectly(double[] var1, DensityFunction var2);
    }

    interface FunctionContext {
        int blockX();

        int blockY();

        int blockZ();

        default Blender getBlender() {
            return Blender.empty();
        }
    }

    record NoiseHolder(Holder<NormalNoise.NoiseParameters> noiseData, @Nullable NormalNoise noise) {
        public static final Codec<DensityFunction.NoiseHolder> CODEC = NormalNoise.NoiseParameters.CODEC
            .xmap(holder -> new DensityFunction.NoiseHolder((Holder<NormalNoise.NoiseParameters>)holder, null), DensityFunction.NoiseHolder::noiseData);

        public NoiseHolder(Holder<NormalNoise.NoiseParameters> noiseData) {
            this(noiseData, null);
        }

        public double getValue(double x, double y, double z) {
            return this.noise == null ? 0.0 : this.noise.getValue(x, y, z);
        }

        public double maxValue() {
            return this.noise == null ? 2.0 : this.noise.maxValue();
        }
    }

    interface SimpleFunction extends DensityFunction {
        @Override
        default void fillArray(double[] ds, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(ds, this);
        }

        @Override
        default DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(this);
        }
    }

    record SinglePointContext(int blockX, int blockY, int blockZ) implements DensityFunction.FunctionContext {
    }

    interface Visitor {
        DensityFunction apply(DensityFunction var1);

        default DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noiseHolder) {
            return noiseHolder;
        }
    }
}
