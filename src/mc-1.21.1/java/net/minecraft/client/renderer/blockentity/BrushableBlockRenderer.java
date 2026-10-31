package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class BrushableBlockRenderer implements BlockEntityRenderer<BrushableBlockEntity> {
    private final ItemRenderer itemRenderer;

    public BrushableBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    public void render(
        BrushableBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
    ) {
        if (blockEntity.getLevel() != null) {
            int i = blockEntity.getBlockState().getValue(BlockStateProperties.DUSTED);
            if (i > 0) {
                Direction direction = blockEntity.getHitDirection();
                if (direction != null) {
                    ItemStack itemStack = blockEntity.getItem();
                    if (!itemStack.isEmpty()) {
                        poseStack.pushPose();
                        poseStack.translate(0.0F, 0.5F, 0.0F);
                        float[] fs = this.translations(direction, i);
                        poseStack.translate(fs[0], fs[1], fs[2]);
                        poseStack.mulPose(Axis.YP.rotationDegrees(75.0F));
                        boolean bl = direction == Direction.EAST || direction == Direction.WEST;
                        poseStack.mulPose(Axis.YP.rotationDegrees((bl ? 90 : 0) + 11));
                        poseStack.scale(0.5F, 0.5F, 0.5F);
                        int j = LevelRenderer.getLightColor(blockEntity.getLevel(), blockEntity.getBlockState(), blockEntity.getBlockPos().relative(direction));
                        this.itemRenderer
                            .renderStatic(itemStack, ItemDisplayContext.FIXED, j, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, blockEntity.getLevel(), 0);
                        poseStack.popPose();
                    }
                }
            }
        }
    }

    private float[] translations(Direction direction, int dustedLevel) {
        float[] fs = new float[]{0.5F, 0.0F, 0.5F};
        float f = dustedLevel / 10.0F * 0.75F;
        switch (direction) {
            case EAST:
                fs[0] = 0.73F + f;
                break;
            case WEST:
                fs[0] = 0.25F - f;
                break;
            case UP:
                fs[1] = 0.25F + f;
                break;
            case DOWN:
                fs[1] = -0.23F - f;
                break;
            case NORTH:
                fs[2] = 0.25F - f;
                break;
            case SOUTH:
                fs[2] = 0.73F + f;
        }

        return fs;
    }
}
