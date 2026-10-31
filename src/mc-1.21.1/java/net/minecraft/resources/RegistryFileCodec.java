package net.minecraft.resources;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;

/**
 * A codec that wraps a single element, or "file", within a registry. Possibly allows inline definitions, and always falls back to the element codec (and thus writing the registry element inline) if it fails to decode from the registry.
 */
public final class RegistryFileCodec<E> implements Codec<Holder<E>> {
    private final ResourceKey<? extends Registry<E>> registryKey;
    private final Codec<E> elementCodec;
    private final boolean allowInline;

    /**
     * Creates a codec for a single registry element, which is held as an un-resolved {@code Supplier<E>}. Both inline definitions of the object, and references to an existing registry element id are allowed.
     *
     * @param registryKey  The registry which elements may belong to.
     * @param elementCodec The codec used to decode either inline definitions, or
     *                     elements before entering them into the registry.
     */
    public static <E> RegistryFileCodec<E> create(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec) {
        return create(registryKey, elementCodec, true);
    }

    public static <E> RegistryFileCodec<E> create(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec, boolean allowInline) {
        return new RegistryFileCodec<>(registryKey, elementCodec, allowInline);
    }

    private RegistryFileCodec(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec, boolean allowInline) {
        this.registryKey = registryKey;
        this.elementCodec = elementCodec;
        this.allowInline = allowInline;
    }

    public <T> DataResult<T> encode(Holder<E> input, DynamicOps<T> ops, T prefix) {
        if (ops instanceof RegistryOps<?> registryOps) {
            Optional<HolderOwner<E>> optional = registryOps.owner(this.registryKey);
            if (optional.isPresent()) {
                if (!input.canSerializeIn(optional.get())) {
                    return DataResult.error(() -> "Element " + input + " is not valid in current registry set");
                }

                return input.unwrap()
                    .map(
                        resourceKey -> ResourceLocation.CODEC.encode(resourceKey.location(), ops, prefix),
                        element -> this.elementCodec.encode((E)element, ops, prefix)
                    );
            }
        }

        return this.elementCodec.encode(input.value(), ops, prefix);
    }

    @Override
    public <T> DataResult<Pair<Holder<E>, T>> decode(DynamicOps<T> ops, T input) {
        if (ops instanceof RegistryOps<?> registryOps) {
            Optional<HolderGetter<E>> optional = registryOps.getter(this.registryKey);
            if (optional.isEmpty()) {
                return DataResult.error(() -> "Registry does not exist: " + this.registryKey);
            }

            HolderGetter<E> holderGetter = optional.get();
            DataResult<Pair<ResourceLocation, T>> dataResult = ResourceLocation.CODEC.decode(ops, input);
            if (dataResult.result().isEmpty()) {
                return !this.allowInline
                    ? DataResult.error(() -> "Inline definitions not allowed here")
                    : this.elementCodec.decode(ops, input).map(pairx -> pairx.mapFirst(Holder::direct));
            }

            Pair<ResourceLocation, T> pair = dataResult.result().get();
            ResourceKey<E> resourceKey = ResourceKey.create(this.registryKey, pair.getFirst());
            return holderGetter.get(resourceKey)
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Failed to get element " + resourceKey))
                .<Pair<Holder<E>, T>>map(holder -> Pair.of(holder, pair.getSecond()))
                .setLifecycle(Lifecycle.stable());
        } else {
            return this.elementCodec.decode(ops, input).map(pairx -> pairx.mapFirst(Holder::direct));
        }
    }

    @Override
    public String toString() {
        return "RegistryFileCodec[" + this.registryKey + " " + this.elementCodec + "]";
    }
}
