package net.salutaryfish.versionunbound.neoforge.mixin.registries;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * NeoForge's patch to {@link Holder.Reference}: data map lookup, a key getter that doesn't allocate, the lookup a
 * reference belongs to, and equality by key, so a reference equals the {@code DeferredHolder} for the same entry.
 */
@Mixin(Holder.Reference.class)
abstract class HolderReferenceMixin<T> {
    @Shadow
    @Final
    private HolderOwner<T> owner;
    @Shadow
    private @Nullable ResourceKey<T> key;

    @Shadow
    public abstract ResourceKey<T> key();

    public <A> @Nullable A getData(DataMapType<T, A> type) {
        if (owner instanceof HolderLookup.RegistryLookup<T> lookup) {
            return lookup.getData(type, key());
        }
        return null;
    }

    // Neo: Add key getter that doesn't allocate
    public @Nullable ResourceKey<T> getKey() {
        return this.key;
    }

    // Neo: Add DeferredHolder-compatible hashCode() overrides
    @Override
    public int hashCode() {
        return key().hashCode();
    }

    // Neo: Add DeferredHolder-compatible equals() overrides
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        return obj instanceof Holder<?> h && h.kind() == Holder.Kind.REFERENCE && h.getKey() == this.key();
    }

    // Neo: Helper method to get the registry lookup from a holder
    public HolderLookup.@Nullable RegistryLookup<T> unwrapLookup() {
        return this.owner instanceof HolderLookup.RegistryLookup<T> rl ? rl : null;
    }
}
