package net.minecraft.world.level.levelgen.structure;

import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootTable;
import org.slf4j.Logger;

public abstract class StructurePiece {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();
    protected BoundingBox boundingBox;
    @Nullable
    private Direction orientation;
    private Mirror mirror;
    private Rotation rotation;
    protected int genDepth;
    private final StructurePieceType type;
    private static final Set<Block> SHAPE_CHECK_BLOCKS = ImmutableSet.<Block>builder()
        .add(Blocks.NETHER_BRICK_FENCE)
        .add(Blocks.TORCH)
        .add(Blocks.WALL_TORCH)
        .add(Blocks.OAK_FENCE)
        .add(Blocks.SPRUCE_FENCE)
        .add(Blocks.DARK_OAK_FENCE)
        .add(Blocks.ACACIA_FENCE)
        .add(Blocks.BIRCH_FENCE)
        .add(Blocks.JUNGLE_FENCE)
        .add(Blocks.LADDER)
        .add(Blocks.IRON_BARS)
        .build();

    protected StructurePiece(StructurePieceType type, int genDepth, BoundingBox boundingBox) {
        this.type = type;
        this.genDepth = genDepth;
        this.boundingBox = boundingBox;
    }

    public StructurePiece(StructurePieceType type, CompoundTag tag) {
        this(
            type,
            tag.getInt("GD"),
            BoundingBox.CODEC.parse(NbtOps.INSTANCE, tag.get("BB")).getOrThrow(string -> new IllegalArgumentException("Invalid boundingbox: " + string))
        );
        int i = tag.getInt("O");
        this.setOrientation(i == -1 ? null : Direction.from2DDataValue(i));
    }

    protected static BoundingBox makeBoundingBox(int x, int y, int z, Direction direction, int offsetX, int offsetY, int offsetZ) {
        return direction.getAxis() == Direction.Axis.Z
            ? new BoundingBox(x, y, z, x + offsetX - 1, y + offsetY - 1, z + offsetZ - 1)
            : new BoundingBox(x, y, z, x + offsetZ - 1, y + offsetY - 1, z + offsetX - 1);
    }

    protected static Direction getRandomHorizontalDirection(RandomSource random) {
        return Direction.Plane.HORIZONTAL.getRandomDirection(random);
    }

    public final CompoundTag createTag(StructurePieceSerializationContext context) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("id", BuiltInRegistries.STRUCTURE_PIECE.getKey(this.getType()).toString());
        BoundingBox.CODEC.encodeStart(NbtOps.INSTANCE, this.boundingBox).resultOrPartial(LOGGER::error).ifPresent(tag -> compoundTag.put("BB", tag));
        Direction direction = this.getOrientation();
        compoundTag.putInt("O", direction == null ? -1 : direction.get2DDataValue());
        compoundTag.putInt("GD", this.genDepth);
        this.addAdditionalSaveData(context, compoundTag);
        return compoundTag;
    }

    protected abstract void addAdditionalSaveData(StructurePieceSerializationContext var1, CompoundTag var2);

    public void addChildren(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random) {
    }

    public abstract void postProcess(
        WorldGenLevel var1, StructureManager var2, ChunkGenerator var3, RandomSource var4, BoundingBox var5, ChunkPos var6, BlockPos var7
    );

    public BoundingBox getBoundingBox() {
        return this.boundingBox;
    }

    public int getGenDepth() {
        return this.genDepth;
    }

    public void setGenDepth(int genDepth) {
        this.genDepth = genDepth;
    }

    public boolean isCloseToChunk(ChunkPos chunkPos, int distance) {
        int i = chunkPos.getMinBlockX();
        int j = chunkPos.getMinBlockZ();
        return this.boundingBox.intersects(i - distance, j - distance, i + 15 + distance, j + 15 + distance);
    }

    public BlockPos getLocatorPosition() {
        return new BlockPos(this.boundingBox.getCenter());
    }

    protected BlockPos.MutableBlockPos getWorldPos(int x, int y, int z) {
        return new BlockPos.MutableBlockPos(this.getWorldX(x, z), this.getWorldY(y), this.getWorldZ(x, z));
    }

    protected int getWorldX(int x, int z) {
        Direction direction = this.getOrientation();
        if (direction == null) {
            return x;
        }

        switch (direction) {
            case NORTH:
            case SOUTH:
                return this.boundingBox.minX() + x;
            case WEST:
                return this.boundingBox.maxX() - z;
            case EAST:
                return this.boundingBox.minX() + z;
            default:
                return x;
        }
    }

    protected int getWorldY(int y) {
        return this.getOrientation() == null ? y : y + this.boundingBox.minY();
    }

    protected int getWorldZ(int x, int z) {
        Direction direction = this.getOrientation();
        if (direction == null) {
            return z;
        }

        switch (direction) {
            case NORTH:
                return this.boundingBox.maxZ() - z;
            case SOUTH:
                return this.boundingBox.minZ() + z;
            case WEST:
            case EAST:
                return this.boundingBox.minZ() + x;
            default:
                return z;
        }
    }

    protected void placeBlock(WorldGenLevel level, BlockState blockstate, int x, int y, int z, BoundingBox boundingbox) {
        BlockPos blockPos = this.getWorldPos(x, y, z);
        if (boundingbox.isInside(blockPos)) {
            if (this.canBeReplaced(level, x, y, z, boundingbox)) {
                if (this.mirror != Mirror.NONE) {
                    blockstate = blockstate.mirror(this.mirror);
                }

                if (this.rotation != Rotation.NONE) {
                    blockstate = blockstate.rotate(this.rotation);
                }

                level.setBlock(blockPos, blockstate, 2);
                FluidState fluidState = level.getFluidState(blockPos);
                if (!fluidState.isEmpty()) {
                    level.scheduleTick(blockPos, fluidState.getType(), 0);
                }

                if (SHAPE_CHECK_BLOCKS.contains(blockstate.getBlock())) {
                    level.getChunk(blockPos).markPosForPostprocessing(blockPos);
                }
            }
        }
    }

    protected boolean canBeReplaced(LevelReader level, int x, int y, int z, BoundingBox box) {
        return true;
    }

    protected BlockState getBlock(BlockGetter level, int x, int y, int z, BoundingBox box) {
        BlockPos blockPos = this.getWorldPos(x, y, z);
        return !box.isInside(blockPos) ? Blocks.AIR.defaultBlockState() : level.getBlockState(blockPos);
    }

    protected boolean isInterior(LevelReader level, int x, int y, int z, BoundingBox box) {
        BlockPos blockPos = this.getWorldPos(x, y + 1, z);
        return !box.isInside(blockPos) ? false : blockPos.getY() < level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, blockPos.getX(), blockPos.getZ());
    }

    protected void generateAirBox(WorldGenLevel level, BoundingBox box, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int i = minY; i <= maxY; i++) {
            for (int j = minX; j <= maxX; j++) {
                for (int k = minZ; k <= maxZ; k++) {
                    this.placeBlock(level, Blocks.AIR.defaultBlockState(), j, i, k, box);
                }
            }
        }
    }

    /**
     * Fill the given area with the selected blocks
     */
    protected void generateBox(
        WorldGenLevel level,
        BoundingBox box,
        int xMin,
        int yMin,
        int zMin,
        int xMax,
        int yMax,
        int zMax,
        BlockState boundaryBlockState,
        BlockState insideBlockState,
        boolean existingOnly
    ) {
        for (int i = yMin; i <= yMax; i++) {
            for (int j = xMin; j <= xMax; j++) {
                for (int k = zMin; k <= zMax; k++) {
                    if (!existingOnly || !this.getBlock(level, j, i, k, box).isAir()) {
                        if (i != yMin && i != yMax && j != xMin && j != xMax && k != zMin && k != zMax) {
                            this.placeBlock(level, insideBlockState, j, i, k, box);
                        } else {
                            this.placeBlock(level, boundaryBlockState, j, i, k, box);
                        }
                    }
                }
            }
        }
    }

    protected void generateBox(
        WorldGenLevel level, BoundingBox boundingBox, BoundingBox box, BlockState boundaryBlockState, BlockState insideBlockState, boolean existingOnly
    ) {
        this.generateBox(
            level, boundingBox, box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), boundaryBlockState, insideBlockState, existingOnly
        );
    }

    protected void generateBox(
        WorldGenLevel level,
        BoundingBox box,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        boolean alwaysReplace,
        RandomSource random,
        StructurePiece.BlockSelector blockSelector
    ) {
        for (int i = minY; i <= maxY; i++) {
            for (int j = minX; j <= maxX; j++) {
                for (int k = minZ; k <= maxZ; k++) {
                    if (!alwaysReplace || !this.getBlock(level, j, i, k, box).isAir()) {
                        blockSelector.next(random, j, i, k, i == minY || i == maxY || j == minX || j == maxX || k == minZ || k == maxZ);
                        this.placeBlock(level, blockSelector.getNext(), j, i, k, box);
                    }
                }
            }
        }
    }

    protected void generateBox(
        WorldGenLevel level, BoundingBox boundingBox, BoundingBox box, boolean alwaysReplace, RandomSource random, StructurePiece.BlockSelector blockSelector
    ) {
        this.generateBox(level, boundingBox, box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), alwaysReplace, random, blockSelector);
    }

    protected void generateMaybeBox(
        WorldGenLevel level,
        BoundingBox box,
        RandomSource random,
        float chance,
        int x1,
        int y1,
        int z1,
        int x2,
        int y2,
        int z2,
        BlockState edgeState,
        BlockState state,
        boolean requireNonAir,
        boolean requireSkylight
    ) {
        for (int i = y1; i <= y2; i++) {
            for (int j = x1; j <= x2; j++) {
                for (int k = z1; k <= z2; k++) {
                    if (!(random.nextFloat() > chance)
                        && (!requireNonAir || !this.getBlock(level, j, i, k, box).isAir())
                        && (!requireSkylight || this.isInterior(level, j, i, k, box))) {
                        if (i != y1 && i != y2 && j != x1 && j != x2 && k != z1 && k != z2) {
                            this.placeBlock(level, state, j, i, k, box);
                        } else {
                            this.placeBlock(level, edgeState, j, i, k, box);
                        }
                    }
                }
            }
        }
    }

    protected void maybeGenerateBlock(WorldGenLevel level, BoundingBox box, RandomSource random, float chance, int x, int y, int z, BlockState state) {
        if (random.nextFloat() < chance) {
            this.placeBlock(level, state, x, y, z, box);
        }
    }

    protected void generateUpperHalfSphere(
        WorldGenLevel level, BoundingBox box, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState state, boolean excludeAir
    ) {
        float f = maxX - minX + 1;
        float g = maxY - minY + 1;
        float h = maxZ - minZ + 1;
        float i = minX + f / 2.0F;
        float j = minZ + h / 2.0F;

        for (int k = minY; k <= maxY; k++) {
            float l = (k - minY) / g;

            for (int m = minX; m <= maxX; m++) {
                float n = (m - i) / (f * 0.5F);

                for (int o = minZ; o <= maxZ; o++) {
                    float p = (o - j) / (h * 0.5F);
                    if (!excludeAir || !this.getBlock(level, m, k, o, box).isAir()) {
                        float q = n * n + l * l + p * p;
                        if (q <= 1.05F) {
                            this.placeBlock(level, state, m, k, o, box);
                        }
                    }
                }
            }
        }
    }

    protected void fillColumnDown(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox box) {
        BlockPos.MutableBlockPos mutableBlockPos = this.getWorldPos(x, y, z);
        if (box.isInside(mutableBlockPos)) {
            while (this.isReplaceableByStructures(level.getBlockState(mutableBlockPos)) && mutableBlockPos.getY() > level.getMinBuildHeight() + 1) {
                level.setBlock(mutableBlockPos, state, 2);
                mutableBlockPos.move(Direction.DOWN);
            }
        }
    }

    protected boolean isReplaceableByStructures(BlockState state) {
        return state.isAir() || state.liquid() || state.is(Blocks.GLOW_LICHEN) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
    }

    protected boolean createChest(WorldGenLevel level, BoundingBox box, RandomSource random, int x, int y, int z, ResourceKey<LootTable> lootTable) {
        return this.createChest(level, box, random, this.getWorldPos(x, y, z), lootTable, null);
    }

    public static BlockState reorient(BlockGetter level, BlockPos pos, BlockState state) {
        Direction direction = null;

        for (Direction direction2 : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = pos.relative(direction2);
            BlockState blockState = level.getBlockState(blockPos);
            if (blockState.is(Blocks.CHEST)) {
                return state;
            }

            if (blockState.isSolidRender(level, blockPos)) {
                if (direction != null) {
                    direction = null;
                    break;
                }

                direction = direction2;
            }
        }

        if (direction != null) {
            return state.setValue(HorizontalDirectionalBlock.FACING, direction.getOpposite());
        }

        Direction direction3 = state.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos blockPos2 = pos.relative(direction3);
        if (level.getBlockState(blockPos2).isSolidRender(level, blockPos2)) {
            direction3 = direction3.getOpposite();
            blockPos2 = pos.relative(direction3);
        }

        if (level.getBlockState(blockPos2).isSolidRender(level, blockPos2)) {
            direction3 = direction3.getClockWise();
            blockPos2 = pos.relative(direction3);
        }

        if (level.getBlockState(blockPos2).isSolidRender(level, blockPos2)) {
            direction3 = direction3.getOpposite();
            blockPos2 = pos.relative(direction3);
        }

        return state.setValue(HorizontalDirectionalBlock.FACING, direction3);
    }

    protected boolean createChest(
        ServerLevelAccessor level, BoundingBox box, RandomSource random, BlockPos pos, ResourceKey<LootTable> lootTable, @Nullable BlockState state
    ) {
        if (box.isInside(pos) && !level.getBlockState(pos).is(Blocks.CHEST)) {
            if (state == null) {
                state = reorient(level, pos, Blocks.CHEST.defaultBlockState());
            }

            level.setBlock(pos, state, 2);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ChestBlockEntity) {
                ((ChestBlockEntity)blockEntity).setLootTable(lootTable, random.nextLong());
            }

            return true;
        } else {
            return false;
        }
    }

    protected boolean createDispenser(
        WorldGenLevel level, BoundingBox box, RandomSource random, int x, int y, int z, Direction facing, ResourceKey<LootTable> lootTable
    ) {
        BlockPos blockPos = this.getWorldPos(x, y, z);
        if (box.isInside(blockPos) && !level.getBlockState(blockPos).is(Blocks.DISPENSER)) {
            this.placeBlock(level, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing), x, y, z, box);
            BlockEntity blockEntity = level.getBlockEntity(blockPos);
            if (blockEntity instanceof DispenserBlockEntity) {
                ((DispenserBlockEntity)blockEntity).setLootTable(lootTable, random.nextLong());
            }

            return true;
        } else {
            return false;
        }
    }

    public void move(int x, int y, int z) {
        this.boundingBox.move(x, y, z);
    }

    public static BoundingBox createBoundingBox(Stream<StructurePiece> pieces) {
        return BoundingBox.encapsulatingBoxes(pieces.map(StructurePiece::getBoundingBox)::iterator)
            .orElseThrow(() -> new IllegalStateException("Unable to calculate boundingbox without pieces"));
    }

    @Nullable
    public static StructurePiece findCollisionPiece(List<StructurePiece> pieces, BoundingBox boundingBox) {
        for (StructurePiece structurePiece : pieces) {
            if (structurePiece.getBoundingBox().intersects(boundingBox)) {
                return structurePiece;
            }
        }

        return null;
    }

    @Nullable
    public Direction getOrientation() {
        return this.orientation;
    }

    public void setOrientation(@Nullable Direction orientation) {
        this.orientation = orientation;
        if (orientation == null) {
            this.rotation = Rotation.NONE;
            this.mirror = Mirror.NONE;
        } else {
            switch (orientation) {
                case SOUTH:
                    this.mirror = Mirror.LEFT_RIGHT;
                    this.rotation = Rotation.NONE;
                    break;
                case WEST:
                    this.mirror = Mirror.LEFT_RIGHT;
                    this.rotation = Rotation.CLOCKWISE_90;
                    break;
                case EAST:
                    this.mirror = Mirror.NONE;
                    this.rotation = Rotation.CLOCKWISE_90;
                    break;
                default:
                    this.mirror = Mirror.NONE;
                    this.rotation = Rotation.NONE;
            }
        }
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public Mirror getMirror() {
        return this.mirror;
    }

    public StructurePieceType getType() {
        return this.type;
    }

    public abstract static class BlockSelector {
        protected BlockState next = Blocks.AIR.defaultBlockState();

        public abstract void next(RandomSource var1, int var2, int var3, int var4, boolean var5);

        public BlockState getNext() {
            return this.next;
        }
    }
}
