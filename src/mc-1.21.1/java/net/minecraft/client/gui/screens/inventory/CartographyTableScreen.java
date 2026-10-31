package net.minecraft.client.gui.screens.inventory;

import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class CartographyTableScreen extends AbstractContainerScreen<CartographyTableMenu> {
    private static final ResourceLocation ERROR_SPRITE = ResourceLocation.withDefaultNamespace("container/cartography_table/error");
    private static final ResourceLocation SCALED_MAP_SPRITE = ResourceLocation.withDefaultNamespace("container/cartography_table/scaled_map");
    private static final ResourceLocation DUPLICATED_MAP_SPRITE = ResourceLocation.withDefaultNamespace("container/cartography_table/duplicated_map");
    private static final ResourceLocation MAP_SPRITE = ResourceLocation.withDefaultNamespace("container/cartography_table/map");
    private static final ResourceLocation LOCKED_SPRITE = ResourceLocation.withDefaultNamespace("container/cartography_table/locked");
    private static final ResourceLocation BG_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/container/cartography_table.png");

    public CartographyTableScreen(CartographyTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.titleLabelY -= 2;
    }

    /**
     * Renders the graphical user interface (GUI) element.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param mouseX      the x-coordinate of the mouse cursor.
     * @param mouseY      the y-coordinate of the mouse cursor.
     * @param partialTick the partial tick time.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int i = this.leftPos;
        int j = this.topPos;
        guiGraphics.blit(BG_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight);
        ItemStack itemStack = this.menu.getSlot(1).getItem();
        boolean bl = itemStack.is(Items.MAP);
        boolean bl2 = itemStack.is(Items.PAPER);
        boolean bl3 = itemStack.is(Items.GLASS_PANE);
        ItemStack itemStack2 = this.menu.getSlot(0).getItem();
        MapId mapId = itemStack2.get(DataComponents.MAP_ID);
        boolean bl4 = false;
        MapItemSavedData mapItemSavedData;
        if (mapId != null) {
            mapItemSavedData = MapItem.getSavedData(mapId, this.minecraft.level);
            if (mapItemSavedData != null) {
                if (mapItemSavedData.locked) {
                    bl4 = true;
                    if (bl2 || bl3) {
                        guiGraphics.blitSprite(ERROR_SPRITE, i + 35, j + 31, 28, 21);
                    }
                }

                if (bl2 && mapItemSavedData.scale >= 4) {
                    bl4 = true;
                    guiGraphics.blitSprite(ERROR_SPRITE, i + 35, j + 31, 28, 21);
                }
            }
        } else {
            mapItemSavedData = null;
        }

        this.renderResultingMap(guiGraphics, mapId, mapItemSavedData, bl, bl2, bl3, bl4);
    }

    private void renderResultingMap(
        GuiGraphics guiGraphics,
        @Nullable MapId mapId,
        @Nullable MapItemSavedData mapData,
        boolean hasMap,
        boolean hasPaper,
        boolean hasGlassPane,
        boolean isMaxSize
    ) {
        int i = this.leftPos;
        int j = this.topPos;
        if (hasPaper && !isMaxSize) {
            guiGraphics.blitSprite(SCALED_MAP_SPRITE, i + 67, j + 13, 66, 66);
            this.renderMap(guiGraphics, mapId, mapData, i + 85, j + 31, 0.226F);
        } else if (hasMap) {
            guiGraphics.blitSprite(DUPLICATED_MAP_SPRITE, i + 67 + 16, j + 13, 50, 66);
            this.renderMap(guiGraphics, mapId, mapData, i + 86, j + 16, 0.34F);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 1.0F);
            guiGraphics.blitSprite(DUPLICATED_MAP_SPRITE, i + 67, j + 13 + 16, 50, 66);
            this.renderMap(guiGraphics, mapId, mapData, i + 70, j + 32, 0.34F);
            guiGraphics.pose().popPose();
        } else if (hasGlassPane) {
            guiGraphics.blitSprite(MAP_SPRITE, i + 67, j + 13, 66, 66);
            this.renderMap(guiGraphics, mapId, mapData, i + 71, j + 17, 0.45F);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 1.0F);
            guiGraphics.blitSprite(LOCKED_SPRITE, i + 118, j + 60, 10, 14);
            guiGraphics.pose().popPose();
        } else {
            guiGraphics.blitSprite(MAP_SPRITE, i + 67, j + 13, 66, 66);
            this.renderMap(guiGraphics, mapId, mapData, i + 71, j + 17, 0.45F);
        }
    }

    private void renderMap(GuiGraphics guiGraphics, @Nullable MapId mapId, @Nullable MapItemSavedData mapData, int x, int y, float scale) {
        if (mapId != null && mapData != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 1.0F);
            guiGraphics.pose().scale(scale, scale, 1.0F);
            this.minecraft.gameRenderer.getMapRenderer().render(guiGraphics.pose(), guiGraphics.bufferSource(), mapId, mapData, true, 15728880);
            guiGraphics.flush();
            guiGraphics.pose().popPose();
        }
    }
}
