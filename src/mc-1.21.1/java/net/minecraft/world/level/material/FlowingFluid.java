package net.minecraft.world.level.material;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class FlowingFluid extends Fluid {
    public static final BooleanProperty FALLING = BlockStateProperties.FALLING;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_FLOWING;
    private static final int CACHE_SIZE = 200;
    private static final ThreadLocal<Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>> OCCLUSION_CACHE = ThreadLocal.withInitial(() -> {
        Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> object2ByteLinkedOpenHashMap = new Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>(200) {
            @Override
            protected void rehash(int newSize) {
            }
        };
        object2ByteLinkedOpenHashMap.defaultReturnValue((byte)127);
        return object2ByteLinkedOpenHashMap;
    });
    private final Map<FluidState, VoxelShape> shapes = Maps.newIdentityHashMap();

    @Override
    protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
        builder.add(FALLING);
    }

    @Override
    public Vec3 getFlow(BlockGetter blockReader, BlockPos pos, FluidState fluidState) {
        double d = 0.0;
        double e = 0.0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            mutableBlockPos.setWithOffset(pos, direction);
            FluidState fluidState2 = blockReader.getFluidState(mutableBlockPos);
            if (this.affectsFlow(fluidState2)) {
                float f = fluidState2.getOwnHeight();
                float g = 0.0F;
                if (f == 0.0F) {
                    if (!blockReader.getBlockState(mutableBlockPos).blocksMotion()) {
                        BlockPos blockPos = mutableBlockPos.below();
                        FluidState fluidState3 = blockReader.getFluidState(blockPos);
                        if (this.affectsFlow(fluidState3)) {
                            f = fluidState3.getOwnHeight();
                            if (f > 0.0F) {
                                g = fluidState.getOwnHeight() - (f - 0.8888889F);
                            }
                        }
                    }
                } else if (f > 0.0F) {
                    g = fluidState.getOwnHeight() - f;
                }

                if (g != 0.0F) {
                    d += direction.getStepX() * g;
                    e += direction.getStepZ() * g;
                }
            }
        }

        Vec3 vec3 = new Vec3(d, 0.0, e);
        if (fluidState.getValue(FALLING)) {
            for (Direction direction2 : Direction.Plane.HORIZONTAL) {
                mutableBlockPos.setWithOffset(pos, direction2);
                if (this.isSolidFace(blockReader, mutableBlockPos, direction2) || this.isSolidFace(blockReader, mutableBlockPos.above(), direction2)) {
                    vec3 = vec3.normalize().add(0.0, -6.0, 0.0);
                    break;
                }
            }
        }

        return vec3.normalize();
    }

    private boolean affectsFlow(FluidState state) {
        return state.isEmpty() || state.getType().isSame(this);
    }

    protected boolean isSolidFace(BlockGetter level, BlockPos neighborPos, Direction side) {
        BlockState blockState = level.getBlockState(neighborPos);
        FluidState fluidState = level.getFluidState(neighborPos);
        if (fluidState.getType().isSame(this)) {
            return false;
        } else if (side == Direction.UP) {
            return true;
        } else {
            return blockState.getBlock() instanceof IceBlock ? false : blockState.isFaceSturdy(level, neighborPos, side);
        }
    }

    protected void spread(Level level, BlockPos pos, FluidState state) {
        if (!state.isEmpty()) {
            BlockState blockState = level.getBlockState(pos);
            BlockPos blockPos = pos.below();
            BlockState blockState2 = level.getBlockState(blockPos);
            FluidState fluidState = this.getNewLiquid(level, blockPos, blockState2);
            if (this.canSpreadTo(level, pos, blockState, Direction.DOWN, blockPos, blockState2, level.getFluidState(blockPos), fluidState.getType())) {
                this.spreadTo(level, blockPos, blockState2, Direction.DOWN, fluidState);
                if (this.sourceNeighborCount(level, pos) >= 3) {
                    this.spreadToSides(level, pos, state, blockState);
                }
            } else if (state.isSource() || !this.isWaterHole(level, fluidState.getType(), pos, blockState, blockPos, blockState2)) {
                this.spreadToSides(level, pos, state, blockState);
            }
        }
    }

    private void spreadToSides(Level level, BlockPos pos, FluidState fluidState, BlockState blockState) {
        int i = fluidState.getAmount() - this.getDropOff(level);
        if (fluidState.getValue(FALLING)) {
            i = 7;
        }

        if (i > 0) {
            Map<Direction, FluidState> map = this.getSpread(level, pos, blockState);

            for (Entry<Direction, FluidState> entry : map.entrySet()) {
                Direction direction = entry.getKey();
                FluidState fluidState2 = entry.getValue();
                BlockPos blockPos = pos.relative(direction);
                BlockState blockState2 = level.getBlockState(blockPos);
                if (this.canSpreadTo(level, pos, blockState, direction, blockPos, blockState2, level.getFluidState(blockPos), fluidState2.getType())) {
                    this.spreadTo(level, blockPos, blockState2, direction, fluidState2);
                }
            }
        }
    }

    protected FluidState getNewLiquid(Level level, BlockPos pos, BlockState blockState) {
        int i = 0;
        int j = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = pos.relative(direction);
            BlockState blockState2 = level.getBlockState(blockPos);
            FluidState fluidState = blockState2.getFluidState();
            if (fluidState.getType().isSame(this) && this.canPassThroughWall(direction, level, pos, blockState, blockPos, blockState2)) {
                if (fluidState.isSource()) {
                    j++;
                }

                i = Math.max(i, fluidState.getAmount());
            }
        }

        if (this.canConvertToSource(level) && j >= 2) {
            BlockState blockState3 = level.getBlockState(pos.below());
            FluidState fluidState2 = blockState3.getFluidState();
            if (blockState3.isSolid() || this.isSourceBlockOfThisType(fluidState2)) {
                return this.getSource(false);
            }
        }

        BlockPos blockPos2 = pos.above();
        BlockState blockState4 = level.getBlockState(blockPos2);
        FluidState fluidState3 = blockState4.getFluidState();
        if (!fluidState3.isEmpty()
            && fluidState3.getType().isSame(this)
            && this.canPassThroughWall(Direction.UP, level, pos, blockState, blockPos2, blockState4)) {
            return this.getFlowing(8, true);
        }

        int k = i - this.getDropOff(level);
        return k <= 0 ? Fluids.EMPTY.defaultFluidState() : this.getFlowing(k, false);
    }

    private boolean canPassThroughWall(Direction direction, BlockGetter level, BlockPos pos, BlockState state, BlockPos spreadPos, BlockState spreadState) {
        Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> object2ByteLinkedOpenHashMap;
        if (!state.getBlock().hasDynamicShape() && !spreadState.getBlock().hasDynamicShape()) {
            object2ByteLinkedOpenHashMap = OCCLUSION_CACHE.get();
        } else {
            object2ByteLinkedOpenHashMap = null;
        }

        Block.BlockStatePairKey blockStatePairKey;
        if (object2ByteLinkedOpenHashMap != null) {
            blockStatePairKey = new Block.BlockStatePairKey(state, spreadState, direction);
            byte b = object2ByteLinkedOpenHashMap.getAndMoveToFirst(blockStatePairKey);
            if (b != 127) {
                return b != 0;
            }
        } else {
            blockStatePairKey = null;
        }

        VoxelShape voxelShape = state.getCollisionShape(level, pos);
        VoxelShape voxelShape2 = spreadState.getCollisionShape(level, spreadPos);
        boolean bl = !Shapes.mergedFaceOccludes(voxelShape, voxelShape2, direction);
        if (object2ByteLinkedOpenHashMap != null) {
            if (object2ByteLinkedOpenHashMap.size() == 200) {
                object2ByteLinkedOpenHashMap.removeLastByte();
            }

            object2ByteLinkedOpenHashMap.putAndMoveToFirst(blockStatePairKey, (byte)(bl ? 1 : 0));
        }

        return bl;
    }

    public abstract Fluid getFlowing();

    public FluidState getFlowing(int level, boolean falling) {
        return this.getFlowing().defaultFluidState().setValue(LEVEL, level).setValue(FALLING, falling);
    }

    public abstract Fluid getSource();

    public FluidState getSource(boolean falling) {
        return this.getSource().defaultFluidState().setValue(FALLING, falling);
    }

    protected abstract boolean canConvertToSource(Level var1);

    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState blockState, Direction direction, FluidState fluidState) {
        if (blockState.getBlock() instanceof LiquidBlockContainer) {
            ((LiquidBlockContainer)blockState.getBlock()).placeLiquid(level, pos, blockState, fluidState);
        } else {
            if (!blockState.isAir()) {
                this.beforeDestroyingBlock(level, pos, blockState);
            }

            level.setBlock(pos, fluidState.createLegacyBlock(), 3);
        }
    }

    protected abstract void beforeDestroyingBlock(LevelAccessor var1, BlockPos var2, BlockState var3);

    private static short getCacheKey(BlockPos sourcePos, BlockPos spreadPos) {
        int i = spreadPos.getX() - sourcePos.getX();
        int j = spreadPos.getZ() - sourcePos.getZ();
        return (short)((i + 128 & 0xFF) << 8 | j + 128 & 0xFF);
    }

    protected int getSlopeDistance(
        LevelReader level,
        BlockPos spreadPos,
        int distance,
        Direction direction,
        BlockState currentSpreadState,
        BlockPos sourcePos,
        Short2ObjectMap<Pair<BlockState, FluidState>> stateCache,
        Short2BooleanMap waterHoleCache
    ) {
        int i = 1000;

        for (Direction direction2 : Direction.Plane.HORIZONTAL) {
            if (direction2 != direction) {
                BlockPos blockPos = spreadPos.relative(direction2);
                short s = getCacheKey(sourcePos, blockPos);
                Pair<BlockState, FluidState> pair = stateCache.computeIfAbsent(s, key -> {
                    BlockState blockStatex = level.getBlockState(blockPos);
                    return Pair.of(blockStatex, blockStatex.getFluidState());
                });
                BlockState blockState = pair.getFirst();
                FluidState fluidState = pair.getSecond();
                if (this.canPassThrough(level, this.getFlowing(), spreadPos, currentSpreadState, direction2, blockPos, blockState, fluidState)) {
                    boolean bl = waterHoleCache.computeIfAbsent(s, key -> {
                        BlockPos blockPos2 = blockPos.below();
                        BlockState blockState2 = level.getBlockState(blockPos2);
                        return this.isWaterHole(level, this.getFlowing(), blockPos, blockState, blockPos2, blockState2);
                    });
                    if (bl) {
                        return distance;
                    }

                    if (distance < this.getSlopeFindDistance(level)) {
                        int j = this.getSlopeDistance(
                            level, blockPos, distance + 1, direction2.getOpposite(), blockState, sourcePos, stateCache, waterHoleCache
                        );
                        if (j < i) {
                            i = j;
                        }
                    }
                }
            }
        }

        return i;
    }

    private boolean isWaterHole(BlockGetter level, Fluid fluid, BlockPos pos, BlockState state, BlockPos spreadPos, BlockState spreadState) {
        if (!this.canPassThroughWall(Direction.DOWN, level, pos, state, spreadPos, spreadState)) {
            return false;
        } else {
            return spreadState.getFluidState().getType().isSame(this) ? true : this.canHoldFluid(level, spreadPos, spreadState, fluid);
        }
    }

    private boolean canPassThrough(
        BlockGetter level, Fluid fluid, BlockPos pos, BlockState state, Direction direction, BlockPos spreadPos, BlockState spreadState, FluidState fluidState
    ) {
        return !this.isSourceBlockOfThisType(fluidState)
            && this.canPassThroughWall(direction, level, pos, state, spreadPos, spreadState)
            && this.canHoldFluid(level, spreadPos, spreadState, fluid);
    }

    private boolean isSourceBlockOfThisType(FluidState state) {
        return state.getType().isSame(this) && state.isSource();
    }

    protected abstract int getSlopeFindDistance(LevelReader var1);

    /**
     * Returns the number of immediately adjacent source blocks of the same fluid that lie on the horizontal plane.
     */
    private int sourceNeighborCount(LevelReader level, BlockPos pos) {
        int i = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = pos.relative(direction);
            FluidState fluidState = level.getFluidState(blockPos);
            if (this.isSourceBlockOfThisType(fluidState)) {
                i++;
            }
        }

        return i;
    }

    protected Map<Direction, FluidState> getSpread(Level level, BlockPos pos, BlockState state) {
        int i = 1000;
        Map<Direction, FluidState> map = Maps.newEnumMap(Direction.class);
        Short2ObjectMap<Pair<BlockState, FluidState>> short2ObjectMap = new Short2ObjectOpenHashMap<>();
        Short2BooleanMap short2BooleanMap = new Short2BooleanOpenHashMap();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = pos.relative(direction);
            short s = getCacheKey(pos, blockPos);
            Pair<BlockState, FluidState> pair = short2ObjectMap.computeIfAbsent(s, key -> {
                BlockState blockStatex = level.getBlockState(blockPos);
                return Pair.of(blockStatex, blockStatex.getFluidState());
            });
            BlockState blockState = pair.getFirst();
            FluidState fluidState = pair.getSecond();
            FluidState fluidState2 = this.getNewLiquid(level, blockPos, blockState);
            if (this.canPassThrough(level, fluidState2.getType(), pos, state, direction, blockPos, blockState, fluidState)) {
                BlockPos blockPos2 = blockPos.below();
                boolean bl = short2BooleanMap.computeIfAbsent(s, key -> {
                    BlockState blockState2 = level.getBlockState(blockPos2);
                    return this.isWaterHole(level, this.getFlowing(), blockPos, blockState, blockPos2, blockState2);
                });
                int j;
                if (bl) {
                    j = 0;
                } else {
                    j = this.getSlopeDistance(level, blockPos, 1, direction.getOpposite(), blockState, pos, short2ObjectMap, short2BooleanMap);
                }

                if (j < i) {
                    map.clear();
                }

                if (j <= i) {
                    map.put(direction, fluidState2);
                    i = j;
                }
            }
        }

        return map;
    }

    private boolean canHoldFluid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        Block block = state.getBlock();
        if (block instanceof LiquidBlockContainer liquidBlockContainer) {
            return liquidBlockContainer.canPlaceLiquid(null, level, pos, state, fluid);
        } else if (block instanceof DoorBlock
            || state.is(BlockTags.SIGNS)
            || state.is(Blocks.LADDER)
            || state.is(Blocks.SUGAR_CANE)
            || state.is(Blocks.BUBBLE_COLUMN)) {
            return false;
        } else {
            return !state.is(Blocks.NETHER_PORTAL) && !state.is(Blocks.END_PORTAL) && !state.is(Blocks.END_GATEWAY) && !state.is(Blocks.STRUCTURE_VOID)
                ? !state.blocksMotion()
                : false;
        }
    }

    protected boolean canSpreadTo(
        BlockGetter level,
        BlockPos fromPos,
        BlockState fromBlockState,
        Direction direction,
        BlockPos toPos,
        BlockState toBlockState,
        FluidState toFluidState,
        Fluid fluid
    ) {
        return toFluidState.canBeReplacedWith(level, toPos, fluid, direction)
            && this.canPassThroughWall(direction, level, fromPos, fromBlockState, toPos, toBlockState)
            && this.canHoldFluid(level, toPos, toBlockState, fluid);
    }

    protected abstract int getDropOff(LevelReader var1);

    protected int getSpreadDelay(Level level, BlockPos pos, FluidState currentState, FluidState newState) {
        return this.getTickDelay(level);
    }

    @Override
    public void tick(Level level, BlockPos pos, FluidState state) {
        if (!state.isSource()) {
            FluidState fluidState = this.getNewLiquid(level, pos, level.getBlockState(pos));
            int i = this.getSpreadDelay(level, pos, state, fluidState);
            if (fluidState.isEmpty()) {
                state = fluidState;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            } else if (!fluidState.equals(state)) {
                state = fluidState;
                BlockState blockState = state.createLegacyBlock();
                level.setBlock(pos, blockState, 2);
                level.scheduleTick(pos, state.getType(), i);
                level.updateNeighborsAt(pos, blockState.getBlock());
            }
        }

        this.spread(level, pos, state);
    }

    protected static int getLegacyLevel(FluidState state) {
        return state.isSource() ? 0 : 8 - Math.min(state.getAmount(), 8) + (state.getValue(FALLING) ? 8 : 0);
    }

    private static boolean hasSameAbove(FluidState fluidState, BlockGetter level, BlockPos pos) {
        return fluidState.getType().isSame(level.getFluidState(pos.above()).getType());
    }

    @Override
    public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
        return hasSameAbove(state, level, pos) ? 1.0F : state.getOwnHeight();
    }

    @Override
    public float getOwnHeight(FluidState state) {
        return state.getAmount() / 9.0F;
    }

    @Override
    public abstract int getAmount(FluidState var1);

    @Override
    public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
        return state.getAmount() == 9 && hasSameAbove(state, level, pos)
            ? Shapes.block()
            : this.shapes.computeIfAbsent(state, stateKey -> Shapes.box(0.0, 0.0, 0.0, 1.0, stateKey.getHeight(level, pos), 1.0));
    }
}
