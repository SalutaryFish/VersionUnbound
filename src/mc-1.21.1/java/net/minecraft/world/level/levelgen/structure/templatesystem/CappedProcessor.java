package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.ServerLevelAccessor;

public class CappedProcessor extends StructureProcessor {
    public static final MapCodec<CappedProcessor> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                StructureProcessorType.SINGLE_CODEC.fieldOf("delegate").forGetter(processor -> processor.delegate),
                IntProvider.POSITIVE_CODEC.fieldOf("limit").forGetter(processor -> processor.limit)
            )
            .apply(instance, CappedProcessor::new)
    );
    private final StructureProcessor delegate;
    private final IntProvider limit;

    public CappedProcessor(StructureProcessor delegate, IntProvider limit) {
        this.delegate = delegate;
        this.limit = limit;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.CAPPED;
    }

    @Override
    public final List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
        ServerLevelAccessor serverLevel,
        BlockPos offset,
        BlockPos pos,
        List<StructureTemplate.StructureBlockInfo> originalBlockInfos,
        List<StructureTemplate.StructureBlockInfo> processedBlockInfos,
        StructurePlaceSettings settings
    ) {
        if (this.limit.getMaxValue() != 0 && !processedBlockInfos.isEmpty()) {
            if (originalBlockInfos.size() != processedBlockInfos.size()) {
                Util.logAndPauseIfInIde(
                    "Original block info list not in sync with processed list, skipping processing. Original size: "
                        + originalBlockInfos.size()
                        + ", Processed size: "
                        + processedBlockInfos.size()
                );
                return processedBlockInfos;
            }

            RandomSource randomSource = RandomSource.create(serverLevel.getLevel().getSeed()).forkPositional().at(offset);
            int i = Math.min(this.limit.sample(randomSource), processedBlockInfos.size());
            if (i < 1) {
                return processedBlockInfos;
            }

            IntArrayList intArrayList = Util.toShuffledList(IntStream.range(0, processedBlockInfos.size()), randomSource);
            IntIterator intIterator = intArrayList.intIterator();
            int j = 0;

            while (intIterator.hasNext() && j < i) {
                int k = intIterator.nextInt();
                StructureTemplate.StructureBlockInfo structureBlockInfo = originalBlockInfos.get(k);
                StructureTemplate.StructureBlockInfo structureBlockInfo2 = processedBlockInfos.get(k);
                StructureTemplate.StructureBlockInfo structureBlockInfo3 = this.delegate
                    .processBlock(serverLevel, offset, pos, structureBlockInfo, structureBlockInfo2, settings);
                if (structureBlockInfo3 != null && !structureBlockInfo2.equals(structureBlockInfo3)) {
                    j++;
                    processedBlockInfos.set(k, structureBlockInfo3);
                }
            }

            return processedBlockInfos;
        } else {
            return processedBlockInfos;
        }
    }
}
