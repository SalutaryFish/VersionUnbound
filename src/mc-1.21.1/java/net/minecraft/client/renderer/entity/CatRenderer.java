package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.CatModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.CatCollarLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class CatRenderer extends MobRenderer<Cat, CatModel<Cat>> {
    public CatRenderer(EntityRendererProvider.Context context) {
        super(context, new CatModel<>(context.bakeLayer(ModelLayers.CAT)), 0.4F);
        this.addLayer(new CatCollarLayer(this, context.getModelSet()));
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Cat entity) {
        return entity.getTextureId();
    }

    protected void scale(Cat livingEntity, PoseStack poseStack, float partialTickTime) {
        super.scale(livingEntity, poseStack, partialTickTime);
        poseStack.scale(0.8F, 0.8F, 0.8F);
    }

    protected void setupRotations(Cat entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
        float f = entity.getLieDownAmount(partialTick);
        if (f > 0.0F) {
            poseStack.translate(0.4F * f, 0.15F * f, 0.1F * f);
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.rotLerp(f, 0.0F, 90.0F)));
            BlockPos blockPos = entity.blockPosition();

            for (Player player : entity.level().getEntitiesOfClass(Player.class, new AABB(blockPos).inflate(2.0, 2.0, 2.0))) {
                if (player.isSleeping()) {
                    poseStack.translate(0.15F * f, 0.0F, 0.0F);
                    break;
                }
            }
        }
    }
}
