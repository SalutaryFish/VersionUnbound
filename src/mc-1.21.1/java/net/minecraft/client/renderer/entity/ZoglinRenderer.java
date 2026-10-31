package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.HoglinModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zoglin;

public class ZoglinRenderer extends MobRenderer<Zoglin, HoglinModel<Zoglin>> {
    private static final ResourceLocation ZOGLIN_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/hoglin/zoglin.png");

    public ZoglinRenderer(EntityRendererProvider.Context context) {
        super(context, new HoglinModel<>(context.bakeLayer(ModelLayers.ZOGLIN)), 0.7F);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Zoglin entity) {
        return ZOGLIN_LOCATION;
    }
}
