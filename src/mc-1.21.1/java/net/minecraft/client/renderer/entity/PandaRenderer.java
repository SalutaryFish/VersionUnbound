package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.model.PandaModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.PandaHoldsItemLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Panda;

public class PandaRenderer extends MobRenderer<Panda, PandaModel<Panda>> {
    private static final Map<Panda.Gene, ResourceLocation> TEXTURES = Util.make(Maps.newEnumMap(Panda.Gene.class), map -> {
        map.put(Panda.Gene.NORMAL, ResourceLocation.withDefaultNamespace("textures/entity/panda/panda.png"));
        map.put(Panda.Gene.LAZY, ResourceLocation.withDefaultNamespace("textures/entity/panda/lazy_panda.png"));
        map.put(Panda.Gene.WORRIED, ResourceLocation.withDefaultNamespace("textures/entity/panda/worried_panda.png"));
        map.put(Panda.Gene.PLAYFUL, ResourceLocation.withDefaultNamespace("textures/entity/panda/playful_panda.png"));
        map.put(Panda.Gene.BROWN, ResourceLocation.withDefaultNamespace("textures/entity/panda/brown_panda.png"));
        map.put(Panda.Gene.WEAK, ResourceLocation.withDefaultNamespace("textures/entity/panda/weak_panda.png"));
        map.put(Panda.Gene.AGGRESSIVE, ResourceLocation.withDefaultNamespace("textures/entity/panda/aggressive_panda.png"));
    });

    public PandaRenderer(EntityRendererProvider.Context context) {
        super(context, new PandaModel<>(context.bakeLayer(ModelLayers.PANDA)), 0.9F);
        this.addLayer(new PandaHoldsItemLayer(this, context.getItemInHandRenderer()));
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Panda entity) {
        return TEXTURES.getOrDefault(entity.getVariant(), TEXTURES.get(Panda.Gene.NORMAL));
    }

    protected void setupRotations(Panda entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
        if (entity.rollCounter > 0) {
            int i = entity.rollCounter;
            int j = i + 1;
            float f = 7.0F;
            float g = entity.isBaby() ? 0.3F : 0.8F;
            if (i < 8) {
                float h = 90 * i / 7.0F;
                float k = 90 * j / 7.0F;
                float l = this.getAngle(h, k, j, partialTick, 8.0F);
                poseStack.translate(0.0F, (g + 0.2F) * (l / 90.0F), 0.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-l));
            } else if (i < 16) {
                float h = (i - 8.0F) / 7.0F;
                float k = 90.0F + 90.0F * h;
                float m = 90.0F + 90.0F * (j - 8.0F) / 7.0F;
                float l = this.getAngle(k, m, j, partialTick, 16.0F);
                poseStack.translate(0.0F, g + 0.2F + (g - 0.2F) * (l - 90.0F) / 90.0F, 0.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-l));
            } else if (i < 24.0F) {
                float h = (i - 16.0F) / 7.0F;
                float k = 180.0F + 90.0F * h;
                float m = 180.0F + 90.0F * (j - 16.0F) / 7.0F;
                float l = this.getAngle(k, m, j, partialTick, 24.0F);
                poseStack.translate(0.0F, g + g * (270.0F - l) / 90.0F, 0.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-l));
            } else if (i < 32) {
                float h = (i - 24.0F) / 7.0F;
                float k = 270.0F + 90.0F * h;
                float m = 270.0F + 90.0F * (j - 24.0F) / 7.0F;
                float l = this.getAngle(k, m, j, partialTick, 32.0F);
                poseStack.translate(0.0F, g * ((360.0F - l) / 90.0F), 0.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-l));
            }
        }

        float n = entity.getSitAmount(partialTick);
        if (n > 0.0F) {
            poseStack.translate(0.0F, 0.8F * n, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(n, entity.getXRot(), entity.getXRot() + 90.0F)));
            poseStack.translate(0.0F, -1.0F * n, 0.0F);
            if (entity.isScared()) {
                float o = (float)(Math.cos(entity.tickCount * 1.25) * Math.PI * 0.05F);
                poseStack.mulPose(Axis.YP.rotationDegrees(o));
                if (entity.isBaby()) {
                    poseStack.translate(0.0F, 0.8F, 0.55F);
                }
            }
        }

        float o = entity.getLieOnBackAmount(partialTick);
        if (o > 0.0F) {
            float f = entity.isBaby() ? 0.5F : 1.3F;
            poseStack.translate(0.0F, f * o, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(o, entity.getXRot(), entity.getXRot() + 180.0F)));
        }
    }

    private float getAngle(float currentAngle, float nextAngle, int nextRollCounter, float partialTick, float rollEndCount) {
        return nextRollCounter < rollEndCount ? Mth.lerp(partialTick, currentAngle, nextAngle) : currentAngle;
    }
}
