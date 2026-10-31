package net.salutaryfish.versionunbound.testmod.hooks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelReader;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

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
}
