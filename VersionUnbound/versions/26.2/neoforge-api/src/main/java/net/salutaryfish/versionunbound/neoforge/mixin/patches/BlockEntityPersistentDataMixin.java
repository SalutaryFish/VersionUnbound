package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@link BlockEntity}, for {@code IBlockEntityExtension}: the persistent data (saved as
 * {@code NeoForgeData}, in vanilla's empty {@code loadAdditional} and {@code saveAdditional}, which subclasses call
 * through {@code super}), and capability invalidation and model data updates when the block entity is removed or
 * placed. Not yet: attachments ({@code BlockEntity} extends NeoForge's {@code AttachmentHolder} there; Phase 2 step 9).
 */
@Mixin(BlockEntity.class)
abstract class BlockEntityPersistentDataMixin {
    @Unique
    private @Nullable CompoundTag customPersistentData;

    @Inject(method = "loadAdditional", at = @At("HEAD"))
    private void versionunbound$loadPersistentData(ValueInput input, CallbackInfo ci) {
        input.read("NeoForgeData", CompoundTag.CODEC).ifPresent(neoData -> this.customPersistentData = neoData);
    }

    @Inject(method = "saveAdditional", at = @At("HEAD"))
    private void versionunbound$savePersistentData(ValueOutput output, CallbackInfo ci) {
        if (this.customPersistentData != null) output.store("NeoForgeData", CompoundTag.CODEC, this.customPersistentData.copy());
    }

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void versionunbound$removed(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        self.invalidateCapabilities();
        self.requestModelDataUpdate();
    }

    @Inject(method = "clearRemoved", at = @At("TAIL"))
    private void versionunbound$placed(CallbackInfo ci) {
        // Neo: invalidate capabilities on block entity placement
        ((BlockEntity) (Object) this).invalidateCapabilities();
    }

    public CompoundTag getPersistentData() {
        if (this.customPersistentData == null)
            this.customPersistentData = new CompoundTag();
        return this.customPersistentData;
    }
}
