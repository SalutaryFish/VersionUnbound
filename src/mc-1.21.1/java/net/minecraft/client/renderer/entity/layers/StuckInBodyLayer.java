package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public abstract class StuckInBodyLayer<T extends LivingEntity, M extends PlayerModel<T>> extends RenderLayer<T, M> {
    public StuckInBodyLayer(LivingEntityRenderer<T, M> renderer) {
        super(renderer);
    }

    protected abstract int numStuck(T var1);

    protected abstract void renderStuckItem(PoseStack var1, MultiBufferSource var2, int var3, Entity var4, float var5, float var6, float var7, float var8);

    public void render(
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        T livingEntity,
        float limbSwing,
        float limbSwingAmount,
        float partialTicks,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        int i = this.numStuck(livingEntity);
        RandomSource randomSource = RandomSource.create(livingEntity.getId());
        if (i > 0) {
            for (int j = 0; j < i; j++) {
                poseStack.pushPose();
                ModelPart modelPart = this.getParentModel().getRandomModelPart(randomSource);
                ModelPart.Cube cube = modelPart.getRandomCube(randomSource);
                modelPart.translateAndRotate(poseStack);
                float f = randomSource.nextFloat();
                float g = randomSource.nextFloat();
                float h = randomSource.nextFloat();
                float k = Mth.lerp(f, cube.minX, cube.maxX) / 16.0F;
                float l = Mth.lerp(g, cube.minY, cube.maxY) / 16.0F;
                float m = Mth.lerp(h, cube.minZ, cube.maxZ) / 16.0F;
                poseStack.translate(k, l, m);
                f = -1.0F * (f * 2.0F - 1.0F);
                g = -1.0F * (g * 2.0F - 1.0F);
                h = -1.0F * (h * 2.0F - 1.0F);
                this.renderStuckItem(poseStack, buffer, packedLight, livingEntity, f, g, h, partialTicks);
                poseStack.popPose();
            }
        }
    }
}
