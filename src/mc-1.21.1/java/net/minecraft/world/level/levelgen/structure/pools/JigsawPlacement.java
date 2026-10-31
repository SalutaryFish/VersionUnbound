package net.minecraft.world.level.levelgen.structure.pools;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SequencedPriorityIterator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;

public class JigsawPlacement {
    static final Logger LOGGER = LogUtils.getLogger();

    public static Optional<Structure.GenerationStub> addPieces(
        Structure.GenerationContext context,
        Holder<StructureTemplatePool> startPool,
        Optional<ResourceLocation> startJigsawName,
        int maxDepth,
        BlockPos pos,
        boolean useExpansionHack,
        Optional<Heightmap.Types> projectStartToHeightmap,
        int maxDistanceFromCenter,
        PoolAliasLookup aliasLookup,
        DimensionPadding dimensionPadding,
        LiquidSettings liquidSettings
    ) {
        RegistryAccess registryAccess = context.registryAccess();
        ChunkGenerator chunkGenerator = context.chunkGenerator();
        StructureTemplateManager structureTemplateManager = context.structureTemplateManager();
        LevelHeightAccessor levelHeightAccessor = context.heightAccessor();
        WorldgenRandom worldgenRandom = context.random();
        Registry<StructureTemplatePool> registry = registryAccess.registryOrThrow(Registries.TEMPLATE_POOL);
        Rotation rotation = Rotation.getRandom(worldgenRandom);
        StructureTemplatePool structureTemplatePool = startPool.unwrapKey()
            .flatMap(resourceKey -> registry.getOptional(aliasLookup.lookup((ResourceKey<StructureTemplatePool>)resourceKey)))
            .orElse(startPool.value());
        StructurePoolElement structurePoolElement = structureTemplatePool.getRandomTemplate(worldgenRandom);
        if (structurePoolElement == EmptyPoolElement.INSTANCE) {
            return Optional.empty();
        }

        BlockPos blockPos;
        if (startJigsawName.isPresent()) {
            ResourceLocation resourceLocation = startJigsawName.get();
            Optional<BlockPos> optional = getRandomNamedJigsaw(structurePoolElement, resourceLocation, pos, rotation, structureTemplateManager, worldgenRandom);
            if (optional.isEmpty()) {
                LOGGER.error(
                    "No starting jigsaw {} found in start pool {}",
                    resourceLocation,
                    startPool.unwrapKey().map(resourceKey -> resourceKey.location().toString()).orElse("<unregistered>")
                );
                return Optional.empty();
            }

            blockPos = optional.get();
        } else {
            blockPos = pos;
        }

        Vec3i vec3i = blockPos.subtract(pos);
        BlockPos blockPos2 = pos.subtract(vec3i);
        PoolElementStructurePiece poolElementStructurePiece = new PoolElementStructurePiece(
            structureTemplateManager,
            structurePoolElement,
            blockPos2,
            structurePoolElement.getGroundLevelDelta(),
            rotation,
            structurePoolElement.getBoundingBox(structureTemplateManager, blockPos2, rotation),
            liquidSettings
        );
        BoundingBox boundingBox = poolElementStructurePiece.getBoundingBox();
        int i = (boundingBox.maxX() + boundingBox.minX()) / 2;
        int j = (boundingBox.maxZ() + boundingBox.minZ()) / 2;
        int k;
        if (projectStartToHeightmap.isPresent()) {
            k = pos.getY() + chunkGenerator.getFirstFreeHeight(i, j, projectStartToHeightmap.get(), levelHeightAccessor, context.randomState());
        } else {
            k = blockPos2.getY();
        }

        int l = boundingBox.minY() + poolElementStructurePiece.getGroundLevelDelta();
        poolElementStructurePiece.move(0, k - l, 0);
        int m = k + vec3i.getY();
        return Optional.of(
            new Structure.GenerationStub(
                new BlockPos(i, m, j),
                structurePiecesBuilder -> {
                    List<PoolElementStructurePiece> list = Lists.newArrayList();
                    list.add(poolElementStructurePiece);
                    if (maxDepth > 0) {
                        AABB aABB = new AABB(
                            i - maxDistanceFromCenter,
                            Math.max(m - maxDistanceFromCenter, levelHeightAccessor.getMinBuildHeight() + dimensionPadding.bottom()),
                            j - maxDistanceFromCenter,
                            i + maxDistanceFromCenter + 1,
                            Math.min(m + maxDistanceFromCenter + 1, levelHeightAccessor.getMaxBuildHeight() - dimensionPadding.top()),
                            j + maxDistanceFromCenter + 1
                        );
                        VoxelShape voxelShape = Shapes.join(Shapes.create(aABB), Shapes.create(AABB.of(boundingBox)), BooleanOp.ONLY_FIRST);
                        addPieces(
                            context.randomState(),
                            maxDepth,
                            useExpansionHack,
                            chunkGenerator,
                            structureTemplateManager,
                            levelHeightAccessor,
                            worldgenRandom,
                            registry,
                            poolElementStructurePiece,
                            list,
                            voxelShape,
                            aliasLookup,
                            liquidSettings
                        );
                        list.forEach(structurePiecesBuilder::addPiece);
                    }
                }
            )
        );
    }

    private static Optional<BlockPos> getRandomNamedJigsaw(
        StructurePoolElement element,
        ResourceLocation startJigsawName,
        BlockPos pos,
        Rotation rotation,
        StructureTemplateManager structureTemplateManager,
        WorldgenRandom random
    ) {
        List<StructureTemplate.StructureBlockInfo> list = element.getShuffledJigsawBlocks(structureTemplateManager, pos, rotation, random);
        Optional<BlockPos> optional = Optional.empty();

        for (StructureTemplate.StructureBlockInfo structureBlockInfo : list) {
            ResourceLocation resourceLocation = ResourceLocation.tryParse(
                Objects.requireNonNull(structureBlockInfo.nbt(), () -> structureBlockInfo + " nbt was null").getString("name")
            );
            if (startJigsawName.equals(resourceLocation)) {
                optional = Optional.of(structureBlockInfo.pos());
                break;
            }
        }

        return optional;
    }

    private static void addPieces(
        RandomState randomState,
        int maxDepth,
        boolean useExpansionHack,
        ChunkGenerator chunkGenerator,
        StructureTemplateManager structureTemplateManager,
        LevelHeightAccessor level,
        RandomSource random,
        Registry<StructureTemplatePool> pools,
        PoolElementStructurePiece startPiece,
        List<PoolElementStructurePiece> pieces,
        VoxelShape free,
        PoolAliasLookup aliasLookup,
        LiquidSettings liquidSettings
    ) {
        JigsawPlacement.Placer placer = new JigsawPlacement.Placer(pools, maxDepth, chunkGenerator, structureTemplateManager, pieces, random);
        placer.tryPlacingChildren(startPiece, new MutableObject<>(free), 0, useExpansionHack, level, randomState, aliasLookup, liquidSettings);

        while (placer.placing.hasNext()) {
            JigsawPlacement.PieceState pieceState = placer.placing.next();
            placer.tryPlacingChildren(pieceState.piece, pieceState.free, pieceState.depth, useExpansionHack, level, randomState, aliasLookup, liquidSettings);
        }
    }

    public static boolean generateJigsaw(
        ServerLevel level, Holder<StructureTemplatePool> startPool, ResourceLocation startJigsawName, int maxDepth, BlockPos pos, boolean keepJigsaws
    ) {
        ChunkGenerator chunkGenerator = level.getChunkSource().getGenerator();
        StructureTemplateManager structureTemplateManager = level.getStructureManager();
        StructureManager structureManager = level.structureManager();
        RandomSource randomSource = level.getRandom();
        Structure.GenerationContext generationContext = new Structure.GenerationContext(
            level.registryAccess(),
            chunkGenerator,
            chunkGenerator.getBiomeSource(),
            level.getChunkSource().randomState(),
            structureTemplateManager,
            level.getSeed(),
            new ChunkPos(pos),
            level,
            holder -> true
        );
        Optional<Structure.GenerationStub> optional = addPieces(
            generationContext,
            startPool,
            Optional.of(startJigsawName),
            maxDepth,
            pos,
            false,
            Optional.empty(),
            128,
            PoolAliasLookup.EMPTY,
            JigsawStructure.DEFAULT_DIMENSION_PADDING,
            JigsawStructure.DEFAULT_LIQUID_SETTINGS
        );
        if (optional.isPresent()) {
            StructurePiecesBuilder structurePiecesBuilder = optional.get().getPiecesBuilder();

            for (StructurePiece structurePiece : structurePiecesBuilder.build().pieces()) {
                if (structurePiece instanceof PoolElementStructurePiece poolElementStructurePiece) {
                    poolElementStructurePiece.place(level, structureManager, chunkGenerator, randomSource, BoundingBox.infinite(), pos, keepJigsaws);
                }
            }

            return true;
        } else {
            return false;
        }
    }

    record PieceState(PoolElementStructurePiece piece, MutableObject<VoxelShape> free, int depth) {
    }

    static final class Placer {
        private final Registry<StructureTemplatePool> pools;
        private final int maxDepth;
        private final ChunkGenerator chunkGenerator;
        private final StructureTemplateManager structureTemplateManager;
        private final List<? super PoolElementStructurePiece> pieces;
        private final RandomSource random;
        final SequencedPriorityIterator<JigsawPlacement.PieceState> placing = new SequencedPriorityIterator<>();

        Placer(
            Registry<StructureTemplatePool> pools,
            int maxDepth,
            ChunkGenerator chunkGenerator,
            StructureTemplateManager structureTemplateManager,
            List<? super PoolElementStructurePiece> pieces,
            RandomSource random
        ) {
            this.pools = pools;
            this.maxDepth = maxDepth;
            this.chunkGenerator = chunkGenerator;
            this.structureTemplateManager = structureTemplateManager;
            this.pieces = pieces;
            this.random = random;
        }

        void tryPlacingChildren(
            PoolElementStructurePiece piece,
            MutableObject<VoxelShape> free,
            int depth,
            boolean useExpansionHack,
            LevelHeightAccessor level,
            RandomState random,
            PoolAliasLookup poolAliasLookup,
            LiquidSettings liquidSettings
        ) {
            StructurePoolElement structurePoolElement = piece.getElement();
            BlockPos blockPos = piece.getPosition();
            Rotation rotation = piece.getRotation();
            StructureTemplatePool.Projection projection = structurePoolElement.getProjection();
            boolean bl = projection == StructureTemplatePool.Projection.RIGID;
            MutableObject<VoxelShape> mutableObject = new MutableObject<>();
            BoundingBox boundingBox = piece.getBoundingBox();
            int i = boundingBox.minY();

            label134:
            for (StructureTemplate.StructureBlockInfo structureBlockInfo : structurePoolElement.getShuffledJigsawBlocks(
                this.structureTemplateManager, blockPos, rotation, this.random
            )) {
                Direction direction = JigsawBlock.getFrontFacing(structureBlockInfo.state());
                BlockPos blockPos2 = structureBlockInfo.pos();
                BlockPos blockPos3 = blockPos2.relative(direction);
                int j = blockPos2.getY() - i;
                int k = -1;
                ResourceKey<StructureTemplatePool> resourceKey = readPoolKey(structureBlockInfo, poolAliasLookup);
                Optional<? extends Holder<StructureTemplatePool>> optional = this.pools.getHolder(resourceKey);
                if (optional.isEmpty()) {
                    JigsawPlacement.LOGGER.warn("Empty or non-existent pool: {}", resourceKey.location());
                } else {
                    Holder<StructureTemplatePool> holder = (Holder<StructureTemplatePool>)optional.get();
                    if (holder.value().size() == 0 && !holder.is(Pools.EMPTY)) {
                        JigsawPlacement.LOGGER.warn("Empty or non-existent pool: {}", resourceKey.location());
                    } else {
                        Holder<StructureTemplatePool> holder2 = holder.value().getFallback();
                        if (holder2.value().size() == 0 && !holder2.is(Pools.EMPTY)) {
                            JigsawPlacement.LOGGER
                                .warn(
                                    "Empty or non-existent fallback pool: {}",
                                    holder2.unwrapKey().map(resourceKeyx -> resourceKeyx.location().toString()).orElse("<unregistered>")
                                );
                        } else {
                            boolean bl2 = boundingBox.isInside(blockPos3);
                            MutableObject<VoxelShape> mutableObject2;
                            if (bl2) {
                                mutableObject2 = mutableObject;
                                if (mutableObject.getValue() == null) {
                                    mutableObject.setValue(Shapes.create(AABB.of(boundingBox)));
                                }
                            } else {
                                mutableObject2 = free;
                            }

                            List<StructurePoolElement> list = Lists.newArrayList();
                            if (depth != this.maxDepth) {
                                list.addAll(holder.value().getShuffledTemplates(this.random));
                            }

                            list.addAll(holder2.value().getShuffledTemplates(this.random));
                            int l = structureBlockInfo.nbt() != null ? structureBlockInfo.nbt().getInt("placement_priority") : 0;

                            for (StructurePoolElement structurePoolElement2 : list) {
                                if (structurePoolElement2 == EmptyPoolElement.INSTANCE) {
                                    break;
                                }

                                for (Rotation rotation2 : Rotation.getShuffled(this.random)) {
                                    List<StructureTemplate.StructureBlockInfo> list2 = structurePoolElement2.getShuffledJigsawBlocks(
                                        this.structureTemplateManager, BlockPos.ZERO, rotation2, this.random
                                    );
                                    BoundingBox boundingBox2 = structurePoolElement2.getBoundingBox(this.structureTemplateManager, BlockPos.ZERO, rotation2);
                                    int m;
                                    if (useExpansionHack && boundingBox2.getYSpan() <= 16) {
                                        m = list2.stream()
                                            .mapToInt(
                                                structureBlockInfox -> {
                                                    if (!boundingBox2.isInside(
                                                        structureBlockInfox.pos().relative(JigsawBlock.getFrontFacing(structureBlockInfox.state()))
                                                    )) {
                                                        return 0;
                                                    }

                                                    ResourceKey<StructureTemplatePool> resourceKeyx = readPoolKey(structureBlockInfox, poolAliasLookup);
                                                    Optional<? extends Holder<StructureTemplatePool>> optionalx = this.pools.getHolder(resourceKeyx);
                                                    Optional<Holder<StructureTemplatePool>> optional2 = optionalx.map(
                                                        holderx -> ((StructureTemplatePool)holderx.value()).getFallback()
                                                    );
                                                    int ix = optionalx.<Integer>map(
                                                            holderx -> ((StructureTemplatePool)holderx.value()).getMaxSize(this.structureTemplateManager)
                                                        )
                                                        .orElse(0);
                                                    int jx = optional2.<Integer>map(
                                                            holderx -> ((StructureTemplatePool)holderx.value()).getMaxSize(this.structureTemplateManager)
                                                        )
                                                        .orElse(0);
                                                    return Math.max(ix, jx);
                                                }
                                            )
                                            .max()
                                            .orElse(0);
                                    } else {
                                        m = 0;
                                    }

                                    for (StructureTemplate.StructureBlockInfo structureBlockInfo2 : list2) {
                                        if (JigsawBlock.canAttach(structureBlockInfo, structureBlockInfo2)) {
                                            BlockPos blockPos4 = structureBlockInfo2.pos();
                                            BlockPos blockPos5 = blockPos3.subtract(blockPos4);
                                            BoundingBox boundingBox3 = structurePoolElement2.getBoundingBox(this.structureTemplateManager, blockPos5, rotation2);
                                            int n = boundingBox3.minY();
                                            StructureTemplatePool.Projection projection2 = structurePoolElement2.getProjection();
                                            boolean bl3 = projection2 == StructureTemplatePool.Projection.RIGID;
                                            int o = blockPos4.getY();
                                            int p = j - o + JigsawBlock.getFrontFacing(structureBlockInfo.state()).getStepY();
                                            int q;
                                            if (bl && bl3) {
                                                q = i + p;
                                            } else {
                                                if (k == -1) {
                                                    k = this.chunkGenerator
                                                        .getFirstFreeHeight(blockPos2.getX(), blockPos2.getZ(), Heightmap.Types.WORLD_SURFACE_WG, level, random);
                                                }

                                                q = k - o;
                                            }

                                            int r = q - n;
                                            BoundingBox boundingBox4 = boundingBox3.moved(0, r, 0);
                                            BlockPos blockPos6 = blockPos5.offset(0, r, 0);
                                            if (m > 0) {
                                                int s = Math.max(m + 1, boundingBox4.maxY() - boundingBox4.minY());
                                                boundingBox4.encapsulate(new BlockPos(boundingBox4.minX(), boundingBox4.minY() + s, boundingBox4.minZ()));
                                            }

                                            if (!Shapes.joinIsNotEmpty(
                                                mutableObject2.getValue(), Shapes.create(AABB.of(boundingBox4).deflate(0.25)), BooleanOp.ONLY_SECOND
                                            )) {
                                                mutableObject2.setValue(
                                                    Shapes.joinUnoptimized(
                                                        mutableObject2.getValue(), Shapes.create(AABB.of(boundingBox4)), BooleanOp.ONLY_FIRST
                                                    )
                                                );
                                                int s = piece.getGroundLevelDelta();
                                                int t;
                                                if (bl3) {
                                                    t = s - p;
                                                } else {
                                                    t = structurePoolElement2.getGroundLevelDelta();
                                                }

                                                PoolElementStructurePiece poolElementStructurePiece = new PoolElementStructurePiece(
                                                    this.structureTemplateManager, structurePoolElement2, blockPos6, t, rotation2, boundingBox4, liquidSettings
                                                );
                                                int u;
                                                if (bl) {
                                                    u = i + j;
                                                } else if (bl3) {
                                                    u = q + o;
                                                } else {
                                                    if (k == -1) {
                                                        k = this.chunkGenerator
                                                            .getFirstFreeHeight(
                                                                blockPos2.getX(), blockPos2.getZ(), Heightmap.Types.WORLD_SURFACE_WG, level, random
                                                            );
                                                    }

                                                    u = k + p / 2;
                                                }

                                                piece.addJunction(new JigsawJunction(blockPos3.getX(), u - j + s, blockPos3.getZ(), p, projection2));
                                                poolElementStructurePiece.addJunction(
                                                    new JigsawJunction(blockPos2.getX(), u - o + t, blockPos2.getZ(), -p, projection)
                                                );
                                                this.pieces.add(poolElementStructurePiece);
                                                if (depth + 1 <= this.maxDepth) {
                                                    JigsawPlacement.PieceState pieceState = new JigsawPlacement.PieceState(
                                                        poolElementStructurePiece, mutableObject2, depth + 1
                                                    );
                                                    this.placing.add(pieceState, l);
                                                }
                                                continue label134;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        private static ResourceKey<StructureTemplatePool> readPoolKey(StructureTemplate.StructureBlockInfo blockInfo, PoolAliasLookup aliasLookup) {
            CompoundTag compoundTag = Objects.requireNonNull(blockInfo.nbt(), () -> blockInfo + " nbt was null");
            ResourceKey<StructureTemplatePool> resourceKey = Pools.parseKey(compoundTag.getString("pool"));
            return aliasLookup.lookup(resourceKey);
        }
    }
}
