package net.minecraft.client.gui.screens.worldselection;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ErrorScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LoadingDotsText;
import net.minecraft.client.gui.screens.NoticeWithLinkScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public class WorldSelectionList extends ObjectSelectionList<WorldSelectionList.Entry> {
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault());
    static final ResourceLocation ERROR_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("world_list/error_highlighted");
    static final ResourceLocation ERROR_SPRITE = ResourceLocation.withDefaultNamespace("world_list/error");
    static final ResourceLocation MARKED_JOIN_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("world_list/marked_join_highlighted");
    static final ResourceLocation MARKED_JOIN_SPRITE = ResourceLocation.withDefaultNamespace("world_list/marked_join");
    static final ResourceLocation WARNING_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("world_list/warning_highlighted");
    static final ResourceLocation WARNING_SPRITE = ResourceLocation.withDefaultNamespace("world_list/warning");
    static final ResourceLocation JOIN_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("world_list/join_highlighted");
    static final ResourceLocation JOIN_SPRITE = ResourceLocation.withDefaultNamespace("world_list/join");
    static final Logger LOGGER = LogUtils.getLogger();
    static final Component FROM_NEWER_TOOLTIP_1 = Component.translatable("selectWorld.tooltip.fromNewerVersion1").withStyle(ChatFormatting.RED);
    static final Component FROM_NEWER_TOOLTIP_2 = Component.translatable("selectWorld.tooltip.fromNewerVersion2").withStyle(ChatFormatting.RED);
    static final Component SNAPSHOT_TOOLTIP_1 = Component.translatable("selectWorld.tooltip.snapshot1").withStyle(ChatFormatting.GOLD);
    static final Component SNAPSHOT_TOOLTIP_2 = Component.translatable("selectWorld.tooltip.snapshot2").withStyle(ChatFormatting.GOLD);
    static final Component WORLD_LOCKED_TOOLTIP = Component.translatable("selectWorld.locked").withStyle(ChatFormatting.RED);
    static final Component WORLD_REQUIRES_CONVERSION = Component.translatable("selectWorld.conversion.tooltip").withStyle(ChatFormatting.RED);
    static final Component INCOMPATIBLE_VERSION_TOOLTIP = Component.translatable("selectWorld.incompatible.tooltip").withStyle(ChatFormatting.RED);
    static final Component WORLD_EXPERIMENTAL = Component.translatable("selectWorld.experimental");
    private final SelectWorldScreen screen;
    private CompletableFuture<List<LevelSummary>> pendingLevels;
    @Nullable
    private List<LevelSummary> currentlyDisplayedLevels;
    private String filter;
    private final WorldSelectionList.LoadingHeader loadingHeader;

    public WorldSelectionList(
        SelectWorldScreen screen, Minecraft minecraft, int width, int height, int y, int itemHeight, String filter, @Nullable WorldSelectionList worlds
    ) {
        super(minecraft, width, height, y, itemHeight);
        this.screen = screen;
        this.loadingHeader = new WorldSelectionList.LoadingHeader(minecraft);
        this.filter = filter;
        if (worlds != null) {
            this.pendingLevels = worlds.pendingLevels;
        } else {
            this.pendingLevels = this.loadLevels();
        }

        this.handleNewLevels(this.pollLevelsIgnoreErrors());
    }

    @Override
    protected void clearEntries() {
        this.children().forEach(WorldSelectionList.Entry::close);
        super.clearEntries();
    }

    @Nullable
    private List<LevelSummary> pollLevelsIgnoreErrors() {
        try {
            return this.pendingLevels.getNow(null);
        } catch (CompletionException | CancellationException runtimeException) {
            return null;
        }
    }

    void reloadWorldList() {
        this.pendingLevels = this.loadLevels();
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
        if (CommonInputs.selected(keyCode)) {
            Optional<WorldSelectionList.WorldListEntry> optional = this.getSelectedOpt();
            if (optional.isPresent()) {
                if (optional.get().canJoin()) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    optional.get().joinWorld();
                }

                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        List<LevelSummary> list = this.pollLevelsIgnoreErrors();
        if (list != this.currentlyDisplayedLevels) {
            this.handleNewLevels(list);
        }

        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void handleNewLevels(@Nullable List<LevelSummary> levels) {
        if (levels == null) {
            this.fillLoadingLevels();
        } else {
            this.fillLevels(this.filter, levels);
        }

        this.currentlyDisplayedLevels = levels;
    }

    public void updateFilter(String filter) {
        if (this.currentlyDisplayedLevels != null && !filter.equals(this.filter)) {
            this.fillLevels(filter, this.currentlyDisplayedLevels);
        }

        this.filter = filter;
    }

    private CompletableFuture<List<LevelSummary>> loadLevels() {
        LevelStorageSource.LevelCandidates levelCandidates;
        try {
            levelCandidates = this.minecraft.getLevelSource().findLevelCandidates();
        } catch (LevelStorageException levelStorageException) {
            LOGGER.error("Couldn't load level list", levelStorageException);
            this.handleLevelLoadFailure(levelStorageException.getMessageComponent());
            return CompletableFuture.completedFuture(List.of());
        }

        if (levelCandidates.isEmpty()) {
            CreateWorldScreen.openFresh(this.minecraft, null);
            return CompletableFuture.completedFuture(List.of());
        } else {
            return this.minecraft.getLevelSource().loadLevelSummaries(levelCandidates).exceptionally(throwable -> {
                this.minecraft.delayCrash(CrashReport.forThrowable(throwable, "Couldn't load level list"));
                return List.of();
            });
        }
    }

    private void fillLevels(String filter, List<LevelSummary> levels) {
        this.clearEntries();
        filter = filter.toLowerCase(Locale.ROOT);

        for (LevelSummary levelSummary : levels) {
            if (this.filterAccepts(filter, levelSummary)) {
                this.addEntry(new WorldSelectionList.WorldListEntry(this, levelSummary));
            }
        }

        this.notifyListUpdated();
    }

    private boolean filterAccepts(String filter, LevelSummary level) {
        return level.getLevelName().toLowerCase(Locale.ROOT).contains(filter) || level.getLevelId().toLowerCase(Locale.ROOT).contains(filter);
    }

    private void fillLoadingLevels() {
        this.clearEntries();
        this.addEntry(this.loadingHeader);
        this.notifyListUpdated();
    }

    private void notifyListUpdated() {
        this.clampScrollAmount();
        this.screen.triggerImmediateNarration(true);
    }

    private void handleLevelLoadFailure(Component exceptionMessage) {
        this.minecraft.setScreen(new ErrorScreen(Component.translatable("selectWorld.unable_to_load"), exceptionMessage));
    }

    @Override
    public int getRowWidth() {
        return 270;
    }

    public void setSelected(@Nullable WorldSelectionList.Entry selected) {
        super.setSelected(selected);
        this.screen.updateButtonStatus(selected instanceof WorldSelectionList.WorldListEntry worldListEntry ? worldListEntry.summary : null);
    }

    public Optional<WorldSelectionList.WorldListEntry> getSelectedOpt() {
        WorldSelectionList.Entry entry = this.getSelected();
        return entry instanceof WorldSelectionList.WorldListEntry worldListEntry ? Optional.of(worldListEntry) : Optional.empty();
    }

    public SelectWorldScreen getScreen() {
        return this.screen;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        if (this.children().contains(this.loadingHeader)) {
            this.loadingHeader.updateNarration(narrationElementOutput);
        } else {
            super.updateWidgetNarration(narrationElementOutput);
        }
    }

    public abstract static class Entry extends ObjectSelectionList.Entry<WorldSelectionList.Entry> implements AutoCloseable {
        @Override
        public void close() {
        }
    }

    public static class LoadingHeader extends WorldSelectionList.Entry {
        private static final Component LOADING_LABEL = Component.translatable("selectWorld.loading_list");
        private final Minecraft minecraft;

        public LoadingHeader(Minecraft minecraft) {
            this.minecraft = minecraft;
        }

        @Override
        public void render(
            GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick
        ) {
            int i = (this.minecraft.screen.width - this.minecraft.font.width(LOADING_LABEL)) / 2;
            int j = top + (height - 9) / 2;
            guiGraphics.drawString(this.minecraft.font, LOADING_LABEL, i, j, 16777215, false);
            String string = LoadingDotsText.get(Util.getMillis());
            int k = (this.minecraft.screen.width - this.minecraft.font.width(string)) / 2;
            int l = j + 9;
            guiGraphics.drawString(this.minecraft.font, string, k, l, -8355712, false);
        }

        @Override
        public Component getNarration() {
            return LOADING_LABEL;
        }
    }

    public final class WorldListEntry extends WorldSelectionList.Entry implements AutoCloseable {
        private static final int ICON_WIDTH = 32;
        private static final int ICON_HEIGHT = 32;
        private final Minecraft minecraft;
        private final SelectWorldScreen screen;
        final LevelSummary summary;
        private final FaviconTexture icon;
        @Nullable
        private Path iconFile;
        private long lastClickTime;

        public WorldListEntry(final WorldSelectionList worldSelectionList, final LevelSummary summary) {
            this.minecraft = worldSelectionList.minecraft;
            this.screen = worldSelectionList.getScreen();
            this.summary = summary;
            this.icon = FaviconTexture.forWorld(this.minecraft.getTextureManager(), summary.getLevelId());
            this.iconFile = summary.getIcon();
            this.validateIconFile();
            this.loadIcon();
        }

        private void validateIconFile() {
            if (this.iconFile != null) {
                try {
                    BasicFileAttributes basicFileAttributes = Files.readAttributes(this.iconFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (basicFileAttributes.isSymbolicLink()) {
                        List<ForbiddenSymlinkInfo> list = this.minecraft.directoryValidator().validateSymlink(this.iconFile);
                        if (!list.isEmpty()) {
                            WorldSelectionList.LOGGER.warn("{}", ContentValidationException.getMessage(this.iconFile, list));
                            this.iconFile = null;
                        } else {
                            basicFileAttributes = Files.readAttributes(this.iconFile, BasicFileAttributes.class);
                        }
                    }

                    if (!basicFileAttributes.isRegularFile()) {
                        this.iconFile = null;
                    }
                } catch (NoSuchFileException noSuchFileException) {
                    this.iconFile = null;
                } catch (IOException iOException) {
                    WorldSelectionList.LOGGER.error("could not validate symlink", iOException);
                    this.iconFile = null;
                }
            }
        }

        @Override
        public Component getNarration() {
            Component component = Component.translatable(
                "narrator.select.world_info",
                this.summary.getLevelName(),
                Component.translationArg(new Date(this.summary.getLastPlayed())),
                this.summary.getInfo()
            );
            if (this.summary.isLocked()) {
                component = CommonComponents.joinForNarration(component, WorldSelectionList.WORLD_LOCKED_TOOLTIP);
            }

            if (this.summary.isExperimental()) {
                component = CommonComponents.joinForNarration(component, WorldSelectionList.WORLD_EXPERIMENTAL);
            }

            return Component.translatable("narrator.select", component);
        }

        @Override
        public void render(
            GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick
        ) {
            String string = this.summary.getLevelName();
            String string2 = this.summary.getLevelId();
            long l = this.summary.getLastPlayed();
            if (l != -1L) {
                string2 = string2 + " (" + WorldSelectionList.DATE_FORMAT.format(Instant.ofEpochMilli(l)) + ")";
            }

            if (StringUtils.isEmpty(string)) {
                string = I18n.get("selectWorld.world") + " " + (index + 1);
            }

            Component component = this.summary.getInfo();
            guiGraphics.drawString(this.minecraft.font, string, left + 32 + 3, top + 1, 16777215, false);
            guiGraphics.drawString(this.minecraft.font, string2, left + 32 + 3, top + 9 + 3, -8355712, false);
            guiGraphics.drawString(this.minecraft.font, component, left + 32 + 3, top + 9 + 9 + 3, -8355712, false);
            RenderSystem.enableBlend();
            guiGraphics.blit(this.icon.textureLocation(), left, top, 0.0F, 0.0F, 32, 32, 32, 32);
            RenderSystem.disableBlend();
            if (this.minecraft.options.touchscreen().get() || hovering) {
                guiGraphics.fill(left, top, left + 32, top + 32, -1601138544);
                int i = mouseX - left;
                boolean bl = i < 32;
                ResourceLocation resourceLocation = bl ? WorldSelectionList.JOIN_HIGHLIGHTED_SPRITE : WorldSelectionList.JOIN_SPRITE;
                ResourceLocation resourceLocation2 = bl ? WorldSelectionList.WARNING_HIGHLIGHTED_SPRITE : WorldSelectionList.WARNING_SPRITE;
                ResourceLocation resourceLocation3 = bl ? WorldSelectionList.ERROR_HIGHLIGHTED_SPRITE : WorldSelectionList.ERROR_SPRITE;
                ResourceLocation resourceLocation4 = bl ? WorldSelectionList.MARKED_JOIN_HIGHLIGHTED_SPRITE : WorldSelectionList.MARKED_JOIN_SPRITE;
                if (this.summary instanceof LevelSummary.SymlinkLevelSummary || this.summary instanceof LevelSummary.CorruptedLevelSummary) {
                    guiGraphics.blitSprite(resourceLocation3, left, top, 32, 32);
                    guiGraphics.blitSprite(resourceLocation4, left, top, 32, 32);
                    return;
                }

                if (this.summary.isLocked()) {
                    guiGraphics.blitSprite(resourceLocation3, left, top, 32, 32);
                    if (bl) {
                        this.screen.setTooltipForNextRenderPass(this.minecraft.font.split(WorldSelectionList.WORLD_LOCKED_TOOLTIP, 175));
                    }
                } else if (this.summary.requiresManualConversion()) {
                    guiGraphics.blitSprite(resourceLocation3, left, top, 32, 32);
                    if (bl) {
                        this.screen.setTooltipForNextRenderPass(this.minecraft.font.split(WorldSelectionList.WORLD_REQUIRES_CONVERSION, 175));
                    }
                } else if (!this.summary.isCompatible()) {
                    guiGraphics.blitSprite(resourceLocation3, left, top, 32, 32);
                    if (bl) {
                        this.screen.setTooltipForNextRenderPass(this.minecraft.font.split(WorldSelectionList.INCOMPATIBLE_VERSION_TOOLTIP, 175));
                    }
                } else if (this.summary.shouldBackup()) {
                    guiGraphics.blitSprite(resourceLocation4, left, top, 32, 32);
                    if (this.summary.isDowngrade()) {
                        guiGraphics.blitSprite(resourceLocation3, left, top, 32, 32);
                        if (bl) {
                            this.screen
                                .setTooltipForNextRenderPass(
                                    ImmutableList.of(
                                        WorldSelectionList.FROM_NEWER_TOOLTIP_1.getVisualOrderText(),
                                        WorldSelectionList.FROM_NEWER_TOOLTIP_2.getVisualOrderText()
                                    )
                                );
                        }
                    } else if (!SharedConstants.getCurrentVersion().isStable()) {
                        guiGraphics.blitSprite(resourceLocation2, left, top, 32, 32);
                        if (bl) {
                            this.screen
                                .setTooltipForNextRenderPass(
                                    ImmutableList.of(
                                        WorldSelectionList.SNAPSHOT_TOOLTIP_1.getVisualOrderText(), WorldSelectionList.SNAPSHOT_TOOLTIP_2.getVisualOrderText()
                                    )
                                );
                        }
                    }
                } else {
                    guiGraphics.blitSprite(resourceLocation, left, top, 32, 32);
                }
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
            if (!this.summary.primaryActionActive()) {
                return true;
            }

            WorldSelectionList.this.setSelected(this);
            if (!(mouseX - WorldSelectionList.this.getRowLeft() <= 32.0) && Util.getMillis() - this.lastClickTime >= 250L) {
                this.lastClickTime = Util.getMillis();
                return super.mouseClicked(mouseX, mouseY, button);
            }

            if (this.canJoin()) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.joinWorld();
            }

            return true;
        }

        public boolean canJoin() {
            return this.summary.primaryActionActive();
        }

        public void joinWorld() {
            if (this.summary.primaryActionActive()) {
                if (this.summary instanceof LevelSummary.SymlinkLevelSummary) {
                    this.minecraft.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> this.minecraft.setScreen(this.screen)));
                } else {
                    this.minecraft.createWorldOpenFlows().openWorld(this.summary.getLevelId(), () -> {
                        WorldSelectionList.this.reloadWorldList();
                        this.minecraft.setScreen(this.screen);
                    });
                }
            }
        }

        public void deleteWorld() {
            this.minecraft
                .setScreen(
                    new ConfirmScreen(
                        bl -> {
                            if (bl) {
                                this.minecraft.setScreen(new ProgressScreen(true));
                                this.doDeleteWorld();
                            }

                            this.minecraft.setScreen(this.screen);
                        },
                        Component.translatable("selectWorld.deleteQuestion"),
                        Component.translatable("selectWorld.deleteWarning", this.summary.getLevelName()),
                        Component.translatable("selectWorld.deleteButton"),
                        CommonComponents.GUI_CANCEL
                    )
                );
        }

        public void doDeleteWorld() {
            LevelStorageSource levelStorageSource = this.minecraft.getLevelSource();
            String string = this.summary.getLevelId();

            try (LevelStorageSource.LevelStorageAccess levelStorageAccess = levelStorageSource.createAccess(string)) {
                levelStorageAccess.deleteLevel();
            } catch (IOException iOException) {
                SystemToast.onWorldDeleteFailure(this.minecraft, string);
                WorldSelectionList.LOGGER.error("Failed to delete world {}", string, iOException);
            }

            WorldSelectionList.this.reloadWorldList();
        }

        public void editWorld() {
            this.queueLoadScreen();
            String string = this.summary.getLevelId();

            LevelStorageSource.LevelStorageAccess levelStorageAccess;
            try {
                levelStorageAccess = this.minecraft.getLevelSource().validateAndCreateAccess(string);
            } catch (IOException iOException) {
                SystemToast.onWorldAccessFailure(this.minecraft, string);
                WorldSelectionList.LOGGER.error("Failed to access level {}", string, iOException);
                WorldSelectionList.this.reloadWorldList();
                return;
            } catch (ContentValidationException contentValidationException) {
                WorldSelectionList.LOGGER.warn("{}", contentValidationException.getMessage());
                this.minecraft.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> this.minecraft.setScreen(this.screen)));
                return;
            }

            EditWorldScreen editWorldScreen;
            try {
                editWorldScreen = EditWorldScreen.create(this.minecraft, levelStorageAccess, bl -> {
                    levelStorageAccess.safeClose();
                    if (bl) {
                        WorldSelectionList.this.reloadWorldList();
                    }

                    this.minecraft.setScreen(this.screen);
                });
            } catch (IOException | NbtException | ReportedNbtException exception) {
                levelStorageAccess.safeClose();
                SystemToast.onWorldAccessFailure(this.minecraft, string);
                WorldSelectionList.LOGGER.error("Failed to load world data {}", string, exception);
                WorldSelectionList.this.reloadWorldList();
                return;
            }

            this.minecraft.setScreen(editWorldScreen);
        }

        public void recreateWorld() {
            this.queueLoadScreen();

            try (LevelStorageSource.LevelStorageAccess levelStorageAccess = this.minecraft.getLevelSource().validateAndCreateAccess(this.summary.getLevelId())) {
                Pair<LevelSettings, WorldCreationContext> pair = this.minecraft.createWorldOpenFlows().recreateWorldData(levelStorageAccess);
                LevelSettings levelSettings = pair.getFirst();
                WorldCreationContext worldCreationContext = pair.getSecond();
                Path path = CreateWorldScreen.createTempDataPackDirFromExistingWorld(
                    levelStorageAccess.getLevelPath(LevelResource.DATAPACK_DIR), this.minecraft
                );
                worldCreationContext.validate();
                if (worldCreationContext.options().isOldCustomizedWorld()) {
                    this.minecraft
                        .setScreen(
                            new ConfirmScreen(
                                bl -> this.minecraft
                                    .setScreen(
                                        bl
                                            ? CreateWorldScreen.createFromExisting(this.minecraft, this.screen, levelSettings, worldCreationContext, path)
                                            : this.screen
                                    ),
                                Component.translatable("selectWorld.recreate.customized.title"),
                                Component.translatable("selectWorld.recreate.customized.text"),
                                CommonComponents.GUI_PROCEED,
                                CommonComponents.GUI_CANCEL
                            )
                        );
                } else {
                    this.minecraft.setScreen(CreateWorldScreen.createFromExisting(this.minecraft, this.screen, levelSettings, worldCreationContext, path));
                }
            } catch (ContentValidationException contentValidationException) {
                WorldSelectionList.LOGGER.warn("{}", contentValidationException.getMessage());
                this.minecraft.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> this.minecraft.setScreen(this.screen)));
            } catch (Exception exception) {
                WorldSelectionList.LOGGER.error("Unable to recreate world", exception);
                this.minecraft
                    .setScreen(
                        new AlertScreen(
                            () -> this.minecraft.setScreen(this.screen),
                            Component.translatable("selectWorld.recreate.error.title"),
                            Component.translatable("selectWorld.recreate.error.text")
                        )
                    );
            }
        }

        private void queueLoadScreen() {
            this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));
        }

        private void loadIcon() {
            boolean bl = this.iconFile != null && Files.isRegularFile(this.iconFile);
            if (bl) {
                try (InputStream inputStream = Files.newInputStream(this.iconFile)) {
                    this.icon.upload(NativeImage.read(inputStream));
                } catch (Throwable throwable) {
                    WorldSelectionList.LOGGER.error("Invalid icon for world {}", this.summary.getLevelId(), throwable);
                    this.iconFile = null;
                }
            } else {
                this.icon.clear();
            }
        }

        @Override
        public void close() {
            this.icon.close();
        }

        public String getLevelName() {
            return this.summary.getLevelName();
        }
    }
}
