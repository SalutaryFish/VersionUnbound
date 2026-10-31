package net.minecraft.client.renderer.entity;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ItemEntityRenderer extends EntityRenderer<ItemEntity> {
    private static final float ITEM_BUNDLE_OFFSET_SCALE = 0.15F;
    private static final float FLAT_ITEM_BUNDLE_OFFSET_X = 0.0F;
    private static final float FLAT_ITEM_BUNDLE_OFFSET_Y = 0.0F;
    private static final float FLAT_ITEM_BUNDLE_OFFSET_Z = 0.09375F;
    private final ItemRenderer itemRenderer;
    private final RandomSource random = RandomSource.create();

    public ItemEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(ItemEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    public void render(ItemEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        ItemStack itemStack = entity.getItem();
        this.random.setSeed(getSeedForItemStack(itemStack));
        BakedModel bakedModel = this.itemRenderer.getModel(itemStack, entity.level(), null, entity.getId());
        boolean bl = bakedModel.isGui3d();
        float f = 0.25F;
        float g = Mth.sin((entity.getAge() + partialTicks) / 10.0F + entity.bobOffs) * 0.1F + 0.1F;
        float h = bakedModel.getTransforms().getTransform(ItemDisplayContext.GROUND).scale.y();
        poseStack.translate(0.0F, g + 0.25F * h, 0.0F);
        float i = entity.getSpin(partialTicks);
        poseStack.mulPose(Axis.YP.rotation(i));
        renderMultipleFromCount(this.itemRenderer, poseStack, buffer, packedLight, itemStack, bakedModel, bl, this.random);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    public static int getSeedForItemStack(ItemStack stack) {
        return stack.isEmpty() ? 187 : Item.getId(stack.getItem()) + stack.getDamageValue();
    }

    @VisibleForTesting
    static int getRenderedAmount(int count) {
        if (count <= 1) {
            return 1;
        } else if (count <= 16) {
            return 2;
        } else if (count <= 32) {
            return 3;
        } else {
            return count <= 48 ? 4 : 5;
        }
    }

    public static void renderMultipleFromCount(
        ItemRenderer itemRenderer, PoseStack poseStack, MultiBufferSource buffer, int packedLight, ItemStack item, RandomSource random, Level level
    ) {
        BakedModel bakedModel = itemRenderer.getModel(item, level, null, 0);
        renderMultipleFromCount(itemRenderer, poseStack, buffer, packedLight, item, bakedModel, bakedModel.isGui3d(), random);
    }

    public static void renderMultipleFromCount(
        ItemRenderer itemRenderer,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        ItemStack item,
        BakedModel model,
        boolean isGui3d,
        RandomSource random
    ) {
        int i = getRenderedAmount(item.getCount());
        float f = model.getTransforms().ground.scale.x();
        float g = model.getTransforms().ground.scale.y();
        float h = model.getTransforms().ground.scale.z();
        if (!isGui3d) {
            float j = -0.0F * (i - 1) * 0.5F * f;
            float k = -0.0F * (i - 1) * 0.5F * g;
            float l = -0.09375F * (i - 1) * 0.5F * h;
            poseStack.translate(j, k, l);
        }

        for (int m = 0; m < i; m++) {
            poseStack.pushPose();
            if (m > 0) {
                if (isGui3d) {
                    float k = (random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float l = (random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float n = (random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    poseStack.translate(k, l, n);
                } else {
                    float k = (random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                    float l = (random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                    poseStack.translate(k, l, 0.0F);
                }
            }

            itemRenderer.render(item, ItemDisplayContext.GROUND, false, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, model);
            poseStack.popPose();
            if (!isGui3d) {
                poseStack.translate(0.0F * f, 0.0F * g, 0.09375F * h);
            }
        }
    }
}
