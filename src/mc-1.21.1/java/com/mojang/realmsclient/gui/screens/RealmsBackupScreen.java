package com.mojang.realmsclient.gui.screens;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.Backup;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.util.RealmsUtil;
import com.mojang.realmsclient.util.task.DownloadTask;
import com.mojang.realmsclient.util.task.RestoreTask;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import org.slf4j.Logger;

public class RealmsBackupScreen extends RealmsScreen {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TITLE = Component.translatable("mco.configure.world.backup");
    static final Component RESTORE_TOOLTIP = Component.translatable("mco.backup.button.restore");
    static final Component HAS_CHANGES_TOOLTIP = Component.translatable("mco.backup.changes.tooltip");
    private static final Component NO_BACKUPS_LABEL = Component.translatable("mco.backup.nobackups");
    private static final Component DOWNLOAD_LATEST = Component.translatable("mco.backup.button.download");
    private static final String UPLOADED_KEY = "uploaded";
    private static final int PADDING = 8;
    final RealmsConfigureWorldScreen lastScreen;
    List<Backup> backups = Collections.emptyList();
    @Nullable
    RealmsBackupScreen.BackupObjectSelectionList backupList;
    final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final int slotId;
    @Nullable
    Button downloadButton;
    final RealmsServer serverData;
    boolean noBackups = false;

    public RealmsBackupScreen(RealmsConfigureWorldScreen lastScreen, RealmsServer serverData, int slotId) {
        super(TITLE);
        this.lastScreen = lastScreen;
        this.serverData = serverData;
        this.slotId = slotId;
    }

    @Override
    public void init() {
        this.layout.addTitleHeader(TITLE, this.font);
        this.backupList = this.layout.addToContents(new RealmsBackupScreen.BackupObjectSelectionList());
        LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        this.downloadButton = linearLayout.addChild(Button.builder(DOWNLOAD_LATEST, button -> this.downloadClicked()).build());
        this.downloadButton.active = false;
        linearLayout.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).build());
        this.layout.visitWidgets(guiEventListener -> {
            AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
        });
        this.repositionElements();
        this.fetchRealmsBackups();
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
        if (this.noBackups && this.backupList != null) {
            guiGraphics.drawString(
                this.font,
                NO_BACKUPS_LABEL,
                this.width / 2 - this.font.width(NO_BACKUPS_LABEL) / 2,
                this.backupList.getY() + this.backupList.getHeight() / 2 - 9 / 2,
                -1,
                false
            );
        }
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.backupList != null) {
            this.backupList.updateSize(this.width, this.layout);
        }
    }

    private void fetchRealmsBackups() {
        (new Thread("Realms-fetch-backups") {
            @Override
            public void run() {
                RealmsClient realmsClient = RealmsClient.create();

                try {
                    List<Backup> list = realmsClient.backupsFor(RealmsBackupScreen.this.serverData.id).backups;
                    RealmsBackupScreen.this.minecraft.execute(() -> {
                        RealmsBackupScreen.this.backups = list;
                        RealmsBackupScreen.this.noBackups = RealmsBackupScreen.this.backups.isEmpty();
                        if (!RealmsBackupScreen.this.noBackups && RealmsBackupScreen.this.downloadButton != null) {
                            RealmsBackupScreen.this.downloadButton.active = true;
                        }

                        if (RealmsBackupScreen.this.backupList != null) {
                            RealmsBackupScreen.this.backupList.children().clear();

                            for (Backup backup : RealmsBackupScreen.this.backups) {
                                RealmsBackupScreen.this.backupList.addEntry(backup);
                            }
                        }
                    });
                } catch (RealmsServiceException realmsServiceException) {
                    RealmsBackupScreen.LOGGER.error("Couldn't request backups", realmsServiceException);
                }
            }
        }).start();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    private void downloadClicked() {
        this.minecraft
            .setScreen(
                RealmsPopups.infoPopupScreen(
                    this,
                    Component.translatable("mco.configure.world.restore.download.question.line1"),
                    popupScreen -> this.minecraft
                        .setScreen(
                            new RealmsLongRunningMcoTaskScreen(
                                this.lastScreen.getNewScreen(),
                                new DownloadTask(
                                    this.serverData.id,
                                    this.slotId,
                                    this.serverData.name
                                        + " ("
                                        + this.serverData.slots.get(this.serverData.activeSlot).getSlotName(this.serverData.activeSlot)
                                        + ")",
                                    this
                                )
                            )
                        )
                )
            );
    }

    class BackupObjectSelectionList extends ContainerObjectSelectionList<RealmsBackupScreen.Entry> {
        private static final int ITEM_HEIGHT = 36;

        public BackupObjectSelectionList() {
            super(
                Minecraft.getInstance(),
                RealmsBackupScreen.this.width,
                RealmsBackupScreen.this.layout.getContentHeight(),
                RealmsBackupScreen.this.layout.getHeaderHeight(),
                36
            );
        }

        public void addEntry(Backup backup) {
            this.addEntry(RealmsBackupScreen.this.new Entry(backup));
        }

        @Override
        public int getMaxPosition() {
            return this.getItemCount() * 36 + this.headerHeight;
        }

        @Override
        public int getRowWidth() {
            return 300;
        }
    }

    class Entry extends ContainerObjectSelectionList.Entry<RealmsBackupScreen.Entry> {
        private static final int Y_PADDING = 2;
        private final Backup backup;
        @Nullable
        private Button restoreButton;
        @Nullable
        private Button changesButton;
        private final List<AbstractWidget> children = new ArrayList<>();

        public Entry(final Backup backup) {
            this.backup = backup;
            this.populateChangeList(backup);
            if (!backup.changeList.isEmpty()) {
                this.changesButton = Button.builder(
                        RealmsBackupScreen.HAS_CHANGES_TOOLTIP,
                        button -> RealmsBackupScreen.this.minecraft.setScreen(new RealmsBackupInfoScreen(RealmsBackupScreen.this, this.backup))
                    )
                    .width(8 + RealmsBackupScreen.this.font.width(RealmsBackupScreen.HAS_CHANGES_TOOLTIP))
                    .createNarration(
                        supplier -> CommonComponents.joinForNarration(Component.translatable("mco.backup.narration", this.getShortBackupDate()), supplier.get())
                    )
                    .build();
                this.children.add(this.changesButton);
            }

            if (!RealmsBackupScreen.this.serverData.expired) {
                this.restoreButton = Button.builder(RealmsBackupScreen.RESTORE_TOOLTIP, button -> this.restoreClicked())
                    .width(8 + RealmsBackupScreen.this.font.width(RealmsBackupScreen.HAS_CHANGES_TOOLTIP))
                    .createNarration(
                        supplier -> CommonComponents.joinForNarration(Component.translatable("mco.backup.narration", this.getShortBackupDate()), supplier.get())
                    )
                    .build();
                this.children.add(this.restoreButton);
            }
        }

        private void populateChangeList(Backup backup) {
            int i = RealmsBackupScreen.this.backups.indexOf(backup);
            if (i != RealmsBackupScreen.this.backups.size() - 1) {
                Backup backup2 = RealmsBackupScreen.this.backups.get(i + 1);

                for (String string : backup.metadata.keySet()) {
                    if (!string.contains("uploaded") && backup2.metadata.containsKey(string)) {
                        if (!backup.metadata.get(string).equals(backup2.metadata.get(string))) {
                            this.addToChangeList(string);
                        }
                    } else {
                        this.addToChangeList(string);
                    }
                }
            }
        }

        private void addToChangeList(String change) {
            if (change.contains("uploaded")) {
                String string = DateFormat.getDateTimeInstance(3, 3).format(this.backup.lastModifiedDate);
                this.backup.changeList.put(change, string);
                this.backup.setUploadedVersion(true);
            } else {
                this.backup.changeList.put(change, this.backup.metadata.get(change));
            }
        }

        private String getShortBackupDate() {
            return DateFormat.getDateTimeInstance(3, 3).format(this.backup.lastModifiedDate);
        }

        private void restoreClicked() {
            Component component = RealmsUtil.convertToAgePresentationFromInstant(this.backup.lastModifiedDate);
            Component component2 = Component.translatable("mco.configure.world.restore.question.line1", this.getShortBackupDate(), component);
            RealmsBackupScreen.this.minecraft
                .setScreen(
                    RealmsPopups.warningPopupScreen(
                        RealmsBackupScreen.this,
                        component2,
                        popupScreen -> RealmsBackupScreen.this.minecraft
                            .setScreen(
                                new RealmsLongRunningMcoTaskScreen(
                                    RealmsBackupScreen.this.lastScreen.getNewScreen(),
                                    new RestoreTask(this.backup, RealmsBackupScreen.this.serverData.id, RealmsBackupScreen.this.lastScreen)
                                )
                            )
                    )
                );
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.children;
        }

        @Override
        public void render(
            GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick
        ) {
            int i = top + height / 2;
            int j = i - 9 - 2;
            int k = i + 2;
            int l = this.backup.isUploadedVersion() ? -8388737 : -1;
            guiGraphics.drawString(
                RealmsBackupScreen.this.font,
                Component.translatable("mco.backup.entry", RealmsUtil.convertToAgePresentationFromInstant(this.backup.lastModifiedDate)),
                left,
                j,
                l,
                false
            );
            guiGraphics.drawString(RealmsBackupScreen.this.font, this.getMediumDatePresentation(this.backup.lastModifiedDate), left, k, 5000268, false);
            int m = 0;
            int n = top + height / 2 - 10;
            if (this.restoreButton != null) {
                m += this.restoreButton.getWidth() + 8;
                this.restoreButton.setX(left + width - m);
                this.restoreButton.setY(n);
                this.restoreButton.render(guiGraphics, mouseX, mouseY, partialTick);
            }

            if (this.changesButton != null) {
                m += this.changesButton.getWidth() + 8;
                this.changesButton.setX(left + width - m);
                this.changesButton.setY(n);
                this.changesButton.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        private String getMediumDatePresentation(Date date) {
            return DateFormat.getDateTimeInstance(3, 3).format(date);
        }
    }
}
