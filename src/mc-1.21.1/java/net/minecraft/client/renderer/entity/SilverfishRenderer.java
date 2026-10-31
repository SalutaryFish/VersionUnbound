package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.SilverfishModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Silverfish;

public class SilverfishRenderer extends MobRenderer<Silverfish, SilverfishModel<Silverfish>> {
    private static final ResourceLocation SILVERFISH_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/silverfish.png");

    public SilverfishRenderer(EntityRendererProvider.Context context) {
        super(context, new SilverfishModel<>(context.bakeLayer(ModelLayers.SILVERFISH)), 0.3F);
    }

    protected float getFlipDegrees(Silverfish livingEntity) {
        return 180.0F;
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Silverfish entity) {
        return SILVERFISH_LOCATION;
    }
}
