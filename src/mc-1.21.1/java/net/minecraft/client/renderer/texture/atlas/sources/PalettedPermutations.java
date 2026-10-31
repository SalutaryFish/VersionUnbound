package net.minecraft.client.renderer.texture.atlas.sources;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.FastColor;
import org.slf4j.Logger;

public class PalettedPermutations implements SpriteSource {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<PalettedPermutations> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.list(ResourceLocation.CODEC).fieldOf("textures").forGetter(palettedPermutations -> palettedPermutations.textures),
                ResourceLocation.CODEC.fieldOf("palette_key").forGetter(palettedPermutations -> palettedPermutations.paletteKey),
                Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC)
                    .fieldOf("permutations")
                    .forGetter(palettedPermutations -> palettedPermutations.permutations)
            )
            .apply(instance, PalettedPermutations::new)
    );
    private final List<ResourceLocation> textures;
    private final Map<String, ResourceLocation> permutations;
    private final ResourceLocation paletteKey;

    private PalettedPermutations(List<ResourceLocation> textures, ResourceLocation paletteKey, Map<String, ResourceLocation> permutations) {
        this.textures = textures;
        this.permutations = permutations;
        this.paletteKey = paletteKey;
    }

    @Override
    public void run(ResourceManager resourceManager, SpriteSource.Output output) {
        Supplier<int[]> supplier = Suppliers.memoize(() -> loadPaletteEntryFromImage(resourceManager, this.paletteKey));
        Map<String, Supplier<IntUnaryOperator>> map = new HashMap<>();
        this.permutations
            .forEach(
                (string, resourceLocationx) -> map.put(
                    string, Suppliers.memoize(() -> createPaletteMapping(supplier.get(), loadPaletteEntryFromImage(resourceManager, resourceLocationx)))
                )
            );

        for (ResourceLocation resourceLocation : this.textures) {
            ResourceLocation resourceLocation2 = TEXTURE_ID_CONVERTER.idToFile(resourceLocation);
            Optional<Resource> optional = resourceManager.getResource(resourceLocation2);
            if (optional.isEmpty()) {
                LOGGER.warn("Unable to find texture {}", resourceLocation2);
            } else {
                LazyLoadedImage lazyLoadedImage = new LazyLoadedImage(resourceLocation2, optional.get(), map.size());

                for (Entry<String, Supplier<IntUnaryOperator>> entry : map.entrySet()) {
                    ResourceLocation resourceLocation3 = resourceLocation.withSuffix("_" + entry.getKey());
                    output.add(resourceLocation3, new PalettedPermutations.PalettedSpriteSupplier(lazyLoadedImage, entry.getValue(), resourceLocation3));
                }
            }
        }
    }

    private static IntUnaryOperator createPaletteMapping(int[] is, int[] js) {
        if (js.length != is.length) {
            LOGGER.warn("Palette mapping has different sizes: {} and {}", is.length, js.length);
            throw new IllegalArgumentException();
        }

        Int2IntMap int2IntMap = new Int2IntOpenHashMap(js.length);

        for (int i = 0; i < is.length; i++) {
            int j = is[i];
            if (FastColor.ABGR32.alpha(j) != 0) {
                int2IntMap.put(FastColor.ABGR32.transparent(j), js[i]);
            }
        }

        return color -> {
            int ix = FastColor.ABGR32.alpha(color);
            if (ix == 0) {
                return color;
            }

            int jx = FastColor.ABGR32.transparent(color);
            int k = int2IntMap.getOrDefault(jx, FastColor.ABGR32.opaque(jx));
            int l = FastColor.ABGR32.alpha(k);
            return FastColor.ABGR32.color(ix * l / 255, k);
        };
    }

    public static int[] loadPaletteEntryFromImage(ResourceManager resourceMananger, ResourceLocation palette) {
        Optional<Resource> optional = resourceMananger.getResource(TEXTURE_ID_CONVERTER.idToFile(palette));
        if (optional.isEmpty()) {
            LOGGER.error("Failed to load palette image {}", palette);
            throw new IllegalArgumentException();
        }

        try (
            InputStream inputStream = optional.get().open();
            NativeImage nativeImage = NativeImage.read(inputStream);
        ) {
            return nativeImage.getPixelsRGBA();
        } catch (Exception exception) {
            LOGGER.error("Couldn't load texture {}", palette, exception);
            throw new IllegalArgumentException();
        }
    }

    @Override
    public SpriteSourceType type() {
        return SpriteSources.PALETTED_PERMUTATIONS;
    }

    record PalettedSpriteSupplier(LazyLoadedImage baseImage, Supplier<IntUnaryOperator> palette, ResourceLocation permutationLocation)
        implements SpriteSource.SpriteSupplier {
        @Nullable
        public SpriteContents apply(SpriteResourceLoader spriteResourceLoader) {
            try {
                NativeImage nativeImage = this.baseImage.get().mappedCopy(this.palette.get());
                return new SpriteContents(
                    this.permutationLocation, new FrameSize(nativeImage.getWidth(), nativeImage.getHeight()), nativeImage, ResourceMetadata.EMPTY
                );
            } catch (IOException | IllegalArgumentException exception) {
                PalettedPermutations.LOGGER.error("unable to apply palette to {}", this.permutationLocation, exception);
                return null;
            } finally {
                this.baseImage.release();
            }
        }

        @Override
        public void discard() {
            this.baseImage.release();
        }
    }
}
