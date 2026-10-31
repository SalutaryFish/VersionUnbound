package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.phys.Vec3;

public class IllusionerRenderer extends IllagerRenderer<Illusioner> {
    private static final ResourceLocation ILLUSIONER = ResourceLocation.withDefaultNamespace("textures/entity/illager/illusioner.png");

    public IllusionerRenderer(EntityRendererProvider.Context context) {
        super(context, new IllagerModel<>(context.bakeLayer(ModelLayers.ILLUSIONER)), 0.5F);
        this.addLayer(
            new ItemInHandLayer<Illusioner, IllagerModel<Illusioner>>(this, context.getItemInHandRenderer()) {
                public void render(
                    PoseStack poseStack,
                    MultiBufferSource multiBufferSource,
                    int i,
                    Illusioner illusioner,
                    float f,
                    float g,
                    float h,
                    float j,
                    float k,
                    float l
                ) {
                    if (illusioner.isCastingSpell() || illusioner.isAggressive()) {
                        super.render(poseStack, multiBufferSource, i, illusioner, f, g, h, j, k, l);
                    }
                }
            }
        );
        this.model.getHat().visible = true;
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Illusioner entity) {
        return ILLUSIONER;
    }

    public void render(Illusioner entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.isInvisible()) {
            Vec3[] vec3s = entity.getIllusionOffsets(partialTicks);
            float f = this.getBob(entity, partialTicks);

            for (int i = 0; i < vec3s.length; i++) {
                poseStack.pushPose();
                poseStack.translate(
                    vec3s[i].x + Mth.cos(i + f * 0.5F) * 0.025, vec3s[i].y + Mth.cos(i + f * 0.75F) * 0.0125, vec3s[i].z + Mth.cos(i + f * 0.7F) * 0.025
                );
                super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
                poseStack.popPose();
            }
        } else {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }
    }

    protected boolean isBodyVisible(Illusioner livingEntity) {
        return true;
    }
}
