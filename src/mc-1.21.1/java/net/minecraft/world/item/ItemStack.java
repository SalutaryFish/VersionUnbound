package net.minecraft.world.item;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.NullOps;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

public final class ItemStack implements DataComponentHolder {
    public static final Codec<Holder<Item>> ITEM_NON_AIR_CODEC = BuiltInRegistries.ITEM
        .holderByNameCodec()
        .validate(item -> item.is(Items.AIR.builtInRegistryHolder()) ? DataResult.error(() -> "Item must not be minecraft:air") : DataResult.success(item));
    public static final Codec<ItemStack> CODEC = Codec.lazyInitialized(
        () -> RecordCodecBuilder.create(
            instance -> instance.group(
                    ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
                    ExtraCodecs.intRange(1, 99).fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
                    DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(itemStack -> itemStack.components.asPatch())
                )
                .apply(instance, ItemStack::new)
        )
    );
    public static final Codec<ItemStack> SINGLE_ITEM_CODEC = Codec.lazyInitialized(
        () -> RecordCodecBuilder.create(
            instance -> instance.group(
                    ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
                    DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(itemStack -> itemStack.components.asPatch())
                )
                .apply(instance, (item, components) -> new ItemStack(item, 1, components))
        )
    );
    public static final Codec<ItemStack> STRICT_CODEC = CODEC.validate(ItemStack::validateStrict);
    public static final Codec<ItemStack> STRICT_SINGLE_ITEM_CODEC = SINGLE_ITEM_CODEC.validate(ItemStack::validateStrict);
    public static final Codec<ItemStack> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC)
        .xmap(optional -> optional.orElse(ItemStack.EMPTY), itemStack -> itemStack.isEmpty() ? Optional.empty() : Optional.of(itemStack));
    public static final Codec<ItemStack> SIMPLE_ITEM_CODEC = ITEM_NON_AIR_CODEC.xmap(ItemStack::new, ItemStack::getItemHolder);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
        private static final StreamCodec<RegistryFriendlyByteBuf, Holder<Item>> ITEM_STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ITEM);

        public ItemStack decode(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
            int i = registryFriendlyByteBuf.readVarInt();
            if (i <= 0) {
                return ItemStack.EMPTY;
            }

            Holder<Item> holder = ITEM_STREAM_CODEC.decode(registryFriendlyByteBuf);
            DataComponentPatch dataComponentPatch = DataComponentPatch.STREAM_CODEC.decode(registryFriendlyByteBuf);
            return new ItemStack(holder, i, dataComponentPatch);
        }

        public void encode(RegistryFriendlyByteBuf registryFriendlyByteBuf, ItemStack itemStack) {
            if (itemStack.isEmpty()) {
                registryFriendlyByteBuf.writeVarInt(0);
            } else {
                registryFriendlyByteBuf.writeVarInt(itemStack.getCount());
                ITEM_STREAM_CODEC.encode(registryFriendlyByteBuf, itemStack.getItemHolder());
                DataComponentPatch.STREAM_CODEC.encode(registryFriendlyByteBuf, itemStack.components.asPatch());
            }
        }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
        public ItemStack decode(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
            ItemStack itemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(registryFriendlyByteBuf);
            if (itemStack.isEmpty()) {
                throw new DecoderException("Empty ItemStack not allowed");
            } else {
                return itemStack;
            }
        }

        public void encode(RegistryFriendlyByteBuf registryFriendlyByteBuf, ItemStack itemStack) {
            if (itemStack.isEmpty()) {
                throw new EncoderException("Empty ItemStack not allowed");
            }

            ItemStack.OPTIONAL_STREAM_CODEC.encode(registryFriendlyByteBuf, itemStack);
        }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> OPTIONAL_LIST_STREAM_CODEC = OPTIONAL_STREAM_CODEC.apply(
        ByteBufCodecs.collection(NonNullList::createWithCapacity)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> LIST_STREAM_CODEC = STREAM_CODEC.apply(
        ByteBufCodecs.collection(NonNullList::createWithCapacity)
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ItemStack EMPTY = new ItemStack((Void)null);
    private static final Component DISABLED_ITEM_TOOLTIP = Component.translatable("item.disabled").withStyle(ChatFormatting.RED);
    private int count;
    private int popTime;
    @Deprecated
    @Nullable
    private final Item item;
    final PatchedDataComponentMap components;
    /**
     * The entity the item is attached to, like an Item Frame.
     */
    @Nullable
    private Entity entityRepresentation;

    private static DataResult<ItemStack> validateStrict(ItemStack stack) {
        DataResult<Unit> dataResult = validateComponents(stack.getComponents());
        if (dataResult.isError()) {
            return dataResult.map(unit -> stack);
        } else {
            return stack.getCount() > stack.getMaxStackSize()
                ? DataResult.error(() -> "Item stack with stack size of " + stack.getCount() + " was larger than maximum: " + stack.getMaxStackSize())
                : DataResult.success(stack);
        }
    }

    public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> validatedStreamCodec(StreamCodec<RegistryFriendlyByteBuf, ItemStack> codec) {
        return new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
            public ItemStack decode(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
                ItemStack itemStack = codec.decode(registryFriendlyByteBuf);
                if (!itemStack.isEmpty()) {
                    RegistryOps<Unit> registryOps = registryFriendlyByteBuf.registryAccess().createSerializationContext(NullOps.INSTANCE);
                    ItemStack.CODEC.encodeStart(registryOps, itemStack).getOrThrow(DecoderException::new);
                }

                return itemStack;
            }

            public void encode(RegistryFriendlyByteBuf registryFriendlyByteBuf, ItemStack itemStack) {
                codec.encode(registryFriendlyByteBuf, itemStack);
            }
        };
    }

    public Optional<TooltipComponent> getTooltipImage() {
        return this.getItem().getTooltipImage(this);
    }

    @Override
    public DataComponentMap getComponents() {
        return !this.isEmpty() ? this.components : DataComponentMap.EMPTY;
    }

    public DataComponentMap getPrototype() {
        return !this.isEmpty() ? this.getItem().components() : DataComponentMap.EMPTY;
    }

    public DataComponentPatch getComponentsPatch() {
        return !this.isEmpty() ? this.components.asPatch() : DataComponentPatch.EMPTY;
    }

    public ItemStack(ItemLike item) {
        this(item, 1);
    }

    public ItemStack(Holder<Item> tag) {
        this(tag.value(), 1);
    }

    public ItemStack(Holder<Item> tag, int count, DataComponentPatch components) {
        this(tag.value(), count, PatchedDataComponentMap.fromPatch(tag.value().components(), components));
    }

    public ItemStack(Holder<Item> item, int count) {
        this(item.value(), count);
    }

    public ItemStack(ItemLike item, int count) {
        this(item, count, new PatchedDataComponentMap(item.asItem().components()));
    }

    private ItemStack(ItemLike item, int count, PatchedDataComponentMap components) {
        this.item = item.asItem();
        this.count = count;
        this.components = components;
        this.getItem().verifyComponentsAfterLoad(this);
    }

    private ItemStack(@Nullable Void unused) {
        this.item = null;
        this.components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
    }

    public static DataResult<Unit> validateComponents(DataComponentMap components) {
        if (components.has(DataComponents.MAX_DAMAGE) && components.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
            return DataResult.error(() -> "Item cannot be both damageable and stackable");
        }

        ItemContainerContents itemContainerContents = components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

        for (ItemStack itemStack : itemContainerContents.nonEmptyItems()) {
            int i = itemStack.getCount();
            int j = itemStack.getMaxStackSize();
            if (i > j) {
                return DataResult.error(() -> "Item stack with count of " + i + " was larger than maximum: " + j);
            }
        }

        return DataResult.success(Unit.INSTANCE);
    }

    public static Optional<ItemStack> parse(HolderLookup.Provider lookupProvider, Tag tag) {
        return CODEC.parse(lookupProvider.createSerializationContext(NbtOps.INSTANCE), tag)
            .resultOrPartial(string -> LOGGER.error("Tried to load invalid item: '{}'", string));
    }

    public static ItemStack parseOptional(HolderLookup.Provider lookupProvider, CompoundTag tag) {
        return tag.isEmpty() ? EMPTY : parse(lookupProvider, tag).orElse(EMPTY);
    }

    public boolean isEmpty() {
        return this == EMPTY || this.item == Items.AIR || this.count <= 0;
    }

    public boolean isItemEnabled(FeatureFlagSet enabledFlags) {
        return this.isEmpty() || this.getItem().isEnabled(enabledFlags);
    }

    /**
     * Splits off a stack of the given amount of this stack and reduces this stack by the amount.
     */
    public ItemStack split(int amount) {
        int i = Math.min(amount, this.getCount());
        ItemStack itemStack = this.copyWithCount(i);
        this.shrink(i);
        return itemStack;
    }

    public ItemStack copyAndClear() {
        if (this.isEmpty()) {
            return EMPTY;
        }

        ItemStack itemStack = this.copy();
        this.setCount(0);
        return itemStack;
    }

    public Item getItem() {
        return this.isEmpty() ? Items.AIR : this.item;
    }

    public Holder<Item> getItemHolder() {
        return this.getItem().builtInRegistryHolder();
    }

    public boolean is(TagKey<Item> tag) {
        return this.getItem().builtInRegistryHolder().is(tag);
    }

    public boolean is(Item item) {
        return this.getItem() == item;
    }

    public boolean is(Predicate<Holder<Item>> item) {
        return item.test(this.getItem().builtInRegistryHolder());
    }

    public boolean is(Holder<Item> item) {
        return this.getItem().builtInRegistryHolder() == item;
    }

    public boolean is(HolderSet<Item> item) {
        return item.contains(this.getItemHolder());
    }

    public Stream<TagKey<Item>> getTags() {
        return this.getItem().builtInRegistryHolder().tags();
    }

    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        BlockPos blockPos = context.getClickedPos();
        if (player != null && !player.getAbilities().mayBuild && !this.canPlaceOnBlockInAdventureMode(new BlockInWorld(context.getLevel(), blockPos, false))) {
            return InteractionResult.PASS;
        }

        Item item = this.getItem();
        InteractionResult interactionResult = item.useOn(context);
        if (player != null && interactionResult.indicateItemUse()) {
            player.awardStat(Stats.ITEM_USED.get(item));
        }

        return interactionResult;
    }

    public float getDestroySpeed(BlockState state) {
        return this.getItem().getDestroySpeed(this, state);
    }

    /**
     * Called when the {@code ItemStack} is equipped and right-clicked. Replaces the {@code ItemStack} with the return value.
     */
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        return this.getItem().use(level, player, usedHand);
    }

    /**
     * Called when the item in use count reach 0, e.g. item food eaten. Return the new ItemStack. Args : world, entity
     */
    public ItemStack finishUsingItem(Level level, LivingEntity livingEntity) {
        return this.getItem().finishUsingItem(this, level, livingEntity);
    }

    public Tag save(HolderLookup.Provider levelRegistryAccess, Tag outputTag) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot encode empty ItemStack");
        } else {
            return CODEC.encode(this, levelRegistryAccess.createSerializationContext(NbtOps.INSTANCE), outputTag).getOrThrow();
        }
    }

    public Tag save(HolderLookup.Provider levelRegistryAccess) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot encode empty ItemStack");
        } else {
            return CODEC.encodeStart(levelRegistryAccess.createSerializationContext(NbtOps.INSTANCE), this).getOrThrow();
        }
    }

    public Tag saveOptional(HolderLookup.Provider levelRegistryAccess) {
        return this.isEmpty() ? new CompoundTag() : this.save(levelRegistryAccess, new CompoundTag());
    }

    public int getMaxStackSize() {
        return this.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }

    public boolean isStackable() {
        return this.getMaxStackSize() > 1 && (!this.isDamageableItem() || !this.isDamaged());
    }

    public boolean isDamageableItem() {
        return this.has(DataComponents.MAX_DAMAGE) && !this.has(DataComponents.UNBREAKABLE) && this.has(DataComponents.DAMAGE);
    }

    public boolean isDamaged() {
        return this.isDamageableItem() && this.getDamageValue() > 0;
    }

    public int getDamageValue() {
        return Mth.clamp(this.getOrDefault(DataComponents.DAMAGE, 0), 0, this.getMaxDamage());
    }

    public void setDamageValue(int damage) {
        this.set(DataComponents.DAMAGE, Mth.clamp(damage, 0, this.getMaxDamage()));
    }

    public int getMaxDamage() {
        return this.getOrDefault(DataComponents.MAX_DAMAGE, 0);
    }

    public void hurtAndBreak(int damage, ServerLevel level, @Nullable ServerPlayer player, Consumer<Item> onBreak) {
        if (this.isDamageableItem()) {
            if (player == null || !player.hasInfiniteMaterials()) {
                if (damage > 0) {
                    damage = EnchantmentHelper.processDurabilityChange(level, this, damage);
                    if (damage <= 0) {
                        return;
                    }
                }

                if (player != null && damage != 0) {
                    CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(player, this, this.getDamageValue() + damage);
                }

                int i = this.getDamageValue() + damage;
                this.setDamageValue(i);
                if (i >= this.getMaxDamage()) {
                    Item item = this.getItem();
                    this.shrink(1);
                    onBreak.accept(item);
                }
            }
        }
    }

    public void hurtAndBreak(int amount, LivingEntity entity, EquipmentSlot slot) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            this.hurtAndBreak(
                amount, serverLevel, entity instanceof ServerPlayer serverPlayer ? serverPlayer : null, item -> entity.onEquippedItemBroken(item, slot)
            );
        }
    }

    public ItemStack hurtAndConvertOnBreak(int amount, ItemLike item, LivingEntity entity, EquipmentSlot slot) {
        this.hurtAndBreak(amount, entity, slot);
        if (this.isEmpty()) {
            ItemStack itemStack = this.transmuteCopyIgnoreEmpty(item, 1);
            if (itemStack.isDamageableItem()) {
                itemStack.setDamageValue(0);
            }

            return itemStack;
        } else {
            return this;
        }
    }

    public boolean isBarVisible() {
        return this.getItem().isBarVisible(this);
    }

    public int getBarWidth() {
        return this.getItem().getBarWidth(this);
    }

    public int getBarColor() {
        return this.getItem().getBarColor(this);
    }

    public boolean overrideStackedOnOther(Slot slot, ClickAction action, Player player) {
        return this.getItem().overrideStackedOnOther(this, slot, action, player);
    }

    public boolean overrideOtherStackedOnMe(ItemStack stack, Slot slot, ClickAction action, Player player, SlotAccess access) {
        return this.getItem().overrideOtherStackedOnMe(this, stack, slot, action, player, access);
    }

    public boolean hurtEnemy(LivingEntity target, Player attacker) {
        Item item = this.getItem();
        if (item.hurtEnemy(this, target, attacker)) {
            attacker.awardStat(Stats.ITEM_USED.get(item));
            return true;
        } else {
            return false;
        }
    }

    public void postHurtEnemy(LivingEntity target, Player attacker) {
        this.getItem().postHurtEnemy(this, target, attacker);
    }

    /**
     * Called when a Block is destroyed using this ItemStack
     */
    public void mineBlock(Level level, BlockState state, BlockPos pos, Player player) {
        Item item = this.getItem();
        if (item.mineBlock(this, level, state, pos, player)) {
            player.awardStat(Stats.ITEM_USED.get(item));
        }
    }

    /**
     * Check whether the given Block can be harvested using this ItemStack.
     */
    public boolean isCorrectToolForDrops(BlockState state) {
        return this.getItem().isCorrectToolForDrops(this, state);
    }

    public InteractionResult interactLivingEntity(Player player, LivingEntity entity, InteractionHand usedHand) {
        return this.getItem().interactLivingEntity(this, player, entity, usedHand);
    }

    public ItemStack copy() {
        if (this.isEmpty()) {
            return EMPTY;
        }

        ItemStack itemStack = new ItemStack(this.getItem(), this.count, this.components.copy());
        itemStack.setPopTime(this.getPopTime());
        return itemStack;
    }

    public ItemStack copyWithCount(int count) {
        if (this.isEmpty()) {
            return EMPTY;
        }

        ItemStack itemStack = this.copy();
        itemStack.setCount(count);
        return itemStack;
    }

    public ItemStack transmuteCopy(ItemLike item) {
        return this.transmuteCopy(item, this.getCount());
    }

    public ItemStack transmuteCopy(ItemLike item, int count) {
        return this.isEmpty() ? EMPTY : this.transmuteCopyIgnoreEmpty(item, count);
    }

    private ItemStack transmuteCopyIgnoreEmpty(ItemLike item, int count) {
        return new ItemStack(item.asItem().builtInRegistryHolder(), count, this.components.asPatch());
    }

    /**
     * Compares both {@code ItemStacks}, returns {@code true} if both {@code ItemStacks} are equal.
     */
    public static boolean matches(ItemStack stack, ItemStack other) {
        if (stack == other) {
            return true;
        } else {
            return stack.getCount() != other.getCount() ? false : isSameItemSameComponents(stack, other);
        }
    }

    @Deprecated
    public static boolean listMatches(List<ItemStack> list, List<ItemStack> other) {
        if (list.size() != other.size()) {
            return false;
        }

        for (int i = 0; i < list.size(); i++) {
            if (!matches(list.get(i), other.get(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean isSameItem(ItemStack stack, ItemStack other) {
        return stack.is(other.getItem());
    }

    public static boolean isSameItemSameComponents(ItemStack stack, ItemStack other) {
        if (!stack.is(other.getItem())) {
            return false;
        } else {
            return stack.isEmpty() && other.isEmpty() ? true : Objects.equals(stack.components, other.components);
        }
    }

    public static MapCodec<ItemStack> lenientOptionalFieldOf(String fieldName) {
        return CODEC.lenientOptionalFieldOf(fieldName)
            .xmap(optional -> optional.orElse(EMPTY), itemStack -> itemStack.isEmpty() ? Optional.empty() : Optional.of(itemStack));
    }

    public static int hashItemAndComponents(@Nullable ItemStack stack) {
        if (stack != null) {
            int i = 31 + stack.getItem().hashCode();
            return 31 * i + stack.getComponents().hashCode();
        } else {
            return 0;
        }
    }

    @Deprecated
    public static int hashStackList(List<ItemStack> list) {
        int i = 0;

        for (ItemStack itemStack : list) {
            i = i * 31 + hashItemAndComponents(itemStack);
        }

        return i;
    }

    public String getDescriptionId() {
        return this.getItem().getDescriptionId(this);
    }

    @Override
    public String toString() {
        return this.getCount() + " " + this.getItem();
    }

    /**
     * Called each tick as long the {@code ItemStack} in in player's inventory. Used to progress the pickup animation and update maps.
     */
    public void inventoryTick(Level level, Entity entity, int inventorySlot, boolean isCurrentItem) {
        if (this.popTime > 0) {
            this.popTime--;
        }

        if (this.getItem() != null) {
            this.getItem().inventoryTick(this, level, entity, inventorySlot, isCurrentItem);
        }
    }

    public void onCraftedBy(Level level, Player player, int amount) {
        player.awardStat(Stats.ITEM_CRAFTED.get(this.getItem()), amount);
        this.getItem().onCraftedBy(this, level, player);
    }

    public void onCraftedBySystem(Level level) {
        this.getItem().onCraftedPostProcess(this, level);
    }

    public int getUseDuration(LivingEntity entity) {
        return this.getItem().getUseDuration(this, entity);
    }

    public UseAnim getUseAnimation() {
        return this.getItem().getUseAnimation(this);
    }

    /**
     * Called when the player releases the use item button.
     */
    public void releaseUsing(Level level, LivingEntity livingEntity, int timeLeft) {
        this.getItem().releaseUsing(this, level, livingEntity, timeLeft);
    }

    public boolean useOnRelease() {
        return this.getItem().useOnRelease(this);
    }

    @Nullable
    public <T> T set(DataComponentType<? super T> component, @Nullable T value) {
        return this.components.set(component, value);
    }

    @Nullable
    public <T, U> T update(DataComponentType<T> component, T defaultValue, U updateValue, BiFunction<T, U, T> updater) {
        return this.set(component, updater.apply(this.getOrDefault(component, defaultValue), updateValue));
    }

    @Nullable
    public <T> T update(DataComponentType<T> component, T defaultValue, UnaryOperator<T> updater) {
        T object = this.getOrDefault(component, defaultValue);
        return this.set(component, updater.apply(object));
    }

    @Nullable
    public <T> T remove(DataComponentType<? extends T> component) {
        return this.components.remove(component);
    }

    public void applyComponentsAndValidate(DataComponentPatch components) {
        DataComponentPatch dataComponentPatch = this.components.asPatch();
        this.components.applyPatch(components);
        Optional<Error<ItemStack>> optional = validateStrict(this).error();
        if (optional.isPresent()) {
            LOGGER.error("Failed to apply component patch '{}' to item: '{}'", components, optional.get().message());
            this.components.restorePatch(dataComponentPatch);
        } else {
            this.getItem().verifyComponentsAfterLoad(this);
        }
    }

    public void applyComponents(DataComponentPatch components) {
        this.components.applyPatch(components);
        this.getItem().verifyComponentsAfterLoad(this);
    }

    public void applyComponents(DataComponentMap components) {
        this.components.setAll(components);
        this.getItem().verifyComponentsAfterLoad(this);
    }

    public Component getHoverName() {
        Component component = this.get(DataComponents.CUSTOM_NAME);
        if (component != null) {
            return component;
        }

        Component component2 = this.get(DataComponents.ITEM_NAME);
        return component2 != null ? component2 : this.getItem().getName(this);
    }

    private <T extends TooltipProvider> void addToTooltip(
        DataComponentType<T> component, Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag
    ) {
        T tooltipProvider = (T)this.get(component);
        if (tooltipProvider != null) {
            tooltipProvider.addToTooltip(context, tooltipAdder, tooltipFlag);
        }
    }

    public List<Component> getTooltipLines(Item.TooltipContext tooltipContext, @Nullable Player player, TooltipFlag tooltipFlag) {
        if (!tooltipFlag.isCreative() && this.has(DataComponents.HIDE_TOOLTIP)) {
            return List.of();
        }

        List<Component> list = Lists.newArrayList();
        MutableComponent mutableComponent = Component.empty().append(this.getHoverName()).withStyle(this.getRarity().color());
        if (this.has(DataComponents.CUSTOM_NAME)) {
            mutableComponent.withStyle(ChatFormatting.ITALIC);
        }

        list.add(mutableComponent);
        if (!tooltipFlag.isAdvanced() && !this.has(DataComponents.CUSTOM_NAME) && this.is(Items.FILLED_MAP)) {
            MapId mapId = this.get(DataComponents.MAP_ID);
            if (mapId != null) {
                list.add(MapItem.getTooltipForId(mapId));
            }
        }

        Consumer<Component> consumer = list::add;
        if (!this.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP)) {
            this.getItem().appendHoverText(this, tooltipContext, list, tooltipFlag);
        }

        this.addToTooltip(DataComponents.JUKEBOX_PLAYABLE, tooltipContext, consumer, tooltipFlag);
        this.addToTooltip(DataComponents.TRIM, tooltipContext, consumer, tooltipFlag);
        this.addToTooltip(DataComponents.STORED_ENCHANTMENTS, tooltipContext, consumer, tooltipFlag);
        this.addToTooltip(DataComponents.ENCHANTMENTS, tooltipContext, consumer, tooltipFlag);
        this.addToTooltip(DataComponents.DYED_COLOR, tooltipContext, consumer, tooltipFlag);
        this.addToTooltip(DataComponents.LORE, tooltipContext, consumer, tooltipFlag);
        this.addAttributeTooltips(consumer, player);
        this.addToTooltip(DataComponents.UNBREAKABLE, tooltipContext, consumer, tooltipFlag);
        AdventureModePredicate adventureModePredicate = this.get(DataComponents.CAN_BREAK);
        if (adventureModePredicate != null && adventureModePredicate.showInTooltip()) {
            consumer.accept(CommonComponents.EMPTY);
            consumer.accept(AdventureModePredicate.CAN_BREAK_HEADER);
            adventureModePredicate.addToTooltip(consumer);
        }

        AdventureModePredicate adventureModePredicate2 = this.get(DataComponents.CAN_PLACE_ON);
        if (adventureModePredicate2 != null && adventureModePredicate2.showInTooltip()) {
            consumer.accept(CommonComponents.EMPTY);
            consumer.accept(AdventureModePredicate.CAN_PLACE_HEADER);
            adventureModePredicate2.addToTooltip(consumer);
        }

        if (tooltipFlag.isAdvanced()) {
            if (this.isDamaged()) {
                list.add(Component.translatable("item.durability", this.getMaxDamage() - this.getDamageValue(), this.getMaxDamage()));
            }

            list.add(Component.literal(BuiltInRegistries.ITEM.getKey(this.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
            int i = this.components.size();
            if (i > 0) {
                list.add(Component.translatable("item.components", i).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        if (player != null && !this.getItem().isEnabled(player.level().enabledFeatures())) {
            list.add(DISABLED_ITEM_TOOLTIP);
        }

        return list;
    }

    private void addAttributeTooltips(Consumer<Component> tooltipAdder, @Nullable Player player) {
        ItemAttributeModifiers itemAttributeModifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (itemAttributeModifiers.showInTooltip()) {
            for (EquipmentSlotGroup equipmentSlotGroup : EquipmentSlotGroup.values()) {
                MutableBoolean mutableBoolean = new MutableBoolean(true);
                this.forEachModifier(equipmentSlotGroup, (holder, attributeModifier) -> {
                    if (mutableBoolean.isTrue()) {
                        tooltipAdder.accept(CommonComponents.EMPTY);
                        tooltipAdder.accept(Component.translatable("item.modifiers." + equipmentSlotGroup.getSerializedName()).withStyle(ChatFormatting.GRAY));
                        mutableBoolean.setFalse();
                    }

                    this.addModifierTooltip(tooltipAdder, player, holder, attributeModifier);
                });
            }
        }
    }

    private void addModifierTooltip(Consumer<Component> tooltipAdder, @Nullable Player player, Holder<Attribute> attribute, AttributeModifier modifier) {
        double d = modifier.amount();
        boolean bl = false;
        if (player != null) {
            if (modifier.is(Item.BASE_ATTACK_DAMAGE_ID)) {
                d += player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
                bl = true;
            } else if (modifier.is(Item.BASE_ATTACK_SPEED_ID)) {
                d += player.getAttributeBaseValue(Attributes.ATTACK_SPEED);
                bl = true;
            }
        }

        double e;
        if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE || modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            )
         {
            e = d * 100.0;
        } else if (attribute.is(Attributes.KNOCKBACK_RESISTANCE)) {
            e = d * 10.0;
        } else {
            e = d;
        }

        if (bl) {
            tooltipAdder.accept(
                CommonComponents.space()
                    .append(
                        Component.translatable(
                            "attribute.modifier.equals." + modifier.operation().id(),
                            ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(e),
                            Component.translatable(attribute.value().getDescriptionId())
                        )
                    )
                    .withStyle(ChatFormatting.DARK_GREEN)
            );
        } else if (d > 0.0) {
            tooltipAdder.accept(
                Component.translatable(
                        "attribute.modifier.plus." + modifier.operation().id(),
                        ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(e),
                        Component.translatable(attribute.value().getDescriptionId())
                    )
                    .withStyle(attribute.value().getStyle(true))
            );
        } else if (d < 0.0) {
            tooltipAdder.accept(
                Component.translatable(
                        "attribute.modifier.take." + modifier.operation().id(),
                        ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(-e),
                        Component.translatable(attribute.value().getDescriptionId())
                    )
                    .withStyle(attribute.value().getStyle(false))
            );
        }
    }

    public boolean hasFoil() {
        Boolean boolean_ = this.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        return boolean_ != null ? boolean_ : this.getItem().isFoil(this);
    }

    public Rarity getRarity() {
        Rarity rarity = this.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
        if (!this.isEnchanted()) {
            return rarity;
        }

        return switch (rarity) {
            case COMMON, UNCOMMON -> Rarity.RARE;
            case RARE -> Rarity.EPIC;
            default -> rarity;
        };
    }

    public boolean isEnchantable() {
        if (!this.getItem().isEnchantable(this)) {
            return false;
        }

        ItemEnchantments itemEnchantments = this.get(DataComponents.ENCHANTMENTS);
        return itemEnchantments != null && itemEnchantments.isEmpty();
    }

    public void enchant(Holder<Enchantment> enchantment, int level) {
        EnchantmentHelper.updateEnchantments(this, mutable -> mutable.upgrade(enchantment, level));
    }

    public boolean isEnchanted() {
        return !this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    public ItemEnchantments getEnchantments() {
        return this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    }

    public boolean isFramed() {
        return this.entityRepresentation instanceof ItemFrame;
    }

    public void setEntityRepresentation(@Nullable Entity entity) {
        if (!this.isEmpty()) {
            this.entityRepresentation = entity;
        }
    }

    @Nullable
    public ItemFrame getFrame() {
        return this.entityRepresentation instanceof ItemFrame ? (ItemFrame)this.getEntityRepresentation() : null;
    }

    @Nullable
    public Entity getEntityRepresentation() {
        return !this.isEmpty() ? this.entityRepresentation : null;
    }

    public void forEachModifier(EquipmentSlotGroup slotGroup, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        ItemAttributeModifiers itemAttributeModifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (!itemAttributeModifiers.modifiers().isEmpty()) {
            itemAttributeModifiers.forEach(slotGroup, action);
        } else {
            this.getItem().getDefaultAttributeModifiers().forEach(slotGroup, action);
        }

        EnchantmentHelper.forEachModifier(this, slotGroup, action);
    }

    public void forEachModifier(EquipmentSlot equipmentSLot, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        ItemAttributeModifiers itemAttributeModifiers = this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (!itemAttributeModifiers.modifiers().isEmpty()) {
            itemAttributeModifiers.forEach(equipmentSLot, action);
        } else {
            this.getItem().getDefaultAttributeModifiers().forEach(equipmentSLot, action);
        }

        EnchantmentHelper.forEachModifier(this, equipmentSLot, action);
    }

    public Component getDisplayName() {
        MutableComponent mutableComponent = Component.empty().append(this.getHoverName());
        if (this.has(DataComponents.CUSTOM_NAME)) {
            mutableComponent.withStyle(ChatFormatting.ITALIC);
        }

        MutableComponent mutableComponent2 = ComponentUtils.wrapInSquareBrackets(mutableComponent);
        if (!this.isEmpty()) {
            mutableComponent2.withStyle(this.getRarity().color())
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(this))));
        }

        return mutableComponent2;
    }

    public boolean canPlaceOnBlockInAdventureMode(BlockInWorld block) {
        AdventureModePredicate adventureModePredicate = this.get(DataComponents.CAN_PLACE_ON);
        return adventureModePredicate != null && adventureModePredicate.test(block);
    }

    public boolean canBreakBlockInAdventureMode(BlockInWorld block) {
        AdventureModePredicate adventureModePredicate = this.get(DataComponents.CAN_BREAK);
        return adventureModePredicate != null && adventureModePredicate.test(block);
    }

    public int getPopTime() {
        return this.popTime;
    }

    public void setPopTime(int popTime) {
        this.popTime = popTime;
    }

    public int getCount() {
        return this.isEmpty() ? 0 : this.count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void limitSize(int maxSize) {
        if (!this.isEmpty() && this.getCount() > maxSize) {
            this.setCount(maxSize);
        }
    }

    public void grow(int increment) {
        this.setCount(this.getCount() + increment);
    }

    public void shrink(int decrement) {
        this.grow(-decrement);
    }

    public void consume(int amount, @Nullable LivingEntity entity) {
        if (entity == null || !entity.hasInfiniteMaterials()) {
            this.shrink(amount);
        }
    }

    public ItemStack consumeAndReturn(int amount, @Nullable LivingEntity entity) {
        ItemStack itemStack = this.copyWithCount(amount);
        this.consume(amount, entity);
        return itemStack;
    }

    /**
     * Called as the stack is being used by an entity.
     */
    public void onUseTick(Level level, LivingEntity livingEntity, int count) {
        this.getItem().onUseTick(level, livingEntity, this, count);
    }

    public void onDestroyed(ItemEntity itemEntity) {
        this.getItem().onDestroyed(itemEntity);
    }

    public SoundEvent getDrinkingSound() {
        return this.getItem().getDrinkingSound();
    }

    public SoundEvent getEatingSound() {
        return this.getItem().getEatingSound();
    }

    public SoundEvent getBreakingSound() {
        return this.getItem().getBreakingSound();
    }

    public boolean canBeHurtBy(DamageSource damageSource) {
        return !this.has(DataComponents.FIRE_RESISTANT) || !damageSource.is(DamageTypeTags.IS_FIRE);
    }
}
