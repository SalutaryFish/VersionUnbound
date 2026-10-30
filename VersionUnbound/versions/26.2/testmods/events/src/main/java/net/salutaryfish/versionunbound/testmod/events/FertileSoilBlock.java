package net.salutaryfish.versionunbound.testmod.events;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.TriState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A soil that isn't in vanilla's crop soil tag but sustains every plant and is always fertile: it overrides NeoForge's
 * {@code IBlockExtension.canSustainPlant} and {@code isFertile}, as Farmer's Delight's rich soil does, for
 * {@code CropScenario}'s growth speed checks. (No {@code @Override}: this testmod compiles without NeoForge's extension
 * interfaces on vanilla's classes. The names and descriptors are what a mod's overrides have.)
 */
final class FertileSoilBlock extends Block {
    FertileSoilBlock(Properties properties) {
        super(properties);
    }

    public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos soilPosition, Direction facing, BlockState plant) {
        return TriState.TRUE;
    }

    public boolean isFertile(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
