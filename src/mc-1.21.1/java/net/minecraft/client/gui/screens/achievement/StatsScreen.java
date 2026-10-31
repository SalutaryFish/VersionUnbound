package net.minecraft.client.gui.screens.achievement;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LoadingDotsWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public class StatsScreen extends Screen {
    private static final Component TITLE = Component.translatable("gui.stats");
    static final ResourceLocation SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot");
    static final ResourceLocation HEADER_SPRITE = ResourceLocation.withDefaultNamespace("statistics/header");
    static final ResourceLocation SORT_UP_SPRITE = ResourceLocation.withDefaultNamespace("statistics/sort_up");
    static final ResourceLocation SORT_DOWN_SPRITE = ResourceLocation.withDefaultNamespace("statistics/sort_down");
    private static final Component PENDING_TEXT = Component.translatable("multiplayer.downloadingStats");
    static final Component NO_VALUE_DISPLAY = Component.translatable("stats.none");
    private static final Component GENERAL_BUTTON = Component.translatable("stat.generalButton");
    private static final Component ITEMS_BUTTON = Component.translatable("stat.itemsButton");
    private static final Component MOBS_BUTTON = Component.translatable("stat.mobsButton");
    protected final Screen lastScreen;
    private static final int LIST_WIDTH = 280;
    private static final int PADDING = 5;
    private static final int FOOTER_HEIGHT = 58;
    private HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 33, 58);
    @Nullable
    private StatsScreen.GeneralStatisticsList statsList;
    @Nullable
    StatsScreen.ItemStatisticsList itemStatsList;
    @Nullable
    private StatsScreen.MobsStatisticsList mobsStatsList;
    final StatsCounter stats;
    @Nullable
    private ObjectSelectionList<?> activeList;
    /**
     * When true, the game will be paused when the gui is shown
     */
    private boolean isLoading = true;

    public StatsScreen(Screen lastScreen, StatsCounter stats) {
        super(TITLE);
        this.lastScreen = lastScreen;
        this.stats = stats;
    }

    @Override
    protected void init() {
        this.layout.addToContents(new LoadingDotsWidget(this.font, PENDING_TEXT));
        this.minecraft.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
    }

    public void initLists() {
        this.statsList = new StatsScreen.GeneralStatisticsList(this.minecraft);
        this.itemStatsList = new StatsScreen.ItemStatisticsList(this.minecraft);
        this.mobsStatsList = new StatsScreen.MobsStatisticsList(this.minecraft);
    }

    public void initButtons() {
        HeaderAndFooterLayout headerAndFooterLayout = new HeaderAndFooterLayout(this, 33, 58);
        headerAndFooterLayout.addTitleHeader(TITLE, this.font);
        LinearLayout linearLayout = headerAndFooterLayout.addToFooter(LinearLayout.vertical()).spacing(5);
        linearLayout.defaultCellSetting().alignHorizontallyCenter();
        LinearLayout linearLayout2 = linearLayout.addChild(LinearLayout.horizontal()).spacing(5);
        linearLayout2.addChild(Button.builder(GENERAL_BUTTON, buttonx -> this.setActiveList(this.statsList)).width(120).build());
        Button button = linearLayout2.addChild(Button.builder(ITEMS_BUTTON, buttonx -> this.setActiveList(this.itemStatsList)).width(120).build());
        Button button2 = linearLayout2.addChild(Button.builder(MOBS_BUTTON, buttonx -> this.setActiveList(this.mobsStatsList)).width(120).build());
        linearLayout.addChild(Button.builder(CommonComponents.GUI_DONE, buttonx -> this.onClose()).width(200).build());
        if (this.itemStatsList != null && this.itemStatsList.children().isEmpty()) {
            button.active = false;
        }

        if (this.mobsStatsList != null && this.mobsStatsList.children().isEmpty()) {
            button2.active = false;
        }

        this.layout = headerAndFooterLayout;
        this.layout.visitWidgets(guiEventListener -> {
            AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
        });
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.activeList != null) {
            this.activeList.updateSize(this.width, this.layout);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    public void onStatsUpdated() {
        if (this.isLoading) {
            this.initLists();
            this.setActiveList(this.statsList);
            this.initButtons();
            this.setInitialFocus();
            this.isLoading = false;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return !this.isLoading;
    }

    public void setActiveList(@Nullable ObjectSelectionList<?> activeList) {
        if (this.activeList != null) {
            this.removeWidget(this.activeList);
        }

        if (activeList != null) {
            this.addRenderableWidget(activeList);
            this.activeList = activeList;
            this.repositionElements();
        }
    }

    static String getTranslationKey(Stat<ResourceLocation> stat) {
        return "stat." + stat.getValue().toString().replace(':', '.');
    }

    class GeneralStatisticsList extends ObjectSelectionList<StatsScreen.GeneralStatisticsList.Entry> {
        public GeneralStatisticsList(final Minecraft minecraft) {
            super(minecraft, StatsScreen.this.width, StatsScreen.this.height - 33 - 58, 33, 14);
            ObjectArrayList<Stat<ResourceLocation>> objectArrayList = new ObjectArrayList<>(Stats.CUSTOM.iterator());
            objectArrayList.sort(Comparator.comparing(statx -> I18n.get(StatsScreen.getTranslationKey(statx))));

            for (Stat<ResourceLocation> stat : objectArrayList) {
                this.addEntry(new StatsScreen.GeneralStatisticsList.Entry(stat));
            }
        }

        @Override
        public int getRowWidth() {
            return 280;
        }

        class Entry extends ObjectSelectionList.Entry<StatsScreen.GeneralStatisticsList.Entry> {
            private final Stat<ResourceLocation> stat;
            private final Component statDisplay;

            Entry(final Stat<ResourceLocation> stat) {
                this.stat = stat;
                this.statDisplay = Component.translatable(StatsScreen.getTranslationKey(stat));
            }

            private String getValueText() {
                return this.stat.format(StatsScreen.this.stats.getValue(this.stat));
            }

            @Override
            public void render(
                GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick
            ) {
                int i = top + height / 2 - 9 / 2;
                int j = index % 2 == 0 ? -1 : -4539718;
                guiGraphics.drawString(StatsScreen.this.font, this.statDisplay, left + 2, i, j);
                String string = this.getValueText();
                guiGraphics.drawString(StatsScreen.this.font, string, left + width - StatsScreen.this.font.width(string) - 4, i, j);
            }

            @Override
            public Component getNarration() {
                return Component.translatable(
                    "narrator.select", Component.empty().append(this.statDisplay).append(CommonComponents.SPACE).append(this.getValueText())
                );
            }
        }
    }

    class ItemStatisticsList extends ObjectSelectionList<StatsScreen.ItemStatisticsList.ItemRow> {
        private static final int SLOT_BG_SIZE = 18;
        private static final int SLOT_STAT_HEIGHT = 22;
        private static final int SLOT_BG_Y = 1;
        private static final int SORT_NONE = 0;
        private static final int SORT_DOWN = -1;
        private static final int SORT_UP = 1;
        private final ResourceLocation[] iconSprites = new ResourceLocation[]{
            ResourceLocation.withDefaultNamespace("statistics/block_mined"),
            ResourceLocation.withDefaultNamespace("statistics/item_broken"),
            ResourceLocation.withDefaultNamespace("statistics/item_crafted"),
            ResourceLocation.withDefaultNamespace("statistics/item_used"),
            ResourceLocation.withDefaultNamespace("statistics/item_picked_up"),
            ResourceLocation.withDefaultNamespace("statistics/item_dropped")
        };
        protected final List<StatType<Block>> blockColumns;
        protected final List<StatType<Item>> itemColumns;
        protected final Comparator<StatsScreen.ItemStatisticsList.ItemRow> itemStatSorter = new StatsScreen.ItemStatisticsList.ItemRowComparator();
        @Nullable
        protected StatType<?> sortColumn;
        protected int headerPressed = -1;
        protected int sortOrder;

        public ItemStatisticsList(final Minecraft minecraft) {
            super(minecraft, StatsScreen.this.width, StatsScreen.this.height - 33 - 58, 33, 22);
            this.blockColumns = Lists.newArrayList();
            this.blockColumns.add(Stats.BLOCK_MINED);
            this.itemColumns = Lists.newArrayList(Stats.ITEM_BROKEN, Stats.ITEM_CRAFTED, Stats.ITEM_USED, Stats.ITEM_PICKED_UP, Stats.ITEM_DROPPED);
            this.setRenderHeader(true, 22);
            Set<Item> set = Sets.newIdentityHashSet();

            for (Item item : BuiltInRegistries.ITEM) {
                boolean bl = false;

                for (StatType<Item> statType : this.itemColumns) {
                    if (statType.contains(item) && StatsScreen.this.stats.getValue(statType.get(item)) > 0) {
                        bl = true;
                    }
                }

                if (bl) {
                    set.add(item);
                }
            }

            for (Block block : BuiltInRegistries.BLOCK) {
                boolean bl = false;

                for (StatType<Block> statType : this.blockColumns) {
                    if (statType.contains(block) && StatsScreen.this.stats.getValue(statType.get(block)) > 0) {
                        bl = true;
                    }
                }

                if (bl) {
                    set.add(block.asItem());
                }
            }

            set.remove(Items.AIR);

            for (Item item : set) {
                this.addEntry(new StatsScreen.ItemStatisticsList.ItemRow(item));
            }
        }

        int getColumnX(int index) {
            return 75 + 40 * index;
        }

        @Override
        protected void renderHeader(GuiGraphics guiGraphics, int x, int y) {
            if (!this.minecraft.mouseHandler.isLeftPressed()) {
                this.headerPressed = -1;
            }

            for (int i = 0; i < this.iconSprites.length; i++) {
                ResourceLocation resourceLocation = this.headerPressed == i ? StatsScreen.SLOT_SPRITE : StatsScreen.HEADER_SPRITE;
                guiGraphics.blitSprite(resourceLocation, x + this.getColumnX(i) - 18, y + 1, 0, 18, 18);
            }

            if (this.sortColumn != null) {
                int i = this.getColumnX(this.getColumnIndex(this.sortColumn)) - 36;
                ResourceLocation resourceLocation = this.sortOrder == 1 ? StatsScreen.SORT_UP_SPRITE : StatsScreen.SORT_DOWN_SPRITE;
                guiGraphics.blitSprite(resourceLocation, x + i, y + 1, 0, 18, 18);
            }

            for (int i = 0; i < this.iconSprites.length; i++) {
                int j = this.headerPressed == i ? 1 : 0;
                guiGraphics.blitSprite(this.iconSprites[i], x + this.getColumnX(i) - 18 + j, y + 1 + j, 0, 18, 18);
            }
        }

        @Override
        public int getRowWidth() {
            return 280;
        }

        @Override
        protected boolean clickedHeader(int x, int y) {
            this.headerPressed = -1;

            for (int i = 0; i < this.iconSprites.length; i++) {
                int j = x - this.getColumnX(i);
                if (j >= -36 && j <= 0) {
                    this.headerPressed = i;
                    break;
                }
            }

            if (this.headerPressed >= 0) {
                this.sortByColumn(this.getColumn(this.headerPressed));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            } else {
                return super.clickedHeader(x, y);
            }
        }

        private StatType<?> getColumn(int index) {
            return index < this.blockColumns.size() ? this.blockColumns.get(index) : this.itemColumns.get(index - this.blockColumns.size());
        }

        private int getColumnIndex(StatType<?> statType) {
            int i = this.blockColumns.indexOf(statType);
            if (i >= 0) {
                return i;
            }

            int j = this.itemColumns.indexOf(statType);
            return j >= 0 ? j + this.blockColumns.size() : -1;
        }

        @Override
        protected void renderDecorations(GuiGraphics guiGraphics, int mouseX, int mouseY) {
            if (mouseY >= this.getY() && mouseY <= this.getBottom()) {
                StatsScreen.ItemStatisticsList.ItemRow itemRow = this.getHovered();
                int i = this.getRowLeft();
                if (itemRow != null) {
                    if (mouseX < i || mouseX > i + 18) {
                        return;
                    }

                    Item item = itemRow.getItem();
                    guiGraphics.renderTooltip(StatsScreen.this.font, item.getDescription(), mouseX, mouseY);
                } else {
                    Component component = null;
                    int j = mouseX - i;

                    for (int k = 0; k < this.iconSprites.length; k++) {
                        int l = this.getColumnX(k);
                        if (j >= l - 18 && j <= l) {
                            component = this.getColumn(k).getDisplayName();
                            break;
                        }
                    }

                    if (component != null) {
                        guiGraphics.renderTooltip(StatsScreen.this.font, component, mouseX, mouseY);
                    }
                }
            }
        }

        protected void sortByColumn(StatType<?> statType) {
            if (statType != this.sortColumn) {
                this.sortColumn = statType;
                this.sortOrder = -1;
            } else if (this.sortOrder == -1) {
                this.sortOrder = 1;
            } else {
                this.sortColumn = null;
                this.sortOrder = 0;
            }

            this.children().sort(this.itemStatSorter);
        }

        class ItemRow extends ObjectSelectionList.Entry<StatsScreen.ItemStatisticsList.ItemRow> {
            private final Item item;

            ItemRow(final Item item) {
                this.item = item;
            }

            public Item getItem() {
                return this.item;
            }

            @Override
            public void render(
                GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick
            ) {
                guiGraphics.blitSprite(StatsScreen.SLOT_SPRITE, left, top, 0, 18, 18);
                guiGraphics.renderFakeItem(this.item.getDefaultInstance(), left + 1, top + 1);
                if (StatsScreen.this.itemStatsList != null) {
                    for (int i = 0; i < StatsScreen.this.itemStatsList.blockColumns.size(); i++) {
                        Stat<Block> stat;
                        if (this.item instanceof BlockItem blockItem) {
                            stat = StatsScreen.this.itemStatsList.blockColumns.get(i).get(blockItem.getBlock());
                        } else {
                            stat = null;
                        }

                        this.renderStat(guiGraphics, stat, left + ItemStatisticsList.this.getColumnX(i), top + height / 2 - 9 / 2, index % 2 == 0);
                    }

                    for (int i = 0; i < StatsScreen.this.itemStatsList.itemColumns.size(); i++) {
                        this.renderStat(
                            guiGraphics,
                            StatsScreen.this.itemStatsList.itemColumns.get(i).get(this.item),
                            left + ItemStatisticsList.this.getColumnX(i + StatsScreen.this.itemStatsList.blockColumns.size()),
                            top + height / 2 - 9 / 2,
                            index % 2 == 0
                        );
                    }
                }
            }

            protected void renderStat(GuiGraphics guiGraphics, @Nullable Stat<?> stat, int x, int y, boolean evenRow) {
                Component component = stat == null ? StatsScreen.NO_VALUE_DISPLAY : Component.literal(stat.format(StatsScreen.this.stats.getValue(stat)));
                guiGraphics.drawString(StatsScreen.this.font, component, x - StatsScreen.this.font.width(component), y, evenRow ? -1 : -4539718);
            }

            @Override
            public Component getNarration() {
                return Component.translatable("narrator.select", this.item.getDescription());
            }
        }

        class ItemRowComparator implements Comparator<StatsScreen.ItemStatisticsList.ItemRow> {
            public int compare(StatsScreen.ItemStatisticsList.ItemRow row1, StatsScreen.ItemStatisticsList.ItemRow row2) {
                Item item = row1.getItem();
                Item item2 = row2.getItem();
                int i;
                int j;
                if (ItemStatisticsList.this.sortColumn == null) {
                    i = 0;
                    j = 0;
                } else if (ItemStatisticsList.this.blockColumns.contains(ItemStatisticsList.this.sortColumn)) {
                    StatType<Block> statType = (StatType<Block>)ItemStatisticsList.this.sortColumn;
                    i = item instanceof BlockItem ? StatsScreen.this.stats.getValue(statType, ((BlockItem)item).getBlock()) : -1;
                    j = item2 instanceof BlockItem ? StatsScreen.this.stats.getValue(statType, ((BlockItem)item2).getBlock()) : -1;
                } else {
                    StatType<Item> statType = (StatType<Item>)ItemStatisticsList.this.sortColumn;
                    i = StatsScreen.this.stats.getValue(statType, item);
                    j = StatsScreen.this.stats.getValue(statType, item2);
                }

                return i == j
                    ? ItemStatisticsList.this.sortOrder * Integer.compare(Item.getId(item), Item.getId(item2))
                    : ItemStatisticsList.this.sortOrder * Integer.compare(i, j);
            }
        }
    }

    class MobsStatisticsList extends ObjectSelectionList<StatsScreen.MobsStatisticsList.MobRow> {
        public MobsStatisticsList(final Minecraft minecraft) {
            super(minecraft, StatsScreen.this.width, StatsScreen.this.height - 33 - 58, 33, 9 * 4);

            for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
                if (StatsScreen.this.stats.getValue(Stats.ENTITY_KILLED.get(entityType)) > 0
                    || StatsScreen.this.stats.getValue(Stats.ENTITY_KILLED_BY.get(entityType)) > 0) {
                    this.addEntry(new StatsScreen.MobsStatisticsList.MobRow(entityType));
                }
            }
        }

        @Override
        public int getRowWidth() {
            return 280;
        }

        class MobRow extends ObjectSelectionList.Entry<StatsScreen.MobsStatisticsList.MobRow> {
            private final Component mobName;
            private final Component kills;
            private final Component killedBy;
            private final boolean hasKills;
            private final boolean wasKilledBy;

            public MobRow(final EntityType<?> entityType) {
                this.mobName = entityType.getDescription();
                int i = StatsScreen.this.stats.getValue(Stats.ENTITY_KILLED.get(entityType));
                if (i == 0) {
                    this.kills = Component.translatable("stat_type.minecraft.killed.none", this.mobName);
                    this.hasKills = false;
                } else {
                    this.kills = Component.translatable("stat_type.minecraft.killed", i, this.mobName);
                    this.hasKills = true;
                }

                int j = StatsScreen.this.stats.getValue(Stats.ENTITY_KILLED_BY.get(entityType));
                if (j == 0) {
                    this.killedBy = Component.translatable("stat_type.minecraft.killed_by.none", this.mobName);
                    this.wasKilledBy = false;
                } else {
                    this.killedBy = Component.translatable("stat_type.minecraft.killed_by", this.mobName, j);
                    this.wasKilledBy = true;
                }
            }

            @Override
            public void render(
                GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick
            ) {
                guiGraphics.drawString(StatsScreen.this.font, this.mobName, left + 2, top + 1, -1);
                guiGraphics.drawString(StatsScreen.this.font, this.kills, left + 2 + 10, top + 1 + 9, this.hasKills ? -4539718 : -8355712);
                guiGraphics.drawString(StatsScreen.this.font, this.killedBy, left + 2 + 10, top + 1 + 9 * 2, this.wasKilledBy ? -4539718 : -8355712);
            }

            @Override
            public Component getNarration() {
                return Component.translatable("narrator.select", CommonComponents.joinForNarration(this.kills, this.killedBy));
            }
        }
    }
}
