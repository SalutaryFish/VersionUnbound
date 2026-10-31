package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CapeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public CapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    public void render(
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        AbstractClientPlayer livingEntity,
        float limbSwing,
        float limbSwingAmount,
        float partialTicks,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        if (!livingEntity.isInvisible() && livingEntity.isModelPartShown(PlayerModelPart.CAPE)) {
            PlayerSkin playerSkin = livingEntity.getSkin();
            if (playerSkin.capeTexture() != null) {
                ItemStack itemStack = livingEntity.getItemBySlot(EquipmentSlot.CHEST);
                if (!itemStack.is(Items.ELYTRA)) {
                    poseStack.pushPose();
                    poseStack.translate(0.0F, 0.0F, 0.125F);
                    double d = Mth.lerp(partialTicks, livingEntity.xCloakO, livingEntity.xCloak) - Mth.lerp(partialTicks, livingEntity.xo, livingEntity.getX());
                    double e = Mth.lerp(partialTicks, livingEntity.yCloakO, livingEntity.yCloak) - Mth.lerp(partialTicks, livingEntity.yo, livingEntity.getY());
                    double f = Mth.lerp(partialTicks, livingEntity.zCloakO, livingEntity.zCloak) - Mth.lerp(partialTicks, livingEntity.zo, livingEntity.getZ());
                    float g = Mth.rotLerp(partialTicks, livingEntity.yBodyRotO, livingEntity.yBodyRot);
                    double h = Mth.sin(g * (float) (Math.PI / 180.0));
                    double i = -Mth.cos(g * (float) (Math.PI / 180.0));
                    float j = (float)e * 10.0F;
                    j = Mth.clamp(j, -6.0F, 32.0F);
                    float k = (float)(d * h + f * i) * 100.0F;
                    k = Mth.clamp(k, 0.0F, 150.0F);
                    float l = (float)(d * i - f * h) * 100.0F;
                    l = Mth.clamp(l, -20.0F, 20.0F);
                    if (k < 0.0F) {
                        k = 0.0F;
                    }

                    float m = Mth.lerp(partialTicks, livingEntity.oBob, livingEntity.bob);
                    j += Mth.sin(Mth.lerp(partialTicks, livingEntity.walkDistO, livingEntity.walkDist) * 6.0F) * 32.0F * m;
                    if (livingEntity.isCrouching()) {
                        j += 25.0F;
                    }

                    poseStack.mulPose(Axis.XP.rotationDegrees(6.0F + k / 2.0F + j));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(l / 2.0F));
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - l / 2.0F));
                    VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entitySolid(playerSkin.capeTexture()));
                    this.getParentModel().renderCloak(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
                    poseStack.popPose();
                }
            }
        }
    }
}
