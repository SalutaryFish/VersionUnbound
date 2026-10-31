package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.ChestBoatModel;
import net.minecraft.client.model.ChestRaftModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.RaftModel;
import net.minecraft.client.model.WaterPatchModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import org.joml.Quaternionf;

public class BoatRenderer extends EntityRenderer<Boat> {
    private final Map<Boat.Type, Pair<ResourceLocation, ListModel<Boat>>> boatResources;

    public BoatRenderer(EntityRendererProvider.Context context, boolean chestBoat) {
        super(context);
        this.shadowRadius = 0.8F;
        this.boatResources = Stream.of(Boat.Type.values())
            .collect(
                ImmutableMap.toImmutableMap(
                    type -> (Boat.Type)type, type -> Pair.of(getTextureLocation(type, chestBoat), this.createBoatModel(context, type, chestBoat))
                )
            );
    }

    private ListModel<Boat> createBoatModel(EntityRendererProvider.Context context, Boat.Type type, boolean chestBoat) {
        ModelLayerLocation modelLayerLocation = chestBoat ? ModelLayers.createChestBoatModelName(type) : ModelLayers.createBoatModelName(type);
        ModelPart modelPart = context.bakeLayer(modelLayerLocation);
        if (type == Boat.Type.BAMBOO) {
            return chestBoat ? new ChestRaftModel(modelPart) : new RaftModel(modelPart);
        } else {
            return chestBoat ? new ChestBoatModel(modelPart) : new BoatModel(modelPart);
        }
    }

    private static ResourceLocation getTextureLocation(Boat.Type type, boolean chestBoat) {
        return chestBoat
            ? ResourceLocation.withDefaultNamespace("textures/entity/chest_boat/" + type.getName() + ".png")
            : ResourceLocation.withDefaultNamespace("textures/entity/boat/" + type.getName() + ".png");
    }

    public void render(Boat entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.375F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        float f = entity.getHurtTime() - partialTicks;
        float g = entity.getDamage() - partialTicks;
        if (g < 0.0F) {
            g = 0.0F;
        }

        if (f > 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(f) * f * g / 10.0F * entity.getHurtDir()));
        }

        float h = entity.getBubbleAngle(partialTicks);
        if (!Mth.equal(h, 0.0F)) {
            poseStack.mulPose(new Quaternionf().setAngleAxis(entity.getBubbleAngle(partialTicks) * (float) (Math.PI / 180.0), 1.0F, 0.0F, 1.0F));
        }

        Pair<ResourceLocation, ListModel<Boat>> pair = this.boatResources.get(entity.getVariant());
        ResourceLocation resourceLocation = pair.getFirst();
        ListModel<Boat> listModel = pair.getSecond();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        listModel.setupAnim(entity, partialTicks, 0.0F, -0.1F, 0.0F, 0.0F);
        VertexConsumer vertexConsumer = buffer.getBuffer(listModel.renderType(resourceLocation));
        listModel.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        if (!entity.isUnderWater()) {
            VertexConsumer vertexConsumer2 = buffer.getBuffer(RenderType.waterMask());
            if (listModel instanceof WaterPatchModel waterPatchModel) {
                waterPatchModel.waterPatch().render(poseStack, vertexConsumer2, packedLight, OverlayTexture.NO_OVERLAY);
            }
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Boat entity) {
        return this.boatResources.get(entity.getVariant()).getFirst();
    }
}
