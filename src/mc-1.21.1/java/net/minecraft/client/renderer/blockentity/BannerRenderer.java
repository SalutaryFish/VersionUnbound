package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;

public class BannerRenderer implements BlockEntityRenderer<BannerBlockEntity> {
    private static final int BANNER_WIDTH = 20;
    private static final int BANNER_HEIGHT = 40;
    private static final int MAX_PATTERNS = 16;
    public static final String FLAG = "flag";
    private static final String POLE = "pole";
    private static final String BAR = "bar";
    private final ModelPart flag;
    private final ModelPart pole;
    private final ModelPart bar;

    public BannerRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart modelPart = context.bakeLayer(ModelLayers.BANNER);
        this.flag = modelPart.getChild("flag");
        this.pole = modelPart.getChild("pole");
        this.bar = modelPart.getChild("bar");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        partDefinition.addOrReplaceChild("flag", CubeListBuilder.create().texOffs(0, 0).addBox(-10.0F, 0.0F, -2.0F, 20.0F, 40.0F, 1.0F), PartPose.ZERO);
        partDefinition.addOrReplaceChild("pole", CubeListBuilder.create().texOffs(44, 0).addBox(-1.0F, -30.0F, -1.0F, 2.0F, 42.0F, 2.0F), PartPose.ZERO);
        partDefinition.addOrReplaceChild("bar", CubeListBuilder.create().texOffs(0, 42).addBox(-10.0F, -32.0F, -1.0F, 20.0F, 2.0F, 2.0F), PartPose.ZERO);
        return LayerDefinition.create(meshDefinition, 64, 64);
    }

    public void render(
        BannerBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
    ) {
        float f = 0.6666667F;
        boolean bl = blockEntity.getLevel() == null;
        poseStack.pushPose();
        long l;
        if (bl) {
            l = 0L;
            poseStack.translate(0.5F, 0.5F, 0.5F);
            this.pole.visible = true;
        } else {
            l = blockEntity.getLevel().getGameTime();
            BlockState blockState = blockEntity.getBlockState();
            if (blockState.getBlock() instanceof BannerBlock) {
                poseStack.translate(0.5F, 0.5F, 0.5F);
                float g = -RotationSegment.convertToDegrees(blockState.getValue(BannerBlock.ROTATION));
                poseStack.mulPose(Axis.YP.rotationDegrees(g));
                this.pole.visible = true;
            } else {
                poseStack.translate(0.5F, -0.16666667F, 0.5F);
                float g = -blockState.getValue(WallBannerBlock.FACING).toYRot();
                poseStack.mulPose(Axis.YP.rotationDegrees(g));
                poseStack.translate(0.0F, -0.3125F, -0.4375F);
                this.pole.visible = false;
            }
        }

        poseStack.pushPose();
        poseStack.scale(0.6666667F, -0.6666667F, -0.6666667F);
        VertexConsumer vertexConsumer = ModelBakery.BANNER_BASE.buffer(bufferSource, RenderType::entitySolid);
        this.pole.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        this.bar.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        BlockPos blockPos = blockEntity.getBlockPos();
        float h = ((float)Math.floorMod(blockPos.getX() * 7 + blockPos.getY() * 9 + blockPos.getZ() * 13 + l, 100L) + partialTick) / 100.0F;
        this.flag.xRot = (-0.0125F + 0.01F * Mth.cos((float) (Math.PI * 2) * h)) * (float) Math.PI;
        this.flag.y = -32.0F;
        renderPatterns(
            poseStack,
            bufferSource,
            packedLight,
            packedOverlay,
            this.flag,
            ModelBakery.BANNER_BASE,
            true,
            blockEntity.getBaseColor(),
            blockEntity.getPatterns()
        );
        poseStack.popPose();
        poseStack.popPose();
    }

    /**
     * @param banner if {@code true}, uses banner material; otherwise if {@code false}
     *               uses shield material
     */
    public static void renderPatterns(
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        int packedOverlay,
        ModelPart flagPart,
        Material flagMaterial,
        boolean banner,
        DyeColor baseColor,
        BannerPatternLayers patterns
    ) {
        renderPatterns(poseStack, buffer, packedLight, packedOverlay, flagPart, flagMaterial, banner, baseColor, patterns, false);
    }

    /**
     * @param banner if {@code true}, uses banner material; otherwise if {@code false}
     *               uses shield material
     */
    public static void renderPatterns(
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        int packedOverlay,
        ModelPart flagPart,
        Material flagMaterial,
        boolean banner,
        DyeColor baseColor,
        BannerPatternLayers patterns,
        boolean glint
    ) {
        flagPart.render(poseStack, flagMaterial.buffer(buffer, RenderType::entitySolid, glint), packedLight, packedOverlay);
        renderPatternLayer(poseStack, buffer, packedLight, packedOverlay, flagPart, banner ? Sheets.BANNER_BASE : Sheets.SHIELD_BASE, baseColor);

        for (int i = 0; i < 16 && i < patterns.layers().size(); i++) {
            BannerPatternLayers.Layer layer = patterns.layers().get(i);
            Material material = banner ? Sheets.getBannerMaterial(layer.pattern()) : Sheets.getShieldMaterial(layer.pattern());
            renderPatternLayer(poseStack, buffer, packedLight, packedOverlay, flagPart, material, layer.color());
        }
    }

    private static void renderPatternLayer(
        PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay, ModelPart flagPart, Material material, DyeColor color
    ) {
        int i = color.getTextureDiffuseColor();
        flagPart.render(poseStack, material.buffer(buffer, RenderType::entityNoOutline), packedLight, packedOverlay, i);
    }
}
