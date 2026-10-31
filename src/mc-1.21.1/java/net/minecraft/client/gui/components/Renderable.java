package net.minecraft.client.gui.components;

import net.minecraft.client.gui.GuiGraphics;

public interface Renderable {
    /**
     * Renders the graphical user interface (GUI) element.
     *
     * @param var1 the GuiGraphics object used for rendering.
     * @param var2 the x-coordinate of the mouse cursor.
     * @param var3 the y-coordinate of the mouse cursor.
     * @param var4 the partial tick time.
     */
    void render(GuiGraphics var1, int var2, int var3, float var4);
}
