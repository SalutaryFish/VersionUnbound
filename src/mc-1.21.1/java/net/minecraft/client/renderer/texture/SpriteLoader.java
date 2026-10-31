package net.minecraft.client.renderer.texture;

import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

public class SpriteLoader {
    public static final Set<MetadataSectionSerializer<?>> DEFAULT_METADATA_SECTIONS = Set.of(AnimationMetadataSection.SERIALIZER);
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ResourceLocation location;
    private final int maxSupportedTextureSize;
    private final int minWidth;
    private final int minHeight;

    public SpriteLoader(ResourceLocation location, int maxSupportedTextureSize, int minWidth, int minHeight) {
        this.location = location;
        this.maxSupportedTextureSize = maxSupportedTextureSize;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
    }

    public static SpriteLoader create(TextureAtlas atlas) {
        return new SpriteLoader(atlas.location(), atlas.maxSupportedTextureSize(), atlas.getWidth(), atlas.getHeight());
    }

    public SpriteLoader.Preparations stitch(List<SpriteContents> contents, int mipLevel, Executor executor) {
        int i = this.maxSupportedTextureSize;
        Stitcher<SpriteContents> stitcher = new Stitcher<>(i, i, mipLevel);
        int j = Integer.MAX_VALUE;
        int k = 1 << mipLevel;

        for (SpriteContents spriteContents : contents) {
            j = Math.min(j, Math.min(spriteContents.width(), spriteContents.height()));
            int l = Math.min(Integer.lowestOneBit(spriteContents.width()), Integer.lowestOneBit(spriteContents.height()));
            if (l < k) {
                LOGGER.warn(
                    "Texture {} with size {}x{} limits mip level from {} to {}",
                    spriteContents.name(),
                    spriteContents.width(),
                    spriteContents.height(),
                    Mth.log2(k),
                    Mth.log2(l)
                );
                k = l;
            }

            stitcher.registerSprite(spriteContents);
        }

        int m = Math.min(j, k);
        int n = Mth.log2(m);
        int l;
        if (n < mipLevel) {
            LOGGER.warn("{}: dropping miplevel from {} to {}, because of minimum power of two: {}", this.location, mipLevel, n, m);
            l = n;
        } else {
            l = mipLevel;
        }

        try {
            stitcher.stitch();
        } catch (StitcherException stitcherException) {
            CrashReport crashReport = CrashReport.forThrowable(stitcherException, "Stitching");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Stitcher");
            crashReportCategory.setDetail(
                "Sprites",
                stitcherException.getAllSprites()
                    .stream()
                    .map(sprite -> String.format(Locale.ROOT, "%s[%dx%d]", sprite.name(), sprite.width(), sprite.height()))
                    .collect(Collectors.joining(","))
            );
            crashReportCategory.setDetail("Max Texture Size", i);
            throw new ReportedException(crashReport);
        }

        int o = Math.max(stitcher.getWidth(), this.minWidth);
        int p = Math.max(stitcher.getHeight(), this.minHeight);
        Map<ResourceLocation, TextureAtlasSprite> map = this.getStitchedSprites(stitcher, o, p);
        TextureAtlasSprite textureAtlasSprite = map.get(MissingTextureAtlasSprite.getLocation());
        CompletableFuture<Void> completableFuture;
        if (l > 0) {
            completableFuture = CompletableFuture.runAsync(() -> map.values().forEach(sprite -> sprite.contents().increaseMipLevel(l)), executor);
        } else {
            completableFuture = CompletableFuture.completedFuture(null);
        }

        return new SpriteLoader.Preparations(o, p, l, textureAtlasSprite, map, completableFuture);
    }

    public static CompletableFuture<List<SpriteContents>> runSpriteSuppliers(
        SpriteResourceLoader spriteResourceLoader, List<Function<SpriteResourceLoader, SpriteContents>> factories, Executor executor
    ) {
        List<CompletableFuture<SpriteContents>> list = factories.stream()
            .map(factory -> CompletableFuture.supplyAsync(() -> (SpriteContents)factory.apply(spriteResourceLoader), executor))
            .toList();
        return Util.sequence(list).thenApply(contentsList -> contentsList.stream().filter(Objects::nonNull).toList());
    }

    public CompletableFuture<SpriteLoader.Preparations> loadAndStitch(
        ResourceManager resouceManager, ResourceLocation location, int mipLevel, Executor executor
    ) {
        return this.loadAndStitch(resouceManager, location, mipLevel, executor, DEFAULT_METADATA_SECTIONS);
    }

    public CompletableFuture<SpriteLoader.Preparations> loadAndStitch(
        ResourceManager resourceManager,
        ResourceLocation location,
        int mipLevel,
        Executor executor,
        Collection<MetadataSectionSerializer<?>> sectionSerializers
    ) {
        SpriteResourceLoader spriteResourceLoader = SpriteResourceLoader.create(sectionSerializers);
        return CompletableFuture.<List<Function<SpriteResourceLoader, SpriteContents>>>supplyAsync(
                () -> SpriteSourceList.load(resourceManager, location).list(resourceManager), executor
            )
            .thenCompose(factories -> runSpriteSuppliers(spriteResourceLoader, (List<Function<SpriteResourceLoader, SpriteContents>>)factories, executor))
            .thenApply(contentsList -> this.stitch((List<SpriteContents>)contentsList, mipLevel, executor));
    }

    private Map<ResourceLocation, TextureAtlasSprite> getStitchedSprites(Stitcher<SpriteContents> stitcher, int x, int y) {
        Map<ResourceLocation, TextureAtlasSprite> map = new HashMap<>();
        stitcher.gatherSprites((contents, xx, yx) -> map.put(contents.name(), new TextureAtlasSprite(this.location, contents, x, y, xx, yx)));
        return map;
    }

    public record Preparations(
        int width,
        int height,
        int mipLevel,
        TextureAtlasSprite missing,
        Map<ResourceLocation, TextureAtlasSprite> regions,
        CompletableFuture<Void> readyForUpload
    ) {
        public CompletableFuture<SpriteLoader.Preparations> waitForUpload() {
            return this.readyForUpload.thenApply(void_ -> this);
        }
    }
}
