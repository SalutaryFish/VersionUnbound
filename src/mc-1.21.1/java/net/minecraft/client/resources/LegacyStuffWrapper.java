package net.minecraft.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public class LegacyStuffWrapper {
    @Deprecated
    public static int[] getPixels(ResourceManager manager, ResourceLocation location) throws IOException {
        try (
            InputStream inputStream = manager.open(location);
            NativeImage nativeImage = NativeImage.read(inputStream);
        ) {
            return nativeImage.makePixelArray();
        }
    }
}
