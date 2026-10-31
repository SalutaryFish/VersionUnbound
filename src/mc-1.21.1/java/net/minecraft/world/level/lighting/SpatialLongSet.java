package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.NoSuchElementException;
import net.minecraft.util.Mth;

public class SpatialLongSet extends LongLinkedOpenHashSet {
    private final SpatialLongSet.InternalMap map;

    public SpatialLongSet(int expectedSize, float loadFactor) {
        super(expectedSize, loadFactor);
        this.map = new SpatialLongSet.InternalMap(expectedSize / 64, loadFactor);
    }

    @Override
    public boolean add(long value) {
        return this.map.addBit(value);
    }

    @Override
    public boolean rem(long value) {
        return this.map.removeBit(value);
    }

    @Override
    public long removeFirstLong() {
        return this.map.removeFirstBit();
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    protected static class InternalMap extends Long2LongLinkedOpenHashMap {
        private static final int X_BITS = Mth.log2(60000000);
        private static final int Z_BITS = Mth.log2(60000000);
        private static final int Y_BITS = 64 - X_BITS - Z_BITS;
        private static final int Y_OFFSET = 0;
        private static final int Z_OFFSET = Y_BITS;
        private static final int X_OFFSET = Y_BITS + Z_BITS;
        private static final long OUTER_MASK = 3L << X_OFFSET | 3L | 3L << Z_OFFSET;
        private int lastPos = -1;
        private long lastOuterKey;
        private final int minSize;

        public InternalMap(int minSize, float loadFactor) {
            super(minSize, loadFactor);
            this.minSize = minSize;
        }

        static long getOuterKey(long value) {
            return value & ~OUTER_MASK;
        }

        static int getInnerKey(long value) {
            int i = (int)(value >>> X_OFFSET & 3L);
            int j = (int)(value >>> 0 & 3L);
            int k = (int)(value >>> Z_OFFSET & 3L);
            return i << 4 | k << 2 | j;
        }

        static long getFullKey(long value, int trailingZeros) {
            value |= (long)(trailingZeros >>> 4 & 3) << X_OFFSET;
            value |= (long)(trailingZeros >>> 2 & 3) << Z_OFFSET;
            return value | (long)(trailingZeros >>> 0 & 3) << 0;
        }

        public boolean addBit(long value) {
            long l = getOuterKey(value);
            int i = getInnerKey(value);
            long m = 1L << i;
            int j;
            if (l == 0L) {
                if (this.containsNullKey) {
                    return this.replaceBit(this.n, m);
                }

                this.containsNullKey = true;
                j = this.n;
            } else {
                if (this.lastPos != -1 && l == this.lastOuterKey) {
                    return this.replaceBit(this.lastPos, m);
                }

                long[] ls = this.key;
                j = (int)HashCommon.mix(l) & this.mask;

                for (long n = ls[j]; n != 0L; n = ls[j]) {
                    if (n == l) {
                        this.lastPos = j;
                        this.lastOuterKey = l;
                        return this.replaceBit(j, m);
                    }

                    j = j + 1 & this.mask;
                }
            }

            this.key[j] = l;
            this.value[j] = m;
            if (this.size == 0) {
                this.first = this.last = j;
                this.link[j] = -1L;
            } else {
                this.link[this.last] = this.link[this.last] ^ (this.link[this.last] ^ j & 4294967295L) & 4294967295L;
                this.link[j] = (this.last & 4294967295L) << 32 | 4294967295L;
                this.last = j;
            }

            if (this.size++ >= this.maxFill) {
                this.rehash(HashCommon.arraySize(this.size + 1, this.f));
            }

            return false;
        }

        private boolean replaceBit(int index, long value) {
            boolean bl = (this.value[index] & value) != 0L;
            this.value[index] = this.value[index] | value;
            return bl;
        }

        public boolean removeBit(long value) {
            long l = getOuterKey(value);
            int i = getInnerKey(value);
            long m = 1L << i;
            if (l == 0L) {
                return this.containsNullKey ? this.removeFromNullEntry(m) : false;
            }

            if (this.lastPos != -1 && l == this.lastOuterKey) {
                return this.removeFromEntry(this.lastPos, m);
            }

            long[] ls = this.key;
            int j = (int)HashCommon.mix(l) & this.mask;

            for (long n = ls[j]; n != 0L; n = ls[j]) {
                if (l == n) {
                    this.lastPos = j;
                    this.lastOuterKey = l;
                    return this.removeFromEntry(j, m);
                }

                j = j + 1 & this.mask;
            }

            return false;
        }

        private boolean removeFromNullEntry(long value) {
            if ((this.value[this.n] & value) == 0L) {
                return false;
            }

            this.value[this.n] = this.value[this.n] & ~value;
            if (this.value[this.n] != 0L) {
                return true;
            }

            this.containsNullKey = false;
            this.size--;
            this.fixPointers(this.n);
            if (this.size < this.maxFill / 4 && this.n > 16) {
                this.rehash(this.n / 2);
            }

            return true;
        }

        private boolean removeFromEntry(int index, long value) {
            if ((this.value[index] & value) == 0L) {
                return false;
            }

            this.value[index] = this.value[index] & ~value;
            if (this.value[index] != 0L) {
                return true;
            }

            this.lastPos = -1;
            this.size--;
            this.fixPointers(index);
            this.shiftKeys(index);
            if (this.size < this.maxFill / 4 && this.n > 16) {
                this.rehash(this.n / 2);
            }

            return true;
        }

        public long removeFirstBit() {
            if (this.size == 0) {
                throw new NoSuchElementException();
            }

            int i = this.first;
            long l = this.key[i];
            int j = Long.numberOfTrailingZeros(this.value[i]);
            this.value[i] = this.value[i] & ~(1L << j);
            if (this.value[i] == 0L) {
                this.removeFirstLong();
                this.lastPos = -1;
            }

            return getFullKey(l, j);
        }

        @Override
        protected void rehash(int newSize) {
            if (newSize > this.minSize) {
                super.rehash(newSize);
            }
        }
    }
}
