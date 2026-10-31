package net.minecraft.world.level.block.entity;

import java.util.List;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class HopperBlockEntity extends RandomizableContainerBlockEntity implements Hopper {
    public static final int MOVE_ITEM_SPEED = 8;
    public static final int HOPPER_CONTAINER_SIZE = 5;
    private static final int[][] CACHED_SLOTS = new int[54][];
    private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
    private int cooldownTime = -1;
    private long tickedGameTime;
    private Direction facing;

    public HopperBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.HOPPER, pos, blockState);
        this.facing = blockState.getValue(HopperBlock.FACING);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, this.items, registries);
        }

        this.cooldownTime = tag.getInt("TransferCooldown");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, this.items, registries);
        }

        tag.putInt("TransferCooldown", this.cooldownTime);
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        this.unpackLootTable(null);
        return ContainerHelper.removeItem(this.getItems(), index, count);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.unpackLootTable(null);
        this.getItems().set(index, stack);
        stack.limitSize(this.getMaxStackSize(stack));
    }

    @Override
    public void setBlockState(BlockState blockState) {
        super.setBlockState(blockState);
        this.facing = blockState.getValue(HopperBlock.FACING);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.hopper");
    }

    public static void pushItemsTick(Level level, BlockPos pos, BlockState state, HopperBlockEntity blockEntity) {
        blockEntity.cooldownTime--;
        blockEntity.tickedGameTime = level.getGameTime();
        if (!blockEntity.isOnCooldown()) {
            blockEntity.setCooldown(0);
            tryMoveItems(level, pos, state, blockEntity, () -> suckInItems(level, blockEntity));
        }
    }

    private static boolean tryMoveItems(Level level, BlockPos pos, BlockState state, HopperBlockEntity blockEntity, BooleanSupplier validator) {
        if (level.isClientSide) {
            return false;
        }

        if (!blockEntity.isOnCooldown() && state.getValue(HopperBlock.ENABLED)) {
            boolean bl = false;
            if (!blockEntity.isEmpty()) {
                bl = ejectItems(level, pos, blockEntity);
            }

            if (!blockEntity.inventoryFull()) {
                bl |= validator.getAsBoolean();
            }

            if (bl) {
                blockEntity.setCooldown(8);
                setChanged(level, pos, state);
                return true;
            }
        }

        return false;
    }

    private boolean inventoryFull() {
        for (ItemStack itemStack : this.items) {
            if (itemStack.isEmpty() || itemStack.getCount() != itemStack.getMaxStackSize()) {
                return false;
            }
        }

        return true;
    }

    private static boolean ejectItems(Level level, BlockPos pos, HopperBlockEntity blockEntity) {
        Container container = getAttachedContainer(level, pos, blockEntity);
        if (container == null) {
            return false;
        }

        Direction direction = blockEntity.facing.getOpposite();
        if (isFullContainer(container, direction)) {
            return false;
        }

        for (int i = 0; i < blockEntity.getContainerSize(); i++) {
            ItemStack itemStack = blockEntity.getItem(i);
            if (!itemStack.isEmpty()) {
                int j = itemStack.getCount();
                ItemStack itemStack2 = addItem(blockEntity, container, blockEntity.removeItem(i, 1), direction);
                if (itemStack2.isEmpty()) {
                    container.setChanged();
                    return true;
                }

                itemStack.setCount(j);
                if (j == 1) {
                    blockEntity.setItem(i, itemStack);
                }
            }
        }

        return false;
    }

    private static int[] getSlots(Container container, Direction direction) {
        if (container instanceof WorldlyContainer worldlyContainer) {
            return worldlyContainer.getSlotsForFace(direction);
        } else {
            int i = container.getContainerSize();
            if (i < CACHED_SLOTS.length) {
                int[] is = CACHED_SLOTS[i];
                if (is != null) {
                    return is;
                }

                int[] js = createFlatSlots(i);
                CACHED_SLOTS[i] = js;
                return js;
            } else {
                return createFlatSlots(i);
            }
        }
    }

    private static int[] createFlatSlots(int size) {
        int[] is = new int[size];
        int i = 0;

        while (i < is.length) {
            is[i] = i++;
        }

        return is;
    }

    /**
     * @return {@code false} if the {@code container} has any room to place items in
     */
    private static boolean isFullContainer(Container container, Direction direction) {
        int[] is = getSlots(container, direction);

        for (int i : is) {
            ItemStack itemStack = container.getItem(i);
            if (itemStack.getCount() < itemStack.getMaxStackSize()) {
                return false;
            }
        }

        return true;
    }

    public static boolean suckInItems(Level level, Hopper hopper) {
        BlockPos blockPos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ());
        BlockState blockState = level.getBlockState(blockPos);
        Container container = getSourceContainer(level, hopper, blockPos, blockState);
        if (container != null) {
            Direction direction = Direction.DOWN;

            for (int i : getSlots(container, direction)) {
                if (tryTakeInItemFromSlot(hopper, container, i, direction)) {
                    return true;
                }
            }

            return false;
        } else {
            boolean bl = hopper.isGridAligned() && blockState.isCollisionShapeFullBlock(level, blockPos) && !blockState.is(BlockTags.DOES_NOT_BLOCK_HOPPERS);
            if (!bl) {
                for (ItemEntity itemEntity : getItemsAtAndAbove(level, hopper)) {
                    if (addItem(hopper, itemEntity)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    /**
     * Pulls from the specified slot in the container and places in any available slot in the hopper.
     * @return {@code true} if the entire stack was moved.
     */
    private static boolean tryTakeInItemFromSlot(Hopper hopper, Container container, int slot, Direction direction) {
        ItemStack itemStack = container.getItem(slot);
        if (!itemStack.isEmpty() && canTakeItemFromContainer(hopper, container, itemStack, slot, direction)) {
            int i = itemStack.getCount();
            ItemStack itemStack2 = addItem(container, hopper, container.removeItem(slot, 1), null);
            if (itemStack2.isEmpty()) {
                container.setChanged();
                return true;
            }

            itemStack.setCount(i);
            if (i == 1) {
                container.setItem(slot, itemStack);
            }
        }

        return false;
    }

    public static boolean addItem(Container container, ItemEntity item) {
        boolean bl = false;
        ItemStack itemStack = item.getItem().copy();
        ItemStack itemStack2 = addItem(null, container, itemStack, null);
        if (itemStack2.isEmpty()) {
            bl = true;
            item.setItem(ItemStack.EMPTY);
            item.discard();
        } else {
            item.setItem(itemStack2);
        }

        return bl;
    }

    /**
     * Attempts to place the passed stack in the container, using as many slots as required.
     * @return any leftover stack
     */
    public static ItemStack addItem(@Nullable Container source, Container destination, ItemStack stack, @Nullable Direction direction) {
        if (destination instanceof WorldlyContainer worldlyContainer && direction != null) {
            int[] is = worldlyContainer.getSlotsForFace(direction);

            for (int i = 0; i < is.length && !stack.isEmpty(); i++) {
                stack = tryMoveInItem(source, destination, stack, is[i], direction);
            }
        } else {
            int j = destination.getContainerSize();

            for (int i = 0; i < j && !stack.isEmpty(); i++) {
                stack = tryMoveInItem(source, destination, stack, i, direction);
            }
        }

        return stack;
    }

    private static boolean canPlaceItemInContainer(Container container, ItemStack stack, int slot, @Nullable Direction direction) {
        return !container.canPlaceItem(slot, stack)
            ? false
            : !(container instanceof WorldlyContainer worldlyContainer && !worldlyContainer.canPlaceItemThroughFace(slot, stack, direction));
    }

    private static boolean canTakeItemFromContainer(Container source, Container destination, ItemStack stack, int slot, Direction direction) {
        return !destination.canTakeItem(source, slot, stack)
            ? false
            : !(destination instanceof WorldlyContainer worldlyContainer && !worldlyContainer.canTakeItemThroughFace(slot, stack, direction));
    }

    private static ItemStack tryMoveInItem(@Nullable Container source, Container destination, ItemStack stack, int slot, @Nullable Direction direction) {
        ItemStack itemStack = destination.getItem(slot);
        if (canPlaceItemInContainer(destination, stack, slot, direction)) {
            boolean bl = false;
            boolean bl2 = destination.isEmpty();
            if (itemStack.isEmpty()) {
                destination.setItem(slot, stack);
                stack = ItemStack.EMPTY;
                bl = true;
            } else if (canMergeItems(itemStack, stack)) {
                int i = stack.getMaxStackSize() - itemStack.getCount();
                int j = Math.min(stack.getCount(), i);
                stack.shrink(j);
                itemStack.grow(j);
                bl = j > 0;
            }

            if (bl) {
                if (bl2 && destination instanceof HopperBlockEntity hopperBlockEntity && !hopperBlockEntity.isOnCustomCooldown()) {
                    int j = 0;
                    if (source instanceof HopperBlockEntity hopperBlockEntity2 && hopperBlockEntity.tickedGameTime >= hopperBlockEntity2.tickedGameTime) {
                        j = 1;
                    }

                    hopperBlockEntity.setCooldown(8 - j);
                }

                destination.setChanged();
            }
        }

        return stack;
    }

    @Nullable
    private static Container getAttachedContainer(Level level, BlockPos pos, HopperBlockEntity blockEntity) {
        return getContainerAt(level, pos.relative(blockEntity.facing));
    }

    @Nullable
    private static Container getSourceContainer(Level level, Hopper hopper, BlockPos pos, BlockState state) {
        return getContainerAt(level, pos, state, hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ());
    }

    public static List<ItemEntity> getItemsAtAndAbove(Level level, Hopper hopper) {
        AABB aABB = hopper.getSuckAabb().move(hopper.getLevelX() - 0.5, hopper.getLevelY() - 0.5, hopper.getLevelZ() - 0.5);
        return level.getEntitiesOfClass(ItemEntity.class, aABB, EntitySelector.ENTITY_STILL_ALIVE);
    }

    @Nullable
    public static Container getContainerAt(Level level, BlockPos pos) {
        return getContainerAt(level, pos, level.getBlockState(pos), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    @Nullable
    private static Container getContainerAt(Level level, BlockPos pos, BlockState state, double x, double y, double z) {
        Container container = getBlockContainer(level, pos, state);
        if (container == null) {
            container = getEntityContainer(level, x, y, z);
        }

        return container;
    }

    @Nullable
    private static Container getBlockContainer(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof WorldlyContainerHolder) {
            return ((WorldlyContainerHolder)block).getContainer(state, level, pos);
        }

        if (state.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container container) {
            if (container instanceof ChestBlockEntity && block instanceof ChestBlock) {
                container = ChestBlock.getContainer((ChestBlock)block, state, level, pos, true);
            }

            return container;
        } else {
            return null;
        }
    }

    @Nullable
    private static Container getEntityContainer(Level level, double x, double y, double z) {
        List<Entity> list = level.getEntities(
            (Entity)null, new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5), EntitySelector.CONTAINER_ENTITY_SELECTOR
        );
        return !list.isEmpty() ? (Container)list.get(level.random.nextInt(list.size())) : null;
    }

    private static boolean canMergeItems(ItemStack stack1, ItemStack stack2) {
        return stack1.getCount() <= stack1.getMaxStackSize() && ItemStack.isSameItemSameComponents(stack1, stack2);
    }

    @Override
    public double getLevelX() {
        return this.worldPosition.getX() + 0.5;
    }

    @Override
    public double getLevelY() {
        return this.worldPosition.getY() + 0.5;
    }

    @Override
    public double getLevelZ() {
        return this.worldPosition.getZ() + 0.5;
    }

    @Override
    public boolean isGridAligned() {
        return true;
    }

    private void setCooldown(int cooldownTime) {
        this.cooldownTime = cooldownTime;
    }

    private boolean isOnCooldown() {
        return this.cooldownTime > 0;
    }

    private boolean isOnCustomCooldown() {
        return this.cooldownTime > 8;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    public static void entityInside(Level level, BlockPos pos, BlockState state, Entity entity, HopperBlockEntity blockEntity) {
        if (entity instanceof ItemEntity itemEntity
            && !itemEntity.getItem().isEmpty()
            && entity.getBoundingBox().move(-pos.getX(), -pos.getY(), -pos.getZ()).intersects(blockEntity.getSuckAabb())) {
            tryMoveItems(level, pos, state, blockEntity, () -> addItem(blockEntity, itemEntity));
        }
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory player) {
        return new HopperMenu(id, player, this);
    }
}
