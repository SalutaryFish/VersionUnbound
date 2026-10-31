package net.minecraft.client.resources;

import java.io.IOException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.GrassColor;

public class GrassColorReloadListener extends SimplePreparableReloadListener<int[]> {
    private static final ResourceLocation LOCATION = ResourceLocation.withDefaultNamespace("textures/colormap/grass.png");

    /**
     * Performs any reloading that can be done off-thread, such as file IO
     */
    protected int[] prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        try {
            return LegacyStuffWrapper.getPixels(resourceManager, LOCATION);
        } catch (IOException iOException) {
            throw new IllegalStateException("Failed to load grass color texture", iOException);
        }
    }

    protected void apply(int[] object, ResourceManager resourceManager, ProfilerFiller profiler) {
        GrassColor.init(object);
    }
}
