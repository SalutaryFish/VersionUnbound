package net.minecraft.util.profiling;

import java.util.function.Supplier;
import net.minecraft.util.profiling.metrics.MetricCategory;

public interface ProfilerFiller {
    String ROOT = "root";

    void startTick();

    void endTick();

    /**
     * Start section
     */
    void push(String var1);

    void push(Supplier<String> var1);

    void pop();

    void popPush(String var1);

    void popPush(Supplier<String> var1);

    void markForCharting(MetricCategory var1);

    default void incrementCounter(String entryId) {
        this.incrementCounter(entryId, 1);
    }

    void incrementCounter(String var1, int var2);

    default void incrementCounter(Supplier<String> entryIdSupplier) {
        this.incrementCounter(entryIdSupplier, 1);
    }

    void incrementCounter(Supplier<String> var1, int var2);

    static ProfilerFiller tee(ProfilerFiller first, ProfilerFiller second) {
        if (first == InactiveProfiler.INSTANCE) {
            return second;
        } else {
            return second == InactiveProfiler.INSTANCE ? first : new ProfilerFiller() {
                @Override
                public void startTick() {
                    first.startTick();
                    second.startTick();
                }

                @Override
                public void endTick() {
                    first.endTick();
                    second.endTick();
                }

                @Override
                public void push(String string) {
                    first.push(string);
                    second.push(string);
                }

                @Override
                public void push(Supplier<String> supplier) {
                    first.push(supplier);
                    second.push(supplier);
                }

                @Override
                public void markForCharting(MetricCategory metricCategory) {
                    first.markForCharting(metricCategory);
                    second.markForCharting(metricCategory);
                }

                @Override
                public void pop() {
                    first.pop();
                    second.pop();
                }

                @Override
                public void popPush(String string) {
                    first.popPush(string);
                    second.popPush(string);
                }

                @Override
                public void popPush(Supplier<String> supplier) {
                    first.popPush(supplier);
                    second.popPush(supplier);
                }

                @Override
                public void incrementCounter(String string, int i) {
                    first.incrementCounter(string, i);
                    second.incrementCounter(string, i);
                }

                @Override
                public void incrementCounter(Supplier<String> supplier, int i) {
                    first.incrementCounter(supplier, i);
                    second.incrementCounter(supplier, i);
                }
            };
        }
    }
}
