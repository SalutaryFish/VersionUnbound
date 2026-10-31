package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.OcelotModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Ocelot;

public class OcelotRenderer extends MobRenderer<Ocelot, OcelotModel<Ocelot>> {
    private static final ResourceLocation CAT_OCELOT_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/cat/ocelot.png");

    public OcelotRenderer(EntityRendererProvider.Context context) {
        super(context, new OcelotModel<>(context.bakeLayer(ModelLayers.OCELOT)), 0.4F);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Ocelot entity) {
        return CAT_OCELOT_LOCATION;
    }
}
