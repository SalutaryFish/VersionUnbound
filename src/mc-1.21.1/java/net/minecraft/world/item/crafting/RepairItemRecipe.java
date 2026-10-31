package net.minecraft.world.item.crafting;

import com.mojang.datafixers.util.Pair;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

public class RepairItemRecipe extends CustomRecipe {
    public RepairItemRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Nullable
    private Pair<ItemStack, ItemStack> getItemsToCombine(CraftingInput input) {
        ItemStack itemStack = null;
        ItemStack itemStack2 = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack itemStack3 = input.getItem(i);
            if (!itemStack3.isEmpty()) {
                if (itemStack == null) {
                    itemStack = itemStack3;
                } else {
                    if (itemStack2 != null) {
                        return null;
                    }

                    itemStack2 = itemStack3;
                }
            }
        }

        return itemStack != null && itemStack2 != null && canCombine(itemStack, itemStack2) ? Pair.of(itemStack, itemStack2) : null;
    }

    private static boolean canCombine(ItemStack stack1, ItemStack stack2) {
        return stack2.is(stack1.getItem())
            && stack1.getCount() == 1
            && stack2.getCount() == 1
            && stack1.has(DataComponents.MAX_DAMAGE)
            && stack2.has(DataComponents.MAX_DAMAGE)
            && stack1.has(DataComponents.DAMAGE)
            && stack2.has(DataComponents.DAMAGE);
    }

    public boolean matches(CraftingInput input, Level level) {
        return this.getItemsToCombine(input) != null;
    }

    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Pair<ItemStack, ItemStack> pair = this.getItemsToCombine(input);
        if (pair == null) {
            return ItemStack.EMPTY;
        }

        ItemStack itemStack = pair.getFirst();
        ItemStack itemStack2 = pair.getSecond();
        int i = Math.max(itemStack.getMaxDamage(), itemStack2.getMaxDamage());
        int j = itemStack.getMaxDamage() - itemStack.getDamageValue();
        int k = itemStack2.getMaxDamage() - itemStack2.getDamageValue();
        int l = j + k + i * 5 / 100;
        ItemStack itemStack3 = new ItemStack(itemStack.getItem());
        itemStack3.set(DataComponents.MAX_DAMAGE, i);
        itemStack3.setDamageValue(Math.max(i - l, 0));
        ItemEnchantments itemEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(itemStack);
        ItemEnchantments itemEnchantments2 = EnchantmentHelper.getEnchantmentsForCrafting(itemStack2);
        EnchantmentHelper.updateEnchantments(
            itemStack3,
            mutable -> registries.lookupOrThrow(Registries.ENCHANTMENT)
                .listElements()
                .filter(reference -> reference.is(EnchantmentTags.CURSE))
                .forEach(reference -> {
                    int ix = Math.max(itemEnchantments.getLevel(reference), itemEnchantments2.getLevel(reference));
                    if (ix > 0) {
                        mutable.upgrade(reference, ix);
                    }
                })
        );
        return itemStack3;
    }

    /**
     * Used to determine if this recipe can fit in a grid of the given width/height
     */
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.REPAIR_ITEM;
    }
}
