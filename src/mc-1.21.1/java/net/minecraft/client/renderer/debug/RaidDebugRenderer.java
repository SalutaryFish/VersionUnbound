package net.minecraft.client.renderer.debug;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collection;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;

public class RaidDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final int MAX_RENDER_DIST = 160;
    private static final float TEXT_SCALE = 0.04F;
    private final Minecraft minecraft;
    private Collection<BlockPos> raidCenters = Lists.newArrayList();

    public RaidDebugRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void setRaidCenters(Collection<BlockPos> raidCenters) {
        this.raidCenters = raidCenters;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        BlockPos blockPos = this.getCamera().getBlockPosition();

        for (BlockPos blockPos2 : this.raidCenters) {
            if (blockPos.closerThan(blockPos2, 160.0)) {
                highlightRaidCenter(poseStack, bufferSource, blockPos2);
            }
        }
    }

    private static void highlightRaidCenter(PoseStack poseStack, MultiBufferSource buffer, BlockPos pos) {
        DebugRenderer.renderFilledUnitCube(poseStack, buffer, pos, 1.0F, 0.0F, 0.0F, 0.15F);
        int i = -65536;
        renderTextOverBlock(poseStack, buffer, "Raid center", pos, -65536);
    }

    private static void renderTextOverBlock(PoseStack poseStack, MultiBufferSource buffer, String text, BlockPos pos, int color) {
        double d = pos.getX() + 0.5;
        double e = pos.getY() + 1.3;
        double f = pos.getZ() + 0.5;
        DebugRenderer.renderFloatingText(poseStack, buffer, text, d, e, f, color, 0.04F, true, 0.0F, true);
    }

    private Camera getCamera() {
        return this.minecraft.gameRenderer.getMainCamera();
    }
}
