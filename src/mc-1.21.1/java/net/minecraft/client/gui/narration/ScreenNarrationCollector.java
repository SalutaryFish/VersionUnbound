package net.minecraft.client.gui.narration;

import com.google.common.collect.Maps;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;

public class ScreenNarrationCollector {
    int generation;
    final Map<ScreenNarrationCollector.EntryKey, ScreenNarrationCollector.NarrationEntry> entries = Maps.newTreeMap(
        Comparator.<ScreenNarrationCollector.EntryKey, NarratedElementType>comparing(entryKey -> entryKey.type).thenComparing(entryKey -> entryKey.depth)
    );

    public void update(Consumer<NarrationElementOutput> updater) {
        this.generation++;
        updater.accept(new ScreenNarrationCollector.Output(0));
    }

    public String collectNarrationText(boolean collectAll) {
        final StringBuilder stringBuilder = new StringBuilder();
        Consumer<String> consumer = new Consumer<String>() {
            private boolean firstEntry = true;

            public void accept(String str) {
                if (!this.firstEntry) {
                    stringBuilder.append(". ");
                }

                this.firstEntry = false;
                stringBuilder.append(str);
            }
        };
        this.entries.forEach((key, entry) -> {
            if (entry.generation == this.generation && (collectAll || !entry.alreadyNarrated)) {
                entry.contents.getText(consumer);
                entry.alreadyNarrated = true;
            }
        });
        return stringBuilder.toString();
    }

    static class EntryKey {
        final NarratedElementType type;
        final int depth;

        EntryKey(NarratedElementType type, int depth) {
            this.type = type;
            this.depth = depth;
        }
    }

    static class NarrationEntry {
        NarrationThunk<?> contents = NarrationThunk.EMPTY;
        int generation = -1;
        boolean alreadyNarrated;

        public ScreenNarrationCollector.NarrationEntry update(int generation, NarrationThunk<?> contents) {
            if (!this.contents.equals(contents)) {
                this.contents = contents;
                this.alreadyNarrated = false;
            } else if (this.generation + 1 != generation) {
                this.alreadyNarrated = false;
            }

            this.generation = generation;
            return this;
        }
    }

    class Output implements NarrationElementOutput {
        private final int depth;

        Output(final int depth) {
            this.depth = depth;
        }

        @Override
        public void add(NarratedElementType type, NarrationThunk<?> contents) {
            ScreenNarrationCollector.this.entries
                .computeIfAbsent(new ScreenNarrationCollector.EntryKey(type, this.depth), entryKey -> new ScreenNarrationCollector.NarrationEntry())
                .update(ScreenNarrationCollector.this.generation, contents);
        }

        @Override
        public NarrationElementOutput nest() {
            return ScreenNarrationCollector.this.new Output(this.depth + 1);
        }
    }
}
