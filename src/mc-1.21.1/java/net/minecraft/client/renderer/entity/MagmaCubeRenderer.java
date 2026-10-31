package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.LavaSlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.MagmaCube;

public class MagmaCubeRenderer extends MobRenderer<MagmaCube, LavaSlimeModel<MagmaCube>> {
    private static final ResourceLocation MAGMACUBE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/slime/magmacube.png");

    public MagmaCubeRenderer(EntityRendererProvider.Context context) {
        super(context, new LavaSlimeModel<>(context.bakeLayer(ModelLayers.MAGMA_CUBE)), 0.25F);
    }

    protected int getBlockLightLevel(MagmaCube entity, BlockPos pos) {
        return 15;
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(MagmaCube entity) {
        return MAGMACUBE_LOCATION;
    }

    public void render(MagmaCube entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.shadowRadius = 0.25F * entity.getSize();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    protected void scale(MagmaCube livingEntity, PoseStack poseStack, float partialTickTime) {
        int i = livingEntity.getSize();
        float f = Mth.lerp(partialTickTime, livingEntity.oSquish, livingEntity.squish) / (i * 0.5F + 1.0F);
        float g = 1.0F / (f + 1.0F);
        poseStack.scale(g * i, 1.0F / g * i, g * i);
    }
}
