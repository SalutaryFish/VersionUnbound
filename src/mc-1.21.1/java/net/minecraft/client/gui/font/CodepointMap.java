package net.minecraft.client.gui.font;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.function.IntFunction;
import javax.annotation.Nullable;

public class CodepointMap<T> {
    private static final int BLOCK_BITS = 8;
    private static final int BLOCK_SIZE = 256;
    private static final int IN_BLOCK_MASK = 255;
    private static final int MAX_BLOCK = 4351;
    private static final int BLOCK_COUNT = 4352;
    private final T[] empty;
    private final T[][] blockMap;
    private final IntFunction<T[]> blockConstructor;

    public CodepointMap(IntFunction<T[]> blockConstructor, IntFunction<T[][]> blockMapConstructor) {
        this.empty = (T[])((Object[])blockConstructor.apply(256));
        this.blockMap = (T[][])((Object[][])blockMapConstructor.apply(4352));
        Arrays.fill(this.blockMap, this.empty);
        this.blockConstructor = blockConstructor;
    }

    public void clear() {
        Arrays.fill(this.blockMap, this.empty);
    }

    @Nullable
    public T get(int index) {
        int i = index >> 8;
        int j = index & 0xFF;
        return this.blockMap[i][j];
    }

    @Nullable
    public T put(int index, T value) {
        int i = index >> 8;
        int j = index & 0xFF;
        T[] objects = this.blockMap[i];
        if (objects == this.empty) {
            objects = (T[])((Object[])this.blockConstructor.apply(256));
            this.blockMap[i] = objects;
            objects[j] = value;
            return null;
        } else {
            T object = objects[j];
            objects[j] = value;
            return object;
        }
    }

    public T computeIfAbsent(int index, IntFunction<T> valueIfAbsentGetter) {
        int i = index >> 8;
        int j = index & 0xFF;
        T[] objects = this.blockMap[i];
        T object = objects[j];
        if (object != null) {
            return object;
        }

        if (objects == this.empty) {
            objects = (T[])((Object[])this.blockConstructor.apply(256));
            this.blockMap[i] = objects;
        }

        T object2 = valueIfAbsentGetter.apply(index);
        objects[j] = object2;
        return object2;
    }

    @Nullable
    public T remove(int i) {
        int j = i >> 8;
        int k = i & 0xFF;
        T[] objects = this.blockMap[j];
        if (objects == this.empty) {
            return null;
        }

        T object = objects[k];
        objects[k] = null;
        return object;
    }

    public void forEach(CodepointMap.Output<T> output) {
        for (int i = 0; i < this.blockMap.length; i++) {
            T[] objects = this.blockMap[i];
            if (objects != this.empty) {
                for (int j = 0; j < objects.length; j++) {
                    T object = objects[j];
                    if (object != null) {
                        int k = i << 8 | j;
                        output.accept(k, object);
                    }
                }
            }
        }
    }

    public IntSet keySet() {
        IntOpenHashSet intOpenHashSet = new IntOpenHashSet();
        this.forEach((index, object) -> intOpenHashSet.add(index));
        return intOpenHashSet;
    }

    @FunctionalInterface
    public interface Output<T> {
        void accept(int var1, T var2);
    }
}
