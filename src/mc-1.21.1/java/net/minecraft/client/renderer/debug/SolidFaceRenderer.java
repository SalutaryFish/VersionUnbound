package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

public class SolidFaceRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;

    public SolidFaceRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        Matrix4f matrix4f = poseStack.last().pose();
        BlockGetter blockGetter = this.minecraft.player.level();
        BlockPos blockPos = BlockPos.containing(camX, camY, camZ);

        for (BlockPos blockPos2 : BlockPos.betweenClosed(blockPos.offset(-6, -6, -6), blockPos.offset(6, 6, 6))) {
            BlockState blockState = blockGetter.getBlockState(blockPos2);
            if (!blockState.is(Blocks.AIR)) {
                VoxelShape voxelShape = blockState.getShape(blockGetter, blockPos2);

                for (AABB aABB : voxelShape.toAabbs()) {
                    AABB aABB2 = aABB.move(blockPos2).inflate(0.002);
                    float f = (float)(aABB2.minX - camX);
                    float g = (float)(aABB2.minY - camY);
                    float h = (float)(aABB2.minZ - camZ);
                    float i = (float)(aABB2.maxX - camX);
                    float j = (float)(aABB2.maxY - camY);
                    float k = (float)(aABB2.maxZ - camZ);
                    int l = -2130771968;
                    if (blockState.isFaceSturdy(blockGetter, blockPos2, Direction.WEST)) {
                        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.debugFilledBox());
                        vertexConsumer.addVertex(matrix4f, f, g, h).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, f, g, k).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, f, j, h).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, f, j, k).setColor(-2130771968);
                    }

                    if (blockState.isFaceSturdy(blockGetter, blockPos2, Direction.SOUTH)) {
                        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.debugFilledBox());
                        vertexConsumer.addVertex(matrix4f, f, j, k).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, f, g, k).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, i, j, k).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, i, g, k).setColor(-2130771968);
                    }

                    if (blockState.isFaceSturdy(blockGetter, blockPos2, Direction.EAST)) {
                        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.debugFilledBox());
                        vertexConsumer.addVertex(matrix4f, i, g, k).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, i, g, h).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, i, j, k).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, i, j, h).setColor(-2130771968);
                    }

                    if (blockState.isFaceSturdy(blockGetter, blockPos2, Direction.NORTH)) {
                        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.debugFilledBox());
                        vertexConsumer.addVertex(matrix4f, i, j, h).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, i, g, h).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, f, j, h).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, f, g, h).setColor(-2130771968);
                    }

                    if (blockState.isFaceSturdy(blockGetter, blockPos2, Direction.DOWN)) {
                        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.debugFilledBox());
                        vertexConsumer.addVertex(matrix4f, f, g, h).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, i, g, h).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, f, g, k).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, i, g, k).setColor(-2130771968);
                    }

                    if (blockState.isFaceSturdy(blockGetter, blockPos2, Direction.UP)) {
                        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.debugFilledBox());
                        vertexConsumer.addVertex(matrix4f, f, j, h).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, f, j, k).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, i, j, h).setColor(-2130771968);
                        vertexConsumer.addVertex(matrix4f, i, j, k).setColor(-2130771968);
                    }
                }
            }
        }
    }
}
