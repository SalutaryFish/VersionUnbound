package net.minecraft.client.renderer.debug;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;

public class WorldGenAttemptRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final List<BlockPos> toRender = Lists.newArrayList();
    private final List<Float> scales = Lists.newArrayList();
    private final List<Float> alphas = Lists.newArrayList();
    private final List<Float> reds = Lists.newArrayList();
    private final List<Float> greens = Lists.newArrayList();
    private final List<Float> blues = Lists.newArrayList();

    public void addPos(BlockPos pos, float scale, float red, float green, float blue, float alpha) {
        this.toRender.add(pos);
        this.scales.add(scale);
        this.alphas.add(alpha);
        this.reds.add(red);
        this.greens.add(green);
        this.blues.add(blue);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.debugFilledBox());

        for (int i = 0; i < this.toRender.size(); i++) {
            BlockPos blockPos = this.toRender.get(i);
            Float float_ = this.scales.get(i);
            float f = float_ / 2.0F;
            LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                vertexConsumer,
                blockPos.getX() + 0.5F - f - camX,
                blockPos.getY() + 0.5F - f - camY,
                blockPos.getZ() + 0.5F - f - camZ,
                blockPos.getX() + 0.5F + f - camX,
                blockPos.getY() + 0.5F + f - camY,
                blockPos.getZ() + 0.5F + f - camZ,
                this.reds.get(i),
                this.greens.get(i),
                this.blues.get(i),
                this.alphas.get(i)
            );
        }
    }
}
