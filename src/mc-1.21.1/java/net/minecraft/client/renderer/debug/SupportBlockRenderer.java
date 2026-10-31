package net.minecraft.client.renderer.debug;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.CollisionContext;

public class SupportBlockRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;
    private double lastUpdateTime = Double.MIN_VALUE;
    private List<Entity> surroundEntities = Collections.emptyList();

    public SupportBlockRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        double d = Util.getNanos();
        if (d - this.lastUpdateTime > 1.0E8) {
            this.lastUpdateTime = d;
            Entity entity = this.minecraft.gameRenderer.getMainCamera().getEntity();
            this.surroundEntities = ImmutableList.copyOf(entity.level().getEntities(entity, entity.getBoundingBox().inflate(16.0)));
        }

        Player player = this.minecraft.player;
        if (player != null && player.mainSupportingBlockPos.isPresent()) {
            this.drawHighlights(poseStack, bufferSource, camX, camY, camZ, player, () -> 0.0, 1.0F, 0.0F, 0.0F);
        }

        for (Entity entity2 : this.surroundEntities) {
            if (entity2 != player) {
                this.drawHighlights(poseStack, bufferSource, camX, camY, camZ, entity2, () -> this.getBias(entity2), 0.0F, 1.0F, 0.0F);
            }
        }
    }

    private void drawHighlights(
        PoseStack poseStack,
        MultiBufferSource buffer,
        double camX,
        double camY,
        double camZ,
        Entity entity,
        DoubleSupplier biasGetter,
        float red,
        float green,
        float blue
    ) {
        entity.mainSupportingBlockPos.ifPresent(blockPos -> {
            double j = biasGetter.getAsDouble();
            BlockPos blockPos2 = entity.getOnPos();
            this.highlightPosition(blockPos2, poseStack, camX, camY, camZ, buffer, 0.02 + j, red, green, blue);
            BlockPos blockPos3 = entity.getOnPosLegacy();
            if (!blockPos3.equals(blockPos2)) {
                this.highlightPosition(blockPos3, poseStack, camX, camY, camZ, buffer, 0.04 + j, 0.0F, 1.0F, 1.0F);
            }
        });
    }

    private double getBias(Entity entity) {
        return 0.02 * (String.valueOf(entity.getId() + 0.132453657).hashCode() % 1000) / 1000.0;
    }

    private void highlightPosition(
        BlockPos pos, PoseStack poseStack, double camX, double camY, double camZ, MultiBufferSource buffer, double bias, float red, float green, float blue
    ) {
        double d = pos.getX() - camX - 2.0 * bias;
        double e = pos.getY() - camY - 2.0 * bias;
        double f = pos.getZ() - camZ - 2.0 * bias;
        double g = d + 1.0 + 4.0 * bias;
        double h = e + 1.0 + 4.0 * bias;
        double i = f + 1.0 + 4.0 * bias;
        LevelRenderer.renderLineBox(poseStack, buffer.getBuffer(RenderType.lines()), d, e, f, g, h, i, red, green, blue, 0.4F);
        LevelRenderer.renderVoxelShape(
            poseStack,
            buffer.getBuffer(RenderType.lines()),
            this.minecraft
                .level
                .getBlockState(pos)
                .getCollisionShape(this.minecraft.level, pos, CollisionContext.empty())
                .move(pos.getX(), pos.getY(), pos.getZ()),
            -camX,
            -camY,
            -camZ,
            red,
            green,
            blue,
            1.0F,
            false
        );
    }
}
