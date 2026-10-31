package net.minecraft.client.renderer.block.model.multipart;

import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public class KeyValueCondition implements Condition {
    private static final Splitter PIPE_SPLITTER = Splitter.on('|').omitEmptyStrings();
    private final String key;
    private final String value;

    public KeyValueCondition(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public Predicate<BlockState> getPredicate(StateDefinition<Block, BlockState> definition) {
        Property<?> property = definition.getProperty(this.key);
        if (property == null) {
            throw new RuntimeException(String.format(Locale.ROOT, "Unknown property '%s' on '%s'", this.key, definition.getOwner()));
        }

        String string = this.value;
        boolean bl = !string.isEmpty() && string.charAt(0) == '!';
        if (bl) {
            string = string.substring(1);
        }

        List<String> list = PIPE_SPLITTER.splitToList(string);
        if (list.isEmpty()) {
            throw new RuntimeException(String.format(Locale.ROOT, "Empty value '%s' for property '%s' on '%s'", this.value, this.key, definition.getOwner()));
        }

        Predicate<BlockState> predicate;
        if (list.size() == 1) {
            predicate = this.getBlockStatePredicate(definition, property, string);
        } else {
            List<Predicate<BlockState>> list2 = list.stream()
                .map(value -> this.getBlockStatePredicate(definition, property, value))
                .collect(Collectors.toList());
            predicate = state -> list2.stream().anyMatch(predicatex -> predicatex.test(state));
        }

        return bl ? predicate.negate() : predicate;
    }

    private Predicate<BlockState> getBlockStatePredicate(StateDefinition<Block, BlockState> definition, Property<?> property, String value) {
        Optional<?> optional = property.getValue(value);
        if (optional.isEmpty()) {
            throw new RuntimeException(
                String.format(Locale.ROOT, "Unknown value '%s' for property '%s' on '%s' in '%s'", value, this.key, definition.getOwner(), this.value)
            );
        } else {
            return state -> state.getValue(property).equals(optional.get());
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("key", this.key).add("value", this.value).toString();
    }
}
