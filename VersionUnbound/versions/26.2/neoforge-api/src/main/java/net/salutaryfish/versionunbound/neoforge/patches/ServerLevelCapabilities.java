package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.CapabilityListenerHolder;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;
import org.jetbrains.annotations.ApiStatus;

/**
 * The capability listener methods NeoForge's patch adds to {@link ServerLevel}, injected into it by the class tweaker;
 * {@code ServerLevelCapabilitiesMixin} holds the listeners and overrides {@code ILevelExtension.invalidateCapabilities}.
 * What calls {@link #cleanCapabilityListenerReferences} (NeoForge's {@code CapabilityHooks}, every level tick) comes with
 * capabilities (Phase 2 step 9).
 */
public interface ServerLevelCapabilities {
    private CapabilityListenerHolder holder() {
        return ((Holder) this).versionunbound$capListenerHolder();
    }

    /**
     * Register a listener for capability invalidation.
     * @see net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener
     */
    default void registerCapabilityListener(BlockPos pos, ICapabilityInvalidationListener listener) {
        holder().addListener(pos, listener);
    }

    /**
     * Internal method, used to clean capability listeners that are not referenced.
     * Do not call.
     */
    @ApiStatus.Internal
    default void cleanCapabilityListenerReferences() {
        holder().clean();
    }

    /** The level's listeners, implemented by {@code ServerLevelCapabilitiesMixin}. */
    interface Holder {
        CapabilityListenerHolder versionunbound$capListenerHolder();
    }
}
