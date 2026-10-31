package net.minecraft.world.flag;

import com.mojang.serialization.Codec;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

public class FeatureFlags {
    public static final FeatureFlag VANILLA;
    public static final FeatureFlag BUNDLE;
    public static final FeatureFlag TRADE_REBALANCE;
    public static final FeatureFlagRegistry REGISTRY;
    public static final Codec<FeatureFlagSet> CODEC;
    public static final FeatureFlagSet VANILLA_SET;
    public static final FeatureFlagSet DEFAULT_FLAGS;

    public static String printMissingFlags(FeatureFlagSet enabledFeatures, FeatureFlagSet requestedFeatures) {
        return printMissingFlags(REGISTRY, enabledFeatures, requestedFeatures);
    }

    public static String printMissingFlags(FeatureFlagRegistry registry, FeatureFlagSet enabledFeatures, FeatureFlagSet requestedFeatures) {
        Set<ResourceLocation> set = registry.toNames(requestedFeatures);
        Set<ResourceLocation> set2 = registry.toNames(enabledFeatures);
        return set.stream().filter(requestFeature -> !set2.contains(requestFeature)).map(ResourceLocation::toString).collect(Collectors.joining(", "));
    }

    public static boolean isExperimental(FeatureFlagSet set) {
        return !set.isSubsetOf(VANILLA_SET);
    }

    static {
        FeatureFlagRegistry.Builder builder = new FeatureFlagRegistry.Builder("main");
        VANILLA = builder.createVanilla("vanilla");
        BUNDLE = builder.createVanilla("bundle");
        TRADE_REBALANCE = builder.createVanilla("trade_rebalance");
        REGISTRY = builder.build();
        CODEC = REGISTRY.codec();
        VANILLA_SET = FeatureFlagSet.of(VANILLA);
        DEFAULT_FLAGS = VANILLA_SET;
    }
}
