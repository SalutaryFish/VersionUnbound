package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public abstract class SingleQuadParticle extends Particle {
    protected float quadSize = 0.1F * (this.random.nextFloat() * 0.5F + 0.5F) * 2.0F;

    protected SingleQuadParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    protected SingleQuadParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    public SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
        return SingleQuadParticle.FacingCameraMode.LOOKAT_XYZ;
    }

    @Override
    public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
        Quaternionf quaternionf = new Quaternionf();
        this.getFacingCameraMode().setRotation(quaternionf, renderInfo, partialTicks);
        if (this.roll != 0.0F) {
            quaternionf.rotateZ(Mth.lerp(partialTicks, this.oRoll, this.roll));
        }

        this.renderRotatedQuad(buffer, renderInfo, quaternionf, partialTicks);
    }

    protected void renderRotatedQuad(VertexConsumer buffer, Camera camera, Quaternionf quaternion, float partialTicks) {
        Vec3 vec3 = camera.getPosition();
        float f = (float)(Mth.lerp(partialTicks, this.xo, this.x) - vec3.x());
        float g = (float)(Mth.lerp(partialTicks, this.yo, this.y) - vec3.y());
        float h = (float)(Mth.lerp(partialTicks, this.zo, this.z) - vec3.z());
        this.renderRotatedQuad(buffer, quaternion, f, g, h, partialTicks);
    }

    protected void renderRotatedQuad(VertexConsumer buffer, Quaternionf quaternion, float x, float y, float z, float partialTicks) {
        float f = this.getQuadSize(partialTicks);
        float g = this.getU0();
        float h = this.getU1();
        float i = this.getV0();
        float j = this.getV1();
        int k = this.getLightColor(partialTicks);
        this.renderVertex(buffer, quaternion, x, y, z, 1.0F, -1.0F, f, h, j, k);
        this.renderVertex(buffer, quaternion, x, y, z, 1.0F, 1.0F, f, h, i, k);
        this.renderVertex(buffer, quaternion, x, y, z, -1.0F, 1.0F, f, g, i, k);
        this.renderVertex(buffer, quaternion, x, y, z, -1.0F, -1.0F, f, g, j, k);
    }

    private void renderVertex(
        VertexConsumer buffer,
        Quaternionf quaternion,
        float x,
        float y,
        float z,
        float xOffset,
        float yOffset,
        float quadSize,
        float u,
        float v,
        int packedLight
    ) {
        Vector3f vector3f = new Vector3f(xOffset, yOffset, 0.0F).rotate(quaternion).mul(quadSize).add(x, y, z);
        buffer.addVertex(vector3f.x(), vector3f.y(), vector3f.z()).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(packedLight);
    }

    public float getQuadSize(float scaleFactor) {
        return this.quadSize;
    }

    @Override
    public Particle scale(float scale) {
        this.quadSize *= scale;
        return super.scale(scale);
    }

    protected abstract float getU0();

    protected abstract float getU1();

    protected abstract float getV0();

    protected abstract float getV1();

    public interface FacingCameraMode {
        SingleQuadParticle.FacingCameraMode LOOKAT_XYZ = (quaternion, camera, partialTick) -> quaternion.set(camera.rotation());
        SingleQuadParticle.FacingCameraMode LOOKAT_Y = (quaternion, camera, partialTick) -> quaternion.set(0.0F, camera.rotation().y, 0.0F, camera.rotation().w);

        void setRotation(Quaternionf var1, Camera var2, float var3);
    }
}
