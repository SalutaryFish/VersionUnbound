package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;

public class ChunkBorderRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;
    private static final int CELL_BORDER = FastColor.ARGB32.color(255, 0, 155, 155);
    private static final int YELLOW = FastColor.ARGB32.color(255, 255, 255, 0);

    public ChunkBorderRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        Entity entity = this.minecraft.gameRenderer.getMainCamera().getEntity();
        float f = (float)(this.minecraft.level.getMinBuildHeight() - camY);
        float g = (float)(this.minecraft.level.getMaxBuildHeight() - camY);
        ChunkPos chunkPos = entity.chunkPosition();
        float h = (float)(chunkPos.getMinBlockX() - camX);
        float i = (float)(chunkPos.getMinBlockZ() - camZ);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.debugLineStrip(1.0));
        Matrix4f matrix4f = poseStack.last().pose();

        for (int j = -16; j <= 32; j += 16) {
            for (int k = -16; k <= 32; k += 16) {
                vertexConsumer.addVertex(matrix4f, h + j, f, i + k).setColor(1.0F, 0.0F, 0.0F, 0.0F);
                vertexConsumer.addVertex(matrix4f, h + j, f, i + k).setColor(1.0F, 0.0F, 0.0F, 0.5F);
                vertexConsumer.addVertex(matrix4f, h + j, g, i + k).setColor(1.0F, 0.0F, 0.0F, 0.5F);
                vertexConsumer.addVertex(matrix4f, h + j, g, i + k).setColor(1.0F, 0.0F, 0.0F, 0.0F);
            }
        }

        for (int j = 2; j < 16; j += 2) {
            int k = j % 4 == 0 ? CELL_BORDER : YELLOW;
            vertexConsumer.addVertex(matrix4f, h + j, f, i).setColor(1.0F, 1.0F, 0.0F, 0.0F);
            vertexConsumer.addVertex(matrix4f, h + j, f, i).setColor(k);
            vertexConsumer.addVertex(matrix4f, h + j, g, i).setColor(k);
            vertexConsumer.addVertex(matrix4f, h + j, g, i).setColor(1.0F, 1.0F, 0.0F, 0.0F);
            vertexConsumer.addVertex(matrix4f, h + j, f, i + 16.0F).setColor(1.0F, 1.0F, 0.0F, 0.0F);
            vertexConsumer.addVertex(matrix4f, h + j, f, i + 16.0F).setColor(k);
            vertexConsumer.addVertex(matrix4f, h + j, g, i + 16.0F).setColor(k);
            vertexConsumer.addVertex(matrix4f, h + j, g, i + 16.0F).setColor(1.0F, 1.0F, 0.0F, 0.0F);
        }

        for (int j = 2; j < 16; j += 2) {
            int k = j % 4 == 0 ? CELL_BORDER : YELLOW;
            vertexConsumer.addVertex(matrix4f, h, f, i + j).setColor(1.0F, 1.0F, 0.0F, 0.0F);
            vertexConsumer.addVertex(matrix4f, h, f, i + j).setColor(k);
            vertexConsumer.addVertex(matrix4f, h, g, i + j).setColor(k);
            vertexConsumer.addVertex(matrix4f, h, g, i + j).setColor(1.0F, 1.0F, 0.0F, 0.0F);
            vertexConsumer.addVertex(matrix4f, h + 16.0F, f, i + j).setColor(1.0F, 1.0F, 0.0F, 0.0F);
            vertexConsumer.addVertex(matrix4f, h + 16.0F, f, i + j).setColor(k);
            vertexConsumer.addVertex(matrix4f, h + 16.0F, g, i + j).setColor(k);
            vertexConsumer.addVertex(matrix4f, h + 16.0F, g, i + j).setColor(1.0F, 1.0F, 0.0F, 0.0F);
        }

        for (int j = this.minecraft.level.getMinBuildHeight(); j <= this.minecraft.level.getMaxBuildHeight(); j += 2) {
            float l = (float)(j - camY);
            int m = j % 8 == 0 ? CELL_BORDER : YELLOW;
            vertexConsumer.addVertex(matrix4f, h, l, i).setColor(1.0F, 1.0F, 0.0F, 0.0F);
            vertexConsumer.addVertex(matrix4f, h, l, i).setColor(m);
            vertexConsumer.addVertex(matrix4f, h, l, i + 16.0F).setColor(m);
            vertexConsumer.addVertex(matrix4f, h + 16.0F, l, i + 16.0F).setColor(m);
            vertexConsumer.addVertex(matrix4f, h + 16.0F, l, i).setColor(m);
            vertexConsumer.addVertex(matrix4f, h, l, i).setColor(m);
            vertexConsumer.addVertex(matrix4f, h, l, i).setColor(1.0F, 1.0F, 0.0F, 0.0F);
        }

        vertexConsumer = bufferSource.getBuffer(RenderType.debugLineStrip(2.0));

        for (int j = 0; j <= 16; j += 16) {
            for (int k = 0; k <= 16; k += 16) {
                vertexConsumer.addVertex(matrix4f, h + j, f, i + k).setColor(0.25F, 0.25F, 1.0F, 0.0F);
                vertexConsumer.addVertex(matrix4f, h + j, f, i + k).setColor(0.25F, 0.25F, 1.0F, 1.0F);
                vertexConsumer.addVertex(matrix4f, h + j, g, i + k).setColor(0.25F, 0.25F, 1.0F, 1.0F);
                vertexConsumer.addVertex(matrix4f, h + j, g, i + k).setColor(0.25F, 0.25F, 1.0F, 0.0F);
            }
        }

        for (int j = this.minecraft.level.getMinBuildHeight(); j <= this.minecraft.level.getMaxBuildHeight(); j += 16) {
            float l = (float)(j - camY);
            vertexConsumer.addVertex(matrix4f, h, l, i).setColor(0.25F, 0.25F, 1.0F, 0.0F);
            vertexConsumer.addVertex(matrix4f, h, l, i).setColor(0.25F, 0.25F, 1.0F, 1.0F);
            vertexConsumer.addVertex(matrix4f, h, l, i + 16.0F).setColor(0.25F, 0.25F, 1.0F, 1.0F);
            vertexConsumer.addVertex(matrix4f, h + 16.0F, l, i + 16.0F).setColor(0.25F, 0.25F, 1.0F, 1.0F);
            vertexConsumer.addVertex(matrix4f, h + 16.0F, l, i).setColor(0.25F, 0.25F, 1.0F, 1.0F);
            vertexConsumer.addVertex(matrix4f, h, l, i).setColor(0.25F, 0.25F, 1.0F, 1.0F);
            vertexConsumer.addVertex(matrix4f, h, l, i).setColor(0.25F, 0.25F, 1.0F, 0.0F);
        }
    }
}
