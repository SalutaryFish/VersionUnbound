package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code Fox.FoxBreedGoal}: {@code BabyEntitySpawnEvent} for two foxes' cub, as in
 * {@link AnimalMixin}. Vanilla casts the result to {@code Fox}, so a listener's child must be a fox, as in NeoForge.
 */
@Mixin(targets = "net.minecraft.world.entity.animal.fox.Fox$FoxBreedGoal")
abstract class FoxBreedGoalMixin {
    // The call's receiver and argument are the goal's animal and partner fields (declared in BreedGoal).
    @WrapOperation(
            method = "breed",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Animal;getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/AgeableMob;"))
    private @Nullable AgeableMob versionunbound$babyEntitySpawn(Animal animal, ServerLevel level, AgeableMob partnerMob, Operation<AgeableMob> original, @Cancellable CallbackInfo ci) {
        AgeableMob offspring = original.call(animal, level, partnerMob);
        Animal partner = (Animal) partnerMob;
        final BabyEntitySpawnEvent event = new BabyEntitySpawnEvent(animal, partner, offspring);
        final boolean cancelled = NeoForge.EVENT_BUS.post(event).isCanceled();
        offspring = event.getChild();
        if (cancelled) {
            //Reset the "inLove" state for the animals
            animal.setAge(6000);
            partner.setAge(6000);
            animal.resetLove();
            partner.resetLove();
            ci.cancel();
        }
        return offspring;
    }
}
