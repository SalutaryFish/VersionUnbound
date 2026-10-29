package net.salutaryfish.versionunbound.neoforge.patches;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.neoforged.fml.ModLoader;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * The static methods NeoForge's patch adds to {@link SpawnPlacements}, which a mixin can't add (see the handoff's Known
 * gap 15): Layer A calls them here, and a mod calling them on {@code SpawnPlacements} gets {@code NoSuchMethodError}. The
 * bodies are NeoForge's; the class tweaker opens {@code DATA_BY_TYPE} and {@code SpawnPlacements.Data} to them.
 */
public final class SpawnPlacementsPatch {
    private SpawnPlacementsPatch() {}

    // Neo: Added to allow for checking if an entity has a spawn placement
    public static boolean hasPlacement(EntityType<?> type) {
        return SpawnPlacements.DATA_BY_TYPE.containsKey(type);
    }

    /**
     * Neo: Purely for Neo usage to fire off the spawn registering events.
     */
    @ApiStatus.Internal
    public static void fireSpawnPlacementEvent() {
        Map<EntityType<?>, RegisterSpawnPlacementsEvent.MergedSpawnPredicate<?>> map = Maps.newHashMap();
        SpawnPlacements.DATA_BY_TYPE.forEach((type, data) -> map.put(type, new RegisterSpawnPlacementsEvent.MergedSpawnPredicate<>(data.predicate(), data.placement(), data.heightMap())));
        ModLoader.postEvent(new RegisterSpawnPlacementsEvent(map));
        map.forEach(((entityType, merged) -> SpawnPlacements.DATA_BY_TYPE.put(entityType, new SpawnPlacements.Data(merged.getHeightmapType(), merged.getSpawnType(), merged.build()))));
    }
}
