package net.minecraft.client.renderer.debug;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;

public class GameTestDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final float PADDING = 0.02F;
    private final Map<BlockPos, GameTestDebugRenderer.Marker> markers = Maps.newHashMap();

    /**
     * @param removeAfter how long after the current time to remove this marker, in
     *                    milliseconds
     */
    public void addMarker(BlockPos pos, int color, String text, int removeAfter) {
        this.markers.put(pos, new GameTestDebugRenderer.Marker(color, text, Util.getMillis() + removeAfter));
    }

    @Override
    public void clear() {
        this.markers.clear();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        long l = Util.getMillis();
        this.markers.entrySet().removeIf(marker -> l > marker.getValue().removeAtTime);
        this.markers.forEach((blockPos, marker) -> this.renderMarker(poseStack, bufferSource, blockPos, marker));
    }

    private void renderMarker(PoseStack poseStack, MultiBufferSource buffer, BlockPos pos, GameTestDebugRenderer.Marker marker) {
        DebugRenderer.renderFilledBox(poseStack, buffer, pos, 0.02F, marker.getR(), marker.getG(), marker.getB(), marker.getA() * 0.75F);
        if (!marker.text.isEmpty()) {
            double d = pos.getX() + 0.5;
            double e = pos.getY() + 1.2;
            double f = pos.getZ() + 0.5;
            DebugRenderer.renderFloatingText(poseStack, buffer, marker.text, d, e, f, -1, 0.01F, true, 0.0F, true);
        }
    }

    static class Marker {
        public int color;
        public String text;
        public long removeAtTime;

        public Marker(int color, String text, long removeAtTime) {
            this.color = color;
            this.text = text;
            this.removeAtTime = removeAtTime;
        }

        public float getR() {
            return (this.color >> 16 & 0xFF) / 255.0F;
        }

        public float getG() {
            return (this.color >> 8 & 0xFF) / 255.0F;
        }

        public float getB() {
            return (this.color & 0xFF) / 255.0F;
        }

        public float getA() {
            return (this.color >> 24 & 0xFF) / 255.0F;
        }
    }
}
