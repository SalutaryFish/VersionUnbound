package net.minecraft.client.gui.screens.advancements;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;

public class AdvancementsScreen extends Screen implements ClientAdvancements.Listener {
    private static final ResourceLocation WINDOW_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");
    public static final int WINDOW_WIDTH = 252;
    public static final int WINDOW_HEIGHT = 140;
    private static final int WINDOW_INSIDE_X = 9;
    private static final int WINDOW_INSIDE_Y = 18;
    public static final int WINDOW_INSIDE_WIDTH = 234;
    public static final int WINDOW_INSIDE_HEIGHT = 113;
    private static final int WINDOW_TITLE_X = 8;
    private static final int WINDOW_TITLE_Y = 6;
    public static final int BACKGROUND_TILE_WIDTH = 16;
    public static final int BACKGROUND_TILE_HEIGHT = 16;
    public static final int BACKGROUND_TILE_COUNT_X = 14;
    public static final int BACKGROUND_TILE_COUNT_Y = 7;
    private static final double SCROLL_SPEED = 16.0;
    private static final Component VERY_SAD_LABEL = Component.translatable("advancements.sad_label");
    private static final Component NO_ADVANCEMENTS_LABEL = Component.translatable("advancements.empty");
    private static final Component TITLE = Component.translatable("gui.advancements");
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    @Nullable
    private final Screen lastScreen;
    private final ClientAdvancements advancements;
    private final Map<AdvancementHolder, AdvancementTab> tabs = Maps.newLinkedHashMap();
    @Nullable
    private AdvancementTab selectedTab;
    private boolean isScrolling;

    public AdvancementsScreen(ClientAdvancements advancements) {
        this(advancements, null);
    }

    public AdvancementsScreen(ClientAdvancements advancements, @Nullable Screen lastScreen) {
        super(TITLE);
        this.advancements = advancements;
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(TITLE, this.font);
        this.tabs.clear();
        this.selectedTab = null;
        this.advancements.setListener(this);
        if (this.selectedTab == null && !this.tabs.isEmpty()) {
            AdvancementTab advancementTab = this.tabs.values().iterator().next();
            this.advancements.setSelectedTab(advancementTab.getRootNode().holder(), true);
        } else {
            this.advancements.setSelectedTab(this.selectedTab == null ? null : this.selectedTab.getRootNode().holder(), true);
        }

        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(200).build());
        this.layout.visitWidgets(guiEventListener -> {
            AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
        });
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    @Override
    public void removed() {
        this.advancements.setListener(null);
        ClientPacketListener clientPacketListener = this.minecraft.getConnection();
        if (clientPacketListener != null) {
            clientPacketListener.send(ServerboundSeenAdvancementsPacket.closedScreen());
        }
    }

    /**
     * Called when a mouse button is clicked within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that was clicked.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int i = (this.width - 252) / 2;
            int j = (this.height - 140) / 2;

            for (AdvancementTab advancementTab : this.tabs.values()) {
                if (advancementTab.isMouseOver(i, j, mouseX, mouseY)) {
                    this.advancements.setSelectedTab(advancementTab.getRootNode().holder(), true);
                    break;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Called when a keyboard key is pressed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param keyCode   the key code of the pressed key.
     * @param scanCode  the scan code of the pressed key.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft.options.keyAdvancements.matches(keyCode, scanCode)) {
            this.minecraft.setScreen(null);
            this.minecraft.mouseHandler.grabMouse();
            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
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
        int i = (this.width - 252) / 2;
        int j = (this.height - 140) / 2;
        this.renderInside(guiGraphics, mouseX, mouseY, i, j);
        this.renderWindow(guiGraphics, i, j);
        this.renderTooltips(guiGraphics, mouseX, mouseY, i, j);
    }

    /**
     * Called when the mouse is dragged within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that is being dragged.
     * @param dragX  the X distance of the drag.
     * @param dragY  the Y distance of the drag.
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) {
            this.isScrolling = false;
            return false;
        }

        if (!this.isScrolling) {
            this.isScrolling = true;
        } else if (this.selectedTab != null) {
            this.selectedTab.scroll(dragX, dragY);
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.selectedTab != null) {
            this.selectedTab.scroll(scrollX * 16.0, scrollY * 16.0);
            return true;
        } else {
            return false;
        }
    }

    private void renderInside(GuiGraphics guiGraphics, int mouseX, int mouseY, int offsetX, int offsetY) {
        AdvancementTab advancementTab = this.selectedTab;
        if (advancementTab == null) {
            guiGraphics.fill(offsetX + 9, offsetY + 18, offsetX + 9 + 234, offsetY + 18 + 113, -16777216);
            int i = offsetX + 9 + 117;
            guiGraphics.drawCenteredString(this.font, NO_ADVANCEMENTS_LABEL, i, offsetY + 18 + 56 - 9 / 2, -1);
            guiGraphics.drawCenteredString(this.font, VERY_SAD_LABEL, i, offsetY + 18 + 113 - 9, -1);
        } else {
            advancementTab.drawContents(guiGraphics, offsetX + 9, offsetY + 18);
        }
    }

    public void renderWindow(GuiGraphics guiGraphics, int offsetX, int offsetY) {
        RenderSystem.enableBlend();
        guiGraphics.blit(WINDOW_LOCATION, offsetX, offsetY, 0, 0, 252, 140);
        if (this.tabs.size() > 1) {
            for (AdvancementTab advancementTab : this.tabs.values()) {
                advancementTab.drawTab(guiGraphics, offsetX, offsetY, advancementTab == this.selectedTab);
            }

            for (AdvancementTab advancementTab : this.tabs.values()) {
                advancementTab.drawIcon(guiGraphics, offsetX, offsetY);
            }
        }

        guiGraphics.drawString(this.font, this.selectedTab != null ? this.selectedTab.getTitle() : TITLE, offsetX + 8, offsetY + 6, 4210752, false);
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY, int offsetX, int offsetY) {
        if (this.selectedTab != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(offsetX + 9, offsetY + 18, 400.0F);
            RenderSystem.enableDepthTest();
            this.selectedTab.drawTooltips(guiGraphics, mouseX - offsetX - 9, mouseY - offsetY - 18, offsetX, offsetY);
            RenderSystem.disableDepthTest();
            guiGraphics.pose().popPose();
        }

        if (this.tabs.size() > 1) {
            for (AdvancementTab advancementTab : this.tabs.values()) {
                if (advancementTab.isMouseOver(offsetX, offsetY, mouseX, mouseY)) {
                    guiGraphics.renderTooltip(this.font, advancementTab.getTitle(), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public void onAddAdvancementRoot(AdvancementNode advancement) {
        AdvancementTab advancementTab = AdvancementTab.create(this.minecraft, this, this.tabs.size(), advancement);
        if (advancementTab != null) {
            this.tabs.put(advancement.holder(), advancementTab);
        }
    }

    @Override
    public void onRemoveAdvancementRoot(AdvancementNode advancement) {
    }

    @Override
    public void onAddAdvancementTask(AdvancementNode advancement) {
        AdvancementTab advancementTab = this.getTab(advancement);
        if (advancementTab != null) {
            advancementTab.addAdvancement(advancement);
        }
    }

    @Override
    public void onRemoveAdvancementTask(AdvancementNode advancement) {
    }

    @Override
    public void onUpdateAdvancementProgress(AdvancementNode advancement, AdvancementProgress advancementProgress) {
        AdvancementWidget advancementWidget = this.getAdvancementWidget(advancement);
        if (advancementWidget != null) {
            advancementWidget.setProgress(advancementProgress);
        }
    }

    @Override
    public void onSelectedTabChanged(@Nullable AdvancementHolder advancement) {
        this.selectedTab = this.tabs.get(advancement);
    }

    @Override
    public void onAdvancementsCleared() {
        this.tabs.clear();
        this.selectedTab = null;
    }

    @Nullable
    public AdvancementWidget getAdvancementWidget(AdvancementNode advancement) {
        AdvancementTab advancementTab = this.getTab(advancement);
        return advancementTab == null ? null : advancementTab.getWidget(advancement.holder());
    }

    @Nullable
    private AdvancementTab getTab(AdvancementNode advancement) {
        AdvancementNode advancementNode = advancement.root();
        return this.tabs.get(advancementNode.holder());
    }
}
