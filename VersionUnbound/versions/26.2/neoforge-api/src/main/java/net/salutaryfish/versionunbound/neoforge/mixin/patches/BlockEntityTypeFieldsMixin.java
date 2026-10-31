package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.salutaryfish.versionunbound.neoforge.patches.BlockEntityTypeExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's {@code onlyOpCanSetNbt} field on {@link BlockEntityType}, set by the constructors that take it (see
 * {@link BlockEntityTypeExtension}), and its check at the head of {@code onlyOpCanSetNbt()}, before vanilla's list.
 */
@Mixin(BlockEntityType.class)
abstract class BlockEntityTypeFieldsMixin implements BlockEntityTypeExtension.Fields {
    /// Neo: Allow modded BE types to declare that they have NBT data only OPs can set
    @Unique
    private boolean versionunbound$onlyOpCanSetNbt;

    @Override
    public void versionunbound$setOnlyOpCanSetNbt(boolean onlyOpCanSetNbt) {
        this.versionunbound$onlyOpCanSetNbt = onlyOpCanSetNbt;
    }

    @Inject(method = "onlyOpCanSetNbt", at = @At("HEAD"), cancellable = true)
    private void versionunbound$onlyOpCanSetNbt(CallbackInfoReturnable<Boolean> cir) {
        if (this.versionunbound$onlyOpCanSetNbt) cir.setReturnValue(true);
    }
}
