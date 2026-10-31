package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

public class SpawnerRenderer implements BlockEntityRenderer<SpawnerBlockEntity> {
    private final EntityRenderDispatcher entityRenderer;

    public SpawnerRenderer(BlockEntityRendererProvider.Context context) {
        this.entityRenderer = context.getEntityRenderer();
    }

    public void render(
        SpawnerBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
    ) {
        Level level = blockEntity.getLevel();
        if (level != null) {
            BaseSpawner baseSpawner = blockEntity.getSpawner();
            Entity entity = baseSpawner.getOrCreateDisplayEntity(level, blockEntity.getBlockPos());
            if (entity != null) {
                renderEntityInSpawner(
                    partialTick, poseStack, bufferSource, packedLight, entity, this.entityRenderer, baseSpawner.getoSpin(), baseSpawner.getSpin()
                );
            }
        }
    }

    public static void renderEntityInSpawner(
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        Entity entity,
        EntityRenderDispatcher entityRenderer,
        double oSpin,
        double spin
    ) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        float f = 0.53125F;
        float g = Math.max(entity.getBbWidth(), entity.getBbHeight());
        if (g > 1.0) {
            f /= g;
        }

        poseStack.translate(0.0F, 0.4F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees((float)Mth.lerp(partialTick, oSpin, spin) * 10.0F));
        poseStack.translate(0.0F, -0.2F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-30.0F));
        poseStack.scale(f, f, f);
        entityRenderer.render(entity, 0.0, 0.0, 0.0, 0.0F, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
