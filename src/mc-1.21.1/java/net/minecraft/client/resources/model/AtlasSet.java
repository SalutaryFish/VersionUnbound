package net.minecraft.client.resources.model;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public class AtlasSet implements AutoCloseable {
    private final Map<ResourceLocation, AtlasSet.AtlasEntry> atlases;

    public AtlasSet(Map<ResourceLocation, ResourceLocation> atlasMap, TextureManager textureManager) {
        this.atlases = atlasMap.entrySet().stream().collect(Collectors.toMap(Entry::getKey, entry -> {
            TextureAtlas textureAtlas = new TextureAtlas(entry.getKey());
            textureManager.register(entry.getKey(), textureAtlas);
            return new AtlasSet.AtlasEntry(textureAtlas, entry.getValue());
        }));
    }

    public TextureAtlas getAtlas(ResourceLocation location) {
        return this.atlases.get(location).atlas();
    }

    @Override
    public void close() {
        this.atlases.values().forEach(AtlasSet.AtlasEntry::close);
        this.atlases.clear();
    }

    public Map<ResourceLocation, CompletableFuture<AtlasSet.StitchResult>> scheduleLoad(ResourceManager resourceManager, int mipLevel, Executor executor) {
        return this.atlases
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey,
                    atlasesEntry -> {
                        AtlasSet.AtlasEntry atlasEntry = atlasesEntry.getValue();
                        return SpriteLoader.create(atlasEntry.atlas)
                            .loadAndStitch(resourceManager, atlasEntry.atlasInfoLocation, mipLevel, executor)
                            .thenApply(preparations -> new AtlasSet.StitchResult(atlasEntry.atlas, preparations));
                    }
                )
            );
    }

    record AtlasEntry(TextureAtlas atlas, ResourceLocation atlasInfoLocation) implements AutoCloseable {
        @Override
        public void close() {
            this.atlas.clearTextureData();
        }
    }

    public static class StitchResult {
        private final TextureAtlas atlas;
        private final SpriteLoader.Preparations preparations;

        public StitchResult(TextureAtlas atlas, SpriteLoader.Preparations preperations) {
            this.atlas = atlas;
            this.preparations = preperations;
        }

        @Nullable
        public TextureAtlasSprite getSprite(ResourceLocation location) {
            return this.preparations.regions().get(location);
        }

        public TextureAtlasSprite missing() {
            return this.preparations.missing();
        }

        public CompletableFuture<Void> readyForUpload() {
            return this.preparations.readyForUpload();
        }

        public void upload() {
            this.atlas.upload(this.preparations);
        }
    }
}
