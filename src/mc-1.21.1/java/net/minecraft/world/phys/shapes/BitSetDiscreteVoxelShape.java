package net.minecraft.world.phys.shapes;

import java.util.BitSet;
import net.minecraft.core.Direction;

public final class BitSetDiscreteVoxelShape extends DiscreteVoxelShape {
    private final BitSet storage;
    private int xMin;
    private int yMin;
    private int zMin;
    private int xMax;
    private int yMax;
    private int zMax;

    public BitSetDiscreteVoxelShape(int xSize, int ySize, int zSize) {
        super(xSize, ySize, zSize);
        this.storage = new BitSet(xSize * ySize * zSize);
        this.xMin = xSize;
        this.yMin = ySize;
        this.zMin = zSize;
    }

    public static BitSetDiscreteVoxelShape withFilledBounds(int x, int y, int z, int xMin, int yMin, int zMin, int xMax, int yMax, int zMax) {
        BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = new BitSetDiscreteVoxelShape(x, y, z);
        bitSetDiscreteVoxelShape.xMin = xMin;
        bitSetDiscreteVoxelShape.yMin = yMin;
        bitSetDiscreteVoxelShape.zMin = zMin;
        bitSetDiscreteVoxelShape.xMax = xMax;
        bitSetDiscreteVoxelShape.yMax = yMax;
        bitSetDiscreteVoxelShape.zMax = zMax;

        for (int i = xMin; i < xMax; i++) {
            for (int j = yMin; j < yMax; j++) {
                for (int k = zMin; k < zMax; k++) {
                    bitSetDiscreteVoxelShape.fillUpdateBounds(i, j, k, false);
                }
            }
        }

        return bitSetDiscreteVoxelShape;
    }

    public BitSetDiscreteVoxelShape(DiscreteVoxelShape shape) {
        super(shape.xSize, shape.ySize, shape.zSize);
        if (shape instanceof BitSetDiscreteVoxelShape) {
            this.storage = (BitSet)((BitSetDiscreteVoxelShape)shape).storage.clone();
        } else {
            this.storage = new BitSet(this.xSize * this.ySize * this.zSize);

            for (int i = 0; i < this.xSize; i++) {
                for (int j = 0; j < this.ySize; j++) {
                    for (int k = 0; k < this.zSize; k++) {
                        if (shape.isFull(i, j, k)) {
                            this.storage.set(this.getIndex(i, j, k));
                        }
                    }
                }
            }
        }

        this.xMin = shape.firstFull(Direction.Axis.X);
        this.yMin = shape.firstFull(Direction.Axis.Y);
        this.zMin = shape.firstFull(Direction.Axis.Z);
        this.xMax = shape.lastFull(Direction.Axis.X);
        this.yMax = shape.lastFull(Direction.Axis.Y);
        this.zMax = shape.lastFull(Direction.Axis.Z);
    }

    protected int getIndex(int x, int y, int z) {
        return (x * this.ySize + y) * this.zSize + z;
    }

    @Override
    public boolean isFull(int x, int y, int z) {
        return this.storage.get(this.getIndex(x, y, z));
    }

    private void fillUpdateBounds(int x, int y, int z, boolean update) {
        this.storage.set(this.getIndex(x, y, z));
        if (update) {
            this.xMin = Math.min(this.xMin, x);
            this.yMin = Math.min(this.yMin, y);
            this.zMin = Math.min(this.zMin, z);
            this.xMax = Math.max(this.xMax, x + 1);
            this.yMax = Math.max(this.yMax, y + 1);
            this.zMax = Math.max(this.zMax, z + 1);
        }
    }

    @Override
    public void fill(int x, int y, int z) {
        this.fillUpdateBounds(x, y, z, true);
    }

    @Override
    public boolean isEmpty() {
        return this.storage.isEmpty();
    }

    @Override
    public int firstFull(Direction.Axis axis) {
        return axis.choose(this.xMin, this.yMin, this.zMin);
    }

    @Override
    public int lastFull(Direction.Axis axis) {
        return axis.choose(this.xMax, this.yMax, this.zMax);
    }

    static BitSetDiscreteVoxelShape join(
        DiscreteVoxelShape mainShape, DiscreteVoxelShape secondaryShape, IndexMerger mergerX, IndexMerger mergerY, IndexMerger mergerZ, BooleanOp operator
    ) {
        BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = new BitSetDiscreteVoxelShape(mergerX.size() - 1, mergerY.size() - 1, mergerZ.size() - 1);
        int[] is = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        mergerX.forMergedIndexes((x1, x2, x3) -> {
            boolean[] bls = new boolean[]{false};
            mergerY.forMergedIndexes((y1, y2, y3) -> {
                boolean[] bls2 = new boolean[]{false};
                mergerZ.forMergedIndexes((z1, z2, z3) -> {
                    if (operator.apply(mainShape.isFullWide(x1, y1, z1), secondaryShape.isFullWide(x2, y2, z2))) {
                        bitSetDiscreteVoxelShape.storage.set(bitSetDiscreteVoxelShape.getIndex(x3, y3, z3));
                        is[2] = Math.min(is[2], z3);
                        is[5] = Math.max(is[5], z3);
                        bls2[0] = true;
                    }

                    return true;
                });
                if (bls2[0]) {
                    is[1] = Math.min(is[1], y3);
                    is[4] = Math.max(is[4], y3);
                    bls[0] = true;
                }

                return true;
            });
            if (bls[0]) {
                is[0] = Math.min(is[0], x3);
                is[3] = Math.max(is[3], x3);
            }

            return true;
        });
        bitSetDiscreteVoxelShape.xMin = is[0];
        bitSetDiscreteVoxelShape.yMin = is[1];
        bitSetDiscreteVoxelShape.zMin = is[2];
        bitSetDiscreteVoxelShape.xMax = is[3] + 1;
        bitSetDiscreteVoxelShape.yMax = is[4] + 1;
        bitSetDiscreteVoxelShape.zMax = is[5] + 1;
        return bitSetDiscreteVoxelShape;
    }

    protected static void forAllBoxes(DiscreteVoxelShape shape, DiscreteVoxelShape.IntLineConsumer consumer, boolean combine) {
        BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = new BitSetDiscreteVoxelShape(shape);

        for (int i = 0; i < bitSetDiscreteVoxelShape.ySize; i++) {
            for (int j = 0; j < bitSetDiscreteVoxelShape.xSize; j++) {
                int k = -1;

                for (int l = 0; l <= bitSetDiscreteVoxelShape.zSize; l++) {
                    if (bitSetDiscreteVoxelShape.isFullWide(j, i, l)) {
                        if (combine) {
                            if (k == -1) {
                                k = l;
                            }
                        } else {
                            consumer.consume(j, i, l, j + 1, i + 1, l + 1);
                        }
                    } else if (k != -1) {
                        int m = j;
                        int n = i;
                        bitSetDiscreteVoxelShape.clearZStrip(k, l, j, i);

                        while (bitSetDiscreteVoxelShape.isZStripFull(k, l, m + 1, i)) {
                            bitSetDiscreteVoxelShape.clearZStrip(k, l, m + 1, i);
                            m++;
                        }

                        while (bitSetDiscreteVoxelShape.isXZRectangleFull(j, m + 1, k, l, n + 1)) {
                            for (int o = j; o <= m; o++) {
                                bitSetDiscreteVoxelShape.clearZStrip(k, l, o, n + 1);
                            }

                            n++;
                        }

                        consumer.consume(j, i, k, m + 1, n + 1, l);
                        k = -1;
                    }
                }
            }
        }
    }

    private boolean isZStripFull(int zMin, int zMax, int x, int y) {
        return x < this.xSize && y < this.ySize ? this.storage.nextClearBit(this.getIndex(x, y, zMin)) >= this.getIndex(x, y, zMax) : false;
    }

    private boolean isXZRectangleFull(int xMin, int xMax, int zMin, int zMax, int y) {
        for (int i = xMin; i < xMax; i++) {
            if (!this.isZStripFull(zMin, zMax, i, y)) {
                return false;
            }
        }

        return true;
    }

    private void clearZStrip(int zMin, int zMax, int x, int y) {
        this.storage.clear(this.getIndex(x, y, zMin), this.getIndex(x, y, zMax));
    }

    public boolean isInterior(int x, int y, int z) {
        boolean bl = x > 0 && x < this.xSize - 1 && y > 0 && y < this.ySize - 1 && z > 0 && z < this.zSize - 1;
        return bl
            && this.isFull(x, y, z)
            && this.isFull(x - 1, y, z)
            && this.isFull(x + 1, y, z)
            && this.isFull(x, y - 1, z)
            && this.isFull(x, y + 1, z)
            && this.isFull(x, y, z - 1)
            && this.isFull(x, y, z + 1);
    }
}
