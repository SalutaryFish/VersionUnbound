package net.minecraft.client.gui.screens.multiplayer;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerLinks;

public class ServerLinksScreen extends Screen {
    private static final int LINK_BUTTON_WIDTH = 310;
    private static final int DEFAULT_ITEM_HEIGHT = 25;
    private static final Component TITLE = Component.translatable("menu.server_links.title");
    private final Screen lastScreen;
    @Nullable
    private ServerLinksScreen.LinkList list;
    final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    final ServerLinks links;

    public ServerLinksScreen(Screen lastScreen, ServerLinks links) {
        super(TITLE);
        this.lastScreen = lastScreen;
        this.links = links;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);
        this.list = this.layout.addToContents(new ServerLinksScreen.LinkList(this.minecraft, this.width, this));
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).width(200).build());
        this.layout.visitWidgets(guiEventListener -> {
            AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
        });
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.list != null) {
            this.list.updateSize(this.width, this.layout);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    static class LinkList extends ContainerObjectSelectionList<ServerLinksScreen.LinkListEntry> {
        public LinkList(Minecraft minecraft, int width, ServerLinksScreen parent) {
            super(minecraft, width, parent.layout.getContentHeight(), parent.layout.getHeaderHeight(), 25);
            parent.links.entries().forEach(entry -> this.addEntry(new ServerLinksScreen.LinkListEntry(parent, entry)));
        }

        @Override
        public int getRowWidth() {
            return 310;
        }

        @Override
        public void updateSize(int width, HeaderAndFooterLayout layout) {
            super.updateSize(width, layout);
            int i = width / 2 - 155;
            this.children().forEach(linkListEntry -> linkListEntry.button.setX(i));
        }
    }

    static class LinkListEntry extends ContainerObjectSelectionList.Entry<ServerLinksScreen.LinkListEntry> {
        final AbstractWidget button;

        LinkListEntry(Screen screen, ServerLinks.Entry entry) {
            this.button = Button.builder(entry.displayName(), ConfirmLinkScreen.confirmLink(screen, entry.link(), false)).width(310).build();
        }

        @Override
        public void render(
            GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick
        ) {
            this.button.setY(top);
            this.button.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.button);
        }
    }
}
