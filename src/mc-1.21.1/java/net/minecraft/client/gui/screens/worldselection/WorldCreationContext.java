package net.minecraft.client.gui.screens.worldselection;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;

public record WorldCreationContext(
    WorldOptions options,
    Registry<LevelStem> datapackDimensions,
    WorldDimensions selectedDimensions,
    LayeredRegistryAccess<RegistryLayer> worldgenRegistries,
    ReloadableServerResources dataPackResources,
    WorldDataConfiguration dataConfiguration
) {
    public WorldCreationContext(
        WorldGenSettings worldGenSettings,
        LayeredRegistryAccess<RegistryLayer> worldGenRegistries,
        ReloadableServerResources dataPackResources,
        WorldDataConfiguration dataConfiguration
    ) {
        this(worldGenSettings.options(), worldGenSettings.dimensions(), worldGenRegistries, dataPackResources, dataConfiguration);
    }

    public WorldCreationContext(
        WorldOptions options,
        WorldDimensions selectedDimensions,
        LayeredRegistryAccess<RegistryLayer> worldGenRegistries,
        ReloadableServerResources dataPackResources,
        WorldDataConfiguration dataConfiguration
    ) {
        this(
            options,
            worldGenRegistries.getLayer(RegistryLayer.DIMENSIONS).registryOrThrow(Registries.LEVEL_STEM),
            selectedDimensions,
            worldGenRegistries.replaceFrom(RegistryLayer.DIMENSIONS),
            dataPackResources,
            dataConfiguration
        );
    }

    public WorldCreationContext withSettings(WorldOptions options, WorldDimensions selectedDimensions) {
        return new WorldCreationContext(
            options, this.datapackDimensions, selectedDimensions, this.worldgenRegistries, this.dataPackResources, this.dataConfiguration
        );
    }

    public WorldCreationContext withOptions(WorldCreationContext.OptionsModifier optionsModifier) {
        return new WorldCreationContext(
            optionsModifier.apply(this.options),
            this.datapackDimensions,
            this.selectedDimensions,
            this.worldgenRegistries,
            this.dataPackResources,
            this.dataConfiguration
        );
    }

    public WorldCreationContext withDimensions(WorldCreationContext.DimensionsUpdater dimensionsUpdater) {
        return new WorldCreationContext(
            this.options,
            this.datapackDimensions,
            dimensionsUpdater.apply(this.worldgenLoadContext(), this.selectedDimensions),
            this.worldgenRegistries,
            this.dataPackResources,
            this.dataConfiguration
        );
    }

    public RegistryAccess.Frozen worldgenLoadContext() {
        return this.worldgenRegistries.compositeAccess();
    }

    public void validate() {
        for (LevelStem levelStem : this.datapackDimensions()) {
            levelStem.generator().validate();
        }
    }

    @FunctionalInterface
    public interface DimensionsUpdater extends BiFunction<RegistryAccess.Frozen, WorldDimensions, WorldDimensions> {
    }

    public interface OptionsModifier extends UnaryOperator<WorldOptions> {
    }
}
