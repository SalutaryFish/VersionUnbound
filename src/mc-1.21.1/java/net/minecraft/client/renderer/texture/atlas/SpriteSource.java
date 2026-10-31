package net.minecraft.client.renderer.texture.atlas;

import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public interface SpriteSource {
    FileToIdConverter TEXTURE_ID_CONVERTER = new FileToIdConverter("textures", ".png");

    void run(ResourceManager var1, SpriteSource.Output var2);

    SpriteSourceType type();

    interface Output {
        default void add(ResourceLocation location, Resource resource) {
            this.add(location, spriteResourceLoader -> spriteResourceLoader.loadSprite(location, resource));
        }

        void add(ResourceLocation var1, SpriteSource.SpriteSupplier var2);

        void removeAll(Predicate<ResourceLocation> var1);
    }

    interface SpriteSupplier extends Function<SpriteResourceLoader, SpriteContents> {
        default void discard() {
        }
    }
}
