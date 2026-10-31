package net.minecraft.client.renderer.debug;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public class GameEventListenerRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;
    private static final int LISTENER_RENDER_DIST = 32;
    private static final float BOX_HEIGHT = 1.0F;
    private final List<GameEventListenerRenderer.TrackedGameEvent> trackedGameEvents = Lists.newArrayList();
    private final List<GameEventListenerRenderer.TrackedListener> trackedListeners = Lists.newArrayList();

    public GameEventListenerRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        Level level = this.minecraft.level;
        if (level == null) {
            this.trackedGameEvents.clear();
            this.trackedListeners.clear();
        } else {
            Vec3 vec3 = new Vec3(camX, 0.0, camZ);
            this.trackedGameEvents.removeIf(GameEventListenerRenderer.TrackedGameEvent::isExpired);
            this.trackedListeners.removeIf(trackedListenerx -> trackedListenerx.isExpired(level, vec3));
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());

            for (GameEventListenerRenderer.TrackedListener trackedListener : this.trackedListeners) {
                trackedListener.getPosition(level)
                    .ifPresent(
                        pos -> {
                            double gx = pos.x() - trackedListener.getListenerRadius();
                            double hx = pos.y() - trackedListener.getListenerRadius();
                            double ix = pos.z() - trackedListener.getListenerRadius();
                            double jx = pos.x() + trackedListener.getListenerRadius();
                            double k = pos.y() + trackedListener.getListenerRadius();
                            double l = pos.z() + trackedListener.getListenerRadius();
                            LevelRenderer.renderVoxelShape(
                                poseStack, vertexConsumer, Shapes.create(new AABB(gx, hx, ix, jx, k, l)), -camX, -camY, -camZ, 1.0F, 1.0F, 0.0F, 0.35F, true
                            );
                        }
                    );
            }

            VertexConsumer vertexConsumer2 = bufferSource.getBuffer(RenderType.debugFilledBox());

            for (GameEventListenerRenderer.TrackedListener trackedListener2 : this.trackedListeners) {
                trackedListener2.getPosition(level)
                    .ifPresent(
                        position -> LevelRenderer.addChainedFilledBoxVertices(
                            poseStack,
                            vertexConsumer2,
                            position.x() - 0.25 - camX,
                            position.y() - camY,
                            position.z() - 0.25 - camZ,
                            position.x() + 0.25 - camX,
                            position.y() - camY + 1.0,
                            position.z() + 0.25 - camZ,
                            1.0F,
                            1.0F,
                            0.0F,
                            0.35F
                        )
                    );
            }

            for (GameEventListenerRenderer.TrackedListener trackedListener2 : this.trackedListeners) {
                trackedListener2.getPosition(level)
                    .ifPresent(
                        position -> {
                            DebugRenderer.renderFloatingText(
                                poseStack, bufferSource, "Listener Origin", position.x(), position.y() + 1.8F, position.z(), -1, 0.025F
                            );
                            DebugRenderer.renderFloatingText(
                                poseStack,
                                bufferSource,
                                BlockPos.containing(position).toString(),
                                position.x(),
                                position.y() + 1.5,
                                position.z(),
                                -6959665,
                                0.025F
                            );
                        }
                    );
            }

            for (GameEventListenerRenderer.TrackedGameEvent trackedGameEvent : this.trackedGameEvents) {
                Vec3 vec32 = trackedGameEvent.position;
                double d = 0.2F;
                double e = vec32.x - 0.2F;
                double f = vec32.y - 0.2F;
                double g = vec32.z - 0.2F;
                double h = vec32.x + 0.2F;
                double i = vec32.y + 0.2F + 0.5;
                double j = vec32.z + 0.2F;
                renderFilledBox(poseStack, bufferSource, new AABB(e, f, g, h, i, j), 1.0F, 1.0F, 1.0F, 0.2F);
                DebugRenderer.renderFloatingText(
                    poseStack, bufferSource, trackedGameEvent.gameEvent.location().toString(), vec32.x, vec32.y + 0.85F, vec32.z, -7564911, 0.0075F
                );
            }
        }
    }

    private static void renderFilledBox(PoseStack poseStack, MultiBufferSource buffer, AABB boundingBox, float red, float green, float blue, float alpha) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera.isInitialized()) {
            Vec3 vec3 = camera.getPosition().reverse();
            DebugRenderer.renderFilledBox(poseStack, buffer, boundingBox.move(vec3), red, green, blue, alpha);
        }
    }

    public void trackGameEvent(ResourceKey<GameEvent> gameEvent, Vec3 position) {
        this.trackedGameEvents.add(new GameEventListenerRenderer.TrackedGameEvent(Util.getMillis(), gameEvent, position));
    }

    public void trackListener(PositionSource listenerSource, int listenerRange) {
        this.trackedListeners.add(new GameEventListenerRenderer.TrackedListener(listenerSource, listenerRange));
    }

    record TrackedGameEvent(long timeStamp, ResourceKey<GameEvent> gameEvent, Vec3 position) {
        public boolean isExpired() {
            return Util.getMillis() - this.timeStamp > 3000L;
        }
    }

    static class TrackedListener implements GameEventListener {
        public final PositionSource listenerSource;
        public final int listenerRange;

        public TrackedListener(PositionSource listenerSource, int listenerRange) {
            this.listenerSource = listenerSource;
            this.listenerRange = listenerRange;
        }

        public boolean isExpired(Level level, Vec3 pos) {
            return this.listenerSource.getPosition(level).filter(position -> position.distanceToSqr(pos) <= 1024.0).isPresent();
        }

        public Optional<Vec3> getPosition(Level level) {
            return this.listenerSource.getPosition(level);
        }

        @Override
        public PositionSource getListenerSource() {
            return this.listenerSource;
        }

        @Override
        public int getListenerRadius() {
            return this.listenerRange;
        }

        @Override
        public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> gameEvent, GameEvent.Context context, Vec3 pos) {
            return false;
        }
    }
}
