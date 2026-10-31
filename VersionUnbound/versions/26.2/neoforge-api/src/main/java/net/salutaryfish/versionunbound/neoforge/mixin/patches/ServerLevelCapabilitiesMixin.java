package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.capabilities.CapabilityListenerHolder;
import net.salutaryfish.versionunbound.neoforge.patches.ServerLevelCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * NeoForge's capability listeners on {@link ServerLevel}: the holder behind {@link ServerLevelCapabilities}, and the
 * overrides of {@code ILevelExtension.invalidateCapabilities} (empty defaults, which the client level keeps).
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelCapabilitiesMixin implements ServerLevelCapabilities.Holder {
    @Unique
    private final CapabilityListenerHolder capListenerHolder = new CapabilityListenerHolder();

    @Override
    public CapabilityListenerHolder versionunbound$capListenerHolder() {
        return this.capListenerHolder;
    }

    public void invalidateCapabilities(BlockPos pos) {
        capListenerHolder.invalidatePos(pos);
    }

    public void invalidateCapabilities(ChunkPos pos) {
        capListenerHolder.invalidateChunk(pos);
    }
}
