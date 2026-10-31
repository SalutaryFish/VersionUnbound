package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.MinecartModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MinecartRenderer<T extends AbstractMinecart> extends EntityRenderer<T> {
    private static final ResourceLocation MINECART_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/minecart.png");
    protected final EntityModel<T> model;
    private final BlockRenderDispatcher blockRenderer;

    public MinecartRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer) {
        super(context);
        this.shadowRadius = 0.7F;
        this.model = new MinecartModel<>(context.bakeLayer(layer));
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.pushPose();
        long l = entity.getId() * 493286711L;
        l = l * l * 4392167121L + l * 98761L;
        float f = (((float)(l >> 16 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
        float g = (((float)(l >> 20 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
        float h = (((float)(l >> 24 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
        poseStack.translate(f, g, h);
        double d = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double e = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        double i = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        double j = 0.3F;
        Vec3 vec3 = entity.getPos(d, e, i);
        float k = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        if (vec3 != null) {
            Vec3 vec32 = entity.getPosOffs(d, e, i, 0.3F);
            Vec3 vec33 = entity.getPosOffs(d, e, i, -0.3F);
            if (vec32 == null) {
                vec32 = vec3;
            }

            if (vec33 == null) {
                vec33 = vec3;
            }

            poseStack.translate(vec3.x - d, (vec32.y + vec33.y) / 2.0 - e, vec3.z - i);
            Vec3 vec34 = vec33.add(-vec32.x, -vec32.y, -vec32.z);
            if (vec34.length() != 0.0) {
                vec34 = vec34.normalize();
                entityYaw = (float)(Math.atan2(vec34.z, vec34.x) * 180.0 / Math.PI);
                k = (float)(Math.atan(vec34.y) * 73.0);
            }
        }

        poseStack.translate(0.0F, 0.375F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-k));
        float m = entity.getHurtTime() - partialTicks;
        float n = entity.getDamage() - partialTicks;
        if (n < 0.0F) {
            n = 0.0F;
        }

        if (m > 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(m) * m * n / 10.0F * entity.getHurtDir()));
        }

        int o = entity.getDisplayOffset();
        BlockState blockState = entity.getDisplayBlockState();
        if (blockState.getRenderShape() != RenderShape.INVISIBLE) {
            poseStack.pushPose();
            float p = 0.75F;
            poseStack.scale(0.75F, 0.75F, 0.75F);
            poseStack.translate(-0.5F, (o - 8) / 16.0F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            this.renderMinecartContents(entity, partialTicks, blockState, poseStack, buffer, packedLight);
            poseStack.popPose();
        }

        poseStack.scale(-1.0F, -1.0F, 1.0F);
        this.model.setupAnim(entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        VertexConsumer vertexConsumer = buffer.getBuffer(this.model.renderType(this.getTextureLocation(entity)));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(T entity) {
        return MINECART_LOCATION;
    }

    protected void renderMinecartContents(T entity, float partialTicks, BlockState state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.blockRenderer.renderSingleBlock(state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
    }
}
