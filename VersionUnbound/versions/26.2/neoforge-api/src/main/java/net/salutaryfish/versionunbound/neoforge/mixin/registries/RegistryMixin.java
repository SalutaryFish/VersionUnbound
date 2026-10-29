package net.salutaryfish.versionunbound.neoforge.mixin.registries;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * NeoForge's patch to {@link Registry}: a holder that wraps a registry reference, such as a {@code DeferredHolder},
 * encodes through the reference it wraps.
 */
@Mixin(Registry.class)
interface RegistryMixin<T> {
    @ModifyVariable(method = "safeCastToReference", at = @At("HEAD"), argsOnly = true)
    private Holder<T> versionunbound$unwrapDelegate(Holder<T> holder) {
        return holder.getDelegate();
    }
}
