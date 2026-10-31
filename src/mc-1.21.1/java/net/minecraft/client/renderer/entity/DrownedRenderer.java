package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.DrownedModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.DrownedOuterLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Zombie;

public class DrownedRenderer extends AbstractZombieRenderer<Drowned, DrownedModel<Drowned>> {
    private static final ResourceLocation DROWNED_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/zombie/drowned.png");

    public DrownedRenderer(EntityRendererProvider.Context context) {
        super(
            context,
            new DrownedModel<>(context.bakeLayer(ModelLayers.DROWNED)),
            new DrownedModel<>(context.bakeLayer(ModelLayers.DROWNED_INNER_ARMOR)),
            new DrownedModel<>(context.bakeLayer(ModelLayers.DROWNED_OUTER_ARMOR))
        );
        this.addLayer(new DrownedOuterLayer<>(this, context.getModelSet()));
    }

    /**
     * Returns the location of an entity's texture.
     */
    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        return DROWNED_LOCATION;
    }

    protected void setupRotations(Drowned entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
        float f = entity.getSwimAmount(partialTick);
        if (f > 0.0F) {
            float g = -10.0F - entity.getXRot();
            float h = Mth.lerp(f, 0.0F, g);
            poseStack.rotateAround(Axis.XP.rotationDegrees(h), 0.0F, entity.getBbHeight() / 2.0F / scale, 0.0F);
        }
    }
}
