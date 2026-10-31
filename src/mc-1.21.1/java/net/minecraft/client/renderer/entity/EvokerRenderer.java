package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.SpellcasterIllager;

public class EvokerRenderer<T extends SpellcasterIllager> extends IllagerRenderer<T> {
    private static final ResourceLocation EVOKER_ILLAGER = ResourceLocation.withDefaultNamespace("textures/entity/illager/evoker.png");

    public EvokerRenderer(EntityRendererProvider.Context context) {
        super(context, new IllagerModel<>(context.bakeLayer(ModelLayers.EVOKER)), 0.5F);
        this.addLayer(
            new ItemInHandLayer<T, IllagerModel<T>>(this, context.getItemInHandRenderer()) {
                public void render(
                    PoseStack poseStack, MultiBufferSource multiBufferSource, int i, T spellcasterIllager, float f, float g, float h, float j, float k, float l
                ) {
                    if (spellcasterIllager.isCastingSpell()) {
                        super.render(poseStack, multiBufferSource, i, spellcasterIllager, f, g, h, j, k, l);
                    }
                }
            }
        );
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(T entity) {
        return EVOKER_ILLAGER;
    }
}
