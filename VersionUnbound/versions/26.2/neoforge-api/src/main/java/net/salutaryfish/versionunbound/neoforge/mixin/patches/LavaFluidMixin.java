package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code LavaFluid}: lava asks the block extension's side-aware {@code ignitedByLava} through
 * NeoForge's three-argument {@code isFlammable}, and the fire it lights and the stone it makes over water go through
 * {@code BlockEvent.FluidPlaceBlockEvent}. {@code @Overwrite}s with NeoForge's patched bodies.
 */
@Mixin(LavaFluid.class)
abstract class LavaFluidMixin {
    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    public void randomTick(final ServerLevel level, final BlockPos pos, final FluidState fluidState, final RandomSource random) {
        if (level.canSpreadFireAround(pos)) {
            int passes = random.nextInt(3);
            if (passes > 0) {
                BlockPos testPos = pos;

                for (int pass = 0; pass < passes; pass++) {
                    testPos = testPos.offset(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);
                    if (!level.isLoaded(testPos)) {
                        return;
                    }

                    BlockState blockState = level.getBlockState(testPos);
                    if (blockState.isAir()) {
                        if (this.hasFlammableNeighbours(level, testPos)) {
                            level.setBlockAndUpdate(testPos, EventHooks.fireFluidPlaceBlockEvent(level, testPos, pos, BaseFireBlock.getState(level, testPos)));
                            return;
                        }
                    } else if (blockState.blocksMotion()) {
                        return;
                    }
                }
            } else {
                for (int i = 0; i < 3; i++) {
                    BlockPos testPos = pos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                    if (!level.isLoaded(testPos)) {
                        return;
                    }

                    if (level.isEmptyBlock(testPos.above()) && this.isFlammable(level, testPos, Direction.UP)) {
                        level.setBlockAndUpdate(testPos.above(), EventHooks.fireFluidPlaceBlockEvent(level, testPos.above(), pos, BaseFireBlock.getState(level, testPos)));
                    }
                }
            }
        }
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    private boolean hasFlammableNeighbours(final LevelReader level, final BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (this.isFlammable(level, pos.relative(direction), direction.getOpposite())) {
                return true;
            }
        }

        return false;
    }

    // NeoForge's three-argument form; vanilla's two-argument one stays (unused).
    private boolean isFlammable(LevelReader level, BlockPos pos, Direction face) {
        if (level.isInsideBuildHeight(pos.getY()) && !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.ignitedByLava(level, pos, face);
    }

    @WrapOperation(
            method = "spreadTo",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean versionunbound$fluidPlaceStone(final LevelAccessor level, final BlockPos pos, final BlockState state, final int flags, final Operation<Boolean> setBlock) {
        return setBlock.call(level, pos, EventHooks.fireFluidPlaceBlockEvent(level, pos, pos, state), flags);
    }
}
