package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code Villager}'s lightning strike: {@code LivingConversionEvent.Pre} outside peaceful (canceling
 * it keeps the villager, and the strike hurts it as in peaceful), and {@code Post} in the conversion callback once the
 * witch's spawn is finalized, before it's made persistent.
 */
@Mixin(Villager.class)
abstract class VillagerMixin {
    @Definition(id = "getDifficulty", method = "Lnet/minecraft/server/level/ServerLevel;getDifficulty()Lnet/minecraft/world/Difficulty;")
    @Definition(id = "PEACEFUL", field = "Lnet/minecraft/world/Difficulty;PEACEFUL:Lnet/minecraft/world/Difficulty;")
    @Expression("?.getDifficulty() != PEACEFUL")
    @ModifyExpressionValue(method = "thunderHit", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canLivingConvert(boolean converts) {
        return converts && EventHooks.canLivingConvert((LivingEntity) (Object) this, EntityTypes.WITCH, timer -> {});
    }

    // The finalizeSpawn call before this point is the coremod's (EventHooks.finalizeMobSpawn), so the injector
    // anchors on the call after it.
    @Inject(method = "lambda$thunderHit$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Witch;setPersistenceRequired()V"))
    private void versionunbound$onLivingConvert(ServerLevel level, Witch witch, CallbackInfo ci) {
        EventHooks.onLivingConvert((LivingEntity) (Object) this, witch);
    }
}
