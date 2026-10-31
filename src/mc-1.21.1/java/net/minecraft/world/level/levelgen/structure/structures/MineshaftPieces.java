package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.slf4j.Logger;

public class MineshaftPieces {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_SHAFT_WIDTH = 3;
    private static final int DEFAULT_SHAFT_HEIGHT = 3;
    private static final int DEFAULT_SHAFT_LENGTH = 5;
    private static final int MAX_PILLAR_HEIGHT = 20;
    private static final int MAX_CHAIN_HEIGHT = 50;
    private static final int MAX_DEPTH = 8;
    public static final int MAGIC_START_Y = 50;

    private static MineshaftPieces.MineShaftPiece createRandomShaftPiece(
        StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, @Nullable Direction orientation, int genDepth, MineshaftStructure.Type type
    ) {
        int i = random.nextInt(100);
        if (i >= 80) {
            BoundingBox boundingBox = MineshaftPieces.MineShaftCrossing.findCrossing(pieces, random, x, y, z, orientation);
            if (boundingBox != null) {
                return new MineshaftPieces.MineShaftCrossing(genDepth, boundingBox, orientation, type);
            }
        } else if (i >= 70) {
            BoundingBox boundingBox = MineshaftPieces.MineShaftStairs.findStairs(pieces, random, x, y, z, orientation);
            if (boundingBox != null) {
                return new MineshaftPieces.MineShaftStairs(genDepth, boundingBox, orientation, type);
            }
        } else {
            BoundingBox boundingBox = MineshaftPieces.MineShaftCorridor.findCorridorSize(pieces, random, x, y, z, orientation);
            if (boundingBox != null) {
                return new MineshaftPieces.MineShaftCorridor(genDepth, random, boundingBox, orientation, type);
            }
        }

        return null;
    }

    static MineshaftPieces.MineShaftPiece generateAndAddPiece(
        StructurePiece piece, StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction direction, int genDepth
    ) {
        if (genDepth > 8) {
            return null;
        }

        if (Math.abs(x - piece.getBoundingBox().minX()) <= 80 && Math.abs(z - piece.getBoundingBox().minZ()) <= 80) {
            MineshaftStructure.Type type = ((MineshaftPieces.MineShaftPiece)piece).type;
            MineshaftPieces.MineShaftPiece mineShaftPiece = createRandomShaftPiece(pieces, random, x, y, z, direction, genDepth + 1, type);
            if (mineShaftPiece != null) {
                pieces.addPiece(mineShaftPiece);
                mineShaftPiece.addChildren(piece, pieces, random);
            }

            return mineShaftPiece;
        } else {
            return null;
        }
    }

    public static class MineShaftCorridor extends MineshaftPieces.MineShaftPiece {
        private final boolean hasRails;
        private final boolean spiderCorridor;
        private boolean hasPlacedSpider;
        private final int numSections;

        public MineShaftCorridor(CompoundTag tag) {
            super(StructurePieceType.MINE_SHAFT_CORRIDOR, tag);
            this.hasRails = tag.getBoolean("hr");
            this.spiderCorridor = tag.getBoolean("sc");
            this.hasPlacedSpider = tag.getBoolean("hps");
            this.numSections = tag.getInt("Num");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("hr", this.hasRails);
            tag.putBoolean("sc", this.spiderCorridor);
            tag.putBoolean("hps", this.hasPlacedSpider);
            tag.putInt("Num", this.numSections);
        }

        public MineShaftCorridor(int genDepth, RandomSource random, BoundingBox boundingBox, Direction orientation, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_CORRIDOR, genDepth, type, boundingBox);
            this.setOrientation(orientation);
            this.hasRails = random.nextInt(3) == 0;
            this.spiderCorridor = !this.hasRails && random.nextInt(23) == 0;
            if (this.getOrientation().getAxis() == Direction.Axis.Z) {
                this.numSections = boundingBox.getZSpan() / 5;
            } else {
                this.numSections = boundingBox.getXSpan() / 5;
            }
        }

        @Nullable
        public static BoundingBox findCorridorSize(StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction direction) {
            for (int i = random.nextInt(3) + 2; i > 0; i--) {
                int j = i * 5;

                BoundingBox boundingBox = switch (direction) {
                    default -> new BoundingBox(0, 0, -(j - 1), 2, 2, 0);
                    case SOUTH -> new BoundingBox(0, 0, 0, 2, 2, j - 1);
                    case WEST -> new BoundingBox(-(j - 1), 0, 0, 0, 2, 2);
                    case EAST -> new BoundingBox(0, 0, 0, j - 1, 2, 2);
                };
                boundingBox.move(x, y, z);
                if (pieces.findCollisionPiece(boundingBox) == null) {
                    return boundingBox;
                }
            }

            return null;
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            int i = this.getGenDepth();
            int j = random.nextInt(4);
            Direction direction = this.getOrientation();
            if (direction != null) {
                switch (direction) {
                    case NORTH:
                    default:
                        if (j <= 1) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX(),
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ() - 1,
                                direction,
                                i
                            );
                        } else if (j == 2) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX() - 1,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ(),
                                Direction.WEST,
                                i
                            );
                        } else {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.maxX() + 1,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ(),
                                Direction.EAST,
                                i
                            );
                        }
                        break;
                    case SOUTH:
                        if (j <= 1) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX(),
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.maxZ() + 1,
                                direction,
                                i
                            );
                        } else if (j == 2) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX() - 1,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.maxZ() - 3,
                                Direction.WEST,
                                i
                            );
                        } else {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.maxX() + 1,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.maxZ() - 3,
                                Direction.EAST,
                                i
                            );
                        }
                        break;
                    case WEST:
                        if (j <= 1) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX() - 1,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ(),
                                direction,
                                i
                            );
                        } else if (j == 2) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX(),
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ() - 1,
                                Direction.NORTH,
                                i
                            );
                        } else {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.minX(),
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.maxZ() + 1,
                                Direction.SOUTH,
                                i
                            );
                        }
                        break;
                    case EAST:
                        if (j <= 1) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.maxX() + 1,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ(),
                                direction,
                                i
                            );
                        } else if (j == 2) {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.maxX() - 3,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.minZ() - 1,
                                Direction.NORTH,
                                i
                            );
                        } else {
                            MineshaftPieces.generateAndAddPiece(
                                piece,
                                pieces,
                                random,
                                this.boundingBox.maxX() - 3,
                                this.boundingBox.minY() - 1 + random.nextInt(3),
                                this.boundingBox.maxZ() + 1,
                                Direction.SOUTH,
                                i
                            );
                        }
                }
            }

            if (i < 8) {
                if (direction != Direction.NORTH && direction != Direction.SOUTH) {
                    for (int k = this.boundingBox.minX() + 3; k + 3 <= this.boundingBox.maxX(); k += 5) {
                        int l = random.nextInt(5);
                        if (l == 0) {
                            MineshaftPieces.generateAndAddPiece(
                                piece, pieces, random, k, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i + 1
                            );
                        } else if (l == 1) {
                            MineshaftPieces.generateAndAddPiece(
                                piece, pieces, random, k, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i + 1
                            );
                        }
                    }
                } else {
                    for (int k = this.boundingBox.minZ() + 3; k + 3 <= this.boundingBox.maxZ(); k += 5) {
                        int l = random.nextInt(5);
                        if (l == 0) {
                            MineshaftPieces.generateAndAddPiece(
                                piece, pieces, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), k, Direction.WEST, i + 1
                            );
                        } else if (l == 1) {
                            MineshaftPieces.generateAndAddPiece(
                                piece, pieces, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), k, Direction.EAST, i + 1
                            );
                        }
                    }
                }
            }
        }

        @Override
        protected boolean createChest(WorldGenLevel level, BoundingBox box, RandomSource random, int x, int y, int z, ResourceKey<LootTable> lootTable) {
            BlockPos blockPos = this.getWorldPos(x, y, z);
            if (box.isInside(blockPos) && level.getBlockState(blockPos).isAir() && !level.getBlockState(blockPos.below()).isAir()) {
                BlockState blockState = Blocks.RAIL
                    .defaultBlockState()
                    .setValue(RailBlock.SHAPE, random.nextBoolean() ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST);
                this.placeBlock(level, blockState, x, y, z, box);
                MinecartChest minecartChest = new MinecartChest(level.getLevel(), blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
                minecartChest.setLootTable(lootTable, random.nextLong());
                level.addFreshEntity(minecartChest);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox box,
            ChunkPos chunkPos,
            BlockPos pos
        ) {
            if (!this.isInInvalidLocation(level, box)) {
                int i = 0;
                int j = 2;
                int k = 0;
                int l = 2;
                int m = this.numSections * 5 - 1;
                BlockState blockState = this.type.getPlanksState();
                this.generateBox(level, box, 0, 0, 0, 2, 1, m, CAVE_AIR, CAVE_AIR, false);
                this.generateMaybeBox(level, box, random, 0.8F, 0, 2, 0, 2, 2, m, CAVE_AIR, CAVE_AIR, false, false);
                if (this.spiderCorridor) {
                    this.generateMaybeBox(level, box, random, 0.6F, 0, 0, 0, 2, 1, m, Blocks.COBWEB.defaultBlockState(), CAVE_AIR, false, true);
                }

                for (int n = 0; n < this.numSections; n++) {
                    int o = 2 + n * 5;
                    this.placeSupport(level, box, 0, 0, o, 2, 2, random);
                    this.maybePlaceCobWeb(level, box, random, 0.1F, 0, 2, o - 1);
                    this.maybePlaceCobWeb(level, box, random, 0.1F, 2, 2, o - 1);
                    this.maybePlaceCobWeb(level, box, random, 0.1F, 0, 2, o + 1);
                    this.maybePlaceCobWeb(level, box, random, 0.1F, 2, 2, o + 1);
                    this.maybePlaceCobWeb(level, box, random, 0.05F, 0, 2, o - 2);
                    this.maybePlaceCobWeb(level, box, random, 0.05F, 2, 2, o - 2);
                    this.maybePlaceCobWeb(level, box, random, 0.05F, 0, 2, o + 2);
                    this.maybePlaceCobWeb(level, box, random, 0.05F, 2, 2, o + 2);
                    if (random.nextInt(100) == 0) {
                        this.createChest(level, box, random, 2, 0, o - 1, BuiltInLootTables.ABANDONED_MINESHAFT);
                    }

                    if (random.nextInt(100) == 0) {
                        this.createChest(level, box, random, 0, 0, o + 1, BuiltInLootTables.ABANDONED_MINESHAFT);
                    }

                    if (this.spiderCorridor && !this.hasPlacedSpider) {
                        int p = 1;
                        int q = o - 1 + random.nextInt(3);
                        BlockPos blockPos = this.getWorldPos(1, 0, q);
                        if (box.isInside(blockPos) && this.isInterior(level, 1, 0, q, box)) {
                            this.hasPlacedSpider = true;
                            level.setBlock(blockPos, Blocks.SPAWNER.defaultBlockState(), 2);
                            if (level.getBlockEntity(blockPos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
                                spawnerBlockEntity.setEntityId(EntityType.CAVE_SPIDER, random);
                            }
                        }
                    }
                }

                for (int n = 0; n <= 2; n++) {
                    for (int o = 0; o <= m; o++) {
                        this.setPlanksBlock(level, box, blockState, n, -1, o);
                    }
                }

                int n = 2;
                this.placeDoubleLowerOrUpperSupport(level, box, 0, -1, 2);
                if (this.numSections > 1) {
                    int o = m - 2;
                    this.placeDoubleLowerOrUpperSupport(level, box, 0, -1, o);
                }

                if (this.hasRails) {
                    BlockState blockState2 = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH);

                    for (int p = 0; p <= m; p++) {
                        BlockState blockState3 = this.getBlock(level, 1, -1, p, box);
                        if (!blockState3.isAir() && blockState3.isSolidRender(level, this.getWorldPos(1, -1, p))) {
                            float f = this.isInterior(level, 1, 0, p, box) ? 0.7F : 0.9F;
                            this.maybeGenerateBlock(level, box, random, f, 1, 0, p, blockState2);
                        }
                    }
                }
            }
        }

        private void placeDoubleLowerOrUpperSupport(WorldGenLevel level, BoundingBox box, int x, int y, int z) {
            BlockState blockState = this.type.getWoodState();
            BlockState blockState2 = this.type.getPlanksState();
            if (this.getBlock(level, x, y, z, box).is(blockState2.getBlock())) {
                this.fillPillarDownOrChainUp(level, blockState, x, y, z, box);
            }

            if (this.getBlock(level, x + 2, y, z, box).is(blockState2.getBlock())) {
                this.fillPillarDownOrChainUp(level, blockState, x + 2, y, z, box);
            }
        }

        @Override
        protected void fillColumnDown(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox box) {
            BlockPos.MutableBlockPos mutableBlockPos = this.getWorldPos(x, y, z);
            if (box.isInside(mutableBlockPos)) {
                int i = mutableBlockPos.getY();

                while (this.isReplaceableByStructures(level.getBlockState(mutableBlockPos)) && mutableBlockPos.getY() > level.getMinBuildHeight() + 1) {
                    mutableBlockPos.move(Direction.DOWN);
                }

                if (this.canPlaceColumnOnTopOf(level, mutableBlockPos, level.getBlockState(mutableBlockPos))) {
                    while (mutableBlockPos.getY() < i) {
                        mutableBlockPos.move(Direction.UP);
                        level.setBlock(mutableBlockPos, state, 2);
                    }
                }
            }
        }

        protected void fillPillarDownOrChainUp(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox box) {
            BlockPos.MutableBlockPos mutableBlockPos = this.getWorldPos(x, y, z);
            if (box.isInside(mutableBlockPos)) {
                int i = mutableBlockPos.getY();
                int j = 1;
                boolean bl = true;

                for (boolean bl2 = true; bl || bl2; j++) {
                    if (bl) {
                        mutableBlockPos.setY(i - j);
                        BlockState blockState = level.getBlockState(mutableBlockPos);
                        boolean bl3 = this.isReplaceableByStructures(blockState) && !blockState.is(Blocks.LAVA);
                        if (!bl3 && this.canPlaceColumnOnTopOf(level, mutableBlockPos, blockState)) {
                            fillColumnBetween(level, state, mutableBlockPos, i - j + 1, i);
                            return;
                        }

                        bl = j <= 20 && bl3 && mutableBlockPos.getY() > level.getMinBuildHeight() + 1;
                    }

                    if (bl2) {
                        mutableBlockPos.setY(i + j);
                        BlockState blockState = level.getBlockState(mutableBlockPos);
                        boolean bl3 = this.isReplaceableByStructures(blockState);
                        if (!bl3 && this.canHangChainBelow(level, mutableBlockPos, blockState)) {
                            level.setBlock(mutableBlockPos.setY(i + 1), this.type.getFenceState(), 2);
                            fillColumnBetween(level, Blocks.CHAIN.defaultBlockState(), mutableBlockPos, i + 2, i + j);
                            return;
                        }

                        bl2 = j <= 50 && bl3 && mutableBlockPos.getY() < level.getMaxBuildHeight() - 1;
                    }
                }
            }
        }

        private static void fillColumnBetween(WorldGenLevel level, BlockState state, BlockPos.MutableBlockPos pos, int minY, int maxY) {
            for (int i = minY; i < maxY; i++) {
                level.setBlock(pos.setY(i), state, 2);
            }
        }

        private boolean canPlaceColumnOnTopOf(LevelReader level, BlockPos pos, BlockState state) {
            return state.isFaceSturdy(level, pos, Direction.UP);
        }

        private boolean canHangChainBelow(LevelReader level, BlockPos pos, BlockState state) {
            return Block.canSupportCenter(level, pos, Direction.DOWN) && !(state.getBlock() instanceof FallingBlock);
        }

        private void placeSupport(WorldGenLevel level, BoundingBox box, int minX, int minY, int z, int maxY, int maxX, RandomSource random) {
            if (this.isSupportingBox(level, box, minX, maxX, maxY, z)) {
                BlockState blockState = this.type.getPlanksState();
                BlockState blockState2 = this.type.getFenceState();
                this.generateBox(level, box, minX, minY, z, minX, maxY - 1, z, blockState2.setValue(FenceBlock.WEST, true), CAVE_AIR, false);
                this.generateBox(level, box, maxX, minY, z, maxX, maxY - 1, z, blockState2.setValue(FenceBlock.EAST, true), CAVE_AIR, false);
                if (random.nextInt(4) == 0) {
                    this.generateBox(level, box, minX, maxY, z, minX, maxY, z, blockState, CAVE_AIR, false);
                    this.generateBox(level, box, maxX, maxY, z, maxX, maxY, z, blockState, CAVE_AIR, false);
                } else {
                    this.generateBox(level, box, minX, maxY, z, maxX, maxY, z, blockState, CAVE_AIR, false);
                    this.maybeGenerateBlock(
                        level,
                        box,
                        random,
                        0.05F,
                        minX + 1,
                        maxY,
                        z - 1,
                        Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH)
                    );
                    this.maybeGenerateBlock(
                        level,
                        box,
                        random,
                        0.05F,
                        minX + 1,
                        maxY,
                        z + 1,
                        Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH)
                    );
                }
            }
        }

        private void maybePlaceCobWeb(WorldGenLevel level, BoundingBox box, RandomSource random, float chance, int x, int y, int z) {
            if (this.isInterior(level, x, y, z, box) && random.nextFloat() < chance && this.hasSturdyNeighbours(level, box, x, y, z, 2)) {
                this.placeBlock(level, Blocks.COBWEB.defaultBlockState(), x, y, z, box);
            }
        }

        private boolean hasSturdyNeighbours(WorldGenLevel level, BoundingBox box, int x, int y, int z, int required) {
            BlockPos.MutableBlockPos mutableBlockPos = this.getWorldPos(x, y, z);
            int i = 0;

            for (Direction direction : Direction.values()) {
                mutableBlockPos.move(direction);
                if (box.isInside(mutableBlockPos) && level.getBlockState(mutableBlockPos).isFaceSturdy(level, mutableBlockPos, direction.getOpposite())) {
                    if (++i >= required) {
                        return true;
                    }
                }

                mutableBlockPos.move(direction.getOpposite());
            }

            return false;
        }
    }

    public static class MineShaftCrossing extends MineshaftPieces.MineShaftPiece {
        private final Direction direction;
        private final boolean isTwoFloored;

        public MineShaftCrossing(CompoundTag tag) {
            super(StructurePieceType.MINE_SHAFT_CROSSING, tag);
            this.isTwoFloored = tag.getBoolean("tf");
            this.direction = Direction.from2DDataValue(tag.getInt("D"));
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putBoolean("tf", this.isTwoFloored);
            tag.putInt("D", this.direction.get2DDataValue());
        }

        public MineShaftCrossing(int genDepth, BoundingBox boundingBox, @Nullable Direction direction, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_CROSSING, genDepth, type, boundingBox);
            this.direction = direction;
            this.isTwoFloored = boundingBox.getYSpan() > 3;
        }

        @Nullable
        public static BoundingBox findCrossing(StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction direction) {
            int i;
            if (random.nextInt(4) == 0) {
                i = 6;
            } else {
                i = 2;
            }
            BoundingBox boundingBox = switch (direction) {
                default -> new BoundingBox(-1, 0, -4, 3, i, 0);
                case SOUTH -> new BoundingBox(-1, 0, 0, 3, i, 4);
                case WEST -> new BoundingBox(-4, 0, -1, 0, i, 3);
                case EAST -> new BoundingBox(0, 0, -1, 4, i, 3);
            };
            boundingBox.move(x, y, z);
            return pieces.findCollisionPiece(boundingBox) != null ? null : boundingBox;
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            int i = this.getGenDepth();
            switch (this.direction) {
                case NORTH:
                default:
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.WEST, i
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.EAST, i
                    );
                    break;
                case SOUTH:
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.WEST, i
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.EAST, i
                    );
                    break;
                case WEST:
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.WEST, i
                    );
                    break;
                case EAST:
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i
                    );
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, Direction.EAST, i
                    );
            }

            if (this.isTwoFloored) {
                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.minZ() - 1, Direction.NORTH, i
                    );
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() - 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.minZ() + 1, Direction.WEST, i
                    );
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.maxX() + 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.minZ() + 1, Direction.EAST, i
                    );
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(
                        piece, pieces, random, this.boundingBox.minX() + 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.maxZ() + 1, Direction.SOUTH, i
                    );
                }
            }
        }

        @Override
        public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox box,
            ChunkPos chunkPos,
            BlockPos pos
        ) {
            if (!this.isInInvalidLocation(level, box)) {
                BlockState blockState = this.type.getPlanksState();
                if (this.isTwoFloored) {
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX() + 1,
                        this.boundingBox.minY(),
                        this.boundingBox.minZ(),
                        this.boundingBox.maxX() - 1,
                        this.boundingBox.minY() + 3 - 1,
                        this.boundingBox.maxZ(),
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX(),
                        this.boundingBox.minY(),
                        this.boundingBox.minZ() + 1,
                        this.boundingBox.maxX(),
                        this.boundingBox.minY() + 3 - 1,
                        this.boundingBox.maxZ() - 1,
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX() + 1,
                        this.boundingBox.maxY() - 2,
                        this.boundingBox.minZ(),
                        this.boundingBox.maxX() - 1,
                        this.boundingBox.maxY(),
                        this.boundingBox.maxZ(),
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX(),
                        this.boundingBox.maxY() - 2,
                        this.boundingBox.minZ() + 1,
                        this.boundingBox.maxX(),
                        this.boundingBox.maxY(),
                        this.boundingBox.maxZ() - 1,
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX() + 1,
                        this.boundingBox.minY() + 3,
                        this.boundingBox.minZ() + 1,
                        this.boundingBox.maxX() - 1,
                        this.boundingBox.minY() + 3,
                        this.boundingBox.maxZ() - 1,
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                } else {
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX() + 1,
                        this.boundingBox.minY(),
                        this.boundingBox.minZ(),
                        this.boundingBox.maxX() - 1,
                        this.boundingBox.maxY(),
                        this.boundingBox.maxZ(),
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                    this.generateBox(
                        level,
                        box,
                        this.boundingBox.minX(),
                        this.boundingBox.minY(),
                        this.boundingBox.minZ() + 1,
                        this.boundingBox.maxX(),
                        this.boundingBox.maxY(),
                        this.boundingBox.maxZ() - 1,
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                }

                this.placeSupportPillar(level, box, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, this.boundingBox.maxY());
                this.placeSupportPillar(level, box, this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() - 1, this.boundingBox.maxY());
                this.placeSupportPillar(level, box, this.boundingBox.maxX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1, this.boundingBox.maxY());
                this.placeSupportPillar(level, box, this.boundingBox.maxX() - 1, this.boundingBox.minY(), this.boundingBox.maxZ() - 1, this.boundingBox.maxY());
                int i = this.boundingBox.minY() - 1;

                for (int j = this.boundingBox.minX(); j <= this.boundingBox.maxX(); j++) {
                    for (int k = this.boundingBox.minZ(); k <= this.boundingBox.maxZ(); k++) {
                        this.setPlanksBlock(level, box, blockState, j, i, k);
                    }
                }
            }
        }

        private void placeSupportPillar(WorldGenLevel level, BoundingBox box, int x, int y, int z, int maxY) {
            if (!this.getBlock(level, x, maxY + 1, z, box).isAir()) {
                this.generateBox(level, box, x, y, z, x, maxY, z, this.type.getPlanksState(), CAVE_AIR, false);
            }
        }
    }

    abstract static class MineShaftPiece extends StructurePiece {
        protected MineshaftStructure.Type type;

        public MineShaftPiece(StructurePieceType structurePieceType, int genDepth, MineshaftStructure.Type type, BoundingBox boundingBox) {
            super(structurePieceType, genDepth, boundingBox);
            this.type = type;
        }

        public MineShaftPiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
            this.type = MineshaftStructure.Type.byId(tag.getInt("MST"));
        }

        @Override
        protected boolean canBeReplaced(LevelReader level, int x, int y, int z, BoundingBox box) {
            BlockState blockState = this.getBlock(level, x, y, z, box);
            return !blockState.is(this.type.getPlanksState().getBlock())
                && !blockState.is(this.type.getWoodState().getBlock())
                && !blockState.is(this.type.getFenceState().getBlock())
                && !blockState.is(Blocks.CHAIN);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt("MST", this.type.ordinal());
        }

        protected boolean isSupportingBox(BlockGetter level, BoundingBox box, int xStart, int xEnd, int y, int z) {
            for (int i = xStart; i <= xEnd; i++) {
                if (this.getBlock(level, i, y + 1, z, box).isAir()) {
                    return false;
                }
            }

            return true;
        }

        protected boolean isInInvalidLocation(LevelAccessor level, BoundingBox boundingBox) {
            int i = Math.max(this.boundingBox.minX() - 1, boundingBox.minX());
            int j = Math.max(this.boundingBox.minY() - 1, boundingBox.minY());
            int k = Math.max(this.boundingBox.minZ() - 1, boundingBox.minZ());
            int l = Math.min(this.boundingBox.maxX() + 1, boundingBox.maxX());
            int m = Math.min(this.boundingBox.maxY() + 1, boundingBox.maxY());
            int n = Math.min(this.boundingBox.maxZ() + 1, boundingBox.maxZ());
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos((i + l) / 2, (j + m) / 2, (k + n) / 2);
            if (level.getBiome(mutableBlockPos).is(BiomeTags.MINESHAFT_BLOCKING)) {
                return true;
            }

            for (int o = i; o <= l; o++) {
                for (int p = k; p <= n; p++) {
                    if (level.getBlockState(mutableBlockPos.set(o, j, p)).liquid()) {
                        return true;
                    }

                    if (level.getBlockState(mutableBlockPos.set(o, m, p)).liquid()) {
                        return true;
                    }
                }
            }

            for (int o = i; o <= l; o++) {
                for (int p = j; p <= m; p++) {
                    if (level.getBlockState(mutableBlockPos.set(o, p, k)).liquid()) {
                        return true;
                    }

                    if (level.getBlockState(mutableBlockPos.set(o, p, n)).liquid()) {
                        return true;
                    }
                }
            }

            for (int o = k; o <= n; o++) {
                for (int p = j; p <= m; p++) {
                    if (level.getBlockState(mutableBlockPos.set(i, p, o)).liquid()) {
                        return true;
                    }

                    if (level.getBlockState(mutableBlockPos.set(l, p, o)).liquid()) {
                        return true;
                    }
                }
            }

            return false;
        }

        protected void setPlanksBlock(WorldGenLevel level, BoundingBox box, BlockState plankState, int x, int y, int z) {
            if (this.isInterior(level, x, y, z, box)) {
                BlockPos blockPos = this.getWorldPos(x, y, z);
                BlockState blockState = level.getBlockState(blockPos);
                if (!blockState.isFaceSturdy(level, blockPos, Direction.UP)) {
                    level.setBlock(blockPos, plankState, 2);
                }
            }
        }
    }

    public static class MineShaftRoom extends MineshaftPieces.MineShaftPiece {
        private final List<BoundingBox> childEntranceBoxes = Lists.newLinkedList();

        public MineShaftRoom(int genDepth, RandomSource random, int x, int z, MineshaftStructure.Type type) {
            super(
                StructurePieceType.MINE_SHAFT_ROOM,
                genDepth,
                type,
                new BoundingBox(x, 50, z, x + 7 + random.nextInt(6), 54 + random.nextInt(6), z + 7 + random.nextInt(6))
            );
            this.type = type;
        }

        public MineShaftRoom(CompoundTag tag) {
            super(StructurePieceType.MINE_SHAFT_ROOM, tag);
            BoundingBox.CODEC
                .listOf()
                .parse(NbtOps.INSTANCE, tag.getList("Entrances", 11))
                .resultOrPartial(MineshaftPieces.LOGGER::error)
                .ifPresent(this.childEntranceBoxes::addAll);
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            int i = this.getGenDepth();
            int j = this.boundingBox.getYSpan() - 3 - 1;
            if (j <= 0) {
                j = 1;
            }

            int k = 0;

            while (k < this.boundingBox.getXSpan()) {
                k += random.nextInt(this.boundingBox.getXSpan());
                if (k + 3 > this.boundingBox.getXSpan()) {
                    break;
                }

                MineshaftPieces.MineShaftPiece mineShaftPiece = MineshaftPieces.generateAndAddPiece(
                    piece,
                    pieces,
                    random,
                    this.boundingBox.minX() + k,
                    this.boundingBox.minY() + random.nextInt(j) + 1,
                    this.boundingBox.minZ() - 1,
                    Direction.NORTH,
                    i
                );
                if (mineShaftPiece != null) {
                    BoundingBox boundingBox = mineShaftPiece.getBoundingBox();
                    this.childEntranceBoxes
                        .add(
                            new BoundingBox(
                                boundingBox.minX(),
                                boundingBox.minY(),
                                this.boundingBox.minZ(),
                                boundingBox.maxX(),
                                boundingBox.maxY(),
                                this.boundingBox.minZ() + 1
                            )
                        );
                }

                k += 4;
            }

            k = 0;

            while (k < this.boundingBox.getXSpan()) {
                k += random.nextInt(this.boundingBox.getXSpan());
                if (k + 3 > this.boundingBox.getXSpan()) {
                    break;
                }

                MineshaftPieces.MineShaftPiece mineShaftPiece = MineshaftPieces.generateAndAddPiece(
                    piece,
                    pieces,
                    random,
                    this.boundingBox.minX() + k,
                    this.boundingBox.minY() + random.nextInt(j) + 1,
                    this.boundingBox.maxZ() + 1,
                    Direction.SOUTH,
                    i
                );
                if (mineShaftPiece != null) {
                    BoundingBox boundingBox = mineShaftPiece.getBoundingBox();
                    this.childEntranceBoxes
                        .add(
                            new BoundingBox(
                                boundingBox.minX(),
                                boundingBox.minY(),
                                this.boundingBox.maxZ() - 1,
                                boundingBox.maxX(),
                                boundingBox.maxY(),
                                this.boundingBox.maxZ()
                            )
                        );
                }

                k += 4;
            }

            k = 0;

            while (k < this.boundingBox.getZSpan()) {
                k += random.nextInt(this.boundingBox.getZSpan());
                if (k + 3 > this.boundingBox.getZSpan()) {
                    break;
                }

                MineshaftPieces.MineShaftPiece mineShaftPiece = MineshaftPieces.generateAndAddPiece(
                    piece,
                    pieces,
                    random,
                    this.boundingBox.minX() - 1,
                    this.boundingBox.minY() + random.nextInt(j) + 1,
                    this.boundingBox.minZ() + k,
                    Direction.WEST,
                    i
                );
                if (mineShaftPiece != null) {
                    BoundingBox boundingBox = mineShaftPiece.getBoundingBox();
                    this.childEntranceBoxes
                        .add(
                            new BoundingBox(
                                this.boundingBox.minX(),
                                boundingBox.minY(),
                                boundingBox.minZ(),
                                this.boundingBox.minX() + 1,
                                boundingBox.maxY(),
                                boundingBox.maxZ()
                            )
                        );
                }

                k += 4;
            }

            k = 0;

            while (k < this.boundingBox.getZSpan()) {
                k += random.nextInt(this.boundingBox.getZSpan());
                if (k + 3 > this.boundingBox.getZSpan()) {
                    break;
                }

                StructurePiece structurePiece = MineshaftPieces.generateAndAddPiece(
                    piece,
                    pieces,
                    random,
                    this.boundingBox.maxX() + 1,
                    this.boundingBox.minY() + random.nextInt(j) + 1,
                    this.boundingBox.minZ() + k,
                    Direction.EAST,
                    i
                );
                if (structurePiece != null) {
                    BoundingBox boundingBox = structurePiece.getBoundingBox();
                    this.childEntranceBoxes
                        .add(
                            new BoundingBox(
                                this.boundingBox.maxX() - 1,
                                boundingBox.minY(),
                                boundingBox.minZ(),
                                this.boundingBox.maxX(),
                                boundingBox.maxY(),
                                boundingBox.maxZ()
                            )
                        );
                }

                k += 4;
            }
        }

        @Override
        public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox box,
            ChunkPos chunkPos,
            BlockPos pos
        ) {
            if (!this.isInInvalidLocation(level, box)) {
                this.generateBox(
                    level,
                    box,
                    this.boundingBox.minX(),
                    this.boundingBox.minY() + 1,
                    this.boundingBox.minZ(),
                    this.boundingBox.maxX(),
                    Math.min(this.boundingBox.minY() + 3, this.boundingBox.maxY()),
                    this.boundingBox.maxZ(),
                    CAVE_AIR,
                    CAVE_AIR,
                    false
                );

                for (BoundingBox boundingBox : this.childEntranceBoxes) {
                    this.generateBox(
                        level,
                        box,
                        boundingBox.minX(),
                        boundingBox.maxY() - 2,
                        boundingBox.minZ(),
                        boundingBox.maxX(),
                        boundingBox.maxY(),
                        boundingBox.maxZ(),
                        CAVE_AIR,
                        CAVE_AIR,
                        false
                    );
                }

                this.generateUpperHalfSphere(
                    level,
                    box,
                    this.boundingBox.minX(),
                    this.boundingBox.minY() + 4,
                    this.boundingBox.minZ(),
                    this.boundingBox.maxX(),
                    this.boundingBox.maxY(),
                    this.boundingBox.maxZ(),
                    CAVE_AIR,
                    false
                );
            }
        }

        @Override
        public void move(int x, int y, int z) {
            super.move(x, y, z);

            for (BoundingBox boundingBox : this.childEntranceBoxes) {
                boundingBox.move(x, y, z);
            }
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            BoundingBox.CODEC
                .listOf()
                .encodeStart(NbtOps.INSTANCE, this.childEntranceBoxes)
                .resultOrPartial(MineshaftPieces.LOGGER::error)
                .ifPresent(tagx -> tag.put("Entrances", tagx));
        }
    }

    public static class MineShaftStairs extends MineshaftPieces.MineShaftPiece {
        public MineShaftStairs(int genDepth, BoundingBox boundingBox, Direction orientation, MineshaftStructure.Type type) {
            super(StructurePieceType.MINE_SHAFT_STAIRS, genDepth, type, boundingBox);
            this.setOrientation(orientation);
        }

        public MineShaftStairs(CompoundTag tag) {
            super(StructurePieceType.MINE_SHAFT_STAIRS, tag);
        }

        @Nullable
        public static BoundingBox findStairs(StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction direction) {
            BoundingBox boundingBox = switch (direction) {
                default -> new BoundingBox(0, -5, -8, 2, 2, 0);
                case SOUTH -> new BoundingBox(0, -5, 0, 2, 2, 8);
                case WEST -> new BoundingBox(-8, -5, 0, 0, 2, 2);
                case EAST -> new BoundingBox(0, -5, 0, 8, 2, 2);
            };
            boundingBox.move(x, y, z);
            return pieces.findCollisionPiece(boundingBox) != null ? null : boundingBox;
        }

        @Override
        public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
            int i = this.getGenDepth();
            Direction direction = this.getOrientation();
            if (direction != null) {
                switch (direction) {
                    case NORTH:
                    default:
                        MineshaftPieces.generateAndAddPiece(
                            piece, pieces, random, this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, i
                        );
                        break;
                    case SOUTH:
                        MineshaftPieces.generateAndAddPiece(
                            piece, pieces, random, this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, i
                        );
                        break;
                    case WEST:
                        MineshaftPieces.generateAndAddPiece(
                            piece, pieces, random, this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ(), Direction.WEST, i
                        );
                        break;
                    case EAST:
                        MineshaftPieces.generateAndAddPiece(
                            piece, pieces, random, this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ(), Direction.EAST, i
                        );
                }
            }
        }

        @Override
        public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox box,
            ChunkPos chunkPos,
            BlockPos pos
        ) {
            if (!this.isInInvalidLocation(level, box)) {
                this.generateBox(level, box, 0, 5, 0, 2, 7, 1, CAVE_AIR, CAVE_AIR, false);
                this.generateBox(level, box, 0, 0, 7, 2, 2, 8, CAVE_AIR, CAVE_AIR, false);

                for (int i = 0; i < 5; i++) {
                    this.generateBox(level, box, 0, 5 - i - (i < 4 ? 1 : 0), 2 + i, 2, 7 - i, 2 + i, CAVE_AIR, CAVE_AIR, false);
                }
            }
        }
    }
}
