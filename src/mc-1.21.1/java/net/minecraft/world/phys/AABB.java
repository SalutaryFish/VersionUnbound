package net.minecraft.world.phys;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.joml.Vector3f;

public class AABB {
    private static final double EPSILON = 1.0E-7;
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public AABB(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public AABB(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    public AABB(Vec3 start, Vec3 end) {
        this(start.x, start.y, start.z, end.x, end.y, end.z);
    }

    public static AABB of(BoundingBox mutableBox) {
        return new AABB(mutableBox.minX(), mutableBox.minY(), mutableBox.minZ(), mutableBox.maxX() + 1, mutableBox.maxY() + 1, mutableBox.maxZ() + 1);
    }

    public static AABB unitCubeFromLowerCorner(Vec3 vector) {
        return new AABB(vector.x, vector.y, vector.z, vector.x + 1.0, vector.y + 1.0, vector.z + 1.0);
    }

    public static AABB encapsulatingFullBlocks(BlockPos startPos, BlockPos endPos) {
        return new AABB(
            Math.min(startPos.getX(), endPos.getX()),
            Math.min(startPos.getY(), endPos.getY()),
            Math.min(startPos.getZ(), endPos.getZ()),
            Math.max(startPos.getX(), endPos.getX()) + 1,
            Math.max(startPos.getY(), endPos.getY()) + 1,
            Math.max(startPos.getZ(), endPos.getZ()) + 1
        );
    }

    public AABB setMinX(double minX) {
        return new AABB(minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinY(double minY) {
        return new AABB(this.minX, minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMinZ(double minZ) {
        return new AABB(this.minX, this.minY, minZ, this.maxX, this.maxY, this.maxZ);
    }

    public AABB setMaxX(double maxX) {
        return new AABB(this.minX, this.minY, this.minZ, maxX, this.maxY, this.maxZ);
    }

    public AABB setMaxY(double maxY) {
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, maxY, this.maxZ);
    }

    public AABB setMaxZ(double maxZ) {
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, maxZ);
    }

    public double min(Direction.Axis axis) {
        return axis.choose(this.minX, this.minY, this.minZ);
    }

    public double max(Direction.Axis axis) {
        return axis.choose(this.maxX, this.maxY, this.maxZ);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof AABB aABB)) {
            return false;
        } else if (Double.compare(aABB.minX, this.minX) != 0) {
            return false;
        } else if (Double.compare(aABB.minY, this.minY) != 0) {
            return false;
        } else if (Double.compare(aABB.minZ, this.minZ) != 0) {
            return false;
        } else if (Double.compare(aABB.maxX, this.maxX) != 0) {
            return false;
        } else {
            return Double.compare(aABB.maxY, this.maxY) != 0 ? false : Double.compare(aABB.maxZ, this.maxZ) == 0;
        }
    }

    @Override
    public int hashCode() {
        long l = Double.doubleToLongBits(this.minX);
        int i = (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.minY);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.minZ);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.maxX);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.maxY);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.maxZ);
        return 31 * i + (int)(l ^ l >>> 32);
    }

    /**
     * Creates a new {@link AxisAlignedBB} that has been contracted by the given amount, with positive changes decreasing max values and negative changes increasing min values.
     * <br/>
     * If the amount to contract by is larger than the length of a side, then the side will wrap (still creating a valid AABB - see last sample).
     *
     * <h3>Samples:</h3>
     * <table>
     * <tr><th>Input</th><th>Result</th></tr>
     * <tr><td><pre><code>new AxisAlignedBB(0, 0, 0, 4, 4, 4).contract(2, 2, 2)</code></pre></td><td><pre><samp>box[0.0, 0.0, 0.0 -> 2.0, 2.0, 2.0]</samp></pre></td></tr>
     * <tr><td><pre><code>new AxisAlignedBB(0, 0, 0, 4, 4, 4).contract(-2, -2, -2)</code></pre></td><td><pre><samp>box[2.0, 2.0, 2.0 -> 4.0, 4.0, 4.0]</samp></pre></td></tr>
     * <tr><td><pre><code>new AxisAlignedBB(5, 5, 5, 7, 7, 7).contract(0, 1, -1)</code></pre></td><td><pre><samp>box[5.0, 5.0, 6.0 -> 7.0, 6.0, 7.0]</samp></pre></td></tr>
     * <tr><td><pre><code>new AxisAlignedBB(-2, -2, -2, 2, 2, 2).contract(4, -4, 0)</code></pre></td><td><pre><samp>box[-8.0, 2.0, -2.0 -> -2.0, 8.0, 2.0]</samp></pre></td></tr>
     * </table>
     *
     * <h3>See Also:</h3>
     * <ul>
     * <li>{@link #expand(double, double, double)} - like this, except for expanding.</li>
     * <li>{@link #grow(double, double, double)} and {@link #grow(double)} - expands in all directions.</li>
     * <li>{@link #shrink(double)} - contracts in all directions (like {@link #grow(double)})</li>
     * </ul>
     *
     * @return A new modified bounding box.
     */
    public AABB contract(double x, double y, double z) {
        double d = this.minX;
        double e = this.minY;
        double f = this.minZ;
        double g = this.maxX;
        double h = this.maxY;
        double i = this.maxZ;
        if (x < 0.0) {
            d -= x;
        } else if (x > 0.0) {
            g -= x;
        }

        if (y < 0.0) {
            e -= y;
        } else if (y > 0.0) {
            h -= y;
        }

        if (z < 0.0) {
            f -= z;
        } else if (z > 0.0) {
            i -= z;
        }

        return new AABB(d, e, f, g, h, i);
    }

    public AABB expandTowards(Vec3 vector) {
        return this.expandTowards(vector.x, vector.y, vector.z);
    }

    /**
     * Creates a new {@link AxisAlignedBB} that has been expanded by the given amount, with positive changes increasing max values and negative changes decreasing min values.
     *
     * <h3>Samples:</h3>
     * <table>
     * <tr><th>Input</th><th>Result</th></tr>
     * <tr><td><pre><code>new AxisAlignedBB(0, 0, 0, 1, 1, 1).expand(2, 2, 2)</code></pre></td><td><pre><samp>box[0, 0, 0 -> 3, 3, 3]</samp></pre></td><td>
     * <tr><td><pre><code>new AxisAlignedBB(0, 0, 0, 1, 1, 1).expand(-2, -2, -2)</code></pre></td><td><pre><samp>box[-2, -2, -2 -> 1, 1, 1]</samp></pre></td><td>
     * <tr><td><pre><code>new AxisAlignedBB(5, 5, 5, 7, 7, 7).expand(0, 1, -1)</code></pre></td><td><pre><samp>box[5, 5, 4, 7, 8, 7]</samp></pre></td><td>
     * </table>
     *
     * <h3>See Also:</h3>
     * <ul>
     * <li>{@link #contract(double, double, double)} - like this, except for shrinking.</li>
     * <li>{@link #grow(double, double, double)} and {@link #grow(double)} - expands in all directions.</li>
     * <li>{@link #shrink(double)} - contracts in all directions (like {@link #grow(double)})</li>
     * </ul>
     *
     * @return A modified bounding box that will always be equal or greater in volume to this bounding box.
     */
    public AABB expandTowards(double x, double y, double z) {
        double d = this.minX;
        double e = this.minY;
        double f = this.minZ;
        double g = this.maxX;
        double h = this.maxY;
        double i = this.maxZ;
        if (x < 0.0) {
            d += x;
        } else if (x > 0.0) {
            g += x;
        }

        if (y < 0.0) {
            e += y;
        } else if (y > 0.0) {
            h += y;
        }

        if (z < 0.0) {
            f += z;
        } else if (z > 0.0) {
            i += z;
        }

        return new AABB(d, e, f, g, h, i);
    }

    /**
     * Creates a new {@link AxisAlignedBB} that has been contracted by the given amount in both directions. Negative values will shrink the AABB instead of expanding it.
     * <br/>
     * Side lengths will be increased by 2 times the value of the parameters, since both min and max are changed.
     * <br/>
     * If contracting and the amount to contract by is larger than the length of a side, then the side will wrap (still creating a valid AABB - see last ample).
     *
     * <h3>Samples:</h3>
     * <table>
     * <tr><th>Input</th><th>Result</th></tr>
     * <tr><td><pre><code>new AxisAlignedBB(0, 0, 0, 1, 1, 1).grow(2, 2, 2)</code></pre></td><td><pre><samp>box[-2.0, -2.0, -2.0 -> 3.0, 3.0, 3.0]</samp></pre></td></tr>
     * <tr><td><pre><code>new AxisAlignedBB(0, 0, 0, 6, 6, 6).grow(-2, -2, -2)</code></pre></td><td><pre><samp>box[2.0, 2.0, 2.0 -> 4.0, 4.0, 4.0]</samp></pre></td></tr>
     * <tr><td><pre><code>new AxisAlignedBB(5, 5, 5, 7, 7, 7).grow(0, 1, -1)</code></pre></td><td><pre><samp>box[5.0, 4.0, 6.0 -> 7.0, 8.0, 6.0]</samp></pre></td></tr>
     * <tr><td><pre><code>new AxisAlignedBB(1, 1, 1, 3, 3, 3).grow(-4, -2, -3)</code></pre></td><td><pre><samp>box[-1.0, 1.0, 0.0 -> 5.0, 3.0, 4.0]</samp></pre></td></tr>
     * </table>
     *
     * <h3>See Also:</h3>
     * <ul>
     * <li>{@link #expand(double, double, double)} - expands in only one direction.</li>
     * <li>{@link #contract(double, double, double)} - contracts in only one direction.</li>
     * <lu>{@link #grow(double)} - version of this that expands in all directions from one parameter.</li>
     * <li>{@link #shrink(double)} - contracts in all directions</li>
     * </ul>
     *
     * @return A modified bounding box.
     */
    public AABB inflate(double x, double y, double z) {
        double d = this.minX - x;
        double e = this.minY - y;
        double f = this.minZ - z;
        double g = this.maxX + x;
        double h = this.maxY + y;
        double i = this.maxZ + z;
        return new AABB(d, e, f, g, h, i);
    }

    /**
     * Creates a new {@link AxisAlignedBB} that is expanded by the given value in all directions. Equivalent to {@link #grow(double, double, double)} with the given value for all 3 params. Negative values will shrink the AABB.
     * <br/>
     * Side lengths will be increased by 2 times the value of the parameter, since both min and max are changed.
     * <br/>
     * If contracting and the amount to contract by is larger than the length of a side, then the side will wrap (still creating a valid AABB - see samples on {@link #grow(double, double, double)}).
     *
     * @return A modified AABB.
     */
    public AABB inflate(double value) {
        return this.inflate(value, value, value);
    }

    public AABB intersect(AABB other) {
        double d = Math.max(this.minX, other.minX);
        double e = Math.max(this.minY, other.minY);
        double f = Math.max(this.minZ, other.minZ);
        double g = Math.min(this.maxX, other.maxX);
        double h = Math.min(this.maxY, other.maxY);
        double i = Math.min(this.maxZ, other.maxZ);
        return new AABB(d, e, f, g, h, i);
    }

    public AABB minmax(AABB other) {
        double d = Math.min(this.minX, other.minX);
        double e = Math.min(this.minY, other.minY);
        double f = Math.min(this.minZ, other.minZ);
        double g = Math.max(this.maxX, other.maxX);
        double h = Math.max(this.maxY, other.maxY);
        double i = Math.max(this.maxZ, other.maxZ);
        return new AABB(d, e, f, g, h, i);
    }

    /**
     * Offsets the current bounding box by the specified amount.
     */
    public AABB move(double x, double y, double z) {
        return new AABB(this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    public AABB move(BlockPos pos) {
        return new AABB(
            this.minX + pos.getX(), this.minY + pos.getY(), this.minZ + pos.getZ(), this.maxX + pos.getX(), this.maxY + pos.getY(), this.maxZ + pos.getZ()
        );
    }

    public AABB move(Vec3 vec) {
        return this.move(vec.x, vec.y, vec.z);
    }

    public AABB move(Vector3f vec) {
        return this.move(vec.x, vec.y, vec.z);
    }

    /**
     * Checks if the bounding box intersects with another.
     */
    public boolean intersects(AABB other) {
        return this.intersects(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
    }

    public boolean intersects(double x1, double y1, double z1, double x2, double y2, double z2) {
        return this.minX < x2 && this.maxX > x1 && this.minY < y2 && this.maxY > y1 && this.minZ < z2 && this.maxZ > z1;
    }

    public boolean intersects(Vec3 min, Vec3 max) {
        return this.intersects(
            Math.min(min.x, max.x), Math.min(min.y, max.y), Math.min(min.z, max.z), Math.max(min.x, max.x), Math.max(min.y, max.y), Math.max(min.z, max.z)
        );
    }

    /**
     * Returns if the supplied Vec3D is completely inside the bounding box
     */
    public boolean contains(Vec3 vec) {
        return this.contains(vec.x, vec.y, vec.z);
    }

    public boolean contains(double x, double y, double z) {
        return x >= this.minX && x < this.maxX && y >= this.minY && y < this.maxY && z >= this.minZ && z < this.maxZ;
    }

    public double getSize() {
        double d = this.getXsize();
        double e = this.getYsize();
        double f = this.getZsize();
        return (d + e + f) / 3.0;
    }

    public double getXsize() {
        return this.maxX - this.minX;
    }

    public double getYsize() {
        return this.maxY - this.minY;
    }

    public double getZsize() {
        return this.maxZ - this.minZ;
    }

    public AABB deflate(double x, double y, double z) {
        return this.inflate(-x, -y, -z);
    }

    /**
     * Creates a new {@link AxisAlignedBB} that is expanded by the given value in all directions. Equivalent to {@link #grow(double)} with value set to the negative of the value provided here. Passing a negative value to this method values will grow the AABB.
     * <br/>
     * Side lengths will be decreased by 2 times the value of the parameter, since both min and max are changed.
     * <br/>
     * If contracting and the amount to contract by is larger than the length of a side, then the side will wrap (still creating a valid AABB - see samples on {@link #grow(double, double, double)}).
     *
     * @return A modified AABB.
     */
    public AABB deflate(double value) {
        return this.inflate(-value);
    }

    public Optional<Vec3> clip(Vec3 from, Vec3 to) {
        double[] ds = new double[]{1.0};
        double d = to.x - from.x;
        double e = to.y - from.y;
        double f = to.z - from.z;
        Direction direction = getDirection(this, from, ds, null, d, e, f);
        if (direction == null) {
            return Optional.empty();
        }

        double g = ds[0];
        return Optional.of(from.add(g * d, g * e, g * f));
    }

    @Nullable
    public static BlockHitResult clip(Iterable<AABB> boxes, Vec3 start, Vec3 end, BlockPos pos) {
        double[] ds = new double[]{1.0};
        Direction direction = null;
        double d = end.x - start.x;
        double e = end.y - start.y;
        double f = end.z - start.z;

        for (AABB aABB : boxes) {
            direction = getDirection(aABB.move(pos), start, ds, direction, d, e, f);
        }

        if (direction == null) {
            return null;
        }

        double g = ds[0];
        return new BlockHitResult(start.add(g * d, g * e, g * f), direction, pos, false);
    }

    @Nullable
    private static Direction getDirection(AABB aabb, Vec3 start, double[] minDistance, @Nullable Direction facing, double deltaX, double deltaY, double deltaZ) {
        if (deltaX > 1.0E-7) {
            facing = clipPoint(
                minDistance, facing, deltaX, deltaY, deltaZ, aabb.minX, aabb.minY, aabb.maxY, aabb.minZ, aabb.maxZ, Direction.WEST, start.x, start.y, start.z
            );
        } else if (deltaX < -1.0E-7) {
            facing = clipPoint(
                minDistance, facing, deltaX, deltaY, deltaZ, aabb.maxX, aabb.minY, aabb.maxY, aabb.minZ, aabb.maxZ, Direction.EAST, start.x, start.y, start.z
            );
        }

        if (deltaY > 1.0E-7) {
            facing = clipPoint(
                minDistance, facing, deltaY, deltaZ, deltaX, aabb.minY, aabb.minZ, aabb.maxZ, aabb.minX, aabb.maxX, Direction.DOWN, start.y, start.z, start.x
            );
        } else if (deltaY < -1.0E-7) {
            facing = clipPoint(
                minDistance, facing, deltaY, deltaZ, deltaX, aabb.maxY, aabb.minZ, aabb.maxZ, aabb.minX, aabb.maxX, Direction.UP, start.y, start.z, start.x
            );
        }

        if (deltaZ > 1.0E-7) {
            facing = clipPoint(
                minDistance, facing, deltaZ, deltaX, deltaY, aabb.minZ, aabb.minX, aabb.maxX, aabb.minY, aabb.maxY, Direction.NORTH, start.z, start.x, start.y
            );
        } else if (deltaZ < -1.0E-7) {
            facing = clipPoint(
                minDistance, facing, deltaZ, deltaX, deltaY, aabb.maxZ, aabb.minX, aabb.maxX, aabb.minY, aabb.maxY, Direction.SOUTH, start.z, start.x, start.y
            );
        }

        return facing;
    }

    @Nullable
    private static Direction clipPoint(
        double[] minDistance,
        @Nullable Direction prevDirection,
        double distanceSide,
        double distanceOtherA,
        double distanceOtherB,
        double minSide,
        double minOtherA,
        double maxOtherA,
        double minOtherB,
        double maxOtherB,
        Direction hitSide,
        double startSide,
        double startOtherA,
        double startOtherB
    ) {
        double d = (minSide - startSide) / distanceSide;
        double e = startOtherA + d * distanceOtherA;
        double f = startOtherB + d * distanceOtherB;
        if (0.0 < d && d < minDistance[0] && minOtherA - 1.0E-7 < e && e < maxOtherA + 1.0E-7 && minOtherB - 1.0E-7 < f && f < maxOtherB + 1.0E-7) {
            minDistance[0] = d;
            return hitSide;
        } else {
            return prevDirection;
        }
    }

    public double distanceToSqr(Vec3 vec) {
        double d = Math.max(Math.max(this.minX - vec.x, vec.x - this.maxX), 0.0);
        double e = Math.max(Math.max(this.minY - vec.y, vec.y - this.maxY), 0.0);
        double f = Math.max(Math.max(this.minZ - vec.z, vec.z - this.maxZ), 0.0);
        return Mth.lengthSquared(d, e, f);
    }

    @Override
    public String toString() {
        return "AABB[" + this.minX + ", " + this.minY + ", " + this.minZ + "] -> [" + this.maxX + ", " + this.maxY + ", " + this.maxZ + "]";
    }

    public boolean hasNaN() {
        return Double.isNaN(this.minX)
            || Double.isNaN(this.minY)
            || Double.isNaN(this.minZ)
            || Double.isNaN(this.maxX)
            || Double.isNaN(this.maxY)
            || Double.isNaN(this.maxZ);
    }

    public Vec3 getCenter() {
        return new Vec3(Mth.lerp(0.5, this.minX, this.maxX), Mth.lerp(0.5, this.minY, this.maxY), Mth.lerp(0.5, this.minZ, this.maxZ));
    }

    public Vec3 getBottomCenter() {
        return new Vec3(Mth.lerp(0.5, this.minX, this.maxX), this.minY, Mth.lerp(0.5, this.minZ, this.maxZ));
    }

    public Vec3 getMinPosition() {
        return new Vec3(this.minX, this.minY, this.minZ);
    }

    public Vec3 getMaxPosition() {
        return new Vec3(this.maxX, this.maxY, this.maxZ);
    }

    public static AABB ofSize(Vec3 center, double xSize, double ySize, double zSize) {
        return new AABB(
            center.x - xSize / 2.0, center.y - ySize / 2.0, center.z - zSize / 2.0, center.x + xSize / 2.0, center.y + ySize / 2.0, center.z + zSize / 2.0
        );
    }
}
