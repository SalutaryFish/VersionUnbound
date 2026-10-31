package net.minecraft.world;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public interface WorldlyContainer extends Container {
    int[] getSlotsForFace(Direction var1);

    /**
     * Returns {@code true} if automation can insert the given item in the given slot from the given side.
     */
    boolean canPlaceItemThroughFace(int var1, ItemStack var2, @Nullable Direction var3);

    /**
     * Returns {@code true} if automation can extract the given item in the given slot from the given side.
     */
    boolean canTakeItemThroughFace(int var1, ItemStack var2, Direction var3);
}
