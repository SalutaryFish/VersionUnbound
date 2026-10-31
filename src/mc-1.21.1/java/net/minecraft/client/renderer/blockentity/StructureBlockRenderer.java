package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;

public class StructureBlockRenderer implements BlockEntityRenderer<StructureBlockEntity> {
    public StructureBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    public void render(
        StructureBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
    ) {
        if (Minecraft.getInstance().player.canUseGameMasterBlocks() || Minecraft.getInstance().player.isSpectator()) {
            BlockPos blockPos = blockEntity.getStructurePos();
            Vec3i vec3i = blockEntity.getStructureSize();
            if (vec3i.getX() >= 1 && vec3i.getY() >= 1 && vec3i.getZ() >= 1) {
                if (blockEntity.getMode() == StructureMode.SAVE || blockEntity.getMode() == StructureMode.LOAD) {
                    double d = blockPos.getX();
                    double e = blockPos.getZ();
                    double f = blockPos.getY();
                    double g = f + vec3i.getY();
                    double h;
                    double i;
                    switch (blockEntity.getMirror()) {
                        case LEFT_RIGHT:
                            h = vec3i.getX();
                            i = -vec3i.getZ();
                            break;
                        case FRONT_BACK:
                            h = -vec3i.getX();
                            i = vec3i.getZ();
                            break;
                        default:
                            h = vec3i.getX();
                            i = vec3i.getZ();
                    }

                    double j;
                    double k;
                    double l;
                    double m;
                    switch (blockEntity.getRotation()) {
                        case CLOCKWISE_90:
                            j = i < 0.0 ? d : d + 1.0;
                            k = h < 0.0 ? e + 1.0 : e;
                            l = j - i;
                            m = k + h;
                            break;
                        case CLOCKWISE_180:
                            j = h < 0.0 ? d : d + 1.0;
                            k = i < 0.0 ? e : e + 1.0;
                            l = j - h;
                            m = k - i;
                            break;
                        case COUNTERCLOCKWISE_90:
                            j = i < 0.0 ? d + 1.0 : d;
                            k = h < 0.0 ? e : e + 1.0;
                            l = j + i;
                            m = k - h;
                            break;
                        default:
                            j = h < 0.0 ? d + 1.0 : d;
                            k = i < 0.0 ? e + 1.0 : e;
                            l = j + h;
                            m = k + i;
                    }

                    float n = 1.0F;
                    float o = 0.9F;
                    float p = 0.5F;
                    if (blockEntity.getMode() == StructureMode.SAVE || blockEntity.getShowBoundingBox()) {
                        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
                        LevelRenderer.renderLineBox(poseStack, vertexConsumer, j, f, k, l, g, m, 0.9F, 0.9F, 0.9F, 1.0F, 0.5F, 0.5F, 0.5F);
                    }

                    if (blockEntity.getMode() == StructureMode.SAVE && blockEntity.getShowAir()) {
                        this.renderInvisibleBlocks(blockEntity, bufferSource, poseStack);
                    }
                }
            }
        }
    }

    private void renderInvisibleBlocks(StructureBlockEntity blockEntity, MultiBufferSource bufferSource, PoseStack poseStack) {
        BlockGetter blockGetter = blockEntity.getLevel();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        BlockPos blockPos = blockEntity.getBlockPos();
        BlockPos blockPos2 = StructureUtils.getStructureOrigin(blockEntity);

        for (BlockPos blockPos3 : BlockPos.betweenClosed(blockPos2, blockPos2.offset(blockEntity.getStructureSize()).offset(-1, -1, -1))) {
            BlockState blockState = blockGetter.getBlockState(blockPos3);
            boolean bl = blockState.isAir();
            boolean bl2 = blockState.is(Blocks.STRUCTURE_VOID);
            boolean bl3 = blockState.is(Blocks.BARRIER);
            boolean bl4 = blockState.is(Blocks.LIGHT);
            boolean bl5 = bl2 || bl3 || bl4;
            if (bl || bl5) {
                float f = bl ? 0.05F : 0.0F;
                double d = blockPos3.getX() - blockPos.getX() + 0.45F - f;
                double e = blockPos3.getY() - blockPos.getY() + 0.45F - f;
                double g = blockPos3.getZ() - blockPos.getZ() + 0.45F - f;
                double h = blockPos3.getX() - blockPos.getX() + 0.55F + f;
                double i = blockPos3.getY() - blockPos.getY() + 0.55F + f;
                double j = blockPos3.getZ() - blockPos.getZ() + 0.55F + f;
                if (bl) {
                    LevelRenderer.renderLineBox(poseStack, vertexConsumer, d, e, g, h, i, j, 0.5F, 0.5F, 1.0F, 1.0F, 0.5F, 0.5F, 1.0F);
                } else if (bl2) {
                    LevelRenderer.renderLineBox(poseStack, vertexConsumer, d, e, g, h, i, j, 1.0F, 0.75F, 0.75F, 1.0F, 1.0F, 0.75F, 0.75F);
                } else if (bl3) {
                    LevelRenderer.renderLineBox(poseStack, vertexConsumer, d, e, g, h, i, j, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F);
                } else if (bl4) {
                    LevelRenderer.renderLineBox(poseStack, vertexConsumer, d, e, g, h, i, j, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F);
                }
            }
        }
    }

    private void renderStructureVoids(StructureBlockEntity blockEntity, VertexConsumer buffer, PoseStack poseStack) {
        BlockGetter blockGetter = blockEntity.getLevel();
        if (blockGetter != null) {
            BlockPos blockPos = blockEntity.getBlockPos();
            BlockPos blockPos2 = StructureUtils.getStructureOrigin(blockEntity);
            Vec3i vec3i = blockEntity.getStructureSize();
            DiscreteVoxelShape discreteVoxelShape = new BitSetDiscreteVoxelShape(vec3i.getX(), vec3i.getY(), vec3i.getZ());

            for (BlockPos blockPos3 : BlockPos.betweenClosed(blockPos2, blockPos2.offset(vec3i).offset(-1, -1, -1))) {
                if (blockGetter.getBlockState(blockPos3).is(Blocks.STRUCTURE_VOID)) {
                    discreteVoxelShape.fill(blockPos3.getX() - blockPos2.getX(), blockPos3.getY() - blockPos2.getY(), blockPos3.getZ() - blockPos2.getZ());
                }
            }

            discreteVoxelShape.forAllFaces((direction, i, j, k) -> {
                float f = 0.48F;
                float g = i + blockPos2.getX() - blockPos.getX() + 0.5F - 0.48F;
                float h = j + blockPos2.getY() - blockPos.getY() + 0.5F - 0.48F;
                float l = k + blockPos2.getZ() - blockPos.getZ() + 0.5F - 0.48F;
                float m = i + blockPos2.getX() - blockPos.getX() + 0.5F + 0.48F;
                float n = j + blockPos2.getY() - blockPos.getY() + 0.5F + 0.48F;
                float o = k + blockPos2.getZ() - blockPos.getZ() + 0.5F + 0.48F;
                LevelRenderer.renderFace(poseStack, buffer, direction, g, h, l, m, n, o, 0.75F, 0.75F, 1.0F, 0.2F);
            });
        }
    }

    public boolean shouldRenderOffScreen(StructureBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
