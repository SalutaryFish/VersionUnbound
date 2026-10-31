package net.minecraft.world.phys;

import com.mojang.serialization.Codec;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

public class Vec3 implements Position {
    public static final Codec<Vec3> CODEC = Codec.DOUBLE
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize((List<Double>)list, 3).map(listx -> new Vec3((Double)listx.get(0), (Double)listx.get(1), (Double)listx.get(2))),
            vec3 -> List.of(vec3.x(), vec3.y(), vec3.z())
        );
    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);
    public final double x;
    public final double y;
    public final double z;

    public static Vec3 fromRGB24(int packed) {
        double d = (packed >> 16 & 0xFF) / 255.0;
        double e = (packed >> 8 & 0xFF) / 255.0;
        double f = (packed & 0xFF) / 255.0;
        return new Vec3(d, e, f);
    }

    /**
     * Copies the coordinates of an int vector exactly.
     */
    public static Vec3 atLowerCornerOf(Vec3i toCopy) {
        return new Vec3(toCopy.getX(), toCopy.getY(), toCopy.getZ());
    }

    public static Vec3 atLowerCornerWithOffset(Vec3i toCopy, double offsetX, double offsetY, double offsetZ) {
        return new Vec3(toCopy.getX() + offsetX, toCopy.getY() + offsetY, toCopy.getZ() + offsetZ);
    }

    /**
     * Copies the coordinates of an Int vector and centers them.
     */
    public static Vec3 atCenterOf(Vec3i toCopy) {
        return atLowerCornerWithOffset(toCopy, 0.5, 0.5, 0.5);
    }

    /**
     * Copies the coordinates of an int vector and centers them horizontally (x and z)
     */
    public static Vec3 atBottomCenterOf(Vec3i toCopy) {
        return atLowerCornerWithOffset(toCopy, 0.5, 0.0, 0.5);
    }

    /**
     * Copies the coordinates of an int vector and centers them horizontally and applies a vertical offset.
     */
    public static Vec3 upFromBottomCenterOf(Vec3i toCopy, double verticalOffset) {
        return atLowerCornerWithOffset(toCopy, 0.5, verticalOffset, 0.5);
    }

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vector3f vector) {
        this(vector.x(), vector.y(), vector.z());
    }

    /**
     * Returns a new vector with the result of the specified vector minus this.
     */
    public Vec3 vectorTo(Vec3 vec) {
        return new Vec3(vec.x - this.x, vec.y - this.y, vec.z - this.z);
    }

    public Vec3 normalize() {
        double d = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        return d < 1.0E-4 ? ZERO : new Vec3(this.x / d, this.y / d, this.z / d);
    }

    public double dot(Vec3 vec) {
        return this.x * vec.x + this.y * vec.y + this.z * vec.z;
    }

    /**
     * Returns a new vector with the result of this vector x the specified vector.
     */
    public Vec3 cross(Vec3 vec) {
        return new Vec3(this.y * vec.z - this.z * vec.y, this.z * vec.x - this.x * vec.z, this.x * vec.y - this.y * vec.x);
    }

    public Vec3 subtract(Vec3 vec) {
        return this.subtract(vec.x, vec.y, vec.z);
    }

    public Vec3 subtract(double x, double y, double z) {
        return this.add(-x, -y, -z);
    }

    public Vec3 add(Vec3 vec) {
        return this.add(vec.x, vec.y, vec.z);
    }

    /**
     * Adds the specified x,y,z vector components to this vector and returns the resulting vector. Does not change this vector.
     */
    public Vec3 add(double x, double y, double z) {
        return new Vec3(this.x + x, this.y + y, this.z + z);
    }

    /**
     * Checks if a position is within a certain distance of the coordinates.
     */
    public boolean closerThan(Position pos, double distance) {
        return this.distanceToSqr(pos.x(), pos.y(), pos.z()) < distance * distance;
    }

    /**
     * Euclidean distance between this and the specified vector, returned as double.
     */
    public double distanceTo(Vec3 vec) {
        double d = vec.x - this.x;
        double e = vec.y - this.y;
        double f = vec.z - this.z;
        return Math.sqrt(d * d + e * e + f * f);
    }

    /**
     * The square of the Euclidean distance between this and the specified vector.
     */
    public double distanceToSqr(Vec3 vec) {
        double d = vec.x - this.x;
        double e = vec.y - this.y;
        double f = vec.z - this.z;
        return d * d + e * e + f * f;
    }

    public double distanceToSqr(double x, double y, double z) {
        double d = x - this.x;
        double e = y - this.y;
        double f = z - this.z;
        return d * d + e * e + f * f;
    }

    public boolean closerThan(Vec3 pos, double horizontalDistance, double verticalDistance) {
        double d = pos.x() - this.x;
        double e = pos.y() - this.y;
        double f = pos.z() - this.z;
        return Mth.lengthSquared(d, f) < Mth.square(horizontalDistance) && Math.abs(e) < verticalDistance;
    }

    public Vec3 scale(double factor) {
        return this.multiply(factor, factor, factor);
    }

    public Vec3 reverse() {
        return this.scale(-1.0);
    }

    public Vec3 multiply(Vec3 vec) {
        return this.multiply(vec.x, vec.y, vec.z);
    }

    public Vec3 multiply(double factorX, double factorY, double factorZ) {
        return new Vec3(this.x * factorX, this.y * factorY, this.z * factorZ);
    }

    public Vec3 offsetRandom(RandomSource random, float factor) {
        return this.add((random.nextFloat() - 0.5F) * factor, (random.nextFloat() - 0.5F) * factor, (random.nextFloat() - 0.5F) * factor);
    }

    public double length() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    public double lengthSqr() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public double horizontalDistance() {
        return Math.sqrt(this.x * this.x + this.z * this.z);
    }

    public double horizontalDistanceSqr() {
        return this.x * this.x + this.z * this.z;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof Vec3 vec3)) {
            return false;
        } else if (Double.compare(vec3.x, this.x) != 0) {
            return false;
        } else {
            return Double.compare(vec3.y, this.y) != 0 ? false : Double.compare(vec3.z, this.z) == 0;
        }
    }

    @Override
    public int hashCode() {
        long l = Double.doubleToLongBits(this.x);
        int i = (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.y);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.z);
        return 31 * i + (int)(l ^ l >>> 32);
    }

    @Override
    public String toString() {
        return "(" + this.x + ", " + this.y + ", " + this.z + ")";
    }

    /**
     * Lerps between this vector and the given vector.
     * @see net.minecraft.util.Mth#lerp(double, double, double)
     */
    public Vec3 lerp(Vec3 to, double delta) {
        return new Vec3(Mth.lerp(delta, this.x, to.x), Mth.lerp(delta, this.y, to.y), Mth.lerp(delta, this.z, to.z));
    }

    public Vec3 xRot(float pitch) {
        float f = Mth.cos(pitch);
        float g = Mth.sin(pitch);
        double d = this.x;
        double e = this.y * f + this.z * g;
        double h = this.z * f - this.y * g;
        return new Vec3(d, e, h);
    }

    public Vec3 yRot(float yaw) {
        float f = Mth.cos(yaw);
        float g = Mth.sin(yaw);
        double d = this.x * f + this.z * g;
        double e = this.y;
        double h = this.z * f - this.x * g;
        return new Vec3(d, e, h);
    }

    public Vec3 zRot(float roll) {
        float f = Mth.cos(roll);
        float g = Mth.sin(roll);
        double d = this.x * f + this.y * g;
        double e = this.y * f - this.x * g;
        double h = this.z;
        return new Vec3(d, e, h);
    }

    /**
     * Returns a {@link net.minecraft.world.phys.Vec3} from the given pitch and yaw degrees as {@link net.minecraft.world.phys.Vec2}.
     */
    public static Vec3 directionFromRotation(Vec2 vec) {
        return directionFromRotation(vec.x, vec.y);
    }

    /**
     * Returns a {@link net.minecraft.world.phys.Vec3} from the given pitch and yaw degrees.
     */
    public static Vec3 directionFromRotation(float pitch, float yaw) {
        float f = Mth.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float g = Mth.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float h = -Mth.cos(-pitch * (float) (Math.PI / 180.0));
        float i = Mth.sin(-pitch * (float) (Math.PI / 180.0));
        return new Vec3(g * h, i, f * h);
    }

    public Vec3 align(EnumSet<Direction.Axis> axes) {
        double d = axes.contains(Direction.Axis.X) ? Mth.floor(this.x) : this.x;
        double e = axes.contains(Direction.Axis.Y) ? Mth.floor(this.y) : this.y;
        double f = axes.contains(Direction.Axis.Z) ? Mth.floor(this.z) : this.z;
        return new Vec3(d, e, f);
    }

    public double get(Direction.Axis axis) {
        return axis.choose(this.x, this.y, this.z);
    }

    public Vec3 with(Direction.Axis axis, double length) {
        double d = axis == Direction.Axis.X ? length : this.x;
        double e = axis == Direction.Axis.Y ? length : this.y;
        double f = axis == Direction.Axis.Z ? length : this.z;
        return new Vec3(d, e, f);
    }

    public Vec3 relative(Direction direction, double length) {
        Vec3i vec3i = direction.getNormal();
        return new Vec3(this.x + length * vec3i.getX(), this.y + length * vec3i.getY(), this.z + length * vec3i.getZ());
    }

    @Override
    public final double x() {
        return this.x;
    }

    @Override
    public final double y() {
        return this.y;
    }

    @Override
    public final double z() {
        return this.z;
    }

    public Vector3f toVector3f() {
        return new Vector3f((float)this.x, (float)this.y, (float)this.z);
    }
}
