package com.mojang.realmsclient.gui.screens;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.Ops;
import com.mojang.realmsclient.dto.PlayerInfo;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.util.RealmsUtil;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class RealmsPlayerScreen extends RealmsScreen {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TITLE = Component.translatable("mco.configure.world.players.title");
    static final Component QUESTION_TITLE = Component.translatable("mco.question");
    private static final int PADDING = 8;
    final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final RealmsConfigureWorldScreen lastScreen;
    final RealmsServer serverData;
    @Nullable
    private RealmsPlayerScreen.InvitedObjectSelectionList invitedList;
    boolean stateChanged;

    public RealmsPlayerScreen(RealmsConfigureWorldScreen lastScreen, RealmsServer serverData) {
        super(TITLE);
        this.lastScreen = lastScreen;
        this.serverData = serverData;
    }

    @Override
    public void init() {
        this.layout.addTitleHeader(TITLE, this.font);
        this.invitedList = this.layout.addToContents(new RealmsPlayerScreen.InvitedObjectSelectionList());
        this.repopulateInvitedList();
        LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearLayout.addChild(
            Button.builder(
                    Component.translatable("mco.configure.world.buttons.invite"),
                    button -> this.minecraft.setScreen(new RealmsInviteScreen(this.lastScreen, this, this.serverData))
                )
                .build()
        );
        linearLayout.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).build());
        this.layout.visitWidgets(guiEventListener -> {
            AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
        });
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.invitedList != null) {
            this.invitedList.updateSize(this.width, this.layout);
        }
    }

    void repopulateInvitedList() {
        if (this.invitedList != null) {
            this.invitedList.children().clear();

            for (PlayerInfo playerInfo : this.serverData.players) {
                this.invitedList.children().add(new RealmsPlayerScreen.Entry(playerInfo));
            }
        }
    }

    @Override
    public void onClose() {
        this.backButtonClicked();
    }

    private void backButtonClicked() {
        if (this.stateChanged) {
            this.minecraft.setScreen(this.lastScreen.getNewScreen());
        } else {
            this.minecraft.setScreen(this.lastScreen);
        }
    }

    class Entry extends ContainerObjectSelectionList.Entry<RealmsPlayerScreen.Entry> {
        private static final Component NORMAL_USER_TEXT = Component.translatable("mco.configure.world.invites.normal.tooltip");
        private static final Component OP_TEXT = Component.translatable("mco.configure.world.invites.ops.tooltip");
        private static final Component REMOVE_TEXT = Component.translatable("mco.configure.world.invites.remove.tooltip");
        private static final ResourceLocation MAKE_OP_SPRITE = ResourceLocation.withDefaultNamespace("player_list/make_operator");
        private static final ResourceLocation REMOVE_OP_SPRITE = ResourceLocation.withDefaultNamespace("player_list/remove_operator");
        private static final ResourceLocation REMOVE_PLAYER_SPRITE = ResourceLocation.withDefaultNamespace("player_list/remove_player");
        private static final int ICON_WIDTH = 8;
        private static final int ICON_HEIGHT = 7;
        private final PlayerInfo playerInfo;
        private final Button removeButton;
        private final Button makeOpButton;
        private final Button removeOpButton;

        public Entry(final PlayerInfo playerInfo) {
            this.playerInfo = playerInfo;
            int i = RealmsPlayerScreen.this.serverData.players.indexOf(this.playerInfo);
            this.makeOpButton = SpriteIconButton.builder(NORMAL_USER_TEXT, button -> this.op(i), false)
                .sprite(MAKE_OP_SPRITE, 8, 7)
                .width(16 + RealmsPlayerScreen.this.font.width(NORMAL_USER_TEXT))
                .narration(
                    supplier -> CommonComponents.joinForNarration(
                        Component.translatable("mco.invited.player.narration", playerInfo.getName()),
                        supplier.get(),
                        Component.translatable("narration.cycle_button.usage.focused", OP_TEXT)
                    )
                )
                .build();
            this.removeOpButton = SpriteIconButton.builder(OP_TEXT, button -> this.deop(i), false)
                .sprite(REMOVE_OP_SPRITE, 8, 7)
                .width(16 + RealmsPlayerScreen.this.font.width(OP_TEXT))
                .narration(
                    supplier -> CommonComponents.joinForNarration(
                        Component.translatable("mco.invited.player.narration", playerInfo.getName()),
                        supplier.get(),
                        Component.translatable("narration.cycle_button.usage.focused", NORMAL_USER_TEXT)
                    )
                )
                .build();
            this.removeButton = SpriteIconButton.builder(REMOVE_TEXT, button -> this.uninvite(i), false)
                .sprite(REMOVE_PLAYER_SPRITE, 8, 7)
                .width(16 + RealmsPlayerScreen.this.font.width(REMOVE_TEXT))
                .narration(
                    supplier -> CommonComponents.joinForNarration(Component.translatable("mco.invited.player.narration", playerInfo.getName()), supplier.get())
                )
                .build();
            this.updateOpButtons();
        }

        private void op(int index) {
            RealmsClient realmsClient = RealmsClient.create();
            UUID uUID = RealmsPlayerScreen.this.serverData.players.get(index).getUuid();

            try {
                this.updateOps(realmsClient.op(RealmsPlayerScreen.this.serverData.id, uUID));
            } catch (RealmsServiceException realmsServiceException) {
                RealmsPlayerScreen.LOGGER.error("Couldn't op the user", realmsServiceException);
            }

            this.updateOpButtons();
        }

        private void deop(int index) {
            RealmsClient realmsClient = RealmsClient.create();
            UUID uUID = RealmsPlayerScreen.this.serverData.players.get(index).getUuid();

            try {
                this.updateOps(realmsClient.deop(RealmsPlayerScreen.this.serverData.id, uUID));
            } catch (RealmsServiceException realmsServiceException) {
                RealmsPlayerScreen.LOGGER.error("Couldn't deop the user", realmsServiceException);
            }

            this.updateOpButtons();
        }

        private void uninvite(int index) {
            if (index >= 0 && index < RealmsPlayerScreen.this.serverData.players.size()) {
                PlayerInfo playerInfo = RealmsPlayerScreen.this.serverData.players.get(index);
                RealmsConfirmScreen realmsConfirmScreen = new RealmsConfirmScreen(bl -> {
                    if (bl) {
                        RealmsClient realmsClient = RealmsClient.create();

                        try {
                            realmsClient.uninvite(RealmsPlayerScreen.this.serverData.id, playerInfo.getUuid());
                        } catch (RealmsServiceException realmsServiceException) {
                            RealmsPlayerScreen.LOGGER.error("Couldn't uninvite user", realmsServiceException);
                        }

                        RealmsPlayerScreen.this.serverData.players.remove(index);
                        RealmsPlayerScreen.this.repopulateInvitedList();
                    }

                    RealmsPlayerScreen.this.stateChanged = true;
                    RealmsPlayerScreen.this.minecraft.setScreen(RealmsPlayerScreen.this);
                }, RealmsPlayerScreen.QUESTION_TITLE, Component.translatable("mco.configure.world.uninvite.player", playerInfo.getName()));
                RealmsPlayerScreen.this.minecraft.setScreen(realmsConfirmScreen);
            }
        }

        private void updateOps(Ops ops) {
            for (PlayerInfo playerInfo : RealmsPlayerScreen.this.serverData.players) {
                playerInfo.setOperator(ops.ops.contains(playerInfo.getName()));
            }
        }

        private void updateOpButtons() {
            this.makeOpButton.visible = !this.playerInfo.isOperator();
            this.removeOpButton.visible = !this.makeOpButton.visible;
        }

        private Button activeOpButton() {
            return this.makeOpButton.visible ? this.makeOpButton : this.removeOpButton;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.activeOpButton(), this.removeButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(this.activeOpButton(), this.removeButton);
        }

        @Override
        public void render(
            GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick
        ) {
            int i;
            if (!this.playerInfo.getAccepted()) {
                i = -6250336;
            } else if (this.playerInfo.getOnline()) {
                i = 8388479;
            } else {
                i = -1;
            }

            int j = top + height / 2 - 16;
            RealmsUtil.renderPlayerFace(guiGraphics, left, j, 32, this.playerInfo.getUuid());
            int k = top + height / 2 - 9 / 2;
            guiGraphics.drawString(RealmsPlayerScreen.this.font, this.playerInfo.getName(), left + 8 + 32, k, i, false);
            int l = top + height / 2 - 10;
            int m = left + width - this.removeButton.getWidth();
            this.removeButton.setPosition(m, l);
            this.removeButton.render(guiGraphics, mouseX, mouseY, partialTick);
            int n = m - this.activeOpButton().getWidth() - 8;
            this.makeOpButton.setPosition(n, l);
            this.makeOpButton.render(guiGraphics, mouseX, mouseY, partialTick);
            this.removeOpButton.setPosition(n, l);
            this.removeOpButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    class InvitedObjectSelectionList extends ContainerObjectSelectionList<RealmsPlayerScreen.Entry> {
        private static final int ITEM_HEIGHT = 36;

        public InvitedObjectSelectionList() {
            super(
                Minecraft.getInstance(),
                RealmsPlayerScreen.this.width,
                RealmsPlayerScreen.this.layout.getContentHeight(),
                RealmsPlayerScreen.this.layout.getHeaderHeight(),
                36
            );
            this.setRenderHeader(true, (int)(9.0F * 1.5F));
        }

        @Override
        protected void renderHeader(GuiGraphics guiGraphics, int i, int j) {
            String string = RealmsPlayerScreen.this.serverData.players != null ? Integer.toString(RealmsPlayerScreen.this.serverData.players.size()) : "0";
            Component component = Component.translatable("mco.configure.world.invited.number", string).withStyle(ChatFormatting.UNDERLINE);
            guiGraphics.drawString(
                RealmsPlayerScreen.this.font, component, i + this.getRowWidth() / 2 - RealmsPlayerScreen.this.font.width(component) / 2, j, -1, false
            );
        }

        @Override
        public int getMaxPosition() {
            return this.getItemCount() * this.itemHeight + this.headerHeight;
        }

        @Override
        public int getRowWidth() {
            return 300;
        }
    }
}
