package net.minecraft.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

public class ByIdMap {
    private static <T> IntFunction<T> createMap(ToIntFunction<T> keyExtractor, T[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Empty value list");
        }

        Int2ObjectMap<T> int2ObjectMap = new Int2ObjectOpenHashMap<>();

        for (T object : values) {
            int i = keyExtractor.applyAsInt(object);
            T object2 = int2ObjectMap.put(i, object);
            if (object2 != null) {
                throw new IllegalArgumentException("Duplicate entry on id " + i + ": current=" + object + ", previous=" + object2);
            }
        }

        return int2ObjectMap;
    }

    public static <T> IntFunction<T> sparse(ToIntFunction<T> keyExtractor, T[] values, T fallback) {
        IntFunction<T> intFunction = createMap(keyExtractor, values);
        return key -> Objects.requireNonNullElse(intFunction.apply(key), fallback);
    }

    private static <T> T[] createSortedArray(ToIntFunction<T> keyExtractor, T[] values) {
        int i = values.length;
        if (i == 0) {
            throw new IllegalArgumentException("Empty value list");
        }

        T[] objects = (T[])values.clone();
        Arrays.fill(objects, null);

        for (T object : values) {
            int j = keyExtractor.applyAsInt(object);
            if (j < 0 || j >= i) {
                throw new IllegalArgumentException("Values are not continous, found index " + j + " for value " + object);
            }

            T object2 = objects[j];
            if (object2 != null) {
                throw new IllegalArgumentException("Duplicate entry on id " + j + ": current=" + object + ", previous=" + object2);
            }

            objects[j] = object;
        }

        for (int k = 0; k < i; k++) {
            if (objects[k] == null) {
                throw new IllegalArgumentException("Missing value at index: " + k);
            }
        }

        return objects;
    }

    public static <T> IntFunction<T> continuous(ToIntFunction<T> keyExtractor, T[] values, ByIdMap.OutOfBoundsStrategy outOfBoundsStrategy) {
        T[] objects = createSortedArray(keyExtractor, values);
        int i = objects.length;

        return switch (outOfBoundsStrategy) {
            case ZERO -> {
                T object = objects[0];
                yield key -> key >= 0 && key < i ? objects[key] : object;
            }
            case WRAP -> key -> objects[Mth.positiveModulo(key, i)];
            case CLAMP -> key -> objects[Mth.clamp(key, 0, i - 1)];
        };
    }

    public enum OutOfBoundsStrategy {
        ZERO,
        WRAP,
        CLAMP;
    }
}
