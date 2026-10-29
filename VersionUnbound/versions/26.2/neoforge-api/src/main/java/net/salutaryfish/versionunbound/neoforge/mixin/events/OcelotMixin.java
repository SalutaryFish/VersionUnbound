package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code Ocelot}: {@code AnimalTameEvent} once the roll for its food succeeds (an ocelot doesn't
 * become tame, it trusts the player); canceling it keeps the ocelot from trusting the player.
 */
@Mixin(Ocelot.class)
abstract class OcelotMixin {
    @Definition(id = "nextInt", method = "Lnet/minecraft/util/RandomSource;nextInt(I)I")
    @Expression("?.nextInt(3) == 0")
    @ModifyExpressionValue(method = "mobInteract", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$animalTame(boolean tamed, @Local(argsOnly = true) Player player) {
        return tamed && !EventHooks.onAnimalTame((Animal) (Object) this, player);
    }
}
