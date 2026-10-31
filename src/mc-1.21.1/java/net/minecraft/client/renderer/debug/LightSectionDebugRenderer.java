package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.time.Duration;
import java.time.Instant;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class LightSectionDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final Duration REFRESH_INTERVAL = Duration.ofMillis(500L);
    private static final int RADIUS = 10;
    private static final Vector4f LIGHT_AND_BLOCKS_COLOR = new Vector4f(1.0F, 1.0F, 0.0F, 0.25F);
    private static final Vector4f LIGHT_ONLY_COLOR = new Vector4f(0.25F, 0.125F, 0.0F, 0.125F);
    private final Minecraft minecraft;
    private final LightLayer lightLayer;
    private Instant lastUpdateTime = Instant.now();
    @Nullable
    private LightSectionDebugRenderer.SectionData data;

    public LightSectionDebugRenderer(Minecraft minecraft, LightLayer lightLayer) {
        this.minecraft = minecraft;
        this.lightLayer = lightLayer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        Instant instant = Instant.now();
        if (this.data == null || Duration.between(this.lastUpdateTime, instant).compareTo(REFRESH_INTERVAL) > 0) {
            this.lastUpdateTime = instant;
            this.data = new LightSectionDebugRenderer.SectionData(
                this.minecraft.level.getLightEngine(), SectionPos.of(this.minecraft.player.blockPosition()), 10, this.lightLayer
            );
        }

        renderEdges(poseStack, this.data.lightAndBlocksShape, this.data.minPos, bufferSource, camX, camY, camZ, LIGHT_AND_BLOCKS_COLOR);
        renderEdges(poseStack, this.data.lightShape, this.data.minPos, bufferSource, camX, camY, camZ, LIGHT_ONLY_COLOR);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.debugSectionQuads());
        renderFaces(poseStack, this.data.lightAndBlocksShape, this.data.minPos, vertexConsumer, camX, camY, camZ, LIGHT_AND_BLOCKS_COLOR);
        renderFaces(poseStack, this.data.lightShape, this.data.minPos, vertexConsumer, camX, camY, camZ, LIGHT_ONLY_COLOR);
    }

    private static void renderFaces(
        PoseStack poseStack, DiscreteVoxelShape shape, SectionPos pos, VertexConsumer buffer, double camX, double camY, double camZ, Vector4f color
    ) {
        shape.forAllFaces((direction, x, y, z) -> {
            int i = x + pos.getX();
            int j = y + pos.getY();
            int k = z + pos.getZ();
            renderFace(poseStack, buffer, direction, camX, camY, camZ, i, j, k, color);
        });
    }

    private static void renderEdges(
        PoseStack poseStack, DiscreteVoxelShape shape, SectionPos pos, MultiBufferSource bufferSource, double camX, double camY, double camZ, Vector4f color
    ) {
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            int i = x1 + pos.getX();
            int j = y1 + pos.getY();
            int k = z1 + pos.getZ();
            int l = x2 + pos.getX();
            int m = y2 + pos.getY();
            int n = z2 + pos.getZ();
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.debugLineStrip(1.0));
            renderEdge(poseStack, vertexConsumer, camX, camY, camZ, i, j, k, l, m, n, color);
        }, true);
    }

    private static void renderFace(
        PoseStack poseStack, VertexConsumer buffer, Direction face, double camX, double camY, double camZ, int blockX, int blockY, int blockZ, Vector4f color
    ) {
        float f = (float)(SectionPos.sectionToBlockCoord(blockX) - camX);
        float g = (float)(SectionPos.sectionToBlockCoord(blockY) - camY);
        float h = (float)(SectionPos.sectionToBlockCoord(blockZ) - camZ);
        LevelRenderer.renderFace(poseStack, buffer, face, f, g, h, f + 16.0F, g + 16.0F, h + 16.0F, color.x(), color.y(), color.z(), color.w());
    }

    private static void renderEdge(
        PoseStack poseStack, VertexConsumer buffer, double camX, double camY, double camZ, int x1, int y1, int z1, int x2, int y2, int z2, Vector4f color
    ) {
        float f = (float)(SectionPos.sectionToBlockCoord(x1) - camX);
        float g = (float)(SectionPos.sectionToBlockCoord(y1) - camY);
        float h = (float)(SectionPos.sectionToBlockCoord(z1) - camZ);
        float i = (float)(SectionPos.sectionToBlockCoord(x2) - camX);
        float j = (float)(SectionPos.sectionToBlockCoord(y2) - camY);
        float k = (float)(SectionPos.sectionToBlockCoord(z2) - camZ);
        Matrix4f matrix4f = poseStack.last().pose();
        buffer.addVertex(matrix4f, f, g, h).setColor(color.x(), color.y(), color.z(), 1.0F);
        buffer.addVertex(matrix4f, i, j, k).setColor(color.x(), color.y(), color.z(), 1.0F);
    }

    static final class SectionData {
        final DiscreteVoxelShape lightAndBlocksShape;
        final DiscreteVoxelShape lightShape;
        final SectionPos minPos;

        SectionData(LevelLightEngine levelLightEngine, SectionPos pos, int radius, LightLayer lightLayer) {
            int i = radius * 2 + 1;
            this.lightAndBlocksShape = new BitSetDiscreteVoxelShape(i, i, i);
            this.lightShape = new BitSetDiscreteVoxelShape(i, i, i);

            for (int j = 0; j < i; j++) {
                for (int k = 0; k < i; k++) {
                    for (int l = 0; l < i; l++) {
                        SectionPos sectionPos = SectionPos.of(pos.x() + l - radius, pos.y() + k - radius, pos.z() + j - radius);
                        LayerLightSectionStorage.SectionType sectionType = levelLightEngine.getDebugSectionType(lightLayer, sectionPos);
                        if (sectionType == LayerLightSectionStorage.SectionType.LIGHT_AND_DATA) {
                            this.lightAndBlocksShape.fill(l, k, j);
                            this.lightShape.fill(l, k, j);
                        } else if (sectionType == LayerLightSectionStorage.SectionType.LIGHT_ONLY) {
                            this.lightShape.fill(l, k, j);
                        }
                    }
                }
            }

            this.minPos = SectionPos.of(pos.x() - radius, pos.y() - radius, pos.z() - radius);
        }
    }
}
