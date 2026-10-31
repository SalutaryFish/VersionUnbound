package net.minecraft.client.gui.narration;

import net.minecraft.client.gui.components.TabOrderedElement;

/**
 * An interface for GUI elements that can provide narration information.
 */
public interface NarratableEntry extends TabOrderedElement, NarrationSupplier {
    NarratableEntry.NarrationPriority narrationPriority();

    default boolean isActive() {
        return true;
    }

    /**
     * The narration priority levels.
     */
    enum NarrationPriority {
        /**
         * No narration priority.
         */
        NONE,
        /**
         * Narration priority when the element is being hovered.
         */
        HOVERED,
        /**
         * Narration priority when the element is focused.
         */
        FOCUSED;

        public boolean isTerminal() {
            return this == FOCUSED;
        }
    }
}
