package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Implements {@code ILevelExtension}'s two abstract methods, with NeoForge's field. Not yet: NeoForge's patches that
 * read the radius in the level's entity lookups (they widen the searched sections for entities larger than 2 blocks),
 * and the entity types that raise it.
 */
@Mixin(Level.class)
abstract class LevelEntityRadiusMixin {
    @Unique
    private double maxEntityRadius = 2.0D;

    public double getMaxEntityRadius() {
        return maxEntityRadius;
    }

    public double increaseMaxEntityRadius(double value) {
        if (value > maxEntityRadius)
            maxEntityRadius = value;
        return maxEntityRadius;
    }
}
