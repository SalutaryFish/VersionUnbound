package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.salutaryfish.versionunbound.neoforge.mixin.patches.CropBlockInvoker;

/**
 * The static method NeoForge's patch adds to {@link CropBlock}: {@code getGrowthSpeed(BlockState, BlockGetter, BlockPos)},
 * which replaces vanilla's {@code getGrowthSpeed(Block, BlockGetter, BlockPos)} there. {@code AddedStaticMethods} adds a
 * method with that descriptor to {@code CropBlock} that calls this one, for mods. NeoForge's version uses the state only
 * for its block, so this asks vanilla's method, which {@code CropBlockMixin} gives NeoForge's soil checks
 * ({@code canSustainPlant}, {@code isFertile}); vanilla's callers (crops, stems, pitcher crops) and Fabric mods keep
 * calling vanilla's method and get the same answer.
 */
public final class CropBlockPatch {
    private CropBlockPatch() {}

    public static float getGrowthSpeed(BlockState cropBlockState, BlockGetter level, BlockPos pos) {
        return CropBlockInvoker.invokeGetGrowthSpeed(cropBlockState.getBlock(), level, pos);
    }
}
