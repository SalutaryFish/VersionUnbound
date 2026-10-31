package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.TurtleModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Turtle;

public class TurtleRenderer extends MobRenderer<Turtle, TurtleModel<Turtle>> {
    private static final ResourceLocation TURTLE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/turtle/big_sea_turtle.png");

    public TurtleRenderer(EntityRendererProvider.Context context) {
        super(context, new TurtleModel<>(context.bakeLayer(ModelLayers.TURTLE)), 0.7F);
    }

    protected float getShadowRadius(Turtle entity) {
        float f = super.getShadowRadius(entity);
        return entity.isBaby() ? f * 0.83F : f;
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Turtle entity) {
        return TURTLE_LOCATION;
    }
}
