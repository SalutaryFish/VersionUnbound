package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;

public class TrialSpawnerRenderer implements BlockEntityRenderer<TrialSpawnerBlockEntity> {
    private final EntityRenderDispatcher entityRenderer;

    public TrialSpawnerRenderer(BlockEntityRendererProvider.Context context) {
        this.entityRenderer = context.getEntityRenderer();
    }

    public void render(
        TrialSpawnerBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
    ) {
        Level level = blockEntity.getLevel();
        if (level != null) {
            TrialSpawner trialSpawner = blockEntity.getTrialSpawner();
            TrialSpawnerData trialSpawnerData = trialSpawner.getData();
            Entity entity = trialSpawnerData.getOrCreateDisplayEntity(trialSpawner, level, trialSpawner.getState());
            if (entity != null) {
                SpawnerRenderer.renderEntityInSpawner(
                    partialTick, poseStack, bufferSource, packedLight, entity, this.entityRenderer, trialSpawnerData.getOSpin(), trialSpawnerData.getSpin()
                );
            }
        }
    }
}
