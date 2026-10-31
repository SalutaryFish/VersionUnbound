package net.minecraft.client.renderer.debug;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Locale;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

public class PathfindingRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Map<Integer, Path> pathMap = Maps.newHashMap();
    private final Map<Integer, Float> pathMaxDist = Maps.newHashMap();
    private final Map<Integer, Long> creationMap = Maps.newHashMap();
    private static final long TIMEOUT = 5000L;
    private static final float MAX_RENDER_DIST = 80.0F;
    private static final boolean SHOW_OPEN_CLOSED = true;
    private static final boolean SHOW_OPEN_CLOSED_COST_MALUS = false;
    private static final boolean SHOW_OPEN_CLOSED_NODE_TYPE_WITH_TEXT = false;
    private static final boolean SHOW_OPEN_CLOSED_NODE_TYPE_WITH_BOX = true;
    private static final boolean SHOW_GROUND_LABELS = true;
    private static final float TEXT_SCALE = 0.02F;

    public void addPath(int entityId, Path path, float maxDistanceToWaypoint) {
        this.pathMap.put(entityId, path);
        this.creationMap.put(entityId, Util.getMillis());
        this.pathMaxDist.put(entityId, maxDistanceToWaypoint);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        if (!this.pathMap.isEmpty()) {
            long l = Util.getMillis();

            for (Integer integer : this.pathMap.keySet()) {
                Path path = this.pathMap.get(integer);
                float f = this.pathMaxDist.get(integer);
                renderPath(poseStack, bufferSource, path, f, true, true, camX, camY, camZ);
            }

            for (Integer integer2 : this.creationMap.keySet().toArray(new Integer[0])) {
                if (l - this.creationMap.get(integer2) > 5000L) {
                    this.pathMap.remove(integer2);
                    this.creationMap.remove(integer2);
                }
            }
        }
    }

    public static void renderPath(PoseStack poseStack, MultiBufferSource buffer, Path path, float f, boolean bl, boolean bl2, double x, double y, double z) {
        renderPathLine(poseStack, buffer.getBuffer(RenderType.debugLineStrip(6.0)), path, x, y, z);
        BlockPos blockPos = path.getTarget();
        if (distanceToCamera(blockPos, x, y, z) <= 80.0F) {
            DebugRenderer.renderFilledBox(
                poseStack,
                buffer,
                new AABB(
                        blockPos.getX() + 0.25F,
                        blockPos.getY() + 0.25F,
                        blockPos.getZ() + 0.25,
                        blockPos.getX() + 0.75F,
                        blockPos.getY() + 0.75F,
                        blockPos.getZ() + 0.75F
                    )
                    .move(-x, -y, -z),
                0.0F,
                1.0F,
                0.0F,
                0.5F
            );

            for (int i = 0; i < path.getNodeCount(); i++) {
                Node node = path.getNode(i);
                if (distanceToCamera(node.asBlockPos(), x, y, z) <= 80.0F) {
                    float g = i == path.getNextNodeIndex() ? 1.0F : 0.0F;
                    float h = i == path.getNextNodeIndex() ? 0.0F : 1.0F;
                    DebugRenderer.renderFilledBox(
                        poseStack,
                        buffer,
                        new AABB(node.x + 0.5F - f, node.y + 0.01F * i, node.z + 0.5F - f, node.x + 0.5F + f, node.y + 0.25F + 0.01F * i, node.z + 0.5F + f)
                            .move(-x, -y, -z),
                        g,
                        0.0F,
                        h,
                        0.5F
                    );
                }
            }
        }

        Path.DebugData debugData = path.debugData();
        if (bl && debugData != null) {
            for (Node node2 : debugData.closedSet()) {
                if (distanceToCamera(node2.asBlockPos(), x, y, z) <= 80.0F) {
                    DebugRenderer.renderFilledBox(
                        poseStack,
                        buffer,
                        new AABB(
                                node2.x + 0.5F - f / 2.0F,
                                node2.y + 0.01F,
                                node2.z + 0.5F - f / 2.0F,
                                node2.x + 0.5F + f / 2.0F,
                                node2.y + 0.1,
                                node2.z + 0.5F + f / 2.0F
                            )
                            .move(-x, -y, -z),
                        1.0F,
                        0.8F,
                        0.8F,
                        0.5F
                    );
                }
            }

            for (Node node2 : debugData.openSet()) {
                if (distanceToCamera(node2.asBlockPos(), x, y, z) <= 80.0F) {
                    DebugRenderer.renderFilledBox(
                        poseStack,
                        buffer,
                        new AABB(
                                node2.x + 0.5F - f / 2.0F,
                                node2.y + 0.01F,
                                node2.z + 0.5F - f / 2.0F,
                                node2.x + 0.5F + f / 2.0F,
                                node2.y + 0.1,
                                node2.z + 0.5F + f / 2.0F
                            )
                            .move(-x, -y, -z),
                        0.8F,
                        1.0F,
                        1.0F,
                        0.5F
                    );
                }
            }
        }

        if (bl2) {
            for (int j = 0; j < path.getNodeCount(); j++) {
                Node node3 = path.getNode(j);
                if (distanceToCamera(node3.asBlockPos(), x, y, z) <= 80.0F) {
                    DebugRenderer.renderFloatingText(
                        poseStack, buffer, String.valueOf(node3.type), node3.x + 0.5, node3.y + 0.75, node3.z + 0.5, -1, 0.02F, true, 0.0F, true
                    );
                    DebugRenderer.renderFloatingText(
                        poseStack,
                        buffer,
                        String.format(Locale.ROOT, "%.2f", node3.costMalus),
                        node3.x + 0.5,
                        node3.y + 0.25,
                        node3.z + 0.5,
                        -1,
                        0.02F,
                        true,
                        0.0F,
                        true
                    );
                }
            }
        }
    }

    public static void renderPathLine(PoseStack poseStack, VertexConsumer consumer, Path path, double x, double y, double z) {
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (!(distanceToCamera(node.asBlockPos(), x, y, z) > 80.0F)) {
                float f = (float)i / path.getNodeCount() * 0.33F;
                int j = i == 0 ? 0 : Mth.hsvToRgb(f, 0.9F, 0.9F);
                int k = j >> 16 & 0xFF;
                int l = j >> 8 & 0xFF;
                int m = j & 0xFF;
                consumer.addVertex(poseStack.last(), (float)(node.x - x + 0.5), (float)(node.y - y + 0.5), (float)(node.z - z + 0.5)).setColor(k, l, m, 255);
            }
        }
    }

    private static float distanceToCamera(BlockPos pos, double x, double y, double z) {
        return (float)(Math.abs(pos.getX() - x) + Math.abs(pos.getY() - y) + Math.abs(pos.getZ() - z));
    }
}
