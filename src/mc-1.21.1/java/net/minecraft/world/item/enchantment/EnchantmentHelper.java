package net.minecraft.world.item.enchantment;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableObject;

public class EnchantmentHelper {
    public static int getItemEnchantmentLevel(Holder<Enchantment> enchantment, ItemStack stack) {
        ItemEnchantments itemEnchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return itemEnchantments.getLevel(enchantment);
    }

    public static ItemEnchantments updateEnchantments(ItemStack stack, Consumer<ItemEnchantments.Mutable> updater) {
        DataComponentType<ItemEnchantments> dataComponentType = getComponentType(stack);
        ItemEnchantments itemEnchantments = stack.get(dataComponentType);
        if (itemEnchantments == null) {
            return ItemEnchantments.EMPTY;
        }

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(itemEnchantments);
        updater.accept(mutable);
        ItemEnchantments itemEnchantments2 = mutable.toImmutable();
        stack.set(dataComponentType, itemEnchantments2);
        return itemEnchantments2;
    }

    public static boolean canStoreEnchantments(ItemStack stack) {
        return stack.has(getComponentType(stack));
    }

    public static void setEnchantments(ItemStack stack, ItemEnchantments enchantments) {
        stack.set(getComponentType(stack), enchantments);
    }

    public static ItemEnchantments getEnchantmentsForCrafting(ItemStack stack) {
        return stack.getOrDefault(getComponentType(stack), ItemEnchantments.EMPTY);
    }

    private static DataComponentType<ItemEnchantments> getComponentType(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
    }

    public static boolean hasAnyEnchantments(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
            || !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    public static int processDurabilityChange(ServerLevel level, ItemStack stack, int damage) {
        MutableFloat mutableFloat = new MutableFloat(damage);
        runIterationOnItem(stack, (holder, i) -> holder.value().modifyDurabilityChange(level, i, stack, mutableFloat));
        return mutableFloat.intValue();
    }

    public static int processAmmoUse(ServerLevel level, ItemStack weapon, ItemStack ammo, int count) {
        MutableFloat mutableFloat = new MutableFloat(count);
        runIterationOnItem(weapon, (holder, i) -> holder.value().modifyAmmoCount(level, i, ammo, mutableFloat));
        return mutableFloat.intValue();
    }

    public static int processBlockExperience(ServerLevel level, ItemStack stack, int experience) {
        MutableFloat mutableFloat = new MutableFloat(experience);
        runIterationOnItem(stack, (holder, i) -> holder.value().modifyBlockExperience(level, i, stack, mutableFloat));
        return mutableFloat.intValue();
    }

    public static int processMobExperience(ServerLevel level, @Nullable Entity killer, Entity mob, int experience) {
        if (killer instanceof LivingEntity livingEntity) {
            MutableFloat mutableFloat = new MutableFloat(experience);
            runIterationOnEquipment(
                livingEntity,
                (holder, i, enchantedItemInUse) -> holder.value().modifyMobExperience(level, i, enchantedItemInUse.itemStack(), mob, mutableFloat)
            );
            return mutableFloat.intValue();
        } else {
            return experience;
        }
    }

    private static void runIterationOnItem(ItemStack stack, EnchantmentHelper.EnchantmentVisitor visitor) {
        ItemEnchantments itemEnchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
            visitor.accept(entry.getKey(), entry.getIntValue());
        }
    }

    private static void runIterationOnItem(ItemStack stack, EquipmentSlot slot, LivingEntity entity, EnchantmentHelper.EnchantmentInSlotVisitor visitor) {
        if (!stack.isEmpty()) {
            ItemEnchantments itemEnchantments = stack.get(DataComponents.ENCHANTMENTS);
            if (itemEnchantments != null && !itemEnchantments.isEmpty()) {
                EnchantedItemInUse enchantedItemInUse = new EnchantedItemInUse(stack, slot, entity);

                for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    if (holder.value().matchingSlot(slot)) {
                        visitor.accept(holder, entry.getIntValue(), enchantedItemInUse);
                    }
                }
            }
        }
    }

    private static void runIterationOnEquipment(LivingEntity entity, EnchantmentHelper.EnchantmentInSlotVisitor visitor) {
        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            runIterationOnItem(entity.getItemBySlot(equipmentSlot), equipmentSlot, entity, visitor);
        }
    }

    public static boolean isImmuneToDamage(ServerLevel level, LivingEntity entity, DamageSource damageSource) {
        MutableBoolean mutableBoolean = new MutableBoolean();
        runIterationOnEquipment(
            entity,
            (holder, i, enchantedItemInUse) -> mutableBoolean.setValue(
                mutableBoolean.isTrue() || holder.value().isImmuneToDamage(level, i, entity, damageSource)
            )
        );
        return mutableBoolean.isTrue();
    }

    public static float getDamageProtection(ServerLevel level, LivingEntity entity, DamageSource damageSource) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnEquipment(
            entity,
            (holder, i, enchantedItemInUse) -> holder.value()
                .modifyDamageProtection(level, i, enchantedItemInUse.itemStack(), entity, damageSource, mutableFloat)
        );
        return mutableFloat.floatValue();
    }

    public static float modifyDamage(ServerLevel level, ItemStack tool, Entity entity, DamageSource damageSource, float damage) {
        MutableFloat mutableFloat = new MutableFloat(damage);
        runIterationOnItem(tool, (holder, i) -> holder.value().modifyDamage(level, i, tool, entity, damageSource, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static float modifyFallBasedDamage(ServerLevel level, ItemStack tool, Entity enity, DamageSource damageSource, float fallBasedDamage) {
        MutableFloat mutableFloat = new MutableFloat(fallBasedDamage);
        runIterationOnItem(tool, (holder, i) -> holder.value().modifyFallBasedDamage(level, i, tool, enity, damageSource, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static float modifyArmorEffectiveness(ServerLevel level, ItemStack tool, Entity entity, DamageSource damageSource, float armorEffectiveness) {
        MutableFloat mutableFloat = new MutableFloat(armorEffectiveness);
        runIterationOnItem(tool, (holder, i) -> holder.value().modifyArmorEffectivness(level, i, tool, entity, damageSource, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static float modifyKnockback(ServerLevel level, ItemStack tool, Entity entity, DamageSource damageSource, float knockback) {
        MutableFloat mutableFloat = new MutableFloat(knockback);
        runIterationOnItem(tool, (holder, i) -> holder.value().modifyKnockback(level, i, tool, entity, damageSource, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static void doPostAttackEffects(ServerLevel level, Entity entity, DamageSource damageSource) {
        if (damageSource.getEntity() instanceof LivingEntity livingEntity) {
            doPostAttackEffectsWithItemSource(level, entity, damageSource, livingEntity.getWeaponItem());
        } else {
            doPostAttackEffectsWithItemSource(level, entity, damageSource, null);
        }
    }

    public static void doPostAttackEffectsWithItemSource(ServerLevel level, Entity entity, DamageSource damageSource, @Nullable ItemStack itemSource) {
        if (entity instanceof LivingEntity livingEntity) {
            runIterationOnEquipment(
                livingEntity,
                (holder, i, enchantedItemInUse) -> holder.value().doPostAttack(level, i, enchantedItemInUse, EnchantmentTarget.VICTIM, entity, damageSource)
            );
        }

        if (itemSource != null && damageSource.getEntity() instanceof LivingEntity livingEntity) {
            runIterationOnItem(
                itemSource,
                EquipmentSlot.MAINHAND,
                livingEntity,
                (holder, i, enchantedItemInUse) -> holder.value().doPostAttack(level, i, enchantedItemInUse, EnchantmentTarget.ATTACKER, entity, damageSource)
            );
        }
    }

    public static void runLocationChangedEffects(ServerLevel level, LivingEntity entity) {
        runIterationOnEquipment(entity, (holder, i, enchantedItemInUse) -> holder.value().runLocationChangedEffects(level, i, enchantedItemInUse, entity));
    }

    public static void runLocationChangedEffects(ServerLevel level, ItemStack stack, LivingEntity entity, EquipmentSlot slot) {
        runIterationOnItem(
            stack, slot, entity, (holder, i, enchantedItemInUse) -> holder.value().runLocationChangedEffects(level, i, enchantedItemInUse, entity)
        );
    }

    public static void stopLocationBasedEffects(LivingEntity entity) {
        runIterationOnEquipment(entity, (holder, i, enchantedItemInUse) -> holder.value().stopLocationBasedEffects(i, enchantedItemInUse, entity));
    }

    public static void stopLocationBasedEffects(ItemStack stack, LivingEntity entity, EquipmentSlot slot) {
        runIterationOnItem(stack, slot, entity, (holder, i, enchantedItemInUse) -> holder.value().stopLocationBasedEffects(i, enchantedItemInUse, entity));
    }

    public static void tickEffects(ServerLevel level, LivingEntity entity) {
        runIterationOnEquipment(entity, (holder, i, enchantedItemInUse) -> holder.value().tick(level, i, enchantedItemInUse, entity));
    }

    public static int getEnchantmentLevel(Holder<Enchantment> enchantment, LivingEntity entity) {
        Iterable<ItemStack> iterable = enchantment.value().getSlotItems(entity).values();
        int i = 0;

        for (ItemStack itemStack : iterable) {
            int j = getItemEnchantmentLevel(enchantment, itemStack);
            if (j > i) {
                i = j;
            }
        }

        return i;
    }

    public static int processProjectileCount(ServerLevel level, ItemStack tool, Entity entity, int projectileCount) {
        MutableFloat mutableFloat = new MutableFloat(projectileCount);
        runIterationOnItem(tool, (holder, i) -> holder.value().modifyProjectileCount(level, i, tool, entity, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static float processProjectileSpread(ServerLevel level, ItemStack tool, Entity entity, float projectileSpread) {
        MutableFloat mutableFloat = new MutableFloat(projectileSpread);
        runIterationOnItem(tool, (holder, i) -> holder.value().modifyProjectileSpread(level, i, tool, entity, mutableFloat));
        return Math.max(0.0F, mutableFloat.floatValue());
    }

    public static int getPiercingCount(ServerLevel level, ItemStack firedFromWeapon, ItemStack pickupItemStack) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(firedFromWeapon, (holder, i) -> holder.value().modifyPiercingCount(level, i, pickupItemStack, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static void onProjectileSpawned(ServerLevel level, ItemStack firedFromWeapon, AbstractArrow arrow, Consumer<Item> onBreak) {
        LivingEntity livingEntity2 = arrow.getOwner() instanceof LivingEntity livingEntity ? livingEntity : null;
        EnchantedItemInUse enchantedItemInUse = new EnchantedItemInUse(firedFromWeapon, null, livingEntity2, onBreak);
        runIterationOnItem(firedFromWeapon, (holder, i) -> holder.value().onProjectileSpawned(level, i, enchantedItemInUse, arrow));
    }

    public static void onHitBlock(
        ServerLevel level,
        ItemStack stack,
        @Nullable LivingEntity owner,
        Entity entity,
        @Nullable EquipmentSlot slot,
        Vec3 pos,
        BlockState state,
        Consumer<Item> onBreak
    ) {
        EnchantedItemInUse enchantedItemInUse = new EnchantedItemInUse(stack, slot, owner, onBreak);
        runIterationOnItem(stack, (holder, i) -> holder.value().onHitBlock(level, i, enchantedItemInUse, entity, pos, state));
    }

    public static int modifyDurabilityToRepairFromXp(ServerLevel level, ItemStack stack, int duabilityToRepairFromXp) {
        MutableFloat mutableFloat = new MutableFloat(duabilityToRepairFromXp);
        runIterationOnItem(stack, (holder, i) -> holder.value().modifyDurabilityToRepairFromXp(level, i, stack, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static float processEquipmentDropChance(ServerLevel level, LivingEntity entity, DamageSource damageSource, float equipmentDropChance) {
        MutableFloat mutableFloat = new MutableFloat(equipmentDropChance);
        RandomSource randomSource = entity.getRandom();
        runIterationOnEquipment(
            entity,
            (holder, i, enchantedItemInUse) -> {
                LootContext lootContext = Enchantment.damageContext(level, i, entity, damageSource);
                holder.value()
                    .getEffects(EnchantmentEffectComponents.EQUIPMENT_DROPS)
                    .forEach(
                        targetedConditionalEffect -> {
                            if (targetedConditionalEffect.enchanted() == EnchantmentTarget.VICTIM
                                && targetedConditionalEffect.affected() == EnchantmentTarget.VICTIM
                                && targetedConditionalEffect.matches(lootContext)) {
                                mutableFloat.setValue(targetedConditionalEffect.effect().process(i, randomSource, mutableFloat.floatValue()));
                            }
                        }
                    );
            }
        );
        if (damageSource.getEntity() instanceof LivingEntity livingEntity) {
            runIterationOnEquipment(
                livingEntity,
                (holder, i, enchantedItemInUse) -> {
                    LootContext lootContext = Enchantment.damageContext(level, i, entity, damageSource);
                    holder.value()
                        .getEffects(EnchantmentEffectComponents.EQUIPMENT_DROPS)
                        .forEach(
                            targetedConditionalEffect -> {
                                if (targetedConditionalEffect.enchanted() == EnchantmentTarget.ATTACKER
                                    && targetedConditionalEffect.affected() == EnchantmentTarget.VICTIM
                                    && targetedConditionalEffect.matches(lootContext)) {
                                    mutableFloat.setValue(targetedConditionalEffect.effect().process(i, randomSource, mutableFloat.floatValue()));
                                }
                            }
                        );
                }
            );
        }

        return mutableFloat.floatValue();
    }

    public static void forEachModifier(ItemStack stack, EquipmentSlotGroup slotGroup, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        runIterationOnItem(stack, (holder, i) -> holder.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES).forEach(enchantmentAttributeEffect -> {
            if (((Enchantment)holder.value()).definition().slots().contains(slotGroup)) {
                action.accept(enchantmentAttributeEffect.attribute(), enchantmentAttributeEffect.getModifier(i, slotGroup));
            }
        }));
    }

    public static void forEachModifier(ItemStack stack, EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        runIterationOnItem(stack, (holder, i) -> holder.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES).forEach(enchantmentAttributeEffect -> {
            if (((Enchantment)holder.value()).matchingSlot(slot)) {
                action.accept(enchantmentAttributeEffect.attribute(), enchantmentAttributeEffect.getModifier(i, slot));
            }
        }));
    }

    public static int getFishingLuckBonus(ServerLevel level, ItemStack stack, Entity entity) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(stack, (holder, i) -> holder.value().modifyFishingLuckBonus(level, i, stack, entity, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static float getFishingTimeReduction(ServerLevel level, ItemStack stack, Entity entity) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(stack, (holder, i) -> holder.value().modifyFishingTimeReduction(level, i, stack, entity, mutableFloat));
        return Math.max(0.0F, mutableFloat.floatValue());
    }

    public static int getTridentReturnToOwnerAcceleration(ServerLevel level, ItemStack stack, Entity entity) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(stack, (holder, i) -> holder.value().modifyTridentReturnToOwnerAcceleration(level, i, stack, entity, mutableFloat));
        return Math.max(0, mutableFloat.intValue());
    }

    public static float modifyCrossbowChargingTime(ItemStack stack, LivingEntity entity, float crossbowChargingTime) {
        MutableFloat mutableFloat = new MutableFloat(crossbowChargingTime);
        runIterationOnItem(stack, (holder, i) -> holder.value().modifyCrossbowChargeTime(entity.getRandom(), i, mutableFloat));
        return Math.max(0.0F, mutableFloat.floatValue());
    }

    public static float getTridentSpinAttackStrength(ItemStack stack, LivingEntity entity) {
        MutableFloat mutableFloat = new MutableFloat(0.0F);
        runIterationOnItem(stack, (holder, i) -> holder.value().modifyTridentSpinAttackStrength(entity.getRandom(), i, mutableFloat));
        return mutableFloat.floatValue();
    }

    public static boolean hasTag(ItemStack stack, TagKey<Enchantment> tag) {
        ItemEnchantments itemEnchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.is(tag)) {
                return true;
            }
        }

        return false;
    }

    public static boolean has(ItemStack stack, DataComponentType<?> componentType) {
        MutableBoolean mutableBoolean = new MutableBoolean(false);
        runIterationOnItem(stack, (holder, i) -> {
            if (holder.value().effects().has(componentType)) {
                mutableBoolean.setTrue();
            }
        });
        return mutableBoolean.booleanValue();
    }

    public static <T> Optional<T> pickHighestLevel(ItemStack stack, DataComponentType<List<T>> componentType) {
        Pair<List<T>, Integer> pair = getHighestLevel(stack, componentType);
        if (pair != null) {
            List<T> list = pair.getFirst();
            int i = pair.getSecond();
            return Optional.of(list.get(Math.min(i, list.size()) - 1));
        } else {
            return Optional.empty();
        }
    }

    @Nullable
    public static <T> Pair<T, Integer> getHighestLevel(ItemStack stack, DataComponentType<T> componentType) {
        MutableObject<Pair<T, Integer>> mutableObject = new MutableObject<>();
        runIterationOnItem(stack, (holder, i) -> {
            if (mutableObject.getValue() == null || mutableObject.getValue().getSecond() < i) {
                T object = holder.value().effects().get(componentType);
                if (object != null) {
                    mutableObject.setValue(Pair.of(object, i));
                }
            }
        });
        return mutableObject.getValue();
    }

    public static Optional<EnchantedItemInUse> getRandomItemWith(DataComponentType<?> componentType, LivingEntity entity, Predicate<ItemStack> filter) {
        List<EnchantedItemInUse> list = new ArrayList<>();

        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            ItemStack itemStack = entity.getItemBySlot(equipmentSlot);
            if (filter.test(itemStack)) {
                ItemEnchantments itemEnchantments = itemStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

                for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    if (holder.value().effects().has(componentType) && holder.value().matchingSlot(equipmentSlot)) {
                        list.add(new EnchantedItemInUse(itemStack, equipmentSlot, entity));
                    }
                }
            }
        }

        return Util.getRandomSafe(list, entity.getRandom());
    }

    /**
     * Returns the enchantability of itemstack, using a separate calculation for each enchantNum (0, 1 or 2), cutting to the max enchantability power of the table, which is locked to a max of 15.
     */
    public static int getEnchantmentCost(RandomSource random, int enchantNum, int power, ItemStack stack) {
        Item item = stack.getItem();
        int i = item.getEnchantmentValue();
        if (i <= 0) {
            return 0;
        }

        if (power > 15) {
            power = 15;
        }

        int j = random.nextInt(8) + 1 + (power >> 1) + random.nextInt(power + 1);
        if (enchantNum == 0) {
            return Math.max(j / 3, 1);
        } else {
            return enchantNum == 1 ? j * 2 / 3 + 1 : Math.max(j, power * 2);
        }
    }

    public static ItemStack enchantItem(
        RandomSource random, ItemStack stack, int level, RegistryAccess registryAccess, Optional<? extends HolderSet<Enchantment>> possibleEnchantments
    ) {
        return enchantItem(
            random,
            stack,
            level,
            possibleEnchantments.map(HolderSet::stream)
                .orElseGet(() -> registryAccess.registryOrThrow(Registries.ENCHANTMENT).holders().map(reference -> (Holder<Enchantment>)reference))
        );
    }

    public static ItemStack enchantItem(RandomSource random, ItemStack stack, int level, Stream<Holder<Enchantment>> possibleEnchantments) {
        List<EnchantmentInstance> list = selectEnchantment(random, stack, level, possibleEnchantments);
        if (stack.is(Items.BOOK)) {
            stack = new ItemStack(Items.ENCHANTED_BOOK);
        }

        for (EnchantmentInstance enchantmentInstance : list) {
            stack.enchant(enchantmentInstance.enchantment, enchantmentInstance.level);
        }

        return stack;
    }

    public static List<EnchantmentInstance> selectEnchantment(RandomSource random, ItemStack stack, int level, Stream<Holder<Enchantment>> possibleEnchantments) {
        List<EnchantmentInstance> list = Lists.newArrayList();
        Item item = stack.getItem();
        int i = item.getEnchantmentValue();
        if (i <= 0) {
            return list;
        }

        level += 1 + random.nextInt(i / 4 + 1) + random.nextInt(i / 4 + 1);
        float f = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
        level = Mth.clamp(Math.round(level + level * f), 1, Integer.MAX_VALUE);
        List<EnchantmentInstance> list2 = getAvailableEnchantmentResults(level, stack, possibleEnchantments);
        if (!list2.isEmpty()) {
            WeightedRandom.getRandomItem(random, list2).ifPresent(list::add);

            while (random.nextInt(50) <= level) {
                if (!list.isEmpty()) {
                    filterCompatibleEnchantments(list2, Util.lastOf(list));
                }

                if (list2.isEmpty()) {
                    break;
                }

                WeightedRandom.getRandomItem(random, list2).ifPresent(list::add);
                level /= 2;
            }
        }

        return list;
    }

    public static void filterCompatibleEnchantments(List<EnchantmentInstance> dataList, EnchantmentInstance data) {
        dataList.removeIf(enchantmentInstance2 -> !Enchantment.areCompatible(data.enchantment, enchantmentInstance2.enchantment));
    }

    public static boolean isEnchantmentCompatible(Collection<Holder<Enchantment>> currentEnchantments, Holder<Enchantment> newEnchantment) {
        for (Holder<Enchantment> holder : currentEnchantments) {
            if (!Enchantment.areCompatible(holder, newEnchantment)) {
                return false;
            }
        }

        return true;
    }

    public static List<EnchantmentInstance> getAvailableEnchantmentResults(int level, ItemStack stack, Stream<Holder<Enchantment>> possibleEnchantments) {
        List<EnchantmentInstance> list = Lists.newArrayList();
        boolean bl = stack.is(Items.BOOK);
        possibleEnchantments.filter(holder -> holder.value().isPrimaryItem(stack) || bl).forEach(holder -> {
            Enchantment enchantment = holder.value();

            for (int j = enchantment.getMaxLevel(); j >= enchantment.getMinLevel(); j--) {
                if (level >= enchantment.getMinCost(j) && level <= enchantment.getMaxCost(j)) {
                    list.add(new EnchantmentInstance((Holder<Enchantment>)holder, j));
                    break;
                }
            }
        });
        return list;
    }

    public static void enchantItemFromProvider(
        ItemStack stack, RegistryAccess registries, ResourceKey<EnchantmentProvider> key, DifficultyInstance difficulty, RandomSource random
    ) {
        EnchantmentProvider enchantmentProvider = registries.registryOrThrow(Registries.ENCHANTMENT_PROVIDER).get(key);
        if (enchantmentProvider != null) {
            updateEnchantments(stack, mutable -> enchantmentProvider.enchant(stack, mutable, random, difficulty));
        }
    }

    @FunctionalInterface
    interface EnchantmentInSlotVisitor {
        void accept(Holder<Enchantment> var1, int var2, EnchantedItemInUse var3);
    }

    @FunctionalInterface
    interface EnchantmentVisitor {
        void accept(Holder<Enchantment> var1, int var2);
    }
}
