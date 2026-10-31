package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.DolphinModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.DolphinCarryingItemLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Dolphin;

public class DolphinRenderer extends MobRenderer<Dolphin, DolphinModel<Dolphin>> {
    private static final ResourceLocation DOLPHIN_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/dolphin.png");

    public DolphinRenderer(EntityRendererProvider.Context context) {
        super(context, new DolphinModel<>(context.bakeLayer(ModelLayers.DOLPHIN)), 0.7F);
        this.addLayer(new DolphinCarryingItemLayer(this, context.getItemInHandRenderer()));
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Dolphin entity) {
        return DOLPHIN_LOCATION;
    }
}
