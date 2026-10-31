package net.minecraft.world.level.storage.loot;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

public interface ContainerComponentManipulator<T> {
    DataComponentType<T> type();

    T empty();

    T setContents(T var1, Stream<ItemStack> var2);

    Stream<ItemStack> getContents(T var1);

    default void setContents(ItemStack stack, T contents, Stream<ItemStack> items) {
        T object = stack.getOrDefault(this.type(), contents);
        T object2 = this.setContents(object, items);
        stack.set(this.type(), object2);
    }

    default void setContents(ItemStack stack, Stream<ItemStack> items) {
        this.setContents(stack, this.empty(), items);
    }

    default void modifyItems(ItemStack stack, UnaryOperator<ItemStack> modifier) {
        T object = stack.get(this.type());
        if (object != null) {
            UnaryOperator<ItemStack> unaryOperator = itemStack -> {
                if (itemStack.isEmpty()) {
                    return itemStack;
                }

                ItemStack itemStack2 = modifier.apply(itemStack);
                itemStack2.limitSize(itemStack2.getMaxStackSize());
                return itemStack2;
            };
            this.setContents(stack, this.getContents(object).map(unaryOperator));
        }
    }
}
