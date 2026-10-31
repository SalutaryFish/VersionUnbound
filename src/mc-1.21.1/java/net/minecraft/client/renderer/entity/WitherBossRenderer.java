package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.WitherBossModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.WitherArmorLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.wither.WitherBoss;

public class WitherBossRenderer extends MobRenderer<WitherBoss, WitherBossModel<WitherBoss>> {
    private static final ResourceLocation WITHER_INVULNERABLE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/wither/wither_invulnerable.png");
    private static final ResourceLocation WITHER_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/wither/wither.png");

    public WitherBossRenderer(EntityRendererProvider.Context context) {
        super(context, new WitherBossModel<>(context.bakeLayer(ModelLayers.WITHER)), 1.0F);
        this.addLayer(new WitherArmorLayer(this, context.getModelSet()));
    }

    protected int getBlockLightLevel(WitherBoss entity, BlockPos pos) {
        return 15;
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(WitherBoss entity) {
        int i = entity.getInvulnerableTicks();
        return i > 0 && (i > 80 || i / 5 % 2 != 1) ? WITHER_INVULNERABLE_LOCATION : WITHER_LOCATION;
    }

    protected void scale(WitherBoss livingEntity, PoseStack poseStack, float partialTickTime) {
        float f = 2.0F;
        int i = livingEntity.getInvulnerableTicks();
        if (i > 0) {
            f -= (i - partialTickTime) / 220.0F * 0.5F;
        }

        poseStack.scale(f, f, f);
    }
}
