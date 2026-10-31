package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, RecipeCraftingHolder, StackedContentsCompatible {
    protected static final int SLOT_INPUT = 0;
    protected static final int SLOT_FUEL = 1;
    protected static final int SLOT_RESULT = 2;
    public static final int DATA_LIT_TIME = 0;
    private static final int[] SLOTS_FOR_UP = new int[]{0};
    private static final int[] SLOTS_FOR_DOWN = new int[]{2, 1};
    private static final int[] SLOTS_FOR_SIDES = new int[]{1};
    public static final int DATA_LIT_DURATION = 1;
    public static final int DATA_COOKING_PROGRESS = 2;
    public static final int DATA_COOKING_TOTAL_TIME = 3;
    public static final int NUM_DATA_VALUES = 4;
    public static final int BURN_TIME_STANDARD = 200;
    public static final int BURN_COOL_SPEED = 2;
    protected NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    int litTime;
    int litDuration;
    int cookingProgress;
    int cookingTotalTime;
    @Nullable
    private static volatile Map<Item, Integer> fuelCache;
    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int i) {
            switch (i) {
                case 0:
                    return AbstractFurnaceBlockEntity.this.litTime;
                case 1:
                    return AbstractFurnaceBlockEntity.this.litDuration;
                case 2:
                    return AbstractFurnaceBlockEntity.this.cookingProgress;
                case 3:
                    return AbstractFurnaceBlockEntity.this.cookingTotalTime;
                default:
                    return 0;
            }
        }

        @Override
        public void set(int i, int j) {
            switch (i) {
                case 0:
                    AbstractFurnaceBlockEntity.this.litTime = j;
                    break;
                case 1:
                    AbstractFurnaceBlockEntity.this.litDuration = j;
                    break;
                case 2:
                    AbstractFurnaceBlockEntity.this.cookingProgress = j;
                    break;
                case 3:
                    AbstractFurnaceBlockEntity.this.cookingTotalTime = j;
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };
    private final Object2IntOpenHashMap<ResourceLocation> recipesUsed = new Object2IntOpenHashMap<>();
    private final RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck;

    protected AbstractFurnaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, RecipeType<? extends AbstractCookingRecipe> recipeType) {
        super(type, pos, blockState);
        this.quickCheck = RecipeManager.createCheck(recipeType);
    }

    public static void invalidateCache() {
        fuelCache = null;
    }

    public static Map<Item, Integer> getFuel() {
        Map<Item, Integer> map = fuelCache;
        if (map != null) {
            return map;
        }

        Map<Item, Integer> map2 = Maps.newLinkedHashMap();
        add(map2, Items.LAVA_BUCKET, 20000);
        add(map2, Blocks.COAL_BLOCK, 16000);
        add(map2, Items.BLAZE_ROD, 2400);
        add(map2, Items.COAL, 1600);
        add(map2, Items.CHARCOAL, 1600);
        add(map2, ItemTags.LOGS, 300);
        add(map2, ItemTags.BAMBOO_BLOCKS, 300);
        add(map2, ItemTags.PLANKS, 300);
        add(map2, Blocks.BAMBOO_MOSAIC, 300);
        add(map2, ItemTags.WOODEN_STAIRS, 300);
        add(map2, Blocks.BAMBOO_MOSAIC_STAIRS, 300);
        add(map2, ItemTags.WOODEN_SLABS, 150);
        add(map2, Blocks.BAMBOO_MOSAIC_SLAB, 150);
        add(map2, ItemTags.WOODEN_TRAPDOORS, 300);
        add(map2, ItemTags.WOODEN_PRESSURE_PLATES, 300);
        add(map2, ItemTags.WOODEN_FENCES, 300);
        add(map2, ItemTags.FENCE_GATES, 300);
        add(map2, Blocks.NOTE_BLOCK, 300);
        add(map2, Blocks.BOOKSHELF, 300);
        add(map2, Blocks.CHISELED_BOOKSHELF, 300);
        add(map2, Blocks.LECTERN, 300);
        add(map2, Blocks.JUKEBOX, 300);
        add(map2, Blocks.CHEST, 300);
        add(map2, Blocks.TRAPPED_CHEST, 300);
        add(map2, Blocks.CRAFTING_TABLE, 300);
        add(map2, Blocks.DAYLIGHT_DETECTOR, 300);
        add(map2, ItemTags.BANNERS, 300);
        add(map2, Items.BOW, 300);
        add(map2, Items.FISHING_ROD, 300);
        add(map2, Blocks.LADDER, 300);
        add(map2, ItemTags.SIGNS, 200);
        add(map2, ItemTags.HANGING_SIGNS, 800);
        add(map2, Items.WOODEN_SHOVEL, 200);
        add(map2, Items.WOODEN_SWORD, 200);
        add(map2, Items.WOODEN_HOE, 200);
        add(map2, Items.WOODEN_AXE, 200);
        add(map2, Items.WOODEN_PICKAXE, 200);
        add(map2, ItemTags.WOODEN_DOORS, 200);
        add(map2, ItemTags.BOATS, 1200);
        add(map2, ItemTags.WOOL, 100);
        add(map2, ItemTags.WOODEN_BUTTONS, 100);
        add(map2, Items.STICK, 100);
        add(map2, ItemTags.SAPLINGS, 100);
        add(map2, Items.BOWL, 100);
        add(map2, ItemTags.WOOL_CARPETS, 67);
        add(map2, Blocks.DRIED_KELP_BLOCK, 4001);
        add(map2, Items.CROSSBOW, 300);
        add(map2, Blocks.BAMBOO, 50);
        add(map2, Blocks.DEAD_BUSH, 100);
        add(map2, Blocks.SCAFFOLDING, 50);
        add(map2, Blocks.LOOM, 300);
        add(map2, Blocks.BARREL, 300);
        add(map2, Blocks.CARTOGRAPHY_TABLE, 300);
        add(map2, Blocks.FLETCHING_TABLE, 300);
        add(map2, Blocks.SMITHING_TABLE, 300);
        add(map2, Blocks.COMPOSTER, 300);
        add(map2, Blocks.AZALEA, 100);
        add(map2, Blocks.FLOWERING_AZALEA, 100);
        add(map2, Blocks.MANGROVE_ROOTS, 300);
        fuelCache = map2;
        return map2;
    }

    private static boolean isNeverAFurnaceFuel(Item item) {
        return item.builtInRegistryHolder().is(ItemTags.NON_FLAMMABLE_WOOD);
    }

    private static void add(Map<Item, Integer> map, TagKey<Item> itemTag, int burnTime) {
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(itemTag)) {
            if (!isNeverAFurnaceFuel(holder.value())) {
                map.put(holder.value(), burnTime);
            }
        }
    }

    private static void add(Map<Item, Integer> map, ItemLike item, int burnTime) {
        Item item2 = item.asItem();
        if (isNeverAFurnaceFuel(item2)) {
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                throw (IllegalStateException)Util.pauseInIde(
                    new IllegalStateException(
                        "A developer tried to explicitly make fire resistant item " + item2.getName(null).getString() + " a furnace fuel. That will not work!"
                    )
                );
            }
        } else {
            map.put(item2, burnTime);
        }
    }

    private boolean isLit() {
        return this.litTime > 0;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
        this.litTime = tag.getShort("BurnTime");
        this.cookingProgress = tag.getShort("CookTime");
        this.cookingTotalTime = tag.getShort("CookTimeTotal");
        this.litDuration = this.getBurnDuration(this.items.get(1));
        CompoundTag compoundTag = tag.getCompound("RecipesUsed");

        for (String string : compoundTag.getAllKeys()) {
            this.recipesUsed.put(ResourceLocation.parse(string), compoundTag.getInt(string));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putShort("BurnTime", (short)this.litTime);
        tag.putShort("CookTime", (short)this.cookingProgress);
        tag.putShort("CookTimeTotal", (short)this.cookingTotalTime);
        ContainerHelper.saveAllItems(tag, this.items, registries);
        CompoundTag compoundTag = new CompoundTag();
        this.recipesUsed.forEach((id, index) -> compoundTag.putInt(id.toString(), index));
        tag.put("RecipesUsed", compoundTag);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity) {
        boolean bl = blockEntity.isLit();
        boolean bl2 = false;
        if (blockEntity.isLit()) {
            blockEntity.litTime--;
        }

        ItemStack itemStack = blockEntity.items.get(1);
        ItemStack itemStack2 = blockEntity.items.get(0);
        boolean bl3 = !itemStack2.isEmpty();
        boolean bl4 = !itemStack.isEmpty();
        if (blockEntity.isLit() || bl4 && bl3) {
            RecipeHolder<?> recipeHolder;
            if (bl3) {
                recipeHolder = blockEntity.quickCheck.getRecipeFor(new SingleRecipeInput(itemStack2), level).orElse(null);
            } else {
                recipeHolder = null;
            }

            int i = blockEntity.getMaxStackSize();
            if (!blockEntity.isLit() && canBurn(level.registryAccess(), recipeHolder, blockEntity.items, i)) {
                blockEntity.litTime = blockEntity.getBurnDuration(itemStack);
                blockEntity.litDuration = blockEntity.litTime;
                if (blockEntity.isLit()) {
                    bl2 = true;
                    if (bl4) {
                        Item item = itemStack.getItem();
                        itemStack.shrink(1);
                        if (itemStack.isEmpty()) {
                            Item item2 = item.getCraftingRemainingItem();
                            blockEntity.items.set(1, item2 == null ? ItemStack.EMPTY : new ItemStack(item2));
                        }
                    }
                }
            }

            if (blockEntity.isLit() && canBurn(level.registryAccess(), recipeHolder, blockEntity.items, i)) {
                blockEntity.cookingProgress++;
                if (blockEntity.cookingProgress == blockEntity.cookingTotalTime) {
                    blockEntity.cookingProgress = 0;
                    blockEntity.cookingTotalTime = getTotalCookTime(level, blockEntity);
                    if (burn(level.registryAccess(), recipeHolder, blockEntity.items, i)) {
                        blockEntity.setRecipeUsed(recipeHolder);
                    }

                    bl2 = true;
                }
            } else {
                blockEntity.cookingProgress = 0;
            }
        } else if (!blockEntity.isLit() && blockEntity.cookingProgress > 0) {
            blockEntity.cookingProgress = Mth.clamp(blockEntity.cookingProgress - 2, 0, blockEntity.cookingTotalTime);
        }

        if (bl != blockEntity.isLit()) {
            bl2 = true;
            state = state.setValue(AbstractFurnaceBlock.LIT, blockEntity.isLit());
            level.setBlock(pos, state, 3);
        }

        if (bl2) {
            setChanged(level, pos, state);
        }
    }

    private static boolean canBurn(RegistryAccess registryAccess, @Nullable RecipeHolder<?> recipe, NonNullList<ItemStack> inventory, int maxStackSize) {
        if (!inventory.get(0).isEmpty() && recipe != null) {
            ItemStack itemStack = recipe.value().getResultItem(registryAccess);
            if (itemStack.isEmpty()) {
                return false;
            } else {
                ItemStack itemStack2 = inventory.get(2);
                if (itemStack2.isEmpty()) {
                    return true;
                } else if (!ItemStack.isSameItemSameComponents(itemStack2, itemStack)) {
                    return false;
                } else {
                    return itemStack2.getCount() < maxStackSize && itemStack2.getCount() < itemStack2.getMaxStackSize()
                        ? true
                        : itemStack2.getCount() < itemStack.getMaxStackSize();
                }
            }
        } else {
            return false;
        }
    }

    private static boolean burn(RegistryAccess registryAccess, @Nullable RecipeHolder<?> recipe, NonNullList<ItemStack> inventory, int maxStackSize) {
        if (recipe != null && canBurn(registryAccess, recipe, inventory, maxStackSize)) {
            ItemStack itemStack = inventory.get(0);
            ItemStack itemStack2 = recipe.value().getResultItem(registryAccess);
            ItemStack itemStack3 = inventory.get(2);
            if (itemStack3.isEmpty()) {
                inventory.set(2, itemStack2.copy());
            } else if (ItemStack.isSameItemSameComponents(itemStack3, itemStack2)) {
                itemStack3.grow(1);
            }

            if (itemStack.is(Blocks.WET_SPONGE.asItem()) && !inventory.get(1).isEmpty() && inventory.get(1).is(Items.BUCKET)) {
                inventory.set(1, new ItemStack(Items.WATER_BUCKET));
            }

            itemStack.shrink(1);
            return true;
        } else {
            return false;
        }
    }

    protected int getBurnDuration(ItemStack fuel) {
        if (fuel.isEmpty()) {
            return 0;
        }

        Item item = fuel.getItem();
        return getFuel().getOrDefault(item, 0);
    }

    private static int getTotalCookTime(Level level, AbstractFurnaceBlockEntity blockEntity) {
        SingleRecipeInput singleRecipeInput = new SingleRecipeInput(blockEntity.getItem(0));
        return blockEntity.quickCheck.getRecipeFor(singleRecipeInput, level).map(recipeHolder -> recipeHolder.value().getCookingTime()).orElse(200);
    }

    public static boolean isFuel(ItemStack stack) {
        return getFuel().containsKey(stack.getItem());
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return SLOTS_FOR_DOWN;
        } else {
            return side == Direction.UP ? SLOTS_FOR_UP : SLOTS_FOR_SIDES;
        }
    }

    /**
     * Returns {@code true} if automation can insert the given item in the given slot from the given side.
     */
    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
        return this.canPlaceItem(index, itemStack);
    }

    /**
     * Returns {@code true} if automation can extract the given item in the given slot from the given side.
     */
    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return direction == Direction.DOWN && index == 1 ? stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET) : true;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    /**
     * Sets the given item stack to the specified slot in the inventory (can be crafting or armor sections).
     */
    @Override
    public void setItem(int index, ItemStack stack) {
        ItemStack itemStack = this.items.get(index);
        boolean bl = !stack.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, stack);
        this.items.set(index, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        if (index == 0 && !bl) {
            this.cookingTotalTime = getTotalCookTime(this.level, this);
            this.cookingProgress = 0;
            this.setChanged();
        }
    }

    /**
     * Returns {@code true} if automation is allowed to insert the given stack (ignoring stack size) into the given slot. For guis use Slot.isItemValid
     */
    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        if (index == 2) {
            return false;
        }

        if (index != 1) {
            return true;
        }

        ItemStack itemStack = this.items.get(1);
        return isFuel(stack) || stack.is(Items.BUCKET) && !itemStack.is(Items.BUCKET);
    }

    @Override
    public void setRecipeUsed(@Nullable RecipeHolder<?> recipe) {
        if (recipe != null) {
            ResourceLocation resourceLocation = recipe.id();
            this.recipesUsed.addTo(resourceLocation, 1);
        }
    }

    @Nullable
    @Override
    public RecipeHolder<?> getRecipeUsed() {
        return null;
    }

    @Override
    public void awardUsedRecipes(Player player, List<ItemStack> items) {
    }

    public void awardUsedRecipesAndPopExperience(ServerPlayer player) {
        List<RecipeHolder<?>> list = this.getRecipesToAwardAndPopExperience(player.serverLevel(), player.position());
        player.awardRecipes(list);

        for (RecipeHolder<?> recipeHolder : list) {
            if (recipeHolder != null) {
                player.triggerRecipeCrafted(recipeHolder, this.items);
            }
        }

        this.recipesUsed.clear();
    }

    public List<RecipeHolder<?>> getRecipesToAwardAndPopExperience(ServerLevel level, Vec3 popVec) {
        List<RecipeHolder<?>> list = Lists.newArrayList();

        for (Entry<ResourceLocation> entry : this.recipesUsed.object2IntEntrySet()) {
            level.getRecipeManager().byKey(entry.getKey()).ifPresent(recipeHolder -> {
                list.add((RecipeHolder<?>)recipeHolder);
                createExperience(level, popVec, entry.getIntValue(), ((AbstractCookingRecipe)recipeHolder.value()).getExperience());
            });
        }

        return list;
    }

    private static void createExperience(ServerLevel level, Vec3 popVec, int recipeIndex, float experience) {
        int i = Mth.floor(recipeIndex * experience);
        float f = Mth.frac(recipeIndex * experience);
        if (f != 0.0F && Math.random() < f) {
            i++;
        }

        ExperienceOrb.award(level, popVec, i);
    }

    @Override
    public void fillStackedContents(StackedContents helper) {
        for (ItemStack itemStack : this.items) {
            helper.accountStack(itemStack);
        }
    }
}
