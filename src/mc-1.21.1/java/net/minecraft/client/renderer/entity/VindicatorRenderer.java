package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Vindicator;

public class VindicatorRenderer extends IllagerRenderer<Vindicator> {
    private static final ResourceLocation VINDICATOR = ResourceLocation.withDefaultNamespace("textures/entity/illager/vindicator.png");

    public VindicatorRenderer(EntityRendererProvider.Context context) {
        super(context, new IllagerModel<>(context.bakeLayer(ModelLayers.VINDICATOR)), 0.5F);
        this.addLayer(
            new ItemInHandLayer<Vindicator, IllagerModel<Vindicator>>(this, context.getItemInHandRenderer()) {
                public void render(
                    PoseStack poseStack,
                    MultiBufferSource multiBufferSource,
                    int i,
                    Vindicator vindicator,
                    float f,
                    float g,
                    float h,
                    float j,
                    float k,
                    float l
                ) {
                    if (vindicator.isAggressive()) {
                        super.render(poseStack, multiBufferSource, i, vindicator, f, g, h, j, k, l);
                    }
                }
            }
        );
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Vindicator entity) {
        return VINDICATOR;
    }
}
