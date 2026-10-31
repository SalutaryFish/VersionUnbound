package net.minecraft.client.renderer.block.model;

import com.mojang.math.Transformation;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockMath;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class FaceBakery {
    public static final int VERTEX_INT_SIZE = 8;
    private static final float RESCALE_22_5 = 1.0F / (float)Math.cos((float) (Math.PI / 8)) - 1.0F;
    private static final float RESCALE_45 = 1.0F / (float)Math.cos((float) (Math.PI / 4)) - 1.0F;
    public static final int VERTEX_COUNT = 4;
    private static final int COLOR_INDEX = 3;
    public static final int UV_INDEX = 4;

    public BakedQuad bakeQuad(
        Vector3f posFrom,
        Vector3f posTo,
        BlockElementFace face,
        TextureAtlasSprite sprite,
        Direction facing,
        ModelState transform,
        @Nullable BlockElementRotation rotation,
        boolean shade
    ) {
        BlockFaceUV blockFaceUV = face.uv();
        if (transform.isUvLocked()) {
            blockFaceUV = recomputeUVs(face.uv(), facing, transform.getRotation());
        }

        float[] fs = new float[blockFaceUV.uvs.length];
        System.arraycopy(blockFaceUV.uvs, 0, fs, 0, fs.length);
        float f = sprite.uvShrinkRatio();
        float g = (blockFaceUV.uvs[0] + blockFaceUV.uvs[0] + blockFaceUV.uvs[2] + blockFaceUV.uvs[2]) / 4.0F;
        float h = (blockFaceUV.uvs[1] + blockFaceUV.uvs[1] + blockFaceUV.uvs[3] + blockFaceUV.uvs[3]) / 4.0F;
        blockFaceUV.uvs[0] = Mth.lerp(f, blockFaceUV.uvs[0], g);
        blockFaceUV.uvs[2] = Mth.lerp(f, blockFaceUV.uvs[2], g);
        blockFaceUV.uvs[1] = Mth.lerp(f, blockFaceUV.uvs[1], h);
        blockFaceUV.uvs[3] = Mth.lerp(f, blockFaceUV.uvs[3], h);
        int[] is = this.makeVertices(blockFaceUV, sprite, facing, this.setupShape(posFrom, posTo), transform.getRotation(), rotation, shade);
        Direction direction = calculateFacing(is);
        System.arraycopy(fs, 0, blockFaceUV.uvs, 0, fs.length);
        if (rotation == null) {
            this.recalculateWinding(is, direction);
        }

        return new BakedQuad(is, face.tintIndex(), direction, sprite, shade);
    }

    public static BlockFaceUV recomputeUVs(BlockFaceUV uv, Direction facing, Transformation rotation) {
        Matrix4f matrix4f = BlockMath.getUVLockTransform(rotation, facing).getMatrix();
        float f = uv.getU(uv.getReverseIndex(0));
        float g = uv.getV(uv.getReverseIndex(0));
        Vector4f vector4f = matrix4f.transform(new Vector4f(f / 16.0F, g / 16.0F, 0.0F, 1.0F));
        float h = 16.0F * vector4f.x();
        float i = 16.0F * vector4f.y();
        float j = uv.getU(uv.getReverseIndex(2));
        float k = uv.getV(uv.getReverseIndex(2));
        Vector4f vector4f2 = matrix4f.transform(new Vector4f(j / 16.0F, k / 16.0F, 0.0F, 1.0F));
        float l = 16.0F * vector4f2.x();
        float m = 16.0F * vector4f2.y();
        float n;
        float o;
        if (Math.signum(j - f) == Math.signum(l - h)) {
            n = h;
            o = l;
        } else {
            n = l;
            o = h;
        }

        float p;
        float q;
        if (Math.signum(k - g) == Math.signum(m - i)) {
            p = i;
            q = m;
        } else {
            p = m;
            q = i;
        }

        float r = (float)Math.toRadians(uv.rotation);
        Matrix3f matrix3f = new Matrix3f(matrix4f);
        Vector3f vector3f = matrix3f.transform(new Vector3f(Mth.cos(r), Mth.sin(r), 0.0F));
        int s = Math.floorMod(-((int)Math.round(Math.toDegrees(Math.atan2(vector3f.y(), vector3f.x())) / 90.0)) * 90, 360);
        return new BlockFaceUV(new float[]{n, p, o, q}, s);
    }

    private int[] makeVertices(
        BlockFaceUV uvs,
        TextureAtlasSprite sprite,
        Direction orientation,
        float[] posDiv16,
        Transformation rotation,
        @Nullable BlockElementRotation partRotation,
        boolean shade
    ) {
        int[] is = new int[32];

        for (int i = 0; i < 4; i++) {
            this.bakeVertex(is, i, orientation, uvs, posDiv16, sprite, rotation, partRotation, shade);
        }

        return is;
    }

    private float[] setupShape(Vector3f min, Vector3f max) {
        float[] fs = new float[Direction.values().length];
        fs[FaceInfo.Constants.MIN_X] = min.x() / 16.0F;
        fs[FaceInfo.Constants.MIN_Y] = min.y() / 16.0F;
        fs[FaceInfo.Constants.MIN_Z] = min.z() / 16.0F;
        fs[FaceInfo.Constants.MAX_X] = max.x() / 16.0F;
        fs[FaceInfo.Constants.MAX_Y] = max.y() / 16.0F;
        fs[FaceInfo.Constants.MAX_Z] = max.z() / 16.0F;
        return fs;
    }

    private void bakeVertex(
        int[] vertexData,
        int vertexIndex,
        Direction facing,
        BlockFaceUV blockFaceUV,
        float[] posDiv16,
        TextureAtlasSprite sprite,
        Transformation rotation,
        @Nullable BlockElementRotation partRotation,
        boolean shade
    ) {
        FaceInfo.VertexInfo vertexInfo = FaceInfo.fromFacing(facing).getVertexInfo(vertexIndex);
        Vector3f vector3f = new Vector3f(posDiv16[vertexInfo.xFace], posDiv16[vertexInfo.yFace], posDiv16[vertexInfo.zFace]);
        this.applyElementRotation(vector3f, partRotation);
        this.applyModelRotation(vector3f, rotation);
        this.fillVertex(vertexData, vertexIndex, vector3f, sprite, blockFaceUV);
    }

    private void fillVertex(int[] vertexData, int vertexIndex, Vector3f vector, TextureAtlasSprite sprite, BlockFaceUV blockFaceUV) {
        int i = vertexIndex * 8;
        vertexData[i] = Float.floatToRawIntBits(vector.x());
        vertexData[i + 1] = Float.floatToRawIntBits(vector.y());
        vertexData[i + 2] = Float.floatToRawIntBits(vector.z());
        vertexData[i + 3] = -1;
        vertexData[i + 4] = Float.floatToRawIntBits(sprite.getU(blockFaceUV.getU(vertexIndex) / 16.0F));
        vertexData[i + 4 + 1] = Float.floatToRawIntBits(sprite.getV(blockFaceUV.getV(vertexIndex) / 16.0F));
    }

    private void applyElementRotation(Vector3f vec, @Nullable BlockElementRotation partRotation) {
        if (partRotation != null) {
            Vector3f vector3f;
            Vector3f vector3f2;
            switch (partRotation.axis()) {
                case X:
                    vector3f = new Vector3f(1.0F, 0.0F, 0.0F);
                    vector3f2 = new Vector3f(0.0F, 1.0F, 1.0F);
                    break;
                case Y:
                    vector3f = new Vector3f(0.0F, 1.0F, 0.0F);
                    vector3f2 = new Vector3f(1.0F, 0.0F, 1.0F);
                    break;
                case Z:
                    vector3f = new Vector3f(0.0F, 0.0F, 1.0F);
                    vector3f2 = new Vector3f(1.0F, 1.0F, 0.0F);
                    break;
                default:
                    throw new IllegalArgumentException("There are only 3 axes");
            }

            Quaternionf quaternionf = new Quaternionf().rotationAxis(partRotation.angle() * (float) (Math.PI / 180.0), vector3f);
            if (partRotation.rescale()) {
                if (Math.abs(partRotation.angle()) == 22.5F) {
                    vector3f2.mul(RESCALE_22_5);
                } else {
                    vector3f2.mul(RESCALE_45);
                }

                vector3f2.add(1.0F, 1.0F, 1.0F);
            } else {
                vector3f2.set(1.0F, 1.0F, 1.0F);
            }

            this.rotateVertexBy(vec, new Vector3f(partRotation.origin()), new Matrix4f().rotation(quaternionf), vector3f2);
        }
    }

    public void applyModelRotation(Vector3f pos, Transformation transform) {
        if (transform != Transformation.identity()) {
            this.rotateVertexBy(pos, new Vector3f(0.5F, 0.5F, 0.5F), transform.getMatrix(), new Vector3f(1.0F, 1.0F, 1.0F));
        }
    }

    private void rotateVertexBy(Vector3f pos, Vector3f origin, Matrix4f transform, Vector3f scale) {
        Vector4f vector4f = transform.transform(new Vector4f(pos.x() - origin.x(), pos.y() - origin.y(), pos.z() - origin.z(), 1.0F));
        vector4f.mul(new Vector4f(scale, 1.0F));
        pos.set(vector4f.x() + origin.x(), vector4f.y() + origin.y(), vector4f.z() + origin.z());
    }

    public static Direction calculateFacing(int[] faceData) {
        Vector3f vector3f = new Vector3f(Float.intBitsToFloat(faceData[0]), Float.intBitsToFloat(faceData[1]), Float.intBitsToFloat(faceData[2]));
        Vector3f vector3f2 = new Vector3f(Float.intBitsToFloat(faceData[8]), Float.intBitsToFloat(faceData[9]), Float.intBitsToFloat(faceData[10]));
        Vector3f vector3f3 = new Vector3f(Float.intBitsToFloat(faceData[16]), Float.intBitsToFloat(faceData[17]), Float.intBitsToFloat(faceData[18]));
        Vector3f vector3f4 = new Vector3f(vector3f).sub(vector3f2);
        Vector3f vector3f5 = new Vector3f(vector3f3).sub(vector3f2);
        Vector3f vector3f6 = new Vector3f(vector3f5).cross(vector3f4).normalize();
        if (!vector3f6.isFinite()) {
            return Direction.UP;
        }

        Direction direction = null;
        float f = 0.0F;

        for (Direction direction2 : Direction.values()) {
            Vec3i vec3i = direction2.getNormal();
            Vector3f vector3f7 = new Vector3f(vec3i.getX(), vec3i.getY(), vec3i.getZ());
            float g = vector3f6.dot(vector3f7);
            if (g >= 0.0F && g > f) {
                f = g;
                direction = direction2;
            }
        }

        return direction == null ? Direction.UP : direction;
    }

    private void recalculateWinding(int[] vertices, Direction direction) {
        int[] is = new int[vertices.length];
        System.arraycopy(vertices, 0, is, 0, vertices.length);
        float[] fs = new float[Direction.values().length];
        fs[FaceInfo.Constants.MIN_X] = 999.0F;
        fs[FaceInfo.Constants.MIN_Y] = 999.0F;
        fs[FaceInfo.Constants.MIN_Z] = 999.0F;
        fs[FaceInfo.Constants.MAX_X] = -999.0F;
        fs[FaceInfo.Constants.MAX_Y] = -999.0F;
        fs[FaceInfo.Constants.MAX_Z] = -999.0F;

        for (int i = 0; i < 4; i++) {
            int j = 8 * i;
            float f = Float.intBitsToFloat(is[j]);
            float g = Float.intBitsToFloat(is[j + 1]);
            float h = Float.intBitsToFloat(is[j + 2]);
            if (f < fs[FaceInfo.Constants.MIN_X]) {
                fs[FaceInfo.Constants.MIN_X] = f;
            }

            if (g < fs[FaceInfo.Constants.MIN_Y]) {
                fs[FaceInfo.Constants.MIN_Y] = g;
            }

            if (h < fs[FaceInfo.Constants.MIN_Z]) {
                fs[FaceInfo.Constants.MIN_Z] = h;
            }

            if (f > fs[FaceInfo.Constants.MAX_X]) {
                fs[FaceInfo.Constants.MAX_X] = f;
            }

            if (g > fs[FaceInfo.Constants.MAX_Y]) {
                fs[FaceInfo.Constants.MAX_Y] = g;
            }

            if (h > fs[FaceInfo.Constants.MAX_Z]) {
                fs[FaceInfo.Constants.MAX_Z] = h;
            }
        }

        FaceInfo faceInfo = FaceInfo.fromFacing(direction);

        for (int j = 0; j < 4; j++) {
            int k = 8 * j;
            FaceInfo.VertexInfo vertexInfo = faceInfo.getVertexInfo(j);
            float h = fs[vertexInfo.xFace];
            float l = fs[vertexInfo.yFace];
            float m = fs[vertexInfo.zFace];
            vertices[k] = Float.floatToRawIntBits(h);
            vertices[k + 1] = Float.floatToRawIntBits(l);
            vertices[k + 2] = Float.floatToRawIntBits(m);

            for (int n = 0; n < 4; n++) {
                int o = 8 * n;
                float p = Float.intBitsToFloat(is[o]);
                float q = Float.intBitsToFloat(is[o + 1]);
                float r = Float.intBitsToFloat(is[o + 2]);
                if (Mth.equal(h, p) && Mth.equal(l, q) && Mth.equal(m, r)) {
                    vertices[k + 4] = is[o + 4];
                    vertices[k + 4 + 1] = is[o + 4 + 1];
                }
            }
        }
    }
}
