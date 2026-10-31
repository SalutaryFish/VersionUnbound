package net.minecraft.world.inventory;

import com.google.common.collect.ImmutableList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BannerPatternTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BannerPatternItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class LoomMenu extends AbstractContainerMenu {
    private static final int PATTERN_NOT_SET = -1;
    private static final int INV_SLOT_START = 4;
    private static final int INV_SLOT_END = 31;
    private static final int USE_ROW_SLOT_START = 31;
    private static final int USE_ROW_SLOT_END = 40;
    private final ContainerLevelAccess access;
    final DataSlot selectedBannerPatternIndex = DataSlot.standalone();
    private List<Holder<BannerPattern>> selectablePatterns = List.of();
    Runnable slotUpdateListener = () -> {};
    private final HolderGetter<BannerPattern> patternGetter;
    final Slot bannerSlot;
    final Slot dyeSlot;
    private final Slot patternSlot;
    private final Slot resultSlot;
    long lastSoundTime;
    private final Container inputContainer = new SimpleContainer(3) {
        @Override
        public void setChanged() {
            super.setChanged();
            LoomMenu.this.slotsChanged(this);
            LoomMenu.this.slotUpdateListener.run();
        }
    };
    private final Container outputContainer = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            LoomMenu.this.slotUpdateListener.run();
        }
    };

    public LoomMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public LoomMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(MenuType.LOOM, containerId);
        this.access = access;
        this.bannerSlot = this.addSlot(new Slot(this.inputContainer, 0, 13, 26) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.getItem() instanceof BannerItem;
            }
        });
        this.dyeSlot = this.addSlot(new Slot(this.inputContainer, 1, 33, 26) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.getItem() instanceof DyeItem;
            }
        });
        this.patternSlot = this.addSlot(new Slot(this.inputContainer, 2, 23, 45) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.getItem() instanceof BannerPatternItem;
            }
        });
        this.resultSlot = this.addSlot(new Slot(this.outputContainer, 0, 143, 57) {
            /**
             * Check if the stack is allowed to be placed in this slot, used for armor slots as well as furnace fuel.
             */
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                LoomMenu.this.bannerSlot.remove(1);
                LoomMenu.this.dyeSlot.remove(1);
                if (!LoomMenu.this.bannerSlot.hasItem() || !LoomMenu.this.dyeSlot.hasItem()) {
                    LoomMenu.this.selectedBannerPatternIndex.set(-1);
                }

                access.execute((level, pos) -> {
                    long l = level.getGameTime();
                    if (LoomMenu.this.lastSoundTime != l) {
                        level.playSound((Player)null, pos, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        LoomMenu.this.lastSoundTime = l;
                    }
                });
                super.onTake(player, stack);
            }
        });

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }

        this.addDataSlot(this.selectedBannerPatternIndex);
        this.patternGetter = playerInventory.player.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
    }

    /**
     * Determines whether supplied player can use this container
     */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, Blocks.LOOM);
    }

    /**
     * Handles the given Button-click on the server, currently only used by enchanting. Name is for legacy.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < this.selectablePatterns.size()) {
            this.selectedBannerPatternIndex.set(id);
            this.setupResultSlot(this.selectablePatterns.get(id));
            return true;
        } else {
            return false;
        }
    }

    private List<Holder<BannerPattern>> getSelectablePatterns(ItemStack stack) {
        if (stack.isEmpty()) {
            return this.patternGetter.get(BannerPatternTags.NO_ITEM_REQUIRED).map(ImmutableList::copyOf).orElse(ImmutableList.of());
        } else {
            return stack.getItem() instanceof BannerPatternItem bannerPatternItem
                ? this.patternGetter.get(bannerPatternItem.getBannerPattern()).map(ImmutableList::copyOf).orElse(ImmutableList.of())
                : List.of();
        }
    }

    private boolean isValidPatternIndex(int index) {
        return index >= 0 && index < this.selectablePatterns.size();
    }

    /**
     * Callback for when the crafting matrix is changed.
     */
    @Override
    public void slotsChanged(Container inventory) {
        ItemStack itemStack = this.bannerSlot.getItem();
        ItemStack itemStack2 = this.dyeSlot.getItem();
        ItemStack itemStack3 = this.patternSlot.getItem();
        if (!itemStack.isEmpty() && !itemStack2.isEmpty()) {
            int i = this.selectedBannerPatternIndex.get();
            boolean bl = this.isValidPatternIndex(i);
            List<Holder<BannerPattern>> list = this.selectablePatterns;
            this.selectablePatterns = this.getSelectablePatterns(itemStack3);
            Holder<BannerPattern> holder;
            if (this.selectablePatterns.size() == 1) {
                this.selectedBannerPatternIndex.set(0);
                holder = this.selectablePatterns.get(0);
            } else if (!bl) {
                this.selectedBannerPatternIndex.set(-1);
                holder = null;
            } else {
                Holder<BannerPattern> holder2 = list.get(i);
                int j = this.selectablePatterns.indexOf(holder2);
                if (j != -1) {
                    holder = holder2;
                    this.selectedBannerPatternIndex.set(j);
                } else {
                    holder = null;
                    this.selectedBannerPatternIndex.set(-1);
                }
            }

            if (holder != null) {
                BannerPatternLayers bannerPatternLayers = itemStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
                boolean bl2 = bannerPatternLayers.layers().size() >= 6;
                if (bl2) {
                    this.selectedBannerPatternIndex.set(-1);
                    this.resultSlot.set(ItemStack.EMPTY);
                } else {
                    this.setupResultSlot(holder);
                }
            } else {
                this.resultSlot.set(ItemStack.EMPTY);
            }

            this.broadcastChanges();
        } else {
            this.resultSlot.set(ItemStack.EMPTY);
            this.selectablePatterns = List.of();
            this.selectedBannerPatternIndex.set(-1);
        }
    }

    public List<Holder<BannerPattern>> getSelectablePatterns() {
        return this.selectablePatterns;
    }

    public int getSelectedBannerPatternIndex() {
        return this.selectedBannerPatternIndex.get();
    }

    public void registerUpdateListener(Runnable listener) {
        this.slotUpdateListener = listener;
    }

    /**
     * Handle when the stack in slot {@code index} is shift-clicked. Normally this moves the stack between the player inventory and the other inventory(s).
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemStack2 = slot.getItem();
            itemStack = itemStack2.copy();
            if (index == this.resultSlot.index) {
                if (!this.moveItemStackTo(itemStack2, 4, 40, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemStack2, itemStack);
            } else if (index != this.dyeSlot.index && index != this.bannerSlot.index && index != this.patternSlot.index) {
                if (itemStack2.getItem() instanceof BannerItem) {
                    if (!this.moveItemStackTo(itemStack2, this.bannerSlot.index, this.bannerSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (itemStack2.getItem() instanceof DyeItem) {
                    if (!this.moveItemStackTo(itemStack2, this.dyeSlot.index, this.dyeSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (itemStack2.getItem() instanceof BannerPatternItem) {
                    if (!this.moveItemStackTo(itemStack2, this.patternSlot.index, this.patternSlot.index + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= 4 && index < 31) {
                    if (!this.moveItemStackTo(itemStack2, 31, 40, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= 31 && index < 40 && !this.moveItemStackTo(itemStack2, 4, 31, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemStack2, 4, 40, false)) {
                return ItemStack.EMPTY;
            }

            if (itemStack2.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemStack2.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemStack2);
        }

        return itemStack;
    }

    /**
     * Called when the container is closed.
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, blockPos) -> this.clearContainer(player, this.inputContainer));
    }

    /**
     * Creates an output banner ItemStack based on the patterns, dyes, etc. in the loom.
     */
    private void setupResultSlot(Holder<BannerPattern> pattern) {
        ItemStack itemStack = this.bannerSlot.getItem();
        ItemStack itemStack2 = this.dyeSlot.getItem();
        ItemStack itemStack3 = ItemStack.EMPTY;
        if (!itemStack.isEmpty() && !itemStack2.isEmpty()) {
            itemStack3 = itemStack.copyWithCount(1);
            DyeColor dyeColor = ((DyeItem)itemStack2.getItem()).getDyeColor();
            itemStack3.update(
                DataComponents.BANNER_PATTERNS,
                BannerPatternLayers.EMPTY,
                bannerPatternLayers -> new BannerPatternLayers.Builder().addAll(bannerPatternLayers).add(pattern, dyeColor).build()
            );
        }

        if (!ItemStack.matches(itemStack3, this.resultSlot.getItem())) {
            this.resultSlot.set(itemStack3);
        }
    }

    public Slot getBannerSlot() {
        return this.bannerSlot;
    }

    public Slot getDyeSlot() {
        return this.dyeSlot;
    }

    public Slot getPatternSlot() {
        return this.patternSlot;
    }

    public Slot getResultSlot() {
        return this.resultSlot;
    }
}
