package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;

public class ExperienceOrbRenderer extends EntityRenderer<ExperienceOrb> {
    private static final ResourceLocation EXPERIENCE_ORB_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/experience_orb.png");
    private static final RenderType RENDER_TYPE = RenderType.itemEntityTranslucentCull(EXPERIENCE_ORB_LOCATION);

    public ExperienceOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    protected int getBlockLightLevel(ExperienceOrb entity, BlockPos pos) {
        return Mth.clamp(super.getBlockLightLevel(entity, pos) + 7, 0, 15);
    }

    public void render(ExperienceOrb entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        int i = entity.getIcon();
        float f = (i % 4 * 16 + 0) / 64.0F;
        float g = (i % 4 * 16 + 16) / 64.0F;
        float h = (i / 4 * 16 + 0) / 64.0F;
        float j = (i / 4 * 16 + 16) / 64.0F;
        float k = 1.0F;
        float l = 0.5F;
        float m = 0.25F;
        float n = 255.0F;
        float o = (entity.tickCount + partialTicks) / 2.0F;
        int p = (int)((Mth.sin(o + 0.0F) + 1.0F) * 0.5F * 255.0F);
        int q = 255;
        int r = (int)((Mth.sin(o + (float) (Math.PI * 4.0 / 3.0)) + 1.0F) * 0.1F * 255.0F);
        poseStack.translate(0.0F, 0.1F, 0.0F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        float s = 0.3F;
        poseStack.scale(0.3F, 0.3F, 0.3F);
        VertexConsumer vertexConsumer = buffer.getBuffer(RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();
        vertex(vertexConsumer, pose, -0.5F, -0.25F, p, 255, r, f, j, packedLight);
        vertex(vertexConsumer, pose, 0.5F, -0.25F, p, 255, r, g, j, packedLight);
        vertex(vertexConsumer, pose, 0.5F, 0.75F, p, 255, r, g, h, packedLight);
        vertex(vertexConsumer, pose, -0.5F, 0.75F, p, 255, r, f, h, packedLight);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, int red, int green, int blue, float u, float v, int packedLight) {
        consumer.addVertex(pose, x, y, 0.0F)
            .setColor(red, green, blue, 128)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(ExperienceOrb entity) {
        return EXPERIENCE_ORB_LOCATION;
    }
}
