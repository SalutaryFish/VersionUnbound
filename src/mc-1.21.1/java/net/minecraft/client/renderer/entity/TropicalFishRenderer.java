package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.ColorableHierarchicalModel;
import net.minecraft.client.model.TropicalFishModelA;
import net.minecraft.client.model.TropicalFishModelB;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.TropicalFishPatternLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.TropicalFish;

public class TropicalFishRenderer extends MobRenderer<TropicalFish, ColorableHierarchicalModel<TropicalFish>> {
    /**
     * Breaking recompile intentionally since modelA/B incorrectly mapped.
     */
    private final ColorableHierarchicalModel<TropicalFish> modelA = this.getModel();
    /**
     * Breaking recompile intentionally since modelA/B incorrectly mapped.
     */
    private final ColorableHierarchicalModel<TropicalFish> modelB;
    private static final ResourceLocation MODEL_A_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a.png");
    private static final ResourceLocation MODEL_B_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b.png");

    public TropicalFishRenderer(EntityRendererProvider.Context context) {
        super(context, new TropicalFishModelA<>(context.bakeLayer(ModelLayers.TROPICAL_FISH_SMALL)), 0.15F);
        this.modelB = new TropicalFishModelB<>(context.bakeLayer(ModelLayers.TROPICAL_FISH_LARGE));
        this.addLayer(new TropicalFishPatternLayer(this, context.getModelSet()));
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(TropicalFish entity) {
        return switch (entity.getVariant().base()) {
            case SMALL -> MODEL_A_TEXTURE;
            case LARGE -> MODEL_B_TEXTURE;
        };
    }

    public void render(TropicalFish entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ColorableHierarchicalModel<TropicalFish> colorableHierarchicalModel = switch (entity.getVariant().base()) {
            case SMALL -> this.modelA;
            case LARGE -> this.modelB;
        };
        this.model = colorableHierarchicalModel;
        colorableHierarchicalModel.setColor(entity.getBaseColor().getTextureDiffuseColor());
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        colorableHierarchicalModel.setColor(-1);
    }

    protected void setupRotations(TropicalFish entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
        float f = 4.3F * Mth.sin(0.6F * bob);
        poseStack.mulPose(Axis.YP.rotationDegrees(f));
        if (!entity.isInWater()) {
            poseStack.translate(0.2F, 0.1F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }
    }
}
