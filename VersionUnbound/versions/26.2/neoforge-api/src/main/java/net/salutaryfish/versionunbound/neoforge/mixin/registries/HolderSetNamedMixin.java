package net.salutaryfish.versionunbound.neoforge.mixin.registries;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@link HolderSet.Named}, for {@link net.neoforged.neoforge.common.extensions.IHolderSetExtension}:
 * listeners that run when a tag is bound again, which composite holder sets use to drop their cached contents.
 */
@Mixin(HolderSet.Named.class)
abstract class HolderSetNamedMixin<T> {
    // FORGE: Keep a list of invalidation callbacks so they can be run when tags rebind
    @Unique
    private final List<Runnable> versionunbound$invalidationCallbacks = new ArrayList<>();

    @Inject(method = "bind", at = @At("TAIL"))
    private void versionunbound$invalidate(List<Holder<T>> contents, CallbackInfo ci) {
        for (Runnable runnable : this.versionunbound$invalidationCallbacks) {
            runnable.run(); // FORGE: invalidate listeners when tags rebind
        }
    }

    public void addInvalidationListener(Runnable runnable) {
        versionunbound$invalidationCallbacks.add(runnable);
    }

    public boolean isImmediatelyResolvable() {
        return false;
    }
}
