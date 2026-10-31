package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CarriedBlockLayer;
import net.minecraft.client.renderer.entity.layers.EnderEyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class EndermanRenderer extends MobRenderer<EnderMan, EndermanModel<EnderMan>> {
    private static final ResourceLocation ENDERMAN_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/enderman/enderman.png");
    private final RandomSource random = RandomSource.create();

    public EndermanRenderer(EntityRendererProvider.Context context) {
        super(context, new EndermanModel<>(context.bakeLayer(ModelLayers.ENDERMAN)), 0.5F);
        this.addLayer(new EnderEyesLayer<>(this));
        this.addLayer(new CarriedBlockLayer(this, context.getBlockRenderDispatcher()));
    }

    public void render(EnderMan entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        BlockState blockState = entity.getCarriedBlock();
        EndermanModel<EnderMan> endermanModel = this.getModel();
        endermanModel.carrying = blockState != null;
        endermanModel.creepy = entity.isCreepy();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    public Vec3 getRenderOffset(EnderMan entity, float partialTicks) {
        if (entity.isCreepy()) {
            double d = 0.02 * entity.getScale();
            return new Vec3(this.random.nextGaussian() * d, 0.0, this.random.nextGaussian() * d);
        } else {
            return super.getRenderOffset(entity, partialTicks);
        }
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(EnderMan entity) {
        return ENDERMAN_LOCATION;
    }
}
