package net.salutaryfish.versionunbound.testmod.events;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block no entity may destroy: it overrides NeoForge's {@code IBlockExtension.canEntityDestroy}, as Create's copycat
 * blocks do, for {@code GriefingScenario}. (No {@code @Override}: this testmod compiles without NeoForge's extension
 * interfaces on vanilla's classes. The name and descriptor are what a mod's override has.)
 */
final class MobProofBlock extends Block {
    MobProofBlock(Properties properties) {
        super(properties);
    }

    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }
}
