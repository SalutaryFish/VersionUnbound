package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import javax.annotation.Nullable;
import net.minecraft.client.model.ShulkerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.layers.ShulkerHeadLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ShulkerRenderer extends MobRenderer<Shulker, ShulkerModel<Shulker>> {
    private static final ResourceLocation DEFAULT_TEXTURE_LOCATION = Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION
        .texture()
        .withPath(string -> "textures/" + string + ".png");
    private static final ResourceLocation[] TEXTURE_LOCATION = Sheets.SHULKER_TEXTURE_LOCATION
        .stream()
        .map(material -> material.texture().withPath(string -> "textures/" + string + ".png"))
        .toArray(ResourceLocation[]::new);

    public ShulkerRenderer(EntityRendererProvider.Context context) {
        super(context, new ShulkerModel<>(context.bakeLayer(ModelLayers.SHULKER)), 0.0F);
        this.addLayer(new ShulkerHeadLayer(this));
    }

    public Vec3 getRenderOffset(Shulker entity, float partialTicks) {
        return entity.getRenderPosition(partialTicks).orElse(super.getRenderOffset(entity, partialTicks)).scale(entity.getScale());
    }

    public boolean shouldRender(Shulker livingEntity, Frustum camera, double camX, double camY, double camZ) {
        return super.shouldRender(livingEntity, camera, camX, camY, camZ) ? true : livingEntity.getRenderPosition(0.0F).filter(vec3 -> {
            EntityType<?> entityType = livingEntity.getType();
            float f = entityType.getHeight() / 2.0F;
            float g = entityType.getWidth() / 2.0F;
            Vec3 vec32 = Vec3.atBottomCenterOf(livingEntity.blockPosition());
            return camera.isVisible(new AABB(vec3.x, vec3.y + f, vec3.z, vec32.x, vec32.y + f, vec32.z).inflate(g, f, g));
        }).isPresent();
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Shulker entity) {
        return getTextureLocation(entity.getColor());
    }

    public static ResourceLocation getTextureLocation(@Nullable DyeColor color) {
        return color == null ? DEFAULT_TEXTURE_LOCATION : TEXTURE_LOCATION[color.getId()];
    }

    protected void setupRotations(Shulker entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot + 180.0F, partialTick, scale);
        poseStack.rotateAround(entity.getAttachFace().getOpposite().getRotation(), 0.0F, 0.5F, 0.0F);
    }
}
