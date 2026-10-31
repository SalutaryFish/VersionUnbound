package net.minecraft.client.renderer.culling;

import net.minecraft.world.phys.AABB;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class Frustum {
    public static final int OFFSET_STEP = 4;
    private final FrustumIntersection intersection = new FrustumIntersection();
    private final Matrix4f matrix = new Matrix4f();
    private Vector4f viewVector;
    private double camX;
    private double camY;
    private double camZ;

    public Frustum(Matrix4f frustum, Matrix4f projection) {
        this.calculateFrustum(frustum, projection);
    }

    public Frustum(Frustum other) {
        this.intersection.set(other.matrix);
        this.matrix.set(other.matrix);
        this.camX = other.camX;
        this.camY = other.camY;
        this.camZ = other.camZ;
        this.viewVector = other.viewVector;
    }

    public Frustum offsetToFullyIncludeCameraCube(int offset) {
        double d = Math.floor(this.camX / offset) * offset;
        double e = Math.floor(this.camY / offset) * offset;
        double f = Math.floor(this.camZ / offset) * offset;
        double g = Math.ceil(this.camX / offset) * offset;
        double h = Math.ceil(this.camY / offset) * offset;

        for (double i = Math.ceil(this.camZ / offset) * offset;
            this.intersection
                    .intersectAab(
                        (float)(d - this.camX),
                        (float)(e - this.camY),
                        (float)(f - this.camZ),
                        (float)(g - this.camX),
                        (float)(h - this.camY),
                        (float)(i - this.camZ)
                    )
                != -2;
            this.camZ = this.camZ - this.viewVector.z() * 4.0F
        ) {
            this.camX = this.camX - this.viewVector.x() * 4.0F;
            this.camY = this.camY - this.viewVector.y() * 4.0F;
        }

        return this;
    }

    public void prepare(double camX, double camY, double camZ) {
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
    }

    private void calculateFrustum(Matrix4f frustum, Matrix4f projection) {
        projection.mul(frustum, this.matrix);
        this.intersection.set(this.matrix);
        this.viewVector = this.matrix.transformTranspose(new Vector4f(0.0F, 0.0F, 1.0F, 0.0F));
    }

    public boolean isVisible(AABB aabb) {
        return this.cubeInFrustum(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    private boolean cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        float f = (float)(minX - this.camX);
        float g = (float)(minY - this.camY);
        float h = (float)(minZ - this.camZ);
        float i = (float)(maxX - this.camX);
        float j = (float)(maxY - this.camY);
        float k = (float)(maxZ - this.camZ);
        return this.intersection.testAab(f, g, h, i, j, k);
    }
}
