package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.PaintingTextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;

public class PaintingRenderer extends EntityRenderer<Painting> {
    public PaintingRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public void render(Painting entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        PaintingVariant paintingVariant = entity.getVariant().value();
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entitySolid(this.getTextureLocation(entity)));
        PaintingTextureManager paintingTextureManager = Minecraft.getInstance().getPaintingTextures();
        this.renderPainting(
            poseStack,
            vertexConsumer,
            entity,
            paintingVariant.width(),
            paintingVariant.height(),
            paintingTextureManager.get(paintingVariant),
            paintingTextureManager.getBackSprite()
        );
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Painting entity) {
        return Minecraft.getInstance().getPaintingTextures().getBackSprite().atlasLocation();
    }

    private void renderPainting(
        PoseStack poseStack,
        VertexConsumer consumer,
        Painting painting,
        int width,
        int height,
        TextureAtlasSprite paintingSprite,
        TextureAtlasSprite backSprite
    ) {
        PoseStack.Pose pose = poseStack.last();
        float f = -width / 2.0F;
        float g = -height / 2.0F;
        float h = 0.03125F;
        float i = backSprite.getU0();
        float j = backSprite.getU1();
        float k = backSprite.getV0();
        float l = backSprite.getV1();
        float m = backSprite.getU0();
        float n = backSprite.getU1();
        float o = backSprite.getV0();
        float p = backSprite.getV(0.0625F);
        float q = backSprite.getU0();
        float r = backSprite.getU(0.0625F);
        float s = backSprite.getV0();
        float t = backSprite.getV1();
        double d = 1.0 / width;
        double e = 1.0 / height;

        for (int u = 0; u < width; u++) {
            for (int v = 0; v < height; v++) {
                float w = f + (u + 1);
                float x = f + u;
                float y = g + (v + 1);
                float z = g + v;
                int aa = painting.getBlockX();
                int ab = Mth.floor(painting.getY() + (y + z) / 2.0F);
                int ac = painting.getBlockZ();
                Direction direction = painting.getDirection();
                if (direction == Direction.NORTH) {
                    aa = Mth.floor(painting.getX() + (w + x) / 2.0F);
                }

                if (direction == Direction.WEST) {
                    ac = Mth.floor(painting.getZ() - (w + x) / 2.0F);
                }

                if (direction == Direction.SOUTH) {
                    aa = Mth.floor(painting.getX() - (w + x) / 2.0F);
                }

                if (direction == Direction.EAST) {
                    ac = Mth.floor(painting.getZ() + (w + x) / 2.0F);
                }

                int ad = LevelRenderer.getLightColor(painting.level(), new BlockPos(aa, ab, ac));
                float ae = paintingSprite.getU((float)(d * (width - u)));
                float af = paintingSprite.getU((float)(d * (width - (u + 1))));
                float ag = paintingSprite.getV((float)(e * (height - v)));
                float ah = paintingSprite.getV((float)(e * (height - (v + 1))));
                this.vertex(pose, consumer, w, z, af, ag, -0.03125F, 0, 0, -1, ad);
                this.vertex(pose, consumer, x, z, ae, ag, -0.03125F, 0, 0, -1, ad);
                this.vertex(pose, consumer, x, y, ae, ah, -0.03125F, 0, 0, -1, ad);
                this.vertex(pose, consumer, w, y, af, ah, -0.03125F, 0, 0, -1, ad);
                this.vertex(pose, consumer, w, y, j, k, 0.03125F, 0, 0, 1, ad);
                this.vertex(pose, consumer, x, y, i, k, 0.03125F, 0, 0, 1, ad);
                this.vertex(pose, consumer, x, z, i, l, 0.03125F, 0, 0, 1, ad);
                this.vertex(pose, consumer, w, z, j, l, 0.03125F, 0, 0, 1, ad);
                this.vertex(pose, consumer, w, y, m, o, -0.03125F, 0, 1, 0, ad);
                this.vertex(pose, consumer, x, y, n, o, -0.03125F, 0, 1, 0, ad);
                this.vertex(pose, consumer, x, y, n, p, 0.03125F, 0, 1, 0, ad);
                this.vertex(pose, consumer, w, y, m, p, 0.03125F, 0, 1, 0, ad);
                this.vertex(pose, consumer, w, z, m, o, 0.03125F, 0, -1, 0, ad);
                this.vertex(pose, consumer, x, z, n, o, 0.03125F, 0, -1, 0, ad);
                this.vertex(pose, consumer, x, z, n, p, -0.03125F, 0, -1, 0, ad);
                this.vertex(pose, consumer, w, z, m, p, -0.03125F, 0, -1, 0, ad);
                this.vertex(pose, consumer, w, y, r, s, 0.03125F, -1, 0, 0, ad);
                this.vertex(pose, consumer, w, z, r, t, 0.03125F, -1, 0, 0, ad);
                this.vertex(pose, consumer, w, z, q, t, -0.03125F, -1, 0, 0, ad);
                this.vertex(pose, consumer, w, y, q, s, -0.03125F, -1, 0, 0, ad);
                this.vertex(pose, consumer, x, y, r, s, -0.03125F, 1, 0, 0, ad);
                this.vertex(pose, consumer, x, z, r, t, -0.03125F, 1, 0, 0, ad);
                this.vertex(pose, consumer, x, z, q, t, 0.03125F, 1, 0, 0, ad);
                this.vertex(pose, consumer, x, y, q, s, 0.03125F, 1, 0, 0, ad);
            }
        }
    }

    private void vertex(
        PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float u, float v, float z, int normalX, int normalY, int normalZ, int packedLight
    ) {
        consumer.addVertex(pose, x, y, z)
            .setColor(-1)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(pose, normalX, normalY, normalZ);
    }
}
