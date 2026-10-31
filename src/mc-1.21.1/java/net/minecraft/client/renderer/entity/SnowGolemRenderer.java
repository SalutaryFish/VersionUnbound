package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.SnowGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.SnowGolemHeadLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.SnowGolem;

public class SnowGolemRenderer extends MobRenderer<SnowGolem, SnowGolemModel<SnowGolem>> {
    private static final ResourceLocation SNOW_GOLEM_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/snow_golem.png");

    public SnowGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new SnowGolemModel<>(context.bakeLayer(ModelLayers.SNOW_GOLEM)), 0.5F);
        this.addLayer(new SnowGolemHeadLayer(this, context.getBlockRenderDispatcher(), context.getItemRenderer()));
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(SnowGolem entity) {
        return SNOW_GOLEM_LOCATION;
    }
}
