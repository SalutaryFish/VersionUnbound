package net.minecraft.client.gui.screens.advancements;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

enum AdvancementTabType {
    ABOVE(
        new AdvancementTabType.Sprites(
            ResourceLocation.withDefaultNamespace("advancements/tab_above_left_selected"),
            ResourceLocation.withDefaultNamespace("advancements/tab_above_middle_selected"),
            ResourceLocation.withDefaultNamespace("advancements/tab_above_right_selected")
        ),
        new AdvancementTabType.Sprites(
            ResourceLocation.withDefaultNamespace("advancements/tab_above_left"),
            ResourceLocation.withDefaultNamespace("advancements/tab_above_middle"),
            ResourceLocation.withDefaultNamespace("advancements/tab_above_right")
        ),
        28,
        32,
        8
    ),
    BELOW(
        new AdvancementTabType.Sprites(
            ResourceLocation.withDefaultNamespace("advancements/tab_below_left_selected"),
            ResourceLocation.withDefaultNamespace("advancements/tab_below_middle_selected"),
            ResourceLocation.withDefaultNamespace("advancements/tab_below_right_selected")
        ),
        new AdvancementTabType.Sprites(
            ResourceLocation.withDefaultNamespace("advancements/tab_below_left"),
            ResourceLocation.withDefaultNamespace("advancements/tab_below_middle"),
            ResourceLocation.withDefaultNamespace("advancements/tab_below_right")
        ),
        28,
        32,
        8
    ),
    LEFT(
        new AdvancementTabType.Sprites(
            ResourceLocation.withDefaultNamespace("advancements/tab_left_top_selected"),
            ResourceLocation.withDefaultNamespace("advancements/tab_left_middle_selected"),
            ResourceLocation.withDefaultNamespace("advancements/tab_left_bottom_selected")
        ),
        new AdvancementTabType.Sprites(
            ResourceLocation.withDefaultNamespace("advancements/tab_left_top"),
            ResourceLocation.withDefaultNamespace("advancements/tab_left_middle"),
            ResourceLocation.withDefaultNamespace("advancements/tab_left_bottom")
        ),
        32,
        28,
        5
    ),
    RIGHT(
        new AdvancementTabType.Sprites(
            ResourceLocation.withDefaultNamespace("advancements/tab_right_top_selected"),
            ResourceLocation.withDefaultNamespace("advancements/tab_right_middle_selected"),
            ResourceLocation.withDefaultNamespace("advancements/tab_right_bottom_selected")
        ),
        new AdvancementTabType.Sprites(
            ResourceLocation.withDefaultNamespace("advancements/tab_right_top"),
            ResourceLocation.withDefaultNamespace("advancements/tab_right_middle"),
            ResourceLocation.withDefaultNamespace("advancements/tab_right_bottom")
        ),
        32,
        28,
        5
    );

    private final AdvancementTabType.Sprites selectedSprites;
    private final AdvancementTabType.Sprites unselectedSprites;
    private final int width;
    private final int height;
    private final int max;

    AdvancementTabType(
        final AdvancementTabType.Sprites selectedSprites, final AdvancementTabType.Sprites unselectedSprites, final int width, final int height, final int max
    ) {
        this.selectedSprites = selectedSprites;
        this.unselectedSprites = unselectedSprites;
        this.width = width;
        this.height = height;
        this.max = max;
    }

    public int getMax() {
        return this.max;
    }

    public void draw(GuiGraphics guiGraphics, int offsetX, int offsetY, boolean isSelected, int index) {
        AdvancementTabType.Sprites sprites = isSelected ? this.selectedSprites : this.unselectedSprites;
        ResourceLocation resourceLocation;
        if (index == 0) {
            resourceLocation = sprites.first();
        } else if (index == this.max - 1) {
            resourceLocation = sprites.last();
        } else {
            resourceLocation = sprites.middle();
        }

        guiGraphics.blitSprite(resourceLocation, offsetX + this.getX(index), offsetY + this.getY(index), this.width, this.height);
    }

    public void drawIcon(GuiGraphics guiGraphics, int offsetX, int offsetY, int index, ItemStack stack) {
        int i = offsetX + this.getX(index);
        int j = offsetY + this.getY(index);
        switch (this) {
            case ABOVE:
                i += 6;
                j += 9;
                break;
            case BELOW:
                i += 6;
                j += 6;
                break;
            case LEFT:
                i += 10;
                j += 5;
                break;
            case RIGHT:
                i += 6;
                j += 5;
        }

        guiGraphics.renderFakeItem(stack, i, j);
    }

    public int getX(int index) {
        switch (this) {
            case ABOVE:
                return (this.width + 4) * index;
            case BELOW:
                return (this.width + 4) * index;
            case LEFT:
                return -this.width + 4;
            case RIGHT:
                return 248;
            default:
                throw new UnsupportedOperationException("Don't know what this tab type is!" + this);
        }
    }

    public int getY(int index) {
        switch (this) {
            case ABOVE:
                return -this.height + 4;
            case BELOW:
                return 136;
            case LEFT:
                return this.height * index;
            case RIGHT:
                return this.height * index;
            default:
                throw new UnsupportedOperationException("Don't know what this tab type is!" + this);
        }
    }

    public boolean isMouseOver(int offsetX, int offsetY, int index, double mouseX, double mouseY) {
        int i = offsetX + this.getX(index);
        int j = offsetY + this.getY(index);
        return mouseX > i && mouseX < i + this.width && mouseY > j && mouseY < j + this.height;
    }

    record Sprites(ResourceLocation first, ResourceLocation middle, ResourceLocation last) {
    }
}
