package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public abstract class EntityRenderer<T extends Entity> {
    protected static final float NAMETAG_SCALE = 0.025F;
    public static final int LEASH_RENDER_STEPS = 24;
    protected final EntityRenderDispatcher entityRenderDispatcher;
    private final Font font;
    protected float shadowRadius;
    protected float shadowStrength = 1.0F;

    protected EntityRenderer(EntityRendererProvider.Context context) {
        this.entityRenderDispatcher = context.getEntityRenderDispatcher();
        this.font = context.getFont();
    }

    public final int getPackedLightCoords(T entity, float partialTicks) {
        BlockPos blockPos = BlockPos.containing(entity.getLightProbePosition(partialTicks));
        return LightTexture.pack(this.getBlockLightLevel(entity, blockPos), this.getSkyLightLevel(entity, blockPos));
    }

    protected int getSkyLightLevel(T entity, BlockPos pos) {
        return entity.level().getBrightness(LightLayer.SKY, pos);
    }

    protected int getBlockLightLevel(T entity, BlockPos pos) {
        return entity.isOnFire() ? 15 : entity.level().getBrightness(LightLayer.BLOCK, pos);
    }

    public boolean shouldRender(T livingEntity, Frustum camera, double camX, double camY, double camZ) {
        if (!livingEntity.shouldRender(camX, camY, camZ)) {
            return false;
        }

        if (livingEntity.noCulling) {
            return true;
        }

        AABB aABB = livingEntity.getBoundingBoxForCulling().inflate(0.5);
        if (aABB.hasNaN() || aABB.getSize() == 0.0) {
            aABB = new AABB(
                livingEntity.getX() - 2.0,
                livingEntity.getY() - 2.0,
                livingEntity.getZ() - 2.0,
                livingEntity.getX() + 2.0,
                livingEntity.getY() + 2.0,
                livingEntity.getZ() + 2.0
            );
        }

        if (camera.isVisible(aABB)) {
            return true;
        }

        if (livingEntity instanceof Leashable leashable) {
            Entity entity = leashable.getLeashHolder();
            if (entity != null) {
                return camera.isVisible(entity.getBoundingBoxForCulling());
            }
        }

        return false;
    }

    public Vec3 getRenderOffset(T entity, float partialTicks) {
        return Vec3.ZERO;
    }

    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (entity instanceof Leashable leashable) {
            Entity entity2 = leashable.getLeashHolder();
            if (entity2 != null) {
                this.renderLeash(entity, partialTick, poseStack, bufferSource, entity2);
            }
        }

        if (this.shouldShowName(entity)) {
            this.renderNameTag(entity, entity.getDisplayName(), poseStack, bufferSource, packedLight, partialTick);
        }
    }

    private <E extends Entity> void renderLeash(T entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, E leashHolder) {
        poseStack.pushPose();
        Vec3 vec3 = leashHolder.getRopeHoldPosition(partialTick);
        double d = entity.getPreciseBodyRotation(partialTick) * (float) (Math.PI / 180.0) + (Math.PI / 2);
        Vec3 vec32 = entity.getLeashOffset(partialTick);
        double e = Math.cos(d) * vec32.z + Math.sin(d) * vec32.x;
        double f = Math.sin(d) * vec32.z - Math.cos(d) * vec32.x;
        double g = Mth.lerp(partialTick, entity.xo, entity.getX()) + e;
        double h = Mth.lerp(partialTick, entity.yo, entity.getY()) + vec32.y;
        double i = Mth.lerp(partialTick, entity.zo, entity.getZ()) + f;
        poseStack.translate(e, vec32.y, f);
        float j = (float)(vec3.x - g);
        float k = (float)(vec3.y - h);
        float l = (float)(vec3.z - i);
        float m = 0.025F;
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.leash());
        Matrix4f matrix4f = poseStack.last().pose();
        float n = Mth.invSqrt(j * j + l * l) * 0.025F / 2.0F;
        float o = l * n;
        float p = j * n;
        BlockPos blockPos = BlockPos.containing(entity.getEyePosition(partialTick));
        BlockPos blockPos2 = BlockPos.containing(leashHolder.getEyePosition(partialTick));
        int q = this.getBlockLightLevel(entity, blockPos);
        int r = this.entityRenderDispatcher.getRenderer(leashHolder).getBlockLightLevel(leashHolder, blockPos2);
        int s = entity.level().getBrightness(LightLayer.SKY, blockPos);
        int t = entity.level().getBrightness(LightLayer.SKY, blockPos2);

        for (int u = 0; u <= 24; u++) {
            addVertexPair(vertexConsumer, matrix4f, j, k, l, q, r, s, t, 0.025F, 0.025F, o, p, u, false);
        }

        for (int u = 24; u >= 0; u--) {
            addVertexPair(vertexConsumer, matrix4f, j, k, l, q, r, s, t, 0.025F, 0.0F, o, p, u, true);
        }

        poseStack.popPose();
    }

    private static void addVertexPair(
        VertexConsumer buffer,
        Matrix4f pose,
        float startX,
        float startY,
        float startZ,
        int entityBlockLight,
        int holderBlockLight,
        int entitySkyLight,
        int holderSkyLight,
        float yOffset,
        float dy,
        float dx,
        float dz,
        int index,
        boolean reverse
    ) {
        float f = index / 24.0F;
        int i = (int)Mth.lerp(f, entityBlockLight, holderBlockLight);
        int j = (int)Mth.lerp(f, entitySkyLight, holderSkyLight);
        int k = LightTexture.pack(i, j);
        float g = index % 2 == (reverse ? 1 : 0) ? 0.7F : 1.0F;
        float h = 0.5F * g;
        float l = 0.4F * g;
        float m = 0.3F * g;
        float n = startX * f;
        float o = startY > 0.0F ? startY * f * f : startY - startY * (1.0F - f) * (1.0F - f);
        float p = startZ * f;
        buffer.addVertex(pose, n - dx, o + dy, p + dz).setColor(h, l, m, 1.0F).setLight(k);
        buffer.addVertex(pose, n + dx, o + yOffset - dy, p - dz).setColor(h, l, m, 1.0F).setLight(k);
    }

    protected boolean shouldShowName(T entity) {
        return entity.shouldShowName() || entity.hasCustomName() && entity == this.entityRenderDispatcher.crosshairPickEntity;
    }

    /**
     * Returns the location of an entity's texture.
     */
    public abstract ResourceLocation getTextureLocation(T var1);

    public Font getFont() {
        return this.font;
    }

    protected void renderNameTag(T entity, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
        double d = this.entityRenderDispatcher.distanceToSqr(entity);
        if (!(d > 4096.0)) {
            Vec3 vec3 = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getViewYRot(partialTick));
            if (vec3 != null) {
                boolean bl = !entity.isDiscrete();
                int i = "deadmau5".equals(displayName.getString()) ? -10 : 0;
                poseStack.pushPose();
                poseStack.translate(vec3.x, vec3.y + 0.5, vec3.z);
                poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
                poseStack.scale(0.025F, -0.025F, 0.025F);
                Matrix4f matrix4f = poseStack.last().pose();
                float f = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
                int j = (int)(f * 255.0F) << 24;
                Font font = this.getFont();
                float g = -font.width(displayName) / 2;
                font.drawInBatch(
                    displayName, g, i, 553648127, false, matrix4f, bufferSource, bl ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, j, packedLight
                );
                if (bl) {
                    font.drawInBatch(displayName, g, i, -1, false, matrix4f, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
                }

                poseStack.popPose();
            }
        }
    }

    protected float getShadowRadius(T entity) {
        return this.shadowRadius;
    }
}
