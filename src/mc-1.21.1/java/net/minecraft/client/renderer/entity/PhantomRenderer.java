package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.PhantomEyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Phantom;

public class PhantomRenderer extends MobRenderer<Phantom, PhantomModel<Phantom>> {
    private static final ResourceLocation PHANTOM_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/phantom.png");

    public PhantomRenderer(EntityRendererProvider.Context context) {
        super(context, new PhantomModel<>(context.bakeLayer(ModelLayers.PHANTOM)), 0.75F);
        this.addLayer(new PhantomEyesLayer<>(this));
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Phantom entity) {
        return PHANTOM_LOCATION;
    }

    protected void scale(Phantom livingEntity, PoseStack poseStack, float partialTickTime) {
        int i = livingEntity.getPhantomSize();
        float f = 1.0F + 0.15F * i;
        poseStack.scale(f, f, f);
        poseStack.translate(0.0F, 1.3125F, 0.1875F);
    }

    protected void setupRotations(Phantom entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
    }
}
