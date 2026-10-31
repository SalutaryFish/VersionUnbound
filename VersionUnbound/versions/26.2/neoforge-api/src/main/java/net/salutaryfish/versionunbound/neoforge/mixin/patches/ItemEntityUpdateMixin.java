package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code ItemEntity.tick}: the stack's {@code onEntityItemUpdate} runs first, and answering true
 * skips the rest of the tick. Not yet: the fluid types' item movement (with the fluid extensions).
 */
@Mixin(ItemEntity.class)
abstract class ItemEntityUpdateMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void versionunbound$entityItemUpdate(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getItem().onEntityItemUpdate(self)) ci.cancel();
    }
}
