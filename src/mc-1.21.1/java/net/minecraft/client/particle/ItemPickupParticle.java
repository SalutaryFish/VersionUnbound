package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

public class ItemPickupParticle extends Particle {
    private static final int LIFE_TIME = 3;
    private final RenderBuffers renderBuffers;
    private final Entity itemEntity;
    private final Entity target;
    private int life;
    private final EntityRenderDispatcher entityRenderDispatcher;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double targetXOld;
    private double targetYOld;
    private double targetZOld;

    public ItemPickupParticle(EntityRenderDispatcher entityRenderDispatcher, RenderBuffers buffers, ClientLevel level, Entity itemEntity, Entity target) {
        this(entityRenderDispatcher, buffers, level, itemEntity, target, itemEntity.getDeltaMovement());
    }

    private ItemPickupParticle(
        EntityRenderDispatcher entityRenderDispatcher, RenderBuffers buffers, ClientLevel level, Entity itemEntity, Entity target, Vec3 speedVector
    ) {
        super(level, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), speedVector.x, speedVector.y, speedVector.z);
        this.renderBuffers = buffers;
        this.itemEntity = this.getSafeCopy(itemEntity);
        this.target = target;
        this.entityRenderDispatcher = entityRenderDispatcher;
        this.updatePosition();
        this.saveOldPosition();
    }

    private Entity getSafeCopy(Entity entity) {
        return !(entity instanceof ItemEntity) ? entity : ((ItemEntity)entity).copy();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }

    @Override
    public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
        float f = (this.life + partialTicks) / 3.0F;
        f *= f;
        double d = Mth.lerp(partialTicks, this.targetXOld, this.targetX);
        double e = Mth.lerp(partialTicks, this.targetYOld, this.targetY);
        double g = Mth.lerp(partialTicks, this.targetZOld, this.targetZ);
        double h = Mth.lerp(f, this.itemEntity.getX(), d);
        double i = Mth.lerp(f, this.itemEntity.getY(), e);
        double j = Mth.lerp(f, this.itemEntity.getZ(), g);
        MultiBufferSource.BufferSource bufferSource = this.renderBuffers.bufferSource();
        Vec3 vec3 = renderInfo.getPosition();
        this.entityRenderDispatcher
            .render(
                this.itemEntity,
                h - vec3.x(),
                i - vec3.y(),
                j - vec3.z(),
                this.itemEntity.getYRot(),
                partialTicks,
                new PoseStack(),
                bufferSource,
                this.entityRenderDispatcher.getPackedLightCoords(this.itemEntity, partialTicks)
            );
        bufferSource.endBatch();
    }

    @Override
    public void tick() {
        this.life++;
        if (this.life == 3) {
            this.remove();
        }

        this.saveOldPosition();
        this.updatePosition();
    }

    private void updatePosition() {
        this.targetX = this.target.getX();
        this.targetY = (this.target.getY() + this.target.getEyeY()) / 2.0;
        this.targetZ = this.target.getZ();
    }

    private void saveOldPosition() {
        this.targetXOld = this.targetX;
        this.targetYOld = this.targetY;
        this.targetZOld = this.targetZ;
    }
}
