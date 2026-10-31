package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;

public class PistonHeadRenderer implements BlockEntityRenderer<PistonMovingBlockEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public PistonHeadRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    public void render(
        PistonMovingBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
    ) {
        Level level = blockEntity.getLevel();
        if (level != null) {
            BlockPos blockPos = blockEntity.getBlockPos().relative(blockEntity.getMovementDirection().getOpposite());
            BlockState blockState = blockEntity.getMovedState();
            if (!blockState.isAir()) {
                ModelBlockRenderer.enableCaching();
                poseStack.pushPose();
                poseStack.translate(blockEntity.getXOff(partialTick), blockEntity.getYOff(partialTick), blockEntity.getZOff(partialTick));
                if (blockState.is(Blocks.PISTON_HEAD) && blockEntity.getProgress(partialTick) <= 4.0F) {
                    blockState = blockState.setValue(PistonHeadBlock.SHORT, blockEntity.getProgress(partialTick) <= 0.5F);
                    this.renderBlock(blockPos, blockState, poseStack, bufferSource, level, false, packedOverlay);
                } else if (blockEntity.isSourcePiston() && !blockEntity.isExtending()) {
                    PistonType pistonType = blockState.is(Blocks.STICKY_PISTON) ? PistonType.STICKY : PistonType.DEFAULT;
                    BlockState blockState2 = Blocks.PISTON_HEAD
                        .defaultBlockState()
                        .setValue(PistonHeadBlock.TYPE, pistonType)
                        .setValue(PistonHeadBlock.FACING, blockState.getValue(PistonBaseBlock.FACING));
                    blockState2 = blockState2.setValue(PistonHeadBlock.SHORT, blockEntity.getProgress(partialTick) >= 0.5F);
                    this.renderBlock(blockPos, blockState2, poseStack, bufferSource, level, false, packedOverlay);
                    BlockPos blockPos2 = blockPos.relative(blockEntity.getMovementDirection());
                    poseStack.popPose();
                    poseStack.pushPose();
                    blockState = blockState.setValue(PistonBaseBlock.EXTENDED, true);
                    this.renderBlock(blockPos2, blockState, poseStack, bufferSource, level, true, packedOverlay);
                } else {
                    this.renderBlock(blockPos, blockState, poseStack, bufferSource, level, false, packedOverlay);
                }

                poseStack.popPose();
                ModelBlockRenderer.clearCache();
            }
        }
    }

    /**
     * @param extended if {@code true}, checks all sides before rendering via {@link
     *                 net.minecraft.world.level.block.Block#shouldRenderFace(
     *                 net.minecraft.world.level.block.state.BlockState,
     *                 net.minecraft.world.level.BlockGetter,
     *                 net.minecraft.core.BlockPos, net.minecraft.core.Direction,
     *                 net.minecraft.core.BlockPos)}
     */
    private void renderBlock(
        BlockPos pos, BlockState state, PoseStack poseStack, MultiBufferSource bufferSource, Level level, boolean extended, int packedOverlay
    ) {
        RenderType renderType = ItemBlockRenderTypes.getMovingBlockRenderType(state);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);
        this.blockRenderer
            .getModelRenderer()
            .tesselateBlock(
                level,
                this.blockRenderer.getBlockModel(state),
                state,
                pos,
                poseStack,
                vertexConsumer,
                extended,
                RandomSource.create(),
                state.getSeed(pos),
                packedOverlay
            );
    }

    @Override
    public int getViewDistance() {
        return 68;
    }
}
