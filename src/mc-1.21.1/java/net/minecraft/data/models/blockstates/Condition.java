package net.minecraft.data.models.blockstates;

import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public interface Condition extends Supplier<JsonElement> {
    void validate(StateDefinition<?, ?> var1);

    static Condition.TerminalCondition condition() {
        return new Condition.TerminalCondition();
    }

    static Condition and(Condition... conditions) {
        return new Condition.CompositeCondition(Condition.Operation.AND, Arrays.asList(conditions));
    }

    static Condition or(Condition... conditions) {
        return new Condition.CompositeCondition(Condition.Operation.OR, Arrays.asList(conditions));
    }

    class CompositeCondition implements Condition {
        private final Condition.Operation operation;
        private final List<Condition> subconditions;

        CompositeCondition(Condition.Operation operation, List<Condition> subconditions) {
            this.operation = operation;
            this.subconditions = subconditions;
        }

        @Override
        public void validate(StateDefinition<?, ?> stateDefinition) {
            this.subconditions.forEach(condition -> condition.validate(stateDefinition));
        }

        public JsonElement get() {
            JsonArray jsonArray = new JsonArray();
            this.subconditions.stream().map(Supplier::get).forEach(jsonArray::add);
            JsonObject jsonObject = new JsonObject();
            jsonObject.add(this.operation.id, jsonArray);
            return jsonObject;
        }
    }

    enum Operation {
        AND("AND"),
        OR("OR");

        final String id;

        Operation(final String id) {
            this.id = id;
        }
    }

    class TerminalCondition implements Condition {
        private final Map<Property<?>, String> terms = Maps.newHashMap();

        private static <T extends Comparable<T>> String joinValues(Property<T> property, Stream<T> valueStream) {
            return valueStream.<CharSequence>map(property::getName).collect(Collectors.joining("|"));
        }

        private static <T extends Comparable<T>> String getTerm(Property<T> property, T firstValue, T[] additionalValues) {
            return joinValues(property, Stream.concat(Stream.of(firstValue), Stream.of(additionalValues)));
        }

        private <T extends Comparable<T>> void putValue(Property<T> property, String value) {
            String string = this.terms.put(property, value);
            if (string != null) {
                throw new IllegalStateException("Tried to replace " + property + " value from " + string + " to " + value);
            }
        }

        public final <T extends Comparable<T>> Condition.TerminalCondition term(Property<T> property, T value) {
            this.putValue(property, property.getName(value));
            return this;
        }

        @SafeVarargs
        public final <T extends Comparable<T>> Condition.TerminalCondition term(Property<T> property, T firstValue, T... additionalValues) {
            this.putValue(property, getTerm(property, firstValue, additionalValues));
            return this;
        }

        public final <T extends Comparable<T>> Condition.TerminalCondition negatedTerm(Property<T> property, T value) {
            this.putValue(property, "!" + property.getName(value));
            return this;
        }

        @SafeVarargs
        public final <T extends Comparable<T>> Condition.TerminalCondition negatedTerm(Property<T> property, T firstValue, T... additionalValues) {
            this.putValue(property, "!" + getTerm(property, firstValue, additionalValues));
            return this;
        }

        public JsonElement get() {
            JsonObject jsonObject = new JsonObject();
            this.terms.forEach((property, string) -> jsonObject.addProperty(property.getName(), string));
            return jsonObject;
        }

        @Override
        public void validate(StateDefinition<?, ?> stateDefinition) {
            List<Property<?>> list = this.terms
                .keySet()
                .stream()
                .filter(property -> stateDefinition.getProperty(property.getName()) != property)
                .collect(Collectors.toList());
            if (!list.isEmpty()) {
                throw new IllegalStateException("Properties " + list + " are missing from " + stateDefinition);
            }
        }
    }
}
