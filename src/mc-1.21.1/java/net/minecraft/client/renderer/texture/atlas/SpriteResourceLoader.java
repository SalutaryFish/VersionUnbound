package net.minecraft.client.renderer.texture.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

@FunctionalInterface
public interface SpriteResourceLoader {
    Logger LOGGER = LogUtils.getLogger();

    static SpriteResourceLoader create(Collection<MetadataSectionSerializer<?>> sectionSerializers) {
        return (resourceLocation, resource) -> {
            ResourceMetadata resourceMetadata;
            try {
                resourceMetadata = resource.metadata().copySections(sectionSerializers);
            } catch (Exception exception) {
                LOGGER.error("Unable to parse metadata from {}", resourceLocation, exception);
                return null;
            }

            NativeImage nativeImage;
            try (InputStream inputStream = resource.open()) {
                nativeImage = NativeImage.read(inputStream);
            } catch (IOException iOException) {
                LOGGER.error("Using missing texture, unable to load {}", resourceLocation, iOException);
                return null;
            }

            AnimationMetadataSection animationMetadataSection = resourceMetadata.getSection(AnimationMetadataSection.SERIALIZER)
                .orElse(AnimationMetadataSection.EMPTY);
            FrameSize frameSize = animationMetadataSection.calculateFrameSize(nativeImage.getWidth(), nativeImage.getHeight());
            if (Mth.isMultipleOf(nativeImage.getWidth(), frameSize.width()) && Mth.isMultipleOf(nativeImage.getHeight(), frameSize.height())) {
                return new SpriteContents(resourceLocation, frameSize, nativeImage, resourceMetadata);
            }

            LOGGER.error(
                "Image {} size {},{} is not multiple of frame size {},{}",
                resourceLocation,
                nativeImage.getWidth(),
                nativeImage.getHeight(),
                frameSize.width(),
                frameSize.height()
            );
            nativeImage.close();
            return null;
        };
    }

    @Nullable
    SpriteContents loadSprite(ResourceLocation var1, Resource var2);
}
