package net.minecraft.client.gui.screens.worldselection;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.Mth;
import net.minecraft.util.worldupdate.WorldUpgrader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.slf4j.Logger;

public class OptimizeWorldScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ToIntFunction<ResourceKey<Level>> DIMENSION_COLORS = Util.make(new Reference2IntOpenHashMap<>(), map -> {
        map.put(Level.OVERWORLD, -13408734);
        map.put(Level.NETHER, -10075085);
        map.put(Level.END, -8943531);
        map.defaultReturnValue(-2236963);
    });
    private final BooleanConsumer callback;
    private final WorldUpgrader upgrader;

    @Nullable
    public static OptimizeWorldScreen create(
        Minecraft minecraft, BooleanConsumer callback, DataFixer dataFixer, LevelStorageSource.LevelStorageAccess levelStorage, boolean eraseCache
    ) {
        try {
            WorldOpenFlows worldOpenFlows = minecraft.createWorldOpenFlows();
            PackRepository packRepository = ServerPacksSource.createPackRepository(levelStorage);

            try (WorldStem worldStem = worldOpenFlows.loadWorldStem(levelStorage.getDataTag(), false, packRepository)) {
                WorldData worldData = worldStem.worldData();
                RegistryAccess.Frozen frozen = worldStem.registries().compositeAccess();
                levelStorage.saveDataTag(frozen, worldData);
                return new OptimizeWorldScreen(callback, dataFixer, levelStorage, worldData.getLevelSettings(), eraseCache, frozen);
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to load datapacks, can't optimize world", exception);
            return null;
        }
    }

    private OptimizeWorldScreen(
        BooleanConsumer callback,
        DataFixer dataFixer,
        LevelStorageSource.LevelStorageAccess levelStorage,
        LevelSettings levelSettings,
        boolean eraseCache,
        RegistryAccess registryAccess
    ) {
        super(Component.translatable("optimizeWorld.title", levelSettings.levelName()));
        this.callback = callback;
        this.upgrader = new WorldUpgrader(levelStorage, dataFixer, registryAccess, eraseCache, false);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> {
            this.upgrader.cancel();
            this.callback.accept(false);
        }).bounds(this.width / 2 - 100, this.height / 4 + 150, 200, 20).build());
    }

    @Override
    public void tick() {
        if (this.upgrader.isFinished()) {
            this.callback.accept(true);
        }
    }

    @Override
    public void onClose() {
        this.callback.accept(false);
    }

    @Override
    public void removed() {
        this.upgrader.cancel();
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
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 16777215);
        int i = this.width / 2 - 150;
        int j = this.width / 2 + 150;
        int k = this.height / 4 + 100;
        int l = k + 10;
        guiGraphics.drawCenteredString(this.font, this.upgrader.getStatus(), this.width / 2, k - 9 - 2, 10526880);
        if (this.upgrader.getTotalChunks() > 0) {
            guiGraphics.fill(i - 1, k - 1, j + 1, l + 1, -16777216);
            guiGraphics.drawString(this.font, Component.translatable("optimizeWorld.info.converted", this.upgrader.getConverted()), i, 40, 10526880);
            guiGraphics.drawString(this.font, Component.translatable("optimizeWorld.info.skipped", this.upgrader.getSkipped()), i, 40 + 9 + 3, 10526880);
            guiGraphics.drawString(this.font, Component.translatable("optimizeWorld.info.total", this.upgrader.getTotalChunks()), i, 40 + (9 + 3) * 2, 10526880);
            int m = 0;

            for (ResourceKey<Level> resourceKey : this.upgrader.levels()) {
                int n = Mth.floor(this.upgrader.dimensionProgress(resourceKey) * (j - i));
                guiGraphics.fill(i + m, k, i + m + n, l, DIMENSION_COLORS.applyAsInt(resourceKey));
                m += n;
            }

            int o = this.upgrader.getConverted() + this.upgrader.getSkipped();
            Component component = Component.translatable("optimizeWorld.progress.counter", o, this.upgrader.getTotalChunks());
            Component component2 = Component.translatable("optimizeWorld.progress.percentage", Mth.floor(this.upgrader.getProgress() * 100.0F));
            guiGraphics.drawCenteredString(this.font, component, this.width / 2, k + 2 * 9 + 2, 10526880);
            guiGraphics.drawCenteredString(this.font, component2, this.width / 2, k + (l - k) / 2 - 9 / 2, 10526880);
        }
    }
}
