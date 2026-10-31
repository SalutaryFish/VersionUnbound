package net.minecraft.util;

import java.util.function.IntConsumer;

public interface BitStorage {
    int getAndSet(int var1, int var2);

    /**
     * Sets the entry at the given location to the given value
     */
    void set(int var1, int var2);

    /**
     * Gets the entry at the given index
     */
    int get(int var1);

    long[] getRaw();

    int getSize();

    int getBits();

    void getAll(IntConsumer var1);

    void unpack(int[] var1);

    BitStorage copy();
}
