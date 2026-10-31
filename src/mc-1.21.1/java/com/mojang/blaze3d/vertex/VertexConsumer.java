package com.mojang.blaze3d.vertex;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Vec3i;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

public interface VertexConsumer {
    VertexConsumer addVertex(float var1, float var2, float var3);

    VertexConsumer setColor(int var1, int var2, int var3, int var4);

    VertexConsumer setUv(float var1, float var2);

    VertexConsumer setUv1(int var1, int var2);

    VertexConsumer setUv2(int var1, int var2);

    VertexConsumer setNormal(float var1, float var2, float var3);

    default void addVertex(
        float x, float y, float z, int color, float u, float v, int packedOverlay, int packedLight, float normalX, float normalY, float normalZ
    ) {
        this.addVertex(x, y, z);
        this.setColor(color);
        this.setUv(u, v);
        this.setOverlay(packedOverlay);
        this.setLight(packedLight);
        this.setNormal(normalX, normalY, normalZ);
    }

    default VertexConsumer setColor(float red, float green, float blue, float alpha) {
        return this.setColor((int)(red * 255.0F), (int)(green * 255.0F), (int)(blue * 255.0F), (int)(alpha * 255.0F));
    }

    default VertexConsumer setColor(int color) {
        return this.setColor(FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color), FastColor.ARGB32.alpha(color));
    }

    default VertexConsumer setWhiteAlpha(int alpha) {
        return this.setColor(FastColor.ARGB32.color(alpha, -1));
    }

    default VertexConsumer setLight(int packedLight) {
        return this.setUv2(packedLight & 65535, packedLight >> 16 & 65535);
    }

    default VertexConsumer setOverlay(int packedOverlay) {
        return this.setUv1(packedOverlay & 65535, packedOverlay >> 16 & 65535);
    }

    default void putBulkData(PoseStack.Pose pose, BakedQuad quad, float red, float green, float blue, float alpha, int packedLight, int packedOverlay) {
        this.putBulkData(
            pose,
            quad,
            new float[]{1.0F, 1.0F, 1.0F, 1.0F},
            red,
            green,
            blue,
            alpha,
            new int[]{packedLight, packedLight, packedLight, packedLight},
            packedOverlay,
            false
        );
    }

    default void putBulkData(
        PoseStack.Pose pose,
        BakedQuad quad,
        float[] brightness,
        float red,
        float green,
        float blue,
        float alpha,
        int[] lightmap,
        int packedOverlay,
        boolean readAlpha
    ) {
        int[] is = quad.getVertices();
        Vec3i vec3i = quad.getDirection().getNormal();
        Matrix4f matrix4f = pose.pose();
        Vector3f vector3f = pose.transformNormal(vec3i.getX(), vec3i.getY(), vec3i.getZ(), new Vector3f());
        int i = 8;
        int j = is.length / 8;
        int k = (int)(alpha * 255.0F);

        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = memoryStack.malloc(DefaultVertexFormat.BLOCK.getVertexSize());
            IntBuffer intBuffer = byteBuffer.asIntBuffer();

            for (int l = 0; l < j; l++) {
                intBuffer.clear();
                intBuffer.put(is, l * 8, 8);
                float f = byteBuffer.getFloat(0);
                float g = byteBuffer.getFloat(4);
                float h = byteBuffer.getFloat(8);
                float p;
                float q;
                float r;
                if (readAlpha) {
                    float m = byteBuffer.get(12) & 0xFF;
                    float n = byteBuffer.get(13) & 0xFF;
                    float o = byteBuffer.get(14) & 0xFF;
                    p = m * brightness[l] * red;
                    q = n * brightness[l] * green;
                    r = o * brightness[l] * blue;
                } else {
                    p = brightness[l] * red * 255.0F;
                    q = brightness[l] * green * 255.0F;
                    r = brightness[l] * blue * 255.0F;
                }

                int s = FastColor.ARGB32.color(k, (int)p, (int)q, (int)r);
                int t = lightmap[l];
                float o = byteBuffer.getFloat(16);
                float u = byteBuffer.getFloat(20);
                Vector3f vector3f2 = matrix4f.transformPosition(f, g, h, new Vector3f());
                this.addVertex(vector3f2.x(), vector3f2.y(), vector3f2.z(), s, o, u, packedOverlay, t, vector3f.x(), vector3f.y(), vector3f.z());
            }
        }
    }

    default VertexConsumer addVertex(Vector3f pos) {
        return this.addVertex(pos.x(), pos.y(), pos.z());
    }

    default VertexConsumer addVertex(PoseStack.Pose pose, Vector3f pos) {
        return this.addVertex(pose, pos.x(), pos.y(), pos.z());
    }

    default VertexConsumer addVertex(PoseStack.Pose pose, float x, float y, float z) {
        return this.addVertex(pose.pose(), x, y, z);
    }

    default VertexConsumer addVertex(Matrix4f pose, float x, float y, float z) {
        Vector3f vector3f = pose.transformPosition(x, y, z, new Vector3f());
        return this.addVertex(vector3f.x(), vector3f.y(), vector3f.z());
    }

    default VertexConsumer setNormal(PoseStack.Pose pose, float normalX, float normalY, float normalZ) {
        Vector3f vector3f = pose.transformNormal(normalX, normalY, normalZ, new Vector3f());
        return this.setNormal(vector3f.x(), vector3f.y(), vector3f.z());
    }
}
