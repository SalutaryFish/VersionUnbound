package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.GoalDebugPayload;

public class GoalSelectorDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final int MAX_RENDER_DIST = 160;
    private final Minecraft minecraft;
    private final Int2ObjectMap<GoalSelectorDebugRenderer.EntityGoalInfo> goalSelectors = new Int2ObjectOpenHashMap<>();

    @Override
    public void clear() {
        this.goalSelectors.clear();
    }

    public void addGoalSelector(int mobId, BlockPos entityPos, List<GoalDebugPayload.DebugGoal> goals) {
        this.goalSelectors.put(mobId, new GoalSelectorDebugRenderer.EntityGoalInfo(entityPos, goals));
    }

    public void removeGoalSelector(int mobId) {
        this.goalSelectors.remove(mobId);
    }

    public GoalSelectorDebugRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        BlockPos blockPos = BlockPos.containing(camera.getPosition().x, 0.0, camera.getPosition().z);

        for (GoalSelectorDebugRenderer.EntityGoalInfo entityGoalInfo : this.goalSelectors.values()) {
            BlockPos blockPos2 = entityGoalInfo.entityPos;
            if (blockPos.closerThan(blockPos2, 160.0)) {
                for (int i = 0; i < entityGoalInfo.goals.size(); i++) {
                    GoalDebugPayload.DebugGoal debugGoal = entityGoalInfo.goals.get(i);
                    double d = blockPos2.getX() + 0.5;
                    double e = blockPos2.getY() + 2.0 + i * 0.25;
                    double f = blockPos2.getZ() + 0.5;
                    int j = debugGoal.isRunning() ? -16711936 : -3355444;
                    DebugRenderer.renderFloatingText(poseStack, bufferSource, debugGoal.name(), d, e, f, j);
                }
            }
        }
    }

    record EntityGoalInfo(BlockPos entityPos, List<GoalDebugPayload.DebugGoal> goals) {
    }
}
