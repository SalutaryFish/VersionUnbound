package net.minecraft.world.level.levelgen.synth;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A generator for a single octave of Simplex noise.
 */
public class SimplexNoise {
    protected static final int[][] GRADIENT = new int[][]{
        {1, 1, 0},
        {-1, 1, 0},
        {1, -1, 0},
        {-1, -1, 0},
        {1, 0, 1},
        {-1, 0, 1},
        {1, 0, -1},
        {-1, 0, -1},
        {0, 1, 1},
        {0, -1, 1},
        {0, 1, -1},
        {0, -1, -1},
        {1, 1, 0},
        {0, -1, 1},
        {-1, 1, 0},
        {0, -1, -1}
    };
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double F2 = 0.5 * (SQRT_3 - 1.0);
    private static final double G2 = (3.0 - SQRT_3) / 6.0;
    /**
     * A permutation array used in noise generation.
     * This is populated with the values [0, 256) and shuffled. Despite the array declared as 512 length, only the first 256 values are used.
     *
     * @see #p(int)
     */
    private final int[] p = new int[512];
    public final double xo;
    public final double yo;
    public final double zo;

    public SimplexNoise(RandomSource random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        int i = 0;

        while (i < 256) {
            this.p[i] = i++;
        }

        for (int ix = 0; ix < 256; ix++) {
            int j = random.nextInt(256 - ix);
            int k = this.p[ix];
            this.p[ix] = this.p[j + ix];
            this.p[j + ix] = k;
        }
    }

    private int p(int index) {
        return this.p[index & 0xFF];
    }

    /**
     * @return The dot product of the provided three-dimensional gradient vector and the vector (x, y, z)
     */
    protected static double dot(int[] gradient, double x, double y, double z) {
        return gradient[0] * x + gradient[1] * y + gradient[2] * z;
    }

    private double getCornerNoise3D(int gradientIndex, double x, double y, double z, double offset) {
        double d = offset - x * x - y * y - z * z;
        double e;
        if (d < 0.0) {
            e = 0.0;
        } else {
            d *= d;
            e = d * d * dot(GRADIENT[gradientIndex], x, y, z);
        }

        return e;
    }

    public double getValue(double x, double y) {
        double d = (x + y) * F2;
        int i = Mth.floor(x + d);
        int j = Mth.floor(y + d);
        double e = (i + j) * G2;
        double f = i - e;
        double g = j - e;
        double h = x - f;
        double k = y - g;
        int l;
        int m;
        if (h > k) {
            l = 1;
            m = 0;
        } else {
            l = 0;
            m = 1;
        }

        double n = h - l + G2;
        double o = k - m + G2;
        double p = h - 1.0 + 2.0 * G2;
        double q = k - 1.0 + 2.0 * G2;
        int r = i & 0xFF;
        int s = j & 0xFF;
        int t = this.p(r + this.p(s)) % 12;
        int u = this.p(r + l + this.p(s + m)) % 12;
        int v = this.p(r + 1 + this.p(s + 1)) % 12;
        double w = this.getCornerNoise3D(t, h, k, 0.0, 0.5);
        double z = this.getCornerNoise3D(u, n, o, 0.0, 0.5);
        double aa = this.getCornerNoise3D(v, p, q, 0.0, 0.5);
        return 70.0 * (w + z + aa);
    }

    public double getValue(double x, double y, double z) {
        double d = 0.3333333333333333;
        double e = (x + y + z) * 0.3333333333333333;
        int i = Mth.floor(x + e);
        int j = Mth.floor(y + e);
        int k = Mth.floor(z + e);
        double f = 0.16666666666666666;
        double g = (i + j + k) * 0.16666666666666666;
        double h = i - g;
        double l = j - g;
        double m = k - g;
        double n = x - h;
        double o = y - l;
        double p = z - m;
        int q;
        int r;
        int s;
        int t;
        int u;
        int v;
        if (n >= o) {
            if (o >= p) {
                q = 1;
                r = 0;
                s = 0;
                t = 1;
                u = 1;
                v = 0;
            } else if (n >= p) {
                q = 1;
                r = 0;
                s = 0;
                t = 1;
                u = 0;
                v = 1;
            } else {
                q = 0;
                r = 0;
                s = 1;
                t = 1;
                u = 0;
                v = 1;
            }
        } else if (o < p) {
            q = 0;
            r = 0;
            s = 1;
            t = 0;
            u = 1;
            v = 1;
        } else if (n < p) {
            q = 0;
            r = 1;
            s = 0;
            t = 0;
            u = 1;
            v = 1;
        } else {
            q = 0;
            r = 1;
            s = 0;
            t = 1;
            u = 1;
            v = 0;
        }

        double w = n - q + 0.16666666666666666;
        double aa = o - r + 0.16666666666666666;
        double ab = p - s + 0.16666666666666666;
        double ac = n - t + 0.3333333333333333;
        double ad = o - u + 0.3333333333333333;
        double ae = p - v + 0.3333333333333333;
        double af = n - 1.0 + 0.5;
        double ag = o - 1.0 + 0.5;
        double ah = p - 1.0 + 0.5;
        int ai = i & 0xFF;
        int aj = j & 0xFF;
        int ak = k & 0xFF;
        int al = this.p(ai + this.p(aj + this.p(ak))) % 12;
        int am = this.p(ai + q + this.p(aj + r + this.p(ak + s))) % 12;
        int an = this.p(ai + t + this.p(aj + u + this.p(ak + v))) % 12;
        int ao = this.p(ai + 1 + this.p(aj + 1 + this.p(ak + 1))) % 12;
        double ap = this.getCornerNoise3D(al, n, o, p, 0.6);
        double aq = this.getCornerNoise3D(am, w, aa, ab, 0.6);
        double ar = this.getCornerNoise3D(an, ac, ad, ae, 0.6);
        double as = this.getCornerNoise3D(ao, af, ag, ah, 0.6);
        return 32.0 * (ap + aq + ar + as);
    }
}
