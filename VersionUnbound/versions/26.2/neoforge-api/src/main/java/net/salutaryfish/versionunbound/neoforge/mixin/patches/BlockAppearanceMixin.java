package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * {@code getAppearance} is a default method of both Fabric API's {@code FabricBlock} and NeoForge's
 * {@code IBlockExtension}, with the same descriptor and the same body. With both injected into {@link Block}, a call on a
 * block that doesn't override it would find two defaults and throw {@code IncompatibleClassChangeError}; declared on
 * {@code Block} itself, it is the one both interfaces' callers reach, and mods' overrides (from either side) replace it.
 */
@Mixin(Block.class)
abstract class BlockAppearanceMixin {
    public BlockState getAppearance(BlockState state, BlockAndLightGetter level, BlockPos pos, Direction side, @Nullable BlockState queryState,
            @Nullable BlockPos queryPos) {
        return state;
    }
}
