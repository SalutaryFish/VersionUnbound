package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.salutaryfish.versionunbound.neoforge.patches.LevelFreshBlockEntities;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code Level}, for the block entity extension's {@code onLoad}: block entities the level's chunks
 * add (LevelChunkBlockEntitiesMixin) wait in NeoForge's fresh list (or its pending list while block entities tick), and
 * {@code tickBlockEntities} tells each that is still in a level {@code onLoad}, once it has started ticking (so any added
 * meanwhile wait for the next tick).
 */
@Mixin(Level.class)
abstract class LevelFreshBlockEntitiesMixin implements LevelFreshBlockEntities {
    @Shadow
    private boolean tickingBlockEntities;

    @Unique
    private final ArrayList<BlockEntity> freshBlockEntities = new ArrayList<>();
    @Unique
    private final ArrayList<BlockEntity> pendingFreshBlockEntities = new ArrayList<>();

    @Override
    public void addFreshBlockEntities(Collection<BlockEntity> beList) {
        if (this.tickingBlockEntities) {
            this.pendingFreshBlockEntities.addAll(beList);
        } else {
            this.freshBlockEntities.addAll(beList);
        }
    }

    @Inject(method = "tickBlockEntities", at = @At("HEAD"))
    private void versionunbound$freshFromPending(CallbackInfo ci) {
        if (!this.pendingFreshBlockEntities.isEmpty()) {
            this.freshBlockEntities.addAll(this.pendingFreshBlockEntities);
            this.pendingFreshBlockEntities.clear();
        }
    }

    @Inject(method = "tickBlockEntities", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;tickingBlockEntities:Z", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER))
    private void versionunbound$onLoad(CallbackInfo ci) {
        if (!this.freshBlockEntities.isEmpty()) {
            this.freshBlockEntities.forEach(blockEntity -> {
                // Only call onLoad() on BEs which have been fully added to the level, prevents crashes with BEs that
                // were discarded due to incompatibility with the BlockState at their position
                if (!blockEntity.isRemoved() && blockEntity.hasLevel()) {
                    blockEntity.onLoad();
                }
            });
            this.freshBlockEntities.clear();
        }
    }
}
