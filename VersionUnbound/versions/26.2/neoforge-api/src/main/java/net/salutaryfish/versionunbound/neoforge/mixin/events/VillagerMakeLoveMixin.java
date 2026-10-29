package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code VillagerMakeLove}: breeding fails when the level didn't add the child (its spawn was
 * cancelled through {@code FinalizeSpawnEvent}, or {@code EntityJoinLevelEvent} was).
 */
@Mixin(VillagerMakeLove.class)
abstract class VillagerMakeLoveMixin {
    @Inject(method = "breed", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V", shift = At.Shift.AFTER), cancellable = true)
    private void versionunbound$childNotAdded(CallbackInfoReturnable<Optional<Villager>> cir, @Local(ordinal = 2) Villager child) {
        // Neo: If villager is blocked from spawning (e.g., FinalizeSpawnEvent), then breed should be unsuccessful
        if (!child.isAddedToLevel()) cir.setReturnValue(Optional.empty());
    }
}
