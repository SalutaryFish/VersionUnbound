package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.FoxModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class FoxHeldItemLayer extends RenderLayer<Fox, FoxModel<Fox>> {
    private final ItemInHandRenderer itemInHandRenderer;

    public FoxHeldItemLayer(RenderLayerParent<Fox, FoxModel<Fox>> renderer, ItemInHandRenderer itemInHandRenderer) {
        super(renderer);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    public void render(
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        Fox livingEntity,
        float limbSwing,
        float limbSwingAmount,
        float partialTicks,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        boolean bl = livingEntity.isSleeping();
        boolean bl2 = livingEntity.isBaby();
        poseStack.pushPose();
        if (bl2) {
            float f = 0.75F;
            poseStack.scale(0.75F, 0.75F, 0.75F);
            poseStack.translate(0.0F, 0.5F, 0.209375F);
        }

        poseStack.translate(this.getParentModel().head.x / 16.0F, this.getParentModel().head.y / 16.0F, this.getParentModel().head.z / 16.0F);
        float f = livingEntity.getHeadRollAngle(partialTicks);
        poseStack.mulPose(Axis.ZP.rotation(f));
        poseStack.mulPose(Axis.YP.rotationDegrees(netHeadYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(headPitch));
        if (livingEntity.isBaby()) {
            if (bl) {
                poseStack.translate(0.4F, 0.26F, 0.15F);
            } else {
                poseStack.translate(0.06F, 0.26F, -0.5F);
            }
        } else if (bl) {
            poseStack.translate(0.46F, 0.26F, 0.22F);
        } else {
            poseStack.translate(0.06F, 0.27F, -0.5F);
        }

        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        if (bl) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }

        ItemStack itemStack = livingEntity.getItemBySlot(EquipmentSlot.MAINHAND);
        this.itemInHandRenderer.renderItem(livingEntity, itemStack, ItemDisplayContext.GROUND, false, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
