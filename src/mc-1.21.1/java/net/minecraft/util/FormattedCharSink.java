package net.minecraft.util;

import net.minecraft.network.chat.Style;

@FunctionalInterface
public interface FormattedCharSink {
    /**
     * Accepts a single code point from a {@link net.minecraft.util.FormattedCharSequence}.
     * @return {@code true} to accept more characters, {@code false} to stop traversing the sequence.
     *
     * @param var1 Contains the relative position of the character in the current
     *             sub-sequence. If multiple formatted char sequences have been
     *             combined, this value will reset to {@code 0} after each sequence
     *             has been fully consumed.
     */
    boolean accept(int var1, Style var2, int var3);
}
