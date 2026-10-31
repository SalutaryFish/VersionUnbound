package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.GuardianModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GuardianRenderer extends MobRenderer<Guardian, GuardianModel> {
    private static final ResourceLocation GUARDIAN_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/guardian.png");
    private static final ResourceLocation GUARDIAN_BEAM_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/guardian_beam.png");
    private static final RenderType BEAM_RENDER_TYPE = RenderType.entityCutoutNoCull(GUARDIAN_BEAM_LOCATION);

    public GuardianRenderer(EntityRendererProvider.Context context) {
        this(context, 0.5F, ModelLayers.GUARDIAN);
    }

    protected GuardianRenderer(EntityRendererProvider.Context context, float shadowRadius, ModelLayerLocation layer) {
        super(context, new GuardianModel(context.bakeLayer(layer)), shadowRadius);
    }

    public boolean shouldRender(Guardian livingEntity, Frustum camera, double camX, double camY, double camZ) {
        if (super.shouldRender(livingEntity, camera, camX, camY, camZ)) {
            return true;
        }

        if (livingEntity.hasActiveAttackTarget()) {
            LivingEntity livingEntity2 = livingEntity.getActiveAttackTarget();
            if (livingEntity2 != null) {
                Vec3 vec3 = this.getPosition(livingEntity2, livingEntity2.getBbHeight() * 0.5, 1.0F);
                Vec3 vec32 = this.getPosition(livingEntity, livingEntity.getEyeHeight(), 1.0F);
                return camera.isVisible(new AABB(vec32.x, vec32.y, vec32.z, vec3.x, vec3.y, vec3.z));
            }
        }

        return false;
    }

    private Vec3 getPosition(LivingEntity livingEntity, double yOffset, float partialTick) {
        double d = Mth.lerp(partialTick, livingEntity.xOld, livingEntity.getX());
        double e = Mth.lerp(partialTick, livingEntity.yOld, livingEntity.getY()) + yOffset;
        double f = Mth.lerp(partialTick, livingEntity.zOld, livingEntity.getZ());
        return new Vec3(d, e, f);
    }

    public void render(Guardian entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        LivingEntity livingEntity = entity.getActiveAttackTarget();
        if (livingEntity != null) {
            float f = entity.getAttackAnimationScale(partialTicks);
            float g = entity.getClientSideAttackTime() + partialTicks;
            float h = g * 0.5F % 1.0F;
            float i = entity.getEyeHeight();
            poseStack.pushPose();
            poseStack.translate(0.0F, i, 0.0F);
            Vec3 vec3 = this.getPosition(livingEntity, livingEntity.getBbHeight() * 0.5, partialTicks);
            Vec3 vec32 = this.getPosition(entity, i, partialTicks);
            Vec3 vec33 = vec3.subtract(vec32);
            float j = (float)(vec33.length() + 1.0);
            vec33 = vec33.normalize();
            float k = (float)Math.acos(vec33.y);
            float l = (float)Math.atan2(vec33.z, vec33.x);
            poseStack.mulPose(Axis.YP.rotationDegrees(((float) (Math.PI / 2) - l) * (180.0F / (float)Math.PI)));
            poseStack.mulPose(Axis.XP.rotationDegrees(k * (180.0F / (float)Math.PI)));
            int m = 1;
            float n = g * 0.05F * -1.5F;
            float o = f * f;
            int p = 64 + (int)(o * 191.0F);
            int q = 32 + (int)(o * 191.0F);
            int r = 128 - (int)(o * 64.0F);
            float s = 0.2F;
            float t = 0.282F;
            float u = Mth.cos(n + (float) (Math.PI * 3.0 / 4.0)) * 0.282F;
            float v = Mth.sin(n + (float) (Math.PI * 3.0 / 4.0)) * 0.282F;
            float w = Mth.cos(n + (float) (Math.PI / 4)) * 0.282F;
            float x = Mth.sin(n + (float) (Math.PI / 4)) * 0.282F;
            float y = Mth.cos(n + ((float) Math.PI * 5.0F / 4.0F)) * 0.282F;
            float z = Mth.sin(n + ((float) Math.PI * 5.0F / 4.0F)) * 0.282F;
            float aa = Mth.cos(n + ((float) Math.PI * 7.0F / 4.0F)) * 0.282F;
            float ab = Mth.sin(n + ((float) Math.PI * 7.0F / 4.0F)) * 0.282F;
            float ac = Mth.cos(n + (float) Math.PI) * 0.2F;
            float ad = Mth.sin(n + (float) Math.PI) * 0.2F;
            float ae = Mth.cos(n + 0.0F) * 0.2F;
            float af = Mth.sin(n + 0.0F) * 0.2F;
            float ag = Mth.cos(n + (float) (Math.PI / 2)) * 0.2F;
            float ah = Mth.sin(n + (float) (Math.PI / 2)) * 0.2F;
            float ai = Mth.cos(n + (float) (Math.PI * 3.0 / 2.0)) * 0.2F;
            float aj = Mth.sin(n + (float) (Math.PI * 3.0 / 2.0)) * 0.2F;
            float ak = j;
            float al = 0.0F;
            float am = 0.4999F;
            float an = -1.0F + h;
            float ao = j * 2.5F + an;
            VertexConsumer vertexConsumer = buffer.getBuffer(BEAM_RENDER_TYPE);
            PoseStack.Pose pose = poseStack.last();
            vertex(vertexConsumer, pose, ac, ak, ad, p, q, r, 0.4999F, ao);
            vertex(vertexConsumer, pose, ac, 0.0F, ad, p, q, r, 0.4999F, an);
            vertex(vertexConsumer, pose, ae, 0.0F, af, p, q, r, 0.0F, an);
            vertex(vertexConsumer, pose, ae, ak, af, p, q, r, 0.0F, ao);
            vertex(vertexConsumer, pose, ag, ak, ah, p, q, r, 0.4999F, ao);
            vertex(vertexConsumer, pose, ag, 0.0F, ah, p, q, r, 0.4999F, an);
            vertex(vertexConsumer, pose, ai, 0.0F, aj, p, q, r, 0.0F, an);
            vertex(vertexConsumer, pose, ai, ak, aj, p, q, r, 0.0F, ao);
            float ap = 0.0F;
            if (entity.tickCount % 2 == 0) {
                ap = 0.5F;
            }

            vertex(vertexConsumer, pose, u, ak, v, p, q, r, 0.5F, ap + 0.5F);
            vertex(vertexConsumer, pose, w, ak, x, p, q, r, 1.0F, ap + 0.5F);
            vertex(vertexConsumer, pose, aa, ak, ab, p, q, r, 1.0F, ap);
            vertex(vertexConsumer, pose, y, ak, z, p, q, r, 0.5F, ap);
            poseStack.popPose();
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, int red, int green, int blue, float u, float v) {
        consumer.addVertex(pose, x, y, z)
            .setColor(red, green, blue, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Guardian entity) {
        return GUARDIAN_LOCATION;
    }
}
