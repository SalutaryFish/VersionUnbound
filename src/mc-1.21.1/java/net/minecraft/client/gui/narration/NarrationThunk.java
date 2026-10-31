package net.minecraft.client.gui.narration;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;

public class NarrationThunk<T> {
    private final T contents;
    private final BiConsumer<Consumer<String>, T> converter;
    public static final NarrationThunk<?> EMPTY = new NarrationThunk<>(Unit.INSTANCE, (consumer, unit) -> {});

    private NarrationThunk(T contents, BiConsumer<Consumer<String>, T> converter) {
        this.contents = contents;
        this.converter = converter;
    }

    public static NarrationThunk<?> from(String text) {
        return new NarrationThunk<>(text, Consumer::accept);
    }

    public static NarrationThunk<?> from(Component component) {
        return new NarrationThunk<>(component, (consumer, consumedComponent) -> consumer.accept(consumedComponent.getString()));
    }

    public static NarrationThunk<?> from(List<Component> components) {
        return new NarrationThunk<>(components, (consumer, consumedComponent) -> components.stream().map(Component::getString).forEach(consumer));
    }

    public void getText(Consumer<String> consumer) {
        this.converter.accept(consumer, this.contents);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return !(other instanceof NarrationThunk<?> narrationThunk)
                ? false
                : narrationThunk.converter == this.converter && narrationThunk.contents.equals(this.contents);
        }
    }

    @Override
    public int hashCode() {
        int i = this.contents.hashCode();
        return 31 * i + this.converter.hashCode();
    }
}
