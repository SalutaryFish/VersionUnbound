package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * A composite loot pool entry container that expands all its children in order.
 * This container always succeeds.
 */
public class EntryGroup extends CompositeEntryBase {
    public static final MapCodec<EntryGroup> CODEC = createCodec(EntryGroup::new);

    EntryGroup(List<LootPoolEntryContainer> children, List<LootItemCondition> conditions) {
        super(children, conditions);
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.GROUP;
    }

    @Override
    protected ComposableEntryContainer compose(List<? extends ComposableEntryContainer> children) {
        return switch (children.size()) {
            case 0 -> ALWAYS_TRUE;
            case 1 -> (ComposableEntryContainer)children.get(0);
            case 2 -> {
                ComposableEntryContainer composableEntryContainer = children.get(0);
                ComposableEntryContainer composableEntryContainer2 = children.get(1);
                yield (lootContext, consumer) -> {
                    composableEntryContainer.expand(lootContext, consumer);
                    composableEntryContainer2.expand(lootContext, consumer);
                    return true;
                };
            }
            default -> (lootContext, consumer) -> {
                for (ComposableEntryContainer composableEntryContainerx : children) {
                    composableEntryContainerx.expand(lootContext, consumer);
                }

                return true;
            };
        };
    }

    public static EntryGroup.Builder list(LootPoolEntryContainer.Builder<?>... children) {
        return new EntryGroup.Builder(children);
    }

    public static class Builder extends LootPoolEntryContainer.Builder<EntryGroup.Builder> {
        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();

        public Builder(LootPoolEntryContainer.Builder<?>... children) {
            for (LootPoolEntryContainer.Builder<?> builder : children) {
                this.entries.add(builder.build());
            }
        }

        protected EntryGroup.Builder getThis() {
            return this;
        }

        @Override
        public EntryGroup.Builder append(LootPoolEntryContainer.Builder<?> childBuilder) {
            this.entries.add(childBuilder.build());
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return new EntryGroup(this.entries.build(), this.getConditions());
        }
    }
}
