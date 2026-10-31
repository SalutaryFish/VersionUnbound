package net.minecraft.world.level.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class WeightedPressurePlateBlock extends BasePressurePlateBlock {
    public static final MapCodec<WeightedPressurePlateBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                Codec.intRange(1, 1024).fieldOf("max_weight").forGetter(weightedPressurePlateBlock -> weightedPressurePlateBlock.maxWeight),
                BlockSetType.CODEC.fieldOf("block_set_type").forGetter(weightedPressurePlateBlock -> weightedPressurePlateBlock.type),
                propertiesCodec()
            )
            .apply(instance, WeightedPressurePlateBlock::new)
    );
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private final int maxWeight;

    @Override
    public MapCodec<WeightedPressurePlateBlock> codec() {
        return CODEC;
    }

    protected WeightedPressurePlateBlock(int maxWeight, BlockSetType type, BlockBehaviour.Properties properties) {
        super(properties, type);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0));
        this.maxWeight = maxWeight;
    }

    /**
     * Calculates what the signal strength of a pressure plate at the given location should be.
     */
    @Override
    protected int getSignalStrength(Level level, BlockPos pos) {
        int i = Math.min(getEntityCount(level, TOUCH_AABB.move(pos), Entity.class), this.maxWeight);
        if (i > 0) {
            float f = (float)Math.min(this.maxWeight, i) / this.maxWeight;
            return Mth.ceil(f * 15.0F);
        } else {
            return 0;
        }
    }

    /**
     * Returns the signal encoded in the given block state.
     */
    @Override
    protected int getSignalForState(BlockState state) {
        return state.getValue(POWER);
    }

    /**
     * Returns the block state that encodes the given signal.
     */
    @Override
    protected BlockState setSignalForState(BlockState state, int strength) {
        return state.setValue(POWER, strength);
    }

    @Override
    protected int getPressedTime() {
        return 10;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }
}
