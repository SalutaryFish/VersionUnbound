package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;

public class EnchantTableRenderer implements BlockEntityRenderer<EnchantingTableBlockEntity> {
    /**
     * The texture for the book above the enchantment table.
     */
    public static final Material BOOK_LOCATION = new Material(
        TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("entity/enchanting_table_book")
    );
    private final BookModel bookModel;

    public EnchantTableRenderer(BlockEntityRendererProvider.Context context) {
        this.bookModel = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    public void render(
        EnchantingTableBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
    ) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.75F, 0.5F);
        float f = blockEntity.time + partialTick;
        poseStack.translate(0.0F, 0.1F + Mth.sin(f * 0.1F) * 0.01F, 0.0F);
        float g = blockEntity.rot - blockEntity.oRot;

        while (g >= (float) Math.PI) {
            g -= (float) (Math.PI * 2);
        }

        while (g < (float) -Math.PI) {
            g += (float) (Math.PI * 2);
        }

        float h = blockEntity.oRot + g * partialTick;
        poseStack.mulPose(Axis.YP.rotation(-h));
        poseStack.mulPose(Axis.ZP.rotationDegrees(80.0F));
        float i = Mth.lerp(partialTick, blockEntity.oFlip, blockEntity.flip);
        float j = Mth.frac(i + 0.25F) * 1.6F - 0.3F;
        float k = Mth.frac(i + 0.75F) * 1.6F - 0.3F;
        float l = Mth.lerp(partialTick, blockEntity.oOpen, blockEntity.open);
        this.bookModel.setupAnim(f, Mth.clamp(j, 0.0F, 1.0F), Mth.clamp(k, 0.0F, 1.0F), l);
        VertexConsumer vertexConsumer = BOOK_LOCATION.buffer(bufferSource, RenderType::entitySolid);
        this.bookModel.render(poseStack, vertexConsumer, packedLight, packedOverlay, -1);
        poseStack.popPose();
    }
}
