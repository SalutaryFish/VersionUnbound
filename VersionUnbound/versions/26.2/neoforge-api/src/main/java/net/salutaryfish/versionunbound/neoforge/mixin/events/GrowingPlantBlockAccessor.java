package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.GrowingPlantBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A growing plant's direction, for {@code GrowingPlantHeadBlockMixin}. */
@Mixin(GrowingPlantBlock.class)
public interface GrowingPlantBlockAccessor {
    @Accessor("growthDirection")
    Direction versionunbound$growthDirection();
}
