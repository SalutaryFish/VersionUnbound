package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.animal.Wolf;

public class WolfRenderer extends MobRenderer<Wolf, WolfModel<Wolf>> {
    public WolfRenderer(EntityRendererProvider.Context context) {
        super(context, new WolfModel<>(context.bakeLayer(ModelLayers.WOLF)), 0.5F);
        this.addLayer(new WolfArmorLayer(this, context.getModelSet()));
        this.addLayer(new WolfCollarLayer(this));
    }

    /**
     * Defines what float the third param in setRotationAngles of ModelBase is
     */
    protected float getBob(Wolf livingBase, float partialTicks) {
        return livingBase.getTailAngle();
    }

    public void render(Wolf entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.isWet()) {
            float f = entity.getWetShade(partialTicks);
            this.model.setColor(FastColor.ARGB32.colorFromFloat(1.0F, f, f, f));
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        if (entity.isWet()) {
            this.model.setColor(-1);
        }
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Wolf entity) {
        return entity.getTexture();
    }
}
