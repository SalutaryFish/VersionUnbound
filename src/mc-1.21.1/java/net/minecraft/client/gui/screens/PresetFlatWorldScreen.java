package net.minecraft.client.gui.screens;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FlatLevelGeneratorPresetTags;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPreset;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.slf4j.Logger;

public class PresetFlatWorldScreen extends Screen {
    static final ResourceLocation SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot");
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int SLOT_BG_SIZE = 18;
    private static final int SLOT_STAT_HEIGHT = 20;
    private static final int SLOT_BG_X = 1;
    private static final int SLOT_BG_Y = 1;
    private static final int SLOT_FG_X = 2;
    private static final int SLOT_FG_Y = 2;
    private static final ResourceKey<Biome> DEFAULT_BIOME = Biomes.PLAINS;
    public static final Component UNKNOWN_PRESET = Component.translatable("flat_world_preset.unknown");
    /**
     * The parent GUI
     */
    private final CreateFlatWorldScreen parent;
    private Component shareText;
    private Component listText;
    private PresetFlatWorldScreen.PresetsList list;
    private Button selectButton;
    EditBox export;
    FlatLevelGeneratorSettings settings;

    public PresetFlatWorldScreen(CreateFlatWorldScreen parent) {
        super(Component.translatable("createWorld.customize.presets.title"));
        this.parent = parent;
    }

    @Nullable
    private static FlatLayerInfo getLayerInfoFromString(HolderGetter<Block> blockGetter, String layerInfo, int currentHeight) {
        List<String> list = Splitter.on('*').limit(2).splitToList(layerInfo);
        int i;
        String string;
        if (list.size() == 2) {
            string = list.get(1);

            try {
                i = Math.max(Integer.parseInt(list.get(0)), 0);
            } catch (NumberFormatException numberFormatException) {
                LOGGER.error("Error while parsing flat world string", numberFormatException);
                return null;
            }
        } else {
            string = list.get(0);
            i = 1;
        }

        int j = Math.min(currentHeight + i, DimensionType.Y_SIZE);
        int k = j - currentHeight;

        Optional<Holder.Reference<Block>> optional;
        try {
            optional = blockGetter.get(ResourceKey.create(Registries.BLOCK, ResourceLocation.parse(string)));
        } catch (Exception exception) {
            LOGGER.error("Error while parsing flat world string", exception);
            return null;
        }

        if (optional.isEmpty()) {
            LOGGER.error("Error while parsing flat world string => Unknown block, {}", string);
            return null;
        } else {
            return new FlatLayerInfo(k, optional.get().value());
        }
    }

    private static List<FlatLayerInfo> getLayersInfoFromString(HolderGetter<Block> blockGetter, String layerInfo) {
        List<FlatLayerInfo> list = Lists.newArrayList();
        String[] strings = layerInfo.split(",");
        int i = 0;

        for (String string : strings) {
            FlatLayerInfo flatLayerInfo = getLayerInfoFromString(blockGetter, string, i);
            if (flatLayerInfo == null) {
                return Collections.emptyList();
            }

            list.add(flatLayerInfo);
            i += flatLayerInfo.getHeight();
        }

        return list;
    }

    public static FlatLevelGeneratorSettings fromString(
        HolderGetter<Block> blockGetter,
        HolderGetter<Biome> biomeGetter,
        HolderGetter<StructureSet> structureSetGetter,
        HolderGetter<PlacedFeature> placedFeatureGetter,
        String settings,
        FlatLevelGeneratorSettings layerGenerationSettings
    ) {
        Iterator<String> iterator = Splitter.on(';').split(settings).iterator();
        if (!iterator.hasNext()) {
            return FlatLevelGeneratorSettings.getDefault(biomeGetter, structureSetGetter, placedFeatureGetter);
        }

        List<FlatLayerInfo> list = getLayersInfoFromString(blockGetter, iterator.next());
        if (list.isEmpty()) {
            return FlatLevelGeneratorSettings.getDefault(biomeGetter, structureSetGetter, placedFeatureGetter);
        }

        Holder.Reference<Biome> reference = biomeGetter.getOrThrow(DEFAULT_BIOME);
        Holder<Biome> holder = reference;
        if (iterator.hasNext()) {
            String string = iterator.next();
            holder = Optional.ofNullable(ResourceLocation.tryParse(string))
                .map(resourceLocation -> ResourceKey.create(Registries.BIOME, resourceLocation))
                .flatMap(biomeGetter::get)
                .orElseGet(() -> {
                    LOGGER.warn("Invalid biome: {}", string);
                    return reference;
                });
        }

        return layerGenerationSettings.withBiomeAndLayers(list, layerGenerationSettings.structureOverrides(), holder);
    }

    static String save(FlatLevelGeneratorSettings levelGeneratorSettings) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < levelGeneratorSettings.getLayersInfo().size(); i++) {
            if (i > 0) {
                stringBuilder.append(",");
            }

            stringBuilder.append(levelGeneratorSettings.getLayersInfo().get(i));
        }

        stringBuilder.append(";");
        stringBuilder.append(
            levelGeneratorSettings.getBiome().unwrapKey().map(ResourceKey::location).orElseThrow(() -> new IllegalStateException("Biome not registered"))
        );
        return stringBuilder.toString();
    }

    @Override
    protected void init() {
        this.shareText = Component.translatable("createWorld.customize.presets.share");
        this.listText = Component.translatable("createWorld.customize.presets.list");
        this.export = new EditBox(this.font, 50, 40, this.width - 100, 20, this.shareText);
        this.export.setMaxLength(1230);
        WorldCreationContext worldCreationContext = this.parent.parent.getUiState().getSettings();
        RegistryAccess registryAccess = worldCreationContext.worldgenLoadContext();
        FeatureFlagSet featureFlagSet = worldCreationContext.dataConfiguration().enabledFeatures();
        HolderGetter<Biome> holderGetter = registryAccess.lookupOrThrow(Registries.BIOME);
        HolderGetter<StructureSet> holderGetter2 = registryAccess.lookupOrThrow(Registries.STRUCTURE_SET);
        HolderGetter<PlacedFeature> holderGetter3 = registryAccess.lookupOrThrow(Registries.PLACED_FEATURE);
        HolderGetter<Block> holderGetter4 = registryAccess.lookupOrThrow(Registries.BLOCK).filterFeatures(featureFlagSet);
        this.export.setValue(save(this.parent.settings()));
        this.settings = this.parent.settings();
        this.addWidget(this.export);
        this.list = this.addRenderableWidget(new PresetFlatWorldScreen.PresetsList(registryAccess, featureFlagSet));
        this.selectButton = this.addRenderableWidget(
            Button.builder(
                    Component.translatable("createWorld.customize.presets.select"),
                    button -> {
                        FlatLevelGeneratorSettings flatLevelGeneratorSettings = fromString(
                            holderGetter4, holderGetter, holderGetter2, holderGetter3, this.export.getValue(), this.settings
                        );
                        this.parent.setConfig(flatLevelGeneratorSettings);
                        this.minecraft.setScreen(this.parent);
                    }
                )
                .bounds(this.width / 2 - 155, this.height - 28, 150, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL, button -> this.minecraft.setScreen(this.parent))
                .bounds(this.width / 2 + 5, this.height - 28, 150, 20)
                .build()
        );
        this.updateButtonValidity(this.list.getSelected() != null);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.list.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String string = this.export.getValue();
        this.init(minecraft, width, height);
        this.export.setValue(string);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
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
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 400.0F);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 16777215);
        guiGraphics.drawString(this.font, this.shareText, 51, 30, 10526880);
        guiGraphics.drawString(this.font, this.listText, 51, 68, 10526880);
        guiGraphics.pose().popPose();
        this.export.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    public void updateButtonValidity(boolean valid) {
        this.selectButton.active = valid || this.export.getValue().length() > 1;
    }

    class PresetsList extends ObjectSelectionList<PresetFlatWorldScreen.PresetsList.Entry> {
        public PresetsList(final RegistryAccess registryAccess, final FeatureFlagSet flags) {
            super(PresetFlatWorldScreen.this.minecraft, PresetFlatWorldScreen.this.width, PresetFlatWorldScreen.this.height - 117, 80, 24);

            for (Holder<FlatLevelGeneratorPreset> holder : registryAccess.registryOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET)
                .getTagOrEmpty(FlatLevelGeneratorPresetTags.VISIBLE)) {
                Set<Block> set = holder.value()
                    .settings()
                    .getLayersInfo()
                    .stream()
                    .map(flatLayerInfo -> flatLayerInfo.getBlockState().getBlock())
                    .filter(block -> !block.isEnabled(flags))
                    .collect(Collectors.toSet());
                if (!set.isEmpty()) {
                    PresetFlatWorldScreen.LOGGER
                        .info(
                            "Discarding flat world preset {} since it contains experimental blocks {}",
                            holder.unwrapKey().map(resourceKey -> resourceKey.location().toString()).orElse("<unknown>"),
                            set
                        );
                } else {
                    this.addEntry(new PresetFlatWorldScreen.PresetsList.Entry(holder));
                }
            }
        }

        public void setSelected(@Nullable PresetFlatWorldScreen.PresetsList.Entry entry) {
            super.setSelected(entry);
            PresetFlatWorldScreen.this.updateButtonValidity(entry != null);
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
            if (super.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }

            if (CommonInputs.selected(keyCode) && this.getSelected() != null) {
                this.getSelected().select();
            }

            return false;
        }

        public class Entry extends ObjectSelectionList.Entry<PresetFlatWorldScreen.PresetsList.Entry> {
            private static final ResourceLocation STATS_ICON_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/container/stats_icons.png");
            private final FlatLevelGeneratorPreset preset;
            private final Component name;

            public Entry(final Holder<FlatLevelGeneratorPreset> presetHolder) {
                this.preset = presetHolder.value();
                this.name = presetHolder.unwrapKey()
                    .map(resourceKey -> Component.translatable(resourceKey.location().toLanguageKey("flat_world_preset")))
                    .orElse(PresetFlatWorldScreen.UNKNOWN_PRESET);
            }

            @Override
            public void render(
                GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick
            ) {
                this.blitSlot(guiGraphics, left, top, this.preset.displayItem().value());
                guiGraphics.drawString(PresetFlatWorldScreen.this.font, this.name, left + 18 + 5, top + 6, 16777215, false);
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
                this.select();
                return super.mouseClicked(mouseX, mouseY, button);
            }

            void select() {
                PresetsList.this.setSelected(this);
                PresetFlatWorldScreen.this.settings = this.preset.settings();
                PresetFlatWorldScreen.this.export.setValue(PresetFlatWorldScreen.save(PresetFlatWorldScreen.this.settings));
                PresetFlatWorldScreen.this.export.moveCursorToStart(false);
            }

            private void blitSlot(GuiGraphics guiGraphics, int x, int y, Item item) {
                this.blitSlotBg(guiGraphics, x + 1, y + 1);
                guiGraphics.renderFakeItem(new ItemStack(item), x + 2, y + 2);
            }

            private void blitSlotBg(GuiGraphics guiGraphics, int x, int y) {
                guiGraphics.blitSprite(PresetFlatWorldScreen.SLOT_SPRITE, x, y, 0, 18, 18);
            }

            @Override
            public Component getNarration() {
                return Component.translatable("narrator.select", this.name);
            }
        }
    }
}
