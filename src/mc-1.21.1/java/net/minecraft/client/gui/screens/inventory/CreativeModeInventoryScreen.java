package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Pair;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.HotbarManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.inventory.Hotbar;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

public class CreativeModeInventoryScreen extends EffectRenderingInventoryScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private static final ResourceLocation[] UNSELECTED_TOP_TABS = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_1"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_2"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_3"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_4"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_5"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_6"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_7")
    };
    private static final ResourceLocation[] SELECTED_TOP_TABS = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_1"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_2"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_3"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_4"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_5"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_6"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_7")
    };
    private static final ResourceLocation[] UNSELECTED_BOTTOM_TABS = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_1"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_2"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_3"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_4"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_5"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_6"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_7")
    };
    private static final ResourceLocation[] SELECTED_BOTTOM_TABS = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_1"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_2"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_3"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_4"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_5"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_6"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_7")
    };
    private static final int NUM_ROWS = 5;
    private static final int NUM_COLS = 9;
    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;
    private static final int SCROLLER_WIDTH = 12;
    private static final int SCROLLER_HEIGHT = 15;
    static final SimpleContainer CONTAINER = new SimpleContainer(45);
    private static final Component TRASH_SLOT_TOOLTIP = Component.translatable("inventory.binSlot");
    private static final int TEXT_COLOR = 16777215;
    /**
     * Currently selected creative inventory tab index.
     */
    private static CreativeModeTab selectedTab = CreativeModeTabs.getDefaultTab();
    /**
     * Amount scrolled in Creative mode inventory (0 = top, 1 = bottom)
     */
    private float scrollOffs;
    /**
     * True if the scrollbar is being dragged
     */
    private boolean scrolling;
    private EditBox searchBox;
    @Nullable
    private List<Slot> originalSlots;
    @Nullable
    private Slot destroyItemSlot;
    private CreativeInventoryListener listener;
    private boolean ignoreTextInput;
    private boolean hasClickedOutside;
    private final Set<TagKey<Item>> visibleTags = new HashSet<>();
    private final boolean displayOperatorCreativeTab;

    public CreativeModeInventoryScreen(LocalPlayer player, FeatureFlagSet enabledFeatures, boolean displayOperatorCreativeTab) {
        super(new CreativeModeInventoryScreen.ItemPickerMenu(player), player.getInventory(), CommonComponents.EMPTY);
        player.containerMenu = this.menu;
        this.imageHeight = 136;
        this.imageWidth = 195;
        this.displayOperatorCreativeTab = displayOperatorCreativeTab;
        this.tryRebuildTabContents(player.connection.searchTrees(), enabledFeatures, this.hasPermissions(player), player.level().registryAccess());
    }

    private boolean hasPermissions(Player player) {
        return player.canUseGameMasterBlocks() && this.displayOperatorCreativeTab;
    }

    private void tryRefreshInvalidatedTabs(FeatureFlagSet enabledFeatures, boolean hasPermissions, HolderLookup.Provider provider) {
        ClientPacketListener clientPacketListener = this.minecraft.getConnection();
        if (this.tryRebuildTabContents(clientPacketListener != null ? clientPacketListener.searchTrees() : null, enabledFeatures, hasPermissions, provider)) {
            for (CreativeModeTab creativeModeTab : CreativeModeTabs.allTabs()) {
                Collection<ItemStack> collection = creativeModeTab.getDisplayItems();
                if (creativeModeTab == selectedTab) {
                    if (creativeModeTab.getType() == CreativeModeTab.Type.CATEGORY && collection.isEmpty()) {
                        this.selectTab(CreativeModeTabs.getDefaultTab());
                    } else {
                        this.refreshCurrentTabContents(collection);
                    }
                }
            }
        }
    }

    private boolean tryRebuildTabContents(
        @Nullable SessionSearchTrees searchTrees, FeatureFlagSet enabledFeatures, boolean hasPermissions, HolderLookup.Provider registries
    ) {
        if (!CreativeModeTabs.tryRebuildTabContents(enabledFeatures, hasPermissions, registries)) {
            return false;
        }

        if (searchTrees != null) {
            List<ItemStack> list = List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
            searchTrees.updateCreativeTooltips(registries, list);
            searchTrees.updateCreativeTags(list);
        }

        return true;
    }

    private void refreshCurrentTabContents(Collection<ItemStack> items) {
        int i = this.menu.getRowIndexForScroll(this.scrollOffs);
        this.menu.items.clear();
        if (selectedTab.getType() == CreativeModeTab.Type.SEARCH) {
            this.refreshSearchResults();
        } else {
            this.menu.items.addAll(items);
        }

        this.scrollOffs = this.menu.getScrollForRowIndex(i);
        this.menu.scrollTo(this.scrollOffs);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.minecraft != null) {
            if (this.minecraft.player != null) {
                this.tryRefreshInvalidatedTabs(
                    this.minecraft.player.connection.enabledFeatures(),
                    this.hasPermissions(this.minecraft.player),
                    this.minecraft.player.level().registryAccess()
                );
            }

            if (!this.minecraft.gameMode.hasInfiniteItems()) {
                this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
            }
        }
    }

    /**
     * Called when the mouse is clicked over a slot or outside the gui.
     */
    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType type) {
        if (this.isCreativeSlot(slot)) {
            this.searchBox.moveCursorToEnd(false);
            this.searchBox.setHighlightPos(0);
        }

        boolean bl = type == ClickType.QUICK_MOVE;
        type = slotId == -999 && type == ClickType.PICKUP ? ClickType.THROW : type;
        if (slot == null && selectedTab.getType() != CreativeModeTab.Type.INVENTORY && type != ClickType.QUICK_CRAFT) {
            if (!this.menu.getCarried().isEmpty() && this.hasClickedOutside) {
                if (mouseButton == 0) {
                    this.minecraft.player.drop(this.menu.getCarried(), true);
                    this.minecraft.gameMode.handleCreativeModeItemDrop(this.menu.getCarried());
                    this.menu.setCarried(ItemStack.EMPTY);
                }

                if (mouseButton == 1) {
                    ItemStack itemStack = this.menu.getCarried().split(1);
                    this.minecraft.player.drop(itemStack, true);
                    this.minecraft.gameMode.handleCreativeModeItemDrop(itemStack);
                }
            }
        } else {
            if (slot != null && !slot.mayPickup(this.minecraft.player)) {
                return;
            }

            if (slot == this.destroyItemSlot && bl) {
                for (int i = 0; i < this.minecraft.player.inventoryMenu.getItems().size(); i++) {
                    this.minecraft.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, i);
                }
            } else if (selectedTab.getType() == CreativeModeTab.Type.INVENTORY) {
                if (slot == this.destroyItemSlot) {
                    this.menu.setCarried(ItemStack.EMPTY);
                } else if (type == ClickType.THROW && slot != null && slot.hasItem()) {
                    ItemStack itemStack = slot.remove(mouseButton == 0 ? 1 : slot.getItem().getMaxStackSize());
                    ItemStack itemStack2 = slot.getItem();
                    this.minecraft.player.drop(itemStack, true);
                    this.minecraft.gameMode.handleCreativeModeItemDrop(itemStack);
                    this.minecraft.gameMode.handleCreativeModeItemAdd(itemStack2, ((CreativeModeInventoryScreen.SlotWrapper)slot).target.index);
                } else if (type == ClickType.THROW && !this.menu.getCarried().isEmpty()) {
                    this.minecraft.player.drop(this.menu.getCarried(), true);
                    this.minecraft.gameMode.handleCreativeModeItemDrop(this.menu.getCarried());
                    this.menu.setCarried(ItemStack.EMPTY);
                } else {
                    this.minecraft
                        .player
                        .inventoryMenu
                        .clicked(slot == null ? slotId : ((CreativeModeInventoryScreen.SlotWrapper)slot).target.index, mouseButton, type, this.minecraft.player);
                    this.minecraft.player.inventoryMenu.broadcastChanges();
                }
            } else if (type != ClickType.QUICK_CRAFT && slot.container == CONTAINER) {
                ItemStack itemStack = this.menu.getCarried();
                ItemStack itemStack2 = slot.getItem();
                if (type == ClickType.SWAP) {
                    if (!itemStack2.isEmpty()) {
                        this.minecraft.player.getInventory().setItem(mouseButton, itemStack2.copyWithCount(itemStack2.getMaxStackSize()));
                        this.minecraft.player.inventoryMenu.broadcastChanges();
                    }

                    return;
                }

                if (type == ClickType.CLONE) {
                    if (this.menu.getCarried().isEmpty() && slot.hasItem()) {
                        ItemStack itemStack3 = slot.getItem();
                        this.menu.setCarried(itemStack3.copyWithCount(itemStack3.getMaxStackSize()));
                    }

                    return;
                }

                if (type == ClickType.THROW) {
                    if (!itemStack2.isEmpty()) {
                        ItemStack itemStack3 = itemStack2.copyWithCount(mouseButton == 0 ? 1 : itemStack2.getMaxStackSize());
                        this.minecraft.player.drop(itemStack3, true);
                        this.minecraft.gameMode.handleCreativeModeItemDrop(itemStack3);
                    }

                    return;
                }

                if (!itemStack.isEmpty() && !itemStack2.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, itemStack2)) {
                    if (mouseButton == 0) {
                        if (bl) {
                            itemStack.setCount(itemStack.getMaxStackSize());
                        } else if (itemStack.getCount() < itemStack.getMaxStackSize()) {
                            itemStack.grow(1);
                        }
                    } else {
                        itemStack.shrink(1);
                    }
                } else if (!itemStack2.isEmpty() && itemStack.isEmpty()) {
                    int j = bl ? itemStack2.getMaxStackSize() : itemStack2.getCount();
                    this.menu.setCarried(itemStack2.copyWithCount(j));
                } else if (mouseButton == 0) {
                    this.menu.setCarried(ItemStack.EMPTY);
                } else if (!this.menu.getCarried().isEmpty()) {
                    this.menu.getCarried().shrink(1);
                }
            } else if (this.menu != null) {
                ItemStack itemStack = slot == null ? ItemStack.EMPTY : this.menu.getSlot(slot.index).getItem();
                this.menu.clicked(slot == null ? slotId : slot.index, mouseButton, type, this.minecraft.player);
                if (AbstractContainerMenu.getQuickcraftHeader(mouseButton) == 2) {
                    for (int k = 0; k < 9; k++) {
                        this.minecraft.gameMode.handleCreativeModeItemAdd(this.menu.getSlot(45 + k).getItem(), 36 + k);
                    }
                } else if (slot != null) {
                    ItemStack itemStack2 = this.menu.getSlot(slot.index).getItem();
                    this.minecraft.gameMode.handleCreativeModeItemAdd(itemStack2, slot.index - this.menu.slots.size() + 9 + 36);
                    int j = 45 + mouseButton;
                    if (type == ClickType.SWAP) {
                        this.minecraft.gameMode.handleCreativeModeItemAdd(itemStack, j - this.menu.slots.size() + 9 + 36);
                    } else if (type == ClickType.THROW && !itemStack.isEmpty()) {
                        ItemStack itemStack4 = itemStack.copyWithCount(mouseButton == 0 ? 1 : itemStack.getMaxStackSize());
                        this.minecraft.player.drop(itemStack4, true);
                        this.minecraft.gameMode.handleCreativeModeItemDrop(itemStack4);
                    }

                    this.minecraft.player.inventoryMenu.broadcastChanges();
                }
            }
        }
    }

    private boolean isCreativeSlot(@Nullable Slot slot) {
        return slot != null && slot.container == CONTAINER;
    }

    @Override
    protected void init() {
        if (this.minecraft.gameMode.hasInfiniteItems()) {
            super.init();
            this.searchBox = new EditBox(this.font, this.leftPos + 82, this.topPos + 6, 80, 9, Component.translatable("itemGroup.search"));
            this.searchBox.setMaxLength(50);
            this.searchBox.setBordered(false);
            this.searchBox.setVisible(false);
            this.searchBox.setTextColor(16777215);
            this.addWidget(this.searchBox);
            CreativeModeTab creativeModeTab = selectedTab;
            selectedTab = CreativeModeTabs.getDefaultTab();
            this.selectTab(creativeModeTab);
            this.minecraft.player.inventoryMenu.removeSlotListener(this.listener);
            this.listener = new CreativeInventoryListener(this.minecraft);
            this.minecraft.player.inventoryMenu.addSlotListener(this.listener);
            if (!selectedTab.shouldDisplay()) {
                this.selectTab(CreativeModeTabs.getDefaultTab());
            }
        } else {
            this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        int i = this.menu.getRowIndexForScroll(this.scrollOffs);
        String string = this.searchBox.getValue();
        this.init(minecraft, width, height);
        this.searchBox.setValue(string);
        if (!this.searchBox.getValue().isEmpty()) {
            this.refreshSearchResults();
        }

        this.scrollOffs = this.menu.getScrollForRowIndex(i);
        this.menu.scrollTo(this.scrollOffs);
    }

    @Override
    public void removed() {
        super.removed();
        if (this.minecraft.player != null && this.minecraft.player.getInventory() != null) {
            this.minecraft.player.inventoryMenu.removeSlotListener(this.listener);
        }
    }

    /**
     * Called when a character is typed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param codePoint the code point of the typed character.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.ignoreTextInput) {
            return false;
        }

        if (selectedTab.getType() != CreativeModeTab.Type.SEARCH) {
            return false;
        }

        String string = this.searchBox.getValue();
        if (this.searchBox.charTyped(codePoint, modifiers)) {
            if (!Objects.equals(string, this.searchBox.getValue())) {
                this.refreshSearchResults();
            }

            return true;
        } else {
            return false;
        }
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
        this.ignoreTextInput = false;
        if (selectedTab.getType() != CreativeModeTab.Type.SEARCH) {
            if (this.minecraft.options.keyChat.matches(keyCode, scanCode)) {
                this.ignoreTextInput = true;
                this.selectTab(CreativeModeTabs.searchTab());
                return true;
            } else {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        } else {
            boolean bl = !this.isCreativeSlot(this.hoveredSlot) || this.hoveredSlot.hasItem();
            boolean bl2 = InputConstants.getKey(keyCode, scanCode).getNumericKeyValue().isPresent();
            if (bl && bl2 && this.checkHotbarKeyPressed(keyCode, scanCode)) {
                this.ignoreTextInput = true;
                return true;
            }

            String string = this.searchBox.getValue();
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                if (!Objects.equals(string, this.searchBox.getValue())) {
                    this.refreshSearchResults();
                }

                return true;
            } else {
                return this.searchBox.isFocused() && this.searchBox.isVisible() && keyCode != 256 ? true : super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    /**
     * Called when a keyboard key is released within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param keyCode   the key code of the released key.
     * @param scanCode  the scan code of the released key.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        this.ignoreTextInput = false;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void refreshSearchResults() {
        this.menu.items.clear();
        this.visibleTags.clear();
        String string = this.searchBox.getValue();
        if (string.isEmpty()) {
            this.menu.items.addAll(selectedTab.getDisplayItems());
        } else {
            ClientPacketListener clientPacketListener = this.minecraft.getConnection();
            if (clientPacketListener != null) {
                SessionSearchTrees sessionSearchTrees = clientPacketListener.searchTrees();
                SearchTree<ItemStack> searchTree;
                if (string.startsWith("#")) {
                    string = string.substring(1);
                    searchTree = sessionSearchTrees.creativeTagSearch();
                    this.updateVisibleTags(string);
                } else {
                    searchTree = sessionSearchTrees.creativeNameSearch();
                }

                this.menu.items.addAll(searchTree.search(string.toLowerCase(Locale.ROOT)));
            }
        }

        this.scrollOffs = 0.0F;
        this.menu.scrollTo(0.0F);
    }

    private void updateVisibleTags(String search) {
        int i = search.indexOf(58);
        Predicate<ResourceLocation> predicate;
        if (i == -1) {
            predicate = resourceLocation -> resourceLocation.getPath().contains(search);
        } else {
            String string = search.substring(0, i).trim();
            String string2 = search.substring(i + 1).trim();
            predicate = resourceLocation -> resourceLocation.getNamespace().contains(string) && resourceLocation.getPath().contains(string2);
        }

        BuiltInRegistries.ITEM.getTagNames().filter(tagKey -> predicate.test(tagKey.location())).forEach(this.visibleTags::add);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (selectedTab.showTitle()) {
            guiGraphics.drawString(this.font, selectedTab.getDisplayName(), 8, 6, 4210752, false);
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
            double d = mouseX - this.leftPos;
            double e = mouseY - this.topPos;

            for (CreativeModeTab creativeModeTab : CreativeModeTabs.tabs()) {
                if (this.checkTabClicked(creativeModeTab, d, e)) {
                    return true;
                }
            }

            if (selectedTab.getType() != CreativeModeTab.Type.INVENTORY && this.insideScrollbar(mouseX, mouseY)) {
                this.scrolling = this.canScroll();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Called when a mouse button is released within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that was released.
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double d = mouseX - this.leftPos;
            double e = mouseY - this.topPos;
            this.scrolling = false;

            for (CreativeModeTab creativeModeTab : CreativeModeTabs.tabs()) {
                if (this.checkTabClicked(creativeModeTab, d, e)) {
                    this.selectTab(creativeModeTab);
                    return true;
                }
            }
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean canScroll() {
        return selectedTab.canScroll() && this.menu.canScroll();
    }

    /**
     * Sets the current creative tab, restructuring the GUI as needed.
     */
    private void selectTab(CreativeModeTab tab) {
        CreativeModeTab creativeModeTab = selectedTab;
        selectedTab = tab;
        this.quickCraftSlots.clear();
        this.menu.items.clear();
        this.clearDraggingState();
        if (selectedTab.getType() == CreativeModeTab.Type.HOTBAR) {
            HotbarManager hotbarManager = this.minecraft.getHotbarManager();

            for (int i = 0; i < 9; i++) {
                Hotbar hotbar = hotbarManager.get(i);
                if (hotbar.isEmpty()) {
                    for (int j = 0; j < 9; j++) {
                        if (j == i) {
                            ItemStack itemStack = new ItemStack(Items.PAPER);
                            itemStack.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);
                            Component component = this.minecraft.options.keyHotbarSlots[i].getTranslatedKeyMessage();
                            Component component2 = this.minecraft.options.keySaveHotbarActivator.getTranslatedKeyMessage();
                            itemStack.set(DataComponents.ITEM_NAME, Component.translatable("inventory.hotbarInfo", component2, component));
                            this.menu.items.add(itemStack);
                        } else {
                            this.menu.items.add(ItemStack.EMPTY);
                        }
                    }
                } else {
                    this.menu.items.addAll(hotbar.load(this.minecraft.level.registryAccess()));
                }
            }
        } else if (selectedTab.getType() == CreativeModeTab.Type.CATEGORY) {
            this.menu.items.addAll(selectedTab.getDisplayItems());
        }

        if (selectedTab.getType() == CreativeModeTab.Type.INVENTORY) {
            AbstractContainerMenu abstractContainerMenu = this.minecraft.player.inventoryMenu;
            if (this.originalSlots == null) {
                this.originalSlots = ImmutableList.copyOf(this.menu.slots);
            }

            this.menu.slots.clear();

            for (int i = 0; i < abstractContainerMenu.slots.size(); i++) {
                int n;
                int j;
                if (i >= 5 && i < 9) {
                    int k = i - 5;
                    int l = k / 2;
                    int m = k % 2;
                    n = 54 + l * 54;
                    j = 6 + m * 27;
                } else if (i >= 0 && i < 5) {
                    n = -2000;
                    j = -2000;
                } else if (i == 45) {
                    n = 35;
                    j = 20;
                } else {
                    int k = i - 9;
                    int l = k % 9;
                    int m = k / 9;
                    n = 9 + l * 18;
                    if (i >= 36) {
                        j = 112;
                    } else {
                        j = 54 + m * 18;
                    }
                }

                Slot slot = new CreativeModeInventoryScreen.SlotWrapper(abstractContainerMenu.slots.get(i), i, n, j);
                this.menu.slots.add(slot);
            }

            this.destroyItemSlot = new Slot(CONTAINER, 0, 173, 112);
            this.menu.slots.add(this.destroyItemSlot);
        } else if (creativeModeTab.getType() == CreativeModeTab.Type.INVENTORY) {
            this.menu.slots.clear();
            this.menu.slots.addAll(this.originalSlots);
            this.originalSlots = null;
        }

        if (selectedTab.getType() == CreativeModeTab.Type.SEARCH) {
            this.searchBox.setVisible(true);
            this.searchBox.setCanLoseFocus(false);
            this.searchBox.setFocused(true);
            if (creativeModeTab != tab) {
                this.searchBox.setValue("");
            }

            this.refreshSearchResults();
        } else {
            this.searchBox.setVisible(false);
            this.searchBox.setCanLoseFocus(true);
            this.searchBox.setFocused(false);
            this.searchBox.setValue("");
        }

        this.scrollOffs = 0.0F;
        this.menu.scrollTo(0.0F);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.canScroll()) {
            return false;
        }

        this.scrollOffs = this.menu.subtractInputFromScroll(this.scrollOffs, scrollY);
        this.menu.scrollTo(this.scrollOffs);
        return true;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        boolean bl = mouseX < guiLeft || mouseY < guiTop || mouseX >= guiLeft + this.imageWidth || mouseY >= guiTop + this.imageHeight;
        this.hasClickedOutside = bl && !this.checkTabClicked(selectedTab, mouseX, mouseY);
        return this.hasClickedOutside;
    }

    protected boolean insideScrollbar(double mouseX, double mouseY) {
        int i = this.leftPos;
        int j = this.topPos;
        int k = i + 175;
        int l = j + 18;
        int m = k + 14;
        int n = l + 112;
        return mouseX >= k && mouseY >= l && mouseX < m && mouseY < n;
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
        if (this.scrolling) {
            int i = this.topPos + 18;
            int j = i + 112;
            this.scrollOffs = ((float)mouseY - i - 7.5F) / (j - i - 15.0F);
            this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
            this.menu.scrollTo(this.scrollOffs);
            return true;
        } else {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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

        for (CreativeModeTab creativeModeTab : CreativeModeTabs.tabs()) {
            if (this.checkTabHovering(guiGraphics, creativeModeTab, mouseX, mouseY)) {
                break;
            }
        }

        if (this.destroyItemSlot != null
            && selectedTab.getType() == CreativeModeTab.Type.INVENTORY
            && this.isHovering(this.destroyItemSlot.x, this.destroyItemSlot.y, 16, 16, mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, TRASH_SLOT_TOOLTIP, mouseX, mouseY);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public List<Component> getTooltipFromContainerItem(ItemStack stack) {
        boolean bl = this.hoveredSlot != null && this.hoveredSlot instanceof CreativeModeInventoryScreen.CustomCreativeSlot;
        boolean bl2 = selectedTab.getType() == CreativeModeTab.Type.CATEGORY;
        boolean bl3 = selectedTab.getType() == CreativeModeTab.Type.SEARCH;
        TooltipFlag.Default default_ = this.minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
        TooltipFlag tooltipFlag = bl ? default_.asCreative() : default_;
        List<Component> list = stack.getTooltipLines(Item.TooltipContext.of(this.minecraft.level), this.minecraft.player, tooltipFlag);
        if (bl2 && bl) {
            return list;
        }

        List<Component> list2 = Lists.newArrayList(list);
        if (bl3 && bl) {
            this.visibleTags.forEach(tagKey -> {
                if (stack.is((TagKey<Item>)tagKey)) {
                    list2.add(1, Component.literal("#" + tagKey.location()).withStyle(ChatFormatting.DARK_PURPLE));
                }
            });
        }

        int i = 1;

        for (CreativeModeTab creativeModeTab : CreativeModeTabs.tabs()) {
            if (creativeModeTab.getType() != CreativeModeTab.Type.SEARCH && creativeModeTab.contains(stack)) {
                list2.add(i++, creativeModeTab.getDisplayName().copy().withStyle(ChatFormatting.BLUE));
            }
        }

        return list2;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        for (CreativeModeTab creativeModeTab : CreativeModeTabs.tabs()) {
            if (creativeModeTab != selectedTab) {
                this.renderTabButton(guiGraphics, creativeModeTab);
            }
        }

        guiGraphics.blit(selectedTab.getBackgroundTexture(), this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        this.searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        int i = this.leftPos + 175;
        int j = this.topPos + 18;
        int k = j + 112;
        if (selectedTab.canScroll()) {
            ResourceLocation resourceLocation = this.canScroll() ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
            guiGraphics.blitSprite(resourceLocation, i, j + (int)((k - j - 17) * this.scrollOffs), 12, 15);
        }

        this.renderTabButton(guiGraphics, selectedTab);
        if (selectedTab.getType() == CreativeModeTab.Type.INVENTORY) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics, this.leftPos + 73, this.topPos + 6, this.leftPos + 105, this.topPos + 49, 20, 0.0625F, mouseX, mouseY, this.minecraft.player
            );
        }
    }

    private int getTabX(CreativeModeTab tab) {
        int i = tab.column();
        int j = 27;
        int k = 27 * i;
        if (tab.isAlignedRight()) {
            k = this.imageWidth - 27 * (7 - i) + 1;
        }

        return k;
    }

    private int getTabY(CreativeModeTab tab) {
        int i = 0;
        if (tab.row() == CreativeModeTab.Row.TOP) {
            i -= 32;
        } else {
            i += this.imageHeight;
        }

        return i;
    }

    protected boolean checkTabClicked(CreativeModeTab creativeModeTab, double relativeMouseX, double relativeMouseY) {
        int i = this.getTabX(creativeModeTab);
        int j = this.getTabY(creativeModeTab);
        return relativeMouseX >= i && relativeMouseX <= i + 26 && relativeMouseY >= j && relativeMouseY <= j + 32;
    }

    protected boolean checkTabHovering(GuiGraphics guiGraphics, CreativeModeTab creativeModeTab, int mouseX, int mouseY) {
        int i = this.getTabX(creativeModeTab);
        int j = this.getTabY(creativeModeTab);
        if (this.isHovering(i + 3, j + 3, 21, 27, mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, creativeModeTab.getDisplayName(), mouseX, mouseY);
            return true;
        } else {
            return false;
        }
    }

    protected void renderTabButton(GuiGraphics guiGraphics, CreativeModeTab creativeModeTab) {
        boolean bl = creativeModeTab == selectedTab;
        boolean bl2 = creativeModeTab.row() == CreativeModeTab.Row.TOP;
        int i = creativeModeTab.column();
        int j = this.leftPos + this.getTabX(creativeModeTab);
        int k = this.topPos - (bl2 ? 28 : -(this.imageHeight - 4));
        ResourceLocation[] resourceLocations;
        if (bl2) {
            resourceLocations = bl ? SELECTED_TOP_TABS : UNSELECTED_TOP_TABS;
        } else {
            resourceLocations = bl ? SELECTED_BOTTOM_TABS : UNSELECTED_BOTTOM_TABS;
        }

        guiGraphics.blitSprite(resourceLocations[Mth.clamp(i, 0, resourceLocations.length)], j, k, 26, 32);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
        j += 5;
        k += 8 + (bl2 ? 1 : -1);
        ItemStack itemStack = creativeModeTab.getIconItem();
        guiGraphics.renderItem(itemStack, j, k);
        guiGraphics.renderItemDecorations(this.font, itemStack, j, k);
        guiGraphics.pose().popPose();
    }

    public boolean isInventoryOpen() {
        return selectedTab.getType() == CreativeModeTab.Type.INVENTORY;
    }

    public static void handleHotbarLoadOrSave(Minecraft client, int index, boolean load, boolean save) {
        LocalPlayer localPlayer = client.player;
        RegistryAccess registryAccess = localPlayer.level().registryAccess();
        HotbarManager hotbarManager = client.getHotbarManager();
        Hotbar hotbar = hotbarManager.get(index);
        if (load) {
            List<ItemStack> list = hotbar.load(registryAccess);

            for (int i = 0; i < Inventory.getSelectionSize(); i++) {
                ItemStack itemStack = list.get(i);
                localPlayer.getInventory().setItem(i, itemStack);
                client.gameMode.handleCreativeModeItemAdd(itemStack, 36 + i);
            }

            localPlayer.inventoryMenu.broadcastChanges();
        } else if (save) {
            hotbar.storeFrom(localPlayer.getInventory(), registryAccess);
            Component component = client.options.keyHotbarSlots[index].getTranslatedKeyMessage();
            Component component2 = client.options.keyLoadHotbarActivator.getTranslatedKeyMessage();
            Component component3 = Component.translatable("inventory.hotbarSaved", component2, component);
            client.gui.setOverlayMessage(component3, false);
            client.getNarrator().sayNow(component3);
            hotbarManager.save();
        }
    }

    static class CustomCreativeSlot extends Slot {
        public CustomCreativeSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        /**
         * Return whether this slot's stack can be taken from this slot.
         */
        @Override
        public boolean mayPickup(Player player) {
            ItemStack itemStack = this.getItem();
            return super.mayPickup(player) && !itemStack.isEmpty()
                ? itemStack.isItemEnabled(player.level().enabledFeatures()) && !itemStack.has(DataComponents.CREATIVE_SLOT_LOCK)
                : itemStack.isEmpty();
        }
    }

    public static class ItemPickerMenu extends AbstractContainerMenu {
        /**
         * The list of items in this container.
         */
        public final NonNullList<ItemStack> items = NonNullList.create();
        private final AbstractContainerMenu inventoryMenu;

        public ItemPickerMenu(Player player) {
            super(null, 0);
            this.inventoryMenu = player.inventoryMenu;
            Inventory inventory = player.getInventory();

            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 9; j++) {
                    this.addSlot(new CreativeModeInventoryScreen.CustomCreativeSlot(CreativeModeInventoryScreen.CONTAINER, i * 9 + j, 9 + j * 18, 18 + i * 18));
                }
            }

            for (int i = 0; i < 9; i++) {
                this.addSlot(new Slot(inventory, i, 9 + i * 18, 112));
            }

            this.scrollTo(0.0F);
        }

        /**
         * Determines whether supplied player can use this container
         */
        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        protected int calculateRowCount() {
            return Mth.positiveCeilDiv(this.items.size(), 9) - 5;
        }

        protected int getRowIndexForScroll(float scrollOffs) {
            return Math.max((int)(scrollOffs * this.calculateRowCount() + 0.5), 0);
        }

        protected float getScrollForRowIndex(int rowIndex) {
            return Mth.clamp((float)rowIndex / this.calculateRowCount(), 0.0F, 1.0F);
        }

        protected float subtractInputFromScroll(float scrollOffs, double input) {
            return Mth.clamp(scrollOffs - (float)(input / this.calculateRowCount()), 0.0F, 1.0F);
        }

        /**
         * Updates the gui slot's ItemStacks based on scroll position.
         */
        public void scrollTo(float pos) {
            int i = this.getRowIndexForScroll(pos);

            for (int j = 0; j < 5; j++) {
                for (int k = 0; k < 9; k++) {
                    int l = k + (j + i) * 9;
                    if (l >= 0 && l < this.items.size()) {
                        CreativeModeInventoryScreen.CONTAINER.setItem(k + j * 9, this.items.get(l));
                    } else {
                        CreativeModeInventoryScreen.CONTAINER.setItem(k + j * 9, ItemStack.EMPTY);
                    }
                }
            }
        }

        public boolean canScroll() {
            return this.items.size() > 45;
        }

        /**
         * Handle when the stack in slot {@code index} is shift-clicked. Normally this moves the stack between the player inventory and the other inventory(s).
         */
        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index >= this.slots.size() - 9 && index < this.slots.size()) {
                Slot slot = this.slots.get(index);
                if (slot != null && slot.hasItem()) {
                    slot.setByPlayer(ItemStack.EMPTY);
                }
            }

            return ItemStack.EMPTY;
        }

        /**
         * Called to determine if the current slot is valid for the stack merging (double-click) code. The stack passed in is null for the initial slot that was double-clicked.
         */
        @Override
        public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
            return slot.container != CreativeModeInventoryScreen.CONTAINER;
        }

        /**
         * Returns {@code true} if the player can "drag-spilt" items into this slot. Returns {@code true} by default. Called to check if the slot can be added to a list of Slots to split the held ItemStack across.
         */
        @Override
        public boolean canDragTo(Slot slot) {
            return slot.container != CreativeModeInventoryScreen.CONTAINER;
        }

        @Override
        public ItemStack getCarried() {
            return this.inventoryMenu.getCarried();
        }

        @Override
        public void setCarried(ItemStack stack) {
            this.inventoryMenu.setCarried(stack);
        }
    }

    static class SlotWrapper extends Slot {
        final Slot target;

        public SlotWrapper(Slot slot, int index, int x, int y) {
            super(slot.container, index, x, y);
            this.target = slot;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            this.target.onTake(player, stack);
        }

        /**
         * Check if the stack is allowed to be placed in this slot, used for armor slots as well as furnace fuel.
         */
        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.target.mayPlace(stack);
        }

        @Override
        public ItemStack getItem() {
            return this.target.getItem();
        }

        @Override
        public boolean hasItem() {
            return this.target.hasItem();
        }

        @Override
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            this.target.setByPlayer(newStack, oldStack);
        }

        /**
         * Helper method to put a stack in the slot.
         */
        @Override
        public void set(ItemStack stack) {
            this.target.set(stack);
        }

        @Override
        public void setChanged() {
            this.target.setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return this.target.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return this.target.getMaxStackSize(stack);
        }

        @Nullable
        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return this.target.getNoItemIcon();
        }

        /**
         * Decrease the size of the stack in slot (first int arg) by the amount of the second int arg. Returns the new stack.
         */
        @Override
        public ItemStack remove(int amount) {
            return this.target.remove(amount);
        }

        @Override
        public boolean isActive() {
            return this.target.isActive();
        }

        /**
         * Return whether this slot's stack can be taken from this slot.
         */
        @Override
        public boolean mayPickup(Player player) {
            return this.target.mayPickup(player);
        }
    }
}
