package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.BreezeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.BreezeEyesLayer;
import net.minecraft.client.renderer.entity.layers.BreezeWindLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.breeze.Breeze;

public class BreezeRenderer extends MobRenderer<Breeze, BreezeModel<Breeze>> {
    private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze.png");

    public BreezeRenderer(EntityRendererProvider.Context context) {
        super(context, new BreezeModel<>(context.bakeLayer(ModelLayers.BREEZE)), 0.5F);
        this.addLayer(new BreezeWindLayer(context, this));
        this.addLayer(new BreezeEyesLayer(this));
    }

    public void render(Breeze entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        BreezeModel<Breeze> breezeModel = this.getModel();
        enable(breezeModel, breezeModel.head(), breezeModel.rods());
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Breeze entity) {
        return TEXTURE_LOCATION;
    }

    public static BreezeModel<Breeze> enable(BreezeModel<Breeze> model, ModelPart... parts) {
        model.head().visible = false;
        model.eyes().visible = false;
        model.rods().visible = false;
        model.wind().visible = false;

        for (ModelPart modelPart : parts) {
            modelPart.visible = true;
        }

        return model;
    }
}
