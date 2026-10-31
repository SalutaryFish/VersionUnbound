package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class FishingHookRenderer extends EntityRenderer<FishingHook> {
    private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE_LOCATION);
    private static final double VIEW_BOBBING_SCALE = 960.0;

    public FishingHookRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public void render(FishingHook entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Player player = entity.getPlayerOwner();
        if (player != null) {
            poseStack.pushPose();
            poseStack.pushPose();
            poseStack.scale(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            PoseStack.Pose pose = poseStack.last();
            VertexConsumer vertexConsumer = buffer.getBuffer(RENDER_TYPE);
            vertex(vertexConsumer, pose, packedLight, 0.0F, 0, 0, 1);
            vertex(vertexConsumer, pose, packedLight, 1.0F, 0, 1, 1);
            vertex(vertexConsumer, pose, packedLight, 1.0F, 1, 1, 0);
            vertex(vertexConsumer, pose, packedLight, 0.0F, 1, 0, 0);
            poseStack.popPose();
            float f = player.getAttackAnim(partialTicks);
            float g = Mth.sin(Mth.sqrt(f) * (float) Math.PI);
            Vec3 vec3 = this.getPlayerHandPos(player, g, partialTicks);
            Vec3 vec32 = entity.getPosition(partialTicks).add(0.0, 0.25, 0.0);
            float h = (float)(vec3.x - vec32.x);
            float i = (float)(vec3.y - vec32.y);
            float j = (float)(vec3.z - vec32.z);
            VertexConsumer vertexConsumer2 = buffer.getBuffer(RenderType.lineStrip());
            PoseStack.Pose pose2 = poseStack.last();
            int k = 16;

            for (int l = 0; l <= 16; l++) {
                stringVertex(h, i, j, vertexConsumer2, pose2, fraction(l, 16), fraction(l + 1, 16));
            }

            poseStack.popPose();
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }
    }

    private Vec3 getPlayerHandPos(Player player, float f, float partialTick) {
        int i = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        ItemStack itemStack = player.getMainHandItem();
        if (!itemStack.is(Items.FISHING_ROD)) {
            i = -i;
        }

        if (this.entityRenderDispatcher.options.getCameraType().isFirstPerson() && player == Minecraft.getInstance().player) {
            double m = 960.0 / this.entityRenderDispatcher.options.fov().get().intValue();
            Vec3 vec3 = this.entityRenderDispatcher.camera.getNearPlane().getPointOnPlane(i * 0.525F, -0.1F).scale(m).yRot(f * 0.5F).xRot(-f * 0.7F);
            return player.getEyePosition(partialTick).add(vec3);
        } else {
            float g = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * (float) (Math.PI / 180.0);
            double d = Mth.sin(g);
            double e = Mth.cos(g);
            float h = player.getScale();
            double j = i * 0.35 * h;
            double k = 0.8 * h;
            float l = player.isCrouching() ? -0.1875F : 0.0F;
            return player.getEyePosition(partialTick).add(-e * j - d * k, l - 0.45 * h, -d * j + e * k);
        }
    }

    private static float fraction(int numerator, int denominator) {
        return (float)numerator / denominator;
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int packedLight, float x, int y, int u, int v) {
        consumer.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F)
            .setColor(-1)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void stringVertex(float x, float y, float z, VertexConsumer consumer, PoseStack.Pose pose, float stringFraction, float nextStringFraction) {
        float f = x * stringFraction;
        float g = y * (stringFraction * stringFraction + stringFraction) * 0.5F + 0.25F;
        float h = z * stringFraction;
        float i = x * nextStringFraction - f;
        float j = y * (nextStringFraction * nextStringFraction + nextStringFraction) * 0.5F + 0.25F - g;
        float k = z * nextStringFraction - h;
        float l = Mth.sqrt(i * i + j * j + k * k);
        i /= l;
        j /= l;
        k /= l;
        consumer.addVertex(pose, f, g, h).setColor(-16777216).setNormal(pose, i, j, k);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(FishingHook entity) {
        return TEXTURE_LOCATION;
    }
}
