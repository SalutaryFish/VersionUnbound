package net.minecraft.client.renderer.entity;

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
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.joml.Quaternionf;

public class EndCrystalRenderer extends EntityRenderer<EndCrystal> {
    private static final ResourceLocation END_CRYSTAL_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(END_CRYSTAL_LOCATION);
    private static final float SIN_45 = (float)Math.sin(Math.PI / 4);
    private static final String GLASS = "glass";
    private static final String BASE = "base";
    private final ModelPart cube;
    private final ModelPart glass;
    private final ModelPart base;

    public EndCrystalRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        ModelPart modelPart = context.bakeLayer(ModelLayers.END_CRYSTAL);
        this.glass = modelPart.getChild("glass");
        this.cube = modelPart.getChild("cube");
        this.base = modelPart.getChild("base");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        partDefinition.addOrReplaceChild("glass", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.ZERO);
        partDefinition.addOrReplaceChild("cube", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.ZERO);
        partDefinition.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 16).addBox(-6.0F, 0.0F, -6.0F, 12.0F, 4.0F, 12.0F), PartPose.ZERO);
        return LayerDefinition.create(meshDefinition, 64, 32);
    }

    public void render(EndCrystal entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        float f = getY(entity, partialTicks);
        float g = (entity.time + partialTicks) * 3.0F;
        VertexConsumer vertexConsumer = buffer.getBuffer(RENDER_TYPE);
        poseStack.pushPose();
        poseStack.scale(2.0F, 2.0F, 2.0F);
        poseStack.translate(0.0F, -0.5F, 0.0F);
        int i = OverlayTexture.NO_OVERLAY;
        if (entity.showsBottom()) {
            this.base.render(poseStack, vertexConsumer, packedLight, i);
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(g));
        poseStack.translate(0.0F, 1.5F + f / 2.0F, 0.0F);
        poseStack.mulPose(new Quaternionf().setAngleAxis((float) (Math.PI / 3), SIN_45, 0.0F, SIN_45));
        this.glass.render(poseStack, vertexConsumer, packedLight, i);
        float h = 0.875F;
        poseStack.scale(0.875F, 0.875F, 0.875F);
        poseStack.mulPose(new Quaternionf().setAngleAxis((float) (Math.PI / 3), SIN_45, 0.0F, SIN_45));
        poseStack.mulPose(Axis.YP.rotationDegrees(g));
        this.glass.render(poseStack, vertexConsumer, packedLight, i);
        poseStack.scale(0.875F, 0.875F, 0.875F);
        poseStack.mulPose(new Quaternionf().setAngleAxis((float) (Math.PI / 3), SIN_45, 0.0F, SIN_45));
        poseStack.mulPose(Axis.YP.rotationDegrees(g));
        this.cube.render(poseStack, vertexConsumer, packedLight, i);
        poseStack.popPose();
        poseStack.popPose();
        BlockPos blockPos = entity.getBeamTarget();
        if (blockPos != null) {
            float j = blockPos.getX() + 0.5F;
            float k = blockPos.getY() + 0.5F;
            float l = blockPos.getZ() + 0.5F;
            float m = (float)(j - entity.getX());
            float n = (float)(k - entity.getY());
            float o = (float)(l - entity.getZ());
            poseStack.translate(m, n, o);
            EnderDragonRenderer.renderCrystalBeams(-m, -n + f, -o, partialTicks, entity.time, poseStack, buffer, packedLight);
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    public static float getY(EndCrystal endCrystal, float partialTick) {
        float f = endCrystal.time + partialTick;
        float g = Mth.sin(f * 0.2F) / 2.0F + 0.5F;
        g = (g * g + g) * 0.4F;
        return g - 1.4F;
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(EndCrystal entity) {
        return END_CRYSTAL_LOCATION;
    }

    public boolean shouldRender(EndCrystal livingEntity, Frustum camera, double camX, double camY, double camZ) {
        return super.shouldRender(livingEntity, camera, camX, camY, camZ) || livingEntity.getBeamTarget() != null;
    }
}
