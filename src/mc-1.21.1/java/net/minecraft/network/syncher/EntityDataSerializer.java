package net.minecraft.network.syncher;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Handles encoding and decoding of data for {@link SynchedEntityData}.
 * Note that mods cannot add new serializers, because this is not a managed registry and the serializer ID is limited to 16.
 */
public interface EntityDataSerializer<T> {
    StreamCodec<? super RegistryFriendlyByteBuf, T> codec();

    default EntityDataAccessor<T> createAccessor(int id) {
        return new EntityDataAccessor<>(id, this);
    }

    T copy(T var1);

    static <T> EntityDataSerializer<T> forValueType(StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
        return () -> streamCodec;
    }

    interface ForValueType<T> extends EntityDataSerializer<T> {
        @Override
        default T copy(T object) {
            return object;
        }
    }
}
