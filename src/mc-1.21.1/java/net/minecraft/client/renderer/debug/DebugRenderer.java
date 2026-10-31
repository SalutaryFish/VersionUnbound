package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class DebugRenderer {
    public final PathfindingRenderer pathfindingRenderer = new PathfindingRenderer();
    public final DebugRenderer.SimpleDebugRenderer waterDebugRenderer;
    public final DebugRenderer.SimpleDebugRenderer chunkBorderRenderer;
    public final DebugRenderer.SimpleDebugRenderer heightMapRenderer;
    public final DebugRenderer.SimpleDebugRenderer collisionBoxRenderer;
    public final DebugRenderer.SimpleDebugRenderer supportBlockRenderer;
    public final DebugRenderer.SimpleDebugRenderer neighborsUpdateRenderer;
    public final StructureRenderer structureRenderer;
    public final DebugRenderer.SimpleDebugRenderer lightDebugRenderer;
    public final DebugRenderer.SimpleDebugRenderer worldGenAttemptRenderer;
    public final DebugRenderer.SimpleDebugRenderer solidFaceRenderer;
    public final DebugRenderer.SimpleDebugRenderer chunkRenderer;
    public final BrainDebugRenderer brainDebugRenderer;
    public final VillageSectionsDebugRenderer villageSectionsDebugRenderer;
    public final BeeDebugRenderer beeDebugRenderer;
    public final RaidDebugRenderer raidDebugRenderer;
    public final GoalSelectorDebugRenderer goalSelectorRenderer;
    public final GameTestDebugRenderer gameTestDebugRenderer;
    public final GameEventListenerRenderer gameEventListenerRenderer;
    public final LightSectionDebugRenderer skyLightSectionDebugRenderer;
    public final BreezeDebugRenderer breezeDebugRenderer;
    private boolean renderChunkborder;

    public DebugRenderer(Minecraft minecraft) {
        this.waterDebugRenderer = new WaterDebugRenderer(minecraft);
        this.chunkBorderRenderer = new ChunkBorderRenderer(minecraft);
        this.heightMapRenderer = new HeightMapRenderer(minecraft);
        this.collisionBoxRenderer = new CollisionBoxRenderer(minecraft);
        this.supportBlockRenderer = new SupportBlockRenderer(minecraft);
        this.neighborsUpdateRenderer = new NeighborsUpdateRenderer(minecraft);
        this.structureRenderer = new StructureRenderer(minecraft);
        this.lightDebugRenderer = new LightDebugRenderer(minecraft);
        this.worldGenAttemptRenderer = new WorldGenAttemptRenderer();
        this.solidFaceRenderer = new SolidFaceRenderer(minecraft);
        this.chunkRenderer = new ChunkDebugRenderer(minecraft);
        this.brainDebugRenderer = new BrainDebugRenderer(minecraft);
        this.villageSectionsDebugRenderer = new VillageSectionsDebugRenderer();
        this.beeDebugRenderer = new BeeDebugRenderer(minecraft);
        this.raidDebugRenderer = new RaidDebugRenderer(minecraft);
        this.goalSelectorRenderer = new GoalSelectorDebugRenderer(minecraft);
        this.gameTestDebugRenderer = new GameTestDebugRenderer();
        this.gameEventListenerRenderer = new GameEventListenerRenderer(minecraft);
        this.skyLightSectionDebugRenderer = new LightSectionDebugRenderer(minecraft, LightLayer.SKY);
        this.breezeDebugRenderer = new BreezeDebugRenderer(minecraft);
    }

    public void clear() {
        this.pathfindingRenderer.clear();
        this.waterDebugRenderer.clear();
        this.chunkBorderRenderer.clear();
        this.heightMapRenderer.clear();
        this.collisionBoxRenderer.clear();
        this.supportBlockRenderer.clear();
        this.neighborsUpdateRenderer.clear();
        this.structureRenderer.clear();
        this.lightDebugRenderer.clear();
        this.worldGenAttemptRenderer.clear();
        this.solidFaceRenderer.clear();
        this.chunkRenderer.clear();
        this.brainDebugRenderer.clear();
        this.villageSectionsDebugRenderer.clear();
        this.beeDebugRenderer.clear();
        this.raidDebugRenderer.clear();
        this.goalSelectorRenderer.clear();
        this.gameTestDebugRenderer.clear();
        this.gameEventListenerRenderer.clear();
        this.skyLightSectionDebugRenderer.clear();
        this.breezeDebugRenderer.clear();
    }

    public boolean switchRenderChunkborder() {
        this.renderChunkborder = !this.renderChunkborder;
        return this.renderChunkborder;
    }

    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ) {
        if (this.renderChunkborder && !Minecraft.getInstance().showOnlyReducedInfo()) {
            this.chunkBorderRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }

        this.gameTestDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
    }

    public static Optional<Entity> getTargetedEntity(@Nullable Entity entity, int distance) {
        if (entity == null) {
            return Optional.empty();
        } else {
            Vec3 vec3 = entity.getEyePosition();
            Vec3 vec32 = entity.getViewVector(1.0F).scale(distance);
            Vec3 vec33 = vec3.add(vec32);
            AABB aABB = entity.getBoundingBox().expandTowards(vec32).inflate(1.0);
            int i = distance * distance;
            Predicate<Entity> predicate = entityx -> !entityx.isSpectator() && entityx.isPickable();
            EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(entity, vec3, vec33, aABB, predicate, i);
            if (entityHitResult == null) {
                return Optional.empty();
            } else {
                return vec3.distanceToSqr(entityHitResult.getLocation()) > i ? Optional.empty() : Optional.of(entityHitResult.getEntity());
            }
        }
    }

    public static void renderFilledUnitCube(PoseStack poseStack, MultiBufferSource bufferSource, BlockPos pos, float red, float green, float blue, float alpha) {
        renderFilledBox(poseStack, bufferSource, pos, pos.offset(1, 1, 1), red, green, blue, alpha);
    }

    public static void renderFilledBox(
        PoseStack poseStack, MultiBufferSource bufferSource, BlockPos startPos, BlockPos endPos, float red, float green, float blue, float alpha
    ) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera.isInitialized()) {
            Vec3 vec3 = camera.getPosition().reverse();
            AABB aABB = AABB.encapsulatingFullBlocks(startPos, endPos).move(vec3);
            renderFilledBox(poseStack, bufferSource, aABB, red, green, blue, alpha);
        }
    }

    public static void renderFilledBox(
        PoseStack poseStack, MultiBufferSource bufferSource, BlockPos pos, float scale, float red, float green, float blue, float alpha
    ) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera.isInitialized()) {
            Vec3 vec3 = camera.getPosition().reverse();
            AABB aABB = new AABB(pos).move(vec3).inflate(scale);
            renderFilledBox(poseStack, bufferSource, aABB, red, green, blue, alpha);
        }
    }

    public static void renderFilledBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB boundingBox, float red, float green, float blue, float alpha) {
        renderFilledBox(
            poseStack,
            bufferSource,
            boundingBox.minX,
            boundingBox.minY,
            boundingBox.minZ,
            boundingBox.maxX,
            boundingBox.maxY,
            boundingBox.maxZ,
            red,
            green,
            blue,
            alpha
        );
    }

    public static void renderFilledBox(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.debugFilledBox());
        LevelRenderer.addChainedFilledBoxVertices(poseStack, vertexConsumer, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
    }

    public static void renderFloatingText(PoseStack poseStack, MultiBufferSource bufferSource, String text, int x, int y, int z, int color) {
        renderFloatingText(poseStack, bufferSource, text, x + 0.5, y + 0.5, z + 0.5, color);
    }

    public static void renderFloatingText(PoseStack poseStack, MultiBufferSource bufferSource, String text, double x, double y, double z, int color) {
        renderFloatingText(poseStack, bufferSource, text, x, y, z, color, 0.02F);
    }

    public static void renderFloatingText(
        PoseStack poseStack, MultiBufferSource bufferSource, String text, double x, double y, double z, int color, float scale
    ) {
        renderFloatingText(poseStack, bufferSource, text, x, y, z, color, scale, true, 0.0F, false);
    }

    public static void renderFloatingText(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        String text,
        double x,
        double y,
        double z,
        int color,
        float scale,
        boolean bl,
        float f,
        boolean transparent
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera.isInitialized() && minecraft.getEntityRenderDispatcher().options != null) {
            Font font = minecraft.font;
            double d = camera.getPosition().x;
            double e = camera.getPosition().y;
            double g = camera.getPosition().z;
            poseStack.pushPose();
            poseStack.translate((float)(x - d), (float)(y - e) + 0.07F, (float)(z - g));
            poseStack.mulPose(camera.rotation());
            poseStack.scale(scale, -scale, scale);
            float h = bl ? -font.width(text) / 2.0F : 0.0F;
            h -= f / scale;
            font.drawInBatch(
                text,
                h,
                0.0F,
                color,
                false,
                poseStack.last().pose(),
                bufferSource,
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0,
                15728880
            );
            poseStack.popPose();
        }
    }

    public interface SimpleDebugRenderer {
        void render(PoseStack var1, MultiBufferSource var2, double var3, double var5, double var7);

        default void clear() {
        }
    }
}
