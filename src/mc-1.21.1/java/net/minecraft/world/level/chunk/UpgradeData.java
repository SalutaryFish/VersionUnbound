package net.minecraft.world.level.chunk;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.SavedTick;
import org.slf4j.Logger;

public class UpgradeData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final UpgradeData EMPTY = new UpgradeData(EmptyBlockGetter.INSTANCE);
    private static final String TAG_INDICES = "Indices";
    private static final Direction8[] DIRECTIONS = Direction8.values();
    private final EnumSet<Direction8> sides = EnumSet.noneOf(Direction8.class);
    private final List<SavedTick<Block>> neighborBlockTicks = Lists.newArrayList();
    private final List<SavedTick<Fluid>> neighborFluidTicks = Lists.newArrayList();
    private final int[][] index;
    static final Map<Block, UpgradeData.BlockFixer> MAP = new IdentityHashMap<>();
    static final Set<UpgradeData.BlockFixer> CHUNKY_FIXERS = Sets.newHashSet();

    private UpgradeData(LevelHeightAccessor level) {
        this.index = new int[level.getSectionsCount()][];
    }

    public UpgradeData(CompoundTag tag, LevelHeightAccessor level) {
        this(level);
        if (tag.contains("Indices", 10)) {
            CompoundTag compoundTag = tag.getCompound("Indices");

            for (int i = 0; i < this.index.length; i++) {
                String string = String.valueOf(i);
                if (compoundTag.contains(string, 11)) {
                    this.index[i] = compoundTag.getIntArray(string);
                }
            }
        }

        int j = tag.getInt("Sides");

        for (Direction8 direction8 : Direction8.values()) {
            if ((j & 1 << direction8.ordinal()) != 0) {
                this.sides.add(direction8);
            }
        }

        loadTicks(
            tag,
            "neighbor_block_ticks",
            id -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(id)).or(() -> Optional.of(Blocks.AIR)),
            this.neighborBlockTicks
        );
        loadTicks(
            tag,
            "neighbor_fluid_ticks",
            id -> BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(id)).or(() -> Optional.of(Fluids.EMPTY)),
            this.neighborFluidTicks
        );
    }

    private static <T> void loadTicks(CompoundTag tag, String identifier, Function<String, Optional<T>> valueFunction, List<SavedTick<T>> ticks) {
        if (tag.contains(identifier, 9)) {
            for (Tag tag2 : tag.getList(identifier, 10)) {
                SavedTick.loadTick((CompoundTag)tag2, valueFunction).ifPresent(ticks::add);
            }
        }
    }

    public void upgrade(LevelChunk chunk) {
        this.upgradeInside(chunk);

        for (Direction8 direction8 : DIRECTIONS) {
            upgradeSides(chunk, direction8);
        }

        Level level = chunk.getLevel();
        this.neighborBlockTicks.forEach(blockTicker -> {
            Block block = blockTicker.type() == Blocks.AIR ? level.getBlockState(blockTicker.pos()).getBlock() : blockTicker.type();
            level.scheduleTick(blockTicker.pos(), block, blockTicker.delay(), blockTicker.priority());
        });
        this.neighborFluidTicks.forEach(fluidTicker -> {
            Fluid fluid = fluidTicker.type() == Fluids.EMPTY ? level.getFluidState(fluidTicker.pos()).getType() : fluidTicker.type();
            level.scheduleTick(fluidTicker.pos(), fluid, fluidTicker.delay(), fluidTicker.priority());
        });
        CHUNKY_FIXERS.forEach(fixers -> fixers.processChunk(level));
    }

    private static void upgradeSides(LevelChunk chunk, Direction8 side) {
        Level level = chunk.getLevel();
        if (chunk.getUpgradeData().sides.remove(side)) {
            Set<Direction> set = side.getDirections();
            int i = 0;
            int j = 15;
            boolean bl = set.contains(Direction.EAST);
            boolean bl2 = set.contains(Direction.WEST);
            boolean bl3 = set.contains(Direction.SOUTH);
            boolean bl4 = set.contains(Direction.NORTH);
            boolean bl5 = set.size() == 1;
            ChunkPos chunkPos = chunk.getPos();
            int k = chunkPos.getMinBlockX() + (!bl5 || !bl4 && !bl3 ? (bl2 ? 0 : 15) : 1);
            int l = chunkPos.getMinBlockX() + (!bl5 || !bl4 && !bl3 ? (bl2 ? 0 : 15) : 14);
            int m = chunkPos.getMinBlockZ() + (!bl5 || !bl && !bl2 ? (bl4 ? 0 : 15) : 1);
            int n = chunkPos.getMinBlockZ() + (!bl5 || !bl && !bl2 ? (bl4 ? 0 : 15) : 14);
            Direction[] directions = Direction.values();
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (BlockPos blockPos : BlockPos.betweenClosed(k, level.getMinBuildHeight(), m, l, level.getMaxBuildHeight() - 1, n)) {
                BlockState blockState = level.getBlockState(blockPos);
                BlockState blockState2 = blockState;

                for (Direction direction : directions) {
                    mutableBlockPos.setWithOffset(blockPos, direction);
                    blockState2 = updateState(blockState2, direction, level, blockPos, mutableBlockPos);
                }

                Block.updateOrDestroy(blockState, blockState2, level, blockPos, 18);
            }
        }
    }

    private static BlockState updateState(BlockState state, Direction direction, LevelAccessor level, BlockPos pos, BlockPos offsetPos) {
        return MAP.getOrDefault(state.getBlock(), UpgradeData.BlockFixers.DEFAULT)
            .updateShape(state, direction, level.getBlockState(offsetPos), level, pos, offsetPos);
    }

    private void upgradeInside(LevelChunk chunk) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos mutableBlockPos2 = new BlockPos.MutableBlockPos();
        ChunkPos chunkPos = chunk.getPos();
        LevelAccessor levelAccessor = chunk.getLevel();

        for (int i = 0; i < this.index.length; i++) {
            LevelChunkSection levelChunkSection = chunk.getSection(i);
            int[] is = this.index[i];
            this.index[i] = null;
            if (is != null && is.length > 0) {
                Direction[] directions = Direction.values();
                PalettedContainer<BlockState> palettedContainer = levelChunkSection.getStates();
                int j = chunk.getSectionYFromSectionIndex(i);
                int k = SectionPos.sectionToBlockCoord(j);

                for (int l : is) {
                    int m = l & 15;
                    int n = l >> 8 & 15;
                    int o = l >> 4 & 15;
                    mutableBlockPos.set(chunkPos.getMinBlockX() + m, k + n, chunkPos.getMinBlockZ() + o);
                    BlockState blockState = palettedContainer.get(l);
                    BlockState blockState2 = blockState;

                    for (Direction direction : directions) {
                        mutableBlockPos2.setWithOffset(mutableBlockPos, direction);
                        if (SectionPos.blockToSectionCoord(mutableBlockPos.getX()) == chunkPos.x
                            && SectionPos.blockToSectionCoord(mutableBlockPos.getZ()) == chunkPos.z) {
                            blockState2 = updateState(blockState2, direction, levelAccessor, mutableBlockPos, mutableBlockPos2);
                        }
                    }

                    Block.updateOrDestroy(blockState, blockState2, levelAccessor, mutableBlockPos, 18);
                }
            }
        }

        for (int i = 0; i < this.index.length; i++) {
            if (this.index[i] != null) {
                LOGGER.warn("Discarding update data for section {} for chunk ({} {})", levelAccessor.getSectionYFromSectionIndex(i), chunkPos.x, chunkPos.z);
            }

            this.index[i] = null;
        }
    }

    public boolean isEmpty() {
        for (int[] is : this.index) {
            if (is != null) {
                return false;
            }
        }

        return this.sides.isEmpty();
    }

    public CompoundTag write() {
        CompoundTag compoundTag = new CompoundTag();
        CompoundTag compoundTag2 = new CompoundTag();

        for (int i = 0; i < this.index.length; i++) {
            String string = String.valueOf(i);
            if (this.index[i] != null && this.index[i].length != 0) {
                compoundTag2.putIntArray(string, this.index[i]);
            }
        }

        if (!compoundTag2.isEmpty()) {
            compoundTag.put("Indices", compoundTag2);
        }

        int i = 0;

        for (Direction8 direction8 : this.sides) {
            i |= 1 << direction8.ordinal();
        }

        compoundTag.putByte("Sides", (byte)i);
        if (!this.neighborBlockTicks.isEmpty()) {
            ListTag listTag = new ListTag();
            this.neighborBlockTicks.forEach(blockTicker -> listTag.add(blockTicker.save(block -> BuiltInRegistries.BLOCK.getKey(block).toString())));
            compoundTag.put("neighbor_block_ticks", listTag);
        }

        if (!this.neighborFluidTicks.isEmpty()) {
            ListTag listTag = new ListTag();
            this.neighborFluidTicks.forEach(fluidTicker -> listTag.add(fluidTicker.save(fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString())));
            compoundTag.put("neighbor_fluid_ticks", listTag);
        }

        return compoundTag;
    }

    public interface BlockFixer {
        BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6);

        default void processChunk(LevelAccessor level) {
        }
    }

    enum BlockFixers implements UpgradeData.BlockFixer {
        BLACKLIST(
            Blocks.OBSERVER,
            Blocks.NETHER_PORTAL,
            Blocks.WHITE_CONCRETE_POWDER,
            Blocks.ORANGE_CONCRETE_POWDER,
            Blocks.MAGENTA_CONCRETE_POWDER,
            Blocks.LIGHT_BLUE_CONCRETE_POWDER,
            Blocks.YELLOW_CONCRETE_POWDER,
            Blocks.LIME_CONCRETE_POWDER,
            Blocks.PINK_CONCRETE_POWDER,
            Blocks.GRAY_CONCRETE_POWDER,
            Blocks.LIGHT_GRAY_CONCRETE_POWDER,
            Blocks.CYAN_CONCRETE_POWDER,
            Blocks.PURPLE_CONCRETE_POWDER,
            Blocks.BLUE_CONCRETE_POWDER,
            Blocks.BROWN_CONCRETE_POWDER,
            Blocks.GREEN_CONCRETE_POWDER,
            Blocks.RED_CONCRETE_POWDER,
            Blocks.BLACK_CONCRETE_POWDER,
            Blocks.ANVIL,
            Blocks.CHIPPED_ANVIL,
            Blocks.DAMAGED_ANVIL,
            Blocks.DRAGON_EGG,
            Blocks.GRAVEL,
            Blocks.SAND,
            Blocks.RED_SAND,
            Blocks.OAK_SIGN,
            Blocks.SPRUCE_SIGN,
            Blocks.BIRCH_SIGN,
            Blocks.ACACIA_SIGN,
            Blocks.CHERRY_SIGN,
            Blocks.JUNGLE_SIGN,
            Blocks.DARK_OAK_SIGN,
            Blocks.OAK_WALL_SIGN,
            Blocks.SPRUCE_WALL_SIGN,
            Blocks.BIRCH_WALL_SIGN,
            Blocks.ACACIA_WALL_SIGN,
            Blocks.JUNGLE_WALL_SIGN,
            Blocks.DARK_OAK_WALL_SIGN,
            Blocks.OAK_HANGING_SIGN,
            Blocks.SPRUCE_HANGING_SIGN,
            Blocks.BIRCH_HANGING_SIGN,
            Blocks.ACACIA_HANGING_SIGN,
            Blocks.JUNGLE_HANGING_SIGN,
            Blocks.DARK_OAK_HANGING_SIGN,
            Blocks.OAK_WALL_HANGING_SIGN,
            Blocks.SPRUCE_WALL_HANGING_SIGN,
            Blocks.BIRCH_WALL_HANGING_SIGN,
            Blocks.ACACIA_WALL_HANGING_SIGN,
            Blocks.JUNGLE_WALL_HANGING_SIGN,
            Blocks.DARK_OAK_WALL_HANGING_SIGN
        ) {
            @Override
            public BlockState updateShape(
                BlockState blockState, Direction direction, BlockState blockState2, LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2
            ) {
                return blockState;
            }
        },
        DEFAULT {
            @Override
            public BlockState updateShape(
                BlockState blockState, Direction direction, BlockState blockState2, LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2
            ) {
                return blockState.updateShape(direction, levelAccessor.getBlockState(blockPos2), levelAccessor, blockPos, blockPos2);
            }
        },
        CHEST(Blocks.CHEST, Blocks.TRAPPED_CHEST) {
            @Override
            public BlockState updateShape(
                BlockState blockState, Direction direction, BlockState blockState2, LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2
            ) {
                if (blockState2.is(blockState.getBlock())
                    && direction.getAxis().isHorizontal()
                    && blockState.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                    && blockState2.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
                    Direction direction2 = blockState.getValue(ChestBlock.FACING);
                    if (direction.getAxis() != direction2.getAxis() && direction2 == blockState2.getValue(ChestBlock.FACING)) {
                        ChestType chestType = direction == direction2.getClockWise() ? ChestType.LEFT : ChestType.RIGHT;
                        levelAccessor.setBlock(blockPos2, blockState2.setValue(ChestBlock.TYPE, chestType.getOpposite()), 18);
                        if (direction2 == Direction.NORTH || direction2 == Direction.EAST) {
                            BlockEntity blockEntity = levelAccessor.getBlockEntity(blockPos);
                            BlockEntity blockEntity2 = levelAccessor.getBlockEntity(blockPos2);
                            if (blockEntity instanceof ChestBlockEntity && blockEntity2 instanceof ChestBlockEntity) {
                                ChestBlockEntity.swapContents((ChestBlockEntity)blockEntity, (ChestBlockEntity)blockEntity2);
                            }
                        }

                        return blockState.setValue(ChestBlock.TYPE, chestType);
                    }
                }

                return blockState;
            }
        },
        LEAVES(
            true,
            Blocks.ACACIA_LEAVES,
            Blocks.CHERRY_LEAVES,
            Blocks.BIRCH_LEAVES,
            Blocks.DARK_OAK_LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.OAK_LEAVES,
            Blocks.SPRUCE_LEAVES
        ) {
            private final ThreadLocal<List<ObjectSet<BlockPos>>> queue = ThreadLocal.withInitial(() -> Lists.newArrayListWithCapacity(7));

            @Override
            public BlockState updateShape(
                BlockState blockState, Direction direction, BlockState blockState2, LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2
            ) {
                BlockState blockState3 = blockState.updateShape(direction, levelAccessor.getBlockState(blockPos2), levelAccessor, blockPos, blockPos2);
                if (blockState != blockState3) {
                    int i = blockState3.getValue(BlockStateProperties.DISTANCE);
                    List<ObjectSet<BlockPos>> list = this.queue.get();
                    if (list.isEmpty()) {
                        for (int j = 0; j < 7; j++) {
                            list.add(new ObjectOpenHashSet<>());
                        }
                    }

                    list.get(i).add(blockPos.immutable());
                }

                return blockState;
            }

            @Override
            public void processChunk(LevelAccessor levelAccessor) {
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
                List<ObjectSet<BlockPos>> list = this.queue.get();

                for (int i = 2; i < list.size(); i++) {
                    int j = i - 1;
                    ObjectSet<BlockPos> objectSet = list.get(j);
                    ObjectSet<BlockPos> objectSet2 = list.get(i);

                    for (BlockPos blockPos : objectSet) {
                        BlockState blockState = levelAccessor.getBlockState(blockPos);
                        if (blockState.getValue(BlockStateProperties.DISTANCE) >= j) {
                            levelAccessor.setBlock(blockPos, blockState.setValue(BlockStateProperties.DISTANCE, j), 18);
                            if (i != 7) {
                                for (Direction direction : DIRECTIONS) {
                                    mutableBlockPos.setWithOffset(blockPos, direction);
                                    BlockState blockState2 = levelAccessor.getBlockState(mutableBlockPos);
                                    if (blockState2.hasProperty(BlockStateProperties.DISTANCE) && blockState.getValue(BlockStateProperties.DISTANCE) > i) {
                                        objectSet2.add(mutableBlockPos.immutable());
                                    }
                                }
                            }
                        }
                    }
                }

                list.clear();
            }
        },
        STEM_BLOCK(Blocks.MELON_STEM, Blocks.PUMPKIN_STEM) {
            @Override
            public BlockState updateShape(
                BlockState blockState, Direction direction, BlockState blockState2, LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2
            ) {
                if (blockState.getValue(StemBlock.AGE) == 7) {
                    Block block = blockState.is(Blocks.PUMPKIN_STEM) ? Blocks.PUMPKIN : Blocks.MELON;
                    if (blockState2.is(block)) {
                        return (blockState.is(Blocks.PUMPKIN_STEM) ? Blocks.ATTACHED_PUMPKIN_STEM : Blocks.ATTACHED_MELON_STEM)
                            .defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, direction);
                    }
                }

                return blockState;
            }
        };

        public static final Direction[] DIRECTIONS = Direction.values();

        BlockFixers(final Block... blocks) {
            this(false, blocks);
        }

        BlockFixers(final boolean chunkyFixer, final Block... blocks) {
            for (Block block : blocks) {
                UpgradeData.MAP.put(block, this);
            }

            if (chunkyFixer) {
                UpgradeData.CHUNKY_FIXERS.add(this);
            }
        }
    }
}
