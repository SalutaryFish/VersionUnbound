package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SheepFurModel;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;

public class SheepFurLayer extends RenderLayer<Sheep, SheepModel<Sheep>> {
    private static final ResourceLocation SHEEP_FUR_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/sheep/sheep_fur.png");
    private final SheepFurModel<Sheep> model;

    public SheepFurLayer(RenderLayerParent<Sheep, SheepModel<Sheep>> renderer, EntityModelSet modelSet) {
        super(renderer);
        this.model = new SheepFurModel<>(modelSet.bakeLayer(ModelLayers.SHEEP_FUR));
    }

    public void render(
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        Sheep livingEntity,
        float limbSwing,
        float limbSwingAmount,
        float partialTicks,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        if (!livingEntity.isSheared()) {
            if (livingEntity.isInvisible()) {
                Minecraft minecraft = Minecraft.getInstance();
                boolean bl = minecraft.shouldEntityAppearGlowing(livingEntity);
                if (bl) {
                    this.getParentModel().copyPropertiesTo(this.model);
                    this.model.prepareMobModel(livingEntity, limbSwing, limbSwingAmount, partialTicks);
                    this.model.setupAnim(livingEntity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
                    VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.outline(SHEEP_FUR_LOCATION));
                    this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, LivingEntityRenderer.getOverlayCoords(livingEntity, 0.0F), -16777216);
                }
            } else {
                int p;
                if (livingEntity.hasCustomName() && "jeb_".equals(livingEntity.getName().getString())) {
                    int i = 25;
                    int j = livingEntity.tickCount / 25 + livingEntity.getId();
                    int k = DyeColor.values().length;
                    int l = j % k;
                    int m = (j + 1) % k;
                    float f = (livingEntity.tickCount % 25 + partialTicks) / 25.0F;
                    int n = Sheep.getColor(DyeColor.byId(l));
                    int o = Sheep.getColor(DyeColor.byId(m));
                    p = FastColor.ARGB32.lerp(f, n, o);
                } else {
                    p = Sheep.getColor(livingEntity.getColor());
                }

                coloredCutoutModelCopyLayerRender(
                    this.getParentModel(),
                    this.model,
                    SHEEP_FUR_LOCATION,
                    poseStack,
                    buffer,
                    packedLight,
                    livingEntity,
                    limbSwing,
                    limbSwingAmount,
                    ageInTicks,
                    netHeadYaw,
                    headPitch,
                    partialTicks,
                    p
                );
            }
        }
    }
}
