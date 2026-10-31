package net.minecraft.client.resources.model;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.level.block.state.BlockState;

public class WeightedBakedModel implements BakedModel {
    private final int totalWeight;
    private final List<WeightedEntry.Wrapper<BakedModel>> list;
    private final BakedModel wrapped;

    public WeightedBakedModel(List<WeightedEntry.Wrapper<BakedModel>> list) {
        this.list = list;
        this.totalWeight = WeightedRandom.getTotalWeight(list);
        this.wrapped = list.get(0).data();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
        return WeightedRandom.getWeightedItem(this.list, Math.abs((int)random.nextLong()) % this.totalWeight)
            .map(wrapper -> wrapper.data().getQuads(state, direction, random))
            .orElse(Collections.emptyList());
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.wrapped.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return this.wrapped.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return this.wrapped.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return this.wrapped.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return this.wrapped.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return this.wrapped.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return this.wrapped.getOverrides();
    }

    public static class Builder {
        private final List<WeightedEntry.Wrapper<BakedModel>> list = Lists.newArrayList();

        public WeightedBakedModel.Builder add(@Nullable BakedModel model, int weight) {
            if (model != null) {
                this.list.add(WeightedEntry.wrap(model, weight));
            }

            return this;
        }

        @Nullable
        public BakedModel build() {
            if (this.list.isEmpty()) {
                return null;
            } else {
                return this.list.size() == 1 ? this.list.get(0).data() : new WeightedBakedModel(this.list);
            }
        }
    }
}
