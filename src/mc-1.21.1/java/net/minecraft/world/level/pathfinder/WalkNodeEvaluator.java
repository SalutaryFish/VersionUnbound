package net.minecraft.world.level.pathfinder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WalkNodeEvaluator extends NodeEvaluator {
    public static final double SPACE_BETWEEN_WALL_POSTS = 0.5;
    private static final double DEFAULT_MOB_JUMP_HEIGHT = 1.125;
    private final Long2ObjectMap<PathType> pathTypesByPosCacheByMob = new Long2ObjectOpenHashMap<>();
    private final Object2BooleanMap<AABB> collisionCache = new Object2BooleanOpenHashMap<>();
    private final Node[] reusableNeighbors = new Node[Direction.Plane.HORIZONTAL.length()];

    @Override
    public void prepare(PathNavigationRegion level, Mob mob) {
        super.prepare(level, mob);
        mob.onPathfindingStart();
    }

    @Override
    public void done() {
        this.mob.onPathfindingDone();
        this.pathTypesByPosCacheByMob.clear();
        this.collisionCache.clear();
        super.done();
    }

    @Override
    public Node getStart() {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int i = this.mob.getBlockY();
        BlockState blockState = this.currentContext.getBlockState(mutableBlockPos.set(this.mob.getX(), i, this.mob.getZ()));
        if (!this.mob.canStandOnFluid(blockState.getFluidState())) {
            if (this.canFloat() && this.mob.isInWater()) {
                while (true) {
                    if (!blockState.is(Blocks.WATER) && blockState.getFluidState() != Fluids.WATER.getSource(false)) {
                        i--;
                        break;
                    }

                    blockState = this.currentContext.getBlockState(mutableBlockPos.set(this.mob.getX(), ++i, this.mob.getZ()));
                }
            } else if (this.mob.onGround()) {
                i = Mth.floor(this.mob.getY() + 0.5);
            } else {
                mutableBlockPos.set(this.mob.getX(), this.mob.getY() + 1.0, this.mob.getZ());

                while (mutableBlockPos.getY() > this.currentContext.level().getMinBuildHeight()) {
                    i = mutableBlockPos.getY();
                    mutableBlockPos.setY(mutableBlockPos.getY() - 1);
                    BlockState blockState2 = this.currentContext.getBlockState(mutableBlockPos);
                    if (!blockState2.isAir() && !blockState2.isPathfindable(PathComputationType.LAND)) {
                        break;
                    }
                }
            }
        } else {
            while (this.mob.canStandOnFluid(blockState.getFluidState())) {
                blockState = this.currentContext.getBlockState(mutableBlockPos.set(this.mob.getX(), ++i, this.mob.getZ()));
            }

            i--;
        }

        BlockPos blockPos = this.mob.blockPosition();
        if (!this.canStartAt(mutableBlockPos.set(blockPos.getX(), i, blockPos.getZ()))) {
            AABB aABB = this.mob.getBoundingBox();
            if (this.canStartAt(mutableBlockPos.set(aABB.minX, i, aABB.minZ))
                || this.canStartAt(mutableBlockPos.set(aABB.minX, i, aABB.maxZ))
                || this.canStartAt(mutableBlockPos.set(aABB.maxX, i, aABB.minZ))
                || this.canStartAt(mutableBlockPos.set(aABB.maxX, i, aABB.maxZ))) {
                return this.getStartNode(mutableBlockPos);
            }
        }

        return this.getStartNode(new BlockPos(blockPos.getX(), i, blockPos.getZ()));
    }

    protected Node getStartNode(BlockPos pos) {
        Node node = this.getNode(pos);
        node.type = this.getCachedPathType(node.x, node.y, node.z);
        node.costMalus = this.mob.getPathfindingMalus(node.type);
        return node;
    }

    protected boolean canStartAt(BlockPos pos) {
        PathType pathType = this.getCachedPathType(pos.getX(), pos.getY(), pos.getZ());
        return pathType != PathType.OPEN && this.mob.getPathfindingMalus(pathType) >= 0.0F;
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return this.getTargetNodeAt(x, y, z);
    }

    @Override
    public int getNeighbors(Node[] outputArray, Node node) {
        int i = 0;
        int j = 0;
        PathType pathType = this.getCachedPathType(node.x, node.y + 1, node.z);
        PathType pathType2 = this.getCachedPathType(node.x, node.y, node.z);
        if (this.mob.getPathfindingMalus(pathType) >= 0.0F && pathType2 != PathType.STICKY_HONEY) {
            j = Mth.floor(Math.max(1.0F, this.mob.maxUpStep()));
        }

        double d = this.getFloorLevel(new BlockPos(node.x, node.y, node.z));

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Node node2 = this.findAcceptedNode(node.x + direction.getStepX(), node.y, node.z + direction.getStepZ(), j, d, direction, pathType2);
            this.reusableNeighbors[direction.get2DDataValue()] = node2;
            if (this.isNeighborValid(node2, node)) {
                outputArray[i++] = node2;
            }
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Direction direction2 = direction.getClockWise();
            if (this.isDiagonalValid(node, this.reusableNeighbors[direction.get2DDataValue()], this.reusableNeighbors[direction2.get2DDataValue()])) {
                Node node3 = this.findAcceptedNode(
                    node.x + direction.getStepX() + direction2.getStepX(),
                    node.y,
                    node.z + direction.getStepZ() + direction2.getStepZ(),
                    j,
                    d,
                    direction,
                    pathType2
                );
                if (this.isDiagonalValid(node3)) {
                    outputArray[i++] = node3;
                }
            }
        }

        return i;
    }

    protected boolean isNeighborValid(@Nullable Node neighbor, Node node) {
        return neighbor != null && !neighbor.closed && (neighbor.costMalus >= 0.0F || node.costMalus < 0.0F);
    }

    protected boolean isDiagonalValid(Node root, @Nullable Node xNode, @Nullable Node zNode) {
        if (zNode == null || xNode == null || zNode.y > root.y || xNode.y > root.y) {
            return false;
        } else if (xNode.type != PathType.WALKABLE_DOOR && zNode.type != PathType.WALKABLE_DOOR) {
            boolean bl = zNode.type == PathType.FENCE && xNode.type == PathType.FENCE && this.mob.getBbWidth() < 0.5;
            return (zNode.y < root.y || zNode.costMalus >= 0.0F || bl) && (xNode.y < root.y || xNode.costMalus >= 0.0F || bl);
        } else {
            return false;
        }
    }

    protected boolean isDiagonalValid(@Nullable Node node) {
        if (node == null || node.closed) {
            return false;
        } else {
            return node.type == PathType.WALKABLE_DOOR ? false : node.costMalus >= 0.0F;
        }
    }

    private static boolean doesBlockHavePartialCollision(PathType pathType) {
        return pathType == PathType.FENCE || pathType == PathType.DOOR_WOOD_CLOSED || pathType == PathType.DOOR_IRON_CLOSED;
    }

    private boolean canReachWithoutCollision(Node node) {
        AABB aABB = this.mob.getBoundingBox();
        Vec3 vec3 = new Vec3(
            node.x - this.mob.getX() + aABB.getXsize() / 2.0,
            node.y - this.mob.getY() + aABB.getYsize() / 2.0,
            node.z - this.mob.getZ() + aABB.getZsize() / 2.0
        );
        int i = Mth.ceil(vec3.length() / aABB.getSize());
        vec3 = vec3.scale(1.0F / i);

        for (int j = 1; j <= i; j++) {
            aABB = aABB.move(vec3);
            if (this.hasCollisions(aABB)) {
                return false;
            }
        }

        return true;
    }

    protected double getFloorLevel(BlockPos pos) {
        BlockGetter blockGetter = this.currentContext.level();
        return (this.canFloat() || this.isAmphibious()) && blockGetter.getFluidState(pos).is(FluidTags.WATER)
            ? pos.getY() + 0.5
            : getFloorLevel(blockGetter, pos);
    }

    public static double getFloorLevel(BlockGetter level, BlockPos pos) {
        BlockPos blockPos = pos.below();
        VoxelShape voxelShape = level.getBlockState(blockPos).getCollisionShape(level, blockPos);
        return blockPos.getY() + (voxelShape.isEmpty() ? 0.0 : voxelShape.max(Direction.Axis.Y));
    }

    protected boolean isAmphibious() {
        return false;
    }

    @Nullable
    protected Node findAcceptedNode(int x, int y, int z, int verticalDeltaLimit, double nodeFloorLevel, Direction direction, PathType pathType) {
        Node node = null;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        double d = this.getFloorLevel(mutableBlockPos.set(x, y, z));
        if (d - nodeFloorLevel > this.getMobJumpHeight()) {
            return null;
        }

        PathType pathType2 = this.getCachedPathType(x, y, z);
        float f = this.mob.getPathfindingMalus(pathType2);
        if (f >= 0.0F) {
            node = this.getNodeAndUpdateCostToMax(x, y, z, pathType2, f);
        }

        if (doesBlockHavePartialCollision(pathType) && node != null && node.costMalus >= 0.0F && !this.canReachWithoutCollision(node)) {
            node = null;
        }

        if (pathType2 != PathType.WALKABLE && (!this.isAmphibious() || pathType2 != PathType.WATER)) {
            if ((node == null || node.costMalus < 0.0F)
                && verticalDeltaLimit > 0
                && (pathType2 != PathType.FENCE || this.canWalkOverFences())
                && pathType2 != PathType.UNPASSABLE_RAIL
                && pathType2 != PathType.TRAPDOOR
                && pathType2 != PathType.POWDER_SNOW) {
                node = this.tryJumpOn(x, y, z, verticalDeltaLimit, nodeFloorLevel, direction, pathType, mutableBlockPos);
            } else if (!this.isAmphibious() && pathType2 == PathType.WATER && !this.canFloat()) {
                node = this.tryFindFirstNonWaterBelow(x, y, z, node);
            } else if (pathType2 == PathType.OPEN) {
                node = this.tryFindFirstGroundNodeBelow(x, y, z);
            } else if (doesBlockHavePartialCollision(pathType2) && node == null) {
                node = this.getClosedNode(x, y, z, pathType2);
            }

            return node;
        } else {
            return node;
        }
    }

    private double getMobJumpHeight() {
        return Math.max(1.125, this.mob.maxUpStep());
    }

    private Node getNodeAndUpdateCostToMax(int x, int y, int z, PathType pathType, float malus) {
        Node node = this.getNode(x, y, z);
        node.type = pathType;
        node.costMalus = Math.max(node.costMalus, malus);
        return node;
    }

    private Node getBlockedNode(int x, int y, int z) {
        Node node = this.getNode(x, y, z);
        node.type = PathType.BLOCKED;
        node.costMalus = -1.0F;
        return node;
    }

    private Node getClosedNode(int x, int y, int z, PathType pathType) {
        Node node = this.getNode(x, y, z);
        node.closed = true;
        node.type = pathType;
        node.costMalus = pathType.getMalus();
        return node;
    }

    @Nullable
    private Node tryJumpOn(
        int x, int y, int z, int verticalDeltaLimit, double nodeFloorLevel, Direction direction, PathType pathType, BlockPos.MutableBlockPos pos
    ) {
        Node node = this.findAcceptedNode(x, y + 1, z, verticalDeltaLimit - 1, nodeFloorLevel, direction, pathType);
        if (node == null) {
            return null;
        }

        if (this.mob.getBbWidth() >= 1.0F) {
            return node;
        }

        if (node.type != PathType.OPEN && node.type != PathType.WALKABLE) {
            return node;
        }

        double d = x - direction.getStepX() + 0.5;
        double e = z - direction.getStepZ() + 0.5;
        double f = this.mob.getBbWidth() / 2.0;
        AABB aABB = new AABB(
            d - f,
            this.getFloorLevel(pos.set(d, y + 1, e)) + 0.001,
            e - f,
            d + f,
            this.mob.getBbHeight() + this.getFloorLevel(pos.set((double)node.x, (double)node.y, (double)node.z)) - 0.002,
            e + f
        );
        return this.hasCollisions(aABB) ? null : node;
    }

    @Nullable
    private Node tryFindFirstNonWaterBelow(int x, int y, int z, @Nullable Node node) {
        y--;

        while (y > this.mob.level().getMinBuildHeight()) {
            PathType pathType = this.getCachedPathType(x, y, z);
            if (pathType != PathType.WATER) {
                return node;
            }

            node = this.getNodeAndUpdateCostToMax(x, y, z, pathType, this.mob.getPathfindingMalus(pathType));
            y--;
        }

        return node;
    }

    private Node tryFindFirstGroundNodeBelow(int x, int y, int z) {
        for (int i = y - 1; i >= this.mob.level().getMinBuildHeight(); i--) {
            if (y - i > this.mob.getMaxFallDistance()) {
                return this.getBlockedNode(x, i, z);
            }

            PathType pathType = this.getCachedPathType(x, i, z);
            float f = this.mob.getPathfindingMalus(pathType);
            if (pathType != PathType.OPEN) {
                if (f >= 0.0F) {
                    return this.getNodeAndUpdateCostToMax(x, i, z, pathType, f);
                }

                return this.getBlockedNode(x, i, z);
            }
        }

        return this.getBlockedNode(x, y, z);
    }

    private boolean hasCollisions(AABB boundingBox) {
        return this.collisionCache.computeIfAbsent(boundingBox, key -> !this.currentContext.level().noCollision(this.mob, boundingBox));
    }

    protected PathType getCachedPathType(int x, int y, int z) {
        return this.pathTypesByPosCacheByMob.computeIfAbsent(BlockPos.asLong(x, y, z), l -> this.getPathTypeOfMob(this.currentContext, x, y, z, this.mob));
    }

    @Override
    public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
        Set<PathType> set = this.getPathTypeWithinMobBB(context, x, y, z);
        if (set.contains(PathType.FENCE)) {
            return PathType.FENCE;
        }

        if (set.contains(PathType.UNPASSABLE_RAIL)) {
            return PathType.UNPASSABLE_RAIL;
        }

        PathType pathType = PathType.BLOCKED;

        for (PathType pathType2 : set) {
            if (mob.getPathfindingMalus(pathType2) < 0.0F) {
                return pathType2;
            }

            if (mob.getPathfindingMalus(pathType2) >= mob.getPathfindingMalus(pathType)) {
                pathType = pathType2;
            }
        }

        return this.entityWidth <= 1
                && pathType != PathType.OPEN
                && mob.getPathfindingMalus(pathType) == 0.0F
                && this.getPathType(context, x, y, z) == PathType.OPEN
            ? PathType.OPEN
            : pathType;
    }

    public Set<PathType> getPathTypeWithinMobBB(PathfindingContext context, int x, int y, int z) {
        EnumSet<PathType> enumSet = EnumSet.noneOf(PathType.class);

        for (int i = 0; i < this.entityWidth; i++) {
            for (int j = 0; j < this.entityHeight; j++) {
                for (int k = 0; k < this.entityDepth; k++) {
                    int l = i + x;
                    int m = j + y;
                    int n = k + z;
                    PathType pathType = this.getPathType(context, l, m, n);
                    BlockPos blockPos = this.mob.blockPosition();
                    boolean bl = this.canPassDoors();
                    if (pathType == PathType.DOOR_WOOD_CLOSED && this.canOpenDoors() && bl) {
                        pathType = PathType.WALKABLE_DOOR;
                    }

                    if (pathType == PathType.DOOR_OPEN && !bl) {
                        pathType = PathType.BLOCKED;
                    }

                    if (pathType == PathType.RAIL
                        && this.getPathType(context, blockPos.getX(), blockPos.getY(), blockPos.getZ()) != PathType.RAIL
                        && this.getPathType(context, blockPos.getX(), blockPos.getY() - 1, blockPos.getZ()) != PathType.RAIL) {
                        pathType = PathType.UNPASSABLE_RAIL;
                    }

                    enumSet.add(pathType);
                }
            }
        }

        return enumSet;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        return getPathTypeStatic(context, new BlockPos.MutableBlockPos(x, y, z));
    }

    public static PathType getPathTypeStatic(Mob mob, BlockPos pos) {
        return getPathTypeStatic(new PathfindingContext(mob.level(), mob), pos.mutable());
    }

    public static PathType getPathTypeStatic(PathfindingContext context, BlockPos.MutableBlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        PathType pathType = context.getPathTypeFromState(i, j, k);
        if (pathType == PathType.OPEN && j >= context.level().getMinBuildHeight() + 1) {
            return switch (context.getPathTypeFromState(i, j - 1, k)) {
                case OPEN, WATER, LAVA, WALKABLE -> PathType.OPEN;
                case DAMAGE_FIRE -> PathType.DAMAGE_FIRE;
                case DAMAGE_OTHER -> PathType.DAMAGE_OTHER;
                case STICKY_HONEY -> PathType.STICKY_HONEY;
                case POWDER_SNOW -> PathType.DANGER_POWDER_SNOW;
                case DAMAGE_CAUTIOUS -> PathType.DAMAGE_CAUTIOUS;
                case TRAPDOOR -> PathType.DANGER_TRAPDOOR;
                default -> checkNeighbourBlocks(context, i, j, k, PathType.WALKABLE);
            };
        } else {
            return pathType;
        }
    }

    public static PathType checkNeighbourBlocks(PathfindingContext context, int x, int y, int z, PathType pathType) {
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    if (i != 0 || k != 0) {
                        PathType pathType2 = context.getPathTypeFromState(x + i, y + j, z + k);
                        if (pathType2 == PathType.DAMAGE_OTHER) {
                            return PathType.DANGER_OTHER;
                        }

                        if (pathType2 == PathType.DAMAGE_FIRE || pathType2 == PathType.LAVA) {
                            return PathType.DANGER_FIRE;
                        }

                        if (pathType2 == PathType.WATER) {
                            return PathType.WATER_BORDER;
                        }

                        if (pathType2 == PathType.DAMAGE_CAUTIOUS) {
                            return PathType.DAMAGE_CAUTIOUS;
                        }
                    }
                }
            }
        }

        return pathType;
    }

    protected static PathType getPathTypeFromState(BlockGetter level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        Block block = blockState.getBlock();
        if (blockState.isAir()) {
            return PathType.OPEN;
        }

        if (blockState.is(BlockTags.TRAPDOORS) || blockState.is(Blocks.LILY_PAD) || blockState.is(Blocks.BIG_DRIPLEAF)) {
            return PathType.TRAPDOOR;
        }

        if (blockState.is(Blocks.POWDER_SNOW)) {
            return PathType.POWDER_SNOW;
        }

        if (blockState.is(Blocks.CACTUS) || blockState.is(Blocks.SWEET_BERRY_BUSH)) {
            return PathType.DAMAGE_OTHER;
        }

        if (blockState.is(Blocks.HONEY_BLOCK)) {
            return PathType.STICKY_HONEY;
        }

        if (blockState.is(Blocks.COCOA)) {
            return PathType.COCOA;
        }

        if (!blockState.is(Blocks.WITHER_ROSE) && !blockState.is(Blocks.POINTED_DRIPSTONE)) {
            FluidState fluidState = blockState.getFluidState();
            if (fluidState.is(FluidTags.LAVA)) {
                return PathType.LAVA;
            }

            if (isBurningBlock(blockState)) {
                return PathType.DAMAGE_FIRE;
            }

            if (block instanceof DoorBlock doorBlock) {
                if (blockState.getValue(DoorBlock.OPEN)) {
                    return PathType.DOOR_OPEN;
                } else {
                    return doorBlock.type().canOpenByHand() ? PathType.DOOR_WOOD_CLOSED : PathType.DOOR_IRON_CLOSED;
                }
            } else {
                if (block instanceof BaseRailBlock) {
                    return PathType.RAIL;
                }

                if (block instanceof LeavesBlock) {
                    return PathType.LEAVES;
                }

                if (!blockState.is(BlockTags.FENCES)
                    && !blockState.is(BlockTags.WALLS)
                    && (!(block instanceof FenceGateBlock) || blockState.getValue(FenceGateBlock.OPEN))) {
                    if (!blockState.isPathfindable(PathComputationType.LAND)) {
                        return PathType.BLOCKED;
                    } else {
                        return fluidState.is(FluidTags.WATER) ? PathType.WATER : PathType.OPEN;
                    }
                } else {
                    return PathType.FENCE;
                }
            }
        } else {
            return PathType.DAMAGE_CAUTIOUS;
        }
    }
}
