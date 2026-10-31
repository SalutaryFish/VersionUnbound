package net.minecraft.client.renderer;

public class RunningTrimmedMean {
    private final long[] values;
    private int count;
    private int cursor;

    public RunningTrimmedMean(int size) {
        this.values = new long[size];
    }

    public long registerValueAndGetMean(long value) {
        if (this.count < this.values.length) {
            this.count++;
        }

        this.values[this.cursor] = value;
        this.cursor = (this.cursor + 1) % this.values.length;
        long l = Long.MAX_VALUE;
        long m = Long.MIN_VALUE;
        long n = 0L;

        for (int i = 0; i < this.count; i++) {
            long o = this.values[i];
            n += o;
            l = Math.min(l, o);
            m = Math.max(m, o);
        }

        if (this.count > 2) {
            n -= l + m;
            return n / (this.count - 2);
        } else {
            return n > 0L ? this.count / n : 0L;
        }
    }
}
