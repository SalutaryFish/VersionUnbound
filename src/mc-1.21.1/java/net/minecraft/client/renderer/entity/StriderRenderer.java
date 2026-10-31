package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.StriderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.SaddleLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Strider;

public class StriderRenderer extends MobRenderer<Strider, StriderModel<Strider>> {
    private static final ResourceLocation STRIDER_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/strider/strider.png");
    private static final ResourceLocation COLD_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/strider/strider_cold.png");
    private static final float SHADOW_RADIUS = 0.5F;

    public StriderRenderer(EntityRendererProvider.Context context) {
        super(context, new StriderModel<>(context.bakeLayer(ModelLayers.STRIDER)), 0.5F);
        this.addLayer(
            new SaddleLayer<>(
                this,
                new StriderModel<>(context.bakeLayer(ModelLayers.STRIDER_SADDLE)),
                ResourceLocation.withDefaultNamespace("textures/entity/strider/strider_saddle.png")
            )
        );
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Strider entity) {
        return entity.isSuffocating() ? COLD_LOCATION : STRIDER_LOCATION;
    }

    protected float getShadowRadius(Strider entity) {
        float f = super.getShadowRadius(entity);
        return entity.isBaby() ? f * 0.5F : f;
    }

    protected void scale(Strider livingEntity, PoseStack poseStack, float partialTickTime) {
        float f = livingEntity.getAgeScale();
        poseStack.scale(f, f, f);
    }

    protected boolean isShaking(Strider entity) {
        return super.isShaking(entity) || entity.isSuffocating();
    }
}
