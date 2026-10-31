package net.minecraft.client.color.item;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.IdMapper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class ItemColors {
    private static final int DEFAULT = -1;
    private final IdMapper<ItemColor> itemColors = new IdMapper<>(32);

    public static ItemColors createDefault(BlockColors colors) {
        ItemColors itemColors = new ItemColors();
        itemColors.register(
            (stack, tintIndex) -> tintIndex > 0 ? -1 : DyedItemColor.getOrDefault(stack, -6265536),
            Items.LEATHER_HELMET,
            Items.LEATHER_CHESTPLATE,
            Items.LEATHER_LEGGINGS,
            Items.LEATHER_BOOTS,
            Items.LEATHER_HORSE_ARMOR
        );
        itemColors.register((stack, tintIndex) -> tintIndex != 1 ? -1 : DyedItemColor.getOrDefault(stack, 0), Items.WOLF_ARMOR);
        itemColors.register((stack, tintIndex) -> GrassColor.get(0.5, 1.0), Blocks.TALL_GRASS, Blocks.LARGE_FERN);
        itemColors.register((stack, tintIndex) -> {
            if (tintIndex != 1) {
                return -1;
            }

            FireworkExplosion fireworkExplosion = stack.get(DataComponents.FIREWORK_EXPLOSION);
            IntList intList = fireworkExplosion != null ? fireworkExplosion.colors() : IntList.of();
            int i = intList.size();
            if (i == 0) {
                return -7697782;
            }

            if (i == 1) {
                return FastColor.ARGB32.opaque(intList.getInt(0));
            }

            int j = 0;
            int k = 0;
            int l = 0;

            for (int m = 0; m < i; m++) {
                int n = intList.getInt(m);
                j += FastColor.ARGB32.red(n);
                k += FastColor.ARGB32.green(n);
                l += FastColor.ARGB32.blue(n);
            }

            return FastColor.ARGB32.color(j / i, k / i, l / i);
        }, Items.FIREWORK_STAR);
        itemColors.register(
            (itemStack, i) -> i > 0 ? -1 : FastColor.ARGB32.opaque(itemStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor()),
            Items.POTION,
            Items.SPLASH_POTION,
            Items.LINGERING_POTION,
            Items.TIPPED_ARROW
        );

        for (SpawnEggItem spawnEggItem : SpawnEggItem.eggs()) {
            itemColors.register((itemStack, i) -> FastColor.ARGB32.opaque(spawnEggItem.getColor(i)), spawnEggItem);
        }

        itemColors.register(
            (itemStack, i) -> {
                BlockState blockState = ((BlockItem)itemStack.getItem()).getBlock().defaultBlockState();
                return colors.getColor(blockState, null, null, i);
            },
            Blocks.GRASS_BLOCK,
            Blocks.SHORT_GRASS,
            Blocks.FERN,
            Blocks.VINE,
            Blocks.OAK_LEAVES,
            Blocks.SPRUCE_LEAVES,
            Blocks.BIRCH_LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES,
            Blocks.LILY_PAD
        );
        itemColors.register((stack, tintIndex) -> FoliageColor.getMangroveColor(), Blocks.MANGROVE_LEAVES);
        itemColors.register(
            (itemStack, i) -> i == 0 ? -1 : FastColor.ARGB32.opaque(itemStack.getOrDefault(DataComponents.MAP_COLOR, MapItemColor.DEFAULT).rgb()),
            Items.FILLED_MAP
        );
        return itemColors;
    }

    public int getColor(ItemStack stack, int tintIndex) {
        ItemColor itemColor = this.itemColors.byId(BuiltInRegistries.ITEM.getId(stack.getItem()));
        return itemColor == null ? -1 : itemColor.getColor(stack, tintIndex);
    }

    public void register(ItemColor itemColor, ItemLike... items) {
        for (ItemLike itemLike : items) {
            this.itemColors.addMapping(itemColor, Item.getId(itemLike.asItem()));
        }
    }
}
