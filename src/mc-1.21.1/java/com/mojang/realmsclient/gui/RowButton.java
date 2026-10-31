package com.mojang.realmsclient.gui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.realms.RealmsObjectSelectionList;

public abstract class RowButton {
    public final int width;
    public final int height;
    public final int xOffset;
    public final int yOffset;

    public RowButton(int width, int height, int xOffset, int yOffset) {
        this.width = width;
        this.height = height;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
    }

    public void drawForRowAt(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        int i = x + this.xOffset;
        int j = y + this.yOffset;
        boolean bl = mouseX >= i && mouseX <= i + this.width && mouseY >= j && mouseY <= j + this.height;
        this.draw(guiGraphics, i, j, bl);
    }

    protected abstract void draw(GuiGraphics var1, int var2, int var3, boolean var4);

    public int getRight() {
        return this.xOffset + this.width;
    }

    public int getBottom() {
        return this.yOffset + this.height;
    }

    public abstract void onClick(int var1);

    public static void drawButtonsInRow(
        GuiGraphics guiGraphics, List<RowButton> buttons, RealmsObjectSelectionList<?> pendingInvitations, int x, int y, int mouseX, int mouseY
    ) {
        for (RowButton rowButton : buttons) {
            if (pendingInvitations.getRowWidth() > rowButton.getRight()) {
                rowButton.drawForRowAt(guiGraphics, x, y, mouseX, mouseY);
            }
        }
    }

    public static void rowButtonMouseClicked(
        RealmsObjectSelectionList<?> list, ObjectSelectionList.Entry<?> entry, List<RowButton> buttons, int button, double mouseX, double mouseY
    ) {
        int i = list.children().indexOf(entry);
        if (i > -1) {
            list.selectItem(i);
            int j = list.getRowLeft();
            int k = list.getRowTop(i);
            int l = (int)(mouseX - j);
            int m = (int)(mouseY - k);

            for (RowButton rowButton : buttons) {
                if (l >= rowButton.xOffset && l <= rowButton.getRight() && m >= rowButton.yOffset && m <= rowButton.getBottom()) {
                    rowButton.onClick(i);
                }
            }
        }
    }
}
