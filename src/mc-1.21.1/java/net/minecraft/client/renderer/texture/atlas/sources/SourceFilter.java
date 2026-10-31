package net.minecraft.client.renderer.texture.atlas.sources;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ResourceLocationPattern;

public class SourceFilter implements SpriteSource {
    public static final MapCodec<SourceFilter> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(ResourceLocationPattern.CODEC.fieldOf("pattern").forGetter(sourceFilter -> sourceFilter.filter))
            .apply(instance, SourceFilter::new)
    );
    private final ResourceLocationPattern filter;

    public SourceFilter(ResourceLocationPattern filter) {
        this.filter = filter;
    }

    @Override
    public void run(ResourceManager resourceManager, SpriteSource.Output output) {
        output.removeAll(this.filter.locationPredicate());
    }

    @Override
    public SpriteSourceType type() {
        return SpriteSources.FILTER;
    }
}
