package net.minecraft.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class BlockRenderDispatcher implements ResourceManagerReloadListener {
    private final BlockModelShaper blockModelShaper;
    private final ModelBlockRenderer modelRenderer;
    private final BlockEntityWithoutLevelRenderer blockEntityRenderer;
    private final LiquidBlockRenderer liquidBlockRenderer;
    private final RandomSource random = RandomSource.create();
    private final BlockColors blockColors;

    public BlockRenderDispatcher(BlockModelShaper blockModelShaper, BlockEntityWithoutLevelRenderer blockEntityRenderer, BlockColors blockColors) {
        this.blockModelShaper = blockModelShaper;
        this.blockEntityRenderer = blockEntityRenderer;
        this.blockColors = blockColors;
        this.modelRenderer = new ModelBlockRenderer(this.blockColors);
        this.liquidBlockRenderer = new LiquidBlockRenderer();
    }

    public BlockModelShaper getBlockModelShaper() {
        return this.blockModelShaper;
    }

    public void renderBreakingTexture(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack, VertexConsumer consumer) {
        if (state.getRenderShape() == RenderShape.MODEL) {
            BakedModel bakedModel = this.blockModelShaper.getBlockModel(state);
            long l = state.getSeed(pos);
            this.modelRenderer.tesselateBlock(level, bakedModel, state, pos, poseStack, consumer, true, this.random, l, OverlayTexture.NO_OVERLAY);
        }
    }

    public void renderBatched(
        BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack, VertexConsumer consumer, boolean checkSides, RandomSource random
    ) {
        try {
            this.modelRenderer
                .tesselateBlock(
                    level, this.getBlockModel(state), state, pos, poseStack, consumer, checkSides, random, state.getSeed(pos), OverlayTexture.NO_OVERLAY
                );
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Tesselating block in world");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Block being tesselated");
            CrashReportCategory.populateBlockDetails(crashReportCategory, level, pos, state);
            throw new ReportedException(crashReport);
        }
    }

    public void renderLiquid(BlockPos pos, BlockAndTintGetter level, VertexConsumer consumer, BlockState blockState, FluidState fluidState) {
        try {
            this.liquidBlockRenderer.tesselate(level, pos, consumer, blockState, fluidState);
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Tesselating liquid in world");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Block being tesselated");
            CrashReportCategory.populateBlockDetails(crashReportCategory, level, pos, null);
            throw new ReportedException(crashReport);
        }
    }

    public ModelBlockRenderer getModelRenderer() {
        return this.modelRenderer;
    }

    public BakedModel getBlockModel(BlockState state) {
        return this.blockModelShaper.getBlockModel(state);
    }

    public void renderSingleBlock(BlockState state, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        RenderShape renderShape = state.getRenderShape();
        if (renderShape != RenderShape.INVISIBLE) {
            switch (renderShape) {
                case MODEL:
                    BakedModel bakedModel = this.getBlockModel(state);
                    int i = this.blockColors.getColor(state, null, null, 0);
                    float f = (i >> 16 & 0xFF) / 255.0F;
                    float g = (i >> 8 & 0xFF) / 255.0F;
                    float h = (i & 0xFF) / 255.0F;
                    this.modelRenderer
                        .renderModel(
                            poseStack.last(),
                            bufferSource.getBuffer(ItemBlockRenderTypes.getRenderType(state, false)),
                            state,
                            bakedModel,
                            f,
                            g,
                            h,
                            packedLight,
                            packedOverlay
                        );
                    break;
                case ENTITYBLOCK_ANIMATED:
                    this.blockEntityRenderer
                        .renderByItem(new ItemStack(state.getBlock()), ItemDisplayContext.NONE, poseStack, bufferSource, packedLight, packedOverlay);
            }
        }
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.liquidBlockRenderer.setupSprites();
    }
}
