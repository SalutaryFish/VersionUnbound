package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.phys.Vec3;

public class BeaconRenderer implements BlockEntityRenderer<BeaconBlockEntity> {
    public static final ResourceLocation BEAM_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
    public static final int MAX_RENDER_Y = 1024;

    public BeaconRenderer(BlockEntityRendererProvider.Context context) {
    }

    public void render(
        BeaconBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
    ) {
        long l = blockEntity.getLevel().getGameTime();
        List<BeaconBlockEntity.BeaconBeamSection> list = blockEntity.getBeamSections();
        int i = 0;

        for (int j = 0; j < list.size(); j++) {
            BeaconBlockEntity.BeaconBeamSection beaconBeamSection = list.get(j);
            renderBeaconBeam(
                poseStack, bufferSource, partialTick, l, i, j == list.size() - 1 ? 1024 : beaconBeamSection.getHeight(), beaconBeamSection.getColor()
            );
            i += beaconBeamSection.getHeight();
        }
    }

    private static void renderBeaconBeam(
        PoseStack poseStack, MultiBufferSource bufferSource, float partialTick, long gameTime, int yOffset, int height, int color
    ) {
        renderBeaconBeam(poseStack, bufferSource, BEAM_LOCATION, partialTick, 1.0F, gameTime, yOffset, height, color, 0.2F, 0.25F);
    }

    public static void renderBeaconBeam(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        ResourceLocation beamLocation,
        float partialTick,
        float textureScale,
        long gameTime,
        int yOffset,
        int height,
        int color,
        float beamRadius,
        float glowRadius
    ) {
        int i = yOffset + height;
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        float f = Math.floorMod(gameTime, 40) + partialTick;
        float g = height < 0 ? f : -f;
        float h = Mth.frac(g * 0.2F - Mth.floor(g * 0.1F));
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(f * 2.25F - 45.0F));
        float j = 0.0F;
        float k = beamRadius;
        float l = beamRadius;
        float m = 0.0F;
        float n = -beamRadius;
        float o = 0.0F;
        float p = 0.0F;
        float q = -beamRadius;
        float r = 0.0F;
        float s = 1.0F;
        float t = -1.0F + h;
        float u = height * textureScale * (0.5F / beamRadius) + t;
        renderPart(
            poseStack,
            bufferSource.getBuffer(RenderType.beaconBeam(beamLocation, false)),
            color,
            yOffset,
            i,
            0.0F,
            k,
            l,
            0.0F,
            n,
            0.0F,
            0.0F,
            q,
            0.0F,
            1.0F,
            u,
            t
        );
        poseStack.popPose();
        j = -glowRadius;
        k = -glowRadius;
        l = glowRadius;
        m = -glowRadius;
        n = -glowRadius;
        o = glowRadius;
        p = glowRadius;
        q = glowRadius;
        r = 0.0F;
        s = 1.0F;
        t = -1.0F + h;
        u = height * textureScale + t;
        renderPart(
            poseStack,
            bufferSource.getBuffer(RenderType.beaconBeam(beamLocation, true)),
            FastColor.ARGB32.color(32, color),
            yOffset,
            i,
            j,
            k,
            l,
            m,
            n,
            o,
            p,
            q,
            0.0F,
            1.0F,
            u,
            t
        );
        poseStack.popPose();
    }

    private static void renderPart(
        PoseStack poseStack,
        VertexConsumer consumer,
        int color,
        int minY,
        int maxY,
        float x1,
        float z1,
        float x2,
        float z2,
        float x3,
        float z3,
        float x4,
        float z4,
        float minU,
        float maxU,
        float minV,
        float maxV
    ) {
        PoseStack.Pose pose = poseStack.last();
        renderQuad(pose, consumer, color, minY, maxY, x1, z1, x2, z2, minU, maxU, minV, maxV);
        renderQuad(pose, consumer, color, minY, maxY, x4, z4, x3, z3, minU, maxU, minV, maxV);
        renderQuad(pose, consumer, color, minY, maxY, x2, z2, x4, z4, minU, maxU, minV, maxV);
        renderQuad(pose, consumer, color, minY, maxY, x3, z3, x1, z1, minU, maxU, minV, maxV);
    }

    private static void renderQuad(
        PoseStack.Pose pose,
        VertexConsumer consumer,
        int color,
        int minY,
        int maxY,
        float minX,
        float minZ,
        float maxX,
        float maxZ,
        float minU,
        float maxU,
        float minV,
        float maxV
    ) {
        addVertex(pose, consumer, color, maxY, minX, minZ, maxU, minV);
        addVertex(pose, consumer, color, minY, minX, minZ, maxU, maxV);
        addVertex(pose, consumer, color, minY, maxX, maxZ, minU, maxV);
        addVertex(pose, consumer, color, maxY, maxX, maxZ, minU, minV);
    }

    private static void addVertex(PoseStack.Pose pose, VertexConsumer consumer, int color, int y, float x, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    public boolean shouldRenderOffScreen(BeaconBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    public boolean shouldRender(BeaconBlockEntity blockEntity, Vec3 cameraPos) {
        return Vec3.atCenterOf(blockEntity.getBlockPos()).multiply(1.0, 0.0, 1.0).closerThan(cameraPos.multiply(1.0, 0.0, 1.0), this.getViewDistance());
    }
}
