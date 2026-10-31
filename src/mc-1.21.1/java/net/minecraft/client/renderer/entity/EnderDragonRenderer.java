package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import javax.annotation.Nullable;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class EnderDragonRenderer extends EntityRenderer<EnderDragon> {
    public static final ResourceLocation CRYSTAL_BEAM_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/end_crystal/end_crystal_beam.png");
    private static final ResourceLocation DRAGON_EXPLODING_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon_exploding.png");
    private static final ResourceLocation DRAGON_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
    private static final ResourceLocation DRAGON_EYES_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon_eyes.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(DRAGON_LOCATION);
    private static final RenderType DECAL = RenderType.entityDecal(DRAGON_LOCATION);
    private static final RenderType EYES = RenderType.eyes(DRAGON_EYES_LOCATION);
    private static final RenderType BEAM = RenderType.entitySmoothCutout(CRYSTAL_BEAM_LOCATION);
    private static final float HALF_SQRT_3 = (float)(Math.sqrt(3.0) / 2.0);
    private final EnderDragonRenderer.DragonModel model;

    public EnderDragonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.model = new EnderDragonRenderer.DragonModel(context.bakeLayer(ModelLayers.ENDER_DRAGON));
    }

    public void render(EnderDragon entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        float f = (float)entity.getLatencyPos(7, partialTicks)[0];
        float g = (float)(entity.getLatencyPos(5, partialTicks)[1] - entity.getLatencyPos(10, partialTicks)[1]);
        poseStack.mulPose(Axis.YP.rotationDegrees(-f));
        poseStack.mulPose(Axis.XP.rotationDegrees(g * 10.0F));
        poseStack.translate(0.0F, 0.0F, 1.0F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        boolean bl = entity.hurtTime > 0;
        this.model.prepareMobModel(entity, 0.0F, 0.0F, partialTicks);
        if (entity.dragonDeathTime > 0) {
            float h = entity.dragonDeathTime / 200.0F;
            int i = FastColor.ARGB32.color(Mth.floor(h * 255.0F), -1);
            VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.dragonExplosionAlpha(DRAGON_EXPLODING_LOCATION));
            this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, i);
            VertexConsumer vertexConsumer2 = buffer.getBuffer(DECAL);
            this.model.renderToBuffer(poseStack, vertexConsumer2, packedLight, OverlayTexture.pack(0.0F, bl));
        } else {
            VertexConsumer vertexConsumer3 = buffer.getBuffer(RENDER_TYPE);
            this.model.renderToBuffer(poseStack, vertexConsumer3, packedLight, OverlayTexture.pack(0.0F, bl));
        }

        VertexConsumer vertexConsumer3 = buffer.getBuffer(EYES);
        this.model.renderToBuffer(poseStack, vertexConsumer3, packedLight, OverlayTexture.NO_OVERLAY);
        if (entity.dragonDeathTime > 0) {
            float j = (entity.dragonDeathTime + partialTicks) / 200.0F;
            poseStack.pushPose();
            poseStack.translate(0.0F, -1.0F, -2.0F);
            renderRays(poseStack, j, buffer.getBuffer(RenderType.dragonRays()));
            renderRays(poseStack, j, buffer.getBuffer(RenderType.dragonRaysDepth()));
            poseStack.popPose();
        }

        poseStack.popPose();
        if (entity.nearestCrystal != null) {
            poseStack.pushPose();
            float j = (float)(entity.nearestCrystal.getX() - Mth.lerp(partialTicks, entity.xo, entity.getX()));
            float k = (float)(entity.nearestCrystal.getY() - Mth.lerp(partialTicks, entity.yo, entity.getY()));
            float l = (float)(entity.nearestCrystal.getZ() - Mth.lerp(partialTicks, entity.zo, entity.getZ()));
            renderCrystalBeams(
                j, k + EndCrystalRenderer.getY(entity.nearestCrystal, partialTicks), l, partialTicks, entity.tickCount, poseStack, buffer, packedLight
            );
            poseStack.popPose();
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void renderRays(PoseStack poseStack, float dragonDeathCompletion, VertexConsumer buffer) {
        poseStack.pushPose();
        float f = Math.min(dragonDeathCompletion > 0.8F ? (dragonDeathCompletion - 0.8F) / 0.2F : 0.0F, 1.0F);
        int i = FastColor.ARGB32.colorFromFloat(1.0F - f, 1.0F, 1.0F, 1.0F);
        int j = 16711935;
        RandomSource randomSource = RandomSource.create(432L);
        Vector3f vector3f = new Vector3f();
        Vector3f vector3f2 = new Vector3f();
        Vector3f vector3f3 = new Vector3f();
        Vector3f vector3f4 = new Vector3f();
        Quaternionf quaternionf = new Quaternionf();
        int k = Mth.floor((dragonDeathCompletion + dragonDeathCompletion * dragonDeathCompletion) / 2.0F * 60.0F);

        for (int l = 0; l < k; l++) {
            quaternionf.rotationXYZ(
                    randomSource.nextFloat() * (float) (Math.PI * 2),
                    randomSource.nextFloat() * (float) (Math.PI * 2),
                    randomSource.nextFloat() * (float) (Math.PI * 2)
                )
                .rotateXYZ(
                    randomSource.nextFloat() * (float) (Math.PI * 2),
                    randomSource.nextFloat() * (float) (Math.PI * 2),
                    randomSource.nextFloat() * (float) (Math.PI * 2) + dragonDeathCompletion * (float) (Math.PI / 2)
                );
            poseStack.mulPose(quaternionf);
            float g = randomSource.nextFloat() * 20.0F + 5.0F + f * 10.0F;
            float h = randomSource.nextFloat() * 2.0F + 1.0F + f * 2.0F;
            vector3f2.set(-HALF_SQRT_3 * h, g, -0.5F * h);
            vector3f3.set(HALF_SQRT_3 * h, g, -0.5F * h);
            vector3f4.set(0.0F, g, h);
            PoseStack.Pose pose = poseStack.last();
            buffer.addVertex(pose, vector3f).setColor(i);
            buffer.addVertex(pose, vector3f2).setColor(16711935);
            buffer.addVertex(pose, vector3f3).setColor(16711935);
            buffer.addVertex(pose, vector3f).setColor(i);
            buffer.addVertex(pose, vector3f3).setColor(16711935);
            buffer.addVertex(pose, vector3f4).setColor(16711935);
            buffer.addVertex(pose, vector3f).setColor(i);
            buffer.addVertex(pose, vector3f4).setColor(16711935);
            buffer.addVertex(pose, vector3f2).setColor(16711935);
        }

        poseStack.popPose();
    }

    public static void renderCrystalBeams(
        float x, float y, float z, float partialTick, int tickCount, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight
    ) {
        float f = Mth.sqrt(x * x + z * z);
        float g = Mth.sqrt(x * x + y * y + z * z);
        poseStack.pushPose();
        poseStack.translate(0.0F, 2.0F, 0.0F);
        poseStack.mulPose(Axis.YP.rotation((float)(-Math.atan2(z, x)) - (float) (Math.PI / 2)));
        poseStack.mulPose(Axis.XP.rotation((float)(-Math.atan2(f, y)) - (float) (Math.PI / 2)));
        VertexConsumer vertexConsumer = bufferSource.getBuffer(BEAM);
        float h = 0.0F - (tickCount + partialTick) * 0.01F;
        float i = Mth.sqrt(x * x + y * y + z * z) / 32.0F - (tickCount + partialTick) * 0.01F;
        int j = 8;
        float k = 0.0F;
        float l = 0.75F;
        float m = 0.0F;
        PoseStack.Pose pose = poseStack.last();

        for (int n = 1; n <= 8; n++) {
            float o = Mth.sin(n * (float) (Math.PI * 2) / 8.0F) * 0.75F;
            float p = Mth.cos(n * (float) (Math.PI * 2) / 8.0F) * 0.75F;
            float q = n / 8.0F;
            vertexConsumer.addVertex(pose, k * 0.2F, l * 0.2F, 0.0F)
                .setColor(-16777216)
                .setUv(m, h)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, -1.0F, 0.0F);
            vertexConsumer.addVertex(pose, k, l, g)
                .setColor(-1)
                .setUv(m, i)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, -1.0F, 0.0F);
            vertexConsumer.addVertex(pose, o, p, g)
                .setColor(-1)
                .setUv(q, i)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, -1.0F, 0.0F);
            vertexConsumer.addVertex(pose, o * 0.2F, p * 0.2F, 0.0F)
                .setColor(-16777216)
                .setUv(q, h)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, -1.0F, 0.0F);
            k = o;
            l = p;
            m = q;
        }

        poseStack.popPose();
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(EnderDragon entity) {
        return DRAGON_LOCATION;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        float f = -16.0F;
        PartDefinition partDefinition2 = partDefinition.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .addBox("upperlip", -6.0F, -1.0F, -24.0F, 12, 5, 16, 176, 44)
                .addBox("upperhead", -8.0F, -8.0F, -10.0F, 16, 16, 16, 112, 30)
                .mirror()
                .addBox("scale", -5.0F, -12.0F, -4.0F, 2, 4, 6, 0, 0)
                .addBox("nostril", -5.0F, -3.0F, -22.0F, 2, 2, 4, 112, 0)
                .mirror()
                .addBox("scale", 3.0F, -12.0F, -4.0F, 2, 4, 6, 0, 0)
                .addBox("nostril", 3.0F, -3.0F, -22.0F, 2, 2, 4, 112, 0),
            PartPose.ZERO
        );
        partDefinition2.addOrReplaceChild(
            "jaw", CubeListBuilder.create().addBox("jaw", -6.0F, 0.0F, -16.0F, 12, 4, 16, 176, 65), PartPose.offset(0.0F, 4.0F, -8.0F)
        );
        partDefinition.addOrReplaceChild(
            "neck",
            CubeListBuilder.create().addBox("box", -5.0F, -5.0F, -5.0F, 10, 10, 10, 192, 104).addBox("scale", -1.0F, -9.0F, -3.0F, 2, 4, 6, 48, 0),
            PartPose.ZERO
        );
        partDefinition.addOrReplaceChild(
            "body",
            CubeListBuilder.create()
                .addBox("body", -12.0F, 0.0F, -16.0F, 24, 24, 64, 0, 0)
                .addBox("scale", -1.0F, -6.0F, -10.0F, 2, 6, 12, 220, 53)
                .addBox("scale", -1.0F, -6.0F, 10.0F, 2, 6, 12, 220, 53)
                .addBox("scale", -1.0F, -6.0F, 30.0F, 2, 6, 12, 220, 53),
            PartPose.offset(0.0F, 4.0F, 8.0F)
        );
        PartDefinition partDefinition3 = partDefinition.addOrReplaceChild(
            "left_wing",
            CubeListBuilder.create().mirror().addBox("bone", 0.0F, -4.0F, -4.0F, 56, 8, 8, 112, 88).addBox("skin", 0.0F, 0.0F, 2.0F, 56, 0, 56, -56, 88),
            PartPose.offset(12.0F, 5.0F, 2.0F)
        );
        partDefinition3.addOrReplaceChild(
            "left_wing_tip",
            CubeListBuilder.create().mirror().addBox("bone", 0.0F, -2.0F, -2.0F, 56, 4, 4, 112, 136).addBox("skin", 0.0F, 0.0F, 2.0F, 56, 0, 56, -56, 144),
            PartPose.offset(56.0F, 0.0F, 0.0F)
        );
        PartDefinition partDefinition4 = partDefinition.addOrReplaceChild(
            "left_front_leg", CubeListBuilder.create().addBox("main", -4.0F, -4.0F, -4.0F, 8, 24, 8, 112, 104), PartPose.offset(12.0F, 20.0F, 2.0F)
        );
        PartDefinition partDefinition5 = partDefinition4.addOrReplaceChild(
            "left_front_leg_tip", CubeListBuilder.create().addBox("main", -3.0F, -1.0F, -3.0F, 6, 24, 6, 226, 138), PartPose.offset(0.0F, 20.0F, -1.0F)
        );
        partDefinition5.addOrReplaceChild(
            "left_front_foot", CubeListBuilder.create().addBox("main", -4.0F, 0.0F, -12.0F, 8, 4, 16, 144, 104), PartPose.offset(0.0F, 23.0F, 0.0F)
        );
        PartDefinition partDefinition6 = partDefinition.addOrReplaceChild(
            "left_hind_leg", CubeListBuilder.create().addBox("main", -8.0F, -4.0F, -8.0F, 16, 32, 16, 0, 0), PartPose.offset(16.0F, 16.0F, 42.0F)
        );
        PartDefinition partDefinition7 = partDefinition6.addOrReplaceChild(
            "left_hind_leg_tip", CubeListBuilder.create().addBox("main", -6.0F, -2.0F, 0.0F, 12, 32, 12, 196, 0), PartPose.offset(0.0F, 32.0F, -4.0F)
        );
        partDefinition7.addOrReplaceChild(
            "left_hind_foot", CubeListBuilder.create().addBox("main", -9.0F, 0.0F, -20.0F, 18, 6, 24, 112, 0), PartPose.offset(0.0F, 31.0F, 4.0F)
        );
        PartDefinition partDefinition8 = partDefinition.addOrReplaceChild(
            "right_wing",
            CubeListBuilder.create().addBox("bone", -56.0F, -4.0F, -4.0F, 56, 8, 8, 112, 88).addBox("skin", -56.0F, 0.0F, 2.0F, 56, 0, 56, -56, 88),
            PartPose.offset(-12.0F, 5.0F, 2.0F)
        );
        partDefinition8.addOrReplaceChild(
            "right_wing_tip",
            CubeListBuilder.create().addBox("bone", -56.0F, -2.0F, -2.0F, 56, 4, 4, 112, 136).addBox("skin", -56.0F, 0.0F, 2.0F, 56, 0, 56, -56, 144),
            PartPose.offset(-56.0F, 0.0F, 0.0F)
        );
        PartDefinition partDefinition9 = partDefinition.addOrReplaceChild(
            "right_front_leg", CubeListBuilder.create().addBox("main", -4.0F, -4.0F, -4.0F, 8, 24, 8, 112, 104), PartPose.offset(-12.0F, 20.0F, 2.0F)
        );
        PartDefinition partDefinition10 = partDefinition9.addOrReplaceChild(
            "right_front_leg_tip", CubeListBuilder.create().addBox("main", -3.0F, -1.0F, -3.0F, 6, 24, 6, 226, 138), PartPose.offset(0.0F, 20.0F, -1.0F)
        );
        partDefinition10.addOrReplaceChild(
            "right_front_foot", CubeListBuilder.create().addBox("main", -4.0F, 0.0F, -12.0F, 8, 4, 16, 144, 104), PartPose.offset(0.0F, 23.0F, 0.0F)
        );
        PartDefinition partDefinition11 = partDefinition.addOrReplaceChild(
            "right_hind_leg", CubeListBuilder.create().addBox("main", -8.0F, -4.0F, -8.0F, 16, 32, 16, 0, 0), PartPose.offset(-16.0F, 16.0F, 42.0F)
        );
        PartDefinition partDefinition12 = partDefinition11.addOrReplaceChild(
            "right_hind_leg_tip", CubeListBuilder.create().addBox("main", -6.0F, -2.0F, 0.0F, 12, 32, 12, 196, 0), PartPose.offset(0.0F, 32.0F, -4.0F)
        );
        partDefinition12.addOrReplaceChild(
            "right_hind_foot", CubeListBuilder.create().addBox("main", -9.0F, 0.0F, -20.0F, 18, 6, 24, 112, 0), PartPose.offset(0.0F, 31.0F, 4.0F)
        );
        return LayerDefinition.create(meshDefinition, 256, 256);
    }

    public static class DragonModel extends EntityModel<EnderDragon> {
        private final ModelPart head;
        private final ModelPart neck;
        private final ModelPart jaw;
        private final ModelPart body;
        private final ModelPart leftWing;
        private final ModelPart leftWingTip;
        private final ModelPart leftFrontLeg;
        private final ModelPart leftFrontLegTip;
        private final ModelPart leftFrontFoot;
        private final ModelPart leftRearLeg;
        private final ModelPart leftRearLegTip;
        private final ModelPart leftRearFoot;
        private final ModelPart rightWing;
        private final ModelPart rightWingTip;
        private final ModelPart rightFrontLeg;
        private final ModelPart rightFrontLegTip;
        private final ModelPart rightFrontFoot;
        private final ModelPart rightRearLeg;
        private final ModelPart rightRearLegTip;
        private final ModelPart rightRearFoot;
        @Nullable
        private EnderDragon entity;
        private float a;

        public DragonModel(ModelPart root) {
            this.head = root.getChild("head");
            this.jaw = this.head.getChild("jaw");
            this.neck = root.getChild("neck");
            this.body = root.getChild("body");
            this.leftWing = root.getChild("left_wing");
            this.leftWingTip = this.leftWing.getChild("left_wing_tip");
            this.leftFrontLeg = root.getChild("left_front_leg");
            this.leftFrontLegTip = this.leftFrontLeg.getChild("left_front_leg_tip");
            this.leftFrontFoot = this.leftFrontLegTip.getChild("left_front_foot");
            this.leftRearLeg = root.getChild("left_hind_leg");
            this.leftRearLegTip = this.leftRearLeg.getChild("left_hind_leg_tip");
            this.leftRearFoot = this.leftRearLegTip.getChild("left_hind_foot");
            this.rightWing = root.getChild("right_wing");
            this.rightWingTip = this.rightWing.getChild("right_wing_tip");
            this.rightFrontLeg = root.getChild("right_front_leg");
            this.rightFrontLegTip = this.rightFrontLeg.getChild("right_front_leg_tip");
            this.rightFrontFoot = this.rightFrontLegTip.getChild("right_front_foot");
            this.rightRearLeg = root.getChild("right_hind_leg");
            this.rightRearLegTip = this.rightRearLeg.getChild("right_hind_leg_tip");
            this.rightRearFoot = this.rightRearLegTip.getChild("right_hind_foot");
        }

        public void prepareMobModel(EnderDragon entity, float limbSwing, float limbSwingAmount, float partialTick) {
            this.entity = entity;
            this.a = partialTick;
        }

        /**
         * Sets this entity's model rotation angles
         */
        public void setupAnim(EnderDragon entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        }

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
            poseStack.pushPose();
            float f = Mth.lerp(this.a, this.entity.oFlapTime, this.entity.flapTime);
            this.jaw.xRot = (float)(Math.sin(f * (float) (Math.PI * 2)) + 1.0) * 0.2F;
            float g = (float)(Math.sin(f * (float) (Math.PI * 2) - 1.0F) + 1.0);
            g = (g * g + g * 2.0F) * 0.05F;
            poseStack.translate(0.0F, g - 2.0F, -3.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(g * 2.0F));
            float h = 0.0F;
            float i = 20.0F;
            float j = -12.0F;
            float k = 1.5F;
            double[] ds = this.entity.getLatencyPos(6, this.a);
            float l = Mth.wrapDegrees((float)(this.entity.getLatencyPos(5, this.a)[0] - this.entity.getLatencyPos(10, this.a)[0]));
            float m = Mth.wrapDegrees((float)(this.entity.getLatencyPos(5, this.a)[0] + l / 2.0F));
            float n = f * (float) (Math.PI * 2);

            for (int o = 0; o < 5; o++) {
                double[] es = this.entity.getLatencyPos(5 - o, this.a);
                float p = (float)Math.cos(o * 0.45F + n) * 0.15F;
                this.neck.yRot = Mth.wrapDegrees((float)(es[0] - ds[0])) * (float) (Math.PI / 180.0) * 1.5F;
                this.neck.xRot = p + this.entity.getHeadPartYOffset(o, ds, es) * (float) (Math.PI / 180.0) * 1.5F * 5.0F;
                this.neck.zRot = -Mth.wrapDegrees((float)(es[0] - m)) * (float) (Math.PI / 180.0) * 1.5F;
                this.neck.y = i;
                this.neck.z = j;
                this.neck.x = h;
                i += Mth.sin(this.neck.xRot) * 10.0F;
                j -= Mth.cos(this.neck.yRot) * Mth.cos(this.neck.xRot) * 10.0F;
                h -= Mth.sin(this.neck.yRot) * Mth.cos(this.neck.xRot) * 10.0F;
                this.neck.render(poseStack, buffer, packedLight, packedOverlay, color);
            }

            this.head.y = i;
            this.head.z = j;
            this.head.x = h;
            double[] fs = this.entity.getLatencyPos(0, this.a);
            this.head.yRot = Mth.wrapDegrees((float)(fs[0] - ds[0])) * (float) (Math.PI / 180.0);
            this.head.xRot = Mth.wrapDegrees(this.entity.getHeadPartYOffset(6, ds, fs)) * (float) (Math.PI / 180.0) * 1.5F * 5.0F;
            this.head.zRot = -Mth.wrapDegrees((float)(fs[0] - m)) * (float) (Math.PI / 180.0);
            this.head.render(poseStack, buffer, packedLight, packedOverlay, color);
            poseStack.pushPose();
            poseStack.translate(0.0F, 1.0F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(-l * 1.5F));
            poseStack.translate(0.0F, -1.0F, 0.0F);
            this.body.zRot = 0.0F;
            this.body.render(poseStack, buffer, packedLight, packedOverlay, color);
            float q = f * (float) (Math.PI * 2);
            this.leftWing.xRot = 0.125F - (float)Math.cos(q) * 0.2F;
            this.leftWing.yRot = -0.25F;
            this.leftWing.zRot = -((float)(Math.sin(q) + 0.125)) * 0.8F;
            this.leftWingTip.zRot = (float)(Math.sin(q + 2.0F) + 0.5) * 0.75F;
            this.rightWing.xRot = this.leftWing.xRot;
            this.rightWing.yRot = -this.leftWing.yRot;
            this.rightWing.zRot = -this.leftWing.zRot;
            this.rightWingTip.zRot = -this.leftWingTip.zRot;
            this.renderSide(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                g,
                this.leftWing,
                this.leftFrontLeg,
                this.leftFrontLegTip,
                this.leftFrontFoot,
                this.leftRearLeg,
                this.leftRearLegTip,
                this.leftRearFoot,
                color
            );
            this.renderSide(
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                g,
                this.rightWing,
                this.rightFrontLeg,
                this.rightFrontLegTip,
                this.rightFrontFoot,
                this.rightRearLeg,
                this.rightRearLegTip,
                this.rightRearFoot,
                color
            );
            poseStack.popPose();
            float p = -Mth.sin(f * (float) (Math.PI * 2)) * 0.0F;
            n = f * (float) (Math.PI * 2);
            i = 10.0F;
            j = 60.0F;
            h = 0.0F;
            ds = this.entity.getLatencyPos(11, this.a);

            for (int r = 0; r < 12; r++) {
                fs = this.entity.getLatencyPos(12 + r, this.a);
                p += Mth.sin(r * 0.45F + n) * 0.05F;
                this.neck.yRot = (Mth.wrapDegrees((float)(fs[0] - ds[0])) * 1.5F + 180.0F) * (float) (Math.PI / 180.0);
                this.neck.xRot = p + (float)(fs[1] - ds[1]) * (float) (Math.PI / 180.0) * 1.5F * 5.0F;
                this.neck.zRot = Mth.wrapDegrees((float)(fs[0] - m)) * (float) (Math.PI / 180.0) * 1.5F;
                this.neck.y = i;
                this.neck.z = j;
                this.neck.x = h;
                i += Mth.sin(this.neck.xRot) * 10.0F;
                j -= Mth.cos(this.neck.yRot) * Mth.cos(this.neck.xRot) * 10.0F;
                h -= Mth.sin(this.neck.yRot) * Mth.cos(this.neck.xRot) * 10.0F;
                this.neck.render(poseStack, buffer, packedLight, packedOverlay, color);
            }

            poseStack.popPose();
        }

        private void renderSide(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            float rotation,
            ModelPart wing,
            ModelPart frontLeg,
            ModelPart frontLegTip,
            ModelPart frontFoot,
            ModelPart rearLeg,
            ModelPart rearLegTip,
            ModelPart rearFoot,
            int alpha
        ) {
            rearLeg.xRot = 1.0F + rotation * 0.1F;
            rearLegTip.xRot = 0.5F + rotation * 0.1F;
            rearFoot.xRot = 0.75F + rotation * 0.1F;
            frontLeg.xRot = 1.3F + rotation * 0.1F;
            frontLegTip.xRot = -0.5F - rotation * 0.1F;
            frontFoot.xRot = 0.75F + rotation * 0.1F;
            wing.render(poseStack, buffer, packedLight, packedOverlay, alpha);
            frontLeg.render(poseStack, buffer, packedLight, packedOverlay, alpha);
            rearLeg.render(poseStack, buffer, packedLight, packedOverlay, alpha);
        }
    }
}
