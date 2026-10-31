package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class WoodlandMansionStructure extends Structure {
    public static final MapCodec<WoodlandMansionStructure> CODEC = simpleCodec(WoodlandMansionStructure::new);

    public WoodlandMansionStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        Rotation rotation = Rotation.getRandom(context.random());
        BlockPos blockPos = this.getLowestYIn5by5BoxOffset7Blocks(context, rotation);
        return blockPos.getY() < 60
            ? Optional.empty()
            : Optional.of(
                new Structure.GenerationStub(blockPos, structurePiecesBuilder -> this.generatePieces(structurePiecesBuilder, context, blockPos, rotation))
            );
    }

    private void generatePieces(StructurePiecesBuilder builder, Structure.GenerationContext context, BlockPos pos, Rotation rotation) {
        List<WoodlandMansionPieces.WoodlandMansionPiece> list = Lists.newLinkedList();
        WoodlandMansionPieces.generateMansion(context.structureTemplateManager(), pos, rotation, list, context.random());
        list.forEach(builder::addPiece);
    }

    @Override
    public void afterPlace(
        WorldGenLevel level,
        StructureManager structureManager,
        ChunkGenerator chunkGenerator,
        RandomSource random,
        BoundingBox boundingBox,
        ChunkPos chunkPos,
        PiecesContainer pieces
    ) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int i = level.getMinBuildHeight();
        BoundingBox boundingBox2 = pieces.calculateBoundingBox();
        int j = boundingBox2.minY();

        for (int k = boundingBox.minX(); k <= boundingBox.maxX(); k++) {
            for (int l = boundingBox.minZ(); l <= boundingBox.maxZ(); l++) {
                mutableBlockPos.set(k, j, l);
                if (!level.isEmptyBlock(mutableBlockPos) && boundingBox2.isInside(mutableBlockPos) && pieces.isInsidePiece(mutableBlockPos)) {
                    for (int m = j - 1; m > i; m--) {
                        mutableBlockPos.setY(m);
                        if (!level.isEmptyBlock(mutableBlockPos) && !level.getBlockState(mutableBlockPos).liquid()) {
                            break;
                        }

                        level.setBlock(mutableBlockPos, Blocks.COBBLESTONE.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.WOODLAND_MANSION;
    }
}
