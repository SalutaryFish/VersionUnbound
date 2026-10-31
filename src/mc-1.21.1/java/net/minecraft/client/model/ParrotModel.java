package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Parrot;

public class ParrotModel extends HierarchicalModel<Parrot> {
    private static final String FEATHER = "feather";
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart tail;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart head;
    private final ModelPart feather;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public ParrotModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.tail = root.getChild("tail");
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
        this.head = root.getChild("head");
        this.feather = this.head.getChild("feather");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        partDefinition.addOrReplaceChild(
            "body", CubeListBuilder.create().texOffs(2, 8).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F), PartPose.offset(0.0F, 16.5F, -3.0F)
        );
        partDefinition.addOrReplaceChild(
            "tail", CubeListBuilder.create().texOffs(22, 1).addBox(-1.5F, -1.0F, -1.0F, 3.0F, 4.0F, 1.0F), PartPose.offset(0.0F, 21.07F, 1.16F)
        );
        partDefinition.addOrReplaceChild(
            "left_wing", CubeListBuilder.create().texOffs(19, 8).addBox(-0.5F, 0.0F, -1.5F, 1.0F, 5.0F, 3.0F), PartPose.offset(1.5F, 16.94F, -2.76F)
        );
        partDefinition.addOrReplaceChild(
            "right_wing", CubeListBuilder.create().texOffs(19, 8).addBox(-0.5F, 0.0F, -1.5F, 1.0F, 5.0F, 3.0F), PartPose.offset(-1.5F, 16.94F, -2.76F)
        );
        PartDefinition partDefinition2 = partDefinition.addOrReplaceChild(
            "head", CubeListBuilder.create().texOffs(2, 2).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offset(0.0F, 15.69F, -2.76F)
        );
        partDefinition2.addOrReplaceChild(
            "head2", CubeListBuilder.create().texOffs(10, 0).addBox(-1.0F, -0.5F, -2.0F, 2.0F, 1.0F, 4.0F), PartPose.offset(0.0F, -2.0F, -1.0F)
        );
        partDefinition2.addOrReplaceChild(
            "beak1", CubeListBuilder.create().texOffs(11, 7).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offset(0.0F, -0.5F, -1.5F)
        );
        partDefinition2.addOrReplaceChild(
            "beak2", CubeListBuilder.create().texOffs(16, 7).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offset(0.0F, -1.75F, -2.45F)
        );
        partDefinition2.addOrReplaceChild(
            "feather", CubeListBuilder.create().texOffs(2, 18).addBox(0.0F, -4.0F, -2.0F, 0.0F, 5.0F, 4.0F), PartPose.offset(0.0F, -2.15F, 0.15F)
        );
        CubeListBuilder cubeListBuilder = CubeListBuilder.create().texOffs(14, 18).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F);
        partDefinition.addOrReplaceChild("left_leg", cubeListBuilder, PartPose.offset(1.0F, 22.0F, -1.05F));
        partDefinition.addOrReplaceChild("right_leg", cubeListBuilder, PartPose.offset(-1.0F, 22.0F, -1.05F));
        return LayerDefinition.create(meshDefinition, 32, 32);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    /**
     * Sets this entity's model rotation angles
     */
    public void setupAnim(Parrot entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.setupAnim(getState(entity), entity.tickCount, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    public void prepareMobModel(Parrot entity, float limbSwing, float limbSwingAmount, float partialTick) {
        this.prepare(getState(entity));
    }

    public void renderOnShoulder(
        PoseStack poseStack,
        VertexConsumer buffer,
        int packedLight,
        int packedOverlay,
        float limbSwing,
        float limbSwingAmount,
        float netHeadYaw,
        float headPitch,
        int tickCount
    ) {
        this.prepare(ParrotModel.State.ON_SHOULDER);
        this.setupAnim(ParrotModel.State.ON_SHOULDER, tickCount, limbSwing, limbSwingAmount, 0.0F, netHeadYaw, headPitch);
        this.root.render(poseStack, buffer, packedLight, packedOverlay);
    }

    private void setupAnim(ParrotModel.State state, int tickCount, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.xRot = headPitch * (float) (Math.PI / 180.0);
        this.head.yRot = netHeadYaw * (float) (Math.PI / 180.0);
        this.head.zRot = 0.0F;
        this.head.x = 0.0F;
        this.body.x = 0.0F;
        this.tail.x = 0.0F;
        this.rightWing.x = -1.5F;
        this.leftWing.x = 1.5F;
        switch (state) {
            case STANDING:
                this.leftLeg.xRot = this.leftLeg.xRot + Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
                this.rightLeg.xRot = this.rightLeg.xRot + Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount;
            case FLYING:
            case ON_SHOULDER:
            default:
                float h = ageInTicks * 0.3F;
                this.head.y = 15.69F + h;
                this.tail.xRot = 1.015F + Mth.cos(limbSwing * 0.6662F) * 0.3F * limbSwingAmount;
                this.tail.y = 21.07F + h;
                this.body.y = 16.5F + h;
                this.leftWing.zRot = -0.0873F - ageInTicks;
                this.leftWing.y = 16.94F + h;
                this.rightWing.zRot = 0.0873F + ageInTicks;
                this.rightWing.y = 16.94F + h;
                this.leftLeg.y = 22.0F + h;
                this.rightLeg.y = 22.0F + h;
            case SITTING:
                break;
            case PARTY:
                float f = Mth.cos(tickCount);
                float g = Mth.sin(tickCount);
                this.head.x = f;
                this.head.y = 15.69F + g;
                this.head.xRot = 0.0F;
                this.head.yRot = 0.0F;
                this.head.zRot = Mth.sin(tickCount) * 0.4F;
                this.body.x = f;
                this.body.y = 16.5F + g;
                this.leftWing.zRot = -0.0873F - ageInTicks;
                this.leftWing.x = 1.5F + f;
                this.leftWing.y = 16.94F + g;
                this.rightWing.zRot = 0.0873F + ageInTicks;
                this.rightWing.x = -1.5F + f;
                this.rightWing.y = 16.94F + g;
                this.tail.x = f;
                this.tail.y = 21.07F + g;
        }
    }

    private void prepare(ParrotModel.State state) {
        this.feather.xRot = -0.2214F;
        this.body.xRot = 0.4937F;
        this.leftWing.xRot = -0.6981F;
        this.leftWing.yRot = (float) -Math.PI;
        this.rightWing.xRot = -0.6981F;
        this.rightWing.yRot = (float) -Math.PI;
        this.leftLeg.xRot = -0.0299F;
        this.rightLeg.xRot = -0.0299F;
        this.leftLeg.y = 22.0F;
        this.rightLeg.y = 22.0F;
        this.leftLeg.zRot = 0.0F;
        this.rightLeg.zRot = 0.0F;
        switch (state) {
            case FLYING:
                this.leftLeg.xRot += (float) (Math.PI * 2.0 / 9.0);
                this.rightLeg.xRot += (float) (Math.PI * 2.0 / 9.0);
            case STANDING:
            case ON_SHOULDER:
            default:
                break;
            case SITTING:
                float f = 1.9F;
                this.head.y = 17.59F;
                this.tail.xRot = 1.5388988F;
                this.tail.y = 22.97F;
                this.body.y = 18.4F;
                this.leftWing.zRot = -0.0873F;
                this.leftWing.y = 18.84F;
                this.rightWing.zRot = 0.0873F;
                this.rightWing.y = 18.84F;
                this.leftLeg.y += 1.9F;
                this.rightLeg.y += 1.9F;
                this.leftLeg.xRot += (float) (Math.PI / 2);
                this.rightLeg.xRot += (float) (Math.PI / 2);
                break;
            case PARTY:
                this.leftLeg.zRot = (float) (-Math.PI / 9);
                this.rightLeg.zRot = (float) (Math.PI / 9);
        }
    }

    private static ParrotModel.State getState(Parrot parrot) {
        if (parrot.isPartyParrot()) {
            return ParrotModel.State.PARTY;
        } else if (parrot.isInSittingPose()) {
            return ParrotModel.State.SITTING;
        } else {
            return parrot.isFlying() ? ParrotModel.State.FLYING : ParrotModel.State.STANDING;
        }
    }

    public enum State {
        FLYING,
        STANDING,
        SITTING,
        PARTY,
        ON_SHOULDER;
    }
}
