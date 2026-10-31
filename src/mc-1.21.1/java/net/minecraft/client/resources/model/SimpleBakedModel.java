package net.minecraft.client.resources.model;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public class SimpleBakedModel implements BakedModel {
    protected final List<BakedQuad> unculledFaces;
    protected final Map<Direction, List<BakedQuad>> culledFaces;
    protected final boolean hasAmbientOcclusion;
    protected final boolean isGui3d;
    protected final boolean usesBlockLight;
    protected final TextureAtlasSprite particleIcon;
    protected final ItemTransforms transforms;
    protected final ItemOverrides overrides;

    public SimpleBakedModel(
        List<BakedQuad> unculledFaces,
        Map<Direction, List<BakedQuad>> culledFaces,
        boolean hasAmbientOcclusion,
        boolean usesBlockLight,
        boolean isGui3d,
        TextureAtlasSprite particleIcon,
        ItemTransforms transforms,
        ItemOverrides overrides
    ) {
        this.unculledFaces = unculledFaces;
        this.culledFaces = culledFaces;
        this.hasAmbientOcclusion = hasAmbientOcclusion;
        this.isGui3d = isGui3d;
        this.usesBlockLight = usesBlockLight;
        this.particleIcon = particleIcon;
        this.transforms = transforms;
        this.overrides = overrides;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
        return direction == null ? this.unculledFaces : this.culledFaces.get(direction);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.hasAmbientOcclusion;
    }

    @Override
    public boolean isGui3d() {
        return this.isGui3d;
    }

    @Override
    public boolean usesBlockLight() {
        return this.usesBlockLight;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return this.particleIcon;
    }

    @Override
    public ItemTransforms getTransforms() {
        return this.transforms;
    }

    @Override
    public ItemOverrides getOverrides() {
        return this.overrides;
    }

    public static class Builder {
        private final List<BakedQuad> unculledFaces = Lists.newArrayList();
        private final Map<Direction, List<BakedQuad>> culledFaces = Maps.newEnumMap(Direction.class);
        private final ItemOverrides overrides;
        private final boolean hasAmbientOcclusion;
        private TextureAtlasSprite particleIcon;
        private final boolean usesBlockLight;
        private final boolean isGui3d;
        private final ItemTransforms transforms;

        public Builder(BlockModel blockModel, ItemOverrides overrides, boolean isGui3d) {
            this(blockModel.hasAmbientOcclusion(), blockModel.getGuiLight().lightLikeBlock(), isGui3d, blockModel.getTransforms(), overrides);
        }

        private Builder(boolean hasAmbientOcclusion, boolean usesBlockLight, boolean isGui3d, ItemTransforms transforms, ItemOverrides overrides) {
            for (Direction direction : Direction.values()) {
                this.culledFaces.put(direction, Lists.newArrayList());
            }

            this.overrides = overrides;
            this.hasAmbientOcclusion = hasAmbientOcclusion;
            this.usesBlockLight = usesBlockLight;
            this.isGui3d = isGui3d;
            this.transforms = transforms;
        }

        public SimpleBakedModel.Builder addCulledFace(Direction facing, BakedQuad quad) {
            this.culledFaces.get(facing).add(quad);
            return this;
        }

        public SimpleBakedModel.Builder addUnculledFace(BakedQuad quad) {
            this.unculledFaces.add(quad);
            return this;
        }

        public SimpleBakedModel.Builder particle(TextureAtlasSprite particleIcon) {
            this.particleIcon = particleIcon;
            return this;
        }

        public SimpleBakedModel.Builder item() {
            return this;
        }

        public BakedModel build() {
            if (this.particleIcon == null) {
                throw new RuntimeException("Missing particle!");
            } else {
                return new SimpleBakedModel(
                    this.unculledFaces,
                    this.culledFaces,
                    this.hasAmbientOcclusion,
                    this.usesBlockLight,
                    this.isGui3d,
                    this.particleIcon,
                    this.transforms,
                    this.overrides
                );
            }
        }
    }
}
