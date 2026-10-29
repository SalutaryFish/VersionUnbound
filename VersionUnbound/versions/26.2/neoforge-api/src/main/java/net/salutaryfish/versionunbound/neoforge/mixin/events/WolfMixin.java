package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code Wolf}: {@code AnimalTameEvent} once the roll for a bone succeeds; canceling it fails the
 * taming.
 */
@Mixin(Wolf.class)
abstract class WolfMixin {
    @Definition(id = "nextInt", method = "Lnet/minecraft/util/RandomSource;nextInt(I)I")
    @Expression("?.nextInt(3) == 0")
    @ModifyExpressionValue(method = "tryToTame", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$animalTame(boolean tamed, @Local(argsOnly = true) Player player) {
        return tamed && !EventHooks.onAnimalTame((Animal) (Object) this, player);
    }
}
