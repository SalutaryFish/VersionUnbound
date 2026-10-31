package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SnowGolemModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SnowGolemHeadLayer extends RenderLayer<SnowGolem, SnowGolemModel<SnowGolem>> {
    private final BlockRenderDispatcher blockRenderer;
    private final ItemRenderer itemRenderer;

    public SnowGolemHeadLayer(RenderLayerParent<SnowGolem, SnowGolemModel<SnowGolem>> renderer, BlockRenderDispatcher blockRenderer, ItemRenderer itemRenderer) {
        super(renderer);
        this.blockRenderer = blockRenderer;
        this.itemRenderer = itemRenderer;
    }

    public void render(
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        SnowGolem livingEntity,
        float limbSwing,
        float limbSwingAmount,
        float partialTicks,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        if (livingEntity.hasPumpkin()) {
            boolean bl = Minecraft.getInstance().shouldEntityAppearGlowing(livingEntity) && livingEntity.isInvisible();
            if (!livingEntity.isInvisible() || bl) {
                poseStack.pushPose();
                this.getParentModel().getHead().translateAndRotate(poseStack);
                float f = 0.625F;
                poseStack.translate(0.0F, -0.34375F, 0.0F);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                poseStack.scale(0.625F, -0.625F, -0.625F);
                ItemStack itemStack = new ItemStack(Blocks.CARVED_PUMPKIN);
                if (bl) {
                    BlockState blockState = Blocks.CARVED_PUMPKIN.defaultBlockState();
                    BakedModel bakedModel = this.blockRenderer.getBlockModel(blockState);
                    int i = LivingEntityRenderer.getOverlayCoords(livingEntity, 0.0F);
                    poseStack.translate(-0.5F, -0.5F, -0.5F);
                    this.blockRenderer
                        .getModelRenderer()
                        .renderModel(
                            poseStack.last(),
                            buffer.getBuffer(RenderType.outline(TextureAtlas.LOCATION_BLOCKS)),
                            blockState,
                            bakedModel,
                            0.0F,
                            0.0F,
                            0.0F,
                            packedLight,
                            i
                        );
                } else {
                    this.itemRenderer
                        .renderStatic(
                            livingEntity,
                            itemStack,
                            ItemDisplayContext.HEAD,
                            false,
                            poseStack,
                            buffer,
                            livingEntity.level(),
                            packedLight,
                            LivingEntityRenderer.getOverlayCoords(livingEntity, 0.0F),
                            livingEntity.getId()
                        );
                }

                poseStack.popPose();
            }
        }
    }
}
