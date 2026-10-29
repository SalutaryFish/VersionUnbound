package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code RunAroundLikeCrazyGoal} (horses, donkeys, mules, llamas): {@code AnimalTameEvent} once the
 * temper roll for the riding player succeeds; canceling it fails the taming, and the horse throws the player off.
 */
@Mixin(RunAroundLikeCrazyGoal.class)
abstract class RunAroundLikeCrazyGoalMixin {
    @Shadow
    @Final
    private AbstractHorse horse;

    @Definition(id = "nextInt", method = "Lnet/minecraft/util/RandomSource;nextInt(I)I")
    @Definition(id = "temper", local = @Local(type = int.class, name = "temper"))
    @Expression("?.nextInt(?) < temper")
    @ModifyExpressionValue(method = "tick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$animalTame(boolean tamed, @Local Player player) {
        return tamed && !EventHooks.onAnimalTame(this.horse, player);
    }
}
