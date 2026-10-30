package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's protected {@code CropBlock.getGrowthSpeed(Block, ...)}, for {@code CropBlockPatch}. */
@Mixin(CropBlock.class)
public interface CropBlockInvoker {
    @Invoker("getGrowthSpeed")
    static float invokeGetGrowthSpeed(Block type, BlockGetter level, BlockPos pos) {
        throw new AssertionError();
    }
}
