package net.minecraft.client.color.block;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;

public class BlockColors {
    private static final int DEFAULT = -1;
    private final IdMapper<BlockColor> blockColors = new IdMapper<>(32);
    private final Map<Block, Set<Property<?>>> coloringStates = Maps.newHashMap();

    public static BlockColors createDefault() {
        BlockColors blockColors = new BlockColors();
        blockColors.register(
            (state, level, pos, tintIndex) -> level != null && pos != null
                ? BiomeColors.getAverageGrassColor(level, state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos)
                : GrassColor.getDefaultColor(),
            Blocks.LARGE_FERN,
            Blocks.TALL_GRASS
        );
        blockColors.addColoringState(DoublePlantBlock.HALF, Blocks.LARGE_FERN, Blocks.TALL_GRASS);
        blockColors.register(
            (state, level, pos, tintIndex) -> level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.getDefaultColor(),
            Blocks.GRASS_BLOCK,
            Blocks.FERN,
            Blocks.SHORT_GRASS,
            Blocks.POTTED_FERN
        );
        blockColors.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.getDefaultColor();
            } else {
                return -1;
            }
        }, Blocks.PINK_PETALS);
        blockColors.register((state, level, pos, tintIndex) -> FoliageColor.getEvergreenColor(), Blocks.SPRUCE_LEAVES);
        blockColors.register((state, level, pos, tintIndex) -> FoliageColor.getBirchColor(), Blocks.BIRCH_LEAVES);
        blockColors.register(
            (state, level, pos, tintIndex) -> level != null && pos != null ? BiomeColors.getAverageFoliageColor(level, pos) : FoliageColor.getDefaultColor(),
            Blocks.OAK_LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES,
            Blocks.VINE,
            Blocks.MANGROVE_LEAVES
        );
        blockColors.register(
            (state, level, pos, tintIndex) -> level != null && pos != null ? BiomeColors.getAverageWaterColor(level, pos) : -1,
            Blocks.WATER,
            Blocks.BUBBLE_COLUMN,
            Blocks.WATER_CAULDRON
        );
        blockColors.register(
            (state, level, pos, tintIndex) -> RedStoneWireBlock.getColorForPower(state.getValue(RedStoneWireBlock.POWER)), Blocks.REDSTONE_WIRE
        );
        blockColors.addColoringState(RedStoneWireBlock.POWER, Blocks.REDSTONE_WIRE);
        blockColors.register(
            (state, level, pos, tintIndex) -> level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos) : -1, Blocks.SUGAR_CANE
        );
        blockColors.register((state, lightReader, pos, tintIndex) -> -2046180, Blocks.ATTACHED_MELON_STEM, Blocks.ATTACHED_PUMPKIN_STEM);
        blockColors.register((state, level, pos, tintIndex) -> {
            int i = state.getValue(StemBlock.AGE);
            return FastColor.ARGB32.color(i * 32, 255 - i * 8, i * 4);
        }, Blocks.MELON_STEM, Blocks.PUMPKIN_STEM);
        blockColors.addColoringState(StemBlock.AGE, Blocks.MELON_STEM, Blocks.PUMPKIN_STEM);
        blockColors.register(
            (blockState, blockAndTintGetter, blockPos, i) -> blockAndTintGetter != null && blockPos != null ? -14647248 : -9321636, Blocks.LILY_PAD
        );
        return blockColors;
    }

    public int getColor(BlockState state, Level level, BlockPos pos) {
        BlockColor blockColor = this.blockColors.byId(BuiltInRegistries.BLOCK.getId(state.getBlock()));
        if (blockColor != null) {
            return blockColor.getColor(state, null, null, 0);
        }

        MapColor mapColor = state.getMapColor(level, pos);
        return mapColor != null ? mapColor.col : -1;
    }

    public int getColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        BlockColor blockColor = this.blockColors.byId(BuiltInRegistries.BLOCK.getId(state.getBlock()));
        return blockColor == null ? -1 : blockColor.getColor(state, level, pos, tintIndex);
    }

    public void register(BlockColor blockColor, Block... blocks) {
        for (Block block : blocks) {
            this.blockColors.addMapping(blockColor, BuiltInRegistries.BLOCK.getId(block));
        }
    }

    private void addColoringStates(Set<Property<?>> properties, Block... blocks) {
        for (Block block : blocks) {
            this.coloringStates.put(block, properties);
        }
    }

    private void addColoringState(Property<?> property, Block... blocks) {
        this.addColoringStates(ImmutableSet.of(property), blocks);
    }

    public Set<Property<?>> getColoringProperties(Block block) {
        return this.coloringStates.getOrDefault(block, ImmutableSet.of());
    }
}
