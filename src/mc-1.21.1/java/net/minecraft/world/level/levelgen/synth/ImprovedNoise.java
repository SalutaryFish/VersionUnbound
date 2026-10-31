package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Generates a single octave of Perlin noise.
 */
public final class ImprovedNoise {
    private static final float SHIFT_UP_EPSILON = 1.0E-7F;
    /**
     * A permutation array used in noise calculation.
     * This is populated with the values [0, 256) and shuffled per instance of {@code ImprovedNoise}.
     *
     * @see #p(int)
     */
    private final byte[] p;
    public final double xo;
    public final double yo;
    public final double zo;

    public ImprovedNoise(RandomSource random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        this.p = new byte[256];

        for (int i = 0; i < 256; i++) {
            this.p[i] = (byte)i;
        }

        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(256 - i);
            byte b = this.p[i];
            this.p[i] = this.p[i + j];
            this.p[i + j] = b;
        }
    }

    public double noise(double x, double y, double z) {
        return this.noise(x, y, z, 0.0, 0.0);
    }

    @Deprecated
    public double noise(double x, double y, double z, double yScale, double yMax) {
        double d = x + this.xo;
        double e = y + this.yo;
        double f = z + this.zo;
        int i = Mth.floor(d);
        int j = Mth.floor(e);
        int k = Mth.floor(f);
        double g = d - i;
        double h = e - j;
        double l = f - k;
        double n;
        if (yScale != 0.0) {
            double m;
            if (yMax >= 0.0 && yMax < h) {
                m = yMax;
            } else {
                m = h;
            }

            n = Mth.floor(m / yScale + 1.0E-7F) * yScale;
        } else {
            n = 0.0;
        }

        return this.sampleAndLerp(i, j, k, g, h - n, l, h);
    }

    public double noiseWithDerivative(double x, double y, double z, double[] values) {
        double d = x + this.xo;
        double e = y + this.yo;
        double f = z + this.zo;
        int i = Mth.floor(d);
        int j = Mth.floor(e);
        int k = Mth.floor(f);
        double g = d - i;
        double h = e - j;
        double l = f - k;
        return this.sampleWithDerivative(i, j, k, g, h, l, values);
    }

    private static double gradDot(int gradIndex, double xFactor, double yFactor, double zFactor) {
        return SimplexNoise.dot(SimplexNoise.GRADIENT[gradIndex & 15], xFactor, yFactor, zFactor);
    }

    private int p(int index) {
        return this.p[index & 0xFF] & 0xFF;
    }

    private double sampleAndLerp(int gridX, int gridY, int gridZ, double deltaX, double weirdDeltaY, double deltaZ, double deltaY) {
        int i = this.p(gridX);
        int j = this.p(gridX + 1);
        int k = this.p(i + gridY);
        int l = this.p(i + gridY + 1);
        int m = this.p(j + gridY);
        int n = this.p(j + gridY + 1);
        double d = gradDot(this.p(k + gridZ), deltaX, weirdDeltaY, deltaZ);
        double e = gradDot(this.p(m + gridZ), deltaX - 1.0, weirdDeltaY, deltaZ);
        double f = gradDot(this.p(l + gridZ), deltaX, weirdDeltaY - 1.0, deltaZ);
        double g = gradDot(this.p(n + gridZ), deltaX - 1.0, weirdDeltaY - 1.0, deltaZ);
        double h = gradDot(this.p(k + gridZ + 1), deltaX, weirdDeltaY, deltaZ - 1.0);
        double o = gradDot(this.p(m + gridZ + 1), deltaX - 1.0, weirdDeltaY, deltaZ - 1.0);
        double p = gradDot(this.p(l + gridZ + 1), deltaX, weirdDeltaY - 1.0, deltaZ - 1.0);
        double q = gradDot(this.p(n + gridZ + 1), deltaX - 1.0, weirdDeltaY - 1.0, deltaZ - 1.0);
        double r = Mth.smoothstep(deltaX);
        double s = Mth.smoothstep(deltaY);
        double t = Mth.smoothstep(deltaZ);
        return Mth.lerp3(r, s, t, d, e, f, g, h, o, p, q);
    }

    private double sampleWithDerivative(int gridX, int gridY, int gridZ, double deltaX, double deltaY, double deltaZ, double[] noiseValues) {
        int i = this.p(gridX);
        int j = this.p(gridX + 1);
        int k = this.p(i + gridY);
        int l = this.p(i + gridY + 1);
        int m = this.p(j + gridY);
        int n = this.p(j + gridY + 1);
        int o = this.p(k + gridZ);
        int p = this.p(m + gridZ);
        int q = this.p(l + gridZ);
        int r = this.p(n + gridZ);
        int s = this.p(k + gridZ + 1);
        int t = this.p(m + gridZ + 1);
        int u = this.p(l + gridZ + 1);
        int v = this.p(n + gridZ + 1);
        int[] is = SimplexNoise.GRADIENT[o & 15];
        int[] js = SimplexNoise.GRADIENT[p & 15];
        int[] ks = SimplexNoise.GRADIENT[q & 15];
        int[] ls = SimplexNoise.GRADIENT[r & 15];
        int[] ms = SimplexNoise.GRADIENT[s & 15];
        int[] ns = SimplexNoise.GRADIENT[t & 15];
        int[] os = SimplexNoise.GRADIENT[u & 15];
        int[] ps = SimplexNoise.GRADIENT[v & 15];
        double d = SimplexNoise.dot(is, deltaX, deltaY, deltaZ);
        double e = SimplexNoise.dot(js, deltaX - 1.0, deltaY, deltaZ);
        double f = SimplexNoise.dot(ks, deltaX, deltaY - 1.0, deltaZ);
        double g = SimplexNoise.dot(ls, deltaX - 1.0, deltaY - 1.0, deltaZ);
        double h = SimplexNoise.dot(ms, deltaX, deltaY, deltaZ - 1.0);
        double w = SimplexNoise.dot(ns, deltaX - 1.0, deltaY, deltaZ - 1.0);
        double x = SimplexNoise.dot(os, deltaX, deltaY - 1.0, deltaZ - 1.0);
        double y = SimplexNoise.dot(ps, deltaX - 1.0, deltaY - 1.0, deltaZ - 1.0);
        double z = Mth.smoothstep(deltaX);
        double aa = Mth.smoothstep(deltaY);
        double ab = Mth.smoothstep(deltaZ);
        double ac = Mth.lerp3(z, aa, ab, is[0], js[0], ks[0], ls[0], ms[0], ns[0], os[0], ps[0]);
        double ad = Mth.lerp3(z, aa, ab, is[1], js[1], ks[1], ls[1], ms[1], ns[1], os[1], ps[1]);
        double ae = Mth.lerp3(z, aa, ab, is[2], js[2], ks[2], ls[2], ms[2], ns[2], os[2], ps[2]);
        double af = Mth.lerp2(aa, ab, e - d, g - f, w - h, y - x);
        double ag = Mth.lerp2(ab, z, f - d, x - h, g - e, y - w);
        double ah = Mth.lerp2(z, aa, h - d, w - e, x - f, y - g);
        double ai = Mth.smoothstepDerivative(deltaX);
        double aj = Mth.smoothstepDerivative(deltaY);
        double ak = Mth.smoothstepDerivative(deltaZ);
        double al = ac + ai * af;
        double am = ad + aj * ag;
        double an = ae + ak * ah;
        noiseValues[0] += al;
        noiseValues[1] += am;
        noiseValues[2] += an;
        return Mth.lerp3(z, aa, ab, d, e, f, g, h, w, x, y);
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder builder) {
        NoiseUtils.parityNoiseOctaveConfigString(builder, this.xo, this.yo, this.zo, this.p);
    }
}
