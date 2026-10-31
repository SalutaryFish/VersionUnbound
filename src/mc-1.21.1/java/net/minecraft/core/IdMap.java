package net.minecraft.core;

import javax.annotation.Nullable;

public interface IdMap<T> extends Iterable<T> {
    int DEFAULT = -1;

    /**
     * @return the integer ID used to identify the given object
     */
    int getId(T var1);

    @Nullable
    T byId(int var1);

    default T byIdOrThrow(int id) {
        T object = this.byId(id);
        if (object == null) {
            throw new IllegalArgumentException("No value with id " + id);
        } else {
            return object;
        }
    }

    default int getIdOrThrow(T value) {
        int i = this.getId(value);
        if (i == -1) {
            throw new IllegalArgumentException("Can't find id for '" + value + "' in map " + this);
        } else {
            return i;
        }
    }

    int size();
}
