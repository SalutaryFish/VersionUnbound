package net.minecraft.client.renderer.blockentity;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.HitResult;

public class BlockEntityRenderDispatcher implements ResourceManagerReloadListener {
    private Map<BlockEntityType<?>, BlockEntityRenderer<?>> renderers = ImmutableMap.of();
    private final Font font;
    private final EntityModelSet entityModelSet;
    public Level level;
    public Camera camera;
    public HitResult cameraHitResult;
    private final Supplier<BlockRenderDispatcher> blockRenderDispatcher;
    private final Supplier<ItemRenderer> itemRenderer;
    private final Supplier<EntityRenderDispatcher> entityRenderer;

    public BlockEntityRenderDispatcher(
        Font font,
        EntityModelSet entityModelSet,
        Supplier<BlockRenderDispatcher> blockRenderDispatcher,
        Supplier<ItemRenderer> itemRenderer,
        Supplier<EntityRenderDispatcher> entityRenderer
    ) {
        this.itemRenderer = itemRenderer;
        this.entityRenderer = entityRenderer;
        this.font = font;
        this.entityModelSet = entityModelSet;
        this.blockRenderDispatcher = blockRenderDispatcher;
    }

    @Nullable
    public <E extends BlockEntity> BlockEntityRenderer<E> getRenderer(E blockEntity) {
        return (BlockEntityRenderer<E>)this.renderers.get(blockEntity.getType());
    }

    public void prepare(Level level, Camera camera, HitResult cameraHitResult) {
        if (this.level != level) {
            this.setLevel(level);
        }

        this.camera = camera;
        this.cameraHitResult = cameraHitResult;
    }

    public <E extends BlockEntity> void render(E blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        BlockEntityRenderer<E> blockEntityRenderer = this.getRenderer(blockEntity);
        if (blockEntityRenderer != null) {
            if (blockEntity.hasLevel() && blockEntity.getType().isValid(blockEntity.getBlockState())) {
                if (blockEntityRenderer.shouldRender(blockEntity, this.camera.getPosition())) {
                    tryRender(blockEntity, () -> setupAndRender(blockEntityRenderer, blockEntity, partialTick, poseStack, bufferSource));
                }
            }
        }
    }

    private static <T extends BlockEntity> void setupAndRender(
        BlockEntityRenderer<T> renderer, T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource
    ) {
        Level level = blockEntity.getLevel();
        int i;
        if (level != null) {
            i = LevelRenderer.getLightColor(level, blockEntity.getBlockPos());
        } else {
            i = 15728880;
        }

        renderer.render(blockEntity, partialTick, poseStack, bufferSource, i, OverlayTexture.NO_OVERLAY);
    }

    /**
     * @return {@code true} if no renderer was found; otherwise {@code false} if render completed
     */
    public <E extends BlockEntity> boolean renderItem(E blockEntity, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockEntityRenderer<E> blockEntityRenderer = this.getRenderer(blockEntity);
        if (blockEntityRenderer == null) {
            return true;
        }

        tryRender(blockEntity, () -> blockEntityRenderer.render(blockEntity, 0.0F, poseStack, bufferSource, packedLight, packedOverlay));
        return false;
    }

    private static void tryRender(BlockEntity blockEntity, Runnable renderer) {
        try {
            renderer.run();
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering Block Entity");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Block Entity Details");
            blockEntity.fillCrashReportCategory(crashReportCategory);
            throw new ReportedException(crashReport);
        }
    }

    public void setLevel(@Nullable Level level) {
        this.level = level;
        if (level == null) {
            this.camera = null;
        }
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        BlockEntityRendererProvider.Context context = new BlockEntityRendererProvider.Context(
            this, this.blockRenderDispatcher.get(), this.itemRenderer.get(), this.entityRenderer.get(), this.entityModelSet, this.font
        );
        this.renderers = BlockEntityRenderers.createEntityRenderers(context);
    }
}
