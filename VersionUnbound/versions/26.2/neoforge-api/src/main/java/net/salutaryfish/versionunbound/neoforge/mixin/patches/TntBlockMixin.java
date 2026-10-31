package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code TntBlock}: TNT overrides the block extension's {@code onCaughtFire} to prime, and its own
 * five sites ask {@code onCaughtFire} instead of priming directly (so a mod's TNT subclass decides). Fire, lava's
 * neighbours and the flint and steel dispenser reach TNT through {@code onCaughtFire} too (FireBlockMixin,
 * FlintAndSteelDispenseItemBehaviorMixin).
 */
@Mixin(TntBlock.class)
abstract class TntBlockMixin {
    @Shadow
    private static boolean prime(final Level level, final BlockPos pos, final @Nullable LivingEntity source) {
        throw new AssertionError();
    }

    // NeoForge's override of IBlockExtension.onCaughtFire.
    public boolean onCaughtFire(BlockState state, Level world, BlockPos pos, @Nullable Direction face, @Nullable LivingEntity igniter) {
        return prime(world, pos, igniter);
    }

    @WrapOperation(
            method = { "onPlace", "neighborChanged" },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/TntBlock;prime(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean versionunbound$caughtFire(final Level level, final BlockPos pos, final Operation<Boolean> prime, @Local(argsOnly = true) final BlockState state) {
        return ((Block) (Object) this).onCaughtFire(state, level, pos, null, null);
    }

    @WrapOperation(
            method = "playerWillDestroy",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/TntBlock;prime(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean versionunbound$caughtFireDestroyed(final Level level, final BlockPos pos, final Operation<Boolean> prime, @Local(argsOnly = true) final BlockState state) {
        return ((Block) (Object) this).onCaughtFire(state, level, pos, null, null);
    }

    @WrapOperation(
            method = "useItemOn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/TntBlock;prime(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean versionunbound$caughtFireUsed(final Level level, final BlockPos pos, final @Nullable LivingEntity player, final Operation<Boolean> prime,
            @Local(argsOnly = true) final BlockState state, @Local(argsOnly = true) final BlockHitResult hitResult) {
        return ((Block) (Object) this).onCaughtFire(state, level, pos, hitResult.getDirection(), player);
    }

    @WrapOperation(
            method = "onProjectileHit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/TntBlock;prime(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean versionunbound$caughtFireShot(final Level level, final BlockPos pos, final @Nullable LivingEntity owner, final Operation<Boolean> prime,
            @Local(argsOnly = true) final BlockState state) {
        return ((Block) (Object) this).onCaughtFire(state, level, pos, null, owner);
    }
}
