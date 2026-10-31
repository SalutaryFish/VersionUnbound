package net.minecraft.client.renderer.block.model;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class ItemOverrides {
    public static final ItemOverrides EMPTY = new ItemOverrides();
    public static final float NO_OVERRIDE = Float.NEGATIVE_INFINITY;
    private final ItemOverrides.BakedOverride[] overrides;
    private final ResourceLocation[] properties;

    private ItemOverrides() {
        this.overrides = new ItemOverrides.BakedOverride[0];
        this.properties = new ResourceLocation[0];
    }

    public ItemOverrides(ModelBaker baker, BlockModel model, List<ItemOverride> overrides) {
        this.properties = overrides.stream()
            .flatMap(ItemOverride::getPredicates)
            .map(ItemOverride.Predicate::getProperty)
            .distinct()
            .toArray(ResourceLocation[]::new);
        Object2IntMap<ResourceLocation> object2IntMap = new Object2IntOpenHashMap<>();

        for (int i = 0; i < this.properties.length; i++) {
            object2IntMap.put(this.properties[i], i);
        }

        List<ItemOverrides.BakedOverride> list = Lists.newArrayList();

        for (int j = overrides.size() - 1; j >= 0; j--) {
            ItemOverride itemOverride = overrides.get(j);
            BakedModel bakedModel = this.bakeModel(baker, model, itemOverride);
            ItemOverrides.PropertyMatcher[] propertyMatchers = itemOverride.getPredicates().map(override -> {
                int i = object2IntMap.getInt(override.getProperty());
                return new ItemOverrides.PropertyMatcher(i, override.getValue());
            }).toArray(ItemOverrides.PropertyMatcher[]::new);
            list.add(new ItemOverrides.BakedOverride(propertyMatchers, bakedModel));
        }

        this.overrides = list.toArray(new ItemOverrides.BakedOverride[0]);
    }

    @Nullable
    private BakedModel bakeModel(ModelBaker baker, BlockModel model, ItemOverride override) {
        UnbakedModel unbakedModel = baker.getModel(override.getModel());
        return Objects.equals(unbakedModel, model) ? null : baker.bake(override.getModel(), BlockModelRotation.X0_Y0);
    }

    @Nullable
    public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        if (this.overrides.length != 0) {
            int i = this.properties.length;
            float[] fs = new float[i];

            for (int j = 0; j < i; j++) {
                ResourceLocation resourceLocation = this.properties[j];
                ItemPropertyFunction itemPropertyFunction = ItemProperties.getProperty(stack, resourceLocation);
                if (itemPropertyFunction != null) {
                    fs[j] = itemPropertyFunction.call(stack, level, entity, seed);
                } else {
                    fs[j] = Float.NEGATIVE_INFINITY;
                }
            }

            for (ItemOverrides.BakedOverride bakedOverride : this.overrides) {
                if (bakedOverride.test(fs)) {
                    BakedModel bakedModel = bakedOverride.model;
                    if (bakedModel == null) {
                        return model;
                    }

                    return bakedModel;
                }
            }
        }

        return model;
    }

    static class BakedOverride {
        private final ItemOverrides.PropertyMatcher[] matchers;
        @Nullable
        final BakedModel model;

        BakedOverride(ItemOverrides.PropertyMatcher[] matchers, @Nullable BakedModel model) {
            this.matchers = matchers;
            this.model = model;
        }

        boolean test(float[] properties) {
            for (ItemOverrides.PropertyMatcher propertyMatcher : this.matchers) {
                float f = properties[propertyMatcher.index];
                if (f < propertyMatcher.value) {
                    return false;
                }
            }

            return true;
        }
    }

    static class PropertyMatcher {
        public final int index;
        public final float value;

        PropertyMatcher(int index, float value) {
            this.index = index;
            this.value = value;
        }
    }
}
