package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import org.jspecify.annotations.Nullable;

/**
 * The static methods NeoForge's patches add to {@link AxeItem} and {@link ShovelItem}, with NeoForge's bodies.
 * {@code AddedStaticMethods} adds them there as forwarders to these; the class tweaker opens the two maps.
 */
public final class ToolItemsPatch {
    private ToolItemsPatch() {}

    public static @Nullable BlockState getAxeStrippingState(BlockState originalState) {
        var strippable = originalState.getData(NeoForgeDataMaps.STRIPPABLES);
        if (strippable != null) return strippable.strippedBlock().withPropertiesOf(originalState);
        Block block = AxeItem.STRIPPABLES.get(originalState.getBlock());
        return block != null ? block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, originalState.getValue(RotatedPillarBlock.AXIS)) : null;
    }

    public static @Nullable BlockState getShovelPathingState(BlockState originalState) {
        return ShovelItem.FLATTENABLES.get(originalState.getBlock());
    }
}
