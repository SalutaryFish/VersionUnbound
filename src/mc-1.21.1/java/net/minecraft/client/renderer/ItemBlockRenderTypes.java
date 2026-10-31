package net.minecraft.client.renderer;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class ItemBlockRenderTypes {
    private static final Map<Block, RenderType> TYPE_BY_BLOCK = Util.make(Maps.newHashMap(), renderTypeByBlock -> {
        RenderType renderType = RenderType.tripwire();
        renderTypeByBlock.put(Blocks.TRIPWIRE, renderType);
        RenderType renderType2 = RenderType.cutoutMipped();
        renderTypeByBlock.put(Blocks.GRASS_BLOCK, renderType2);
        renderTypeByBlock.put(Blocks.IRON_BARS, renderType2);
        renderTypeByBlock.put(Blocks.GLASS_PANE, renderType2);
        renderTypeByBlock.put(Blocks.TRIPWIRE_HOOK, renderType2);
        renderTypeByBlock.put(Blocks.HOPPER, renderType2);
        renderTypeByBlock.put(Blocks.CHAIN, renderType2);
        renderTypeByBlock.put(Blocks.JUNGLE_LEAVES, renderType2);
        renderTypeByBlock.put(Blocks.OAK_LEAVES, renderType2);
        renderTypeByBlock.put(Blocks.SPRUCE_LEAVES, renderType2);
        renderTypeByBlock.put(Blocks.ACACIA_LEAVES, renderType2);
        renderTypeByBlock.put(Blocks.CHERRY_LEAVES, renderType2);
        renderTypeByBlock.put(Blocks.BIRCH_LEAVES, renderType2);
        renderTypeByBlock.put(Blocks.DARK_OAK_LEAVES, renderType2);
        renderTypeByBlock.put(Blocks.AZALEA_LEAVES, renderType2);
        renderTypeByBlock.put(Blocks.FLOWERING_AZALEA_LEAVES, renderType2);
        renderTypeByBlock.put(Blocks.MANGROVE_ROOTS, renderType2);
        renderTypeByBlock.put(Blocks.MANGROVE_LEAVES, renderType2);
        RenderType renderType3 = RenderType.cutout();
        renderTypeByBlock.put(Blocks.OAK_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.SPRUCE_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.BIRCH_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.JUNGLE_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.ACACIA_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.CHERRY_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.DARK_OAK_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.GLASS, renderType3);
        renderTypeByBlock.put(Blocks.WHITE_BED, renderType3);
        renderTypeByBlock.put(Blocks.ORANGE_BED, renderType3);
        renderTypeByBlock.put(Blocks.MAGENTA_BED, renderType3);
        renderTypeByBlock.put(Blocks.LIGHT_BLUE_BED, renderType3);
        renderTypeByBlock.put(Blocks.YELLOW_BED, renderType3);
        renderTypeByBlock.put(Blocks.LIME_BED, renderType3);
        renderTypeByBlock.put(Blocks.PINK_BED, renderType3);
        renderTypeByBlock.put(Blocks.GRAY_BED, renderType3);
        renderTypeByBlock.put(Blocks.LIGHT_GRAY_BED, renderType3);
        renderTypeByBlock.put(Blocks.CYAN_BED, renderType3);
        renderTypeByBlock.put(Blocks.PURPLE_BED, renderType3);
        renderTypeByBlock.put(Blocks.BLUE_BED, renderType3);
        renderTypeByBlock.put(Blocks.BROWN_BED, renderType3);
        renderTypeByBlock.put(Blocks.GREEN_BED, renderType3);
        renderTypeByBlock.put(Blocks.RED_BED, renderType3);
        renderTypeByBlock.put(Blocks.BLACK_BED, renderType3);
        renderTypeByBlock.put(Blocks.POWERED_RAIL, renderType3);
        renderTypeByBlock.put(Blocks.DETECTOR_RAIL, renderType3);
        renderTypeByBlock.put(Blocks.COBWEB, renderType3);
        renderTypeByBlock.put(Blocks.SHORT_GRASS, renderType3);
        renderTypeByBlock.put(Blocks.FERN, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_BUSH, renderType3);
        renderTypeByBlock.put(Blocks.SEAGRASS, renderType3);
        renderTypeByBlock.put(Blocks.TALL_SEAGRASS, renderType3);
        renderTypeByBlock.put(Blocks.DANDELION, renderType3);
        renderTypeByBlock.put(Blocks.POPPY, renderType3);
        renderTypeByBlock.put(Blocks.BLUE_ORCHID, renderType3);
        renderTypeByBlock.put(Blocks.ALLIUM, renderType3);
        renderTypeByBlock.put(Blocks.AZURE_BLUET, renderType3);
        renderTypeByBlock.put(Blocks.RED_TULIP, renderType3);
        renderTypeByBlock.put(Blocks.ORANGE_TULIP, renderType3);
        renderTypeByBlock.put(Blocks.WHITE_TULIP, renderType3);
        renderTypeByBlock.put(Blocks.PINK_TULIP, renderType3);
        renderTypeByBlock.put(Blocks.OXEYE_DAISY, renderType3);
        renderTypeByBlock.put(Blocks.CORNFLOWER, renderType3);
        renderTypeByBlock.put(Blocks.WITHER_ROSE, renderType3);
        renderTypeByBlock.put(Blocks.LILY_OF_THE_VALLEY, renderType3);
        renderTypeByBlock.put(Blocks.BROWN_MUSHROOM, renderType3);
        renderTypeByBlock.put(Blocks.RED_MUSHROOM, renderType3);
        renderTypeByBlock.put(Blocks.TORCH, renderType3);
        renderTypeByBlock.put(Blocks.WALL_TORCH, renderType3);
        renderTypeByBlock.put(Blocks.SOUL_TORCH, renderType3);
        renderTypeByBlock.put(Blocks.SOUL_WALL_TORCH, renderType3);
        renderTypeByBlock.put(Blocks.FIRE, renderType3);
        renderTypeByBlock.put(Blocks.SOUL_FIRE, renderType3);
        renderTypeByBlock.put(Blocks.SPAWNER, renderType3);
        renderTypeByBlock.put(Blocks.TRIAL_SPAWNER, renderType3);
        renderTypeByBlock.put(Blocks.VAULT, renderType3);
        renderTypeByBlock.put(Blocks.REDSTONE_WIRE, renderType3);
        renderTypeByBlock.put(Blocks.WHEAT, renderType3);
        renderTypeByBlock.put(Blocks.OAK_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.LADDER, renderType3);
        renderTypeByBlock.put(Blocks.RAIL, renderType3);
        renderTypeByBlock.put(Blocks.IRON_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.REDSTONE_TORCH, renderType3);
        renderTypeByBlock.put(Blocks.REDSTONE_WALL_TORCH, renderType3);
        renderTypeByBlock.put(Blocks.CACTUS, renderType3);
        renderTypeByBlock.put(Blocks.SUGAR_CANE, renderType3);
        renderTypeByBlock.put(Blocks.REPEATER, renderType3);
        renderTypeByBlock.put(Blocks.OAK_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.SPRUCE_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.BIRCH_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.JUNGLE_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.ACACIA_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.CHERRY_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.DARK_OAK_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.CRIMSON_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.WARPED_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.MANGROVE_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.BAMBOO_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.COPPER_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.EXPOSED_COPPER_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.WEATHERED_COPPER_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.OXIDIZED_COPPER_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.WAXED_COPPER_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.WAXED_EXPOSED_COPPER_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.WAXED_WEATHERED_COPPER_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.ATTACHED_PUMPKIN_STEM, renderType3);
        renderTypeByBlock.put(Blocks.ATTACHED_MELON_STEM, renderType3);
        renderTypeByBlock.put(Blocks.PUMPKIN_STEM, renderType3);
        renderTypeByBlock.put(Blocks.MELON_STEM, renderType3);
        renderTypeByBlock.put(Blocks.VINE, renderType3);
        renderTypeByBlock.put(Blocks.GLOW_LICHEN, renderType3);
        renderTypeByBlock.put(Blocks.LILY_PAD, renderType3);
        renderTypeByBlock.put(Blocks.NETHER_WART, renderType3);
        renderTypeByBlock.put(Blocks.BREWING_STAND, renderType3);
        renderTypeByBlock.put(Blocks.COCOA, renderType3);
        renderTypeByBlock.put(Blocks.BEACON, renderType3);
        renderTypeByBlock.put(Blocks.FLOWER_POT, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_OAK_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_SPRUCE_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_BIRCH_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_JUNGLE_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_ACACIA_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_CHERRY_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_DARK_OAK_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_MANGROVE_PROPAGULE, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_FERN, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_DANDELION, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_POPPY, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_BLUE_ORCHID, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_ALLIUM, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_AZURE_BLUET, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_RED_TULIP, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_ORANGE_TULIP, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_WHITE_TULIP, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_PINK_TULIP, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_OXEYE_DAISY, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_CORNFLOWER, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_LILY_OF_THE_VALLEY, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_WITHER_ROSE, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_RED_MUSHROOM, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_BROWN_MUSHROOM, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_DEAD_BUSH, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_CACTUS, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_AZALEA, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_FLOWERING_AZALEA, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_TORCHFLOWER, renderType3);
        renderTypeByBlock.put(Blocks.CARROTS, renderType3);
        renderTypeByBlock.put(Blocks.POTATOES, renderType3);
        renderTypeByBlock.put(Blocks.COMPARATOR, renderType3);
        renderTypeByBlock.put(Blocks.ACTIVATOR_RAIL, renderType3);
        renderTypeByBlock.put(Blocks.IRON_TRAPDOOR, renderType3);
        renderTypeByBlock.put(Blocks.SUNFLOWER, renderType3);
        renderTypeByBlock.put(Blocks.LILAC, renderType3);
        renderTypeByBlock.put(Blocks.ROSE_BUSH, renderType3);
        renderTypeByBlock.put(Blocks.PEONY, renderType3);
        renderTypeByBlock.put(Blocks.TALL_GRASS, renderType3);
        renderTypeByBlock.put(Blocks.LARGE_FERN, renderType3);
        renderTypeByBlock.put(Blocks.SPRUCE_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.BIRCH_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.JUNGLE_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.ACACIA_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.CHERRY_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.DARK_OAK_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.MANGROVE_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.BAMBOO_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.COPPER_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.EXPOSED_COPPER_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.WEATHERED_COPPER_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.OXIDIZED_COPPER_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.WAXED_COPPER_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.WAXED_EXPOSED_COPPER_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.WAXED_WEATHERED_COPPER_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.WAXED_OXIDIZED_COPPER_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.END_ROD, renderType3);
        renderTypeByBlock.put(Blocks.CHORUS_PLANT, renderType3);
        renderTypeByBlock.put(Blocks.CHORUS_FLOWER, renderType3);
        renderTypeByBlock.put(Blocks.TORCHFLOWER, renderType3);
        renderTypeByBlock.put(Blocks.TORCHFLOWER_CROP, renderType3);
        renderTypeByBlock.put(Blocks.PITCHER_PLANT, renderType3);
        renderTypeByBlock.put(Blocks.PITCHER_CROP, renderType3);
        renderTypeByBlock.put(Blocks.BEETROOTS, renderType3);
        renderTypeByBlock.put(Blocks.KELP, renderType3);
        renderTypeByBlock.put(Blocks.KELP_PLANT, renderType3);
        renderTypeByBlock.put(Blocks.TURTLE_EGG, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_TUBE_CORAL, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_BRAIN_CORAL, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_BUBBLE_CORAL, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_FIRE_CORAL, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_HORN_CORAL, renderType3);
        renderTypeByBlock.put(Blocks.TUBE_CORAL, renderType3);
        renderTypeByBlock.put(Blocks.BRAIN_CORAL, renderType3);
        renderTypeByBlock.put(Blocks.BUBBLE_CORAL, renderType3);
        renderTypeByBlock.put(Blocks.FIRE_CORAL, renderType3);
        renderTypeByBlock.put(Blocks.HORN_CORAL, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_TUBE_CORAL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_BRAIN_CORAL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_BUBBLE_CORAL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_FIRE_CORAL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_HORN_CORAL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.TUBE_CORAL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.BRAIN_CORAL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.BUBBLE_CORAL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.FIRE_CORAL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.HORN_CORAL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_TUBE_CORAL_WALL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_BRAIN_CORAL_WALL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_BUBBLE_CORAL_WALL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_FIRE_CORAL_WALL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.DEAD_HORN_CORAL_WALL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.TUBE_CORAL_WALL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.BRAIN_CORAL_WALL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.BUBBLE_CORAL_WALL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.FIRE_CORAL_WALL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.HORN_CORAL_WALL_FAN, renderType3);
        renderTypeByBlock.put(Blocks.SEA_PICKLE, renderType3);
        renderTypeByBlock.put(Blocks.CONDUIT, renderType3);
        renderTypeByBlock.put(Blocks.BAMBOO_SAPLING, renderType3);
        renderTypeByBlock.put(Blocks.BAMBOO, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_BAMBOO, renderType3);
        renderTypeByBlock.put(Blocks.SCAFFOLDING, renderType3);
        renderTypeByBlock.put(Blocks.STONECUTTER, renderType3);
        renderTypeByBlock.put(Blocks.LANTERN, renderType3);
        renderTypeByBlock.put(Blocks.SOUL_LANTERN, renderType3);
        renderTypeByBlock.put(Blocks.CAMPFIRE, renderType3);
        renderTypeByBlock.put(Blocks.SOUL_CAMPFIRE, renderType3);
        renderTypeByBlock.put(Blocks.SWEET_BERRY_BUSH, renderType3);
        renderTypeByBlock.put(Blocks.WEEPING_VINES, renderType3);
        renderTypeByBlock.put(Blocks.WEEPING_VINES_PLANT, renderType3);
        renderTypeByBlock.put(Blocks.TWISTING_VINES, renderType3);
        renderTypeByBlock.put(Blocks.TWISTING_VINES_PLANT, renderType3);
        renderTypeByBlock.put(Blocks.NETHER_SPROUTS, renderType3);
        renderTypeByBlock.put(Blocks.CRIMSON_FUNGUS, renderType3);
        renderTypeByBlock.put(Blocks.WARPED_FUNGUS, renderType3);
        renderTypeByBlock.put(Blocks.CRIMSON_ROOTS, renderType3);
        renderTypeByBlock.put(Blocks.WARPED_ROOTS, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_CRIMSON_FUNGUS, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_WARPED_FUNGUS, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_CRIMSON_ROOTS, renderType3);
        renderTypeByBlock.put(Blocks.POTTED_WARPED_ROOTS, renderType3);
        renderTypeByBlock.put(Blocks.CRIMSON_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.WARPED_DOOR, renderType3);
        renderTypeByBlock.put(Blocks.POINTED_DRIPSTONE, renderType3);
        renderTypeByBlock.put(Blocks.SMALL_AMETHYST_BUD, renderType3);
        renderTypeByBlock.put(Blocks.MEDIUM_AMETHYST_BUD, renderType3);
        renderTypeByBlock.put(Blocks.LARGE_AMETHYST_BUD, renderType3);
        renderTypeByBlock.put(Blocks.AMETHYST_CLUSTER, renderType3);
        renderTypeByBlock.put(Blocks.LIGHTNING_ROD, renderType3);
        renderTypeByBlock.put(Blocks.CAVE_VINES, renderType3);
        renderTypeByBlock.put(Blocks.CAVE_VINES_PLANT, renderType3);
        renderTypeByBlock.put(Blocks.SPORE_BLOSSOM, renderType3);
        renderTypeByBlock.put(Blocks.FLOWERING_AZALEA, renderType3);
        renderTypeByBlock.put(Blocks.AZALEA, renderType3);
        renderTypeByBlock.put(Blocks.PINK_PETALS, renderType3);
        renderTypeByBlock.put(Blocks.BIG_DRIPLEAF, renderType3);
        renderTypeByBlock.put(Blocks.BIG_DRIPLEAF_STEM, renderType3);
        renderTypeByBlock.put(Blocks.SMALL_DRIPLEAF, renderType3);
        renderTypeByBlock.put(Blocks.HANGING_ROOTS, renderType3);
        renderTypeByBlock.put(Blocks.SCULK_SENSOR, renderType3);
        renderTypeByBlock.put(Blocks.CALIBRATED_SCULK_SENSOR, renderType3);
        renderTypeByBlock.put(Blocks.SCULK_VEIN, renderType3);
        renderTypeByBlock.put(Blocks.SCULK_SHRIEKER, renderType3);
        renderTypeByBlock.put(Blocks.MANGROVE_PROPAGULE, renderType3);
        renderTypeByBlock.put(Blocks.FROGSPAWN, renderType3);
        renderTypeByBlock.put(Blocks.COPPER_GRATE, renderType3);
        renderTypeByBlock.put(Blocks.EXPOSED_COPPER_GRATE, renderType3);
        renderTypeByBlock.put(Blocks.WEATHERED_COPPER_GRATE, renderType3);
        renderTypeByBlock.put(Blocks.OXIDIZED_COPPER_GRATE, renderType3);
        renderTypeByBlock.put(Blocks.WAXED_COPPER_GRATE, renderType3);
        renderTypeByBlock.put(Blocks.WAXED_EXPOSED_COPPER_GRATE, renderType3);
        renderTypeByBlock.put(Blocks.WAXED_WEATHERED_COPPER_GRATE, renderType3);
        renderTypeByBlock.put(Blocks.WAXED_OXIDIZED_COPPER_GRATE, renderType3);
        RenderType renderType4 = RenderType.translucent();
        renderTypeByBlock.put(Blocks.ICE, renderType4);
        renderTypeByBlock.put(Blocks.NETHER_PORTAL, renderType4);
        renderTypeByBlock.put(Blocks.WHITE_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.ORANGE_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.MAGENTA_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.LIGHT_BLUE_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.YELLOW_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.LIME_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.PINK_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.GRAY_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.LIGHT_GRAY_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.CYAN_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.PURPLE_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.BLUE_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.BROWN_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.GREEN_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.RED_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.BLACK_STAINED_GLASS, renderType4);
        renderTypeByBlock.put(Blocks.WHITE_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.ORANGE_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.MAGENTA_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.YELLOW_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.LIME_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.PINK_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.GRAY_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.CYAN_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.PURPLE_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.BLUE_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.BROWN_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.GREEN_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.RED_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.BLACK_STAINED_GLASS_PANE, renderType4);
        renderTypeByBlock.put(Blocks.SLIME_BLOCK, renderType4);
        renderTypeByBlock.put(Blocks.HONEY_BLOCK, renderType4);
        renderTypeByBlock.put(Blocks.FROSTED_ICE, renderType4);
        renderTypeByBlock.put(Blocks.BUBBLE_COLUMN, renderType4);
        renderTypeByBlock.put(Blocks.TINTED_GLASS, renderType4);
    });
    private static final Map<Fluid, RenderType> TYPE_BY_FLUID = Util.make(Maps.newHashMap(), renderTypeByFluid -> {
        RenderType renderType = RenderType.translucent();
        renderTypeByFluid.put(Fluids.FLOWING_WATER, renderType);
        renderTypeByFluid.put(Fluids.WATER, renderType);
    });
    private static boolean renderCutout;

    public static RenderType getChunkRenderType(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof LeavesBlock) {
            return renderCutout ? RenderType.cutoutMipped() : RenderType.solid();
        }

        RenderType renderType = TYPE_BY_BLOCK.get(block);
        return renderType != null ? renderType : RenderType.solid();
    }

    public static RenderType getMovingBlockRenderType(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof LeavesBlock) {
            return renderCutout ? RenderType.cutoutMipped() : RenderType.solid();
        } else {
            RenderType renderType = TYPE_BY_BLOCK.get(block);
            if (renderType != null) {
                return renderType == RenderType.translucent() ? RenderType.translucentMovingBlock() : renderType;
            } else {
                return RenderType.solid();
            }
        }
    }

    public static RenderType getRenderType(BlockState state, boolean cull) {
        RenderType renderType = getChunkRenderType(state);
        if (renderType == RenderType.translucent()) {
            if (!Minecraft.useShaderTransparency()) {
                return Sheets.translucentCullBlockSheet();
            } else {
                return cull ? Sheets.translucentCullBlockSheet() : Sheets.translucentItemSheet();
            }
        } else {
            return Sheets.cutoutBlockSheet();
        }
    }

    public static RenderType getRenderType(ItemStack stack, boolean cull) {
        Item item = stack.getItem();
        if (item instanceof BlockItem) {
            Block block = ((BlockItem)item).getBlock();
            return getRenderType(block.defaultBlockState(), cull);
        } else {
            return cull ? Sheets.translucentCullBlockSheet() : Sheets.translucentItemSheet();
        }
    }

    public static RenderType getRenderLayer(FluidState fluidState) {
        RenderType renderType = TYPE_BY_FLUID.get(fluidState.getType());
        return renderType != null ? renderType : RenderType.solid();
    }

    public static void setFancy(boolean fancy) {
        renderCutout = fancy;
    }
}
