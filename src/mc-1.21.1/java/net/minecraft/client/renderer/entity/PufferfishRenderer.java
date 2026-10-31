package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PufferfishBigModel;
import net.minecraft.client.model.PufferfishMidModel;
import net.minecraft.client.model.PufferfishSmallModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Pufferfish;

public class PufferfishRenderer extends MobRenderer<Pufferfish, EntityModel<Pufferfish>> {
    private static final ResourceLocation PUFFER_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/fish/pufferfish.png");
    private int puffStateO = 3;
    private final EntityModel<Pufferfish> small;
    private final EntityModel<Pufferfish> mid;
    private final EntityModel<Pufferfish> big = this.getModel();

    public PufferfishRenderer(EntityRendererProvider.Context context) {
        super(context, new PufferfishBigModel<>(context.bakeLayer(ModelLayers.PUFFERFISH_BIG)), 0.2F);
        this.mid = new PufferfishMidModel<>(context.bakeLayer(ModelLayers.PUFFERFISH_MEDIUM));
        this.small = new PufferfishSmallModel<>(context.bakeLayer(ModelLayers.PUFFERFISH_SMALL));
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Pufferfish entity) {
        return PUFFER_LOCATION;
    }

    public void render(Pufferfish entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        int i = entity.getPuffState();
        if (i != this.puffStateO) {
            if (i == 0) {
                this.model = this.small;
            } else if (i == 1) {
                this.model = this.mid;
            } else {
                this.model = this.big;
            }
        }

        this.puffStateO = i;
        this.shadowRadius = 0.1F + 0.1F * i;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    protected void setupRotations(Pufferfish entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        poseStack.translate(0.0F, Mth.cos(bob * 0.05F) * 0.08F, 0.0F);
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
    }
}
