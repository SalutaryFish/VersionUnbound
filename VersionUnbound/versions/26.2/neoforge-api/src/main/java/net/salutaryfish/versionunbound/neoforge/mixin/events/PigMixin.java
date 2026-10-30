package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code Pig}: {@code LivingConversionEvent.Pre} when lightning strikes a pig outside peaceful
 * (canceling it keeps the pig, and the strike hurts it as in peaceful), and {@code Post} at the end of the conversion
 * callback.
 */
@Mixin(Pig.class)
abstract class PigMixin {
    @Definition(id = "getDifficulty", method = "Lnet/minecraft/server/level/ServerLevel;getDifficulty()Lnet/minecraft/world/Difficulty;")
    @Definition(id = "PEACEFUL", field = "Lnet/minecraft/world/Difficulty;PEACEFUL:Lnet/minecraft/world/Difficulty;")
    @Expression("?.getDifficulty() != PEACEFUL")
    @ModifyExpressionValue(method = "thunderHit", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canLivingConvert(boolean converts) {
        return converts && EventHooks.canLivingConvert((LivingEntity) (Object) this, EntityTypes.ZOMBIFIED_PIGLIN, timer -> {});
    }

    @Inject(method = "lambda$thunderHit$0", at = @At("TAIL"))
    private void versionunbound$onLivingConvert(ServerLevel level, ZombifiedPiglin zombifiedPiglin, CallbackInfo ci) {
        EventHooks.onLivingConvert((LivingEntity) (Object) this, zombifiedPiglin);
    }
}
