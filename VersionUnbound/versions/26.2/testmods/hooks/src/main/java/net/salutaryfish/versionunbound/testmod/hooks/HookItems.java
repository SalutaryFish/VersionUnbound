package net.salutaryfish.versionunbound.testmod.hooks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelReader;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import org.jspecify.annotations.Nullable;

/** This mod's items, each overriding item extension methods vanilla's code should ask. */
final class HookItems {
    private HookItems() {}

    static final class CarvingKnife extends Item {
        CarvingKnife(Properties properties) {
            super(properties);
        }

        @Override
        public boolean canPerformAction(ItemInstance stack, ItemAbility itemAbility) {
            return itemAbility == ItemAbilities.SHEARS_CARVE || itemAbility == ItemAbilities.SHEARS_TRIM;
        }
    }

    /** A use onItemUseFirst heard: where, and on which side. */
    record FirstUse(BlockPos pos, boolean clientSide) {}

    static final List<FirstUse> FIRST_USES = new ArrayList<>();
    /** What FirstUser's onItemUseFirst answers. */
    static InteractionResult firstUseResult = InteractionResult.SUCCESS;

    /** Uses itself before the block it's used on. */
    static final class FirstUser extends Item {
        FirstUser(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
            FIRST_USES.add(new FirstUse(context.getClickedPos().immutable(), context.getLevel().isClientSide()));
            return firstUseResult;
        }
    }

    /** Lets a sneaking player use the block anyway. */
    static final class SneakPass extends Item {
        SneakPass(Properties properties) {
            super(properties);
        }

        @Override
        public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
            return true;
        }
    }

    /** Burns for 1234 ticks in a furnace (not in other cooking blocks), and leaves a stick behind. */
    static final class Ember extends Item {
        Ember(Properties properties) {
            super(properties);
        }

        @Override
        public int getBurnTime(ItemStack itemStack, @Nullable RecipeType<?> recipeType, FuelValues fuelValues) {
            return recipeType == RecipeType.SMELTING ? 1234 : 0;
        }

        @Override
        public @Nullable ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
            return new ItemStackTemplate(Items.STICK);
        }
    }

    static final List<String> HEARD = new ArrayList<>();

    /** Takes over its item entity's tick and its holder's swing. */
    static final class Wand extends Item {
        Wand(Properties properties) {
            super(properties);
        }

        @Override
        public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
            HEARD.add("onEntityItemUpdate");
            return true;
        }

        @Override
        public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
            HEARD.add("onEntitySwing " + hand);
            return true;
        }
    }
}
