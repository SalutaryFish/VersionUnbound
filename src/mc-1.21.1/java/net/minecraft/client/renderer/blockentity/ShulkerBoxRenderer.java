package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.ShulkerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ShulkerBoxRenderer implements BlockEntityRenderer<ShulkerBoxBlockEntity> {
    private final ShulkerModel<?> model;

    public ShulkerBoxRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new ShulkerModel(context.bakeLayer(ModelLayers.SHULKER));
    }

    public void render(
        ShulkerBoxBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
    ) {
        Direction direction = Direction.UP;
        if (blockEntity.hasLevel()) {
            BlockState blockState = blockEntity.getLevel().getBlockState(blockEntity.getBlockPos());
            if (blockState.getBlock() instanceof ShulkerBoxBlock) {
                direction = blockState.getValue(ShulkerBoxBlock.FACING);
            }
        }

        DyeColor dyeColor = blockEntity.getColor();
        Material material;
        if (dyeColor == null) {
            material = Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION;
        } else {
            material = Sheets.SHULKER_TEXTURE_LOCATION.get(dyeColor.getId());
        }

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        float f = 0.9995F;
        poseStack.scale(0.9995F, 0.9995F, 0.9995F);
        poseStack.mulPose(direction.getRotation());
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.translate(0.0F, -1.0F, 0.0F);
        ModelPart modelPart = this.model.getLid();
        modelPart.setPos(0.0F, 24.0F - blockEntity.getProgress(partialTick) * 0.5F * 16.0F, 0.0F);
        modelPart.yRot = 270.0F * blockEntity.getProgress(partialTick) * (float) (Math.PI / 180.0);
        VertexConsumer vertexConsumer = material.buffer(bufferSource, RenderType::entityCutoutNoCull);
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
