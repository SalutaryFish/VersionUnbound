package net.minecraft.world.phys.shapes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.math.DoubleMath;
import com.google.common.math.IntMath;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.Arrays;
import java.util.Objects;
import net.minecraft.Util;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public final class Shapes {
    public static final double EPSILON = 1.0E-7;
    public static final double BIG_EPSILON = 1.0E-6;
    private static final VoxelShape BLOCK = Util.make(() -> {
        DiscreteVoxelShape discreteVoxelShape = new BitSetDiscreteVoxelShape(1, 1, 1);
        discreteVoxelShape.fill(0, 0, 0);
        return new CubeVoxelShape(discreteVoxelShape);
    });
    public static final VoxelShape INFINITY = box(
        Double.NEGATIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY
    );
    private static final VoxelShape EMPTY = new ArrayVoxelShape(
        new BitSetDiscreteVoxelShape(0, 0, 0),
        new DoubleArrayList(new double[]{0.0}),
        new DoubleArrayList(new double[]{0.0}),
        new DoubleArrayList(new double[]{0.0})
    );

    public static VoxelShape empty() {
        return EMPTY;
    }

    public static VoxelShape block() {
        return BLOCK;
    }

    public static VoxelShape box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!(minX > maxX) && !(minY > maxY) && !(minZ > maxZ)) {
            return create(minX, minY, minZ, maxX, maxY, maxZ);
        } else {
            throw new IllegalArgumentException("The min values need to be smaller or equals to the max values");
        }
    }

    public static VoxelShape create(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!(maxX - minX < 1.0E-7) && !(maxY - minY < 1.0E-7) && !(maxZ - minZ < 1.0E-7)) {
            int i = findBits(minX, maxX);
            int j = findBits(minY, maxY);
            int k = findBits(minZ, maxZ);
            if (i < 0 || j < 0 || k < 0) {
                return new ArrayVoxelShape(
                    BLOCK.shape,
                    DoubleArrayList.wrap(new double[]{minX, maxX}),
                    DoubleArrayList.wrap(new double[]{minY, maxY}),
                    DoubleArrayList.wrap(new double[]{minZ, maxZ})
                );
            }

            if (i == 0 && j == 0 && k == 0) {
                return block();
            }

            int l = 1 << i;
            int m = 1 << j;
            int n = 1 << k;
            BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = BitSetDiscreteVoxelShape.withFilledBounds(
                l,
                m,
                n,
                (int)Math.round(minX * l),
                (int)Math.round(minY * m),
                (int)Math.round(minZ * n),
                (int)Math.round(maxX * l),
                (int)Math.round(maxY * m),
                (int)Math.round(maxZ * n)
            );
            return new CubeVoxelShape(bitSetDiscreteVoxelShape);
        } else {
            return empty();
        }
    }

    public static VoxelShape create(AABB aabb) {
        return create(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    @VisibleForTesting
    protected static int findBits(double minBits, double maxBits) {
        if (!(minBits < -1.0E-7) && !(maxBits > 1.0000001)) {
            for (int i = 0; i <= 3; i++) {
                int j = 1 << i;
                double d = minBits * j;
                double e = maxBits * j;
                boolean bl = Math.abs(d - Math.round(d)) < 1.0E-7 * j;
                boolean bl2 = Math.abs(e - Math.round(e)) < 1.0E-7 * j;
                if (bl && bl2) {
                    return i;
                }
            }

            return -1;
        } else {
            return -1;
        }
    }

    protected static long lcm(int aa, int bb) {
        return (long)aa * (bb / IntMath.gcd(aa, bb));
    }

    public static VoxelShape or(VoxelShape shape1, VoxelShape shape2) {
        return join(shape1, shape2, BooleanOp.OR);
    }

    public static VoxelShape or(VoxelShape shape1, VoxelShape... others) {
        return Arrays.stream(others).reduce(shape1, Shapes::or);
    }

    public static VoxelShape join(VoxelShape shape1, VoxelShape shape2, BooleanOp function) {
        return joinUnoptimized(shape1, shape2, function).optimize();
    }

    public static VoxelShape joinUnoptimized(VoxelShape shape1, VoxelShape shape2, BooleanOp function) {
        if (function.apply(false, false)) {
            throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException());
        }

        if (shape1 == shape2) {
            return function.apply(true, true) ? shape1 : empty();
        }

        boolean bl = function.apply(true, false);
        boolean bl2 = function.apply(false, true);
        if (shape1.isEmpty()) {
            return bl2 ? shape2 : empty();
        }

        if (shape2.isEmpty()) {
            return bl ? shape1 : empty();
        }

        IndexMerger indexMerger = createIndexMerger(1, shape1.getCoords(Direction.Axis.X), shape2.getCoords(Direction.Axis.X), bl, bl2);
        IndexMerger indexMerger2 = createIndexMerger(indexMerger.size() - 1, shape1.getCoords(Direction.Axis.Y), shape2.getCoords(Direction.Axis.Y), bl, bl2);
        IndexMerger indexMerger3 = createIndexMerger(
            (indexMerger.size() - 1) * (indexMerger2.size() - 1), shape1.getCoords(Direction.Axis.Z), shape2.getCoords(Direction.Axis.Z), bl, bl2
        );
        BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = BitSetDiscreteVoxelShape.join(
            shape1.shape, shape2.shape, indexMerger, indexMerger2, indexMerger3, function
        );
        return indexMerger instanceof DiscreteCubeMerger && indexMerger2 instanceof DiscreteCubeMerger && indexMerger3 instanceof DiscreteCubeMerger
            ? new CubeVoxelShape(bitSetDiscreteVoxelShape)
            : new ArrayVoxelShape(bitSetDiscreteVoxelShape, indexMerger.getList(), indexMerger2.getList(), indexMerger3.getList());
    }

    public static boolean joinIsNotEmpty(VoxelShape shape1, VoxelShape shape2, BooleanOp resultOperator) {
        if (resultOperator.apply(false, false)) {
            throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException());
        }

        boolean bl = shape1.isEmpty();
        boolean bl2 = shape2.isEmpty();
        if (!bl && !bl2) {
            if (shape1 == shape2) {
                return resultOperator.apply(true, true);
            }

            boolean bl3 = resultOperator.apply(true, false);
            boolean bl4 = resultOperator.apply(false, true);

            for (Direction.Axis axis : AxisCycle.AXIS_VALUES) {
                if (shape1.max(axis) < shape2.min(axis) - 1.0E-7) {
                    return bl3 || bl4;
                }

                if (shape2.max(axis) < shape1.min(axis) - 1.0E-7) {
                    return bl3 || bl4;
                }
            }

            IndexMerger indexMerger = createIndexMerger(1, shape1.getCoords(Direction.Axis.X), shape2.getCoords(Direction.Axis.X), bl3, bl4);
            IndexMerger indexMerger2 = createIndexMerger(
                indexMerger.size() - 1, shape1.getCoords(Direction.Axis.Y), shape2.getCoords(Direction.Axis.Y), bl3, bl4
            );
            IndexMerger indexMerger3 = createIndexMerger(
                (indexMerger.size() - 1) * (indexMerger2.size() - 1), shape1.getCoords(Direction.Axis.Z), shape2.getCoords(Direction.Axis.Z), bl3, bl4
            );
            return joinIsNotEmpty(indexMerger, indexMerger2, indexMerger3, shape1.shape, shape2.shape, resultOperator);
        } else {
            return resultOperator.apply(!bl, !bl2);
        }
    }

    private static boolean joinIsNotEmpty(
        IndexMerger mergerX,
        IndexMerger mergerY,
        IndexMerger mergerZ,
        DiscreteVoxelShape primaryShape,
        DiscreteVoxelShape secondaryShape,
        BooleanOp resultOperator
    ) {
        return !mergerX.forMergedIndexes(
            (x1, y1, z1) -> mergerY.forMergedIndexes(
                (x2, y2, z2) -> mergerZ.forMergedIndexes(
                    (x3, y3, z3) -> !resultOperator.apply(primaryShape.isFullWide(x1, x2, x3), secondaryShape.isFullWide(y1, y2, y3))
                )
            )
        );
    }

    public static double collide(Direction.Axis movementAxis, AABB collisionBox, Iterable<VoxelShape> possibleHits, double desiredOffset) {
        for (VoxelShape voxelShape : possibleHits) {
            if (Math.abs(desiredOffset) < 1.0E-7) {
                return 0.0;
            }

            desiredOffset = voxelShape.collide(movementAxis, collisionBox, desiredOffset);
        }

        return desiredOffset;
    }

    public static boolean blockOccudes(VoxelShape shape, VoxelShape adjacentShape, Direction side) {
        if (shape == block() && adjacentShape == block()) {
            return true;
        }

        if (adjacentShape.isEmpty()) {
            return false;
        }

        Direction.Axis axis = side.getAxis();
        Direction.AxisDirection axisDirection = side.getAxisDirection();
        VoxelShape voxelShape = axisDirection == Direction.AxisDirection.POSITIVE ? shape : adjacentShape;
        VoxelShape voxelShape2 = axisDirection == Direction.AxisDirection.POSITIVE ? adjacentShape : shape;
        BooleanOp booleanOp = axisDirection == Direction.AxisDirection.POSITIVE ? BooleanOp.ONLY_FIRST : BooleanOp.ONLY_SECOND;
        return DoubleMath.fuzzyEquals(voxelShape.max(axis), 1.0, 1.0E-7)
            && DoubleMath.fuzzyEquals(voxelShape2.min(axis), 0.0, 1.0E-7)
            && !joinIsNotEmpty(new SliceShape(voxelShape, axis, voxelShape.shape.getSize(axis) - 1), new SliceShape(voxelShape2, axis, 0), booleanOp);
    }

    public static VoxelShape getFaceShape(VoxelShape voxelShape, Direction direction) {
        if (voxelShape == block()) {
            return block();
        }

        Direction.Axis axis = direction.getAxis();
        boolean bl;
        int i;
        if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            bl = DoubleMath.fuzzyEquals(voxelShape.max(axis), 1.0, 1.0E-7);
            i = voxelShape.shape.getSize(axis) - 1;
        } else {
            bl = DoubleMath.fuzzyEquals(voxelShape.min(axis), 0.0, 1.0E-7);
            i = 0;
        }

        return !bl ? empty() : new SliceShape(voxelShape, axis, i);
    }

    public static boolean mergedFaceOccludes(VoxelShape shape, VoxelShape adjacentShape, Direction side) {
        if (shape != block() && adjacentShape != block()) {
            Direction.Axis axis = side.getAxis();
            Direction.AxisDirection axisDirection = side.getAxisDirection();
            VoxelShape voxelShape = axisDirection == Direction.AxisDirection.POSITIVE ? shape : adjacentShape;
            VoxelShape voxelShape2 = axisDirection == Direction.AxisDirection.POSITIVE ? adjacentShape : shape;
            if (!DoubleMath.fuzzyEquals(voxelShape.max(axis), 1.0, 1.0E-7)) {
                voxelShape = empty();
            }

            if (!DoubleMath.fuzzyEquals(voxelShape2.min(axis), 0.0, 1.0E-7)) {
                voxelShape2 = empty();
            }

            return !joinIsNotEmpty(
                block(),
                joinUnoptimized(new SliceShape(voxelShape, axis, voxelShape.shape.getSize(axis) - 1), new SliceShape(voxelShape2, axis, 0), BooleanOp.OR),
                BooleanOp.ONLY_FIRST
            );
        } else {
            return true;
        }
    }

    public static boolean faceShapeOccludes(VoxelShape voxelShape1, VoxelShape voxelShape2) {
        if (voxelShape1 == block() || voxelShape2 == block()) {
            return true;
        } else {
            return voxelShape1.isEmpty() && voxelShape2.isEmpty()
                ? false
                : !joinIsNotEmpty(block(), joinUnoptimized(voxelShape1, voxelShape2, BooleanOp.OR), BooleanOp.ONLY_FIRST);
        }
    }

    @VisibleForTesting
    protected static IndexMerger createIndexMerger(int size, DoubleList list1, DoubleList list2, boolean excludeUpper, boolean excludeLower) {
        int i = list1.size() - 1;
        int j = list2.size() - 1;
        if (list1 instanceof CubePointRange && list2 instanceof CubePointRange) {
            long l = lcm(i, j);
            if (size * l <= 256L) {
                return new DiscreteCubeMerger(i, j);
            }
        }

        if (list1.getDouble(i) < list2.getDouble(0) - 1.0E-7) {
            return new NonOverlappingMerger(list1, list2, false);
        } else if (list2.getDouble(j) < list1.getDouble(0) - 1.0E-7) {
            return new NonOverlappingMerger(list2, list1, true);
        } else {
            return i == j && Objects.equals(list1, list2) ? new IdenticalMerger(list1) : new IndirectMerger(list1, list2, excludeUpper, excludeLower);
        }
    }

    public interface DoubleLineConsumer {
        void consume(double var1, double var3, double var5, double var7, double var9, double var11);
    }
}
