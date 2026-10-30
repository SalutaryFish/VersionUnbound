package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementProgressEvent.ProgressType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code PlayerAdvancements}: {@code AdvancementProgressEvent} when a criterion is granted or revoked
 * (once the advancement is marked as changed), and {@code AdvancementEarnEvent} when a grant completes an advancement,
 * which NeoForge fires inside the lambda that announces it, after the announcement: only advancements with a display get
 * it (NeoForge 1.21.1 too, though the event's javadoc names recipe advancements, which have none).
 */
@Mixin(PlayerAdvancements.class)
abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z", shift = At.Shift.AFTER))
    private void versionunbound$progressGranted(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir, @Local AdvancementProgress progress) {
        EventHooks.onAdvancementProgressedEvent(this.player, holder, progress, criterion, ProgressType.GRANT);
    }

    @Inject(method = "lambda$award$0", at = @At("TAIL"))
    private void versionunbound$earned(AdvancementHolder holder, DisplayInfo display, CallbackInfo ci) {
        EventHooks.onAdvancementEarnedEvent(this.player, holder);
    }

    @Inject(method = "revoke", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z", shift = At.Shift.AFTER))
    private void versionunbound$progressRevoked(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir, @Local AdvancementProgress progress) {
        EventHooks.onAdvancementProgressedEvent(this.player, advancement, progress, criterion, ProgressType.REVOKE);
    }
}
