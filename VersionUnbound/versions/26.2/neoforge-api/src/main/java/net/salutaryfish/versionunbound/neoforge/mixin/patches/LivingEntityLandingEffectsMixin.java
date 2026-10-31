package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code LivingEntity.checkFallDamage}: the block landed on gets {@code addLandingEffects} with the
 * particle count first, and vanilla's block particles are sent only if it answers false. NeoForge also gives the particle
 * the landing position (its three-argument {@code BlockParticleOption}); Fabric API's particle module already sets it on
 * vanilla's two-argument one at the same site, so that part stays Fabric API's. NeoForge decides before building the
 * particle; here the particle is built either way (it has no side effects).
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityLandingEffectsMixin {
    @WrapOperation(method = "checkFallDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"))
    private int versionunbound$landingEffects(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count, double xDist, double yDist,
            double zDist, double speed, Operation<Integer> original, double ya, boolean onGround, BlockState onState, BlockPos pos) {
        if (onState.addLandingEffects(level, pos, onState, (LivingEntity) (Object) this, count)) return 0;
        return original.call(level, particle, x, y, z, count, xDist, yDist, zDist, speed);
    }
}
