package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableList.Builder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.Graph;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.apache.commons.lang3.mutable.MutableInt;

public class FeatureSorter {
    public static <T> List<FeatureSorter.StepFeatureData> buildFeaturesPerStep(
        List<T> featureSetSources, Function<T, List<HolderSet<PlacedFeature>>> toFeatueSetFunction, boolean notRecursiveFlag
    ) {
        Object2IntMap<PlacedFeature> object2IntMap = new Object2IntOpenHashMap<>();
        MutableInt mutableInt = new MutableInt(0);

        record FeatureData(int featureIndex, int step, PlacedFeature feature) {
        }

        Comparator<FeatureData> comparator = Comparator.comparingInt(FeatureData::step).thenComparingInt(FeatureData::featureIndex);
        Map<FeatureData, Set<FeatureData>> map = new TreeMap<>(comparator);
        int i = 0;

        for (T object : featureSetSources) {
            List<FeatureData> list = Lists.newArrayList();
            List<HolderSet<PlacedFeature>> list2 = toFeatueSetFunction.apply(object);
            i = Math.max(i, list2.size());

            for (int j = 0; j < list2.size(); j++) {
                for (Holder<PlacedFeature> holder : list2.get(j)) {
                    PlacedFeature placedFeature = holder.value();
                    list.add(new FeatureData(object2IntMap.computeIfAbsent(placedFeature, key -> mutableInt.getAndIncrement()), j, placedFeature));
                }
            }

            for (int j = 0; j < list.size(); j++) {
                Set<FeatureData> set = map.computeIfAbsent(list.get(j), key -> new TreeSet<>(comparator));
                if (j < list.size() - 1) {
                    set.add(list.get(j + 1));
                }
            }
        }

        Set<FeatureData> set2 = new TreeSet<>(comparator);
        Set<FeatureData> set3 = new TreeSet<>(comparator);
        List<FeatureData> list = Lists.newArrayList();

        for (FeatureData lv : map.keySet()) {
            if (!set3.isEmpty()) {
                throw new IllegalStateException("You somehow broke the universe; DFS bork (iteration finished with non-empty in-progress vertex set");
            }

            if (!set2.contains(lv) && Graph.depthFirstSearch(map, set2, set3, list::add, lv)) {
                if (!notRecursiveFlag) {
                    throw new IllegalStateException("Feature order cycle found");
                }

                List<T> list3 = new ArrayList<>(featureSetSources);

                int k;
                do {
                    k = list3.size();
                    ListIterator<T> listIterator = list3.listIterator();

                    while (listIterator.hasNext()) {
                        T object2 = listIterator.next();
                        listIterator.remove();

                        try {
                            buildFeaturesPerStep(list3, toFeatueSetFunction, false);
                        } catch (IllegalStateException illegalStateException) {
                            continue;
                        }

                        listIterator.add(object2);
                    }
                } while (k != list3.size());

                throw new IllegalStateException("Feature order cycle found, involved sources: " + list3);
            }
        }

        Collections.reverse(list);
        Builder<FeatureSorter.StepFeatureData> builder = ImmutableList.builder();

        for (int j = 0; j < i; j++) {
            int l = j;
            List<PlacedFeature> list4 = list.stream().filter(featureData -> featureData.step() == l).map(FeatureData::feature).collect(Collectors.toList());
            builder.add(new FeatureSorter.StepFeatureData(list4));
        }

        return builder.build();
    }

    public record StepFeatureData(List<PlacedFeature> features, ToIntFunction<PlacedFeature> indexMapping) {
        StepFeatureData(List<PlacedFeature> features) {
            this(features, Util.createIndexIdentityLookup(features));
        }
    }
}
