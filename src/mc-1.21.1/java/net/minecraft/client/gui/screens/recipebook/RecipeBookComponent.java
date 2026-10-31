package net.minecraft.client.gui.screens.recipebook;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.recipebook.PlaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeBookComponent implements PlaceRecipe<Ingredient>, Renderable, GuiEventListener, NarratableEntry, RecipeShownListener {
    public static final WidgetSprites RECIPE_BUTTON_SPRITES = new WidgetSprites(
        ResourceLocation.withDefaultNamespace("recipe_book/button"), ResourceLocation.withDefaultNamespace("recipe_book/button_highlighted")
    );
    private static final WidgetSprites FILTER_BUTTON_SPRITES = new WidgetSprites(
        ResourceLocation.withDefaultNamespace("recipe_book/filter_enabled"),
        ResourceLocation.withDefaultNamespace("recipe_book/filter_disabled"),
        ResourceLocation.withDefaultNamespace("recipe_book/filter_enabled_highlighted"),
        ResourceLocation.withDefaultNamespace("recipe_book/filter_disabled_highlighted")
    );
    protected static final ResourceLocation RECIPE_BOOK_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/recipe_book.png");
    private static final Component SEARCH_HINT = Component.translatable("gui.recipebook.search_hint")
        .withStyle(ChatFormatting.ITALIC)
        .withStyle(ChatFormatting.GRAY);
    public static final int IMAGE_WIDTH = 147;
    public static final int IMAGE_HEIGHT = 166;
    private static final int OFFSET_X_POSITION = 86;
    private static final Component ONLY_CRAFTABLES_TOOLTIP = Component.translatable("gui.recipebook.toggleRecipes.craftable");
    private static final Component ALL_RECIPES_TOOLTIP = Component.translatable("gui.recipebook.toggleRecipes.all");
    private int xOffset;
    private int width;
    private int height;
    protected final GhostRecipe ghostRecipe = new GhostRecipe();
    private final List<RecipeBookTabButton> tabButtons = Lists.newArrayList();
    @Nullable
    private RecipeBookTabButton selectedTab;
    protected StateSwitchingButton filterButton;
    protected RecipeBookMenu<?, ?> menu;
    protected Minecraft minecraft;
    @Nullable
    private EditBox searchBox;
    private String lastSearch = "";
    private ClientRecipeBook book;
    private final RecipeBookPage recipeBookPage = new RecipeBookPage();
    private final StackedContents stackedContents = new StackedContents();
    private int timesInventoryChanged;
    private boolean ignoreTextInput;
    private boolean visible;
    private boolean widthTooNarrow;

    public void init(int width, int height, Minecraft minecraft, boolean widthTooNarrow, RecipeBookMenu<?, ?> menu) {
        this.minecraft = minecraft;
        this.width = width;
        this.height = height;
        this.menu = menu;
        this.widthTooNarrow = widthTooNarrow;
        minecraft.player.containerMenu = menu;
        this.book = minecraft.player.getRecipeBook();
        this.timesInventoryChanged = minecraft.player.getInventory().getTimesChanged();
        this.visible = this.isVisibleAccordingToBookData();
        if (this.visible) {
            this.initVisuals();
        }
    }

    public void initVisuals() {
        this.xOffset = this.widthTooNarrow ? 0 : 86;
        int i = (this.width - 147) / 2 - this.xOffset;
        int j = (this.height - 166) / 2;
        this.stackedContents.clear();
        this.minecraft.player.getInventory().fillStackedContents(this.stackedContents);
        this.menu.fillCraftSlotsStackedContents(this.stackedContents);
        String string = this.searchBox != null ? this.searchBox.getValue() : "";
        this.searchBox = new EditBox(this.minecraft.font, i + 25, j + 13, 81, 9 + 5, Component.translatable("itemGroup.search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(16777215);
        this.searchBox.setValue(string);
        this.searchBox.setHint(SEARCH_HINT);
        this.recipeBookPage.init(this.minecraft, i, j);
        this.recipeBookPage.addListener(this);
        this.filterButton = new StateSwitchingButton(i + 110, j + 12, 26, 16, this.book.isFiltering(this.menu));
        this.updateFilterButtonTooltip();
        this.initFilterButtonTextures();
        this.tabButtons.clear();

        for (RecipeBookCategories recipeBookCategories : RecipeBookCategories.getCategories(this.menu.getRecipeBookType())) {
            this.tabButtons.add(new RecipeBookTabButton(recipeBookCategories));
        }

        if (this.selectedTab != null) {
            this.selectedTab = this.tabButtons
                .stream()
                .filter(recipeBookTabButton -> recipeBookTabButton.getCategory().equals(this.selectedTab.getCategory()))
                .findFirst()
                .orElse(null);
        }

        if (this.selectedTab == null) {
            this.selectedTab = this.tabButtons.get(0);
        }

        this.selectedTab.setStateTriggered(true);
        this.updateCollections(false);
        this.updateTabs();
    }

    private void updateFilterButtonTooltip() {
        this.filterButton.setTooltip(this.filterButton.isStateTriggered() ? Tooltip.create(this.getRecipeFilterName()) : Tooltip.create(ALL_RECIPES_TOOLTIP));
    }

    protected void initFilterButtonTextures() {
        this.filterButton.initTextureValues(FILTER_BUTTON_SPRITES);
    }

    public int updateScreenPosition(int width, int imageWidth) {
        int i;
        if (this.isVisible() && !this.widthTooNarrow) {
            i = 177 + (width - imageWidth - 200) / 2;
        } else {
            i = (width - imageWidth) / 2;
        }

        return i;
    }

    public void toggleVisibility() {
        this.setVisible(!this.isVisible());
    }

    public boolean isVisible() {
        return this.visible;
    }

    private boolean isVisibleAccordingToBookData() {
        return this.book.isOpen(this.menu.getRecipeBookType());
    }

    protected void setVisible(boolean visible) {
        if (visible) {
            this.initVisuals();
        }

        this.visible = visible;
        this.book.setOpen(this.menu.getRecipeBookType(), visible);
        if (!visible) {
            this.recipeBookPage.setInvisible();
        }

        this.sendUpdateSettings();
    }

    public void slotClicked(@Nullable Slot slot) {
        if (slot != null && slot.index < this.menu.getSize()) {
            this.ghostRecipe.clear();
            if (this.isVisible()) {
                this.updateStackedContents();
            }
        }
    }

    private void updateCollections(boolean resetPageNumber) {
        List<RecipeCollection> list = this.book.getCollection(this.selectedTab.getCategory());
        list.forEach(recipeCollection -> recipeCollection.canCraft(this.stackedContents, this.menu.getGridWidth(), this.menu.getGridHeight(), this.book));
        List<RecipeCollection> list2 = Lists.newArrayList(list);
        list2.removeIf(recipeCollection -> !recipeCollection.hasKnownRecipes());
        list2.removeIf(recipeCollection -> !recipeCollection.hasFitting());
        String string = this.searchBox.getValue();
        if (!string.isEmpty()) {
            ClientPacketListener clientPacketListener = this.minecraft.getConnection();
            if (clientPacketListener != null) {
                ObjectSet<RecipeCollection> objectSet = new ObjectLinkedOpenHashSet<>(
                    clientPacketListener.searchTrees().recipes().search(string.toLowerCase(Locale.ROOT))
                );
                list2.removeIf(recipeCollection -> !objectSet.contains(recipeCollection));
            }
        }

        if (this.book.isFiltering(this.menu)) {
            list2.removeIf(recipeCollection -> !recipeCollection.hasCraftable());
        }

        this.recipeBookPage.updateCollections(list2, resetPageNumber);
    }

    private void updateTabs() {
        int i = (this.width - 147) / 2 - this.xOffset - 30;
        int j = (this.height - 166) / 2 + 3;
        int k = 27;
        int l = 0;

        for (RecipeBookTabButton recipeBookTabButton : this.tabButtons) {
            RecipeBookCategories recipeBookCategories = recipeBookTabButton.getCategory();
            if (recipeBookCategories == RecipeBookCategories.CRAFTING_SEARCH || recipeBookCategories == RecipeBookCategories.FURNACE_SEARCH) {
                recipeBookTabButton.visible = true;
                recipeBookTabButton.setPosition(i, j + 27 * l++);
            } else if (recipeBookTabButton.updateVisibility(this.book)) {
                recipeBookTabButton.setPosition(i, j + 27 * l++);
                recipeBookTabButton.startAnimation(this.minecraft);
            }
        }
    }

    public void tick() {
        boolean bl = this.isVisibleAccordingToBookData();
        if (this.isVisible() != bl) {
            this.setVisible(bl);
        }

        if (this.isVisible()) {
            if (this.timesInventoryChanged != this.minecraft.player.getInventory().getTimesChanged()) {
                this.updateStackedContents();
                this.timesInventoryChanged = this.minecraft.player.getInventory().getTimesChanged();
            }
        }
    }

    private void updateStackedContents() {
        this.stackedContents.clear();
        this.minecraft.player.getInventory().fillStackedContents(this.stackedContents);
        this.menu.fillCraftSlotsStackedContents(this.stackedContents);
        this.updateCollections(false);
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
        if (this.isVisible()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
            int i = (this.width - 147) / 2 - this.xOffset;
            int j = (this.height - 166) / 2;
            guiGraphics.blit(RECIPE_BOOK_LOCATION, i, j, 1, 1, 147, 166);
            this.searchBox.render(guiGraphics, mouseX, mouseY, partialTick);

            for (RecipeBookTabButton recipeBookTabButton : this.tabButtons) {
                recipeBookTabButton.render(guiGraphics, mouseX, mouseY, partialTick);
            }

            this.filterButton.render(guiGraphics, mouseX, mouseY, partialTick);
            this.recipeBookPage.render(guiGraphics, i, j, mouseX, mouseY, partialTick);
            guiGraphics.pose().popPose();
        }
    }

    public void renderTooltip(GuiGraphics guiGraphics, int renderX, int renderY, int mouseX, int mouseY) {
        if (this.isVisible()) {
            this.recipeBookPage.renderTooltip(guiGraphics, mouseX, mouseY);
            this.renderGhostRecipeTooltip(guiGraphics, renderX, renderY, mouseX, mouseY);
        }
    }

    protected Component getRecipeFilterName() {
        return ONLY_CRAFTABLES_TOOLTIP;
    }

    private void renderGhostRecipeTooltip(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        ItemStack itemStack = null;

        for (int i = 0; i < this.ghostRecipe.size(); i++) {
            GhostRecipe.GhostIngredient ghostIngredient = this.ghostRecipe.get(i);
            int j = ghostIngredient.getX() + x;
            int k = ghostIngredient.getY() + y;
            if (mouseX >= j && mouseY >= k && mouseX < j + 16 && mouseY < k + 16) {
                itemStack = ghostIngredient.getItem();
            }
        }

        if (itemStack != null && this.minecraft.screen != null) {
            guiGraphics.renderComponentTooltip(this.minecraft.font, Screen.getTooltipFromItem(this.minecraft, itemStack), mouseX, mouseY);
        }
    }

    public void renderGhostRecipe(GuiGraphics guiGraphics, int leftPos, int topPos, boolean bl, float partialTick) {
        this.ghostRecipe.render(guiGraphics, this.minecraft, leftPos, topPos, bl, partialTick);
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
        if (this.isVisible() && !this.minecraft.player.isSpectator()) {
            if (this.recipeBookPage.mouseClicked(mouseX, mouseY, button, (this.width - 147) / 2 - this.xOffset, (this.height - 166) / 2, 147, 166)) {
                RecipeHolder<?> recipeHolder = this.recipeBookPage.getLastClickedRecipe();
                RecipeCollection recipeCollection = this.recipeBookPage.getLastClickedRecipeCollection();
                if (recipeHolder != null && recipeCollection != null) {
                    if (!recipeCollection.isCraftable(recipeHolder) && this.ghostRecipe.getRecipe() == recipeHolder) {
                        return false;
                    }

                    this.ghostRecipe.clear();
                    this.minecraft.gameMode.handlePlaceRecipe(this.minecraft.player.containerMenu.containerId, recipeHolder, Screen.hasShiftDown());
                    if (!this.isOffsetNextToMainGUI()) {
                        this.setVisible(false);
                    }
                }

                return true;
            } else {
                if (this.searchBox.mouseClicked(mouseX, mouseY, button)) {
                    this.searchBox.setFocused(true);
                    return true;
                }

                this.searchBox.setFocused(false);
                if (this.filterButton.mouseClicked(mouseX, mouseY, button)) {
                    boolean bl = this.toggleFiltering();
                    this.filterButton.setStateTriggered(bl);
                    this.updateFilterButtonTooltip();
                    this.sendUpdateSettings();
                    this.updateCollections(false);
                    return true;
                }

                for (RecipeBookTabButton recipeBookTabButton : this.tabButtons) {
                    if (recipeBookTabButton.mouseClicked(mouseX, mouseY, button)) {
                        if (this.selectedTab != recipeBookTabButton) {
                            if (this.selectedTab != null) {
                                this.selectedTab.setStateTriggered(false);
                            }

                            this.selectedTab = recipeBookTabButton;
                            this.selectedTab.setStateTriggered(true);
                            this.updateCollections(true);
                        }

                        return true;
                    }
                }

                return false;
            }
        } else {
            return false;
        }
    }

    private boolean toggleFiltering() {
        RecipeBookType recipeBookType = this.menu.getRecipeBookType();
        boolean bl = !this.book.isFiltering(recipeBookType);
        this.book.setFiltering(recipeBookType, bl);
        return bl;
    }

    public boolean hasClickedOutside(double mouseX, double mouseY, int x, int y, int width, int height, int i) {
        if (!this.isVisible()) {
            return true;
        }

        boolean bl = mouseX < x || mouseY < y || mouseX >= x + width || mouseY >= y + height;
        boolean bl2 = x - 147 < mouseX && mouseX < x && y < mouseY && mouseY < y + height;
        return bl && !bl2 && !this.selectedTab.isHoveredOrFocused();
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
        if (!this.isVisible() || this.minecraft.player.isSpectator()) {
            return false;
        } else if (keyCode == 256 && !this.isOffsetNextToMainGUI()) {
            this.setVisible(false);
            return true;
        } else if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            this.checkSearchStringUpdate();
            return true;
        } else if (this.searchBox.isFocused() && this.searchBox.isVisible() && keyCode != 256) {
            return true;
        } else if (this.minecraft.options.keyChat.matches(keyCode, scanCode) && !this.searchBox.isFocused()) {
            this.ignoreTextInput = true;
            this.searchBox.setFocused(true);
            return true;
        } else {
            return false;
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
        return GuiEventListener.super.keyReleased(keyCode, scanCode, modifiers);
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
        } else if (!this.isVisible() || this.minecraft.player.isSpectator()) {
            return false;
        } else if (this.searchBox.charTyped(codePoint, modifiers)) {
            this.checkSearchStringUpdate();
            return true;
        } else {
            return GuiEventListener.super.charTyped(codePoint, modifiers);
        }
    }

    /**
     * Checks if the given mouse coordinates are over the GUI element.
     * <p>
     * @return {@code true} if the mouse is over the GUI element, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    /**
     * Sets the focus state of the GUI element.
     *
     * @param focused {@code true} to apply focus, {@code false} to remove focus
     */
    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    private void checkSearchStringUpdate() {
        String string = this.searchBox.getValue().toLowerCase(Locale.ROOT);
        this.pirateSpeechForThePeople(string);
        if (!string.equals(this.lastSearch)) {
            this.updateCollections(false);
            this.lastSearch = string;
        }
    }

    /**
     * Check if we should activate the pirate speak easter egg.
     */
    private void pirateSpeechForThePeople(String text) {
        if ("excitedze".equals(text)) {
            LanguageManager languageManager = this.minecraft.getLanguageManager();
            String string = "en_pt";
            LanguageInfo languageInfo = languageManager.getLanguage("en_pt");
            if (languageInfo == null || languageManager.getSelected().equals("en_pt")) {
                return;
            }

            languageManager.setSelected("en_pt");
            this.minecraft.options.languageCode = "en_pt";
            this.minecraft.reloadResourcePacks();
            this.minecraft.options.save();
        }
    }

    private boolean isOffsetNextToMainGUI() {
        return this.xOffset == 86;
    }

    public void recipesUpdated() {
        this.updateTabs();
        if (this.isVisible()) {
            this.updateCollections(false);
        }
    }

    @Override
    public void recipesShown(List<RecipeHolder<?>> recipes) {
        for (RecipeHolder<?> recipeHolder : recipes) {
            this.minecraft.player.removeRecipeHighlight(recipeHolder);
        }
    }

    public void setupGhostRecipe(RecipeHolder<?> recipe, List<Slot> slots) {
        ItemStack itemStack = recipe.value().getResultItem(this.minecraft.level.registryAccess());
        this.ghostRecipe.setRecipe(recipe);
        this.ghostRecipe.addIngredient(Ingredient.of(itemStack), slots.get(0).x, slots.get(0).y);
        this.placeRecipe(
            this.menu.getGridWidth(), this.menu.getGridHeight(), this.menu.getResultSlotIndex(), recipe, recipe.value().getIngredients().iterator(), 0
        );
    }

    public void addItemToSlot(Ingredient item, int slot, int maxAmount, int x, int y) {
        if (!item.isEmpty()) {
            Slot slot2 = this.menu.slots.get(slot);
            this.ghostRecipe.addIngredient(item, slot2.x, slot2.y);
        }
    }

    protected void sendUpdateSettings() {
        if (this.minecraft.getConnection() != null) {
            RecipeBookType recipeBookType = this.menu.getRecipeBookType();
            boolean bl = this.book.getBookSettings().isOpen(recipeBookType);
            boolean bl2 = this.book.getBookSettings().isFiltering(recipeBookType);
            this.minecraft.getConnection().send(new ServerboundRecipeBookChangeSettingsPacket(recipeBookType, bl, bl2));
        }
    }

    @Override
    public NarratableEntry.NarrationPriority narrationPriority() {
        return this.visible ? NarratableEntry.NarrationPriority.HOVERED : NarratableEntry.NarrationPriority.NONE;
    }

    /**
     * Updates the narration output with the current narration information.
     *
     * @param narrationElementOutput the output to update with narration information.
     */
    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
        List<NarratableEntry> list = Lists.newArrayList();
        this.recipeBookPage.listButtons(abstractWidget -> {
            if (abstractWidget.isActive()) {
                list.add(abstractWidget);
            }
        });
        list.add(this.searchBox);
        list.add(this.filterButton);
        list.addAll(this.tabButtons);
        Screen.NarratableSearchResult narratableSearchResult = Screen.findNarratableWidget(list, null);
        if (narratableSearchResult != null) {
            narratableSearchResult.entry.updateNarration(narrationElementOutput.nest());
        }
    }
}
