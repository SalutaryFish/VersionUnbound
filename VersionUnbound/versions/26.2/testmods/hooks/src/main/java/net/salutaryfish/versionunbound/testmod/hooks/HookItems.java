package net.salutaryfish.versionunbound.testmod.hooks;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
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
}
