package net.minecraft.client.gui.layouts;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;

public interface Layout extends LayoutElement {
    void visitChildren(Consumer<LayoutElement> var1);

    @Override
    default void visitWidgets(Consumer<AbstractWidget> consumer) {
        this.visitChildren(layoutElement -> layoutElement.visitWidgets(consumer));
    }

    default void arrangeElements() {
        this.visitChildren(layoutElement -> {
            if (layoutElement instanceof Layout layout) {
                layout.arrangeElements();
            }
        });
    }
}
