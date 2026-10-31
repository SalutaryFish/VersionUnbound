package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class EntityRenderDispatcher implements ResourceManagerReloadListener {
    private static final RenderType SHADOW_RENDER_TYPE = RenderType.entityShadow(ResourceLocation.withDefaultNamespace("textures/misc/shadow.png"));
    private static final float MAX_SHADOW_RADIUS = 32.0F;
    private static final float SHADOW_POWER_FALLOFF_Y = 0.5F;
    private Map<EntityType<?>, EntityRenderer<?>> renderers = ImmutableMap.of();
    /**
     * lists the various player skin types with their associated Renderer class instances.
     */
    private Map<PlayerSkin.Model, EntityRenderer<? extends Player>> playerRenderers = Map.of();
    public final TextureManager textureManager;
    private Level level;
    public Camera camera;
    private Quaternionf cameraOrientation;
    public Entity crosshairPickEntity;
    private final ItemRenderer itemRenderer;
    private final BlockRenderDispatcher blockRenderDispatcher;
    private final ItemInHandRenderer itemInHandRenderer;
    private final Font font;
    public final Options options;
    private final EntityModelSet entityModels;
    private boolean shouldRenderShadow = true;
    private boolean renderHitBoxes;

    public <E extends Entity> int getPackedLightCoords(E entity, float partialTicks) {
        return this.getRenderer(entity).getPackedLightCoords(entity, partialTicks);
    }

    public EntityRenderDispatcher(
        Minecraft minecraft,
        TextureManager textureManager,
        ItemRenderer itemRenderer,
        BlockRenderDispatcher blockRenderDispatcher,
        Font font,
        Options options,
        EntityModelSet entityModels
    ) {
        this.textureManager = textureManager;
        this.itemRenderer = itemRenderer;
        this.itemInHandRenderer = new ItemInHandRenderer(minecraft, this, itemRenderer);
        this.blockRenderDispatcher = blockRenderDispatcher;
        this.font = font;
        this.options = options;
        this.entityModels = entityModels;
    }

    public <T extends Entity> EntityRenderer<? super T> getRenderer(T entity) {
        if (entity instanceof AbstractClientPlayer abstractClientPlayer) {
            PlayerSkin.Model model = abstractClientPlayer.getSkin().model();
            EntityRenderer<? extends Player> entityRenderer = this.playerRenderers.get(model);
            return (EntityRenderer<? super T>)(entityRenderer != null ? entityRenderer : this.playerRenderers.get(PlayerSkin.Model.WIDE));
        } else {
            return (EntityRenderer<? super T>)this.renderers.get(entity.getType());
        }
    }

    public void prepare(Level level, Camera activeRenderInfo, Entity entity) {
        this.level = level;
        this.camera = activeRenderInfo;
        this.cameraOrientation = activeRenderInfo.rotation();
        this.crosshairPickEntity = entity;
    }

    public void overrideCameraOrientation(Quaternionf cameraOrientation) {
        this.cameraOrientation = cameraOrientation;
    }

    public void setRenderShadow(boolean renderShadow) {
        this.shouldRenderShadow = renderShadow;
    }

    public void setRenderHitBoxes(boolean debugBoundingBox) {
        this.renderHitBoxes = debugBoundingBox;
    }

    public boolean shouldRenderHitBoxes() {
        return this.renderHitBoxes;
    }

    public <E extends Entity> boolean shouldRender(E entity, Frustum frustum, double camX, double camY, double camZ) {
        EntityRenderer<? super E> entityRenderer = this.getRenderer(entity);
        return entityRenderer.shouldRender(entity, frustum, camX, camY, camZ);
    }

    public <E extends Entity> void render(
        E entity, double x, double y, double z, float rotationYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight
    ) {
        EntityRenderer<? super E> entityRenderer = this.getRenderer(entity);

        try {
            Vec3 vec3 = entityRenderer.getRenderOffset(entity, partialTicks);
            double d = x + vec3.x();
            double e = y + vec3.y();
            double f = z + vec3.z();
            poseStack.pushPose();
            poseStack.translate(d, e, f);
            entityRenderer.render(entity, rotationYaw, partialTicks, poseStack, buffer, packedLight);
            if (entity.displayFireAnimation()) {
                this.renderFlame(poseStack, buffer, entity, Mth.rotationAroundAxis(Mth.Y_AXIS, this.cameraOrientation, new Quaternionf()));
            }

            poseStack.translate(-vec3.x(), -vec3.y(), -vec3.z());
            if (this.options.entityShadows().get() && this.shouldRenderShadow && !entity.isInvisible()) {
                float g = entityRenderer.getShadowRadius(entity);
                if (g > 0.0F) {
                    double h = this.distanceToSqr(entity.getX(), entity.getY(), entity.getZ());
                    float i = (float)((1.0 - h / 256.0) * entityRenderer.shadowStrength);
                    if (i > 0.0F) {
                        renderShadow(poseStack, buffer, entity, i, partialTicks, this.level, Math.min(g, 32.0F));
                    }
                }
            }

            if (this.renderHitBoxes && !entity.isInvisible() && !Minecraft.getInstance().showOnlyReducedInfo()) {
                renderHitbox(poseStack, buffer.getBuffer(RenderType.lines()), entity, partialTicks, 1.0F, 1.0F, 1.0F);
            }

            poseStack.popPose();
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering entity in world");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being rendered");
            entity.fillCrashReportCategory(crashReportCategory);
            CrashReportCategory crashReportCategory2 = crashReport.addCategory("Renderer details");
            crashReportCategory2.setDetail("Assigned renderer", entityRenderer);
            crashReportCategory2.setDetail("Location", CrashReportCategory.formatLocation(this.level, x, y, z));
            crashReportCategory2.setDetail("Rotation", rotationYaw);
            crashReportCategory2.setDetail("Delta", partialTicks);
            throw new ReportedException(crashReport);
        }
    }

    private static void renderServerSideHitbox(PoseStack poseStack, Entity entity, MultiBufferSource bufferSource) {
        Entity entity2 = getServerSideEntity(entity);
        if (entity2 == null) {
            DebugRenderer.renderFloatingText(poseStack, bufferSource, "Missing", entity.getX(), entity.getBoundingBox().maxY + 1.5, entity.getZ(), -65536);
        } else {
            poseStack.pushPose();
            poseStack.translate(entity2.getX() - entity.getX(), entity2.getY() - entity.getY(), entity2.getZ() - entity.getZ());
            renderHitbox(poseStack, bufferSource.getBuffer(RenderType.lines()), entity2, 1.0F, 0.0F, 1.0F, 0.0F);
            renderVector(poseStack, bufferSource.getBuffer(RenderType.lines()), new Vector3f(), entity2.getDeltaMovement(), -256);
            poseStack.popPose();
        }
    }

    @Nullable
    private static Entity getServerSideEntity(Entity entity) {
        IntegratedServer integratedServer = Minecraft.getInstance().getSingleplayerServer();
        if (integratedServer != null) {
            ServerLevel serverLevel = integratedServer.getLevel(entity.level().dimension());
            if (serverLevel != null) {
                return serverLevel.getEntity(entity.getId());
            }
        }

        return null;
    }

    private static void renderHitbox(PoseStack poseStack, VertexConsumer buffer, Entity entity, float red, float green, float blue, float alpha) {
        AABB aABB = entity.getBoundingBox().move(-entity.getX(), -entity.getY(), -entity.getZ());
        LevelRenderer.renderLineBox(poseStack, buffer, aABB, green, blue, alpha, 1.0F);
        if (entity instanceof EnderDragon) {
            double d = -Mth.lerp(red, entity.xOld, entity.getX());
            double e = -Mth.lerp(red, entity.yOld, entity.getY());
            double f = -Mth.lerp(red, entity.zOld, entity.getZ());

            for (EnderDragonPart enderDragonPart : ((EnderDragon)entity).getSubEntities()) {
                poseStack.pushPose();
                double g = d + Mth.lerp(red, enderDragonPart.xOld, enderDragonPart.getX());
                double h = e + Mth.lerp(red, enderDragonPart.yOld, enderDragonPart.getY());
                double i = f + Mth.lerp(red, enderDragonPart.zOld, enderDragonPart.getZ());
                poseStack.translate(g, h, i);
                LevelRenderer.renderLineBox(
                    poseStack,
                    buffer,
                    enderDragonPart.getBoundingBox().move(-enderDragonPart.getX(), -enderDragonPart.getY(), -enderDragonPart.getZ()),
                    0.25F,
                    1.0F,
                    0.0F,
                    1.0F
                );
                poseStack.popPose();
            }
        }

        if (entity instanceof LivingEntity) {
            float j = 0.01F;
            LevelRenderer.renderLineBox(
                poseStack,
                buffer,
                aABB.minX,
                entity.getEyeHeight() - 0.01F,
                aABB.minZ,
                aABB.maxX,
                entity.getEyeHeight() + 0.01F,
                aABB.maxZ,
                1.0F,
                0.0F,
                0.0F,
                1.0F
            );
        }

        Entity entity2 = entity.getVehicle();
        if (entity2 != null) {
            float k = Math.min(entity2.getBbWidth(), entity.getBbWidth()) / 2.0F;
            float l = 0.0625F;
            Vec3 vec3 = entity2.getPassengerRidingPosition(entity).subtract(entity.position());
            LevelRenderer.renderLineBox(poseStack, buffer, vec3.x - k, vec3.y, vec3.z - k, vec3.x + k, vec3.y + 0.0625, vec3.z + k, 1.0F, 1.0F, 0.0F, 1.0F);
        }

        renderVector(poseStack, buffer, new Vector3f(0.0F, entity.getEyeHeight(), 0.0F), entity.getViewVector(red).scale(2.0), -16776961);
    }

    private static void renderVector(PoseStack poseStack, VertexConsumer buffer, Vector3f startPos, Vec3 vector, int color) {
        PoseStack.Pose pose = poseStack.last();
        buffer.addVertex(pose, startPos).setColor(color).setNormal(pose, (float)vector.x, (float)vector.y, (float)vector.z);
        buffer.addVertex(pose, (float)(startPos.x() + vector.x), (float)(startPos.y() + vector.y), (float)(startPos.z() + vector.z))
            .setColor(color)
            .setNormal(pose, (float)vector.x, (float)vector.y, (float)vector.z);
    }

    private void renderFlame(PoseStack poseStack, MultiBufferSource buffer, Entity entity, Quaternionf quaternion) {
        TextureAtlasSprite textureAtlasSprite = ModelBakery.FIRE_0.sprite();
        TextureAtlasSprite textureAtlasSprite2 = ModelBakery.FIRE_1.sprite();
        poseStack.pushPose();
        float f = entity.getBbWidth() * 1.4F;
        poseStack.scale(f, f, f);
        float g = 0.5F;
        float h = 0.0F;
        float i = entity.getBbHeight() / f;
        float j = 0.0F;
        poseStack.mulPose(quaternion);
        poseStack.translate(0.0F, 0.0F, 0.3F - (int)i * 0.02F);
        float k = 0.0F;
        int l = 0;
        VertexConsumer vertexConsumer = buffer.getBuffer(Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = poseStack.last();

        while (i > 0.0F) {
            TextureAtlasSprite textureAtlasSprite3 = l % 2 == 0 ? textureAtlasSprite : textureAtlasSprite2;
            float m = textureAtlasSprite3.getU0();
            float n = textureAtlasSprite3.getV0();
            float o = textureAtlasSprite3.getU1();
            float p = textureAtlasSprite3.getV1();
            if (l / 2 % 2 == 0) {
                float q = o;
                o = m;
                m = q;
            }

            fireVertex(pose, vertexConsumer, -g - 0.0F, 0.0F - j, k, o, p);
            fireVertex(pose, vertexConsumer, g - 0.0F, 0.0F - j, k, m, p);
            fireVertex(pose, vertexConsumer, g - 0.0F, 1.4F - j, k, m, n);
            fireVertex(pose, vertexConsumer, -g - 0.0F, 1.4F - j, k, o, n);
            i -= 0.45F;
            j -= 0.45F;
            g *= 0.9F;
            k -= 0.03F;
            l++;
        }

        poseStack.popPose();
    }

    private static void fireVertex(PoseStack.Pose matrixEntry, VertexConsumer buffer, float x, float y, float z, float texU, float texV) {
        buffer.addVertex(matrixEntry, x, y, z).setColor(-1).setUv(texU, texV).setUv1(0, 10).setLight(240).setNormal(matrixEntry, 0.0F, 1.0F, 0.0F);
    }

    private static void renderShadow(
        PoseStack poseStack, MultiBufferSource buffer, Entity entity, float weight, float partialTicks, LevelReader level, float size
    ) {
        double d = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double e = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        double f = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        float g = Math.min(weight / 0.5F, size);
        int i = Mth.floor(d - size);
        int j = Mth.floor(d + size);
        int k = Mth.floor(e - g);
        int l = Mth.floor(e);
        int m = Mth.floor(f - size);
        int n = Mth.floor(f + size);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vertexConsumer = buffer.getBuffer(SHADOW_RENDER_TYPE);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int o = m; o <= n; o++) {
            for (int p = i; p <= j; p++) {
                mutableBlockPos.set(p, 0, o);
                ChunkAccess chunkAccess = level.getChunk(mutableBlockPos);

                for (int q = k; q <= l; q++) {
                    mutableBlockPos.setY(q);
                    float h = weight - (float)(e - mutableBlockPos.getY()) * 0.5F;
                    renderBlockShadow(pose, vertexConsumer, chunkAccess, level, mutableBlockPos, d, e, f, size, h);
                }
            }
        }
    }

    private static void renderBlockShadow(
        PoseStack.Pose pose,
        VertexConsumer vertexConsumer,
        ChunkAccess chunk,
        LevelReader level,
        BlockPos pos,
        double x,
        double y,
        double z,
        float size,
        float weight
    ) {
        BlockPos blockPos = pos.below();
        BlockState blockState = chunk.getBlockState(blockPos);
        if (blockState.getRenderShape() != RenderShape.INVISIBLE && level.getMaxLocalRawBrightness(pos) > 3) {
            if (blockState.isCollisionShapeFullBlock(chunk, blockPos)) {
                VoxelShape voxelShape = blockState.getShape(chunk, blockPos);
                if (!voxelShape.isEmpty()) {
                    float f = LightTexture.getBrightness(level.dimensionType(), level.getMaxLocalRawBrightness(pos));
                    float g = weight * 0.5F * f;
                    if (g >= 0.0F) {
                        if (g > 1.0F) {
                            g = 1.0F;
                        }

                        int i = FastColor.ARGB32.color(Mth.floor(g * 255.0F), 255, 255, 255);
                        AABB aABB = voxelShape.bounds();
                        double d = pos.getX() + aABB.minX;
                        double e = pos.getX() + aABB.maxX;
                        double h = pos.getY() + aABB.minY;
                        double j = pos.getZ() + aABB.minZ;
                        double k = pos.getZ() + aABB.maxZ;
                        float l = (float)(d - x);
                        float m = (float)(e - x);
                        float n = (float)(h - y);
                        float o = (float)(j - z);
                        float p = (float)(k - z);
                        float q = -l / 2.0F / size + 0.5F;
                        float r = -m / 2.0F / size + 0.5F;
                        float s = -o / 2.0F / size + 0.5F;
                        float t = -p / 2.0F / size + 0.5F;
                        shadowVertex(pose, vertexConsumer, i, l, n, o, q, s);
                        shadowVertex(pose, vertexConsumer, i, l, n, p, q, t);
                        shadowVertex(pose, vertexConsumer, i, m, n, p, r, t);
                        shadowVertex(pose, vertexConsumer, i, m, n, o, r, s);
                    }
                }
            }
        }
    }

    private static void shadowVertex(PoseStack.Pose pose, VertexConsumer consumer, int color, float offsetX, float offsetY, float offsetZ, float u, float v) {
        Vector3f vector3f = pose.pose().transformPosition(offsetX, offsetY, offsetZ, new Vector3f());
        consumer.addVertex(vector3f.x(), vector3f.y(), vector3f.z(), color, u, v, OverlayTexture.NO_OVERLAY, 15728880, 0.0F, 1.0F, 0.0F);
    }

    /**
     * World sets this RenderManager's worldObj to the world provided
     */
    public void setLevel(@Nullable Level level) {
        this.level = level;
        if (level == null) {
            this.camera = null;
        }
    }

    public double distanceToSqr(Entity entity) {
        return this.camera.getPosition().distanceToSqr(entity.position());
    }

    public double distanceToSqr(double x, double y, double z) {
        return this.camera.getPosition().distanceToSqr(x, y, z);
    }

    public Quaternionf cameraOrientation() {
        return this.cameraOrientation;
    }

    public ItemInHandRenderer getItemInHandRenderer() {
        return this.itemInHandRenderer;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        EntityRendererProvider.Context context = new EntityRendererProvider.Context(
            this, this.itemRenderer, this.blockRenderDispatcher, this.itemInHandRenderer, resourceManager, this.entityModels, this.font
        );
        this.renderers = EntityRenderers.createEntityRenderers(context);
        this.playerRenderers = EntityRenderers.createPlayerRenderers(context);
    }
}
